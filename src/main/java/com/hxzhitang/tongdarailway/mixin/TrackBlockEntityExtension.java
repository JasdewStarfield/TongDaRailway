package com.hxzhitang.tongdarailway.mixin;

import com.hxzhitang.tongdarailway.blocks.ITrackPreGenExtension;
import com.hxzhitang.tongdarailway.railway.fixed.FixedPlacementBudget;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.trains.track.*;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.*;

/** Persist native curve data before any tick; defer only live graph work to loaded server chunks. */
@Mixin(TrackBlockEntity.class)
public abstract class TrackBlockEntityExtension extends SmartBlockEntity implements ITrackPreGenExtension {
    @Shadow public abstract Map<BlockPos,BezierConnection> getConnections();
    @Unique private List<BlockPos> tongDaRailway$bootstrap=List.of();
    @Unique private boolean tongDaRailway$worldgenCurve;
    public TrackBlockEntityExtension(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}

    @Override public void addConnectionToPreGen(BezierConnection connection) {
        // addConnection() schedules live-world work and fake rails. Its native storage is safe to populate directly.
        getConnections().put(connection.getKey(),connection);
        tongDaRailway$worldgenCurve=true;
        queueGraphBootstrap(List.of(worldPosition));
    }
    @Override public void queueGraphBootstrap(List<BlockPos> positions) {
        var combined=new LinkedHashSet<>(tongDaRailway$bootstrap);combined.addAll(positions);
        tongDaRailway$bootstrap=List.copyOf(combined);
    }
    @Inject(method="write",at=@At("RETURN"))
    private void tongDaRailway$writeBootstrap(CompoundTag tag,HolderLookup.Provider registries,boolean packet,CallbackInfo ci) {
        if(!tongDaRailway$bootstrap.isEmpty())tag.putLongArray("EowRailBootstrap",tongDaRailway$bootstrap.stream().mapToLong(BlockPos::asLong).toArray());
        if(tongDaRailway$worldgenCurve)tag.putBoolean("EowWorldgenCurve",true);
    }
    @Inject(method="read",at=@At("RETURN"))
    private void tongDaRailway$readBootstrap(CompoundTag tag,HolderLookup.Provider registries,boolean packet,CallbackInfo ci) {
        tongDaRailway$bootstrap=Arrays.stream(tag.getLongArray("EowRailBootstrap")).mapToObj(BlockPos::of).toList();
        tongDaRailway$worldgenCurve=tag.getBoolean("EowWorldgenCurve");
    }
    @Unique private static boolean tongDaRailway$ready(ServerLevel world,BlockPos pos) {
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)
            if(world.getChunkSource().getChunkNow((pos.getX()>>4)+x,(pos.getZ()>>4)+z)==null)return false;
        return true;
    }
    @Unique private boolean tongDaRailway$connectionsReady(ServerLevel world) {
        if(!tongDaRailway$ready(world,worldPosition))return false;
        for(var target:getConnections().keySet())if(!tongDaRailway$ready(world,target))return false;
        return true;
    }
    @Inject(method="lazyTick",at=@At("HEAD"),cancellable=true)
    private void tongDaRailway$keepFakeRailsInLoadedChunks(CallbackInfo ci) {
        // Native fake-rail maintenance must not request chunks from a distant generated curve endpoint.
        if(tongDaRailway$worldgenCurve&&level instanceof ServerLevel world&&!tongDaRailway$connectionsReady(world))ci.cancel();
    }
    @Inject(method="tick",at=@At("RETURN"))
    private void tongDaRailway$activateGeneratedGraph(CallbackInfo ci) {
        if(tongDaRailway$bootstrap.isEmpty()||!(level instanceof ServerLevel world)||!tongDaRailway$connectionsReady(world))return;
        for(var pos:tongDaRailway$bootstrap) {
            var state=world.getBlockState(pos);
            if(state.is(AllBlocks.TRACK)&&state.getValue(TrackBlock.HAS_BE)&&world.getBlockEntity(pos) instanceof TrackBlockEntity rail)
                for(var target:rail.getConnections().keySet())if(!tongDaRailway$ready(world,target))return;
        }
        if(!FixedPlacementBudget.acquire(world,tongDaRailway$bootstrap.size()))return;
        var positions=tongDaRailway$bootstrap;tongDaRailway$bootstrap=List.of();setChanged();
        // Geometry and curves already exist. Native block ticks now discover them in Create's graph.
        for(var pos:positions)if(world.getBlockState(pos).is(AllBlocks.TRACK))world.scheduleTick(pos,AllBlocks.TRACK.get(),1);
        if(getConnections().isEmpty()&&world.getBlockState(worldPosition).is(AllBlocks.TRACK))
            world.setBlock(worldPosition,getBlockState().setValue(TrackBlock.HAS_BE,false),2);
    }
}
