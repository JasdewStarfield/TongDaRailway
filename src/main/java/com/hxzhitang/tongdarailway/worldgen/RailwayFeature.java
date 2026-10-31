package com.hxzhitang.tongdarailway.worldgen;

import com.hxzhitang.tongdarailway.Config;
import com.hxzhitang.tongdarailway.Tongdarailway;
import com.hxzhitang.tongdarailway.blocks.ITrackPreGenExtension;
import com.hxzhitang.tongdarailway.blocks.ModBlocks;
import com.hxzhitang.tongdarailway.blocks.TrackSpawnerBlockEntity;
import com.hxzhitang.tongdarailway.railway.RailwayBuilder;
import com.hxzhitang.tongdarailway.railway.RailwayMap;
import com.hxzhitang.tongdarailway.railway.RegionPos;
import com.hxzhitang.tongdarailway.railway.planner.StationPlanner;
import com.hxzhitang.tongdarailway.structure.ModStructureManager;
import com.hxzhitang.tongdarailway.structure.RailwayTemplate;
import com.hxzhitang.tongdarailway.structure.StationTemplate;
import com.hxzhitang.tongdarailway.structure.TrackPutInfo;
import com.hxzhitang.tongdarailway.util.CurveRoute;
import com.mojang.serialization.Codec;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.trains.track.*;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class RailwayFeature extends Feature<RailwayFeatureConfig> {
    public RailwayFeature(Codec<RailwayFeatureConfig> codec) {
        super(codec);
    }

    @Override
    public boolean place(@NotNull FeaturePlaceContext<RailwayFeatureConfig> ctx) {
        ChunkPos cPos = new ChunkPos(ctx.origin());
        RegionPos regionPos = RegionPos.regionPosFromChunkPos(cPos);
        WorldGenLevel world = ctx.level();

        RailwayBuilder builder = RailwayBuilder.getInstance(ctx.level().getSeed());
        if (builder == null) return false;

        RailwayMap railwayMap = builder.regionRailways.get(regionPos);
        if (railwayMap == null) return false;

        // 根据路线生成路基
        if (builder.regionRailways.containsKey(regionPos)) {
            if (railwayMap.routeMap.containsKey(cPos)) {
                placeRoadbed(railwayMap, cPos, world);
            }
        }

        // 放置车站
        for (StationPlanner.StationGenInfo stationPlace : railwayMap.stations) {
            var station = stationPlace.stationTemplate();
            if (station == null) continue;
            var pos = stationPlace.placePos();
            var center = pos.getCenter();

            if (station.getBoundChunks(center).contains(cPos)) {
                placeStation(cPos, center, station, world);
            }
        }

        // 放置铁轨刷怪笼
        // 也许机械动力铁轨天然厌恶生成时放置，我只能用这种愚蠢的方法了
        if (Config.useTrackSpawnerPlaceTrack && Config.generateTrackSpawner) {
            if (builder.regionRailways.containsKey(regionPos)) {
                if (railwayMap.trackMap.containsKey(cPos)) {
                    var firstInfo = railwayMap.trackMap.get(cPos).getFirst();
                    BlockPos checkPos = firstInfo.pos().offset(0, -1, 0);
                    if (!world.getBlockState(checkPos).is(ModBlocks.TRACK_SPAWNER.get())) {
                        world.setBlock(checkPos, ModBlocks.TRACK_SPAWNER.get().defaultBlockState(), 3);
                    }
                    if (world.getBlockEntity(checkPos) instanceof TrackSpawnerBlockEntity trackSpawner) {
                        trackSpawner.addTrackPutInfo(railwayMap.trackMap.get(cPos));
                    }
                }
            }
        }
        if (!Config.useTrackSpawnerPlaceTrack && railwayMap.abandoned) {
            com.hxzhitang.tongdarailway.railway.fixed.WorldgenTrackPlacement.place(world,cPos,builder.worldgenTracks(cPos));
        }
        if (!Config.useTrackSpawnerPlaceTrack && !railwayMap.abandoned) {
            // 咱就是说，能不能把文件编码改成UTF-8？IDEA一打开就是乱码
            // 抱歉，似乎我的IDEA一开始就是GBK编码 :-( 确实不想再改了

            // 放置铁轨
            if (builder.regionRailways.containsKey(regionPos) && railwayMap.trackMap.containsKey(cPos)) {
                List<TrackPutInfo> tracks = railwayMap.trackMap.get(cPos);
                tracks.forEach(track -> {
                    if (track.bezier() != null) {
                        if (Math.abs(track.bezier().endOffset().y) > 15){
                            Tongdarailway.LOGGER.warn("Railway track height offset is too large. Generation Failed at" + track.pos().toString());
                            return;
                        }
                        placeCurveTrack(world, track);
                    } else {
                        if (!world.getBlockState(track.pos()).is(AllBlocks.TRACK)) {
                            world.setBlock(track.pos(), AllBlocks.TRACK.getDefaultState().setValue(TrackBlock.SHAPE, track.shape()), 3);
                        }
                    }
                });
            }
        }

        if(railwayMap.abandoned)com.hxzhitang.tongdarailway.railway.fixed.FixedRailwayNetwork.placePlatforms(world,cPos);
        return true;
    }

    private void placeCurveTrack(WorldGenLevel world, TrackPutInfo track) {
        BlockPos startPos = track.pos();
        world.setBlock(startPos, AllBlocks.TRACK.getDefaultState().setValue(TrackBlock.SHAPE, track.shape()).setValue(TrackBlock.HAS_BE,true), 3);

        Vec3 offset = track.bezier().endOffset();
        BlockPos endPos = startPos.offset((int) offset.x, (int) offset.y, (int) offset.z);
        world.setBlock(endPos, AllBlocks.TRACK.getDefaultState().setValue(TrackBlock.SHAPE, track.endShape()).setValue(TrackBlock.HAS_BE,true), 3);

        Vec3 start1 = track.bezier().start().add(getStartVec(track.bezier().startAxis()));
        Vec3 start2 = track.bezier().start().add(track.bezier().endOffset()).add(getStartVec(track.bezier().endAxis()));

        Vec3 axis1 = track.bezier().startAxis();  // X轴正方向
        Vec3 axis2 = track.bezier().endAxis();  // Z轴正方向

        Vec3 normal1 = new Vec3(0, 1, 0);
        Vec3 normal2 = new Vec3(0, 1, 0);

        BezierConnection connection = new BezierConnection(
                Couple.create(startPos, endPos),
                Couple.create(start1, start2),
                Couple.create(axis1, axis2),
                Couple.create(normal1, normal2),
                true,  // teToTe
                false, // hasGirder
                TrackMaterial.ANDESITE
        );

        var tbe1 = world.getBlockEntity(startPos);
        var tbe2 = world.getBlockEntity(endPos);

        if(tbe1 != null && tbe2 != null){
            ((ITrackPreGenExtension) tbe1).addConnectionToPreGen(connection);
            ((ITrackPreGenExtension) tbe2).addConnectionToPreGen(connection.secondary());
        }
    }

    private static void placeStation(ChunkPos cPos, Vec3 center, StationTemplate station, WorldGenLevel world) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                var test = new Vec3(cPos.x*16+x, center.y + 1, cPos.z*16+z);
                if (!station.isInVoxel(test.subtract(center).add(-0.5, -0.5, -0.5)))
                    continue;
                for (int oy = station.getLowerBound(); oy < station.getUpperBound(); oy++) {
                    int y = oy + (int) center.y;
                    var p = new Vec3(cPos.x*16+x, y, cPos.z*16+z).add(-0.5, -0.5, -0.5);
                    var blockState = station.getBlockState(p.subtract(center));
                    if (blockState == null) {
                        // 应对机械动力蓝图保存的nbt文件不包含空气
                        if (station.isInVoxel(p.subtract(center))) {
                            blockState = Blocks.AIR.defaultBlockState();
                        } else {
                            continue;
                        }
                    }
                    BlockPos blockPos = new BlockPos(cPos.getMinBlockX()+x, y, cPos.getMinBlockZ()+z);
                    CompoundTag blockEntityTag = station.getBlockEntityTag(p.subtract(center));
                    boolean shouldLoadBlockEntity = blockEntityTag != null && blockState.hasBlockEntity();
                    if (shouldLoadBlockEntity) {
                        Clearable.tryClear(world.getBlockEntity(blockPos));
                        world.setBlock(blockPos, Blocks.BARRIER.defaultBlockState(), 20);
                    }
                    if(com.hxzhitang.tongdarailway.railway.fixed.FixedRailwayNetwork.enabled()
                            &&com.hxzhitang.tongdarailway.railway.fixed.FixedRailwayNetwork.protects(blockPos))continue;
                    world.setBlock(blockPos, blockState, 2);
                    if (shouldLoadBlockEntity) {
                        placeBlockEntity(world, blockPos, blockEntityTag);
                    }
                }
            }
        }
        if(com.hxzhitang.tongdarailway.railway.fixed.FixedRailwayNetwork.enabled()) {
            int floor=(int)center.y+1-station.getBeardifierGroundLevelDelta();
            for(int x=0;x<16;x++)for(int z=0;z<16;z++) {
                var base=new BlockPos(cPos.getMinBlockX()+x,floor,cPos.getMinBlockZ()+z);
                var state=world.getBlockState(base);
                if(!station.isInVoxel(Vec3.atLowerCornerOf(base).add(-0.5,-0.5,-0.5).subtract(center))
                        ||!state.isFaceSturdy(world,base,Direction.DOWN))continue;
                // At most six blocks of local fill; unsuitable cliffs are rejected by the selector.
                for(int depth=1;depth<=6;depth++) {
                    var support=base.below(depth);var old=world.getBlockState(support);
                    if(old.isFaceSturdy(world,support,Direction.UP)||old.hasBlockEntity()||old.is(Blocks.BEDROCK)
                            ||com.hxzhitang.tongdarailway.railway.fixed.FixedRailwayNetwork.protects(support))break;
                    world.setBlock(support,(depth<=2?Blocks.DIRT:Blocks.STONE).defaultBlockState(),2);
                }
            }
        }
    }

    private static void placeBlockEntity(WorldGenLevel world, BlockPos blockPos, CompoundTag blockEntityTag) {
        CompoundTag tag = blockEntityTag.copy();
        tag.putInt("x", blockPos.getX());
        tag.putInt("y", blockPos.getY());
        tag.putInt("z", blockPos.getZ());

        var owner=world.getChunk(blockPos.getX()>>4,blockPos.getZ()>>4);
        BlockEntity blockEntity=owner.getBlockEntity(blockPos);
        if(blockEntity==null)blockEntity=world.getBlockEntity(blockPos);
        if(blockEntity==null&&world.getBlockState(blockPos).getBlock() instanceof net.minecraft.world.level.block.EntityBlock factory) {
            blockEntity=factory.newBlockEntity(blockPos,world.getBlockState(blockPos));
            if(blockEntity!=null)owner.setBlockEntity(blockEntity);
        }
        // 不加载传动轴方块实体避免传动轴断裂和虚空应力
        if (blockEntity != null && !(blockEntity instanceof KineticBlockEntity)) {
            blockEntity.loadWithComponents(tag, world.registryAccess());
            blockEntity.setChanged();
        }
    }

    private static Vec3 getStartVec(Vec3 dir) {
        double offX;
        if (Math.abs(dir.x) < 1e-6) {
            offX = 0.5;
        } else if (dir.x < 0) {
            offX = 0;
        } else {
            offX = 1;
        }

        double offZ;
        if (Math.abs(dir.z) < 1e-6) {
            offZ = 0.5;
        } else if (dir.z < 0) {
            offZ = 0;
        } else {
            offZ = 1;
        }

        return new Vec3(offX, 0, offZ);
    }

    private static void placeRoadbed(RailwayMap railwayMap, ChunkPos cPos, WorldGenLevel world) {
        var routes = railwayMap.routeMap.get(cPos);
        for (CurveRoute route : routes) {
            int seed = route.getSegments().size();
            RailwayTemplate ground = ModStructureManager.getRandomGround(seed);
            RailwayTemplate bridge = ModStructureManager.getRandomBridge(seed);
            RailwayTemplate tunnel = ModStructureManager.getRandomTunnel(seed);

            // Sample each route cross-section before this chunk writes any road blocks. This
            // prevents earlier excavation columns from changing the material decision for later walls.
            var frames=new CurveRoute.Frame[16][16];var naturalIce=new boolean[16][16];var bridgeSection=new boolean[16][16];
            var iceCuts=new java.util.BitSet[16][16];
            double iceCore=(tunnel==null?11:tunnel.getDepth())/2.0+1;
            var materials=new java.util.HashMap<Long,Boolean>();var terrain=new java.util.HashMap<Long,Integer>();
            int halfWidth=Math.max(ground==null?0:ground.getDepth(),Math.max(bridge==null?0:bridge.getDepth(),tunnel==null?0:tunnel.getDepth()))/2+1;
            for(int x=0;x<16;x++)for(int z=0;z<16;z++) {
                var point=new Vec3(cPos.getMinBlockX()+x,80,cPos.getMinBlockZ()+z);var frame=route.getFrame(point);frames[x][z]=frame;
                if(Math.abs(point.subtract(frame.nearestPoint).dot(frame.binormal0))>halfWidth)continue;
                var center=BlockPos.containing(frame.nearestPoint);if(!world.hasChunk(center.getX()>>4,center.getZ()>>4))continue;long key=center.asLong();
                naturalIce[x][z]=materials.computeIfAbsent(key,k->naturalIceCorridor(world,center,frame.binormal0));
                // Record original ice before template walls can mask it. Local frozen columns
                // also qualify near rocky shores where the wider material survey rejects ice.
                double lateral=point.subtract(frame.nearestPoint).dot(frame.binormal0);
                iceCuts[x][z]=planIceCut(world,cPos.getMinBlockX()+x,cPos.getMinBlockZ()+z,frame,lateral,iceCore,halfWidth);
                int surface=terrain.computeIfAbsent(key,k->world.getHeight(Heightmap.Types.OCEAN_FLOOR_WG,center.getX(),center.getZ()));
                bridgeSection[x][z]=frame.nearestPoint.y>surface+4;
            }

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    // 获取一个线上点
                    var testPoint0 = new Vec3(cPos.x*16+x, 80, cPos.z*16+z);
                    CurveRoute.Frame frame = frames[x][z];

                    var nearest0 = frame.nearestPoint;

                    double t = frame.globalT;
                    var normal0 = frame.normal0;
                    var binormal0 = frame.binormal0;

                    Vec3 vec0 = testPoint0.subtract(nearest0);

                    // 根据曲线上高度和实际高度判断应用桥隧
                    BlockPos nearestPos = new BlockPos((int) nearest0.x, (int) nearest0.y, (int) nearest0.z);
                    int h = world.getHeight(Heightmap.Types.OCEAN_FLOOR_WG,cPos.getMinBlockX()+x,cPos.getMinBlockZ()+z);

                    boolean conditionBridge = nearest0.y > h + 4;
                    boolean conditionTunnel = nearest0.y < h - 12;
                    boolean isGround = !conditionBridge && !conditionTunnel;

                    // 随机获取一个路基，使用路线段数作为种子来选择
                    RailwayTemplate structureTemplate;
                    if (conditionBridge) {
                        structureTemplate = bridge;
                    } else if (conditionTunnel) {
                        structureTemplate = tunnel;
                    } else {
                        structureTemplate = ground;
                    }

                    double localX = t * route.getTotalLength();

                    double z0 = vec0.dot(binormal0);
                    var scars=railwayMap.damagePlans.get(com.hxzhitang.tongdarailway.railway.fixed.RailwayDecay.identity(route));
                    boolean damage=railwayMap.abandoned&&scars!=null&&scars.contains(localX);
                    // Apply only original frozen cells in this chunk's owned column, including
                    // wall slots and the narrow outer fringe; do this before template early exits.
                    var cut=iceCuts[x][z];
                    if(cut!=null)for(int bit=cut.nextSetBit(0);bit>=0;bit=cut.nextSetBit(bit+1)) {
                        var pos=new BlockPos(cPos.getMinBlockX()+x,world.getMinBuildHeight()+bit,cPos.getMinBlockZ()+z);
                        if(frozenSnow(world.getBlockState(pos)))placeAndUpdate(world,pos,Blocks.AIR.defaultBlockState());
                    }
                    // Remove deck, both railings and supports as one column, including bank-side
                    // columns of the same bridge cross-section. Uneven ends leave attached fragments.
                    if(railwayMap.abandoned&&scars!=null&&(conditionBridge||bridgeSection[x][z])
                            &&scars.bridgeMissing(localX,z0,railwayMap.decaySeed^com.hxzhitang.tongdarailway.railway.fixed.RailwayDecay.identity(route)))continue;

                    if (structureTemplate == null || !structureTemplate.isInVoxel(1, 1, z0))
                        continue;

                    for (int oy = structureTemplate.getLowerBound(); oy <= structureTemplate.getUpperBound(); oy++) {
                        int y = oy + (int) nearest0.y;
                        var testPoint = new Vec3(cPos.x*16+x, y, cPos.z*16+z);
                        var vec = testPoint.subtract(nearest0);

                        double localY = vec.dot(normal0) - 0.15;
                        double localZ = vec.dot(binormal0);

                        // 根据标架下坐标,从模板结构找到对应方块,并且放置
                        BlockState blockState = structureTemplate.getBlockState(localX, localY, localZ);
                        if (blockState != null) {
                            // Ice cuts retain native side walls; only tunnel air and the
                            // existing floor template are written. Rock tunnels keep their lining.
                            if(!conditionBridge&&!com.hxzhitang.tongdarailway.railway.fixed.RailwayTerrainPolicy.placeTunnelVoxel(naturalIce[x][z]||(cut!=null&&!cut.isEmpty()),blockState.isAir(),localY))continue;
                            BlockPos blockPos = new BlockPos(cPos.getMinBlockX()+x, y, cPos.getMinBlockZ()+z);
                            if (!isGround) {
                                placeAndUpdate(world, blockPos, blockState);
                            } else {
                                placeNotAirReplaceStone(world, blockPos, blockState, Blocks.COBBLESTONE.defaultBlockState());
                            }
                        }
                    }
                    // 向下填充地基直到遇到支撑方块(隧道不考虑向下填充地基)
                    if(damage&&!conditionBridge&&Math.abs(z0)<3.5) {
                        // Write only this loop's owned X/Z column. Collapses follow the tunnel;
                        // surface failures form an uneven lateral landslide across the abandoned track.
                        var rail=new BlockPos(cPos.getMinBlockX()+x,(int)nearest0.y,cPos.getMinBlockZ()+z);
                        com.hxzhitang.tongdarailway.railway.fixed.RailwayRuin.placeDebris(world,rail,z0,conditionTunnel,
                                railwayMap.decaySeed^com.hxzhitang.tongdarailway.railway.fixed.RailwayDecay.identity(route));
                    }
                    if (conditionTunnel)
                        continue;

                    for (int oy = structureTemplate.getLowerBound() - 1; oy > structureTemplate.getLowerBound() - 100; oy--) {
                        int y = oy + (int) nearest0.y;

                        BlockPos blockPos = new BlockPos(cPos.getMinBlockX()+x, y, cPos.getMinBlockZ()+z);

                        if (world.getBlockState(blockPos).isFaceSturdy(world, blockPos, Direction.UP)) {
                            break;
                        }

                        var testPoint = new Vec3(cPos.x*16+x, y, cPos.z*16+z);
                        var vec = testPoint.subtract(nearest0);

                        double localY = vec.dot(normal0) - 0.15;
                        double localZ = vec.dot(binormal0);

                        BlockState blockState = structureTemplate.getBlockState(localX, localY, localZ);
                        if (blockState != null) {
                            placeAndUpdate(world, blockPos, blockState);
                        }
                    }
                }
            }

        }
    }

    /** Plan a bounded open cut from untouched terrain; rocks stop the upward scan.
     * At the edge only trim thin ice with open space two blocks farther out, retaining broad banks. */
    private static java.util.BitSet planIceCut(WorldGenLevel world,int x,int z,CurveRoute.Frame frame,double lateral,double core,int halfWidth) {
        if(Math.abs(lateral)>Math.min(halfWidth,core+2))return null;
        var removed=new java.util.BitSet();
        int first=Math.max(world.getMinBuildHeight(),(int)Math.floor(frame.nearestPoint.y)+1);
        int upper=Math.min(world.getMaxBuildHeight(),world.getHeight(Heightmap.Types.WORLD_SURFACE_WG,x,z));
        int direction=lateral<0?-1:1;
        int outerX=x+(int)Math.round(frame.binormal0.x*direction*2);
        int outerZ=z+(int)Math.round(frame.binormal0.z*direction*2);
        com.hxzhitang.tongdarailway.railway.fixed.RailwayTerrainPolicy.clearCap(first,upper,y->{
            var state=world.getBlockState(new BlockPos(x,y,z));
            if(frozenSnow(state)) {
                boolean exposed=false;
                if(Math.abs(lateral)>core&&world.hasChunk(outerX>>4,outerZ>>4)) {
                    var outside=world.getBlockState(new BlockPos(outerX,y,outerZ));
                    exposed=outside.isAir()||!outside.getFluidState().isEmpty();
                }
                return com.hxzhitang.tongdarailway.railway.fixed.RailwayTerrainPolicy.trimIce(lateral,core,exposed)
                        ?com.hxzhitang.tongdarailway.railway.fixed.RailwayTerrainPolicy.CapCell.FROZEN
                        :com.hxzhitang.tongdarailway.railway.fixed.RailwayTerrainPolicy.CapCell.EMPTY;
            }
            return state.isAir()||!state.getFluidState().isEmpty()
                    ?com.hxzhitang.tongdarailway.railway.fixed.RailwayTerrainPolicy.CapCell.EMPTY
                    :com.hxzhitang.tongdarailway.railway.fixed.RailwayTerrainPolicy.CapCell.OTHER;
        },y->removed.set(y-world.getMinBuildHeight()));
        return removed;
    }

    private static boolean frozenSnow(BlockState state) {
        return frozenMaterial(state)||state.is(Blocks.SNOW)||state.is(Blocks.POWDER_SNOW);
    }

    /** Bounded nearby reads use only the worldgen region; no remote chunks are requested.
     * Samples outside the carved width keep the ice decision stable along chunk boundaries. */
    private static boolean naturalIceCorridor(WorldGenLevel world,BlockPos center,Vec3 side) {
        int ice=0,rock=0;
        for(int lateral:new int[]{-8,0,8})for(int height:new int[]{2,6,10}) {
            var pos=center.offset((int)Math.round(side.x*lateral),height,(int)Math.round(side.z*lateral));
            if(!world.hasChunk(pos.getX()>>4,pos.getZ()>>4))continue;
            var state=world.getBlockState(pos);
            if(frozenMaterial(state))ice++;
            else if(state.canOcclude()&&state.getFluidState().isEmpty())rock++;
        }
        return com.hxzhitang.tongdarailway.railway.fixed.RailwayTerrainPolicy.naturalIce(ice,rock);
    }

    private static boolean frozenMaterial(BlockState state) {
        return state.is(Blocks.ICE)||state.is(Blocks.PACKED_ICE)||state.is(Blocks.BLUE_ICE)||state.is(Blocks.SNOW_BLOCK);
    }
    private static void placeAndUpdate(WorldGenLevel world, BlockPos blockPos, BlockState blockState) {
        if(com.hxzhitang.tongdarailway.railway.fixed.FixedRailwayNetwork.enabled()) {
            if(!com.hxzhitang.tongdarailway.railway.fixed.FixedRailwayNetwork.protects(blockPos)
                    &&!world.getBlockState(blockPos).hasBlockEntity()&&!world.getBlockState(blockPos).is(Blocks.BEDROCK))
                world.setBlock(blockPos,blockState,2);
            return;
        }
        world.setBlock(blockPos, blockState, 3);
        BlockState updatedState = Block.updateFromNeighbourShapes(world.getBlockState(blockPos), world, blockPos);
        world.setBlock(blockPos, updatedState, 3);

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = blockPos.relative(direction);
            BlockState neighborState = Block.updateFromNeighbourShapes(world.getBlockState(neighborPos), world, neighborPos);
            world.setBlock(neighborPos, neighborState, 3);
        }
    }

    private static void placeNotAirReplaceStone(WorldGenLevel world, BlockPos blockPos, BlockState blockState, BlockState replaceBlock) {
        if(com.hxzhitang.tongdarailway.railway.fixed.FixedRailwayNetwork.enabled()) {
            if(!blockState.is(Blocks.STONE))placeAndUpdate(world,blockPos,blockState);
            else if(world.getBlockState(blockPos).canOcclude())placeAndUpdate(world,blockPos,replaceBlock);
            return;
        }
        if (!blockState.is(Blocks.STONE)) {
            world.setBlock(blockPos, blockState, 3);
            return;
        }
        if (world.getBlockState(blockPos).canOcclude() && blockState.is(Blocks.STONE)) {
            world.setBlock(blockPos, replaceBlock, 3);
        }
    }
}