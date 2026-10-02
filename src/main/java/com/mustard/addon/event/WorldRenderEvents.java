package com.mustard.addon.event;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import com.mustard.addon.modules.SusChunkFinder;
import com.mustard.addon.modules.SusChunkFinderRenderer;

/**
 * Registers world render event for ESP visuals
 */
public class WorldRenderEvents {
    
    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.world == null) return;
            
            if (SusChunkFinder.getInstance().isEnabled()) {
                SusChunkFinderRenderer.getInstance().render(context);
            }
        });
    }
}
