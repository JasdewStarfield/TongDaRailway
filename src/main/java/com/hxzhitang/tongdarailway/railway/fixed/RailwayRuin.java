package com.hxzhitang.tongdarailway.railway.fixed;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import com.hxzhitang.tongdarailway.util.CurveRoute;

/** Physical deterioration uses the same frozen repair intervals as track omission. */
public final class RailwayRuin {
    public enum Kind { BROKEN_BRIDGE,TUNNEL_COLLAPSE,LANDSLIDE }
    private RailwayRuin() {}
    public static Kind kind(boolean bridge,boolean tunnel) {
        return bridge?Kind.BROKEN_BRIDGE:tunnel?Kind.TUNNEL_COLLAPSE:Kind.LANDSLIDE;
    }
    public static int rubbleHeight(long seed,BlockPos pos,double lateral,boolean tunnel) {
        // An asymmetric pile leaves a recognizable failure front instead of a uniform stone wall.
        int side=(RailwayDecay.mix(seed)&1)==0?1:-1;
        double ridge=tunnel?3-Math.abs(lateral):3-Math.abs(lateral-side*1.6);
        int roughness=(int)Math.floorMod(RailwayDecay.mix(seed^pos.asLong()),3)-1;
        return Math.max(0,Math.min(4,(int)Math.floor(ridge)+roughness));
    }
    public static void placeDebris(WorldGenLevel world,BlockPos rail,double lateral,boolean tunnel,long seed) {
        int height=rubbleHeight(seed,rail,lateral,tunnel);
        for(int y=0;y<height;y++) {
            var p=rail.above(y);var old=world.getBlockState(p);
            if(old.hasBlockEntity()||old.is(Blocks.BEDROCK)||FixedRailwayNetwork.protects(p))continue;
            long hash=RailwayDecay.mix(seed^p.asLong());
            var block=Math.floorMod(hash,4)==0?Blocks.GRAVEL:tunnel?Blocks.COBBLESTONE:
                    Math.floorMod(hash,3)==0?Blocks.COARSE_DIRT:Blocks.STONE;
            world.setBlock(p,block.defaultBlockState(),2);
        }
    }
}
