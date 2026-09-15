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

    public double getDefaultDistance()
    {
        Vec3i size = this.access.getBoxSize();
        double diagonal = Math.sqrt(size.getX() * (double) size.getX() + size.getY() * (double) size.getY() + size.getZ() * (double) size.getZ());

        return Math.max(3.0, diagonal * 1.2);
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
        Vec3i size = this.access.getBoxSize();
        double diagonal = Math.sqrt(size.getX() * (double) size.getX() + size.getY() * (double) size.getY() + size.getZ() * (double) size.getZ());

        GlStateManager.viewport(0, 0, width, height);
        GlStateManager.clearColor(0f, 0f, 0f, 0f);
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

        RenderHelper.disableStandardItemLighting();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

        // The lightmap unit is left disabled: every block is tessellated against a
        // constant full-bright light value, so there is nothing meaningful to sample there.
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.disableTexture2D();
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);

        this.drawLayer(BlockRenderLayer.SOLID);
        this.drawLayer(BlockRenderLayer.CUTOUT_MIPPED);
        this.drawLayer(BlockRenderLayer.CUTOUT);

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                                            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.depthMask(false);
        this.drawLayer(BlockRenderLayer.TRANSLUCENT);
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();

        if (renderTileEntities)
        {
            this.drawTileEntities();
        }

        GlStateManager.disableCull();
        GlStateManager.disableDepth();
        GlStateManager.color(1f, 1f, 1f, 1f);

        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.popMatrix();
    }

    private void drawLayer(BlockRenderLayer layer)
    {
        VertexBuffer vbo = this.vbos.get(layer);

        if (vbo == null)
        {
            return;
        }

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

        vbo.bindBuffer();
        vbo.drawArrays(GL11.GL_QUADS);
        OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, 0);
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
