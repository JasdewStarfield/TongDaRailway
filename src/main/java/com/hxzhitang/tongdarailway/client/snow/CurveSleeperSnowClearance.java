package com.hxzhitang.tongdarailway.client.snow;

import com.mojang.blaze3d.vertex.PoseStack;

/** Keep the native curve sleepers above one-layer snow without moving the rails or railway graph. */
public final class CurveSleeperSnowClearance {
    public static final float LIFT=1.0f/64;
    private CurveSleeperSnowClearance() {}

    public static void apply(PoseStack.Pose[] sleepers) {
        for(var sleeper:sleepers) {
            if(sleeper==null)continue; // Create leaves unused endpoint slots empty.
            var matrix=sleeper.pose();
            // These matrices already contain curve rotations: lift along world Y,
            // rather than translating along a tilted sleeper's local axis.
            matrix.m31(matrix.m31()+LIFT);
        }
    }
}
