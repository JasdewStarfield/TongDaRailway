package com.hxzhitang.tongdarailway.mixin.client;

import com.hxzhitang.tongdarailway.client.track.CurveRenderDistance;
import com.simibubi.create.content.trains.track.TrackBlockEntity;
import com.simibubi.create.content.trains.track.TrackRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** The fallback renderer owns long curves at one endpoint, even when the camera is beside their middle. */
@Mixin(value=TrackRenderer.class,remap=false)
public abstract class TrackCurveRenderDistanceMixin implements BlockEntityRenderer<TrackBlockEntity> {
    @Shadow public abstract int getViewDistance();

    @Override
    public boolean shouldRender(TrackBlockEntity track,Vec3 camera) {
        int distance=getViewDistance();
        if(Vec3.atCenterOf(track.getBlockPos()).distanceToSqr(camera)<(double)distance*distance)return true;
        // Only primary connections are drawn by this endpoint. Native cached bounds are cheap to reuse.
        // No chunk queries, world changes or unconditional visibility are needed for this client-only fix.
        for(var connection:track.getConnections().values())
            if(connection.isPrimary()&&CurveRenderDistance.nearBounds(camera,connection.getBounds(),distance))return true;
        return false;
    }
}
