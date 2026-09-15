package dev.froyln.schematicpreview.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import fi.dy.masa.malilib.gui.widget.list.BaseListWidget;

/**
 * {@code BaseListWidget.getHoveredListWidget}'s fixed-height fast path maps
 * {@code relativeY / entryWidgetFixedHeight} directly into the flat entry-widget list, which is
 * only correct for a single-column layout. Forcing this false makes it fall back to a linear
 * {@code isMouseOver} scan instead - correct regardless of column count, and not meaningfully
 * slower at the handful of visible rows a browser screen ever renders. Set unconditionally,
 * once, rather than toggled per preview type.
 */
@Mixin(BaseListWidget.class)
public interface BaseListWidgetAccessor
{
    @Accessor(value = "areEntriesFixedHeight", remap = false)
    void schematicpreview$setAreEntriesFixedHeight(boolean value);
}
