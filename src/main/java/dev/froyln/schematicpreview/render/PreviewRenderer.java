package dev.froyln.schematicpreview.render;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.Project;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.VertexBufferUploader;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import fi.dy.masa.litematica.schematic.ISchematic;

/**
 * Owns the tessellated VBOs for one schematic and knows how to draw them into whatever
 * framebuffer the caller has already bound. One instance is shared by every {@code PreviewWidget}
 * showing the same schematic path (see {@code PreviewCache}) so switching between the side panel
 * and fullscreen never re-tessellates.
 */
public class PreviewRenderer
{
    // ponytail: fixed per-tick tessellation budget, not configurable - revisit if a huge
    // schematic visibly stalls the side panel in testing.
    private static final int TESSELLATE_BUDGET_PER_TICK = 4096;

    private SchematicBlockAccess access;
    private List<BlockPos> tileEntityPositions;
    // Renderer classes that threw once. Static: the failure is a property of the renderer
    // (typically "needs a World", which these TEs never have), not of one schematic.
    private static final Set<Class<?>> TILE_ENTITY_BLACKLIST = new HashSet<>();

    private long cursor;
    private long totalVolume;
    private boolean tessellationDone;

    private final EnumMap<BlockRenderLayer, BufferBuilder> buildingBuffers = new EnumMap<>(BlockRenderLayer.class);
    private final EnumMap<BlockRenderLayer, VertexBuffer> vbos = new EnumMap<>(BlockRenderLayer.class);
    private static final VertexBufferUploader VERTEX_UPLOADER = new VertexBufferUploader();

    public void setup(ISchematic schematic)
    {
        this.access = new SchematicBlockAccess(schematic);
        this.tileEntityPositions = this.access.getTileEntityPositions();
        Vec3i size = this.access.getBoxSize();
        this.totalVolume = (long) size.getX() * size.getY() * size.getZ();
        this.cursor = 0;
        this.tessellationDone = this.totalVolume <= 0;
    }

    public boolean isTessellationDone()
    {
        return this.tessellationDone;
    }

    public net.minecraft.util.math.Vec3d getCenter()
    {
        Vec3i size = this.access.getBoxSize();
        BlockPos boxMin = this.access.getBoxMin();

        return new net.minecraft.util.math.Vec3d(boxMin.getX() + size.getX() / 2.0,
                                                  boxMin.getY() + size.getY() / 2.0,
                                                  boxMin.getZ() + size.getZ() / 2.0);
    }

    /**
     * Distance at which the bounding diagonal fits inside both the vertical and the
     * aspect-derived horizontal FOV (the narrower one clips corners on tall widgets otherwise).
     */
    public double getDefaultDistance(double fovYDegrees, double aspect)
    {
        Vec3i size = this.access.getBoxSize();
        double diagonal = Math.sqrt(size.getX() * (double) size.getX() + size.getY() * (double) size.getY() + size.getZ() * (double) size.getZ());
        double halfDiagonal = diagonal / 2.0;

        double halfFovY = Math.toRadians(fovYDegrees) / 2.0;
        double halfFovX = Math.atan(Math.tan(halfFovY) * aspect);

        double distanceForY = halfDiagonal / Math.sin(halfFovY);
        double distanceForX = halfDiagonal / Math.sin(halfFovX);

        return Math.max(3.0, Math.max(distanceForY, distanceForX) * 1.1);
    }

    public void tick()
    {
        if (this.tessellationDone)
        {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        BlockRendererDispatcher dispatcher = mc.getBlockRendererDispatcher();
        Vec3i size = this.access.getBoxSize();
        BlockPos boxMin = this.access.getBoxMin();
        int sizeX = size.getX();
        int sizeY = size.getY();
        long limit = Math.min(this.totalVolume, this.cursor + TESSELLATE_BUDGET_PER_TICK);

        for (; this.cursor < limit; this.cursor++)
        {
            long index = this.cursor;
            int x = (int) (index % sizeX);
            index /= sizeX;
            int y = (int) (index % sizeY);
            int z = (int) (index / sizeY);

            BlockPos pos = boxMin.add(x, y, z);
            IBlockState state = this.access.getBlockState(pos);

            if (state.getBlock() == net.minecraft.init.Blocks.AIR)
            {
                continue;
            }

            BlockRenderLayer layer = state.getBlock().getRenderLayer();
            BufferBuilder buffer = this.buildingBuffers.get(layer);

            if (buffer == null)
            {
                buffer = new BufferBuilder(0x200000);
                buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
                this.buildingBuffers.put(layer, buffer);
            }

            dispatcher.renderBlock(state, pos, this.access, buffer);
        }

        if (this.cursor >= this.totalVolume)
        {
            this.upload();
            this.tessellationDone = true;
        }
    }

    private void upload()
    {
        for (Map.Entry<BlockRenderLayer, BufferBuilder> entry : this.buildingBuffers.entrySet())
        {
            BufferBuilder buffer = entry.getValue();
            buffer.finishDrawing();

            VertexBuffer vbo = new VertexBuffer(DefaultVertexFormats.BLOCK);
            VERTEX_UPLOADER.setVertexBuffer(vbo);
            VERTEX_UPLOADER.draw(buffer);

            this.vbos.put(entry.getKey(), vbo);
        }

        this.buildingBuffers.clear();
    }

    public void draw(int width, int height, double fov, float yRot, float xRot, double distance,
                      double targetX, double targetY, double targetZ, boolean renderTileEntities, boolean transparentBackground)
    {
        Vec3i size = this.access.getBoxSize();
        double diagonal = Math.sqrt(size.getX() * (double) size.getX() + size.getY() * (double) size.getY() + size.getZ() * (double) size.getZ());

        // Opaque clear: the FBO is blitted with blending on, alpha 0 would let the world behind
        // the GUI show through. Only captureImage() wants the transparent clear.
        GlStateManager.viewport(0, 0, width, height);
        GlStateManager.clearColor(transparentBackground ? 0f : 0.05f, transparentBackground ? 0f : 0.05f, transparentBackground ? 0f : 0.05f, transparentBackground ? 0f : 1f);
        GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        Project.gluPerspective((float) fov, (float) width / (float) height, 0.05f, (float) Math.max(16.0, diagonal * 4.0));

        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.translate(0.0, 0.0, -distance);
        GlStateManager.rotate(xRot, 1f, 0f, 0f);
        GlStateManager.rotate(yRot, 0f, 1f, 0f);
        GlStateManager.translate(-targetX, -targetY, -targetZ);

        // Pin every GL state this depends on; callers (GUI frame, capture) leave arbitrary state.
        GlStateManager.disableFog();
        GlStateManager.disableColorMaterial();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableTexGenCoord(GlStateManager.TexGen.S);
        GlStateManager.disableTexGenCoord(GlStateManager.TexGen.T);
        GlStateManager.disableTexGenCoord(GlStateManager.TexGen.R);
        GlStateManager.disableTexGenCoord(GlStateManager.TexGen.Q);
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1f);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

        // Tile entity renderers sample the lightmap unit unconditionally; a 1x1 white texture
        // there is the full-bright identity. Must switch units via GlStateManager (it caches
        // per-unit state), never the raw OpenGlHelper.setActiveTexture - see AGENTS.md Gotchas.
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.enableTexture2D();
        GlStateManager.bindTexture(getFullBrightLightmapTexture());
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);

        this.drawLayer(BlockRenderLayer.SOLID);
        this.drawLayer(BlockRenderLayer.CUTOUT_MIPPED);
        this.drawLayer(BlockRenderLayer.CUTOUT);

        GlStateManager.enableBlend();
        // Alpha factors composite ("over"); (ONE, ZERO) would overwrite an opaque block's alpha
        // on a transparent capture.
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                                            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.depthMask(false);
        this.drawLayer(BlockRenderLayer.TRANSLUCENT);
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();

        // Tile entity models take the current color; drawLayer() invalidated the cache.
        GlStateManager.color(1f, 1f, 1f, 1f);

        if (renderTileEntities)
        {
            this.drawTileEntities();
        }

        // Tile entity renderers (end portal especially) leave lighting/blend/texgen changed.
        GlStateManager.disableLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.disableTexGenCoord(GlStateManager.TexGen.S);
        GlStateManager.disableTexGenCoord(GlStateManager.TexGen.T);
        GlStateManager.disableTexGenCoord(GlStateManager.TexGen.R);
        GlStateManager.disableTexGenCoord(GlStateManager.TexGen.Q);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.enableTexture2D();
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.disableTexture2D();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);

        GlStateManager.disableCull();
        GlStateManager.disableDepth();
        GlStateManager.color(1f, 1f, 1f, 1f);

        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.popMatrix();
    }

    // Created once, never deleted: one 1x1 texture for the mod's lifetime.
    private static int fullBrightLightmapTexture = -1;

    private static int getFullBrightLightmapTexture()
    {
        if (fullBrightLightmapTexture < 0)
        {
            fullBrightLightmapTexture = GlStateManager.generateTexture();
            GlStateManager.bindTexture(fullBrightLightmapTexture);
            GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

            java.nio.ByteBuffer pixel = org.lwjgl.BufferUtils.createByteBuffer(4);
            pixel.put((byte) 255).put((byte) 255).put((byte) 255).put((byte) 255);
            pixel.flip();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, 1, 1, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
        }

        return fullBrightLightmapTexture;
    }

    /**
     * Renders one frame into a throwaway FBO with a transparent clear and reads it back as a
     * background-free image. Caller must have called {@link #tick()} this frame.
     */
    public java.awt.image.BufferedImage captureImage(int width, int height, double fov, float yRot, float xRot, double distance,
                                                       double targetX, double targetY, double targetZ, boolean renderTileEntities)
    {
        Framebuffer captureFbo = new Framebuffer(width, height, true);

        try
        {
            captureFbo.bindFramebuffer(true);
            this.draw(width, height, fov, yRot, xRot, distance, targetX, targetY, targetZ, renderTileEntities, true);

            // Same read-back as ScreenShotHelper; layout matches TYPE_INT_ARGB directly.
            GlStateManager.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GlStateManager.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
            GlStateManager.bindTexture(captureFbo.framebufferTexture);

            java.nio.IntBuffer pixelBuffer = org.lwjgl.BufferUtils.createIntBuffer(width * height);
            GlStateManager.glGetTexImage(GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL12.GL_BGRA,
                                          org.lwjgl.opengl.GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixelBuffer);

            int[] pixels = new int[width * height];
            pixelBuffer.get(pixels);

            // GL rows are bottom-to-top; flip.
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);

            for (int row = 0; row < height; row++)
            {
                image.setRGB(0, row, width, 1, pixels, (height - 1 - row) * width, width);
            }

            return image;
        }
        finally
        {
            Minecraft.getMinecraft().getFramebuffer().bindFramebuffer(true);
            captureFbo.deleteFramebuffer();
        }
    }

    private void drawLayer(BlockRenderLayer layer)
    {
        VertexBuffer vbo = this.vbos.get(layer);

        if (vbo == null)
        {
            return;
        }

        // Bind before the pointer setup: the pointer calls take offsets into the bound buffer.
        vbo.bindBuffer();

        GlStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GlStateManager.glVertexPointer(3, GL11.GL_FLOAT, 28, 0);
        GlStateManager.glEnableClientState(GL11.GL_COLOR_ARRAY);
        GlStateManager.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, 28, 12);
        GlStateManager.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GlStateManager.glTexCoordPointer(2, GL11.GL_FLOAT, 28, 16);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.glTexCoordPointer(2, GL11.GL_SHORT, 28, 24);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);

        vbo.drawArrays(GL11.GL_QUADS);
        OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, 0);

        // Same teardown as RenderGlobal.renderBlockLayer; a color-array draw leaves the GL
        // current color undefined, so the cached color must be reset.
        GlStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        GlStateManager.glDisableClientState(GL11.GL_COLOR_ARRAY);
        GlStateManager.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.resetColor();
    }

    private void drawTileEntities()
    {
        TileEntityRendererDispatcher dispatcher = TileEntityRendererDispatcher.instance;
        int stackDepth = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);

        for (BlockPos pos : this.tileEntityPositions)
        {
            TileEntity te = this.access.getTileEntity(pos);

            if (te == null || TILE_ENTITY_BLACKLIST.contains(te.getClass()))
            {
                continue;
            }

            try
            {
                dispatcher.render(te, pos.getX(), pos.getY(), pos.getZ(), 0f);
            }
            catch (Throwable t)
            {
                TILE_ENTITY_BLACKLIST.add(te.getClass());

                // A renderer that throws mid-way leaves its matrix pushes on the stack.
                GlStateManager.matrixMode(GL11.GL_MODELVIEW);

                while (GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH) > stackDepth)
                {
                    GlStateManager.popMatrix();
                }
            }
        }
    }

    public void close()
    {
        for (BufferBuilder buffer : this.buildingBuffers.values())
        {
            buffer.finishDrawing();
        }

        this.buildingBuffers.clear();

        for (VertexBuffer vbo : this.vbos.values())
        {
            vbo.deleteGlBuffers();
        }

        this.vbos.clear();
    }
}
