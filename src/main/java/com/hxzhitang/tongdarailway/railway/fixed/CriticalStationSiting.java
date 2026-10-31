package com.hxzhitang.tongdarailway.railway.fixed;

import com.google.gson.*;
import com.hxzhitang.tongdarailway.Tongdarailway;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import java.util.*;

/** Read-only landmark geometry guides nearby station placement; it never creates connecting roads. */
public final class CriticalStationSiting {
    private record Site(BlockPos entrance,Direction outward,BoundingBox bounds) {}
    private static volatile Map<BlockPos,Site> sites=Map.of();
    private CriticalStationSiting() {}
    public static void clear(){sites=Map.of();}

    /** Capture optional saved plans on the server thread before background railway planning begins. */
    public static void initialize(MinecraftServer server) {
        clear();if(!net.neoforged.fml.ModList.get().isLoaded("eowcore"))return;
        try {
            var service=Class.forName("yourscraft.jasdewstarfield.eowcore.world.site.StrategicSiteGenerationService");
            var optional=(Optional<?>)service.getMethod("getPlan",MinecraftServer.class,ResourceLocation.class)
                    .invoke(null,server,ResourceLocation.parse("eowcore:radar_station"));
            if(optional.isEmpty())return;var plan=optional.get();var type=plan.getClass();
            var center=(BlockPos)type.getMethod("center").invoke(plan);
            var pieces=(List<?>)type.getMethod("pieces").invoke(plan);BoundingBox bounds=null;BlockPos entrance=null;Direction outward=null;
            var resource=server.getResourceManager().getResource(ResourceLocation.parse("tongdarailway:station_site/radar_station.json")).orElseThrow();
            JsonObject rule;try(var reader=new java.io.InputStreamReader(resource.open(),java.nio.charset.StandardCharsets.UTF_8)){rule=JsonParser.parseReader(reader).getAsJsonObject();}
            var offset=rule.getAsJsonArray("entrance");var local=new BlockPos(offset.get(0).getAsInt(),offset.get(1).getAsInt(),offset.get(2).getAsInt());
            for(var piece:pieces) {
                var pieceType=piece.getClass();var box=(BoundingBox)pieceType.getMethod("boundingBox").invoke(piece);
                if(bounds==null)bounds=new BoundingBox(box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ());else bounds.encapsulate(box);
                if(!pieceType.getMethod("templateId").invoke(piece).toString().equals(rule.get("piece").getAsString()))continue;
                var origin=(BlockPos)pieceType.getMethod("origin").invoke(piece);var rotation=(Rotation)pieceType.getMethod("rotation").invoke(piece);
                entrance=entrance(origin,local,rotation);outward=rotation.rotate(Direction.byName(rule.get("outward").getAsString()));
            }
            if(entrance!=null)sites=Map.of(center,new Site(entrance,outward,bounds));
            else Tongdarailway.LOGGER.warn("[EowRail] Radar doorway template not recognized; nearby station search uses its center");
        }catch(Exception failure){Tongdarailway.LOGGER.warn("[EowRail] Cannot read optional radar station geometry; retaining center-based siting",failure);}
    }
    public static BlockPos entrance(BlockPos origin,BlockPos local,Rotation rotation){return origin.offset(local.rotate(rotation));}
    public static BlockPos approach(BlockPos center){var site=sites.get(center);return site==null?center:site.entrance;}
    /** A preference near the real gate replaces the old fixed southwest offset. */
    public static BlockPos preferred(BlockPos center){var site=sites.get(center);return site==null?center.offset(-128,0,0):site.entrance.relative(site.outward,96);}
    public static boolean overlaps(BlockPos center,int minX,int minZ,int maxX,int maxZ,int margin) {
        var site=sites.get(center);
        if(site==null)return maxX>=center.getX()-64-margin&&minX<=center.getX()+64+margin&&maxZ>=center.getZ()-64-margin&&minZ<=center.getZ()+64+margin;
        var b=site.bounds;return maxX>=b.minX()-margin&&minX<=b.maxX()+margin&&maxZ>=b.minZ()-margin&&minZ<=b.maxZ()+margin;
    }
    /** Native approaches need enough room for their full excavation width outside the unchanged protected site. */
    public static boolean blocksApproach(BlockPos center,int x,int z){return Math.abs(x-center.getX())<=72&&Math.abs(z-center.getZ())<=72||overlaps(center,x,z,x,z,16);}
}
