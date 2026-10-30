package com.hxzhitang.tongdarailway.railway.fixed;

import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Grid-aligned native junctions keep nearby rail geometry attached to nearby endpoint chunks. */
public final class CompactTrackSegments {
    public static final double MAX_CURVE_SPAN=96;
    public record Piece(Vec3 start,Vec3 axis,Vec3 end,Vec3 endAxis) {}
    private CompactTrackSegments() {}
    public static List<Piece> split(Vec3 start,Vec3 axis,Vec3 end,Vec3 endAxis) {
        var delta=end.subtract(start);var a=grid(axis);var backwards=grid(endAxis);
        if(a.lengthSqr()==0||a.normalize().dot(backwards.normalize())>-.999999)return List.of();
        // Straight grades can use several native, horizontal-tangent grade connectors.
        // Every endpoint stays on the integer rail grid; no arbitrary curve tangent becomes a track block.
        var flat=delta.multiply(1,0,1);
        if(flat.lengthSqr()>0&&flat.normalize().dot(a.normalize())>.999999&&delta.y!=0) {
            int steps=(int)Math.round(flat.length()/a.length());int count=(int)Math.ceil(delta.length()/80);
            var result=new ArrayList<Piece>();var previous=start;
            for(int i=1;i<=count;i++) {
                int along=(int)Math.round(steps*i/(double)count);
                var next=start.add(a.scale(along)).add(0,Math.round(delta.y*i/(double)count),0);
                result.add(new Piece(previous,a,next,a.reverse()));previous=next;
            }
            return List.copyOf(result);
        }
        if(delta.y!=0)return List.of();
        // A wide S bend is two short 45-degree turns with a real diagonal/cardinal track between them.
        for(var d:List.of(new Vec3(1,0,0),new Vec3(1,0,1),new Vec3(0,0,1),new Vec3(-1,0,1),
                new Vec3(-1,0,0),new Vec3(-1,0,-1),new Vec3(0,0,-1),new Vec3(1,0,-1))) {
            double dot=a.normalize().dot(d.normalize());if(dot<.70||dot>.72)continue;
            double det=a.x*d.z-a.z*d.x;
            double along=(delta.x*d.z-delta.z*d.x)/det;
            double diagonal=(a.x*delta.z-a.z*delta.x)/det;
            int radius=(int)Math.min(12,Math.floor(Math.min(along,diagonal)/2));
            if(radius<8||Math.abs(along-Math.rint(along))>1e-5||Math.abs(diagonal-Math.rint(diagonal))>1e-5)continue;
            int prefix=(int)Math.floor((along-2*radius)/2);
            var p1=start.add(a.scale(prefix));var p2=p1.add(a.add(d).scale(radius));
            var p3=p2.add(d.scale(diagonal-2*radius));var p4=p3.add(a.add(d).scale(radius));
            return List.of(new Piece(start,a,p1,a.reverse()),new Piece(p1,a,p2,d.reverse()),
                    new Piece(p2,d,p3,d.reverse()),new Piece(p3,d,p4,a.reverse()),new Piece(p4,a,end,a.reverse()));
        }
        return List.of();
    }
    private static Vec3 grid(Vec3 axis) {
        var n=axis.multiply(1,0,1).normalize();return new Vec3(Math.round(n.x),0,Math.round(n.z));
    }
}
