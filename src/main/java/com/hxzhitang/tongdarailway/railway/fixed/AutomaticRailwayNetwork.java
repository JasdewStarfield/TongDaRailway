package com.hxzhitang.tongdarailway.railway.fixed;

import com.google.gson.*;
import com.hxzhitang.tongdarailway.Tongdarailway;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;
import java.nio.file.*;
import java.util.*;

/** One bounded starter network per world. Discovery uses seed plans, never remote chunks. */
public final class AutomaticRailwayNetwork {
    private static boolean enabled;
    private static Path snapshot;
    private static final List<BlockPos> critical=new ArrayList<>();
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private AutomaticRailwayNetwork() {}
    public static boolean enabled(){return enabled;}
    public static void reset(){enabled=false;snapshot=null;critical.clear();RegionalRailwayNetwork.clear();CriticalStationSiting.clear();}

    /** Runs after Core's strategic-site initialization, while saved data is owned by the server thread. */
    public static void initialize(MinecraftServer server) {
        Path config=FMLPaths.CONFIGDIR.get().resolve("tongdarailway-auto-network.json");
        if(!Files.isRegularFile(config))return;
        try {
            var options=JsonParser.parseString(Files.readString(config)).getAsJsonObject();
            if(!options.has("enabled")||!options.get("enabled").getAsBoolean())return;
            enabled=true;
            snapshot=server.getWorldPath(LevelResource.ROOT).resolve("data/tongdarailway-auto-network.json");
            boolean regional=options.has("regional")&&options.get("regional").getAsBoolean();
            if(!regional&&Files.isRegularFile(snapshot)) {
                var saved=JsonParser.parseString(Files.readString(snapshot)).getAsJsonObject();
                FixedRailwayNetwork.restoreAutomatic(saved,server.overworld().getSeed());return;
            }
            var ids=options.has("critical_sites")?options.getAsJsonArray("critical_sites"):JsonParser.parseString("[\"eowcore:radar_station\"]").getAsJsonArray();
            if(ids.size()>4)throw new IllegalArgumentException("A starter railway region supports at most four critical sites");
            // Missing landmarks affect only their stations; ordinary regions keep their seed-based density.
            var requested=new ArrayList<String>();for(var id:ids)requested.add(id.getAsString());
            boolean coreLoaded=net.neoforged.fml.ModList.get().isLoaded("eowcore");
            critical.addAll(resolveCriticalSites(requested,id->{
                if(!coreLoaded)return Optional.empty();
                var api=Class.forName("yourscraft.jasdewstarfield.eowcore.world.StrategicSiteApi");
                var get=api.getMethod("getCenter",MinecraftServer.class,ResourceLocation.class);
                return (Optional<BlockPos>)get.invoke(null,server,ResourceLocation.parse(id));
            },id->coreLoaded?missingSiteDetails(server,id):"EOWCore is not installed",
                    (message,failure)->Tongdarailway.LOGGER.warn("[EowRail] "+message
                            +"; seed="+server.overworld().getSeed()+"; world="+server.getWorldPath(LevelResource.ROOT),failure)));
            Tongdarailway.LOGGER.info("[EowRail] Critical-site lookup finished: requested={}, resolved={}, skipped={}; regional={}. "
                    +"Missing sites do not block world startup. Existing world manifests retain their saved anchors.",
                    requested.size(),critical.size(),requested.size()-critical.size(),regional);
            if(regional){RegionalRailwayNetwork.initialize(server,critical,options);return;}
            FixedRailwayNetwork.setAutomatic(candidates(server.overworld().getSeed(),critical,server.overworld().getSharedSpawnPos()));
        } catch(java.io.IOException e){throw new IllegalStateException("Cannot initialize automatic railway network",e);}
    }

    @FunctionalInterface
    interface SiteQuery { Optional<BlockPos> get(String id) throws ReflectiveOperationException; }

    /** Resolve each landmark independently, retaining later valid sites after an absent or broken integration. */
    static List<BlockPos> resolveCriticalSites(List<String> ids,SiteQuery query,
            java.util.function.Function<String,String> details,java.util.function.BiConsumer<String,Throwable> warning) {
        var positions=new ArrayList<BlockPos>();
        for(var id:ids) {
            try {
                var result=query.get(id);
                if(result.isPresent()){positions.add(result.get());continue;}
                warning.accept("Skipping critical site "+id+": "+details.apply(id)
                        +"; action=omit associated station, continue ordinary railway planning; no wait/retry during startup",null);
            }catch(ReflectiveOperationException|RuntimeException failure) {
                warning.accept("Skipping critical site "+id+": integration query failed ("+failure.getClass().getName()
                        +"); action=omit associated station, continue ordinary railway planning; full cause follows",failure);
            }
        }
        return List.copyOf(positions);
    }

    /** Inspect only configuration and saved decisions; diagnostics must not trigger site searches or chunk loads. */
    private static String missingSiteDetails(MinecraftServer server,String id) {
        if(!id.equals("eowcore:radar_station"))return "no registered strategic-site plan for this ID";
        try {
            var config=Class.forName("yourscraft.jasdewstarfield.eowcore.config.EowCoreConfig");
            var auto=configValue(config,"AUTO_PLACE_TEMPLATE");
            var template=configValue(config,"TEMPLATE_ID");
            var temporary=configValue(config,"TEMPORARY_RADAR_LAYOUT");
            var dataType=Class.forName("yourscraft.jasdewstarfield.eowcore.world.radar.RadarStationSavedData");
            var data=dataType.getMethod("get",MinecraftServer.class).invoke(null,server);
            var selected=(Optional<?>)dataType.getMethod("siteCenter").invoke(data);
            String reason=Boolean.FALSE.equals(auto)?"radar automatic template placement is disabled"
                    :selected.isEmpty()?"radar has no selected location; selection may have exhausted the bounded search or not run"
                    :"radar location is selected but its layout is unavailable; inspect preceding EOWCore template/layout validation logs for the exact resource/error";
            return reason+"; autoPlaceTemplate="+auto+"; selectedCenter="+selected+"; templateId="+template+"; temporaryLayout="+temporary;
        }catch(ReflectiveOperationException|RuntimeException failure) {
            // Preserve the diagnostic failure as well as the original missing-plan result.
            Tongdarailway.LOGGER.warn("[EowRail] Cannot inspect missing radar plan details; site={}",id,failure);
            return "no registered radar plan; additional diagnostic query failed: "+failure;
        }
    }

    private static Object configValue(Class<?> config,String name)throws ReflectiveOperationException {
        var value=config.getField(name).get(null);
        return value.getClass().getMethod("get").invoke(value);
    }

    /** Stable radial candidates are fallback small halts, not invented mine or settlement locations. */
    public static FixedRailwayNetwork.Network candidates(long seed,List<BlockPos> sites) {
        return candidates(seed,sites,BlockPos.ZERO);
    }

    /** An empty landmark list creates independent halts, never an invented critical station at spawn. */
    static FixedRailwayNetwork.Network candidates(long seed,List<BlockPos> sites,BlockPos fallbackOrigin) {
        var nodes=new ArrayList<FixedRailwayNetwork.Node>();
        for(int i=0;i<sites.size();i++) {
            var p=sites.get(i);
            nodes.add(new FixedRailwayNetwork.Node("critical_"+i,CriticalStationSiting.preferred(p),Direction.WEST,p,64,"critical","small_stone_station_0",0));
        }
        var random=new Random(seed^0x454f575241494cL);var origin=sites.isEmpty()?fallbackOrigin:sites.getFirst();
        double phase=random.nextDouble()*Math.PI*2;
        for(int i=0;i<Math.min(4,6-sites.size());i++) {
            double angle=phase+i*Math.PI/2+(random.nextDouble()-.5)*.3;
            int distance=850+random.nextInt(1051);
            var p=origin.offset((int)Math.round(Math.cos(angle)*distance),0,(int)Math.round(Math.sin(angle)*distance));
            nodes.add(new FixedRailwayNetwork.Node("halt_"+i,p,Direction.WEST,p,0,"halt","eow_small_halt",random.nextInt(4)*90));
        }
        return new FixedRailwayNetwork.Network(seed,nodes,List.of());
    }

    /** Preview at most twelve real structure starts; Structure.generate constructs pieces without placing blocks. */
    public static List<FixedRailwayNetwork.Node> mines(ServerLevel level,BlockPos origin) {
        var candidates=new ArrayList<Map.Entry<ChunkPos,net.minecraft.world.level.levelgen.structure.StructureSet>>();
        var state=level.getChunkSource().getGeneratorState();
        var sets=level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
        for(var entry:sets.entrySet()) {
            var set=entry.getValue();
            if(set.structures().stream().noneMatch(s->s.structure().unwrapKey().map(k->k.location().getNamespace().equals("bettermineshafts")).orElse(false))
                    ||!(set.placement() instanceof RandomSpreadStructurePlacement placement))continue;
            int span=placement.spacing();int cx=origin.getX()>>4,cz=origin.getZ()>>4;
            int radius=(int)Math.ceil(2300.0/(span*16));
            for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++) {
                var chunk=placement.getPotentialStructureChunk(level.getSeed(),cx+x*span,cz+z*span);
                double distance=chunk.getMiddleBlockPosition(0).distToLowCornerSqr(origin.getX(),0,origin.getZ());
                if(distance<320L*320||distance>2300L*2300||!placement.isStructureChunk(state,chunk.x,chunk.z))continue;
                candidates.add(Map.entry(chunk,set));
            }
        }
        candidates.sort(Comparator.<Map.Entry<ChunkPos,net.minecraft.world.level.levelgen.structure.StructureSet>>comparingDouble(e->e.getKey().getMiddleBlockPosition(0).distToLowCornerSqr(origin.getX(),0,origin.getZ()))
                .thenComparingLong(e->e.getKey().toLong()));
        var result=new ArrayList<FixedRailwayNetwork.Node>();var visited=new HashSet<ChunkPos>();int checked=0;
        var generator=level.getChunkSource().getGenerator();
        for(var candidate:candidates) {
            if(!visited.add(candidate.getKey()))continue;
            if(checked++>=12||result.size()>=2)break;
            var selections=new ArrayList<>(candidate.getValue().structures());
            var random=new net.minecraft.world.level.levelgen.WorldgenRandom(new net.minecraft.world.level.levelgen.LegacyRandomSource(0));
            random.setLargeFeatureSeed(level.getSeed(),candidate.getKey().x,candidate.getKey().z);
            while(!selections.isEmpty()) {
                int roll=random.nextInt(selections.stream().mapToInt(v->v.weight()).sum()),index=0;
                while((roll-=selections.get(index).weight())>=0)index++;
                var selection=selections.remove(index);var structure=selection.structure().value();
                var start=structure.generate(level.registryAccess(),generator,generator.getBiomeSource(),level.getChunkSource().randomState(),
                        level.getStructureManager(),level.getSeed(),candidate.getKey(),0,level,structure.biomes()::contains);
                if(!start.isValid())continue;
                // Core's supplemental piece records the actual selected surface mouth, including cliff outlets.
                for(var piece:start.getPieces())if(piece.getClass().getName().endsWith(".NativeMineshaftEntrancePiece"))try {
                    var route=piece.getClass().getMethod("route").invoke(piece);
                    var kind=route.getClass().getMethod("kind").invoke(route).toString();
                    if(kind.equals("CAPPED"))continue;
                    var mouth=(BlockPos)route.getClass().getMethod("mouth").invoke(route);
                    if(result.stream().anyMatch(n->n.center().distSqr(mouth)<400L*400))continue;
                    result.add(new FixedRailwayNetwork.Node("mine_"+candidate.getKey().x+"_"+candidate.getKey().z,mouth.offset(96,0,64),Direction.WEST,mouth,18,"mine","eow_small_halt",0));
                }catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot read planned mine entrance",e);}
                // A structure set chooses one valid biome variant, just as vanilla worldgen does.
                break;
            }
        }
        Tongdarailway.LOGGER.info("[EowRail] Checked {} mine starts without loading chunks; selected {} mouths",Math.min(checked,12),result.size());
        return result;
    }

    /** Store finalized decisions inside this world; later config edits cannot silently move its stations. */
    public static void save(JsonObject data) {
        if(snapshot==null)return;
        try {
            Files.createDirectories(snapshot.getParent());var temporary=snapshot.resolveSibling(snapshot.getFileName()+".tmp");
            Files.writeString(temporary,JSON.toJson(data));Files.move(temporary,snapshot,StandardCopyOption.REPLACE_EXISTING);
        }catch(java.io.IOException e){throw new IllegalStateException("Cannot save automatic railway plan",e);}
    }
}
