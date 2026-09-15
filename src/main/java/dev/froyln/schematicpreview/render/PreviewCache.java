package dev.froyln.schematicpreview.render;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;

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

    public static void tickClose()
    {
        if (Minecraft.getMinecraft().currentScreen == null && (SCHEMATICS.isEmpty() == false || RENDERERS.isEmpty() == false))
        {
            close();
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
    }
}
