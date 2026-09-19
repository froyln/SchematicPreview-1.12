package dev.froyln.schematicpreview.mixin;

import java.nio.file.Path;
import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fi.dy.masa.litematica.gui.util.SchematicInfoCache;
import fi.dy.masa.litematica.gui.widget.SchematicInfoWidget;
import fi.dy.masa.malilib.gui.widget.ContainerWidget;
import fi.dy.masa.malilib.gui.widget.InteractableWidget;

import dev.froyln.schematicpreview.config.Configs;
import dev.froyln.schematicpreview.gui.PreviewWidget;
import dev.froyln.schematicpreview.render.PreviewCache;

/**
 * Replaces the vanilla 2D thumbnail in the schematic info panel with a live 3D
 * {@link PreviewWidget}. {@code extends ContainerWidget} is the usual trick to reach
 * inherited protected members without shadows.
 */
@Mixin(SchematicInfoWidget.class)
public abstract class SchematicInfoWidgetMixin extends ContainerWidget
{
    @Shadow(remap = false) @Nullable protected SchematicInfoCache.SchematicInfo currentInfo;

    @Unique private PreviewWidget schematicpreview_previewWidget;

    private SchematicInfoWidgetMixin(int width, int height)
    {
        super(width, height);
    }

    @Inject(method = "reCreateSubWidgets", at = @At("TAIL"), remap = false)
    private void schematicpreview$addPreview(CallbackInfo ci)
    {
        if (this.schematicpreview_previewWidget != null)
        {
            this.schematicpreview_previewWidget.close();
            this.schematicpreview_previewWidget = null;
        }

        if (Configs.Generic.ENABLED.getBooleanValue() == false || this.currentInfo == null)
        {
            return;
        }

        Path file = this.currentInfo.schematic.getFile();

        if (file == null || this.subWidgets.isEmpty())
        {
            return;
        }

        InteractableWidget last = this.subWidgets.get(this.subWidgets.size() - 1);
        int x = this.getX() + 4;
        int y = last.getBottom() + 4;
        int width = this.getWidth() - 8;
        int height = this.getBottom() - y - 4;

        if (width <= 0 || height <= 0)
        {
            return;
        }

        this.schematicpreview_previewWidget = new PreviewWidget(x, y, width, height, file);
        this.addWidget(this.schematicpreview_previewWidget);
    }

    @Inject(method = "clearCache", at = @At("TAIL"), remap = false)
    private void schematicpreview$clearCache(CallbackInfo ci)
    {
        if (this.schematicpreview_previewWidget != null)
        {
            this.schematicpreview_previewWidget.close();
            this.schematicpreview_previewWidget = null;
        }

        PreviewCache.close();
    }
}
