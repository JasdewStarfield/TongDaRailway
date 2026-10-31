package com.hxzhitang.tongdarailway.railway.fixed;

import com.hxzhitang.tongdarailway.Tongdarailway;
import com.hxzhitang.tongdarailway.railway.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import java.util.*;

/** Experimental A/B policies share seed-only structure previews and never request remote chunks. */
public final class SurfaceStructurePolicy {
    private static volatile String mode="off";
    private static volatile Set<Structure> targets=Set.of();
    private static volatile java.lang.ref.WeakReference<ServerLevel> activeLevel=new java.lang.ref.WeakReference<>(null);
    private static final ThreadLocal<Map<BoundingBox,String>> LABELS=ThreadLocal.withInitial(HashMap::new);
    private static final ThreadLocal<List<BoundingBox>> OBSTACLES=new ThreadLocal<>();
    public static void configure(String value) {
        if(!Set.of("off","avoid","remove").contains(value))throw new IllegalArgumentException("Unknown surface_structure_policy: "+value);
        mode=value;
    }
    /** Removal uses completed surface starts from every mod; avoidance retains its bounded vanilla preview scope. */
    public static void selectTargets(ServerLevel level){
        activeLevel=new java.lang.ref.WeakReference<>(level);
        var selected=new HashSet<Structure>();
        for(var e:level.registryAccess().registryOrThrow(Registries.STRUCTURE).entrySet())
            if(targetType(e.getKey().location().getNamespace(), e.getKey().location().getPath(), e.getValue().step(), mode))selected.add(e.getValue());
        targets=Set.copyOf(selected);
        Tongdarailway.LOGGER.info("[EowRail] Structure policy scope: {} surface structure types",targets.size());
    }
    /** Include declared surface buildings and raw-generation candidates. Deep raw starts are filtered after layout. */
    static boolean targetType(String namespace, String path, GenerationStep.Decoration step, String policy) {
        // Structory declares even its surface towers as RAW_GENERATION. Their completed
        // boxes are classified against the natural surface later, without loading chunks.
        if (policy.equals("remove") && step == GenerationStep.Decoration.RAW_GENERATION) return true;
        if (step != GenerationStep.Decoration.SURFACE_STRUCTURES) return false;
        return policy.equals("remove") || namespace.equals("minecraft") && landBuilding(path);
    }
    private static boolean landBuilding(String id){return id.startsWith("village_")||Set.of("pillager_outpost","woodland_mansion","desert_pyramid","jungle_pyramid","swamp_hut","igloo","ruined_portal","ruined_portal_desert","ruined_portal_jungle","ruined_portal_mountain","ruined_portal_swamp").contains(id);}
    public static String mode(){return mode;}
    public static boolean previewing(){return OBSTACLES.get()!=null;}
    private SurfaceStructurePolicy(){}

    /** The whole planning call is guarded, including its later mine-entrance previews. */
    public static void begin(ServerLevel level,RegionalTopology.Unit unit,boolean restored) {
        OBSTACLES.set(new ArrayList<>());
        if(!mode.equals("avoid")||restored)return;
        long began=System.nanoTime();int checked=0;
        var boxes=OBSTACLES.get();var generator=level.getChunkSource().getGenerator();
        var state=level.getChunkSource().getGeneratorState();
        for(var set:level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET)) {
            if(!(set.placement() instanceof RandomSpreadStructurePlacement placement)
                    ||set.structures().stream().noneMatch(e->eligible(e.structure().value())))continue;
            int span=placement.spacing();
            int minX=Math.floorDiv(unit.x()*256-32,span),maxX=Math.floorDiv((unit.x()+unit.width())*256+32,span);
            int minZ=Math.floorDiv(unit.z()*256-32,span),maxZ=Math.floorDiv((unit.z()+unit.depth())*256+32,span);
            for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++) {
                var cp=placement.getPotentialStructureChunk(level.getSeed(),x*span,z*span);
                if(!placement.isStructureChunk(state,cp.x,cp.z))continue;
                var choices=new ArrayList<>(set.structures());
                var random=new WorldgenRandom(new LegacyRandomSource(0));random.setLargeFeatureSeed(level.getSeed(),cp.x,cp.z);
                while(!choices.isEmpty()) {
                    int roll=random.nextInt(choices.stream().mapToInt(e->e.weight()).sum()),i=0;
                    while((roll-=choices.get(i).weight())>=0)i++;
                    var structure=choices.remove(i).structure().value();checked++;
                    var start=structure.generate(level.registryAccess(),generator,generator.getBiomeSource(),level.getChunkSource().randomState(),
                            level.getStructureManager(),level.getSeed(),cp,0,level,structure.biomes()::contains);
                    if(!start.isValid())continue;
                    if(eligible(structure)){var b=start.getBoundingBox();boxes.add(b);LABELS.get().put(b,level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(structure).toString());}
                    break;
                }
            }
        }
        Tongdarailway.LOGGER.info("[EowRail] Surface survey {} mode={}: {} starts checked, {} footprints, {} ms",unit.id(),mode,checked,boxes.size(),(System.nanoTime()-began)/1e6);
    }
    /** Persist the shared survey with the plan so comparisons use the exact tested footprints. */
    public static com.google.gson.JsonArray footprints(){
        var rows=new com.google.gson.JsonArray();var boxes=OBSTACLES.get();
        if(boxes!=null)for(var b:boxes){var row=new com.google.gson.JsonArray();for(int v:new int[]{b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ()})row.add(v);row.add(LABELS.get().getOrDefault(b,"unknown"));rows.add(row);}
        return rows;
    }
    public static void end(){OBSTACLES.remove();LABELS.remove();}
    public static boolean eligible(Structure structure){return targets.contains(structure);}
    /** Horizontal reservations cover bridges, supports and excavation, not just the central rail voxel. */
    public static boolean blocked(int x,int z) {
        var boxes=OBSTACLES.get();return mode.equals("avoid")&&boxes!=null&&boxes.stream().anyMatch(b->contains(b,x,z,24));
    }
    public static boolean stationBlocked(int minX,int minZ,int maxX,int maxZ) {
        var boxes=OBSTACLES.get();return mode.equals("avoid")&&boxes!=null&&boxes.stream().anyMatch(b->overlap(b,minX,minZ,maxX,maxZ,56));
    }
    static boolean contains(BoundingBox b,int x,int z,int margin){return overlap(b,x,z,x,z,margin);}
    static boolean overlap(BoundingBox b,int minX,int minZ,int maxX,int maxZ,int margin){return maxX>=b.minX()-margin&&minX<=b.maxX()+margin&&maxZ>=b.minZ()-margin&&minZ<=b.maxZ()+margin;}

    /** Called only after a complete native structure layout exists, before any blocks are placed. */
    public static boolean cancel(Structure structure,StructureStart start,Structure.GenerationContext context) {
        if(!mode.equals("remove")||previewing()||!eligible(structure)||!start.isValid()
                ||!RegionalRailwayNetwork.enabled())return false;
        ServerLevel level=context.heightAccessor() instanceof WorldGenRegion region?region.getLevel()
                :context.heightAccessor() instanceof ServerLevel serverLevel?serverLevel:null;
        // Native structure starts can supply ChunkAccess as the height accessor. Match the exact
        // overworld generator and seed before using the initialized planning level in that case.
        if(level==null){var current=activeLevel.get();if(current!=null&&context.chunkGenerator()==current.getChunkSource().getGenerator()&&context.seed()==current.getSeed())level=current;}
        if(level==null)return false;
        var box=start.getBoundingBox();
        if (structure.step() == GenerationStep.Decoration.RAW_GENERATION) {
            var center = box.getCenter();
            int ground = context.chunkGenerator().getBaseHeight(center.getX(), center.getZ(),
                    Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
            // Keep deep raw-generation ruins and caves. Surface buildings may have buried foundations.
            if (box.maxY() < ground - 12) return false;
        }
        var builder=RailwayBuilder.getInstance(context.seed(),level);
        var visited=new HashSet<RegionalTopology.Unit>();
        for(int x=Math.floorDiv(box.minX()-56,4096);x<=Math.floorDiv(box.maxX()+56,4096);x++)
            for(int z=Math.floorDiv(box.minZ()-56,4096);z<=Math.floorDiv(box.maxZ()+56,4096);z++) {
                var cell=new RegionPos(x,z);var unit=RegionalRailwayNetwork.unit(cell);
                if(!visited.add(unit)||!RegionalRailwayNetwork.active(cell))continue;
                var plan=RegionalRailwayNetwork.plan(builder,level,cell);
                if(intersects(plan,box)) {
                    var id=level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(structure);
                    Tongdarailway.LOGGER.info("[EowRail] Cancel surface structure {} start={} footprint={} conflictingSystem={}",id,start.getChunkPos(),box,unit.id());
                    return true;
                }
            }
        return false;
    }
    public static boolean intersects(RegionalRailwayNetwork.Plan plan,BoundingBox box) {
        for(var station:plan.stations()) {
            var b=station.stationTemplate().getBeardifierBox(station.placePos());
            if(overlap(box,b.minX(),b.minZ(),b.maxX(),b.maxZ(),56))return true;
        }
        for(var route:plan.routes())for(var segment:route.way().getSegments()) {
            int count=Math.max(1,(int)Math.ceil(segment.getLength()/4));
            for(int i=0;i<=count;i++){var p=segment.getPointAt(i/(double)count);if(contains(box,(int)Math.floor(p.x),(int)Math.floor(p.z),24))return true;}
        }
        return false;
    }
}
