package dev.froyln.schematicpreview.render;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import javax.annotation.Nullable;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.math.Vec3d;

import dev.froyln.schematicpreview.config.Configs;
import fi.dy.masa.litematica.schematic.ISchematic;
import fi.dy.masa.litematica.schematic.ISchematicRegion;
import fi.dy.masa.litematica.schematic.SchematicType;

/**
 * Single owner of the off-thread schematic loads and the tessellated {@link PreviewRenderer}s
 * for whatever schematic path(s) are currently on screen. Freed entirely once no screen is
 * open (see {@link #tickClose()}), so GL resources never outlive the GUI that requested them.
 */
public final class PreviewCache
{
    private static final Executor LOADER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SchematicPreview-Loader");
        thread.setDaemon(true);
        return thread;
    });

    private static final Map<Path, CompletableFuture<ISchematic>> SCHEMATICS = new HashMap<>();
    private static final Map<Path, PreviewRenderer> RENDERERS = new HashMap<>();
    // Directory -> its first schematic file (Optional.empty() when it has none). malilib
    // rebuilds every entry widget on each scroll/refresh, so without this the browser did a
    // Files.list per directory row per rebuild.
    private static final Map<Path, Optional<Path>> FIRST_SCHEMATICS = new HashMap<>();

    // Shared by every list/tile row preview - never one Framebuffer per row (see AGENTS.md
    // security invariants: small widgets share one FBO and render sequentially).
    @Nullable private static Framebuffer smallFbo;

    private PreviewCache()
    {
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
                ISchematic schematic = SchematicType.tryCreateSchematicFrom(file);

                if (schematic != null)
                {
                    // Warms Litematica's per-container block counts here, off-thread: the
                    // first call walks the whole container, later ones are cached, so
                    // getBlockCount() on the render thread never stalls the GUI.
                    getBlockCount(schematic);
                }

                return schematic;
            }
            catch (Throwable t)
            {
                return null;
            }
        }, LOADER);
    }

    public static PreviewRenderer getRenderer(Path file, ISchematic schematic)
    {
        return RENDERERS.computeIfAbsent(file, p -> {
            PreviewRenderer renderer = new PreviewRenderer();
            renderer.setup(schematic);
            return renderer;
        });
    }

    /**
     * First regular file in {@code directory} (sorted by path) accepted by {@code filter}, or
     * {@code null}; cached until {@link #close()}.
     */
    @Nullable
    public static Path getFirstSchematicIn(Path directory, Predicate<Path> filter)
    {
        return FIRST_SCHEMATICS.computeIfAbsent(directory, dir -> {
            try (Stream<Path> stream = Files.list(dir))
            {
                return stream.filter(Files::isRegularFile).filter(filter).sorted().findFirst();
            }
            catch (IOException ignore)
            {
                return Optional.empty();
            }
        }).orElse(null);
    }

    /**
     * Non-air block count of {@code schematic}, counted from the containers - the file's own
     * {@code TotalBlocks} metadata is read verbatim by Litematica and a third-party writer can
     * put anything there, so it can't be what gates memory use.
     */
    public static long getBlockCount(ISchematic schematic)
    {
        long count = 0;

        for (ISchematicRegion region : schematic.getRegions().values())
        {
            count += region.getBlockStateContainer().getTotalBlockCount();
        }

        return count;
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
                      center.x, center.y, center.z, Configs.Preview.RENDER_TILE_ENTITIES.getBooleanValue(), false);

        Minecraft.getMinecraft().getFramebuffer().bindFramebuffer(true);

        PreviewRenderUtils.blitFramebuffer(smallFbo, x, y, width, height, width, height, z);

        return true;
    }

    public static void tickClose()
    {
        boolean hasState = SCHEMATICS.isEmpty() == false || RENDERERS.isEmpty() == false ||
                           FIRST_SCHEMATICS.isEmpty() == false || smallFbo != null;

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
        invalidateDirectory(file.getParent());

        PreviewRenderer renderer = RENDERERS.remove(file);

        if (renderer != null)
        {
            renderer.close();
        }
    }

    /** Forgets which file is first in {@code directory}, e.g. after a new file was written into it. */
    public static void invalidateDirectory(@Nullable Path directory)
    {
        FIRST_SCHEMATICS.remove(directory);
    }

    public static void close()
    {
        for (PreviewRenderer renderer : RENDERERS.values())
        {
            renderer.close();
        }

        RENDERERS.clear();
        SCHEMATICS.clear();
        FIRST_SCHEMATICS.clear();

        if (smallFbo != null)
        {
            smallFbo.deleteFramebuffer();
            smallFbo = null;
        }
    }
}
