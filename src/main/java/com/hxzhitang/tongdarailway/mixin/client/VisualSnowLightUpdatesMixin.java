package com.hxzhitang.tongdarailway.mixin.client;

import com.hxzhitang.tongdarailway.client.snow.VisualTrackSnow;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Relight cached visual snow after either skylight or block-light propagation changes. */
@Mixin(ClientChunkCache.class)
public abstract class VisualSnowLightUpdatesMixin {
    @Shadow @Final private ClientLevel level;
    @Inject(method="onLightUpdate",at=@At("RETURN"))
    private void eow$relightSnow(LightLayer type,SectionPos section,CallbackInfo ci) {
        VisualTrackSnow.lightChanged(level,section);
    }
}
