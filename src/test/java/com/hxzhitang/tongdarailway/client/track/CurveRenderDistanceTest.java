package com.hxzhitang.tongdarailway.client.track;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CurveRenderDistanceTest {
    @Test void reportedCameraPositionsStayVisibleAcrossTheEndpointCutoff() {
        var owner=new Vec3(-931.5,87.5,1244.5);
        var first=new Vec3(-888,104,1404);var second=new Vec3(-849,104,1428);
        assertTrue(owner.distanceTo(first)<192);assertTrue(owner.distanceTo(second)>192);
        var bounds=new AABB(-932,87,1244,-775,87,1627).inflate(1.375);
        assertTrue(CurveRenderDistance.nearBounds(first,bounds,192));
        assertTrue(CurveRenderDistance.nearBounds(second,bounds,192));
    }
    @Test void aLongCurveDoesNotDisappearWhenBothEndpointsAreDistant() {
        var bounds=new AABB(-1500,80,0,-500,90,40);
        assertTrue(CurveRenderDistance.nearBounds(new Vec3(-1000,100,20),bounds,192));
    }
    @Test void farCurvesStillRespectTheNativeDistanceLimitIncludingHeight() {
        var bounds=new AABB(0,80,0,400,80,30);
        assertFalse(CurveRenderDistance.nearBounds(new Vec3(200,273,15),bounds,192));
        assertFalse(CurveRenderDistance.nearBounds(new Vec3(-193,80,15),bounds,192));
        assertFalse(CurveRenderDistance.nearBounds(new Vec3(-140,80,-140),bounds,192));
    }
}
