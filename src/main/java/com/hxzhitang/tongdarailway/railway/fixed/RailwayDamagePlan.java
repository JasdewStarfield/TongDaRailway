package com.hxzhitang.tongdarailway.railway.fixed;

import com.hxzhitang.tongdarailway.structure.TrackPutInfo;
import com.hxzhitang.tongdarailway.util.CurveRoute;
import net.minecraft.nbt.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** The same saved arc intervals control visible ruins and omitted native rail geometry. */
public final class RailwayDamagePlan {
    public record Interval(double from,double to) {
        public boolean overlaps(Interval other){return from<other.to-0.01&&to>other.from+0.01;}
    }
    private final List<Interval> intervals;
    private final Map<TrackPutInfo,Interval> footprints=new java.util.concurrent.ConcurrentHashMap<>();
    private RailwayDamagePlan(List<Interval> intervals){this.intervals=List.copyOf(intervals);}
    public List<Interval> intervals(){return intervals;}
    public boolean contains(double distance){return intervals.stream().anyMatch(i->distance>=i.from&&distance<=i.to);}
    /** Full-width bridge loss shares the saved rail scar. The central track gap stays exact,
     * while each half-block lateral strip leaves a different short, attached broken edge. */
    public boolean bridgeMissing(double distance,double lateral,long seed) {
        for(var interval:intervals) {
            double left=0,right=0;
            if(Math.abs(lateral)>=1.25) {
                long hash=RailwayDecay.mix(seed^Double.doubleToLongBits(interval.from())
                        ^RailwayDecay.mix((long)Math.floor(lateral*2)));
                left=1+Math.floorMod(hash,4);right=1+Math.floorMod(hash>>>8,4);
            }
            if(distance>=interval.from()+left&&distance<=interval.to()-right)return true;
        }
        return false;
    }
    public boolean keep(CurveRoute route,TrackPutInfo track) {
        if(track.bezier()==null)return !contains(route.getFrame(Vec3.atCenterOf(track.pos())).globalT*route.getTotalLength());
        var span=footprints.computeIfAbsent(track,t->footprint(route,t));
        return intervals.stream().noneMatch(i->i.overlaps(span));
    }
    public static RailwayDamagePlan build(long seed,CurveRoute route,List<TrackPutInfo> tracks) {
        var spans=new LinkedHashMap<TrackPutInfo,Interval>();
        for(var track:tracks)if(track.bezier()!=null)spans.put(track,footprint(route,track));
        var result=new ArrayList<Interval>();double length=route.getTotalLength();long id=RailwayDecay.identity(route);
        for(int band=0;band*320<length;band++) {
            long hash=RailwayDecay.mix(seed^id^RailwayDecay.mix(band));
            double target=band*320+96+Math.floorMod(hash,96),width=18+Math.floorMod(hash>>>8,9);
            for(int shift:new int[]{0,-32,32,-64,64,-96,96}) {
                var candidate=new Interval(target+shift,target+shift+width);
                // A small scar can consume a short curve, but never a long healthy approach.
                for(int pass=0;pass<4;pass++)for(var span:spans.values())if(candidate.overlaps(span)&&span.to-span.from<=40)
                    candidate=new Interval(Math.min(candidate.from,span.from),Math.max(candidate.to,span.to));
                var chosen=candidate;
                if(chosen.from<80||chosen.to>length-80||chosen.to-chosen.from>40)continue;
                if(spans.values().stream().anyMatch(s->s.to-s.from>40&&s.overlaps(chosen)))continue;
                if(result.stream().anyMatch(i->new Interval(i.from-24,i.to+24).overlaps(chosen)))continue;
                result.add(chosen);break;
            }
        }
        result.sort(Comparator.comparingDouble(Interval::from));var plan=new RailwayDamagePlan(result);plan.footprints.putAll(spans);return plan;
    }
    private static Interval footprint(CurveRoute route,TrackPutInfo track) {
        var b=track.bezier();var curve=CurveRoute.BezierSegment.getCubicBezier(b.start(),b.startAxis(),b.endOffset(),b.endAxis());
        int count=Math.max(8,(int)Math.ceil(b.endOffset().length()/3));double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
        for(int i=0;i<=count;i++) {
            double distance=route.getFrame(curve.getPointAt(i/(double)count)).globalT*route.getTotalLength();
            min=Math.min(min,distance);max=Math.max(max,distance);
        }
        return new Interval(min,max);
    }
    public ListTag toNBT() {
        var tag=new ListTag();for(var interval:intervals){var value=new CompoundTag();value.putDouble("From",interval.from);value.putDouble("To",interval.to);tag.add(value);}return tag;
    }
    public static RailwayDamagePlan fromNBT(ListTag tag) {
        var result=new ArrayList<Interval>();for(var item:tag){var value=(CompoundTag)item;result.add(new Interval(value.getDouble("From"),value.getDouble("To")));}return new RailwayDamagePlan(result);
    }
}
