package com.hxzhitang.tongdarailway.railway.fixed;

import com.hxzhitang.tongdarailway.structure.TrackPutInfo;
import com.simibubi.create.content.trains.track.TrackShape;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeCurveSafetyTest {
    private TrackPutInfo curve(Vec3 offset,Vec3 end) {
        return new TrackPutInfo(BlockPos.ZERO,TrackShape.XO,new TrackPutInfo.BezierInfo(Vec3.ZERO,new Vec3(1,0,0),offset,end),TrackShape.ZO);
    }
    @Test void rejectTinyQuarterTurnsAndSingleCurveHairpins() {
        assertFalse(NativeCurveSafety.valid(curve(new Vec3(4,0,4),new Vec3(0,0,-1))));
        assertFalse(NativeCurveSafety.valid(curve(new Vec3(0,0,20),new Vec3(1,0,0))));
        assertFalse(NativeCurveSafety.valid(curve(new Vec3(-20,0,20),new Vec3(0,0,-1))));
    }
    @Test void keepRegularNativeTurnsAndBroadOffsetApproaches() {
        assertTrue(NativeCurveSafety.valid(curve(new Vec3(16,0,16),new Vec3(0,0,-1))));
        assertTrue(NativeCurveSafety.valid(curve(new Vec3(80,0,20),new Vec3(-1,0,0))));
    }
}
