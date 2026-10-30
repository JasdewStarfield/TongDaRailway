package com.hxzhitang.tongdarailway.railway.fixed;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StationPortAssignmentTest {
    @Test void northSouthThroughRoutesUseOppositeMouthsInEitherLinkOrder() {
        var north=new Vec3(0,0,-1);var south=north.reverse();var exits=List.of(north,south);
        assertEquals(List.of(0,1),StationPortAssignment.solve(List.of(north,south),exits).ports());
        assertEquals(List.of(1,0),StationPortAssignment.solve(List.of(south,north),exits).ports());
    }
    @Test void stationAxisFollowsTheNetworkAndSameSideBranchesMayShareAMouth() {
        var n=new Vec3(0,0,-1);var e=new Vec3(1,0,0);
        assertTrue(StationPortAssignment.solve(List.of(n,n.reverse()),List.of(n,n.reverse())).cost()
                <StationPortAssignment.solve(List.of(n,n.reverse()),List.of(e,e.reverse())).cost());
        assertEquals(List.of(0,0),StationPortAssignment.solve(List.of(n,n),List.of(n,n.reverse())).ports());
    }
}
