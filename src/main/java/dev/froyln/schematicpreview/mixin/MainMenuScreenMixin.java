package dev.froyln.schematicpreview.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.OpenGlHelper;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.gui.MainMenuScreen;
import fi.dy.masa.malilib.gui.BaseScreen;
import fi.dy.masa.malilib.gui.icon.DefaultIcons;
import fi.dy.masa.malilib.gui.widget.button.GenericButton;

import dev.froyln.schematicpreview.config.Configs;

/**
 * Adds an "Open schematics folder" button to Litematica's main menu, in the empty slot under
 * "Configuration menu". Opening goes through vanilla {@code OpenGlHelper.openFile} (the same
 * call the resource pack screen's "Open folder" button uses), so there is no platform dispatch
 * of our own; the only thing that reaches it is the configured schematics base directory.
 * {@code extends BaseScreen} is the usual trick to reach the inherited {@code addWidget}.
 */
@Mixin(MainMenuScreen.class)
public abstract class MainMenuScreenMixin extends BaseScreen
{
    @Shadow(remap = false) @Final protected GenericButton configScreenButton;

    @Unique private GenericButton schematicpreview_openFolderButton;

    @Inject(method = "reAddActiveWidgets", at = @At("TAIL"), remap = false)
    private void schematicpreview$addOpenFolderButton(CallbackInfo ci)
    {
        if (Configs.Generic.ENABLED.getBooleanValue() == false)
        {
            return;
        }

        if (this.schematicpreview_openFolderButton == null)
        {
            this.schematicpreview_openFolderButton = GenericButton.create("schematicpreview.button.open_schematics_folder",
                    DefaultIcons.FILE_BROWSER_DIR);
            this.schematicpreview_openFolderButton.setActionListener(
                    () -> OpenGlHelper.openFile(DataManager.getSchematicsBaseDirectory().toFile()));
            this.schematicpreview_openFolderButton.setAutomaticWidth(false);
        }

        // The equal-width pass over Litematica's own buttons has already run at TAIL
        this.schematicpreview_openFolderButton.setWidth(this.configScreenButton.getWidth());
        this.addWidget(this.schematicpreview_openFolderButton);
    }

    @Inject(method = "updateWidgetPositions", at = @At("TAIL"), remap = false)
    private void schematicpreview$positionOpenFolderButton(CallbackInfo ci)
    {
        if (this.schematicpreview_openFolderButton != null)
        {
            this.schematicpreview_openFolderButton.setPosition(this.configScreenButton.getX(),
                    this.configScreenButton.getY() + 22);
        }
    }
}
