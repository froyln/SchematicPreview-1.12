package dev.froyln.schematicpreview.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fi.dy.masa.litematica.gui.BaseSchematicBrowserScreen;
import fi.dy.masa.litematica.gui.util.SchematicBrowserIconProvider;
import fi.dy.masa.malilib.gui.widget.button.ButtonActionListener;
import fi.dy.masa.malilib.gui.widget.button.GenericButton;
import fi.dy.masa.malilib.gui.widget.list.BaseFileBrowserWidget;
import fi.dy.masa.malilib.gui.widget.list.BaseFileBrowserWidget.DirectoryEntry;
import fi.dy.masa.malilib.gui.widget.list.entry.DataListEntryWidgetFactory;
import fi.dy.masa.malilib.gui.widget.list.header.DirectoryNavigationWidget;

import dev.froyln.schematicpreview.config.Configs;
import dev.froyln.schematicpreview.config.PreviewType;
import dev.froyln.schematicpreview.gui.BrowserWidgetAccessors;
import dev.froyln.schematicpreview.gui.PreviewDirectoryEntryWidget;
import dev.froyln.schematicpreview.gui.SchematicPreviewIcons;
import dev.froyln.schematicpreview.gui.TileEntryWidgetFactory;

/**
 * Installs preview-aware entry widgets/factory and a preview-type cycle button into every
 * schematic browser screen. {@code createListWidget()} is the single point
 * {@code SchematicBrowserScreen}, {@code SchematicManagerScreen}, and
 * {@code BaseSaveSchematicScreen} all funnel through (only the manager screen overrides it, and
 * only to call {@code super.createListWidget()} then set an unrelated flag), so one mixin here
 * covers all three.
 */
@Mixin(BaseSchematicBrowserScreen.class)
public abstract class BaseSchematicBrowserScreenMixin
{
    private static final int BUTTON_SIZE = 14;

    @Shadow(remap = false) protected SchematicBrowserIconProvider cachingIconProvider;

    @Inject(method = "createListWidget", at = @At("RETURN"), remap = false)
    private void schematicpreview$installPreviewEntries(CallbackInfoReturnable<BaseFileBrowserWidget> cir)
    {
        if (Configs.Generic.ENABLED.getBooleanValue() == false)
        {
            return;
        }

        BaseFileBrowserWidget listWidget = cir.getReturnValue();
        BrowserWidgetAccessors.setAreEntriesFixedHeight(listWidget, false);

        this.schematicpreview$applyPreviewType(listWidget);

        DirectoryNavigationWidget nav = BrowserWidgetAccessors.getNavigationWidget(listWidget);
        GenericButton button = GenericButton.create(BUTTON_SIZE, BUTTON_SIZE, SchematicPreviewIcons.PREVIEW_TYPE);
        button.translateAndAddHoverString("schematicpreview.button.preview_type");
        button.setActionListener((mouseButton, widget) -> {
            if (mouseButton != 0 && mouseButton != 1)
            {
                return false;
            }

            Configs.Menu.PREVIEW_TYPE.cycleValue(mouseButton == 1);
            this.schematicpreview$applyPreviewType(listWidget);
            listWidget.refreshFilteredEntries();
            return true;
        });
        button.setPosition(nav.getX() - BUTTON_SIZE - 2, nav.getY());
        listWidget.addWidget(button);
    }

    @Unique
    private void schematicpreview$applyPreviewType(BaseFileBrowserWidget listWidget)
    {
        PreviewType type = Configs.Menu.PREVIEW_TYPE.getValue();
        DataListEntryWidgetFactory<DirectoryEntry> entryFactory = (data, constructData) ->
                new PreviewDirectoryEntryWidget(data, constructData, listWidget, this.cachingIconProvider, type);
        listWidget.setDataListEntryWidgetFactory(entryFactory);

        if (type.isTile())
        {
            int cellWidth = listWidget.getEntryWidgetWidth() / type.getColumns();
            listWidget.setListEntryWidgetFixedHeight(type.getHeight(cellWidth));
            listWidget.setListEntryWidgetFactory(new TileEntryWidgetFactory(listWidget, entryFactory, type));
        }
        else
        {
            // LIST / LIST_PREVIEW heights don't depend on the width.
            listWidget.setListEntryWidgetFixedHeight(type.getHeight(0));
            listWidget.setListEntryWidgetFactory(listWidget);
        }
    }
}
