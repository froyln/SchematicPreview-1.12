package dev.froyln.schematicpreview.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import fi.dy.masa.malilib.gui.widget.list.BaseFileBrowserWidget;
import fi.dy.masa.malilib.gui.widget.list.header.DirectoryNavigationWidget;

/**
 * Exposes {@code BaseFileBrowserWidget}'s private navigation bar widget, so the preview-type
 * button can be positioned next to it.
 */
@Mixin(BaseFileBrowserWidget.class)
public interface BaseFileBrowserWidgetAccessor
{
    @Accessor(value = "navigationWidget", remap = false)
    DirectoryNavigationWidget schematicpreview$getNavigationWidget();
}
