package com.hxzhitang.tongdarailway.railway.fixed;

import com.google.gson.*;
import com.hxzhitang.tongdarailway.Config;
import com.hxzhitang.tongdarailway.Tongdarailway;
import com.hxzhitang.tongdarailway.railway.*;
import com.hxzhitang.tongdarailway.railway.planner.*;
import com.hxzhitang.tongdarailway.util.AStarPathfinder;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLPaths;
import java.nio.file.*;
import java.util.*;

/** EOW fork: explicit, seed-bound external anchors leave Core and its structure planners independent. */
public final class FixedRailwayNetwork {
    public record Node(String id,BlockPos rail,Direction facing,BlockPos center,int radius) { }
    public record Link(String from,String to) { }
    public record Network(long seed,List<Node> nodes,List<Link> links) {
        public Network { nodes=List.copyOf(nodes);links=List.copyOf(links); }
    }
    private static volatile Network current=new Network(0,List.of(),List.of());
    private FixedRailwayNetwork() { }
    public static boolean enabled() {return Config.fixedNodeMode;}
    public static Network current() {return current;}
    public static void load() {
        current=new Network(0,List.of(),List.of());
        Path path=FMLPaths.CONFIGDIR.get().resolve("tongdarailway-fixed-nodes.json");
        if(!Files.isRegularFile(path))return;
        try {current=parse(Files.readString(path));}
        catch(Exception e){throw new IllegalStateException("Invalid fixed railway nodes: "+path,e);}
        Tongdarailway.LOGGER.info("[EowRail] Loaded {} fixed nodes and {} links for seed {}",
                current.nodes().size(),current.links().size(),current.seed());
    }
    /** Validate all links before any terrain is changed; nodes are supplied before world generation. */
    public static Network parse(String text) {
        var root=JsonParser.parseString(text).getAsJsonObject();
        if(root.get("format").getAsInt()!=1)throw new IllegalArgumentException("Unknown fixed network format");
        var nodes=new ArrayList<Node>();var names=new HashSet<String>();
        for(var element:root.getAsJsonArray("nodes")) {
            var n=element.getAsJsonObject();String id=n.get("id").getAsString();
            if(!names.add(id))throw new IllegalArgumentException("Duplicate node: "+id);
            var rail=pos(n.getAsJsonArray("rail"));var center=pos(n.getAsJsonArray("protected_center"));
            Direction facing=Direction.byName(n.get("facing").getAsString());int radius=n.get("protected_radius").getAsInt();
            if(facing==null||facing.getAxis().isVertical()||radius<0||radius>128||rail.getY()<63||rail.getY()>250)
                throw new IllegalArgumentException("Invalid rail anchor: "+id);
            if(Math.abs(rail.getX()-center.getX())<=radius+8&&Math.abs(rail.getZ()-center.getZ())<=radius+8)
                throw new IllegalArgumentException("Rail anchor must remain outside the protected site: "+id);
            nodes.add(new Node(id,rail,facing,center,radius));
        }
        var links=new ArrayList<Link>();var pairs=new HashSet<String>();
        for(var element:root.getAsJsonArray("links")) {
            var n=element.getAsJsonObject();String a=n.get("from").getAsString(),b=n.get("to").getAsString();
            if(a.equals(b)||!names.contains(a)||!names.contains(b))throw new IllegalArgumentException("Invalid link");
            String pair=a.compareTo(b)<0?a+":"+b:b+":"+a;
            if(!pairs.add(pair))throw new IllegalArgumentException("Duplicate link");
            var left=nodes.stream().filter(v->v.id().equals(a)).findFirst().orElseThrow();
            var right=nodes.stream().filter(v->v.id().equals(b)).findFirst().orElseThrow();
            if(left.rail().distSqr(right.rail())>4096L*4096)throw new IllegalArgumentException("Prototype links are limited to 4096 blocks");
            links.add(new Link(a,b));
        }
        if(nodes.size()>8||links.size()>4)throw new IllegalArgumentException("Prototype node budget exceeded");
        return new Network(root.get("seed").getAsLong(),nodes,links);
    }
    private static BlockPos pos(JsonArray array) {
        if(array.size()!=3)throw new IllegalArgumentException("Expected XYZ");
        int x=array.get(0).getAsInt(),y=array.get(1).getAsInt(),z=array.get(2).getAsInt();
        if(Math.abs((long)x)>29_000_000||Math.abs((long)z)>29_000_000)throw new IllegalArgumentException("Outside world bounds");
        return new BlockPos(x,y,z);
    }
    public static boolean protects(BlockPos pos) {
        for(var n:current.nodes())if(Math.abs(pos.getX()-n.center().getX())<=n.radius()
                &&Math.abs(pos.getZ()-n.center().getZ())<=n.radius())return true;
        return false;
    }
    public static boolean intersects(RegionPos region,long seed) {
        if(current.seed()!=seed)return false;
        int size=Tongdarailway.CHUNK_GROUP_SIZE*16,left=region.x()*size,top=region.z()*size;
        for(var link:current.links()) {
            var a=node(link.from()).rail();var b=node(link.to()).rail();
            if(Math.max(a.getX(),b.getX())+256>=left&&Math.min(a.getX(),b.getX())-256<left+size
                    &&Math.max(a.getZ(),b.getZ())+256>=top&&Math.min(a.getZ(),b.getZ())-256<top+size)return true;
        }
        return false;
    }
    private static Node node(String id) {return current.nodes().stream().filter(n->n.id().equals(id)).findFirst().orElseThrow();}
    public static List<RoutePlanner.ResultWay> plan(RailwayBuilder builder,ServerLevel level) {
        if(current.seed()!=level.getSeed())return List.of();
        var result=new ArrayList<RoutePlanner.ResultWay>();
        for(var link:current.links()) {
            long start=System.nanoTime();var a=node(link.from());var b=node(link.to());
            Vec3 av=Vec3.atLowerCornerOf(a.rail()),bv=Vec3.atLowerCornerOf(b.rail());
            Vec3 ad=Vec3.atLowerCornerOf(a.facing().getNormal()),bd=Vec3.atLowerCornerOf(b.facing().getNormal());
            var ap=a.rail().relative(a.facing(),48);var bp=b.rail().relative(b.facing(),48);
            int[] from={ap.getX(),ap.getZ(),ap.getY()},to={bp.getX(),bp.getZ(),bp.getY()};
            var con=new StationPlanner.ConnectionGenInfo(av,ad,bv,bd,from,to);
            var center=RegionPos.regionPosFromWorldPos(ap.getX(),ap.getZ());
            int regions=Math.abs(center.x()-RegionPos.regionPosFromWorldPos(bp.getX(),bp.getZ()).x())
                    +Math.abs(center.z()-RegionPos.regionPosFromWorldPos(bp.getX(),bp.getZ()).z())+1;
            var way=AStarPathfinder.findPath(builder,from,Set.of(to),center,regions,(x,z)->{
                var point=new BlockPos(x,80,z);
                return protects(point)?100000:0;
            });
            var route=new RoutePlanner().getWay(builder,way,con,level);
            if(route==null)throw new IllegalStateException("No valid fixed route: "+link);
            result.add(route);
            long kept=route.trackPutInfos().stream().filter(t->RailwayDecay.keep(level.getSeed(),route.way(),t)).count();
            Tongdarailway.LOGGER.info("[EowRail] Planned {} -> {}: {} blocks, {} / {} track records retained, {} ms",
                    a.id(),b.id(),Math.round(route.way().getTotalLength()),kept,route.trackPutInfos().size(),(System.nanoTime()-start)/1e6);
        }
        return List.copyOf(result);
    }
    /** Small loading pads stop outside existing buildings and shafts, and write only their own chunk slice. */
    public static void placePlatforms(WorldGenLevel level,ChunkPos owner) {
        if(current.seed()!=level.getSeed())return;
        for(var node:current.nodes())for(int along=-5;along<=5;along++)for(int side=3;side<=5;side++) {
            var p=node.rail().relative(node.facing(),along).relative(node.facing().getClockWise(),side).below();
            if(!owner.equals(new ChunkPos(p))||protects(p)||level.getBlockState(p).hasBlockEntity())continue;
            level.setBlock(p,(Math.floorMod(p.asLong(),5)==0?Blocks.MOSSY_COBBLESTONE:Blocks.COBBLESTONE).defaultBlockState(),2);
        }
    }
}
