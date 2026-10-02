package com.mustard.addon.mixin;

import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mustard.addon.modules.SusChunkFinder;
import meteordevelopment.meteorclient.systems.modules.Modules;

/**
 * Intercepts chunk unload events
 * Allows cache management when chunks are removed from view distance
 */
@Mixin(ClientChunkManager.class)
public class ClientChunkManagerMixin {
    
    @Inject(
        method = "unload",
        at = @At("HEAD")
    )
    private void onChunkUnload(int x, int z, CallbackInfo ci) {
        SusChunkFinder module = Modules.get().get(SusChunkFinder.class);
        if (module == null || !module.isActive()) return;

        ChunkPos chunkPos = new ChunkPos(x, z);
        module.onChunkUnload(chunkPos);
    }
}
