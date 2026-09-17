package dev.froyln.schematicpreview.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fi.dy.masa.litematica.gui.MaterialListScreen;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.litematica.materials.MaterialListPlacement;
import fi.dy.masa.litematica.materials.MaterialListSchematic;
import fi.dy.masa.malilib.gui.widget.list.DataListWidget;

import dev.froyln.schematicpreview.gui.ReplaceMaterialListEntryWidget;

/**
 * Adds the Replace button to material lists backed by a schematic or a placement (not the area
 * analyzer, which has no schematic to mutate).
 */
@Mixin(MaterialListScreen.class)
public abstract class MaterialListScreenMixin
{
    @Shadow(remap = false) protected MaterialListBase materialList;

    @Inject(method = "createListWidget", at = @At("RETURN"), remap = false)
    private void schematicpreview$installReplaceButton(CallbackInfoReturnable<DataListWidget<MaterialListEntry>> cir)
    {
        MaterialListBase materialList = this.materialList;

        if (materialList instanceof MaterialListSchematic || materialList instanceof MaterialListPlacement)
        {
            cir.getReturnValue().setDataListEntryWidgetFactory(
                    (data, constructData) -> new ReplaceMaterialListEntryWidget(data, constructData, materialList));
        }
    }
}
