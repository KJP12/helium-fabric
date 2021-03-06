package net.kjp12.plymouth.debug.anti_xray;// Created 2021-03-30T00:02:20

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kjp12.plymouth.antixray.IShadowChunk;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.HashSet;
import java.util.Set;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * @author KJP12
 * @since 0.0.0
 */
public class AntiXrayDebugger {
    public static final Identifier
            debugAntiXraySet = new Identifier("plymouth-debug", "set"),
            debugAntiXrayUpdate = new Identifier("plymouth-debug", "update"),
            debugAntiXrayTest = new Identifier("plymouth-debug", "test"),
            debugAntiXrayMask = new Identifier("plymouth-debug", "mask");
    public static final Set<ServerPlayNetworkHandler> players = new HashSet<>();

    public static boolean canSendDebugInformation(ServerCommandSource source) {
        var entity = source.getEntity();
        return entity instanceof ServerPlayerEntity && players.contains(((ServerPlayerEntity) entity).networkHandler);
    }

    public static void initialise() {
        ServerPlayConnectionEvents.JOIN.register((player, packetSender, server) -> {
            if (ServerPlayNetworking.canSend(player, AntiXrayDebugger.debugAntiXrayUpdate))
                AntiXrayDebugger.players.add(player);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> dispatcher.register(literal("mdump").requires(AntiXrayDebugger::canSendDebugInformation).executes(ctx -> {
            var player = ctx.getSource().getPlayer();
            int cx = MathHelper.floor(player.getX()) >> 4, cz = MathHelper.floor(player.getZ()) >> 4;
            var masks = ((IShadowChunk) player.world.getChunk(cx, cz)).plymouth$getShadowMasks();
            var packet = new PacketByteBuf(Unpooled.buffer()).writeVarInt(cx).writeVarInt(cz).writeVarInt(masks.length);
            for (var mask : masks) {
                if (mask == null) packet.writeVarInt(0);
                else {
                    packet.writeLongArray(mask.toLongArray());
                }
            }
            ServerPlayNetworking.send(player, AntiXrayDebugger.debugAntiXrayMask, packet);
            return 1;
        })));
    }
}
