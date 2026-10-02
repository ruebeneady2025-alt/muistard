package com.mustard.addon.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mustard.addon.modules.SusChunkFinder;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.util.math.ChunkPos;

/**
 * Intercepts chunk data packets to trigger instant detection
 * Hooks into ClientPlayNetworkHandler to capture ChunkDataS2CPacket
 */
@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {
    
    /**
     * Inject after chunk is loaded from packet
     * This ensures the chunk data is fully processed before our detection runs
     */
    @Inject(
        method = "onChunkData",
        at = @At("TAIL")
    )
    private void onChunkDataLoaded(ChunkDataS2CPacket packet, CallbackInfo ci) {
        // Get the chunk from the client world
        if (net.minecraft.client.MinecraftClient.getInstance().world != null) {
            ChunkPos chunkPos = packet.getChunkPos();
            WorldChunk chunk = net.minecraft.client.MinecraftClient.getInstance()
                .world.getChunk(chunkPos.x, chunkPos.z);
            
            if (chunk != null) {
                SusChunkFinder.getInstance().onChunkLoad(chunk);
            }
        }
    }
}
