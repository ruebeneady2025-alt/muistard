package com.mustard.addon;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import com.mustard.addon.event.WorldRenderEvents;
import com.mustard.addon.event.ClientLifecycleEvents;
import com.mustard.addon.modules.SusChunkFinder;

/**
 * Main Mustard addon initializer
 */
public class MustardAddon implements ClientModInitializer {
    
    @Override
    public void onInitializeClient() {
        // Register event handlers
        WorldRenderEvents.register();
        ClientLifecycleEvents.register();
        
        // Verify modules are loaded
        SusChunkFinder.getInstance();
    }
}
