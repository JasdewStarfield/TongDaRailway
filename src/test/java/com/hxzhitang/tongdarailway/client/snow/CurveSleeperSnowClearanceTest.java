package com.hxzhitang.tongdarailway.client.snow;

import com.mojang.blaze3d.vertex.PoseStack;
import com.hxzhitang.tongdarailway.railway.snow.SnowOverlayPolicy;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CurveSleeperSnowClearanceTest {
    @Test void nativeSnowClearsTheLowestCurveSleeperTopEvenWithoutAnOverlay() {
        // Current Create tie.obj and SegmentAngles offsets; native snow is exactly 1/8 block high.
        double nativeSleeperTop=0.06561524278519393+0.1875-0.12890625;
        assertTrue(nativeSleeperTop<1.0/8);
        assertTrue(nativeSleeperTop+CurveSleeperSnowClearance.LIFT>1.0/8+1.0/128);
        assertTrue(nativeSleeperTop+CurveSleeperSnowClearance.LIFT>SnowOverlayPolicy.THIN_SNOW+1.0/128);
    }

    @Test void liftingTiltedSleepersPreservesRailAlignmentAndNormals() {
        var stack=new PoseStack();stack.translate(12,107,-19);
        stack.mulPose(new Quaternionf().rotationXYZ(0.4f,0.8f,-0.2f));
        var pose=stack.last();float[] before=new float[16];pose.pose().get(before);
        var normalBefore=new org.joml.Matrix3f(pose.normal());
        CurveSleeperSnowClearance.apply(new PoseStack.Pose[]{null,pose});
        float[] after=new float[16];pose.pose().get(after);
        for(int i=0;i<16;i++)assertEquals(before[i]+(i==13?CurveSleeperSnowClearance.LIFT:0),after[i],0.000001f);
        assertEquals(normalBefore,pose.normal());
    }
}
