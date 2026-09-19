package dev.froyln.schematicpreview.gui;

import java.nio.file.Path;
import java.util.function.Predicate;

import fi.dy.masa.malilib.gui.widget.list.BaseFileBrowserWidget;
import fi.dy.masa.malilib.gui.widget.list.header.DirectoryNavigationWidget;

import dev.froyln.schematicpreview.mixin.BaseFileBrowserWidgetAccessor;
import dev.froyln.schematicpreview.mixin.BaseListWidgetAccessor;

/**
 * Plain call sites for {@code BaseFileBrowserWidgetAccessor} and {@code BaseListWidgetAccessor}.
 * Must live outside the mixin package and outside any mixin's injected method - both cases
 * fail at runtime, not at compile time. See AGENTS.md Gotchas.
 */
public final class BrowserWidgetAccessors
{
    private BrowserWidgetAccessors()
    {
    }

    public static void setAreEntriesFixedHeight(BaseFileBrowserWidget listWidget, boolean value)
    {
        ((BaseListWidgetAccessor) listWidget).schematicpreview$setAreEntriesFixedHeight(value);
    }

    public static DirectoryNavigationWidget getNavigationWidget(BaseFileBrowserWidget listWidget)
    {
        return ((BaseFileBrowserWidgetAccessor) listWidget).schematicpreview$getNavigationWidget();
    }

    public static Predicate<Path> getFileFilter(BaseFileBrowserWidget listWidget)
    {
        return ((BaseFileBrowserWidgetAccessor) listWidget).schematicpreview$getFileFilter();
    }
}
