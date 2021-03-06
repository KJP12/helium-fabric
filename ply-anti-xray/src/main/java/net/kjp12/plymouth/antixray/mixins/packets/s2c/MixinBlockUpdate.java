package net.kjp12.plymouth.antixray.mixins.packets.s2c;

import net.kjp12.plymouth.antixray.IShadowChunk;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BlockUpdateS2CPacket.class)
public class MixinBlockUpdate {
    @Redirect(method = "<init>(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/BlockView;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;"))
    private static BlockState helium$init$proxyWorldChunk$getBlockState(BlockView self, BlockPos pos) {
        return self instanceof World ? ((IShadowChunk) ((World) self).getChunk(pos.getX() >> 4, pos.getZ() >> 4)).plymouth$getShadowBlock(pos) : self.getBlockState(pos);
    }
}
