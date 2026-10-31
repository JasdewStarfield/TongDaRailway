package com.hxzhitang.tongdarailway.mixin;
import com.hxzhitang.tongdarailway.railway.fixed.SurfaceStructurePolicy;
import net.minecraft.world.level.levelgen.structure.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reject whole layouts before registration; never remove individual already-placed building pieces. */
@Mixin(Structure.class)
public class SurfaceStructurePolicyMixin {
    @Inject(method="generate",at=@At("RETURN"),cancellable=true)
    private void eow$filter(net.minecraft.core.RegistryAccess registries,net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.biome.BiomeSource biomes,net.minecraft.world.level.levelgen.RandomState randomState,
            net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templates,long seed,
            net.minecraft.world.level.ChunkPos chunk,int references,net.minecraft.world.level.LevelHeightAccessor heights,
            java.util.function.Predicate<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> valid,
            CallbackInfoReturnable<StructureStart> result) {
        if(!SurfaceStructurePolicy.mode().equals("remove")||SurfaceStructurePolicy.previewing())return;
        var self=(Structure)(Object)this;
        // Previews construct native layouts under a thread-local guard and cannot recurse into rail planning.
        var context=new Structure.GenerationContext(registries,generator,biomes,randomState,templates,seed,chunk,heights,valid);
        if(SurfaceStructurePolicy.cancel(self,result.getReturnValue(),context))result.setReturnValue(StructureStart.INVALID_START);
    }
}
