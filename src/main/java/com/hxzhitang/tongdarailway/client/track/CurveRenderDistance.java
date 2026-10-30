package com.hxzhitang.tongdarailway.client.track;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Keep the native distance limit, measured from the visible curve rather than its owning endpoint. */
public final class CurveRenderDistance {
    private CurveRenderDistance() {}
    public static boolean nearBounds(Vec3 camera,AABB bounds,double distance) {
        // Create caches a world-space box enclosing every segment, including the rail width.
        // Squared distance avoids per-frame square roots and leaves far-away curves culled.
        double x=Math.max(0,Math.max(bounds.minX-camera.x,camera.x-bounds.maxX));
        double y=Math.max(0,Math.max(bounds.minY-camera.y,camera.y-bounds.maxY));
        double z=Math.max(0,Math.max(bounds.minZ-camera.z,camera.z-bounds.maxZ));
        return x*x+y*y+z*z<distance*distance;
    }
}
