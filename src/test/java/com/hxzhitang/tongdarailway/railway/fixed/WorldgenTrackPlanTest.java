package com.hxzhitang.tongdarailway.railway.fixed;

import com.hxzhitang.tongdarailway.structure.TrackPutInfo;
import com.simibubi.create.content.trains.track.TrackShape;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WorldgenTrackPlanTest {
    private TrackPutInfo curve() {
        return new TrackPutInfo(new BlockPos(-1,95,-1),TrackShape.XO,
                new TrackPutInfo.BezierInfo(new Vec3(-1,95,-1),new Vec3(1,0,0),new Vec3(40,2,40),new Vec3(0,0,-1)),TrackShape.ZO);
    }
    @Test void assignsBothEndsAcrossNegativeChunkAndPlanningRegionBoundary() {
        var track=curve();var plan=new WorldgenTrackPlan(List.of(track));
        var first=plan.inChunk(new ChunkPos(-1,-1));var second=plan.inChunk(new ChunkPos(2,2));
        assertEquals(1,first.size());assertEquals(1,second.size());
        assertTrue(first.getFirst().curves().getFirst().primary());
        assertFalse(second.getFirst().curves().getFirst().primary());
        assertEquals(new BlockPos(39,97,39),second.getFirst().pos());
        assertTrue(plan.inChunk(new ChunkPos(0,0)).isEmpty());
    }
    @Test void straightRecordAtCurveEndKeepsTheCurveAndDoesNotDuplicateTheBlock() {
        var curve=curve();var straight=new TrackPutInfo(curve.pos(),TrackShape.XO,null,null);
        for(var records:List.of(List.of(curve,straight),List.of(straight,curve))) {
            var endpoints=new WorldgenTrackPlan(records).inChunk(new ChunkPos(-1,-1));
            assertEquals(1,endpoints.size());assertEquals(1,endpoints.getFirst().curves().size());
        }
    }
    @Test void nativeRecordRoundTripKeepsEndpointOwnershipAndImmutableResults() {
        var track=TrackPutInfo.fromNBT(curve().toNBT());
        var endpoints=new WorldgenTrackPlan(List.of(track)).inChunk(new ChunkPos(2,2));
        assertEquals(TrackShape.ZO,endpoints.getFirst().shape());
        assertThrows(UnsupportedOperationException.class,()->endpoints.clear());
        assertThrows(UnsupportedOperationException.class,()->endpoints.getFirst().curves().clear());
    }
}
