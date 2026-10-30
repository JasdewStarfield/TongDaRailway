package com.hxzhitang.tongdarailway.railway.snow;

import com.simibubi.create.content.trains.track.TrackShape;

/** Pure geometry/environment rules shared by the client mesh builder and focused tests. */
public final class SnowOverlayPolicy {
    // Match native one-layer snow exactly; the separate sleeper correction provides clearance.
    public static final double THIN_SNOW=1.0/8;
    private SnowOverlayPolicy() {}
    public static boolean supports(TrackShape shape){return shape!=TrackShape.NONE&&!shape.isPortal();}
    public static boolean snowIdentifier(String id,int layers) {
        if(id.equals("minecraft:snow_block"))return true;
        return (id.equals("minecraft:snow")||id.startsWith("snowrealmagic:"))&&layers>0;
    }
    // A pair of nearby snow cells establishes a snowy surface; empty space and water never qualify.
    public static boolean canCover(boolean supported,boolean waterlogged,int snowNeighbours) {
        return supported&&!waterlogged&&snowNeighbours>=2;
    }
}
