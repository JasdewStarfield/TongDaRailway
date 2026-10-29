package com.hxzhitang.tongdarailway.railway.fixed;

import com.hxzhitang.tongdarailway.structure.TrackPutInfo;
import com.hxzhitang.tongdarailway.util.CurveRoute;
import net.minecraft.world.phys.Vec3;

/** Fixed intervals survive chunk order and reload. Damage is omitted from the saved track plan. */
public final class RailwayDecay {
    private RailwayDecay() { }

    public static long identity(CurveRoute route) {
        var segments=route.getSegments();
        Vec3 a=segments.getFirst().getPointAt(0),b=segments.getLast().getPointAt(1);
        return Double.doubleToLongBits(a.x*31+a.z)*31+Double.doubleToLongBits(b.x*31+b.z);
    }
    static long mix(long n) {
        n=(n^(n>>>30))*0xbf58476d1ce4e5b9L;
        n=(n^(n>>>27))*0x94d049bb133111ebL;
        return n^(n>>>31);
    }
    /** Keep the terminal approaches intact; every 320-block band owns one short repair site. */
    public static boolean gap(long seed,long route,double distance,double length) {
        if(distance<80||distance>length-80)return false;
        long band=(long)Math.floor(distance/320);
        long hash=mix(seed^route^mix(band));
        double start=band*320+96+Math.floorMod(hash,96);
        return distance>=start&&distance<start+18+Math.floorMod(hash>>>8,9);
    }
    /** Every third repair site can lose its central roadbed or bridge span as well as rails. */
    public static boolean major(long seed,CurveRoute route,double distance) {
        return gap(seed,identity(route),distance,route.getTotalLength())
                &&Math.floorMod(mix(seed^identity(route)^((long)Math.floor(distance/320))),3)==0;
    }
    public static boolean keep(long seed,CurveRoute route,TrackPutInfo track) {
        long id=identity(route);double length=route.getTotalLength();
        if(track.bezier()==null) {
            double d=route.getFrame(Vec3.atCenterOf(track.pos())).globalT*length;
            return !gap(seed,id,d,length);
        }
        var info=track.bezier();
        // Sample the cubic, not just its endpoints: an intact Bezier must not bridge a damaged interval.
        Vec3 a=info.start(),d=a.add(info.endOffset());
        // Reuse the original planner's actual cubic geometry for both signs and control distances.
        var segment=CurveRoute.BezierSegment.getCubicBezier(a,info.startAxis(),info.endOffset(),info.endAxis());
        int count=Math.max(8,(int)Math.ceil(info.endOffset().length()/3));
        for(int i=0;i<=count;i++) {
            double t=i/(double)count;
            var p=segment.getPointAt(t);
            if(gap(seed,id,route.getFrame(p).globalT*length,length))return false;
        }
        return true;
    }
}
