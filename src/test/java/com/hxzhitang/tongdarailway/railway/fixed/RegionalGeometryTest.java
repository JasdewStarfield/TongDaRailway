package com.hxzhitang.tongdarailway.railway.fixed;
import com.hxzhitang.tongdarailway.railway.planner.RoutePlanner;
import com.hxzhitang.tongdarailway.util.CurveRoute;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RegionalGeometryTest {
    @Test void exactTrackRecordsAndResolvedCurveMouthsSurviveTheWorldSnapshot() throws Exception {
        var route=new RoutePlanner.ResultWay(new CurveRoute(),new ArrayList<>());
        route.addBezier(new Vec3(-932,87,1244),new Vec3(0,0,1),new Vec3(157,0,383),new Vec3(0,0,-1));
        var before=RegionalGeometry.write(List.of(route));var restored=RegionalGeometry.read(TagParser.parseTag(before.toString()));
        assertEquals(1,restored.size());assertEquals(before,RegionalGeometry.write(restored));
        assertTrue(restored.getFirst().trackPutInfos().stream().allMatch(NativeCurveSafety::valid));
    }
    @Test void anEmptySystemCanRemainEmptyWithoutAnotherSearch() throws Exception {
        assertTrue(RegionalGeometry.read(TagParser.parseTag(RegionalGeometry.write(List.of()).toString())).isEmpty());
    }
}
