package com.mustard.addon.modules;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import java.util.*;

/**
 * SusChunkFinder - Detects suspicious chunks using packet-based instant detection
 * Inspired by Krypton and other utility clients
 * NO ACTIVE SCANNING - Only processes chunks on packet load
 * NO ENTITY/TILE ENTITY DETECTION - Block-based detection only
 */
public class SusChunkFinder {
    private static final SusChunkFinder INSTANCE = new SusChunkFinder();
    
    // Core detection flags
    private boolean enabled = true;
    private Set<ChunkPos> suspiciousChunks = Collections.synchronizedSet(new HashSet<>());
    private Map<ChunkPos, List<String>> chunkReasons = Collections.synchronizedMap(new HashMap<>());
    
    // Detection toggles
    private boolean detectAmethyst = true;
    private boolean detectKelp = true;
    private boolean detectBamboo = true;
    private boolean detectVines = true;
    private boolean detectGrowthBlocks = true;
    private boolean detectStructuralBlocks = true;
    
    // Cache & packet configuration
    private boolean clearCacheOnRelog = true;
    private boolean processUnloadEvents = true;
    
    // Visual configuration
    private HighlightStyle highlightStyle = HighlightStyle.OUTLINE;
    private int susChunkColor = 0xFF0033; // Bright Neon Red
    private float colorAlpha = 0.7f;
    private boolean showDistanceIndicator = true;
    
    // Rendering cache
    private Vec3d lastPlayerPos = Vec3d.ZERO;
    private ChunkPos closestSusChunk = null;
    private double closestDistance = Double.MAX_VALUE;
    
    public enum HighlightStyle {
        OUTLINE("Outline"),
        FILLED("Filled");
        
        public final String displayName;
        HighlightStyle(String displayName) {
            this.displayName = displayName;
        }
    }
    
    private SusChunkFinder() {}
    
    public static SusChunkFinder getInstance() {
        return INSTANCE;
    }
    
    /**
     * Called when a chunk packet is received - INSTANT, NO POLLING
     * This is the core detection method
     */
    public void onChunkLoad(WorldChunk chunk) {
        if (!enabled || chunk == null) return;
        
        ChunkPos chunkPos = chunk.getPos();
        List<String> reasons = new ArrayList<>();
        
        // Single-pass block checking
        checkChunkBlocks(chunk, reasons);
        
        if (!reasons.isEmpty()) {
            suspiciousChunks.add(chunkPos);
            chunkReasons.put(chunkPos, reasons);
        } else {
            suspiciousChunks.remove(chunkPos);
            chunkReasons.remove(chunkPos);
        }
    }
    
    /**
     * Single-pass block state inspection - extremely lightweight
     */
    private void checkChunkBlocks(WorldChunk chunk, List<String> reasons) {
        ChunkPos chunkPos = chunk.getPos();
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return;
        
        int chunkX = chunkPos.getStartX();
        int chunkZ = chunkPos.getStartZ();
        
        // Iterate through all block positions in the chunk
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = world.getBottomY(); y < world.getTopY(); y++) {
                    int worldX = chunkX + x;
                    int worldZ = chunkZ + z;
                    
                    var blockState = chunk.getBlockState(worldX, y, worldZ);
                    var blockName = blockState.getBlock().getTranslationKey();
                    
                    // AMETHYST CLUSTERS
                    if (detectAmethyst && isAmethystCluster(blockName)) {
                        if (!isInAmethystGeode(world, worldX, y, worldZ)) {
                            reasons.add("Amethyst @ Y:" + y);
                        }
                    }
                    
                    // KELP
                    if (detectKelp && isKelp(blockName)) {
                        if (!isOceanBiome(world, worldX, worldZ) || y > 62) {
                            reasons.add("Kelp @ Y:" + y);
                        }
                    }
                    
                    // BAMBOO
                    if (detectBamboo && isBamboo(blockName)) {
                        if (y < 64 || !isJungleBiome(world, worldX, worldZ)) {
                            reasons.add("Bamboo @ Y:" + y);
                        }
                    }
                    
                    // VINES & CAVE VINES
                    if (detectVines && (isVine(blockName) || isCaveVine(blockName))) {
                        if (!isLushCaveOrJungle(world, worldX, y, worldZ)) {
                            reasons.add(isVine(blockName) ? "Vine" : "CaveVine");
                        }
                    }
                    
                    // GROWTH BLOCKS (Sugar Cane, Cactus, Sweet Berry)
                    if (detectGrowthBlocks && isGrowthBlock(blockName)) {
                        if (y < 62 || !isNaturalGrowthLocation(world, worldX, y, worldZ, blockName)) {
                            reasons.add("Growth:" + blockName.replace("block.minecraft.", ""));
                        }
                    }
                    
                    // STRUCTURAL BLOCKS (Obsidian, Crafting Table, Ender Chest, etc.)
                    if (detectStructuralBlocks && isStructuralBlock(blockName)) {
                        if (!isNaturalStructuralLocation(world, worldX, y, worldZ, blockName)) {
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
    
    private boolean isInAmethystGeode(ClientWorld world, int x, int y, int z) {
        // Check if surrounded by amethyst blocks or deep slate
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
        return amethystCount > 20; // Natural geodes have high amethyst density
    }
    
    private boolean isKelp(String blockName) {
        return blockName.contains("kelp");
    }
    
    private boolean isOceanBiome(ClientWorld world, int x, int z) {
        var biome = world.getBiome(x, 0, z);
        String biomeName = biome.value().getTranslationKey();
        return biomeName.contains("ocean") || biomeName.contains("deep_ocean");
    }
    
    private boolean isBamboo(String blockName) {
        return blockName.contains("bamboo");
    }
    
    private boolean isJungleBiome(ClientWorld world, int x, int z) {
        var biome = world.getBiome(x, 64, z);
        String biomeName = biome.value().getTranslationKey();
        return biomeName.contains("jungle");
    }
    
    private boolean isVine(String blockName) {
        return blockName.contains("vine") && !blockName.contains("cave");
    }
    
    private boolean isCaveVine(String blockName) {
        return blockName.contains("cave_vine") || blockName.contains("glow_berries");
    }
    
    private boolean isLushCaveOrJungle(ClientWorld world, int x, int y, int z) {
        var biome = world.getBiome(x, y, z);
        String biomeName = biome.value().getTranslationKey();
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
        if (blockName.contains("sweet_berry")) {
            return y > 62; // Natural in overworld surface
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
        // Obsidian can appear naturally near lava or in nether
        if (blockName.contains("obsidian")) {
            var biome = world.getBiome(x, y, z).value().getTranslationKey();
            return biome.contains("nether");
        }
        // Crafting table, ender chest, enchanting table, beacon never generate naturally
        return !blockName.contains("crafting_table") && !blockName.contains("ender_chest")
            && !blockName.contains("enchanting_table") && !blockName.contains("beacon")
            && !blockName.contains("anvil") && !blockName.contains("netherite_block");
    }
    
    // ==================== CHUNK UNLOAD / CACHE ====================
    
    public void onChunkUnload(ChunkPos chunkPos) {
        if (!processUnloadEvents) return;
        suspiciousChunks.remove(chunkPos);
        chunkReasons.remove(chunkPos);
    }
    
    public void clearCache() {
        suspiciousChunks.clear();
        chunkReasons.clear();
        closestSusChunk = null;
        closestDistance = Double.MAX_VALUE;
    }
    
    // ==================== GETTERS / SETTERS ====================
    
    public Set<ChunkPos> getSuspiciousChunks() {
        return new HashSet<>(suspiciousChunks);
    }
    
    public List<String> getReasonsForChunk(ChunkPos pos) {
        return chunkReasons.getOrDefault(pos, new ArrayList<>());
    }
    
    public void updatePlayerPosition(Vec3d playerPos) {
        this.lastPlayerPos = playerPos;
        
        // Find closest sus chunk
        closestSusChunk = null;
        closestDistance = Double.MAX_VALUE;
        
        for (ChunkPos chunkPos : suspiciousChunks) {
            double chunkCenterX = chunkPos.getCenterX();
            double chunkCenterZ = chunkPos.getCenterZ();
            double distance = playerPos.distanceTo(new Vec3d(chunkCenterX, playerPos.y, chunkCenterZ));
            
            if (distance < closestDistance) {
                closestDistance = distance;
                closestSusChunk = chunkPos;
            }
        }
    }
    
    public ChunkPos getClosestSusChunk() {
        return closestSusChunk;
    }
    
    public double getClosestDistance() {
        return closestDistance;
    }
    
    // ==================== CONFIG GETTERS / SETTERS ====================
    
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
    public boolean isDetectAmethyst() { return detectAmethyst; }
    public void setDetectAmethyst(boolean detect) { this.detectAmethyst = detect; }
    
    public boolean isDetectKelp() { return detectKelp; }
    public void setDetectKelp(boolean detect) { this.detectKelp = detect; }
    
    public boolean isDetectBamboo() { return detectBamboo; }
    public void setDetectBamboo(boolean detect) { this.detectBamboo = detect; }
    
    public boolean isDetectVines() { return detectVines; }
    public void setDetectVines(boolean detect) { this.detectVines = detect; }
    
    public boolean isDetectGrowthBlocks() { return detectGrowthBlocks; }
    public void setDetectGrowthBlocks(boolean detect) { this.detectGrowthBlocks = detect; }
    
    public boolean isDetectStructuralBlocks() { return detectStructuralBlocks; }
    public void setDetectStructuralBlocks(boolean detect) { this.detectStructuralBlocks = detect; }
    
    public boolean isClearCacheOnRelog() { return clearCacheOnRelog; }
    public void setClearCacheOnRelog(boolean clear) { this.clearCacheOnRelog = clear; }
    
    public boolean isProcessUnloadEvents() { return processUnloadEvents; }
    public void setProcessUnloadEvents(boolean process) { this.processUnloadEvents = process; }
    
    public HighlightStyle getHighlightStyle() { return highlightStyle; }
    public void setHighlightStyle(HighlightStyle style) { this.highlightStyle = style; }
    
    public int getSusChunkColor() { return susChunkColor; }
    public void setSusChunkColor(int color) { this.susChunkColor = color; }
    
    public float getColorAlpha() { return colorAlpha; }
    public void setColorAlpha(float alpha) { this.colorAlpha = Math.max(0.0f, Math.min(1.0f, alpha)); }
    
    public boolean isShowDistanceIndicator() { return showDistanceIndicator; }
    public void setShowDistanceIndicator(boolean show) { this.showDistanceIndicator = show; }
}
