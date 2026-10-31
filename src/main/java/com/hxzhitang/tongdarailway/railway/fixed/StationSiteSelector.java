package com.hxzhitang.tongdarailway.railway.fixed;

import net.minecraft.core.BlockPos;
import java.util.*;
import java.util.function.*;

/** Finite terrain-only search: no candidate ever asks the server to generate a chunk. */
public final class StationSiteSelector {
    public record Site(BlockPos rail,int minHeight,int maxHeight,int fill,int cut,double score) {}
    private StationSiteSelector() {}
    public static Site select(BlockPos preferred,int width,int depth,int seaLevel,
                              IntBinaryOperator coarse,IntBinaryOperator exact,BiPredicate<Integer,Integer> allowed) {
        return select(preferred,width,depth,seaLevel,coarse,exact,allowed,new int[]{128,192});
    }
    /** Critical small halts may widen their finite search without weakening the grounding limits. */
    public static Site select(BlockPos preferred,int width,int depth,int seaLevel,
                              IntBinaryOperator coarse,IntBinaryOperator exact,BiPredicate<Integer,Integer> allowed,int[] radii) {
        return select(preferred,width,depth,seaLevel,coarse,exact,allowed,radii,32);
    }
    /** Only explicitly selected compact halts use a coarser search; exact footprint checks stay unchanged. */
    public static Site select(BlockPos preferred,int width,int depth,int seaLevel,
                              IntBinaryOperator coarse,IntBinaryOperator exact,BiPredicate<Integer,Integer> allowed,int[] radii,int step) {
        // Nine real footprint heights avoid the route cache's 32-block aliasing.
        // Only a small shortlist checks all 25 surface columns and their dry footing.
        var examined=new HashSet<Long>();
        for(int radius:radii) {
            var candidates=new ArrayList<Site>();
            for(int dx=-radius;dx<=radius;dx+=step)for(int dz=-radius;dz<=radius;dz+=step) {
                if(dx*dx+dz*dz>radius*radius)continue;
                int x=preferred.getX()+dx,z=preferred.getZ()+dz;
                if(!allowed.test(x,z))continue;
                var sampled=sample(preferred,x,z,width,depth,3,coarse);
                // Flat water must not crowd all dry land out of the exact-sampling shortlist.
                double penalty=sampled.minHeight()<=seaLevel+1?100000:0;
                candidates.add(new Site(sampled.rail(),sampled.minHeight(),sampled.maxHeight(),sampled.fill(),sampled.cut(),sampled.score()+penalty));
            }
            candidates.sort(Comparator.comparingDouble(Site::score).thenComparingInt(s->s.rail().getX()).thenComparingInt(s->s.rail().getZ()));
            Site best=null;int refinedCount=0;
            for(var candidate:candidates) {
                long key=((long)candidate.rail().getX()<<32)^(candidate.rail().getZ()&0xffffffffL);
                if(refinedCount>=6)break;
                if(!examined.add(key))continue;
                refinedCount++;
                var refined=sample(preferred,candidate.rail().getX(),candidate.rail().getZ(),width,depth,5,exact);
                if(refined.rail().getY()>250||refined.minHeight()<=seaLevel+1||refined.maxHeight()-refined.minHeight()>8||refined.fill()>5||refined.cut()>5)continue;
                if(best==null||refined.score()<best.score())best=refined;
            }
            if(best!=null)return best;
        }
        throw new IllegalArgumentException("No dry, sufficiently flat station footprint near "+preferred);
    }
    /** Critical sites prefer the nearest acceptable footprint to their entrance, not the flattest remote field.
     * Dry footing and cut/fill limits match the ordinary selector; only ranking and search spacing differ. */
    public static Site selectNearby(BlockPos entrance,int width,int depth,int seaLevel,
                                     IntBinaryOperator coarse,IntBinaryOperator exact,BiPredicate<Integer,Integer> allowed) {
        var examined=new HashSet<Long>();
        for(int radius:new int[]{64,96,128,160,192,256,320}) {
            var candidates=new ArrayList<Site>();
            for(int dx=-radius;dx<=radius;dx+=16)for(int dz=-radius;dz<=radius;dz+=16) {
                if(dx*dx+dz*dz>radius*radius)continue;
                int x=entrance.getX()+dx,z=entrance.getZ()+dz;
                if(!allowed.test(x,z))continue;
                var sampled=sample(entrance,x,z,width,depth,3,coarse);
                if(sampled.minHeight()<=seaLevel+1)continue;
                candidates.add(sampled);
            }
            // Distance bands stop a perfectly flat faraway site from displacing a nearby usable one.
            candidates.sort(Comparator.comparingInt((Site v)->(int)(Math.hypot(v.rail().getX()-entrance.getX(),v.rail().getZ()-entrance.getZ())/16))
                    .thenComparingDouble(Site::score).thenComparingInt(v->v.rail().getX()).thenComparingInt(v->v.rail().getZ()));
            Site best=null;int count=0;
            for(var candidate:candidates) {
                long key=((long)candidate.rail().getX()<<32)^(candidate.rail().getZ()&0xffffffffL);
                if(count>=12)break;if(!examined.add(key))continue;count++;
                var refined=sample(entrance,candidate.rail().getX(),candidate.rail().getZ(),width,depth,5,exact);
                if(refined.rail().getY()>250||refined.minHeight()<=seaLevel+1||refined.maxHeight()-refined.minHeight()>8||refined.fill()>5||refined.cut()>5)continue;
                if(best==null||nearbyCost(refined,entrance)<nearbyCost(best,entrance))best=refined;
            }
            if(best!=null)return best;
        }
        throw new IllegalArgumentException("No nearby dry station footprint around landmark entrance "+entrance);
    }
    public static double nearbyCost(Site site,BlockPos entrance){return Math.hypot(site.rail().getX()-entrance.getX(),site.rail().getZ()-entrance.getZ())+site.score()*.01;}
    private static Site sample(BlockPos preferred,int x,int z,int width,int depth,int grid,IntBinaryOperator heights) {
        int[] values=new int[grid*grid];int n=0;
        int left=x+1-width/2,top=z+1-depth/2;
        for(int ix=0;ix<grid;ix++)for(int iz=0;iz<grid;iz++)
            values[n++]=heights.applyAsInt(left+(width-1)*ix/(grid-1),top+(depth-1)*iz/(grid-1));
        Arrays.sort(values);int min=values[0],max=values[values.length-1],median=values[values.length/2];
        double deviation=0;for(int value:values)deviation+=Math.abs(value-median);
        // Terrain fit dominates proximity. The configured rail Y is deliberately not a target height.
        double distance=Math.hypot(x-preferred.getX(),z-preferred.getZ());
        double score=(max-min)*(max-min)*20+deviation/values.length*8+distance/16;
        return new Site(new BlockPos(x,median,z),min,max,median-min,max-median,score);
    }
}
