package com.hxzhitang.tongdarailway.mixin;

import com.hxzhitang.tongdarailway.railway.fixed.NoLoadGraphAccess;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.track.TrackPropagator;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/** Scope the native graph/associated signal traversal, restoring normal access in every exit path. */
@Mixin(TrackPropagator.class)
public abstract class TrackGraphTraversalMixin {
    @WrapMethod(method="onRailAdded")
    private static TrackGraph tongDaRailway$loadedGraph(LevelAccessor level,BlockPos pos,BlockState state,Operation<TrackGraph> original) {
        return NoLoadGraphAccess.within(level,pos,()->original.call(level,pos,state));
    }
    @WrapMethod(method="onRailRemoved")
    private static void tongDaRailway$loadedRemoval(LevelAccessor level,BlockPos pos,BlockState state,Operation<Void> original) {
        NoLoadGraphAccess.within(level,pos,()->{original.call(level,pos,state);return null;});
    }
}
