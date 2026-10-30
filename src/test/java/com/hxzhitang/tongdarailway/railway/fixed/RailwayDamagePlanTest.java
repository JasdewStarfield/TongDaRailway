package com.hxzhitang.tongdarailway.railway.fixed;

import com.hxzhitang.tongdarailway.structure.TrackPutInfo;
import com.hxzhitang.tongdarailway.util.CurveRoute;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RailwayDamagePlanTest {
    private static CurveRoute line(){var route=new CurveRoute();route.addSegment(new CurveRoute.LineSegment(new Vec3(0,90,0),new Vec3(2000,90,0)));return route;}
    private static TrackPutInfo curve(double start,int length) {
        var a=new Vec3(start,90,0);var axis=new Vec3(1,0,0);
        return TrackPutInfo.getByDir(BlockPos.containing(a),axis,new TrackPutInfo.BezierInfo(a,axis,new Vec3(length,0,0),axis.reverse()));
    }
    @Test void aLongCurveCannotCreateHundredsOfBlocksOfHealthyUnrailedRoadbed() {
        var route=line();var longCurve=curve(0,600);var plan=RailwayDamagePlan.build(42,route,List.of(longCurve));
        assertTrue(plan.keep(route,longCurve));assertFalse(plan.intervals().isEmpty());
        for(var scar:plan.intervals()){assertTrue(scar.from()>=600);assertTrue(scar.to()-scar.from()<=40);}
    }
    @Test void aRemovedShortCurveHasItsEntireFootprintInsideThePhysicalScar() {
        var route=line();var nominal=RailwayDamagePlan.build(42,route,List.of()).intervals().getFirst();
        int start=(int)Math.floor(nominal.from())-2;var shortCurve=curve(start,30);
        var plan=RailwayDamagePlan.build(42,route,List.of(shortCurve));assertFalse(plan.keep(route,shortCurve));
        assertTrue(plan.intervals().stream().anyMatch(i->i.from()<=start+0.1&&i.to()>=start+29.9));
    }
    @Test void roadbedAndTrackUseTheSameSavedDamageAfterReload() {
        var route=line();var plan=RailwayDamagePlan.build(42,route,List.of());var map=new com.hxzhitang.tongdarailway.railway.RailwayMap(new com.hxzhitang.tongdarailway.railway.RegionPos(0,0));
        map.damagePlans.put(RailwayDecay.identity(route),plan);
        var restored=com.hxzhitang.tongdarailway.railway.RailwayMap.fromNBT(map.toNBT()).damagePlans.get(RailwayDecay.identity(route));
        assertEquals(plan.intervals(),restored.intervals());
        for(int x=0;x<=2000;x++) {
            var track=TrackPutInfo.getByDir(new BlockPos(x,90,0),new Vec3(1,0,0),null);
            assertEquals(plan.keep(route,track),restored.keep(route,track));
            double d=route.getFrame(Vec3.atCenterOf(track.pos())).globalT*route.getTotalLength();
            assertEquals(!restored.contains(d),restored.keep(route,track));
        }
    }
}
