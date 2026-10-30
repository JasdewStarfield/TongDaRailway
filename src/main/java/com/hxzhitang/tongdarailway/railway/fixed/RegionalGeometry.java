package com.hxzhitang.tongdarailway.railway.fixed;

import com.hxzhitang.tongdarailway.railway.planner.RoutePlanner;
import com.hxzhitang.tongdarailway.structure.TrackPutInfo;
import com.hxzhitang.tongdarailway.util.CurveRoute;
import net.minecraft.nbt.*;
import java.util.*;

/** Save resolved mouths and geometry together; later exploration must not reassign an existing junction. */
public final class RegionalGeometry {
    private RegionalGeometry() {}
    public static CompoundTag write(List<RoutePlanner.ResultWay> routes) {
        var root=new CompoundTag();var rows=new ListTag();
        for(var route:routes){var row=new CompoundTag();row.put("Way",route.way().toNBT());var tracks=new ListTag();for(var track:route.trackPutInfos())tracks.add(track.toNBT());row.put("Tracks",tracks);rows.add(row);}
        root.put("Routes",rows);return root;
    }
    public static List<RoutePlanner.ResultWay> read(CompoundTag root) {
        var result=new ArrayList<RoutePlanner.ResultWay>();
        for(var item:root.getList("Routes",Tag.TAG_COMPOUND)) {
            var row=(CompoundTag)item;var tracks=new ArrayList<TrackPutInfo>();
            for(var track:row.getList("Tracks",Tag.TAG_COMPOUND))tracks.add(TrackPutInfo.fromNBT((CompoundTag)track));
            result.add(new RoutePlanner.ResultWay(CurveRoute.fromNBT(row.getList("Way",Tag.TAG_LIST)),List.copyOf(tracks)));
        }
        return List.copyOf(result);
    }
}
