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
    private final Set<Class<?>> tileEntityBlacklist = new HashSet<>();

    private int cursor;
    private int totalVolume;
    private boolean tessellationDone;

    private final EnumMap<BlockRenderLayer, BufferBuilder> buildingBuffers = new EnumMap<>(BlockRenderLayer.class);
    private final EnumMap<BlockRenderLayer, VertexBuffer> vbos = new EnumMap<>(BlockRenderLayer.class);
    private static final VertexBufferUploader VERTEX_UPLOADER = new VertexBufferUploader();

    public void setup(ISchematic schematic)
    {
        this.access = new SchematicBlockAccess(schematic);
        this.tileEntityPositions = this.access.getTileEntityPositions();
        Vec3i size = this.access.getBoxSize();
        this.totalVolume = size.getX() * size.getY() * size.getZ();
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
     * Distance along the view axis at which the schematic's full bounding diagonal fits inside
     * both the vertical and the (aspect-derived) horizontal field of view - not just "far enough
     * for the diagonal", which was too close on any narrow/tall widget (aspect far from 1:1, e.g.
     * the side panel or a tall multi-region schematic): the diagonal fit vertically long before it
     * fit through the much narrower horizontal FOV, clipping the corners.
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

        // Small margin on top of the exact fit so corners aren't right on the frustum edge.
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
        int limit = Math.min(this.totalVolume, this.cursor + TESSELLATE_BUDGET_PER_TICK);

        for (; this.cursor < limit; this.cursor++)
        {
            int index = this.cursor;
            int x = index % sizeX;
            index /= sizeX;
            int y = index % sizeY;
            int z = index / sizeY;

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
                      double targetX, double targetY, double targetZ, boolean renderTileEntities)
    {
        this.draw(width, height, fov, yRot, xRot, distance, targetX, targetY, targetZ, renderTileEntities, false);
    }

    private void draw(int width, int height, double fov, float yRot, float xRot, double distance,
                       double targetX, double targetY, double targetZ, boolean renderTileEntities, boolean transparentBackground)
    {
        Vec3i size = this.access.getBoxSize();
        double diagonal = Math.sqrt(size.getX() * (double) size.getX() + size.getY() * (double) size.getY() + size.getZ() * (double) size.getZ());

        // Opaque, not alpha 0: this FBO is blitted with blending on (see PreviewRenderUtils),
        // so a transparent clear let whatever was already on screen behind the widget - the
        // live game world, for this GUI - show through anywhere the schematic doesn't cover.
        // captureImage() below is the one caller that wants the transparent clear on purpose,
        // for a background-free exported image read back straight from this FBO.
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

        // This draws from two very different contexts: the normal per-frame GUI render (state
        // left by whatever malilib drew just before us) and a one-off capture triggered from a
        // button click (state left by whichever GL calls last ran during input handling - often
        // Framebuffer's own post-render blit, which disables alpha test and leaves a foreign
        // blend func). Pin every piece of state this method depends on instead of trusting
        // either caller's leftovers - found via a real capture silently dropping the top face of
        // a redstone-wire-covered block (alpha test off let the wire's near-transparent padding
        // texels through) while onscreen looked correct (that frame's state happened to be sane).
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

        // Every block is tessellated against a constant full-bright light value, so the static
        // geometry itself doesn't need the lightmap unit - but tile entity renderers (e.g.
        // TileEntityChestRenderer) unconditionally sample it via OpenGlHelper.setLightmapTextureCoords
        // and expect *some* valid texture bound there. Leaving the unit merely disabled worked for
        // plain blocks but left whatever texture happened to still be bound from earlier GUI
        // rendering in place for tile entities to sample - looked exactly like a chest rendering
        // with a wrong (reddish) tint. Bind a real 1x1 opaque-white texture instead: multiplying by
        // white is the correct "full bright" identity regardless of what coordinates get sampled.
        //
        // The unit switch MUST go through GlStateManager.setActiveTexture, never the raw
        // OpenGlHelper.setActiveTexture: GlStateManager caches bound-texture / texture2D state
        // per unit, indexed by the unit *it* last switched to. A raw switch leaves that index on
        // unit 0, so the bind/enable/disable below get recorded against unit 0's cache while GL
        // applies them to unit 1 - and unit 1's own cache entry keeps saying "the lightmap is
        // bound". Next world frame, EntityRenderer.enableLightmap()'s bindTexture(lightmap) is
        // then a cached no-op, and the whole world renders lit by this 1x1 white texture.
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.enableTexture2D();
        GlStateManager.bindTexture(getFullBrightLightmapTexture());
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);

        this.drawLayer(BlockRenderLayer.SOLID);
        this.drawLayer(BlockRenderLayer.CUTOUT_MIPPED);
        this.drawLayer(BlockRenderLayer.CUTOUT);

        GlStateManager.enableBlend();
        // Alpha factors (ONE, ONE_MINUS_SRC_ALPHA) match the standard "over" compositing formula
        // for the alpha channel (outA = srcA + dstA*(1-srcA)); the previous (ONE, ZERO) replaced
        // whatever alpha an opaque block behind a translucent quad had already written with the
        // translucent quad's own alpha - on a background-free capture that meant a translucent
        // block (water, a portal...) sitting in front of an opaque one made the opaque block
        // read back as semi-transparent too.
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                                            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.depthMask(false);
        this.drawLayer(BlockRenderLayer.TRANSLUCENT);
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();

        // Real glColor, not a cached no-op (drawLayer() just invalidated the cache): tile entity
        // models draw without a color array, so they take whatever the current color is.
        GlStateManager.color(1f, 1f, 1f, 1f);

        if (renderTileEntities)
        {
            this.drawTileEntities();
        }

        // Tile entity renderers (the end portal one especially - see AGENTS.md Gotchas) leave GL
        // state behind that vanilla only ever relies on the next world-render frame to reset:
        // lighting back on, a foreign blend func, texgen coords still enabled. Left alone, that
        // state leaks into whatever malilib draws right after us in the same GUI frame - looked
        // exactly like the whole screen going flat grey/white the moment an end-portal-bearing
        // schematic was selected.
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

    // Created once and never deleted - a single 1x1 texture shared for the mod's whole lifetime,
    // same as the block atlas itself; not the kind of per-schematic GL resource that needs an owner.
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
     * background-free image, for the fullscreen screen's save/copy buttons. Uses its own FBO
     * (never the widget's own or the shared small-preview one) so this has no effect on their
     * size or lifecycle. Caller is expected to have already called {@link #tick()} this frame.
     */
    public java.awt.image.BufferedImage captureImage(int width, int height, double fov, float yRot, float xRot, double distance,
                                                       double targetX, double targetY, double targetZ, boolean renderTileEntities)
    {
        Framebuffer captureFbo = new Framebuffer(width, height, true);

        try
        {
            captureFbo.bindFramebuffer(true);
            this.draw(width, height, fov, yRot, xRot, distance, targetX, targetY, targetZ, renderTileEntities, true);

            // Same GL_BGRA + GL_UNSIGNED_INT_8_8_8_8_REV read-back ScreenShotHelper.createScreenshot
            // uses - that byte layout matches TYPE_INT_ARGB directly, no channel swapping needed.
            // (ScreenShotHelper itself can't be reused: it always builds a TYPE_INT_RGB image,
            // discarding alpha.)
            GlStateManager.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GlStateManager.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
            GlStateManager.bindTexture(captureFbo.framebufferTexture);

            java.nio.IntBuffer pixelBuffer = org.lwjgl.BufferUtils.createIntBuffer(width * height);
            GlStateManager.glGetTexImage(GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL12.GL_BGRA,
                                          org.lwjgl.opengl.GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixelBuffer);

            int[] pixels = new int[width * height];
            pixelBuffer.get(pixels);

            // glGetTexImage returns rows bottom-to-top (GL texture origin is bottom-left);
            // BufferedImage.setRGB expects top-to-bottom - flip or the saved/copied image is
            // vertically mirrored relative to what's on screen.
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

        // glVertexPointer/glColorPointer/glTexCoordPointer interpret their last argument as a
        // byte offset into the currently-bound GL_ARRAY_BUFFER, not a client-side pointer, so
        // the VBO must be bound *before* the pointer setup - not after (confirmed the hard way:
        // LWJGL throws "Cannot use offsets when Array Buffer Object is disabled" otherwise).
        vbo.bindBuffer();

        // The legacy client-side array states aren't guaranteed on in this GUI context
        // (unlike the constant world-render loop malilib's own VBO helpers assume) - enable
        // them ourselves before pointer setup, matching WorldVertexBufferUploader's own draws.
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

        // Same teardown RenderGlobal.renderBlockLayer does after its VBO pass: the client-side
        // array states and pointers would otherwise stay enabled - pointing at offsets into a
        // buffer that is no longer bound - for whatever draws next, and a color-array draw leaves
        // the GL current color undefined, so GlStateManager's cached color must be invalidated
        // or its next color(1,1,1,1) is a silent no-op.
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

        for (BlockPos pos : this.tileEntityPositions)
        {
            TileEntity te = this.access.getTileEntity(pos);

            if (te == null || this.tileEntityBlacklist.contains(te.getClass()))
            {
                continue;
            }

            try
            {
                dispatcher.render(te, pos.getX(), pos.getY(), pos.getZ(), 0f);
            }
            catch (Throwable t)
            {
                this.tileEntityBlacklist.add(te.getClass());
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
