package com.hxzhitang.tongdarailway.railway.fixed;
import org.junit.jupiter.api.Test;
import net.minecraft.core.BlockPos;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Generation order must not alter candidates or displace the mandatory strategic anchor. */
class AutomaticRailwayNetworkTest {
    @Test void automaticDiagonalConnectionKeepsIntegerNativeAxes() throws Exception {
        var method=FixedRailwayNetwork.class.getDeclaredMethod("directConnection",com.hxzhitang.tongdarailway.structure.StationTemplate.Exit.class,com.hxzhitang.tongdarailway.structure.StationTemplate.Exit.class);
        method.setAccessible(true);
        var a=new com.hxzhitang.tongdarailway.structure.StationTemplate.Exit(new BlockPos(-1208,94,190),new net.minecraft.world.phys.Vec3(-1,0,0));
        var b=new com.hxzhitang.tongdarailway.structure.StationTemplate.Exit(new BlockPos(-2164,88,-351),new net.minecraft.world.phys.Vec3(1,0,0));
        assertNotNull(method.invoke(null,a,b));
    }
    @Test void sameSeedKeepsTheSameDecisions() {
        var sites=List.of(new BlockPos(-1003,95,127));
        assertEquals(AutomaticRailwayNetwork.candidates(42,sites),AutomaticRailwayNetwork.candidates(42,sites));
        assertNotEquals(AutomaticRailwayNetwork.candidates(42,sites).nodes(),AutomaticRailwayNetwork.candidates(43,sites).nodes());
    }
    @Test void criticalSitesAndSmallIndependentHaltsHaveSeparateRoles() {
        var site=new BlockPos(800,100,-500);
        for(long seed:new long[]{0,1,-4051455670688324092L,Long.MAX_VALUE}) {
            var network=AutomaticRailwayNetwork.candidates(seed,List.of(site));
            assertEquals(site,network.nodes().getFirst().center());
            assertEquals("critical",network.nodes().getFirst().role());
            assertEquals(4,network.nodes().stream().filter(n->n.radius()==0&&n.role().equals("halt")&&n.station().equals("eow_small_halt")).count());
            assertEquals(network.nodes().size()-1,RegionalRailwayGraph.connect(network.nodes()).size());
        }
    }
}
