package dev.froyln.schematicpreview.gui;

import fi.dy.masa.malilib.gui.widget.list.BaseFileBrowserWidget;
import fi.dy.masa.malilib.gui.widget.list.header.DirectoryNavigationWidget;

import dev.froyln.schematicpreview.mixin.BaseFileBrowserWidgetAccessor;
import dev.froyln.schematicpreview.mixin.BaseListWidgetAccessor;

/**
 * Plain call sites for {@code BaseFileBrowserWidgetAccessor} and {@code BaseListWidgetAccessor}.
 * Deliberately lives outside {@code dev.froyln.schematicpreview.mixin}: that whole package is
 * declared as the mixin config's root package, and Mixin excludes every class under it from
 * normal classloading - not just the ones actually annotated {@code @Mixin} - so referencing
 * either accessor interface from a class in that same package throws
 * {@code NoClassDefFoundError: ... is a mixin class and cannot be referenced directly} at
 * runtime (confirmed in-game via {@code tools/test-in-game.sh}; neither `./gradlew build` nor
 * `compileJava` catch this). Casting to them also can't happen *inside* another mixin's own
 * injected method body either (a separate, earlier-caught issue - see AGENTS.md -> Gotchas):
 * this class is the one safe place for both casts, outside the mixin package and not itself a
 * mixin.
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
}
