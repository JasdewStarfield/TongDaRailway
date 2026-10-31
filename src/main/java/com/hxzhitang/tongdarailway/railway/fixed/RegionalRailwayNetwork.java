package com.hxzhitang.tongdarailway.railway.fixed;

import com.google.gson.*;
import com.hxzhitang.tongdarailway.Tongdarailway;
import com.hxzhitang.tongdarailway.railway.*;
import com.hxzhitang.tongdarailway.railway.planner.*;
import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Sparse independent systems, spanning a shared nine-cell planning area. */
public final class RegionalRailwayNetwork {
    public record Plan(FixedRailwayNetwork.Network network,List<StationPlanner.StationGenInfo> stations,
                       List<RoutePlanner.ResultWay> routes,WorldgenTrackPlan tracks) {}
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static volatile World world;
    private static final class World {
        final long seed;final Path directory;final List<BlockPos> critical;final Set<RegionPos> criticalRegions;
        final double probability,pairing;
        final RegionalPrefetchQueue<RegionalTopology.Unit,Prefetch> prefetch=new RegionalPrefetchQueue<>(64);
        final java.util.concurrent.atomic.AtomicInteger demand=new java.util.concurrent.atomic.AtomicInteger();
        final ScheduledExecutorService prefetchWorker=Executors.newSingleThreadScheduledExecutor(job->{var thread=new Thread(job,"TongDaRailway-neighborhood-prefetch");thread.setDaemon(true);thread.setPriority(Thread.MIN_PRIORITY);return thread;});
        volatile boolean prefetchRunning;
        final LinkedHashMap<RegionalTopology.Unit,CompletableFuture<Plan>> cache=new LinkedHashMap<>(32,.75f,true);
        final Map<RegionalTopology.Unit,List<FixedRailwayNetwork.Node>> knownNodes=new ConcurrentHashMap<>();
        final Map<RegionalTopology.Unit,Map<String,BlockPos>> knownStations=new ConcurrentHashMap<>();
        World(long seed,Path directory,List<BlockPos> critical,double probability,double pairing){this.seed=seed;this.directory=directory;this.critical=List.copyOf(critical);this.probability=probability;this.pairing=pairing;this.criticalRegions=new HashSet<>();critical.forEach(p->criticalRegions.add(RegionalTopology.region(p)));}
    }
    private RegionalRailwayNetwork() {}
    public static boolean enabled(){return world!=null&&FixedRailwayNetwork.enabled();}
    public static void clear(){stopPrefetch();world=null;}
    /** Interrupt before the server saves/closes its levels; a cancelled plan may be retried on demand. */
    public static void stopPrefetch(){
        var w=world;if(w==null)return;w.prefetch.clear();w.prefetchWorker.shutdown();
        // Let an in-flight plan finish first: a real chunk may already be waiting on its shared future.
        try{if(!w.prefetchWorker.awaitTermination(8,TimeUnit.SECONDS)){w.prefetchWorker.shutdownNow();if(!w.prefetchWorker.awaitTermination(2,TimeUnit.SECONDS))Tongdarailway.LOGGER.warn("[EowRail] Prefetch worker is still stopping");}}
        catch(InterruptedException interrupted){w.prefetchWorker.shutdownNow();Thread.currentThread().interrupt();}
    }
    /** Freeze density and strategic anchors once per world, before any regional generation begins. */
    public static void initialize(MinecraftServer server,List<BlockPos> critical,JsonObject options)throws java.io.IOException {
        var directory=server.getWorldPath(LevelResource.ROOT).resolve("data/tongdarailway-regions");Files.createDirectories(directory);
        var manifest=directory.resolve("network.json");double probability=number(options,"region_probability",.28),pairing=number(options,"neighbor_connection_probability",.25);
        if(Files.isRegularFile(manifest)) {
            var saved=JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
            if(saved.get("seed").getAsLong()!=server.overworld().getSeed())throw new IllegalArgumentException("Regional railway seed mismatch");
            probability=number(saved,"region_probability",.28);pairing=number(saved,"neighbor_connection_probability",.25);
            var positions=new ArrayList<BlockPos>();for(var item:saved.getAsJsonArray("critical")){var a=item.getAsJsonArray();positions.add(new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt()));}critical=positions;
        } else {
            var saved=new JsonObject();saved.addProperty("format",1);saved.addProperty("seed",server.overworld().getSeed());saved.addProperty("region_probability",probability);saved.addProperty("neighbor_connection_probability",pairing);
            var array=new JsonArray();for(var p:critical){var a=new JsonArray();a.add(p.getX());a.add(p.getY());a.add(p.getZ());array.add(a);}saved.add("critical",array);Files.writeString(manifest,JSON.toJson(saved));
        }
        // Freeze the experimental policy with each world just like density and strategic anchors.
        var metadata=JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
        String policy=metadata.has("surface_structure_policy")?metadata.get("surface_structure_policy").getAsString()
                :options.has("surface_structure_policy")?options.get("surface_structure_policy").getAsString():"off";
        SurfaceStructurePolicy.configure(policy);SurfaceStructurePolicy.selectTargets(server.overworld());metadata.addProperty("surface_structure_policy",policy);Files.writeString(manifest,JSON.toJson(metadata));
        world=new World(server.overworld().getSeed(),directory,critical,probability,pairing);
        var initialized=world;
        initialized.prefetchWorker.scheduleWithFixedDelay(()->pumpPrefetch(initialized),250,250,TimeUnit.MILLISECONDS);
        Tongdarailway.LOGGER.info("[EowRail] Regional abandoned systems enabled: density {}, neighbor pairing {}",probability,pairing);
    }
    private static double number(JsonObject o,String name,double fallback){double v=o.has(name)?o.get(name).getAsDouble():fallback;if(!Double.isFinite(v)||v<0||v>1)throw new IllegalArgumentException("Invalid railway probability: "+name);return v;}
    public static boolean active(RegionPos region){var w=world;return w!=null&&RegionalTopology.active(w.seed,region,w.criticalRegions,w.probability);}
    public static RegionalTopology.Unit unit(RegionPos region){var w=Objects.requireNonNull(world);return RegionalTopology.unit(w.seed,region,w.criticalRegions,w.probability,w.pairing);}
    public static List<BlockPos> criticalSites(){return Objects.requireNonNull(world).critical;}
    public static Plan plan(RailwayBuilder builder,ServerLevel level,RegionPos region) {
        var w=Objects.requireNonNull(world);w.demand.incrementAndGet();
        try{return planInternal(builder,level,region);}finally{w.demand.decrementAndGet();}
    }
    private static Plan planInternal(RailwayBuilder builder,ServerLevel level,RegionPos region) {
        var w=Objects.requireNonNull(world);var key=unit(region);CompletableFuture<Plan> future;boolean owner=false;
        synchronized(w.cache){future=w.cache.get(key);if(future==null){future=new CompletableFuture<>();w.cache.put(key,future);owner=true;}}
        if(owner)try {
            builder.checkPlanningCancelled();long began=System.nanoTime();var file=w.directory.resolve(key.id()+".json");
            JsonObject saved=Files.isRegularFile(file)?JsonParser.parseString(Files.readString(file)).getAsJsonObject():null;
            var input=saved==null?NaturalRailwayLayout.create(w.seed,key,w.critical,(x,z)->builder.getExactHeight(level,x,z),level.getSeaLevel()):null;
            var result=FixedRailwayNetwork.planRegional(builder,level,input,saved,key);
            var kept=new ArrayList<com.hxzhitang.tongdarailway.structure.TrackPutInfo>();
            for(var route:result.routes())for(var track:route.trackPutInfos())if(builder.damagePlan(route).keep(route.way(),track))kept.add(track);
            var plan=new Plan(result.network(),result.stations(),result.routes(),new WorldgenTrackPlan(kept,result.stations()));
            builder.checkPlanningCancelled();
            result.snapshot().addProperty("geometry",RegionalGeometry.write(result.routes()).toString());
            var temporary=file.resolveSibling(file.getFileName()+".tmp");Files.writeString(temporary,JSON.toJson(result.snapshot()));Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);
            w.knownNodes.put(key,result.network().nodes());var positions=new LinkedHashMap<String,BlockPos>();
            var rows=result.snapshot().getAsJsonArray("placements");for(var item:rows){var row=item.getAsJsonObject();var a=row.getAsJsonArray("place");positions.put(row.get("node").getAsString(),new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt()));}w.knownStations.put(key,Map.copyOf(positions));
            future.complete(plan);
            Tongdarailway.LOGGER.info("[EowRail] System {}: {} stations, {} links, {} ms{}",key.id(),plan.stations().size(),plan.routes().size(),(System.nanoTime()-began)/1e6,saved==null?"":" (saved sites)");
            // Keep only a bounded working set of full route/endpoint indices; lightweight labels remain available.
            synchronized(w.cache){var it=w.cache.entrySet().iterator();while(w.cache.size()>32&&it.hasNext()){var item=it.next();if(!item.getKey().equals(key)&&item.getValue().isDone())it.remove();}}
        }catch(Throwable failure){future.completeExceptionally(failure);if(failure instanceof CancellationException){synchronized(w.cache){w.cache.remove(key,future);}}}
        return future.join();
    }
    private record Prefetch(RailwayBuilder builder,ServerLevel level,RegionPos region) {}
    /** Poll player positions once a second; this only queues noise-based plans and never requests chunks. */
    public static void tickPrefetch(MinecraftServer server) {
        if(!enabled()||server.getTickCount()%20!=0)return;
        var players=server.getPlayerList().getPlayers().stream().filter(p->p.serverLevel()==server.overworld()).map(p->p.blockPosition()).toList();
        if(players.isEmpty())return;
        var builder=RailwayBuilder.getInstance(server.overworld().getSeed());
        if(builder!=null)requestPrefetchAround(builder,server.overworld(),players);
    }
    /** Each player covers the surrounding 3x3 large planning areas, center first; multiplayer requests deduplicate. */
    public static void requestPrefetchAround(RailwayBuilder builder,ServerLevel level,List<BlockPos> players) {
        var w=world;if(w==null||w.prefetchWorker.isShutdown())return;
        var wanted=new LinkedHashMap<RegionalTopology.Unit,Prefetch>();
        for(int ring=0;ring<=1;ring++)for(var player:players) {
            var center=unit(RegionalTopology.region(player));
            for(int dx=-ring;dx<=ring;dx++)for(int dz=-ring;dz<=ring;dz++) {
                if(Math.max(Math.abs(dx),Math.abs(dz))!=ring)continue;
                var region=new RegionPos(center.x()+dx*center.width(),center.z()+dz*center.depth());
                var key=unit(region);if(!active(region)||w.knownNodes.containsKey(key))continue;
                synchronized(w.cache){if(w.cache.containsKey(key))continue;}
                wanted.putIfAbsent(key,new Prefetch(builder,level,region));
            }
        }
        w.prefetch.replace(wanted);
    }
    private static void pumpPrefetch(World w) {
        // Demand work runs on the existing foreground pool, bypassing this queue. Do not start
        // another speculative system while a real generation request is already using the planner.
        if(world!=w||w.demand.get()>0||w.prefetchWorker.isShutdown())return;
        var request=w.prefetch.poll();if(request==null)return;w.prefetchRunning=true;
        try{planInternal(request.builder(),request.level(),request.region());}
        catch(Throwable failure){if(!w.prefetchWorker.isShutdown())Tongdarailway.LOGGER.warn("[EowRail] Neighborhood prefetch failed for {}",request.region(),failure);}
        finally{w.prefetchRunning=false;}
    }
    /** Small read-only diagnostic used to verify the bounded queue without forcing world generation. */
    public static int pendingPrefetch(){var w=world;return w==null?0:w.prefetch.size()+(w.prefetchRunning?1:0);}
    /** Flat-terrain entry point for topology tests; live planning supplies bounded noise-column heights. */
    public static FixedRailwayNetwork.Network candidates(long seed,RegionalTopology.Unit unit,List<BlockPos> critical) {
        return NaturalRailwayLayout.create(seed,unit,critical,(x,z)->80,63);
    }
    /** Keep the main corridor order and reconnect side branches only to their intended spine anchors. */
    public static List<FixedRailwayNetwork.Link> longLinks(List<FixedRailwayNetwork.Node> nodes) {
        var spine=nodes.stream().filter(n->n.role().equals("corridor_stop")).sorted(Comparator.comparing(FixedRailwayNetwork.Node::id)).toList();
        var links=new ArrayList<FixedRailwayNetwork.Link>();
        for(int i=1;i<spine.size();i++)addLink(links,spine.get(i-1),spine.get(i));
        var returns=nodes.stream().filter(n->n.role().startsWith("return_to_")).sorted(Comparator.comparing(FixedRailwayNetwork.Node::id)).toList();
        for(var n:returns)spine.stream().filter(s->s.id().equals(n.role().substring("return_to_".length()))).findFirst().ifPresent(anchor->addLink(links,n,anchor));
        if(returns.size()==2)addLink(links,returns.getFirst(),returns.getLast());
        for(var n:nodes)if(!spine.contains(n)&&!returns.contains(n))spine.stream().min(Comparator.comparingDouble(s->s.rail().distSqr(n.rail()))).ifPresent(nearest->addLink(links,n,nearest));
        return List.copyOf(links);
    }
    private static void addLink(List<FixedRailwayNetwork.Link> links,FixedRailwayNetwork.Node a,FixedRailwayNetwork.Node b) {
        // Skipped sites may create a longer gap; leave that gap for players instead of an unbounded search.
        if(a.rail().distSqr(b.rail())<=4096L*4096)links.add(new FixedRailwayNetwork.Link(a.id(),b.id()));
    }
    public static boolean protects(BlockPos pos) {
        var w=world;if(w==null)return false;
        for(var p:w.critical)if(Math.abs(pos.getX()-p.getX())<=64&&Math.abs(pos.getZ()-p.getZ())<=64)return true;
        var nodes=w.knownNodes.get(unit(RegionalTopology.region(pos)));if(nodes!=null)for(var n:nodes)
            if(n.radius()>0&&Math.abs(pos.getX()-n.center().getX())<=n.radius()&&Math.abs(pos.getZ()-n.center().getZ())<=n.radius())return true;
        return false;
    }
    public static Map<String,BlockPos> knownStations(){var w=world;var result=new TreeMap<String,BlockPos>();if(w!=null)w.knownStations.forEach((unit,positions)->positions.forEach((id,pos)->result.put(unit.id()+"/"+id,pos)));return result;}
}
