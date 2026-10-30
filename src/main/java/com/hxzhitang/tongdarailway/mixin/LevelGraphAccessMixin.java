package com.hxzhitang.tongdarailway.mixin;

import com.hxzhitang.tongdarailway.railway.fixed.NoLoadGraphAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only native graph-discovery scope sees absent chunks as absent track; normal world access is unchanged. */
@Mixin(Level.class)
public abstract class LevelGraphAccessMixin {
    @Inject(method="getBlockState",at=@At("HEAD"),cancellable=true)
    private void tongDaRailway$noGraphChunkLoad(BlockPos pos,CallbackInfoReturnable<BlockState> ci) {
        if((Object)this instanceof ServerLevel world&&NoLoadGraphAccess.unavailable(world,pos))ci.setReturnValue(Blocks.AIR.defaultBlockState());
    }
    @Inject(method="getBlockEntity",at=@At("HEAD"),cancellable=true)
    private void tongDaRailway$noGraphEntityChunkLoad(BlockPos pos,CallbackInfoReturnable<BlockEntity> ci) {
        if((Object)this instanceof ServerLevel world&&NoLoadGraphAccess.unavailable(world,pos))ci.setReturnValue(null);
    }
}
