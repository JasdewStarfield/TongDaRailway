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
    public record Node(String id,BlockPos rail,Direction facing,BlockPos center,int radius,String role,String station,int rotation) {
        public Node(String id,BlockPos rail,Direction facing,BlockPos center,int radius){this(id,rail,facing,center,radius,"site","",0);}
    }
    public record Link(String from,String to) { }
    public record Network(long seed,List<Node> nodes,List<Link> links) {
        public Network { nodes=List.copyOf(nodes);links=List.copyOf(links); }
    }
    private static final class PlanningState {
        Network network=new Network(0,List.of(),List.of());
        final Map<String,StationPlanner.StationGenInfo> stations=new java.util.concurrent.ConcurrentHashMap<>();
        boolean restored;RegionalTopology.Unit bounds;
    }
    private static final PlanningState LEGACY=new PlanningState();
    private static final ThreadLocal<PlanningState> CONTEXT=new ThreadLocal<>();
    private static PlanningState state(){var local=CONTEXT.get();return local==null?LEGACY:local;}
    public record RegionalResult(Network network,List<StationPlanner.StationGenInfo> stations,List<RoutePlanner.ResultWay> routes,JsonObject snapshot) {}
    /** Jobs for unrelated regions share immutable templates, but never their mutable station/route state. */
    public static RegionalResult planRegional(RailwayBuilder builder,ServerLevel level,Network network,JsonObject saved,RegionalTopology.Unit bounds) {
        if(CONTEXT.get()!=null)throw new IllegalStateException("Recursive regional railway planning");
        var local=new PlanningState();local.bounds=bounds;CONTEXT.set(local);
        try {
            SurfaceStructurePolicy.begin(level,bounds,saved!=null);
            if(saved!=null)restoreAutomatic(saved,level.getSeed());else local.network=network;
            var routes=saved!=null&&saved.has("geometry")?RegionalGeometry.read(net.minecraft.nbt.TagParser.parseTag(saved.get("geometry").getAsString())):plan(builder,level);
            return new RegionalResult(local.network,stations(level.getSeed()),routes,snapshot());
        }catch(com.mojang.brigadier.exceptions.CommandSyntaxException invalid){throw new IllegalArgumentException("Invalid saved regional railway geometry",invalid);}
        finally{SurfaceStructurePolicy.end();CONTEXT.remove();}
    }
    private FixedRailwayNetwork() { }
    public static boolean enabled() {return Config.fixedNodeMode;}
    public static Network current() {return state().network;}
    public static void load() {
        AutomaticRailwayNetwork.reset();state().restored=false;
        state().stations.clear();
        state().network=new Network(0,List.of(),List.of());
        Path path=FMLPaths.CONFIGDIR.get().resolve("tongdarailway-fixed-nodes.json");
        if(!Files.isRegularFile(path))return;
        try {state().network=parse(Files.readString(path));}
        catch(Exception e){throw new IllegalStateException("Invalid fixed railway nodes: "+path,e);}
        Tongdarailway.LOGGER.info("[EowRail] Loaded {} fixed nodes and {} links for seed {}",
                state().network.nodes().size(),state().network.links().size(),state().network.seed());
    }
    public static void setAutomatic(Network network){state().network=network;state().stations.clear();state().restored=false;}
    /** Restore exact template placements rather than re-running terrain search after a restart. */
    public static void restoreAutomatic(JsonObject saved,long seed) {
        if(saved.get("seed").getAsLong()!=seed)throw new IllegalArgumentException("Automatic railway snapshot belongs to another seed");
        state().network=parse(saved.toString());state().stations.clear();
        for(var item:saved.getAsJsonArray("placements")) {
            var row=item.getAsJsonObject();var n=node(row.get("node").getAsString());
            state().stations.put(n.id(),new StationPlanner.StationGenInfo(template(n,row.get("rotation").getAsInt()),pos(row.getAsJsonArray("place"))));
        }
        state().restored=true;
    }
    private static JsonObject snapshot() {
        var saved=new JsonObject();saved.addProperty("format",2);saved.addProperty("seed",state().network.seed());
        var nodes=new JsonArray();var placements=new JsonArray();var links=new JsonArray();
        for(var n:state().network.nodes()) {
            var row=new JsonObject();row.addProperty("id",n.id());row.add("rail",jsonPos(n.rail()));row.add("protected_center",jsonPos(n.center()));
            row.addProperty("protected_radius",n.radius());row.addProperty("facing",n.facing().getName());row.addProperty("role",n.role());row.addProperty("station",n.station());row.addProperty("rotation",n.rotation());nodes.add(row);
            var plan=station(n);var place=new JsonObject();place.addProperty("node",n.id());place.add("place",jsonPos(plan.placePos()));place.addProperty("rotation",switch(plan.stationTemplate().rotation){case NONE->0;case CLOCKWISE_90->90;case CLOCKWISE_180->180;case COUNTERCLOCKWISE_90->270;});placements.add(place);
        }
        for(var l:state().network.links()){var row=new JsonObject();row.addProperty("from",l.from());row.addProperty("to",l.to());links.add(row);}
        saved.add("surface_structure_footprints",SurfaceStructurePolicy.footprints());saved.add("nodes",nodes);saved.add("links",links);saved.add("placements",placements);return saved;
    }
    private static JsonArray jsonPos(BlockPos p){var a=new JsonArray();a.add(p.getX());a.add(p.getY());a.add(p.getZ());return a;}
    /** Validate all links before any terrain is changed; nodes are supplied before world generation. */
    public static Network parse(String text) {
        var root=JsonParser.parseString(text).getAsJsonObject();
        int format=root.get("format").getAsInt();
        if(format!=1&&format!=2)throw new IllegalArgumentException("Unknown fixed network format");
        var nodes=new ArrayList<Node>();var names=new HashSet<String>();
        for(var element:root.getAsJsonArray("nodes")) {
            var n=element.getAsJsonObject();String id=n.get("id").getAsString();
            if(!names.add(id))throw new IllegalArgumentException("Duplicate node: "+id);
            var rail=pos(n.getAsJsonArray("rail"));var center=pos(n.getAsJsonArray("protected_center"));
            Direction facing=Direction.byName(n.get("facing").getAsString());int radius=n.get("protected_radius").getAsInt();
            if(facing==null||facing.getAxis().isVertical()||radius<0||radius>128||rail.getY()<-64||rail.getY()>320)
                throw new IllegalArgumentException("Invalid rail anchor: "+id);
            if(radius>0&&Math.abs(rail.getX()-center.getX())<=radius+8&&Math.abs(rail.getZ()-center.getZ())<=radius+8)
                throw new IllegalArgumentException("Rail anchor must remain outside the protected site: "+id);
            String role=n.has("role")?n.get("role").getAsString():"site";
            String station=n.has("station")?n.get("station").getAsString():"";
            int rotation=n.has("rotation")?n.get("rotation").getAsInt():0;
            if(!station.matches("[a-z0-9_]*")||rotation<0||rotation>270||rotation%90!=0)throw new IllegalArgumentException("Invalid station: "+id);
            nodes.add(new Node(id,rail,facing,center,radius,role,station,rotation));
        }
        var links=new ArrayList<Link>();var pairs=new HashSet<String>();
        JsonArray supplied=root.has("links")?root.getAsJsonArray("links"):new JsonArray();
        for(var element:supplied) {
            var n=element.getAsJsonObject();String a=n.get("from").getAsString(),b=n.get("to").getAsString();
            if(a.equals(b)||!names.contains(a)||!names.contains(b))throw new IllegalArgumentException("Invalid link");
            String pair=a.compareTo(b)<0?a+":"+b:b+":"+a;
            if(!pairs.add(pair))throw new IllegalArgumentException("Duplicate link");
            var left=nodes.stream().filter(v->v.id().equals(a)).findFirst().orElseThrow();
            var right=nodes.stream().filter(v->v.id().equals(b)).findFirst().orElseThrow();
            if(left.rail().distSqr(right.rail())>4096L*4096)throw new IllegalArgumentException("Prototype links are limited to 4096 blocks");
            links.add(new Link(a,b));
        }
        if(nodes.size()>8||links.size()>12)throw new IllegalArgumentException("Regional node budget exceeded");
        if(format==2&&root.has("auto_connect")&&root.get("auto_connect").getAsBoolean()) {
            if(!links.isEmpty())throw new IllegalArgumentException("Automatic and explicit links cannot be mixed");
            links.addAll(RegionalRailwayGraph.connect(nodes));
        }
        return new Network(root.get("seed").getAsLong(),nodes,links);
    }
    private static BlockPos pos(JsonArray array) {
        if(array.size()!=3)throw new IllegalArgumentException("Expected XYZ");
        int x=array.get(0).getAsInt(),y=array.get(1).getAsInt(),z=array.get(2).getAsInt();
        if(Math.abs((long)x)>29_000_000||Math.abs((long)z)>29_000_000)throw new IllegalArgumentException("Outside world bounds");
        return new BlockPos(x,y,z);
    }
    public static boolean protects(BlockPos pos) {
        if(SurfaceStructurePolicy.blocked(pos.getX(),pos.getZ()))return true;
        if(RegionalRailwayNetwork.enabled()) {
            // A landmark can straddle the border of two independently planned systems.
            if(RegionalRailwayNetwork.protects(pos))return true;
            if(CONTEXT.get()==null)return false;
        }
        for(var n:state().network.nodes())if(n.radius()>0&&Math.abs(pos.getX()-n.center().getX())<=n.radius()
                &&Math.abs(pos.getZ()-n.center().getZ())<=n.radius())return true;
        return false;
    }
    public static boolean intersects(RegionPos region,long seed) {
        if(state().network.seed()!=seed)return false;
        if(AutomaticRailwayNetwork.enabled()&&state().network.links().isEmpty())return true;
        int size=Tongdarailway.CHUNK_GROUP_SIZE*16,left=region.x()*size,top=region.z()*size;
        for(var link:state().network.links()) {
            var a=node(link.from()).rail();var b=node(link.to()).rail();
            if(Math.max(a.getX(),b.getX())+768>=left&&Math.min(a.getX(),b.getX())-768<left+size
                    &&Math.max(a.getZ(),b.getZ())+768>=top&&Math.min(a.getZ(),b.getZ())-768<top+size)return true;
        }
        return false;
    }
    private static Node node(String id) {return state().network.nodes().stream().filter(n->n.id().equals(id)).findFirst().orElseThrow();}
    public static List<RoutePlanner.ResultWay> plan(RailwayBuilder builder,ServerLevel level) {
        if(state().network.seed()!=level.getSeed())return List.of();
        boolean automatic=AutomaticRailwayNetwork.enabled();
        if(automatic&&!state().restored) {
            var candidates=new ArrayList<>(state().network.nodes());
            if(candidates.size()<8&&!candidates.isEmpty())candidates.addAll(AutomaticRailwayNetwork.mines(level,state().network.nodes().getFirst().center()).stream()
                    .filter(n->state().bounds==null||state().bounds.contains(n.center().getX(),n.center().getZ(),256))
                    .limit(8-candidates.size()).toList());
            state().network=new Network(state().network.seed(),candidates,state().bounds==null?RegionalRailwayGraph.connectAvailable(candidates):RegionalRailwayNetwork.longLinks(candidates));
        }
        prepareStations(builder,level);
        if(automatic&&!state().restored)state().network=new Network(state().network.seed(),state().network.nodes(),state().bounds==null?RegionalRailwayGraph.connectAvailable(state().network.nodes()):RegionalRailwayNetwork.longLinks(state().network.nodes()));
        var result=new ArrayList<RoutePlanner.ResultWay>();
        var uses=new HashMap<String,Integer>();
        var assigned=assignPorts();
        var successful=new ArrayList<Link>();
        for(var link:state().network.links()) {
            long start=System.nanoTime();var a=node(link.from());var b=node(link.to());
            RoutePlanner.ResultWay route=null;
            com.hxzhitang.tongdarailway.structure.StationTemplate.Exit chosenA=null,chosenB=null;
            // Terrain-grounded stations can have different elevations. Try native exits before
            // taking a bounded wider approach; every candidate keeps the original grade checks.
            var optionsA=List.of(assigned.get(a.id()+"/"+b.id()));var optionsB=List.of(assigned.get(b.id()+"/"+a.id()));
            search: for(var ae:optionsA)for(var be:optionsB) {
                var candidate=planConnection(builder,level,ae,be,0);
                if(candidate==null)continue;
                route=candidate;chosenA=ae;chosenB=be;break search;
            }
            if(route==null&&state().bounds==null)for(int detour:new int[]{512,-512}) {
                var ae=optionsA.getFirst();var be=optionsB.getFirst();
                var candidate=planConnection(builder,level,ae,be,detour);
                if(candidate!=null){route=candidate;chosenA=ae;chosenB=be;break;}
            }
            if(route==null) {
                if(!automatic)throw new IllegalStateException("No valid fixed route: "+link);
                Tongdarailway.LOGGER.info("[EowRail] Skipped unsuitable optional connection {}",link);continue;
            }
            uses.merge(a.id()+chosenA.exitPos(),1,Integer::sum);
            uses.merge(b.id()+chosenB.exitPos(),1,Integer::sum);
            result.add(route);successful.add(link);
            var planned=route;
            long kept=planned.trackPutInfos().stream().filter(t->builder.damagePlan(planned).keep(planned.way(),t)).count();
            Tongdarailway.LOGGER.info("[EowRail] Planned {} -> {}: {} blocks, {} / {} track records retained, {} ms",
                    a.id(),b.id(),Math.round(route.way().getTotalLength()),kept,route.trackPutInfos().size(),(System.nanoTime()-start)/1e6);
        }
        if(state().bounds!=null)for(var required:state().network.nodes().stream().filter(n->n.role().equals("critical")).toList()) {
            if(successful.stream().anyMatch(l->l.from().equals(required.id())||l.to().equals(required.id())))continue;
            boolean joined=false;
            // Required sites may retry their two native mouths against a small set of nearby stations.
            var alternatives=state().network.nodes().stream().filter(n->!n.id().equals(required.id()))
                    .sorted(Comparator.comparingDouble(n->n.rail().distSqr(required.rail()))).limit(3).toList();
            search:for(var other:alternatives)for(var ae:station(required).getExits())for(var be:station(other).getExits()) {
                var route=directConnection(ae,be);
                if(route==null)continue;
                result.add(route);successful.add(new Link(required.id(),other.id()));joined=true;break search;
            }
            if(!joined) {
                // A short unfinished outward branch is preferable to a required station with no railway.
                // It uses real straight track and the same protected-landmark/region limits as every route.
                spur:for(var exit:station(required).getExits())for(int length:new int[]{128,96,64}) {
                    var route=new RoutePlanner.ResultWay(new com.hxzhitang.tongdarailway.util.CurveRoute(),new ArrayList<>());
                    var from=Vec3.atLowerCornerOf(exit.exitPos());route.addLine(from,from.add(exit.dir().scale(length)));
                    if(!validRoute(route))continue;
                    result.add(route);joined=true;
                    Tongdarailway.LOGGER.info("[EowRail] Required station {} keeps a {}-block unfinished outward branch",required.id(),length);break spur;
                }
            }
            if(!joined)throw new IllegalStateException("Required station has no safe outward railway: "+required.id());
        }
        if(automatic) {
            // Disconnected optional candidates have no station; critical stations remain even if a route fails.
            var reachable=new HashSet<String>();state().network.nodes().stream().filter(n->n.role().equals("critical")).forEach(n->reachable.add(n.id()));
            // Independent regional components are useful repair opportunities even if a joining route failed.
            if(state().bounds!=null)for(var link:successful){reachable.add(link.from());reachable.add(link.to());}
            boolean changed;do{changed=false;for(var l:successful)if(reachable.contains(l.from())||reachable.contains(l.to())){changed|=reachable.add(l.from());changed|=reachable.add(l.to());}}while(changed);
            state().network=new Network(state().network.seed(),state().network.nodes().stream().filter(n->reachable.contains(n.id())).toList(),successful.stream().filter(l->reachable.contains(l.from())&&reachable.contains(l.to())).toList());
            result.removeIf(r->!reachable.contains(currentNodeFor(r.trackPutInfos().getFirst().pos())));
            state().stations.keySet().retainAll(reachable);if(state().bounds==null)AutomaticRailwayNetwork.save(snapshot());state().restored=true;
        }
        return List.copyOf(result);
    }
    private static String currentNodeFor(BlockPos exit) {
        for(var n:state().network.nodes())if(station(n).getExits().stream().anyMatch(e->e.exitPos().equals(exit)))return n.id();
        return "";
    }
    public static boolean intersectsProtectedSite(com.hxzhitang.tongdarailway.structure.TrackPutInfo track) {
        if(protects(track.pos()))return true;
        if(track.bezier()==null)return false;
        var b=track.bezier();var curve=com.hxzhitang.tongdarailway.util.CurveRoute.BezierSegment.getCubicBezier(b.start(),b.startAxis(),b.endOffset(),b.endAxis());
        int count=Math.max(8,(int)Math.ceil(b.endOffset().length()/3));
        for(int i=0;i<=count;i++)if(protects(BlockPos.containing(curve.getPointAt(i/(double)count))))return true;
        return false;
    }
    /** Resolve only named upstream templates already loaded by the resource manager. */
    public static List<StationPlanner.StationGenInfo> stations(long seed) {
        if(state().network.seed()!=seed)return List.of();
        return state().network.nodes().stream().filter(n->!n.station().isEmpty()).map(FixedRailwayNetwork::station).toList();
    }
    private static StationPlanner.StationGenInfo station(Node node) {
        var plan=state().stations.get(node.id());
        if(plan==null)throw new IllegalStateException("Station terrain has not been prepared: "+node.id());
        return plan;
    }
    private static com.hxzhitang.tongdarailway.structure.StationTemplate template(Node node) {
        return template(node,node.rotation());
    }
    private static com.hxzhitang.tongdarailway.structure.StationTemplate template(Node node,int rotation) {
        // The upstream loader retains its full directory and suffix in template pool IDs.
        int id=("railway_structure/station/"+node.station()+".json").hashCode()+rotation;
        var template=com.hxzhitang.tongdarailway.structure.ModStructureManager.station2.getById(id);
        if(template==null)template=com.hxzhitang.tongdarailway.structure.ModStructureManager.station4.getById(id);
        if(template==null)throw new IllegalArgumentException("Unknown station template: "+node.station());
        return template;
    }
    private static void prepareStations(RailwayBuilder builder,ServerLevel level) {
        var accepted=new ArrayList<Node>();
        for(var original:state().network.nodes()) {
            var node=original;
            if(node.station().isEmpty()||state().stations.containsKey(node.id())){accepted.add(node);continue;}
            try {prepareStation(builder,level,node);accepted.add(node);}
            catch(IllegalArgumentException failure) {
                if(!AutomaticRailwayNetwork.enabled())throw failure;
                if(!node.role().equals("critical")&&!node.role().equals("border_halt")&&!(state().bounds!=null&&node.role().equals("corridor_stop")&&!node.station().equals("eow_small_halt"))){Tongdarailway.LOGGER.info("[EowRail] Skipped station {}: {}",node.id(),failure.getMessage());continue;}
                node=new Node(node.id(),node.rail(),node.facing(),node.center(),node.radius(),node.role(),"eow_small_halt",node.rotation());
                try {prepareStation(builder,level,node);accepted.add(node);}
                catch(IllegalArgumentException smallerFailure){if(node.role().equals("critical"))throw smallerFailure;Tongdarailway.LOGGER.info("[EowRail] Skipped border halt {}: {}",node.id(),smallerFailure.getMessage());}
            }
        }
        state().network=new Network(state().network.seed(),accepted,state().network.links());
    }
    private static void prepareStation(RailwayBuilder builder,ServerLevel level,Node node) {
        var neighbours=neighbours(node).stream().map(n->Vec3.atLowerCornerOf(n.rail().subtract(node.rail()))).toList();
        boolean nearby=AutomaticRailwayNetwork.enabled()&&node.role().equals("critical");
        long selectionStart=System.nanoTime();
        StationPlanner.StationGenInfo best=null;StationSiteSelector.Site chosen=null;double cost=Double.POSITIVE_INFINITY;
        int rotation=node.rotation();
        for(int turn=0;turn<4;turn++) {
            int candidate=(node.rotation()+turn*90)%360;var trial=template(node,candidate);
            double score=StationPortAssignment.solve(neighbours,trial.getExits().stream().map(e->e.dir()).toList()).cost();
            if(!nearby){if(score<cost-1e-8){cost=score;rotation=candidate;}continue;}
            try {
                var site=selectStationSite(builder,level,node,trial);
                // Joint rotation/position selection avoids pushing a gate-side station away just
                // to preserve a rail-port orientation chosen before the site was inspected.
                double value=StationSiteSelector.nearbyCost(site,CriticalStationSiting.approach(node.center()))+score*.25;
                if(value<cost){cost=value;chosen=site;best=new StationPlanner.StationGenInfo(trial,site.rail().below(trial.getExits().stream().mapToInt(e->e.exitPos().getY()).min().orElseThrow()));}
            }catch(IllegalArgumentException unsuitable){/* Try the remaining native template rotations. */}
        }
        if(!nearby) {
            var trial=template(node,rotation);chosen=selectStationSite(builder,level,node,trial);
            best=new StationPlanner.StationGenInfo(trial,chosen.rail().below(trial.getExits().stream().mapToInt(e->e.exitPos().getY()).min().orElseThrow()));
        }
        if(best==null)throw new IllegalArgumentException("No nearby native station placement for "+node.id());
        state().stations.put(node.id(),best);
        Tongdarailway.LOGGER.info("[EowRail] Grounded station {} at {}: terrain {}..{}, fill {}, cut {}",node.id(),chosen.rail(),chosen.minHeight(),chosen.maxHeight(),chosen.fill(),chosen.cut());
        if(nearby)Tongdarailway.LOGGER.info("[EowRail] Landmark station {}: centerDistance={}, entranceDistance={}, rotation={}, selectionMs={}",node.id(),
                Math.hypot(chosen.rail().getX()-node.center().getX(),chosen.rail().getZ()-node.center().getZ()),
                Math.hypot(chosen.rail().getX()-CriticalStationSiting.approach(node.center()).getX(),chosen.rail().getZ()-CriticalStationSiting.approach(node.center()).getZ()),best.stationTemplate().rotation,(System.nanoTime()-selectionStart)/1e6);
    }
    private static StationSiteSelector.Site selectStationSite(RailwayBuilder builder,ServerLevel level,Node node,com.hxzhitang.tongdarailway.structure.StationTemplate template) {
        int width=template.getWidth(),depth=template.getDepth();
        // One shared 32-block grid bounds the coarse survey across all four rotations.
        // The exact 25-column footprint and dry-ground checks below retain full precision.
        boolean nearby=AutomaticRailwayNetwork.enabled()&&node.role().equals("critical");
        java.util.function.IntBinaryOperator coarse=(x,z)->builder.getExactHeight(level,
                nearby?Math.floorDiv(x+16,32)*32:x,nearby?Math.floorDiv(z+16,32)*32:z);
        java.util.function.IntBinaryOperator exact=(x,z)->{int h=builder.getExactHeight(level,x,z);return builder.hasSolidBaseSurface(level,x,z,h)?h:level.getSeaLevel();};
        java.util.function.BiPredicate<Integer,Integer> allowed=(x,z)-> {
                int minX=x+1-width/2,minZ=z+1-depth/2,maxX=minX+width-1,maxZ=minZ+depth-1;
                if(SurfaceStructurePolicy.stationBlocked(minX,minZ,maxX,maxZ))return false;
                if(state().bounds!=null&&(!state().bounds.contains(minX,minZ,64)||!state().bounds.contains(maxX,maxZ,64)))return false;
                if(state().bounds!=null)for(var landmark:RegionalRailwayNetwork.criticalSites()) {
                    if(node.role().equals("critical")) {
                        if(CriticalStationSiting.overlaps(landmark,minX,minZ,maxX,maxZ,32))return false;
                    } else if(maxX>=landmark.getX()-96&&minX<=landmark.getX()+96&&maxZ>=landmark.getZ()-96&&minZ<=landmark.getZ()+96)return false;
                    for(var exit:template.getExits())for(int distance=0;distance<=48;distance+=8) {
                        int px=x+exit.exitPos().getX()+(int)(exit.dir().x*distance),pz=z+exit.exitPos().getZ()+(int)(exit.dir().z*distance);
                        if(node.role().equals("critical")?CriticalStationSiting.blocksApproach(landmark,px,pz):Math.abs(px-landmark.getX())<=88&&Math.abs(pz-landmark.getZ())<=88)return false;
                    }
                }
                // Reserve the station's existing 32-block terrain blend around all real POIs.
                for(var other:state().network.nodes()) {
                    if(other.radius()==0)continue;
                    // The related landmark uses its actual footprint plus the existing terrain blend.
                    if(node.role().equals("critical")&&other.role().equals("critical")) {
                        if(CriticalStationSiting.overlaps(other.center(),minX,minZ,maxX,maxZ,32))return false;
                        continue;
                    }
                    int radius=other.radius()+32;
                    if(maxX>=other.center().getX()-radius&&minX<=other.center().getX()+radius
                            &&maxZ>=other.center().getZ()-radius&&minZ<=other.center().getZ()+radius)return false;
                }
                // Keep both native approaches clear as well as the building footprint.
                for(var exit:template.getExits())for(int distance=0;distance<=48;distance+=8) {
                    int px=x+exit.exitPos().getX()+(int)(exit.dir().x*distance);
                    int pz=z+exit.exitPos().getZ()+(int)(exit.dir().z*distance);
                    for(var other:state().network.nodes())if(other.radius()>0) {
                        if(node.role().equals("critical")&&other.role().equals("critical")) {
                            if(CriticalStationSiting.blocksApproach(other.center(),px,pz))return false;
                        } else if(Math.abs(px-other.center().getX())<=other.radius()+24&&Math.abs(pz-other.center().getZ())<=other.radius()+24)return false;
                    }
                }
                for(var placed:state().stations.values()) {
                    var box=placed.stationTemplate().getBeardifierBox(placed.placePos());
                    if(maxX>=box.minX()-16&&minX<=box.maxX()+16&&maxZ>=box.minZ()-16&&minZ<=box.maxZ()+16)return false;
                }
                // Exact footprint columns, rather than the biome name, decide dry footing.
                return true;
            };
        if(nearby) {
            try{return StationSiteSelector.selectNearby(CriticalStationSiting.approach(node.center()),width,depth,level.getSeaLevel(),coarse,exact,allowed);}
            catch(IllegalArgumentException noNearSite) {
                // Prefer the existing smaller native halt before accepting a remote large station.
                if(!node.station().equals("eow_small_halt"))throw noNearSite;
                // Difficult seeds retain the former broad compact-station fallback instead of
                // making a strict proximity preference a new world-start requirement.
                return StationSiteSelector.select(node.rail(),width,depth,level.getSeaLevel(),coarse,exact,allowed,new int[]{128,256,384,512},64);
            }
        }
        return StationSiteSelector.select(node.rail(),width,depth,level.getSeaLevel(),coarse,exact,allowed,
                state().bounds!=null&&node.station().equals("eow_small_halt")?new int[]{128,192,320}:new int[]{128,192},
                AutomaticRailwayNetwork.enabled()&&node.station().equals("eow_small_halt")?64:32);
    }
    private static List<Node> neighbours(Node node) {
        return state().network.links().stream().filter(l->l.from().equals(node.id())||l.to().equals(node.id()))
                .map(l->node(l.from().equals(node.id())?l.to():l.from())).toList();
    }
    private static Map<String,com.hxzhitang.tongdarailway.structure.StationTemplate.Exit> assignPorts() {
        var result=new HashMap<String,com.hxzhitang.tongdarailway.structure.StationTemplate.Exit>();
        for(var node:state().network.nodes()) {
            var adjacent=neighbours(node);
            var exits=node.station().isEmpty()?List.of(new com.hxzhitang.tongdarailway.structure.StationTemplate.Exit(node.rail(),Vec3.atLowerCornerOf(node.facing().getNormal()))):station(node).getExits();
            var origin=node.station().isEmpty()?node.rail():station(node).placePos();
            var bearings=adjacent.stream().map(n->Vec3.atLowerCornerOf((n.station().isEmpty()?n.rail():station(n).placePos()).subtract(origin))).toList();
            var assignment=StationPortAssignment.solve(bearings,exits.stream().map(e->e.dir()).toList());
            for(int i=0;i<adjacent.size();i++) {
                var exit=exits.get(assignment.ports().get(i));result.put(node.id()+"/"+adjacent.get(i).id(),exit);
                Tongdarailway.LOGGER.info("[EowRail] Assigned {} -> {} mouth {} direction {}",node.id(),adjacent.get(i).id(),exit.exitPos(),exit.dir());
            }
        }
        return result;
    }
    private static RoutePlanner.ResultWay planConnection(RailwayBuilder builder,ServerLevel level,
            com.hxzhitang.tongdarailway.structure.StationTemplate.Exit ae,
            com.hxzhitang.tongdarailway.structure.StationTemplate.Exit be,int detour) {
        Vec3 av=Vec3.atLowerCornerOf(ae.exitPos()),bv=Vec3.atLowerCornerOf(be.exitPos());
        if(detour==0) {
            var direct=directConnection(ae,be);
            if(direct!=null)return direct;
        }
        var ap=ae.exitPos().offset(BlockPos.containing(ae.dir().scale(48)));
        var bp=be.exitPos().offset(BlockPos.containing(be.dir().scale(48)));
        int[] from={ap.getX(),ap.getZ(),ap.getY()},to={bp.getX(),bp.getZ(),bp.getY()};
        var con=new StationPlanner.ConnectionGenInfo(av,ae.dir(),bv,be.dir(),from,to);
        var center=RegionPos.regionPosFromWorldPos(ap.getX(),ap.getZ());
        int regions=Math.abs(center.x()-RegionPos.regionPosFromWorldPos(bp.getX(),bp.getZ()).x())
                +Math.abs(center.z()-RegionPos.regionPosFromWorldPos(bp.getX(),bp.getZ()).z())+2;
        AStarPathfinder.AdditionalCostFunction cost=(x,z)->blocksRoute(new BlockPos(x,80,z))
                ||(state().bounds!=null&&!state().bounds.contains(x,z,64))?100000:0;
        List<int[]> way;
        if(detour==0)way=AStarPathfinder.findPath(builder,from,Set.of(to),center,regions,cost);
        else {
            var side=new Vec3(-(bp.getZ()-ap.getZ()),0,bp.getX()-ap.getX()).normalize().scale(detour);
            int[] via={(ap.getX()+bp.getX())/2+(int)side.x,(ap.getZ()+bp.getZ())/2+(int)side.z};
            var first=AStarPathfinder.findPath(builder,from,Set.of(via),center,regions,cost);
            var second=AStarPathfinder.findPath(builder,via,Set.of(to),center,regions,cost);
            if(first.isEmpty()||second.isEmpty())return null;
            way=new ArrayList<>(first);way.addAll(second.subList(1,second.size()));
        }
        var route=new RoutePlanner().getWay(builder,way,con,level);
        // Reject whole plans before publication, including curves sweeping across protected POIs.
        if(!validRoute(route))return null;
        return route;
    }
    private static boolean validRoute(RoutePlanner.ResultWay route) {
        return route!=null&&route.trackPutInfos().stream().allMatch(t->NativeCurveSafety.valid(t)&&!intersectsProtectedSite(t)
                &&(state().bounds==null||(state().bounds.contains(t.pos().getX(),t.pos().getZ(),48)
                &&(t.bezier()==null||state().bounds.contains(t.pos().getX()+(int)t.bezier().endOffset().x,t.pos().getZ()+(int)t.bezier().endOffset().z,48)))));
    }
    private static RoutePlanner.ResultWay directConnection(
            com.hxzhitang.tongdarailway.structure.StationTemplate.Exit ae,
            com.hxzhitang.tongdarailway.structure.StationTemplate.Exit be) {
        // Three native sections keep bends level and put the height change on a long middle run.
        var a=Vec3.atLowerCornerOf(ae.exitPos());var b=Vec3.atLowerCornerOf(be.exitPos());
        var direction=com.hxzhitang.tongdarailway.util.MyMth.get8Dir(b.subtract(a));
        // Native track axes and endpoints use integer diagonals, avoiding fractional cell offsets.
        var heading=new Vec3(Math.round(direction.x),0,Math.round(direction.z));
        int half=(int)Math.ceil(Math.max(24,Math.abs(a.y-b.y)*6+24)/heading.length());
        for(int lead:new int[]{32,64,96}) {
            var start=a.add(ae.dir().scale(lead));var end=b.add(be.dir().scale(lead));
            var center=new Vec3(Math.floor((start.x+end.x)/2),0,Math.floor((start.z+end.z)/2));
            var middleA=center.subtract(heading.scale(half)).add(0,a.y,0);
            var middleB=center.add(heading.scale(half)).add(0,b.y,0);
            if(middleA.subtract(start).dot(ae.dir())<16||end.subtract(middleB).dot(be.dir().reverse())<16)continue;
            var route=new RoutePlanner.ResultWay(new com.hxzhitang.tongdarailway.util.CurveRoute(),new ArrayList<>());
            route.addLine(a,start);
            if(!route.connectWay(start,middleA,ae.dir(),heading.reverse(),true))continue;
            // A collinear grade needs one native Bezier. The general junction fitter adds
            // an extra block of straight extent, which can fold its final fragment backwards.
            route.addBezier(middleA,heading,middleB.subtract(middleA),heading.reverse());
            if(!route.connectWay(middleB,end,heading,be.dir(),true))continue;
            route.addLine(end,b);
            if(validRoute(route))return route;
        }
        var corner=cornerConnection(ae,be);
        return corner!=null&&validRoute(corner)?corner:null;
    }
    /** A bounded quarter-turn avoids asking the diagonal middle fitter for a whole ring quadrant. */
    static RoutePlanner.ResultWay cornerConnection(
            com.hxzhitang.tongdarailway.structure.StationTemplate.Exit ae,
            com.hxzhitang.tongdarailway.structure.StationTemplate.Exit be) {
        if(Math.abs(ae.dir().dot(be.dir()))>1e-6)return null;
        var a=Vec3.atLowerCornerOf(ae.exitPos());var b=Vec3.atLowerCornerOf(be.exitPos());
        var corner=new Vec3(Math.abs(ae.dir().x)>.5?b.x:a.x,Math.floor((a.y+b.y)/2),Math.abs(ae.dir().z)>.5?b.z:a.z);
        if(corner.subtract(a).dot(ae.dir())<128||corner.subtract(b).dot(be.dir())<128)return null;
        var entry=corner.subtract(ae.dir().scale(32));var exit=corner.subtract(be.dir().scale(32));
        var route=new RoutePlanner.ResultWay(new com.hxzhitang.tongdarailway.util.CurveRoute(),new ArrayList<>());
        if(a.y==entry.y)route.addLine(a,entry);else route.addBezier(a,ae.dir(),entry.subtract(a),ae.dir().reverse());
        if(!route.connectWay(entry,exit,ae.dir(),be.dir(),true))return null;
        if(b.y==exit.y)route.addLine(exit,b);else route.addBezier(exit,be.dir().reverse(),b.subtract(exit),be.dir());
        return route;
    }
    private static boolean blocksRoute(BlockPos pos) {
        if(protects(pos))return true;
        for(var plan:stations(state().network.seed())) {
            var box=plan.stationTemplate().getBeardifierBox(plan.placePos());
            if(pos.getX()>=box.minX()-4&&pos.getX()<=box.maxX()+4&&pos.getZ()>=box.minZ()-4&&pos.getZ()<=box.maxZ()+4)return true;
        }
        return false;
    }
    /** Small loading pads stop outside existing buildings and shafts, and write only their own chunk slice. */
    public static void placePlatforms(WorldGenLevel level,ChunkPos owner) {
        if(RegionalRailwayNetwork.enabled())return;
        if(state().network.seed()!=level.getSeed())return;
        for(var node:state().network.nodes())if(node.station().isEmpty())for(int along=-5;along<=5;along++)for(int side=3;side<=5;side++) {
            var p=node.rail().relative(node.facing(),along).relative(node.facing().getClockWise(),side).below();
            if(!owner.equals(new ChunkPos(p))||protects(p)||level.getBlockState(p).hasBlockEntity())continue;
            level.setBlock(p,(Math.floorMod(p.asLong(),5)==0?Blocks.MOSSY_COBBLESTONE:Blocks.COBBLESTONE).defaultBlockState(),2);
        }
    }
}
