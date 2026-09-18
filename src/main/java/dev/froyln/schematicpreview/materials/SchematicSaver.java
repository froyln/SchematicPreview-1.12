package dev.froyln.schematicpreview.materials;

import java.nio.file.Path;
import javax.annotation.Nullable;

import fi.dy.masa.litematica.schematic.ISchematic;
import fi.dy.masa.malilib.gui.BaseScreen;
import fi.dy.masa.malilib.gui.ConfirmActionScreen;
import fi.dy.masa.malilib.gui.TextInputScreen;
import fi.dy.masa.malilib.gui.util.GuiUtils;
import fi.dy.masa.malilib.util.FileNameUtils;
import fi.dy.masa.malilib.overlay.message.MessageDispatcher;

import dev.froyln.schematicpreview.gui.PopupScreenCompat;
import dev.froyln.schematicpreview.render.PreviewCache;

/**
 * "Save" / "Save as" for a schematic that was read straight from disk for a material list
 * (never loaded/placed) - lets Replace edits be written back without going through
 * SchematicHolder or a placement. Only schematics with a backing file
 * ({@link ISchematic#getFile()} non-null) can be saved; callers gate on that first.
 */
public final class SchematicSaver
{
    private SchematicSaver()
    {
    }

    /**
     * Opens a confirm dialog, then overwrites {@code schematic}'s own file on confirm.
     */
    public static void save(ISchematic schematic)
    {
        Path file = schematic.getFile();
        String name = file.getFileName().toString();

        ConfirmActionScreen screen = new ConfirmActionScreen(280,
                "schematicpreview.gui.save_schematic.confirm_title",
                () -> overwrite(schematic, file, name),
                "schematicpreview.gui.save_schematic.confirm_message", name);
        screen.setParent(GuiUtils.getCurrentScreen());
        BaseScreen.openScreen(PopupScreenCompat.keepPopupSize(screen));
    }

    /**
     * Opens a text input pre-filled with a "_replaced" suggestion, writing a new file (never
     * overwriting) alongside the source on confirm. Litematica's own {@code writeToFile}
     * refuses an existing name and shows its own error; the input screen stays open on failure
     * since {@code consumeString} returns that result.
     */
    public static void saveAs(ISchematic schematic)
    {
        Path dir = schematic.getFile().getParent();
        String defaultName = FileNameUtils.getFileNameWithoutExtension(schematic.getFile().getFileName().toString()) + "_replaced";

        BaseScreen.openScreenWithParent(PopupScreenCompat.keepPopupSize(
                new TextInputScreen("schematicpreview.gui.save_schematic_as.title",
                                    defaultName, (name) -> writeAs(schematic, dir, name))));
    }

    private static void overwrite(ISchematic schematic, Path file, String name)
    {
        if (schematic.writeToFile(file, true))
        {
            schematic.getMetadata().clearModifiedSinceSaved();
            PreviewCache.invalidate(file);
            MessageDispatcher.success().translate("schematicpreview.message.schematic_saved", name);
        }
    }

    private static boolean writeAs(ISchematic schematic, Path dir, String name)
    {
        if (name.trim().isEmpty())
        {
            return false;
        }

        boolean success = schematic.writeToFile(dir, name, false);

        if (success)
        {
            MessageDispatcher.success().translate("schematicpreview.message.schematic_saved", name);
        }

        return success;
    }
}
