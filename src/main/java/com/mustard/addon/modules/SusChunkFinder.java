package com.mustard.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

/**
 * SusChunkFinder - Detects suspicious chunks using packet-based instant detection
 * Inspired by Krypton and other utility clients
 * NO ACTIVE SCANNING - Only processes chunks on packet load
 * NO ENTITY/TILE ENTITY DETECTION - Block-based detection only
 */
public class SusChunkFinder extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPacket = settings.createGroup("Packet & Cache");
    private final SettingGroup sgVisuals = settings.createGroup("Visuals");

    // Detection toggles
    private final Setting<Boolean> amethystClusters = sgGeneral.add(new BoolSetting.Builder()
        .name("amethyst-clusters")
        .description("Detect amethyst clusters outside of natural geode conditions.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> kelp = sgGeneral.add(new BoolSetting.Builder()
        .name("kelp")
        .description("Detect kelp planted in non-ocean biomes or at unnatural Y-levels.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> bamboo = sgGeneral.add(new BoolSetting.Builder()
        .name("bamboo")
        .description("Detect bamboo growing underground or in non-jungle biomes.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> vines = sgGeneral.add(new BoolSetting.Builder()
        .name("vines-cave-vines")
        .description("Detect vines/cave vines placed manually outside lush/jungle conditions.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> growthBlocks = sgGeneral.add(new BoolSetting.Builder()
        .name("growth-block-filter")
        .description("General toggle for checking unexpected plant growth.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> structuralBlocks = sgGeneral.add(new BoolSetting.Builder()
        .name("player-placed-structural-blocks")
        .description("Flags common manually placed builder blocks (obsidian, crafting table, etc).")
        .defaultValue(true)
        .build());

    // Packet & Cache
    private final Setting<Boolean> clearCacheOnRelog = sgPacket.add(new BoolSetting.Builder()
        .name("clear-cache-on-relog")
        .description("Clears suspicious chunk cache when disconnecting or changing dimensions.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> processUnloadEvents = sgPacket.add(new BoolSetting.Builder()
        .name("process-unload-events")
        .description("Automatically removes chunks from visual list when server unloads them.")
        .defaultValue(true)
        .build());

    // Visuals
    private final Setting<HighlightStyle> highlightStyle = sgVisuals.add(new EnumSetting.Builder<HighlightStyle>()
        .name("highlight-style")
        .description("Outline = wireframe, Filled = translucent box.")
        .defaultValue(HighlightStyle.OUTLINE)
        .build());

    private final Setting<Color> susChunkColor = sgVisuals.add(new ColorSetting.Builder()
        .name("sus-chunk-color")
        .description("Color for suspicious chunk highlights.")
        .defaultValue(new Color(255, 0, 51, 180))
        .build());

    private final Setting<Boolean> distanceIndicator = sgVisuals.add(new BoolSetting.Builder()
        .name("distance-indicator")
        .description("Render text showing closest suspicious chunk and distance in meters.")
        .defaultValue(true)
        .build());

    // Internal state
    private final Set<ChunkPos> suspiciousChunks = Collections.synchronizedSet(new HashSet<>());
    private final Map<ChunkPos, List<String>> chunkReasons = Collections.synchronizedMap(new HashMap<>());

    private ChunkPos closestSusChunk = null;
    private double closestDistance = Double.MAX_VALUE;

    public enum HighlightStyle {
        OUTLINE,
        FILLED
    }

    public SusChunkFinder() {
        super(Categories.Render, "sus-chunk-finder", "Detect suspicious chunks via packet-driven block analysis. No active scanning, no tile entity checks.");
    }

    @Override
    public void onActivate() {
        suspiciousChunks.clear();
        chunkReasons.clear();
        closestSusChunk = null;
        closestDistance = Double.MAX_VALUE;
    }

    @Override
    public void onDeactivate() {
        suspiciousChunks.clear();
        chunkReasons.clear();
        closestSusChunk = null;
        closestDistance = Double.MAX_VALUE;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null) {
            if (clearCacheOnRelog.get()) {
                clearCache();
            }
        }
        updateClosestChunk();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null || suspiciousChunks.isEmpty()) return;
        render(event);
    }

    /**
     * Called when a chunk packet is received - INSTANT, NO POLLING
     */
    public void onChunkLoad(WorldChunk chunk) {
        if (mc.world == null || chunk == null) return;

        ChunkPos pos = chunk.getPos();
        List<String> reasons = new ArrayList<>();

        checkChunkBlocks(chunk, reasons);

        if (!reasons.isEmpty()) {
            suspiciousChunks.add(pos);
            chunkReasons.put(pos, reasons);
        } else {
            suspiciousChunks.remove(pos);
            chunkReasons.remove(pos);
        }

        updateClosestChunk();
    }

    /**
     * Called when a chunk unload packet arrives
     */
    public void onChunkUnload(ChunkPos pos) {
        if (!processUnloadEvents.get()) return;
        suspiciousChunks.remove(pos);
        chunkReasons.remove(pos);
        updateClosestChunk();
    }

    public void clearCache() {
        suspiciousChunks.clear();
        chunkReasons.clear();
        closestSusChunk = null;
        closestDistance = Double.MAX_VALUE;
    }

    /**
     * Single-pass block state inspection - extremely lightweight
     */
    private void checkChunkBlocks(WorldChunk chunk, List<String> reasons) {
        ClientWorld world = mc.world;
        if (world == null) return;

        ChunkPos chunkPos = chunk.getPos();
        int chunkStartX = chunkPos.getStartX();
        int chunkStartZ = chunkPos.getStartZ();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = world.getBottomY(); y < world.getTopY(); y++) {
                    int wx = chunkStartX + x;
                    int wz = chunkStartZ + z;

                    var blockState = chunk.getBlockState(wx, y, wz);
                    String blockName = blockState.getBlock().getTranslationKey();

                    if (amethystClusters.get() && isAmethystCluster(blockName)) {
                        if (!isInNaturalAmethystGeode(world, wx, y, wz)) {
                            reasons.add("Amethyst @ Y:" + y);
                        }
                    }

                    if (kelp.get() && isKelp(blockName)) {
                        if (!isOceanBiome(world, wx, wz) || y > 62) {
                            reasons.add("Kelp @ Y:" + y);
                        }
                    }

                    if (bamboo.get() && isBamboo(blockName)) {
                        if (y < 64 || !isJungleBiome(world, wx, wz)) {
                            reasons.add("Bamboo @ Y:" + y);
                        }
                    }

                    if (vines.get() && (isVine(blockName) || isCaveVine(blockName))) {
                        if (!isLushCaveOrJungle(world, wx, y, wz)) {
                            reasons.add(isVine(blockName) ? "Vine" : "CaveVine");
                        }
                    }

                    if (growthBlocks.get() && isGrowthBlock(blockName)) {
                        if (y < 62 || !isNaturalGrowthLocation(world, wx, y, wz, blockName)) {
                            reasons.add("Growth:" + blockName.replace("block.minecraft.", ""));
                        }
                    }

                    if (structuralBlocks.get() && isStructuralBlock(blockName)) {
                        if (!isNaturalStructuralLocation(world, wx, y, wz, blockName)) {
                            reasons.add("Struct:" + blockName.replace("block.minecraft.", ""));
                        }
                    }
                }
            }
        }
    }

    // ==================== BLOCK DETECTION HELPERS ====================

    private boolean isAmethystCluster(String blockName) {
        return blockName.contains("amethyst_cluster");
    }

    private boolean isInNaturalAmethystGeode(ClientWorld world, int x, int y, int z) {
        int amethystCount = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    var nearby = world.getBlockState(x + dx, y + dy, z + dz);
                    String name = nearby.getBlock().getTranslationKey();
                    if (name.contains("amethyst") || name.contains("deepslate")) amethystCount++;
                }
            }
        }
        return amethystCount > 20;
    }

    private boolean isKelp(String blockName) {
        return blockName.contains("kelp");
    }

    private boolean isOceanBiome(ClientWorld world, int x, int z) {
        Biome biome = world.getBiome(x, 64, z);
        String biomeName = biome.getKey().map(k -> k.getValue().toString()).orElse("");
        return biomeName.contains("ocean") || biomeName.contains("deep_ocean");
    }

    private boolean isBamboo(String blockName) {
        return blockName.contains("bamboo");
    }

    private boolean isJungleBiome(ClientWorld world, int x, int z) {
        Biome biome = world.getBiome(x, 64, z);
        String biomeName = biome.getKey().map(k -> k.getValue().toString()).orElse("");
        return biomeName.contains("jungle");
    }

    private boolean isVine(String blockName) {
        return blockName.contains("vine") && !blockName.contains("cave");
    }

    private boolean isCaveVine(String blockName) {
        return blockName.contains("cave_vine") || blockName.contains("glow_berries");
    }

    private boolean isLushCaveOrJungle(ClientWorld world, int x, int y, int z) {
        Biome biome = world.getBiome(x, y, z);
        String biomeName = biome.getKey().map(k -> k.getValue().toString()).orElse("");
        return biomeName.contains("lush_caves") || biomeName.contains("jungle");
    }

    private boolean isGrowthBlock(String blockName) {
        return blockName.contains("sugar_cane") || blockName.contains("cactus") 
            || blockName.contains("sweet_berry_bush");
    }

    private boolean isNaturalGrowthLocation(ClientWorld world, int x, int y, int z, String blockName) {
        if (blockName.contains("sugar_cane")) {
            var below = world.getBlockState(x, y - 1, z).getBlock().getTranslationKey();
            return below.contains("sand") || below.contains("dirt") || below.contains("grass");
        }
        if (blockName.contains("cactus")) {
            var below = world.getBlockState(x, y - 1, z).getBlock().getTranslationKey();
            return below.contains("sand");
        }
        if (blockName.contains("sweet_berry_bush")) {
            return y > 62;
        }
        return true;
    }

    private boolean isStructuralBlock(String blockName) {
        return blockName.contains("obsidian") || blockName.contains("crafting_table")
            || blockName.contains("ender_chest") || blockName.contains("enchanting_table")
            || blockName.contains("netherite_block") || blockName.contains("beacon")
            || blockName.contains("anvil");
    }

    private boolean isNaturalStructuralLocation(ClientWorld world, int x, int y, int z, String blockName) {
        if (blockName.contains("obsidian")) {
            Biome biome = world.getBiome(x, y, z);
            String biomeName = biome.getKey().map(k -> k.getValue().toString()).orElse("");
            return biomeName.contains("nether");
        }
        return false;
    }

    private void updateClosestChunk() {
        closestSusChunk = null;
        closestDistance = Double.MAX_VALUE;

        if (mc.player == null) return;
        Vec3d playerPos = mc.player.getPos();

        for (ChunkPos pos : suspiciousChunks) {
            double cx = pos.getCenterX();
            double cz = pos.getCenterZ();
            double distance = playerPos.distanceTo(new Vec3d(cx, playerPos.y, cz));

            if (distance < closestDistance) {
                closestDistance = distance;
                closestSusChunk = pos;
            }
        }
    }

    private void render(Render3DEvent event) {
        Vec3d camPos = mc.gameRenderer.getCamera().getPos();
        Color color = susChunkColor.get();

        for (ChunkPos chunkPos : suspiciousChunks) {
            int x1 = chunkPos.getStartX();
            int z1 = chunkPos.getStartZ();
            int x2 = x1 + 16;
            int z2 = z1 + 16;
            int y1 = mc.world.getBottomY();
            int y2 = mc.world.getTopY();

            if (highlightStyle.get() == HighlightStyle.OUTLINE) {
                renderOutline(event, x1, z1, x2, z2, y1, y2, camPos, color);
            } else {
                renderFilled(event, x1, z1, x2, z2, y1, y2, camPos, color);
            }
        }

        if (distanceIndicator.get()) {
            renderDistanceLabel();
        }
    }

    private void renderOutline(Render3DEvent event, int x1, int z1, int x2, int z2, int y1, int y2, Vec3d cam, Color color) {
        double dx1 = x1 - cam.x;
        double dz1 = z1 - cam.z;
        double dx2 = x2 - cam.x;
        double dz2 = z2 - cam.z;
        double dy1 = y1 - cam.y;
        double dy2 = y2 - cam.y;

        // Bottom edges
        event.line(dx1, dy1, dz1, dx2, dy1, dz1, color);
        event.line(dx2, dy1, dz1, dx2, dy1, dz2, color);
        event.line(dx2, dy1, dz2, dx1, dy1, dz2, color);
        event.line(dx1, dy1, dz2, dx1, dy1, dz1, color);

        // Top edges
        event.line(dx1, dy2, dz1, dx2, dy2, dz1, color);
        event.line(dx2, dy2, dz1, dx2, dy2, dz2, color);
        event.line(dx2, dy2, dz2, dx1, dy2, dz2, color);
        event.line(dx1, dy2, dz2, dx1, dy2, dz1, color);

        // Vertical edges
        event.line(dx1, dy1, dz1, dx1, dy2, dz1, color);
        event.line(dx2, dy1, dz1, dx2, dy2, dz1, color);
        event.line(dx2, dy1, dz2, dx2, dy2, dz2, color);
        event.line(dx1, dy1, dz2, dx1, dy2, dz2, color);
    }

    private void renderFilled(Render3DEvent event, int x1, int z1, int x2, int z2, int y1, int y2, Vec3d cam, Color color) {
        double dx1 = x1 - cam.x;
        double dz1 = z1 - cam.z;
        double dx2 = x2 - cam.x;
        double dz2 = z2 - cam.z;
        double dy1 = y1 - cam.y;
        double dy2 = y2 - cam.y;

        // Bottom, Top, Front, Back, Left, Right
        event.quad(dx1, dy1, dz1, dx2, dy1, dz1, dx2, dy1, dz2, dx1, dy1, dz2, color);
        event.quad(dx1, dy2, dz1, dx2, dy2, dz1, dx2, dy2, dz2, dx1, dy2, dz2, color);
        event.quad(dx1, dy1, dz1, dx2, dy1, dz1, dx2, dy2, dz1, dx1, dy2, dz1, color);
        event.quad(dx1, dy1, dz2, dx2, dy1, dz2, dx2, dy2, dz2, dx1, dy2, dz2, color);
        event.quad(dx1, dy1, dz1, dx1, dy1, dz2, dx1, dy2, dz2, dx1, dy2, dz1, color);
        event.quad(dx2, dy1, dz1, dx2, dy1, dz2, dx2, dy2, dz2, dx2, dy2, dz1, color);
    }

    private void renderDistanceLabel() {
        if (closestSusChunk == null || mc.player == null) return;

        String text = String.format("Closest Sus: X:%d Z:%d [%.1fm]", 
            closestSusChunk.getCenterBlockX(), closestSusChunk.getCenterBlockZ(), closestDistance);
        
        int screenWidth = mc.getWindow().getScaledWidth();
        int x = screenWidth / 2 - mc.textRenderer.getWidth(text) / 2;
        int y = 30;

        mc.textRenderer.drawWithBackground(text, x, y, 0x00FF00, 0);
    }

    public Set<ChunkPos> getSuspiciousChunks() {
        return new HashSet<>(suspiciousChunks);
    }

    public Map<ChunkPos, List<String>> getChunkReasons() {
        return new HashMap<>(chunkReasons);
    }
}
