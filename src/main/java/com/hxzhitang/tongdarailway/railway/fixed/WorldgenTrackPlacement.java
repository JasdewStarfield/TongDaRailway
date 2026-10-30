package com.hxzhitang.tongdarailway.railway.fixed;

import com.hxzhitang.tongdarailway.blocks.ITrackPreGenExtension;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.trains.track.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import java.util.*;

/** Place static geometry now; only Create's live network bootstrap waits for safe server ticking. */
public final class WorldgenTrackPlacement {
    private WorldgenTrackPlacement() {}
    public static void place(WorldGenLevel world,ChunkPos owner,WorldgenTrackPlan plan) {
        var endpoints=plan.inChunk(owner);
        if(endpoints.isEmpty())return;
        var positions=new ArrayList<BlockPos>();TrackBlockEntity bootstrap=null;
        var chunk=world.getChunk(owner.x,owner.z);
        for(var endpoint:endpoints) {
            if(FixedRailwayNetwork.protects(endpoint.pos()))continue;
            // The first local rail holds a saved bootstrap list. It renders as an ordinary rail.
            boolean hasEntity=!endpoint.curves().isEmpty()||bootstrap==null;
            var state=AllBlocks.TRACK.getDefaultState().setValue(TrackBlock.SHAPE,endpoint.shape()).setValue(TrackBlock.HAS_BE,hasEntity);
            world.setBlock(endpoint.pos(),state,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
            positions.add(endpoint.pos());
            if(hasEntity) {
                var raw=chunk.getBlockEntity(endpoint.pos());
                if(raw==null)raw=world.getBlockEntity(endpoint.pos());
                if(raw==null) {
                    // DH deliberately hides ordinary block entities through WorldGenLevel. Store the native
                    // entity in this owned chunk directly; no ticker or live Level is attached during preview.
                    raw=((EntityBlock)state.getBlock()).newBlockEntity(endpoint.pos(),state);
                    if(raw!=null)chunk.setBlockEntity(raw);
                }
                if(!(raw instanceof TrackBlockEntity entity))throw new IllegalStateException("Missing generated track entity at "+endpoint.pos());
                for(var curve:endpoint.curves())((ITrackPreGenExtension)entity).addConnectionToPreGen(curve.createConnection());
                if(bootstrap==null)bootstrap=entity;
            }
        }
        if(bootstrap!=null)((ITrackPreGenExtension)bootstrap).queueGraphBootstrap(positions);
    }
}
