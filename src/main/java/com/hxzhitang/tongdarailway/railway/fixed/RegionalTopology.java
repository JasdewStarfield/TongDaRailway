package com.hxzhitang.tongdarailway.railway.fixed;

import com.hxzhitang.tongdarailway.railway.RegionPos;
import net.minecraft.core.BlockPos;
import java.util.*;

/** Seed-only regional membership never needs terrain, neighboring plans, or generated chunks. */
public final class RegionalTopology {
    public static final int SIZE=4096;
    public record Unit(int x,int z,int width,int depth) {
        public String id(){return "r_"+x+"_"+z+"_"+width+"_"+depth;}
        public List<RegionPos> cells(){var result=new ArrayList<RegionPos>();for(int dx=0;dx<width;dx++)for(int dz=0;dz<depth;dz++)result.add(new RegionPos(x+dx,z+dz));return List.copyOf(result);}
        public boolean contains(int px,int pz,int margin){return px>=x*SIZE+margin&&px<(x+width)*SIZE-margin&&pz>=z*SIZE+margin&&pz<(z+depth)*SIZE-margin;}
        public BlockPos clamp(BlockPos p,int margin){return new BlockPos(Math.max(x*SIZE+margin,Math.min((x+width)*SIZE-margin-1,p.getX())),p.getY(),Math.max(z*SIZE+margin,Math.min((z+depth)*SIZE-margin-1,p.getZ())));}
    }
    private RegionalTopology() {}
    public static RegionPos region(BlockPos p){return new RegionPos(Math.floorDiv(p.getX(),SIZE),Math.floorDiv(p.getZ(),SIZE));}
    public static long key(long seed,int x,int z,long salt) {
        long value=seed^((long)x<<32)^(z&0xffffffffL)^salt;
        value=(value^(value>>>30))*0xbf58476d1ce4e5b9L;value=(value^(value>>>27))*0x94d049bb133111ebL;
        return value^(value>>>31);
    }
    private static double roll(long seed,int x,int z,long salt){return (key(seed,x,z,salt)>>>11)*0x1.0p-53;}
    /** A shared nine-cell owner keeps long systems independent of exploration order. */
    public static boolean active(long seed,RegionPos r,Set<RegionPos> critical,double probability) {
        var owner=unit(seed,r,critical,probability,0);
        return critical.stream().anyMatch(p->owner.cells().contains(p))
                ||roll(seed,owner.x(),owner.z(),0x454f574c4f4e4753L)<probability;
    }
    public static Unit unit(long seed,RegionPos r,Set<RegionPos> critical,double probability,double pairing) {
        return new Unit(Math.floorDiv(r.x(),3)*3,Math.floorDiv(r.z(),3)*3,3,3);
    }
}
