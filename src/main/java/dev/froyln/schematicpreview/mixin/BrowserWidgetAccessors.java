package dev.froyln.schematicpreview.mixin;

import fi.dy.masa.malilib.gui.widget.list.BaseFileBrowserWidget;
import fi.dy.masa.malilib.gui.widget.list.header.DirectoryNavigationWidget;

/**
 * Plain (non-mixin) call sites for {@link BaseFileBrowserWidgetAccessor} and
 * {@link BaseListWidgetAccessor}. Casting to an accessor-mixin interface directly from inside
 * another mixin's injected method body confuses this LiteLoader-bundled Mixin version's
 * bytecode transformer - it treats the accessor interface itself as a type needing remapping
 * relative to the injected method's own target class hierarchy, throwing a
 * {@code MixinTransformerError}/{@code InvalidMixinException} at class-load time (confirmed by
 * running the built litemod in-game - see AGENTS.md -> Gotchas). Ordinary, non-mixin code
 * calling the same casts at runtime works fine, since the accessor interfaces have already been
 * woven into their targets by then.
 */
final class BrowserWidgetAccessors
{
    private BrowserWidgetAccessors()
    {
    }

    static void setAreEntriesFixedHeight(BaseFileBrowserWidget listWidget, boolean value)
    {
        ((BaseListWidgetAccessor) listWidget).schematicpreview$setAreEntriesFixedHeight(value);
    }

    static DirectoryNavigationWidget getNavigationWidget(BaseFileBrowserWidget listWidget)
    {
        return ((BaseFileBrowserWidgetAccessor) listWidget).schematicpreview$getNavigationWidget();
    }
}
