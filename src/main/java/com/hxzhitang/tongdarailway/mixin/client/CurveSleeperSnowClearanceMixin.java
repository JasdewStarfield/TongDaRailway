package com.hxzhitang.tongdarailway.mixin.client;

import com.hxzhitang.tongdarailway.VisualSnowConfig;
import com.hxzhitang.tongdarailway.client.snow.CurveSleeperSnowClearance;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.trains.track.BezierConnection;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The shared native pose cache feeds both Flywheel and the fallback curve renderer. */
@Mixin(value=BezierConnection.SegmentAngles.class,remap=false)
public abstract class CurveSleeperSnowClearanceMixin {
    @Shadow @Final public PoseStack.Pose[] tieTransform;

    @Inject(method="<init>",at=@At("RETURN"),remap=false)
    private void tongDaRailway$clearNativeSnow(CallbackInfo ci) {
        // Native snow intersects the shallow V-shaped sleeper top even with our overlay off.
        if(VisualSnowConfig.CURVE_SLEEPER_CLEARANCE.get())CurveSleeperSnowClearance.apply(tieTransform);
    }
}
