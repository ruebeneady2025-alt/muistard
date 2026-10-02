package com.mustard.addon.modules;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

/**
 * Renders ESP visuals for suspicious chunks
 * Uses OpenGL for clean, through-terrain visibility
 */
public class SusChunkFinderRenderer {
    private static final SusChunkFinderRenderer INSTANCE = new SusChunkFinderRenderer();
    private static final int CHUNK_SIZE = 16;
    
    private SusChunkFinderRenderer() {}
    
    public static SusChunkFinderRenderer getInstance() {
        return INSTANCE;
    }
    
    /**
     * Renders all suspicious chunks
     * Called from WorldRenderCallback
     */
    public void render(WorldRenderContext context) {
        SusChunkFinder finder = SusChunkFinder.getInstance();
        
        if (!finder.isEnabled() || finder.getSuspiciousChunks().isEmpty()) {
            return;
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        
        // Update player position for distance calculation
        finder.updatePlayerPosition(client.player.getPos());
        
        Vec3d cameraPos = context.camera().getPos();
        Matrix4f positionMatrix = context.matrixStack().peek().getPositionMatrix();
        
        // Setup rendering
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.defaultBlendFunc();
        
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(VertexFormat.DrawMode.LINES, VertexFormats.POSITION_COLOR);
        
        // Render each suspicious chunk
        for (ChunkPos chunkPos : finder.getSuspiciousChunks()) {
            renderChunkBounds(buffer, chunkPos, cameraPos, finder);
        }
        
        Tessellator.getInstance().draw();
        
        // Render distance indicator if enabled
        if (finder.isShowDistanceIndicator()) {
            renderDistanceIndicator(client, finder);
        }
        
        RenderSystem.disableBlend();
    }
    
    /**
     * Renders the bounding box for a single chunk
     */
    private void renderChunkBounds(BufferBuilder buffer, ChunkPos chunkPos, Vec3d cameraPos, SusChunkFinder finder) {
        int startX = chunkPos.getStartX();
        int startZ = chunkPos.getStartZ();
        int endX = startX + CHUNK_SIZE;
        int endZ = startZ + CHUNK_SIZE;
        
        MinecraftClient client = MinecraftClient.getInstance();
        int minY = (client.world != null) ? client.world.getBottomY() : 0;
        int maxY = (client.world != null) ? client.world.getTopY() : 256;
        
        // Get color components
        int color = finder.getSusChunkColor();
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        float a = finder.getColorAlpha();
        int argb = ((int)(a * 255) << 24) | (((int)(r * 255)) << 16) 
                 | (((int)(g * 255)) << 8) | ((int)(b * 255));
        
        if (finder.getHighlightStyle() == SusChunkFinder.HighlightStyle.OUTLINE) {
            renderOutline(buffer, startX, startZ, endX, endZ, minY, maxY, cameraPos, argb);
        } else {
            renderFilled(buffer, startX, startZ, endX, endZ, minY, maxY, cameraPos, argb);
        }
    }
    
    /**
     * Renders outline style (wireframe cube)
     */
    private void renderOutline(BufferBuilder buffer, int x1, int z1, int x2, int z2, 
                               int y1, int y2, Vec3d camera, int color) {
        // Adjust for camera position
        double dx1 = x1 - camera.x;
        double dz1 = z1 - camera.z;
        double dx2 = x2 - camera.x;
        double dz2 = z2 - camera.z;
        double dy1 = y1 - camera.y;
        double dy2 = y2 - camera.y;
        
        // Bottom face
        drawLine(buffer, dx1, dy1, dz1, dx2, dy1, dz1, color);
        drawLine(buffer, dx2, dy1, dz1, dx2, dy1, dz2, color);
        drawLine(buffer, dx2, dy1, dz2, dx1, dy1, dz2, color);
        drawLine(buffer, dx1, dy1, dz2, dx1, dy1, dz1, color);
        
        // Top face
        drawLine(buffer, dx1, dy2, dz1, dx2, dy2, dz1, color);
        drawLine(buffer, dx2, dy2, dz1, dx2, dy2, dz2, color);
        drawLine(buffer, dx2, dy2, dz2, dx1, dy2, dz2, color);
        drawLine(buffer, dx1, dy2, dz2, dx1, dy2, dz1, color);
        
        // Vertical edges
        drawLine(buffer, dx1, dy1, dz1, dx1, dy2, dz1, color);
        drawLine(buffer, dx2, dy1, dz1, dx2, dy2, dz1, color);
        drawLine(buffer, dx2, dy1, dz2, dx2, dy2, dz2, color);
        drawLine(buffer, dx1, dy1, dz2, dx1, dy2, dz2, color);
    }
    
    /**
     * Renders filled style (translucent cube faces)
     */
    private void renderFilled(BufferBuilder buffer, int x1, int z1, int x2, int z2, 
                              int y1, int y2, Vec3d camera, int color) {
        double dx1 = x1 - camera.x;
        double dz1 = z1 - camera.z;
        double dx2 = x2 - camera.x;
        double dz2 = z2 - camera.z;
        double dy1 = y1 - camera.y;
        double dy2 = y2 - camera.y;
        
        // Bottom
        drawQuad(buffer, dx1, dy1, dz1, dx2, dy1, dz1, dx2, dy1, dz2, dx1, dy1, dz2, color);
        // Top
        drawQuad(buffer, dx1, dy2, dz1, dx2, dy2, dz1, dx2, dy2, dz2, dx1, dy2, dz2, color);
        // Front
        drawQuad(buffer, dx1, dy1, dz1, dx2, dy1, dz1, dx2, dy2, dz1, dx1, dy2, dz1, color);
        // Back
        drawQuad(buffer, dx1, dy1, dz2, dx2, dy1, dz2, dx2, dy2, dz2, dx1, dy2, dz2, color);
        // Left
        drawQuad(buffer, dx1, dy1, dz1, dx1, dy1, dz2, dx1, dy2, dz2, dx1, dy2, dz1, color);
        // Right
        drawQuad(buffer, dx2, dy1, dz1, dx2, dy1, dz2, dx2, dy2, dz2, dx2, dy2, dz1, color);
    }
    
    private void drawLine(BufferBuilder buffer, double x1, double y1, double z1, 
                         double x2, double y2, double z2, int color) {
        buffer.vertex(x1, y1, z1).color(color).next();
        buffer.vertex(x2, y2, z2).color(color).next();
    }
    
    private void drawQuad(BufferBuilder buffer, double x1, double y1, double z1,
                         double x2, double y2, double z2, double x3, double y3, double z3,
                         double x4, double y4, double z4, int color) {
        buffer.vertex(x1, y1, z1).color(color).next();
        buffer.vertex(x2, y2, z2).color(color).next();
        buffer.vertex(x3, y3, z3).color(color).next();
        buffer.vertex(x4, y4, z4).color(color).next();
    }
    
    /**
     * Renders distance indicator text
     */
    private void renderDistanceIndicator(MinecraftClient client, SusChunkFinder finder) {
        ChunkPos closest = finder.getClosestSusChunk();
        if (closest == null || client.player == null) return;
        
        double distance = finder.getClosestDistance();
        String text = String.format("Closest Sus: X:%d Z:%d [%.1fm]", 
            closest.getCenterBlockX(), closest.getCenterBlockZ(), distance);
        
        int screenWidth = client.getWindow().getWidth();
        int screenHeight = client.getWindow().getHeight();
        int x = screenWidth / 2 - client.textRenderer.getWidth(text) / 2;
        int y = 30;
        
        // Draw with background
        client.textRenderer.drawWithBackgroundAndShadow(client.getRenderTickCounter().getTickDelta(), 
            text, x, y, 0x00FF00);
    }
}
