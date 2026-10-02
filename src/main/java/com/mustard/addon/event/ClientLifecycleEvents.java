package com.mustard.addon.event;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import com.mustard.addon.modules.SusChunkFinder;

/**
 * Handles client lifecycle events (disconnect, dimension change, etc.)
 */
public class ClientLifecycleEvents {
    
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Detect disconnect (world becomes null)
            if (client.world == null && SusChunkFinder.getInstance().isClearCacheOnRelog()) {
                SusChunkFinder.getInstance().clearCache();
            }
        });
    }
}
