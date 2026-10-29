package com.hxzhitang.tongdarailway.railway.fixed;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.hxzhitang.tongdarailway.railway.*;
import com.hxzhitang.tongdarailway.structure.TrackPutInfo;
import com.hxzhitang.tongdarailway.util.CurveRoute;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Keep repair gaps meaningful after serialization and refuse invalid endpoints before generation. */
class FixedRailwayTest {
    static String valid() { return """
        {"format":1,"seed":42,"nodes":[
        {"id":"radar","rail":[-100,90,0],"facing":"west","protected_center":[0,90,0],"protected_radius":48},
        {"id":"mine","rail":[-900,92,-300],"facing":"east","protected_center":[-950,92,-300],"protected_radius":16}],
        "links":[{"from":"radar","to":"mine"}]}
        """; }
    static CurveRoute line() {
        var route=new CurveRoute();route.addSegment(new CurveRoute.LineSegment(new Vec3(-1000,90,-20),new Vec3(1000,90,-20)));return route;
    }
    @Test void explicitSeedAndNodeIdentitiesSurviveParsing() {
        var network=FixedRailwayNetwork.parse(valid());
        assertEquals(42,network.seed());assertEquals(2,network.nodes().size());assertEquals(1,network.links().size());
        assertThrows(UnsupportedOperationException.class,()->network.nodes().clear());
    }
    @Test void terminalCannotOverlapAProtectedBuildingOrShaft() {
        assertThrows(IllegalArgumentException.class,()->FixedRailwayNetwork.parse(valid().replace("[-100,90,0]","[-30,90,0]")));
    }
    @Test void missingNodeAndVerticalFacingAreRejected() {
        assertThrows(IllegalArgumentException.class,()->FixedRailwayNetwork.parse(valid().replace("\"to\":\"mine\"","\"to\":\"missing\"")));
        assertThrows(IllegalArgumentException.class,()->FixedRailwayNetwork.parse(valid().replace("west","up")));
    }
    @Test void loopsDuplicateLinksAndOverlongLinksAreRejected() {
        assertThrows(IllegalArgumentException.class,()->FixedRailwayNetwork.parse(valid().replace("\"to\":\"mine\"","\"to\":\"radar\"")));
        assertThrows(IllegalArgumentException.class,()->FixedRailwayNetwork.parse(valid().replace("[-900,92,-300]","[-9000,92,-300]")));
        assertThrows(IllegalArgumentException.class,()->FixedRailwayNetwork.parse(valid().replace("\"from\":\"radar\"","\"from\":\"mine\"")));
    }
    @Test void everyRepairSiteLeavesItsTerminalApproachesIntact() {
        for(long seed:new long[]{-44,0,42,Long.MAX_VALUE}) {
            int gaps=0;
            for(int distance=0;distance<2000;distance++) {
                boolean missing=RailwayDecay.gap(seed,19,distance,2000);
                if(distance<80||distance>1920)assertFalse(missing);
                if(missing)gaps++;
            }
            assertTrue(gaps>=80&&gaps<250,"Damage must remain sparse and repairable");
        }
    }
    @Test void trackAndDamagePlansRemainIdenticalAfterNbtRoundTrip() {
        var curve=line();var restored=CurveRoute.fromNBT(curve.toNBT());
        assertEquals(RailwayDecay.identity(curve),RailwayDecay.identity(restored));
        for(int x=-1000;x<=1000;x++) {
            var track=TrackPutInfo.getByDir(new BlockPos(x,90,-20),new Vec3(1,0,0),null);
            assertEquals(RailwayDecay.keep(42,curve,track),RailwayDecay.keep(42,restored,TrackPutInfo.fromNBT(track.toNBT())));
        }
    }
    @Test void aCurveCannotConnectAcrossARecordedRepairGap() {
        var curve=line();var a=new Vec3(-1000,90,-20);
        var info=new TrackPutInfo.BezierInfo(a,new Vec3(1,0,0),new Vec3(2000,0,0),new Vec3(-1,0,0));
        var track=TrackPutInfo.getByDir(BlockPos.containing(a),new Vec3(1,0,0),info);
        assertFalse(RailwayDecay.keep(42,curve,track));
    }
    @Test void savedRegionRetainsDecayIdentityAndNeverRecreatesMissingTracks() {
        var map=new RailwayMap(new RegionPos(-1,0));map.abandoned=true;map.decaySeed=42;
        var curve=line();var chunk=new net.minecraft.world.level.ChunkPos(-10,0);
        map.routeMap.put(chunk,Set.of(curve));map.trackMap.put(chunk,new ArrayList<>());
        var restored=RailwayMap.fromNBT(map.toNBT());
        assertTrue(restored.abandoned);assertEquals(42,restored.decaySeed);
        assertTrue(restored.trackMap.get(chunk).isEmpty());
    }
}
