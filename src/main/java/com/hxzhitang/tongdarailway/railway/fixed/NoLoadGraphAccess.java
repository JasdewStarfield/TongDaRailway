package com.hxzhitang.tongdarailway.railway.fixed;

import com.simibubi.create.AllBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import java.util.*;
import java.util.function.Supplier;

/** Native graph discovery may inspect loaded rails, but must never generate its remote frontier. */
public final class NoLoadGraphAccess {
    private record Context(ServerLevel world,BlockPos anchor) {}
    private record Target(ResourceKey<Level> dimension,long chunk) {}
    private static final ThreadLocal<Context> ACTIVE=new ThreadLocal<>();
    private static final Map<ServerLevel,FrontierRetryQueue<Target,BlockPos>> PENDING=new WeakHashMap<>();
    private static long deferredReads,replayedFrontiers;
    private NoLoadGraphAccess() {}
    public static <T>T within(LevelAccessor level,BlockPos anchor,Supplier<T> action) {
        if(!FixedRailwayNetwork.enabled()||!(level instanceof ServerLevel world))return action.get();
        var old=ACTIVE.get();ACTIVE.set(new Context(world,anchor.immutable()));
        try{return action.get();}finally{if(old==null)ACTIVE.remove();else ACTIVE.set(old);}
    }
    public static boolean unavailable(ServerLevel world,BlockPos pos) {
        var context=ACTIVE.get();if(context==null)return false;
        if(world.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null)return false;
        deferredReads++;
        PENDING.computeIfAbsent(context.world(),k->new FrontierRetryQueue<>())
                .add(new Target(world.dimension(),new ChunkPos(pos).toLong()),context.anchor());
        return true;
    }
    public static void tick(net.minecraft.server.MinecraftServer server) {
        // A future loaded frontier replays the native rail tick, which merges the partial graphs.
        for(var world:server.getAllLevels()) {
            var queue=PENDING.get(world);if(queue==null)continue;
            queue.poll(8,(target,anchor)->{
                var remote=server.getLevel(target.dimension());var cp=new ChunkPos(target.chunk());
                if(remote==null||remote.getChunkSource().getChunkNow(cp.x,cp.z)==null)return false;
                if(world.getChunkSource().getChunkNow(anchor.getX()>>4,anchor.getZ()>>4)==null)return false;
                return FixedPlacementBudget.acquire(world,1);
            },(target,anchor)->{
                if(world.getBlockState(anchor).is(AllBlocks.TRACK)){world.scheduleTick(anchor,AllBlocks.TRACK.get(),1);replayedFrontiers++;}
            });
        }
    }
    public static long deferredReads(){return deferredReads;}
    public static long replayedFrontiers(){return replayedFrontiers;}
    public static void clear(){PENDING.clear();ACTIVE.remove();deferredReads=0;replayedFrontiers=0;}
}
