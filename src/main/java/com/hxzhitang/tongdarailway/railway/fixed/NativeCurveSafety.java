package com.hxzhitang.tongdarailway.railway.fixed;

import com.hxzhitang.tongdarailway.structure.TrackPutInfo;
import com.hxzhitang.tongdarailway.util.CurveRoute;

/** Reject folded/tiny Beziers even when the upstream path builder has produced endpoint data. */
public final class NativeCurveSafety {
    private NativeCurveSafety() {}
    public static boolean valid(TrackPutInfo track) {
        var b=track.bezier();if(b==null)return true;
        // Reject unconvertible long curves before publication, so a route can retry its layout.
        if(b.endOffset().length()>CompactTrackSegments.MAX_CURVE_SPAN+1e-6)return false;
        var chord=b.endOffset().multiply(1,0,1);if(chord.lengthSqr()<1)return false;
        var a=b.startAxis().multiply(1,0,1).normalize();var end=b.endAxis().multiply(1,0,1).normalize();
        // A single native curve cannot turn back into an identically facing station mouth.
        if(a.dot(end)>.999||a.dot(chord)<-1e-6||end.dot(chord)>1e-6)return false;
        var curve=CurveRoute.BezierSegment.getCubicBezier(b.start(),b.startAxis(),b.endOffset(),b.endAxis());
        for(int step=0;step<=64;step++) {
            double t=step/64.0,u=1-t;
            var d=curve.p1.subtract(curve.p0).scale(3*u*u).add(curve.p2.subtract(curve.p1).scale(6*u*t)).add(curve.p3.subtract(curve.p2).scale(3*t*t));
            var dd=curve.p2.subtract(curve.p1.scale(2)).add(curve.p0).scale(6*u).add(curve.p3.subtract(curve.p2.scale(2)).add(curve.p1).scale(6*t));
            if(d.dot(chord)<-1e-6)return false;
            double cross=Math.abs(d.x*dd.z-d.z*dd.x),speed2=d.x*d.x+d.z*d.z;
            if(cross>1e-8&&Math.pow(speed2,1.5)/cross<6.5)return false;
        }
        return true;
    }
}
