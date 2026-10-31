package com.hxzhitang.tongdarailway.railway.fixed;

/** Small material rules keep natural ice walls without changing track height or rock tunnels. */
public final class RailwayTerrainPolicy {
    public enum CapCell { EMPTY, FROZEN, OTHER }
    private RailwayTerrainPolicy(){}
    /** Clear a whole exposed frozen cap, stopping at any non-snow material; never erase higher rock. */
    public static int clearCap(int first,int upper,java.util.function.IntFunction<CapCell> sample,java.util.function.IntConsumer remove) {
        int count=0;
        for(int y=first;y<upper;y++) {
            var cell=sample.apply(y);
            if(cell==CapCell.FROZEN){remove.accept(y);count++;}
            else if(cell!=CapCell.EMPTY)break;
        }
        return count;
    }
    /** The core opens fully; its two-block fringe only loses narrow exposed remnants. */
    public static boolean trimIce(double lateral,double core,boolean exposedOutside) {
        return Math.abs(lateral)<=core||(Math.abs(lateral)<=core+2&&exposedOutside);
    }
    public static boolean naturalIce(int frozenSamples,int rockSamples){return frozenSamples>0&&rockSamples==0;}
    public static boolean placeTunnelVoxel(boolean naturalIce,boolean air,double height){return !naturalIce||air||height<=0.25;}
}
