package dev.froyln.schematicpreview.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import fi.dy.masa.malilib.gui.widget.list.BaseListWidget;

/**
 * {@code getHoveredListWidget}'s fixed-height fast path assumes one column; forcing this false
 * makes it fall back to a per-widget {@code isMouseOver} scan.
 */
@Mixin(BaseListWidget.class)
public interface BaseListWidgetAccessor
{
    @Accessor(value = "areEntriesFixedHeight", remap = false)
    void schematicpreview$setAreEntriesFixedHeight(boolean value);
}
