package com.hxzhitang.tongdarailway.railway.fixed;

import com.hxzhitang.tongdarailway.railway.planner.RoutePlanner;
import com.hxzhitang.tongdarailway.util.CurveRoute;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompactTrackSegmentsTest {
    @Test void reportedWideSCurveBecomesShortTurnsAndRealDiagonalTrack() {
        var route=new RoutePlanner.ResultWay(new CurveRoute(),new ArrayList<>());
        route.addBezier(new Vec3(-932,87,1244),new Vec3(0,0,1),new Vec3(157,0,383),new Vec3(0,0,-1));
        assertTrue(route.trackPutInfos().stream().filter(t->t.bezier()==null).count()>200);
        assertEquals(2,route.trackPutInfos().stream().filter(t->t.bezier()!=null).count());
        assertTrue(route.trackPutInfos().stream().allMatch(NativeCurveSafety::valid));
    }
    @Test void diagonalInputAlsoSplitsOntoTheEightDirectionGrid() {
        var pieces=CompactTrackSegments.split(Vec3.ZERO,new Vec3(1,0,1),new Vec3(350,0,150),new Vec3(-1,0,-1));
        assertFalse(pieces.isEmpty());assertEquals(new Vec3(350,0,150),pieces.getLast().end());
        for(int i=1;i<pieces.size();i++)assertEquals(pieces.get(i-1).end(),pieces.get(i).start());
    }
    @Test void longGradesHaveShortConnectedIntegerEndpoints() {
        var route=new RoutePlanner.ResultWay(new CurveRoute(),new ArrayList<>());
        route.addBezier(new Vec3(0,90,0),new Vec3(1,0,1),new Vec3(320,-18,320),new Vec3(-1,0,-1));
        assertTrue(route.trackPutInfos().size()>4);assertTrue(route.trackPutInfos().stream().allMatch(NativeCurveSafety::valid));
        for(var track:route.trackPutInfos())if(track.bezier()!=null)assertTrue(track.bezier().endOffset().length()<=96);
        var last=route.trackPutInfos().getLast().bezier();assertEquals(new Vec3(320,72,320),last.start().add(last.endOffset()));
    }
    @Test void gradeSplittingAlwaysMakesProgressEvenForAnUnsuitableSteepInput() {
        var pieces=CompactTrackSegments.split(Vec3.ZERO,new Vec3(1,0,0),new Vec3(80,60,0),new Vec3(-1,0,0));
        assertEquals(2,pieces.size());for(var piece:pieces)assertTrue(piece.end().distanceTo(piece.start())<96);
    }
    @Test void diagonalStraightTrackIsRepresentedByActualBlocks() {
        var route=new RoutePlanner.ResultWay(new CurveRoute(),new ArrayList<>());
        route.addBezier(Vec3.ZERO,new Vec3(1,0,1),new Vec3(320,0,320),new Vec3(-1,0,-1));
        assertEquals(321,route.trackPutInfos().size());assertTrue(route.trackPutInfos().stream().allMatch(t->t.bezier()==null));
    }
}
