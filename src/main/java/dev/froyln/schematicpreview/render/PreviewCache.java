package dev.froyln.schematicpreview.render;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import javax.annotation.Nullable;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.math.Vec3d;

import dev.froyln.schematicpreview.config.Configs;
import fi.dy.masa.litematica.schematic.ISchematic;
import fi.dy.masa.litematica.schematic.SchematicType;

/**
 * Single owner of the off-thread schematic loads and the tessellated {@link PreviewRenderer}s
 * for whatever schematic path(s) are currently on screen. Freed entirely once no screen is
 * open (see {@link #tickClose()}), so GL resources never outlive the GUI that requested them.
 */
public final class PreviewCache
{
    private static final Executor LOADER = Executors.newSingleThreadExecutor(daemonThreadFactory());

    private static final Map<Path, CompletableFuture<ISchematic>> SCHEMATICS = new HashMap<>();
    private static final Map<Path, PreviewRenderer> RENDERERS = new HashMap<>();

    // Shared by every list/tile row preview - never one Framebuffer per row (see AGENTS.md
    // security invariants: small widgets share one FBO and render sequentially).
    @Nullable private static Framebuffer smallFbo;

    private PreviewCache()
    {
    }

    private static ThreadFactory daemonThreadFactory()
    {
        return runnable -> {
            Thread thread = new Thread(runnable, "SchematicPreview-Loader");
            thread.setDaemon(true);
            return thread;
        };
    }

    public static CompletableFuture<ISchematic> getSchematic(Path file)
    {
        return SCHEMATICS.computeIfAbsent(file, PreviewCache::load);
    }

    private static CompletableFuture<ISchematic> load(Path file)
    {
        return CompletableFuture.supplyAsync(() -> {
            try
            {
                return SchematicType.tryCreateSchematicFrom(file);
            }
            catch (Throwable t)
            {
                return null;
            }
        }, LOADER);
    }

    @Nullable
    public static PreviewRenderer getRenderer(Path file, ISchematic schematic)
    {
        return RENDERERS.computeIfAbsent(file, p -> {
            PreviewRenderer renderer = new PreviewRenderer();
            renderer.setup(schematic);
            return renderer;
        });
    }

    /**
     * Renders a small, non-interactive preview of {@code file} at a fixed camera angle into
     * the given rectangle (widget/GUI coordinates), gated by {@code Configs.Preview
     * .PREVIEW_MAX_VOLUME} per the Security invariants in AGENTS.md. Returns {@code false}
     * (drawing nothing) while the schematic is still loading, failed to parse, or exceeds the
     * volume cap - callers are expected to already have their own static fallback (the
     * browser's per-file-type icon) visible underneath for exactly those cases, rather than
     * this drawing its own placeholder. Unlike {@code PreviewWidget}, this never allocates its
     * own {@link Framebuffer}; every caller shares one, resized as needed and rendered into
     * sequentially.
     */
    public static boolean renderSmallPreview(Path file, int x, int y, int width, int height, float z)
    {
        if (width <= 0 || height <= 0)
        {
            return false;
        }

        CompletableFuture<ISchematic> future = getSchematic(file);

        if (future.isDone() == false)
        {
            return false;
        }

        ISchematic schematic = future.getNow(null);

        if (schematic == null || schematic.getMetadata().getTotalVolume() > Configs.Preview.PREVIEW_MAX_VOLUME.getIntegerValue())
        {
            return false;
        }

        PreviewRenderer renderer = getRenderer(file, schematic);

        if (renderer == null)
        {
            return false;
        }

        renderer.tick();

        if (smallFbo == null || smallFbo.framebufferWidth < width || smallFbo.framebufferHeight < height)
        {
            int newWidth = Math.max(width, smallFbo == null ? 0 : smallFbo.framebufferWidth);
            int newHeight = Math.max(height, smallFbo == null ? 0 : smallFbo.framebufferHeight);

            if (smallFbo != null)
            {
                smallFbo.deleteFramebuffer();
            }

            smallFbo = new Framebuffer(newWidth, newHeight, true);
            smallFbo.setFramebufferFilter(GL11.GL_NEAREST);
        }

        smallFbo.bindFramebuffer(true);

        Vec3d center = renderer.getCenter();
        float yRot = (float) Configs.Preview.PREVIEW_ROTATION_Y.getDoubleValue();
        float xRot = (float) Configs.Preview.PREVIEW_ROTATION_X.getDoubleValue();
        double fov = Configs.Preview.PREVIEW_FOV.getDoubleValue();
        renderer.draw(width, height, fov, yRot, xRot, renderer.getDefaultDistance(fov, (double) width / height),
                      center.x, center.y, center.z, Configs.Preview.RENDER_TILE_ENTITIES.getBooleanValue());

        Minecraft.getMinecraft().getFramebuffer().bindFramebuffer(true);

        PreviewRenderUtils.blitFramebuffer(smallFbo, x, y, width, height, width, height, z);

        return true;
    }

    public static void tickClose()
    {
        boolean hasState = SCHEMATICS.isEmpty() == false || RENDERERS.isEmpty() == false || smallFbo != null;

        if (Minecraft.getMinecraft().currentScreen == null && hasState)
        {
            close();
        }
    }

    /**
     * Drops the cached schematic and tessellated renderer for {@code file}, so the next
     * preview request re-reads it from disk. Call after overwriting a schematic file in place
     * (see {@code materials.SchematicSaver}) - otherwise the browser keeps showing the
     * pre-overwrite geometry for that path. Runs on the client thread, same as {@link #close()}.
     */
    public static void invalidate(Path file)
    {
        SCHEMATICS.remove(file);

        PreviewRenderer renderer = RENDERERS.remove(file);

        if (renderer != null)
        {
            renderer.close();
        }
    }

    public static void close()
    {
        for (PreviewRenderer renderer : RENDERERS.values())
        {
            renderer.close();
        }

        RENDERERS.clear();
        SCHEMATICS.clear();

        if (smallFbo != null)
        {
            smallFbo.deleteFramebuffer();
            smallFbo = null;
        }
    }
}
