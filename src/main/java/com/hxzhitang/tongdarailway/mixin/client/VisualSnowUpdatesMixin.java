package com.hxzhitang.tongdarailway.mixin.client;

import com.hxzhitang.tongdarailway.client.snow.VisualTrackSnow;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Invalidate client meshes after nearby track/snow updates without adding server-side state. */
@Mixin(ClientLevel.class)
public abstract class VisualSnowUpdatesMixin {
    @Inject(method="setBlocksDirty",at=@At("RETURN"))
    private void tongDaRailway$refreshVisualSnow(BlockPos pos,BlockState oldState,BlockState newState,CallbackInfo ci) {
        VisualTrackSnow.changed((ClientLevel)(Object)this,pos,oldState,newState);
    }
}
