package dev.froyln.schematicpreview.gui;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.annotation.Nullable;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import fi.dy.masa.malilib.gui.BaseScreen;
import fi.dy.masa.malilib.gui.util.ScreenContext;
import fi.dy.masa.malilib.gui.widget.InteractableWidget;
import fi.dy.masa.malilib.render.ShapeRenderUtils;

import dev.froyln.schematicpreview.config.Configs;
import dev.froyln.schematicpreview.render.PreviewCache;
import dev.froyln.schematicpreview.render.PreviewRenderer;
import dev.froyln.schematicpreview.render.PreviewRenderUtils;
import fi.dy.masa.litematica.schematic.ISchematic;

/**
 * Live 3D render of a schematic, tessellated and cached by {@link PreviewCache}. Each instance
 * (side panel, fullscreen) owns its own {@link Framebuffer} sized to its own pixel dimensions,
 * and its own camera state; the tessellated geometry itself is shared via the cache.
 */
public class PreviewWidget extends InteractableWidget
{
    private static final int BUTTON_SIZE = 12;
    private static final double MIN_DISTANCE = 1.5;

    private final Path path;
    @Nullable private Framebuffer fbo;

    private boolean cameraInitialized;
    private float yRot;
    private float xRot;
    private double distance;
    private double targetX;
    private double targetY;
    private double targetZ;
    private double maxDistance = 64.0;

    @Nullable private Consumer<BufferedImage> pendingCapture;

    private boolean freecam;
    private boolean dragging;
    private int dragButton;
    private int dragStartMouseX;
    private int dragStartMouseY;
    private float dragStartYRot;
    private float dragStartXRot;
    private double dragStartTargetX;
    private double dragStartTargetY;
    private double dragStartTargetZ;

    public PreviewWidget(int x, int y, int width, int height, Path path)
    {
        super(x, y, width, height);

        this.path = path;
    }

    private void openFullscreen()
    {
        BaseScreen.openScreenWithParent(new PreviewFullscreenScreen(this.path));
    }

    private boolean isOverFullscreenButton(int mouseX, int mouseY)
    {
        int x = this.getFullscreenButtonX();
        int y = this.getY() + 2;
        return mouseX >= x && mouseX < x + BUTTON_SIZE && mouseY >= y && mouseY < y + BUTTON_SIZE;
    }

    private boolean isOverFreecamButton(int mouseX, int mouseY)
    {
        int x = this.getFreecamButtonX();
        int y = this.getY() + 2;
        return mouseX >= x && mouseX < x + BUTTON_SIZE && mouseY >= y && mouseY < y + BUTTON_SIZE;
    }

    private int getFullscreenButtonX()
    {
        return this.getRight() - 2 - BUTTON_SIZE;
    }

    private int getFreecamButtonX()
    {
        return this.getFullscreenButtonX() - 2 - BUTTON_SIZE;
    }

    @Override
    protected boolean onMouseClicked(int mouseX, int mouseY, int mouseButton)
    {
        if (mouseButton == 0 && this.isOverFullscreenButton(mouseX, mouseY))
        {
            this.openFullscreen();
            return true;
        }

        if (mouseButton == 0 && this.isOverFreecamButton(mouseX, mouseY))
        {
            this.freecam = !this.freecam;
            return true;
        }

        if (mouseButton == 0 || mouseButton == 1)
        {
            this.dragging = true;
            this.dragButton = mouseButton;
            this.dragStartMouseX = mouseX;
            this.dragStartMouseY = mouseY;
            this.dragStartYRot = this.yRot;
            this.dragStartXRot = this.xRot;
            this.dragStartTargetX = this.targetX;
            this.dragStartTargetY = this.targetY;
            this.dragStartTargetZ = this.targetZ;
            return true;
        }

        return super.onMouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void onMouseReleased(int mouseX, int mouseY, int mouseButton)
    {
        this.dragging = false;
        super.onMouseReleased(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean onMouseMoved(int mouseX, int mouseY)
    {
        if (this.dragging)
        {
            int dx = mouseX - this.dragStartMouseX;
            int dy = mouseY - this.dragStartMouseY;
            boolean rotating = (this.dragButton == 0) != this.freecam;

            if (rotating)
            {
                this.yRot = this.dragStartYRot + dx * 0.5f;
                this.xRot = MathHelper.clamp(this.dragStartXRot - dy * 0.5f, -90f, 90f);
            }
            else
            {
                // ponytail: pan uses a yaw-only right vector and world-up, ignoring pitch -
                // good enough for a preview pan, a full trackball basis isn't worth it here.
                double yawRad = Math.toRadians(this.dragStartYRot);
                double rightX = Math.cos(yawRad);
                double rightZ = Math.sin(yawRad);
                double panScale = this.distance / 300.0;

                this.targetX = this.dragStartTargetX - dx * rightX * panScale;
                this.targetZ = this.dragStartTargetZ - dx * rightZ * panScale;
                this.targetY = this.dragStartTargetY + dy * panScale;
            }

            return true;
        }

        return super.onMouseMoved(mouseX, mouseY);
    }

    @Override
    protected boolean onMouseScrolled(int mouseX, int mouseY, double mouseWheelDelta)
    {
        double factor = mouseWheelDelta < 0 ? 1.1 : (1.0 / 1.1);
        this.distance = MathHelper.clamp(this.distance * factor, MIN_DISTANCE, this.maxDistance);
        return true;
    }

    @Override
    public void renderAt(int x, int y, float z, ScreenContext ctx)
    {
        super.renderAt(x, y, z, ctx);

        int width = this.getWidth();
        int height = this.getHeight();

        if (width <= 0 || height <= 0)
        {
            return;
        }

        CompletableFuture<ISchematic> future = PreviewCache.getSchematic(this.path);

        if (future.isDone() == false)
        {
            PreviewRenderUtils.renderPlaceholder(x, y, width, height, z, "schematicpreview.label.preview.loading", ctx);
            return;
        }

        ISchematic schematic = future.getNow(null);

        if (schematic == null)
        {
            PreviewRenderUtils.renderPlaceholder(x, y, width, height, z, "schematicpreview.label.preview.invalid", ctx);
            return;
        }

        PreviewRenderer renderer = PreviewCache.getRenderer(this.path, schematic);

        if (this.cameraInitialized == false)
        {
            Vec3d center = renderer.getCenter();
            this.targetX = center.x;
            this.targetY = center.y;
            this.targetZ = center.z;
            this.distance = renderer.getDefaultDistance(Configs.Preview.PREVIEW_FOV.getDoubleValue(), (double) width / height);
            this.maxDistance = this.distance * 8.0;
            this.yRot = (float) Configs.Preview.PREVIEW_ROTATION_Y.getDoubleValue();
            this.xRot = (float) Configs.Preview.PREVIEW_ROTATION_X.getDoubleValue();
            this.cameraInitialized = true;
        }

        renderer.tick();

        this.drawSceneToFbo(width, height, renderer);
        PreviewRenderUtils.blitFramebuffer(this.fbo, x, y, width, height, z);
        this.renderOverlayButtons(x, y, ctx);
        this.serviceCaptureRequest(renderer);
    }

    /**
     * Resolves a pending {@link #requestCapture(Consumer)} right after this frame's own
     * {@link #drawSceneToFbo} call - capturing from here, instead of directly from the Save/Copy
     * button's click handler, means the capture always runs with the exact GL state this frame
     * just proved works (see the state-pinning comment in {@link PreviewRenderer#draw}), and with
     * whatever tessellation progress this frame's {@code renderer.tick()} just made. A click
     * handler runs during input processing, before any of that - capturing there could see
     * leftover state from the last thing the previous frame's GUI drew, or from unrelated input
     * handling (the "wrong image" / "white screen" bugs this replaced).
     */
    private void serviceCaptureRequest(PreviewRenderer renderer)
    {
        if (this.pendingCapture == null)
        {
            return;
        }

        Consumer<BufferedImage> callback = this.pendingCapture;
        this.pendingCapture = null;

        if (renderer.isTessellationDone() == false || this.fbo == null)
        {
            callback.accept(null);
            return;
        }

        BufferedImage image = renderer.captureImage(this.fbo.framebufferWidth, this.fbo.framebufferHeight,
                                                     Configs.Preview.PREVIEW_FOV.getDoubleValue(), this.yRot, this.xRot, this.distance,
                                                     this.targetX, this.targetY, this.targetZ, Configs.Preview.RENDER_TILE_ENTITIES.getBooleanValue());
        callback.accept(image);
    }

    private void drawSceneToFbo(int width, int height, PreviewRenderer renderer)
    {
        int scale = new ScaledResolution(this.mc).getScaleFactor();
        int texWidth = Math.max(1, width * scale);
        int texHeight = Math.max(1, height * scale);

        if (this.fbo == null || this.fbo.framebufferWidth != texWidth || this.fbo.framebufferHeight != texHeight)
        {
            if (this.fbo != null)
            {
                this.fbo.deleteFramebuffer();
            }

            this.fbo = new Framebuffer(texWidth, texHeight, true);
            this.fbo.setFramebufferFilter(GL11.GL_NEAREST);
        }

        this.fbo.bindFramebuffer(true);

        renderer.draw(texWidth, texHeight, Configs.Preview.PREVIEW_FOV.getDoubleValue(), this.yRot, this.xRot, this.distance,
                      this.targetX, this.targetY, this.targetZ, Configs.Preview.RENDER_TILE_ENTITIES.getBooleanValue(), false);

        this.mc.getFramebuffer().bindFramebuffer(true);
    }

    private void renderOverlayButtons(int x, int y, ScreenContext ctx)
    {
        int barY = this.getY() + 2;
        int freecamColor = this.freecam ? 0xFF3070FF : 0x80000000;

        ShapeRenderUtils.renderRectangle(this.getFreecamButtonX(), barY, this.getZ() + 1f, BUTTON_SIZE, BUTTON_SIZE, freecamColor);
        ShapeRenderUtils.renderRectangle(this.getFullscreenButtonX(), barY, this.getZ() + 1f, BUTTON_SIZE, BUTTON_SIZE, 0x80000000);
        SchematicPreviewIcons.FREECAM.renderAt(this.getFreecamButtonX(), barY, this.getZ() + 2f, true, this.isOverFreecamButton(ctx.mouseX, ctx.mouseY));
        SchematicPreviewIcons.FULLSCREEN.renderAt(this.getFullscreenButtonX(), barY, this.getZ() + 2f, true, this.isOverFullscreenButton(ctx.mouseX, ctx.mouseY));
    }

    /**
     * Asks for the current view as a transparent-background image, for the fullscreen screen's
     * save/copy buttons. {@code callback} runs on the next render frame (see
     * {@link #serviceCaptureRequest}), with {@code null} if no frame has been rendered yet
     * (schematic still loading/invalid), or if a large schematic's VBOs haven't finished
     * incremental tessellation - {@link PreviewRenderer} only uploads its VBOs once the whole
     * volume is done, so capturing mid-tessellation would silently draw an empty/partial scene.
     */
    public void requestCapture(Consumer<BufferedImage> callback)
    {
        if (this.cameraInitialized == false || this.fbo == null)
        {
            callback.accept(null);
            return;
        }

        this.pendingCapture = callback;
    }

    public void close()
    {
        if (this.fbo != null)
        {
            this.fbo.deleteFramebuffer();
            this.fbo = null;
        }
    }
}
