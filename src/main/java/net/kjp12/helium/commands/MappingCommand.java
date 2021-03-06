package net.kjp12.helium.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.kjp12.helium.Helium;
import net.kjp12.helium.HeliumEarlyRiser;
import net.kjp12.helium.mixins.AccessorMapState;
import net.minecraft.SharedConstants;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.map.MapState;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Predicate;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * @author KJP12
 * @since 0.0.0
 **/
public class MappingCommand {
    public static final Predicate<ServerCommandSource>
            REQUIRE_MAPPING_PERMISSION = scs -> scs.hasPermissionLevel(3);

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var m0 = argument("overwrite", IntegerArgumentType.integer()).then(argument("uri", StringArgumentType.greedyString()).executes(ctx -> {
            assert REQUIRE_MAPPING_PERMISSION.test(ctx.getSource()) : String.format("BugCheck: %s: %s -> %s", ctx, ctx.getSource(), ctx.getInput());
            var uri = StringArgumentType.getString(ctx, "uri");
            var map = IntegerArgumentType.getInteger(ctx, "overwrite");
            var uriLinked = new LiteralText(uri).styled(s -> s.withFormatting(Formatting.BLUE, Formatting.UNDERLINE).withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, uri)));
            ctx.getSource().sendFeedback(new LiteralText("Downloading from ").append(uriLinked).append(" for Map ").append(Integer.toString(map)), true);
            // Asynchronous access moment
            // We need to operate on the overworld due to messing with map states.
            ForkJoinPool.commonPool().execute(() -> {
                try {
                    var connection = new URL(uri).openConnection();
                    if (connection instanceof HttpURLConnection) {
                        var httpConnection = (HttpURLConnection) connection;
                        httpConnection.setRequestMethod("GET");
                        httpConnection.setInstanceFollowRedirects(true);
                        if (httpConnection.getResponseCode() / 100 != 2) {
                            var source = ctx.getSource();
                            HeliumEarlyRiser.LOGGER.error("[Server Failure] Failed to download {} for map {}; following dump below.", uri, map);
                            source.sendError(new LiteralText("Failed to download ").append(uriLinked));
                            source.sendError(Helium.SEE_LOGS);
                            try (var body = httpConnection.getErrorStream()) {
                                body.transferTo(System.err);
                            } catch (IOException ioe) {
                                HeliumEarlyRiser.LOGGER.warn("[IO Failure] Failed to pipe {}", uri, ioe);
                            }
                            try (var body = httpConnection.getInputStream()) {
                                body.transferTo(System.err);
                            } catch (IOException ioe) {
                                HeliumEarlyRiser.LOGGER.warn("[IO Failure] Failed to pipe {}", uri, ioe);
                            }
                            return;
                        }
                    } else throw new IllegalArgumentException("Non-HTTP requests not allowed.");
                    try (var body = connection.getInputStream()) {
                        // This *must* be submitted to the server and joined, else we risk both closing the pipe too early,
                        // and possibly corrupting the persistent storage.
                        ctx.getSource().getMinecraftServer().submitAndJoin(() -> {
                            var source = ctx.getSource();
                            try {
                                var server = source.getMinecraftServer();
                                var overworld = server.getOverworld();
                                // note: data nesting can be blamed on by Mojang, who thought this was a good idea to begin with.
                                var mapCompound = Helium.readTag(body, server.getDataFixer(), SharedConstants.getGameVersion().getWorldVersion()).getCompound("data");
                                var mapName = FilledMapItem.getMapName(map);
                                var oldMapState = (MapState & AccessorMapState) overworld.getMapState(mapName);
                                assert oldMapState != null;
                                if (!mapCompound.contains("dimension"))
                                    mapCompound.putString("dimension", overworld.getRegistryKey().getValue().toString());
                                var newMapState = (MapState & AccessorMapState) MapState.fromNbt(mapCompound);
                                overworld.putMapState(mapName, newMapState);
                                for (var updateTracker : oldMapState.getUpdateTrackers()) {
                                    newMapState.getPlayerSyncData(updateTracker.player);
                                }
                                newMapState.callMarkDirty(0, 0);
                                newMapState.callMarkDirty(127, 127);
                                source.sendFeedback(new LiteralText("Deployed ").append(uriLinked).append(" to map " + map), true);
                            } catch (RuntimeException | IOException ioe) {
                                HeliumEarlyRiser.LOGGER.error("[IO Failure] Failed to parse {} for map {}", uri, map, ioe);
                                source.sendError(new LiteralText("Failed to parse ").append(uriLinked).append(": ").append(new LiteralText(ioe.getLocalizedMessage())));
                                source.sendError(Helium.SEE_LOGS);
                            }
                        });
                    }
                } catch (IOException ioe) {
                    var source = ctx.getSource();
                    HeliumEarlyRiser.LOGGER.error("[IO Failure] Failed to download {} for map {}", uri, map, ioe);
                    source.sendError(new LiteralText("Failed to download ").append(uriLinked).append(": ").append(new LiteralText(ioe.getLocalizedMessage())));
                    source.sendError(Helium.SEE_LOGS);
                }
            });
            return Command.SINGLE_SUCCESS;
        }));
        dispatcher.register(literal("overwrite").then(literal("map").requires(REQUIRE_MAPPING_PERMISSION).then(m0)));
    }
}
