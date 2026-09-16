package dev.froyln.schematicpreview.mixin;

import java.nio.file.Path;
import java.util.function.Predicate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import fi.dy.masa.malilib.gui.widget.list.BaseFileBrowserWidget;
import fi.dy.masa.malilib.gui.widget.list.header.DirectoryNavigationWidget;

/**
 * Exposes {@code BaseFileBrowserWidget}'s private navigation bar widget, so the preview-type
 * button can be positioned next to it, and its protected file filter, so directory rows can
 * find their first schematic file using the same extension filter Litematica configured the
 * browser with.
 */
@Mixin(BaseFileBrowserWidget.class)
public interface BaseFileBrowserWidgetAccessor
{
    @Accessor(value = "navigationWidget", remap = false)
    DirectoryNavigationWidget schematicpreview$getNavigationWidget();

    @Accessor(value = "fileFilter", remap = false)
    Predicate<Path> schematicpreview$getFileFilter();
}
