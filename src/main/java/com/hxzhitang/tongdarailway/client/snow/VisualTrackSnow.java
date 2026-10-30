package com.hxzhitang.tongdarailway.client.snow;

import com.hxzhitang.tongdarailway.VisualSnowConfig;
import com.hxzhitang.tongdarailway.railway.snow.SnowOverlayPolicy;
import com.mojang.blaze3d.vertex.*;
import com.simibubi.create.content.trains.track.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.event.level.ChunkEvent;
import java.util.*;

/** A client-only, cached snow surface. Native rail blocks, entities and railway data remain untouched. */
@EventBusSubscriber(modid="tongdarailway",value=Dist.CLIENT)
public final class VisualTrackSnow {
    private record Face(Vec3 a,Vec3 b,Vec3 c,Vec3 d,Vec3 normal,int light) {}
    private record Mesh(List<Face> faces,Set<Long> dependencies,AABB bounds) {}
    private static final Map<Long,Mesh> MESHES=new HashMap<>();
    private static final Map<Long,Set<Long>> DEPENDENTS=new HashMap<>();
    private static final Set<Long> KNOWN=new HashSet<>();
    private static final LinkedHashSet<Long> DIRTY=new LinkedHashSet<>();
    private static final ResourceLocation SNOW=ResourceLocation.withDefaultNamespace("block/snow");
    private static ClientLevel owner;
    private static boolean wasEnabled;
    private VisualTrackSnow() {}

    private static LevelChunk loaded(ClientLevel level,long key) {
        var p=new ChunkPos(key);return level.getChunkSource().getChunk(p.x,p.z,ChunkStatus.FULL,false);
    }
    private static void bind(ClientLevel level) {
        if(owner==level)return;
        owner=level;MESHES.clear();DEPENDENTS.clear();KNOWN.clear();DIRTY.clear();wasEnabled=false;
        // Also handles enabling/reloading the addon after chunks already reached the client.
        var mc=Minecraft.getInstance();if(level==null||mc.player==null)return;
        var at=mc.player.chunkPosition();int radius=mc.options.getEffectiveRenderDistance()+1;
        for(int x=at.x-radius;x<=at.x+radius;x++)for(int z=at.z-radius;z<=at.z+radius;z++) {
            long key=ChunkPos.asLong(x,z);if(loaded(level,key)!=null){KNOWN.add(key);DIRTY.add(key);}
        }
    }
    private static void affected(long key) {
        if(KNOWN.contains(key))DIRTY.add(key);
        var dependent=DEPENDENTS.get(key);if(dependent!=null)DIRTY.addAll(dependent);
    }
    @SubscribeEvent public static void load(ChunkEvent.Load event) {
        if(!(event.getLevel() instanceof ClientLevel level))return;
        var mc=Minecraft.getInstance();long key=event.getChunk().getPos().toLong();
        mc.execute(()->{if(mc.level!=level)return;bind(level);KNOWN.add(key);DIRTY.add(key);affected(key);});
    }
    @SubscribeEvent public static void unload(ChunkEvent.Unload event) {
        if(!(event.getLevel() instanceof ClientLevel level))return;
        var mc=Minecraft.getInstance();long key=event.getChunk().getPos().toLong();
        mc.execute(()->{if(owner!=level)return;KNOWN.remove(key);DIRTY.remove(key);affected(key);remove(key);});
    }
    /** Called only by a client mixin; changes are queued, never sampled on packet/render callbacks. */
    public static void changed(ClientLevel level,BlockPos pos,BlockState oldState,BlockState newState) {
        // Track placement and removal of the supporting floor also change the snow footprint.
        if(!railCell(oldState)&&!railCell(newState)&&!snow(oldState)&&!snow(newState)
                &&!railCell(level.getBlockState(pos.above())))return;
        var mc=Minecraft.getInstance();var immutable=pos.immutable();
        mc.execute(()->{
            if(mc.level!=level)return;bind(level);
            int cx=immutable.getX()>>4,cz=immutable.getZ()>>4;
            for(int x=cx-1;x<=cx+1;x++)for(int z=cz-1;z<=cz+1;z++)affected(ChunkPos.asLong(x,z));
        });
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();bind(mc.level);if(owner==null)return;
        boolean enabled=VisualSnowConfig.ENABLED.get();
        if(!enabled){if(wasEnabled){MESHES.clear();DEPENDENTS.clear();DIRTY.clear();}wasEnabled=false;return;}
        if(!wasEnabled){DIRTY.addAll(KNOWN);wasEnabled=true;}
        long deadline=System.nanoTime()+2_000_000;int rebuilt=0;
        while(!DIRTY.isEmpty()&&rebuilt<8) {
            var iterator=DIRTY.iterator();long key=iterator.next();iterator.remove();
            var chunk=loaded(owner,key);remove(key);
            if(chunk!=null&&KNOWN.contains(key)) {
                var mesh=build(owner,chunk);MESHES.put(key,mesh);
                for(long dependency:mesh.dependencies())DEPENDENTS.computeIfAbsent(dependency,k->new HashSet<>()).add(key);
            }
            if(++rebuilt>0&&System.nanoTime()>=deadline)break;
        }
    }
    private static void remove(long key) {
        var old=MESHES.remove(key);if(old==null)return;
        for(long dependency:old.dependencies()) {
            var set=DEPENDENTS.get(dependency);if(set==null)continue;
            set.remove(key);if(set.isEmpty())DEPENDENTS.remove(dependency);
        }
    }
    private static boolean snow(BlockState state) {
        var id=BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if(!id.getNamespace().equals("snowrealmagic")&&!state.is(net.minecraft.world.level.block.Blocks.SNOW)&&!state.is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK))return false;
        int layers=1;
        for(var property:state.getProperties())if(property.getName().equals("layers")) {
            var value=state.getValue(property);if(value instanceof Integer i)layers=i;
        }
        return SnowOverlayPolicy.snowIdentifier(id.toString(),layers);
    }
    private static boolean railCell(BlockState state) {
        // Curve helpers can replace real snow. Cover their supported cells too,
        // using the lowered snow surface so the cover stays beneath Create's sleepers.
        return state.getBlock() instanceof TrackBlock||state.getBlock() instanceof FakeTrackBlock;
    }
    private static int snowyNeighbours(ClientLevel world,BlockPos base,Set<Long> dependencies) {
        int count=0;
        // Snow follows the world's horizontal block grid, regardless of the rail direction.
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++) {
            if(x==0&&z==0)continue;
            var pos=base.offset(x,0,z);long key=new ChunkPos(pos).toLong();dependencies.add(key);
            if(loaded(world,key)!=null&&snow(world.getBlockState(pos)))count++;
        }
        return count;
    }
    private static Face face(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int light) {
        return new Face(a,b,c,d,b.subtract(a).cross(d.subtract(a)).normalize(),light);
    }
    private static Mesh build(ClientLevel world,LevelChunk chunk) {
        var faces=new ArrayList<Face>();var dependencies=new HashSet<Long>();
        int minX=chunk.getPos().getMinBlockX(),minZ=chunk.getPos().getMinBlockZ();var origin=new Vec3(minX,0,minZ);
        var sections=chunk.getSections();
        for(int section=0;section<sections.length;section++) {
            var blocks=sections[section];
            if(blocks==null||blocks.hasOnlyAir()||!blocks.maybeHas(VisualTrackSnow::railCell))continue;
            int minY=chunk.getMinBuildHeight()+section*16;
            for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++) {
                var state=blocks.getBlockState(x,y,z);if(!railCell(state))continue;
                if(state.getBlock() instanceof TrackBlock&&!SnowOverlayPolicy.supports(state.getValue(TrackBlock.SHAPE)))continue;
                var pos=new BlockPos(minX+x,minY+y,minZ+z);var below=pos.below();
                // Cover only supported track/helper cells; rising curves must not gain floating snow.
                boolean supported=world.getBlockState(below).isFaceSturdy(world,below,Direction.UP);
                boolean waterlogged=state.getValue(BlockStateProperties.WATERLOGGED);
                if(!supported||waterlogged)continue;
                if(!SnowOverlayPolicy.canCover(supported,waterlogged,snowyNeighbours(world,pos,dependencies)))continue;
                int light=LevelRenderer.getLightColor(world,pos);var at=Vec3.atLowerCornerOf(pos).subtract(origin);
                double h=SnowOverlayPolicy.THIN_SNOW;
                // Exactly one world-aligned square per occupied block; curves need no extruded mesh.
                faces.add(face(at.add(0,h,0),at.add(0,h,1),at.add(1,h,1),at.add(1,h,0),light));
            }
        }
        AABB bounds=null;
        for(var face:faces)for(var vertex:List.of(face.a(),face.b(),face.c(),face.d())) {
            var point=vertex.add(origin);var box=new AABB(point,point);
            bounds=bounds==null?box:bounds.minmax(box);
        }
        return new Mesh(List.copyOf(faces),Set.copyOf(dependencies),bounds==null?null:bounds.inflate(0.1));
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES||owner!=Minecraft.getInstance().level||!wasEnabled)return;
        var mc=Minecraft.getInstance();var camera=event.getCamera().getPosition();double range=(mc.options.getEffectiveRenderDistance()+1)*16.0;
        // This is dynamic world geometry, so use the block-entity atlas shader rather than
        // the terrain shader, whose chunk offsets and vertex layout are managed by Iris.
        var buffers=mc.renderBuffers().bufferSource();var type=Sheets.cutoutBlockSheet();
        var sprite=mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(SNOW);
        var pose=event.getPoseStack();var out=buffers.getBuffer(type);
        for(var entry:MESHES.entrySet()) {
            var mesh=entry.getValue();if(mesh.bounds()==null||mesh.bounds().distanceToSqr(camera)>range*range||!event.getFrustum().isVisible(mesh.bounds()))continue;
            var chunk=new ChunkPos(entry.getKey());pose.pushPose();
            try {
                // The world renderer already supplies the camera rotation globally. Only
                // translate cached chunk-local vertices; never apply that rotation twice.
                pose.translate(chunk.getMinBlockX()-camera.x,-camera.y,chunk.getMinBlockZ()-camera.z);
                for(var f:mesh.faces()) {
                    vertex(out,pose.last(),f.a(),f.normal(),sprite.getU0(),sprite.getV0(),f.light());
                    vertex(out,pose.last(),f.b(),f.normal(),sprite.getU0(),sprite.getV1(),f.light());
                    vertex(out,pose.last(),f.c(),f.normal(),sprite.getU1(),sprite.getV1(),f.light());
                    vertex(out,pose.last(),f.d(),f.normal(),sprite.getU1(),sprite.getV0(),f.light());
                }
            } finally {pose.popPose();}
        }
        // Submit while the normal world view is still active, before later rendering stages.
        buffers.endBatch(type);
    }
    private static void vertex(VertexConsumer out,PoseStack.Pose pose,Vec3 p,Vec3 normal,float u,float v,int light) {
        out.addVertex(pose,(float)p.x,(float)p.y,(float)p.z).setColor(255,255,255,255).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose,(float)normal.x,(float)normal.y,(float)normal.z);
    }
}
