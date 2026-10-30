package com.hxzhitang.tongdarailway.railway.fixed;

import com.hxzhitang.tongdarailway.structure.TrackPutInfo;
import com.simibubi.create.content.trains.track.*;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** An immutable endpoint index: generation never needs to write or read a remote chunk. */
public final class WorldgenTrackPlan {
    public record CurveEnd(TrackPutInfo track, boolean primary) {
        public BezierConnection createConnection() {
            var b=track.bezier();var start=track.pos();var end=start.offset(BlockPos.containing(b.endOffset()));
            var connection=new BezierConnection(Couple.create(start,end),
                    Couple.create(b.start().add(edge(b.startAxis())),b.start().add(b.endOffset()).add(edge(b.endAxis()))),
                    Couple.create(b.startAxis(),b.endAxis()),Couple.create(new Vec3(0,1,0),new Vec3(0,1,0)),
                    true,false,TrackMaterial.ANDESITE);
            // Each chunk/preview receives its own Create connection object, including its lazy geometry caches.
            return primary?connection:connection.secondary();
        }
    }
    public record Endpoint(BlockPos pos,TrackShape shape,List<CurveEnd> curves) {}
    private final Map<ChunkPos,List<Endpoint>> chunks;
    public WorldgenTrackPlan(List<TrackPutInfo> tracks) {
        var positions=new LinkedHashMap<BlockPos,Endpoint>();
        for(var track:tracks) {
            merge(positions,track.pos(),track.shape(),track.bezier()==null?null:new CurveEnd(track,true));
            if(track.bezier()!=null)merge(positions,track.pos().offset(BlockPos.containing(track.bezier().endOffset())),
                    track.endShape(),new CurveEnd(track,false));
        }
        var indexed=new HashMap<ChunkPos,List<Endpoint>>();
        for(var e:positions.values())indexed.computeIfAbsent(new ChunkPos(e.pos()),k->new ArrayList<>()).add(e);
        var frozen=new HashMap<ChunkPos,List<Endpoint>>();
        indexed.forEach((key,value)->frozen.put(key,List.copyOf(value)));chunks=Map.copyOf(frozen);
    }
    public WorldgenTrackPlan(List<TrackPutInfo> tracks,List<com.hxzhitang.tongdarailway.railway.planner.StationPlanner.StationGenInfo> stations) {
        this(withStationTracks(tracks,stations));
    }
    private static List<TrackPutInfo> withStationTracks(List<TrackPutInfo> tracks,List<com.hxzhitang.tongdarailway.railway.planner.StationPlanner.StationGenInfo> stations) {
        var result=new ArrayList<>(tracks);
        for(var station:stations) {
            var template=station.stationTemplate();var box=template.getBeardifierBox(station.placePos());var center=station.placePos().getCenter();
            for(var p:BlockPos.betweenClosed(box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ())) {
                var state=template.getBlockState(Vec3.atLowerCornerOf(p).add(-0.5,-0.5,-0.5).subtract(center));
                if(state!=null&&state.getBlock() instanceof TrackBlock)result.add(new TrackPutInfo(p.immutable(),state.getValue(TrackBlock.SHAPE),null,null));
            }
        }
        return result;
    }
    public List<Endpoint> inChunk(ChunkPos chunk){return chunks.getOrDefault(chunk,List.of());}
    private static void merge(Map<BlockPos,Endpoint> map,BlockPos pos,TrackShape shape,CurveEnd curve) {
        var previous=map.get(pos);var curves=new ArrayList<CurveEnd>();
        if(previous!=null)curves.addAll(previous.curves());
        if(curve!=null&&!curves.contains(curve))curves.add(curve);
        map.put(pos,new Endpoint(pos,shape,List.copyOf(curves)));
    }
    private static Vec3 edge(Vec3 axis) {
        return new Vec3(Math.abs(axis.x)<1e-6?0.5:axis.x<0?0:1,0,Math.abs(axis.z)<1e-6?0.5:axis.z<0?0:1);
    }
}
