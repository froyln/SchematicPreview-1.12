package dev.froyln.schematicpreview.gui;

import java.util.Collection;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

import fi.dy.masa.litematica.gui.widget.list.entry.MaterialListEntryWidget;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.litematica.materials.MaterialListPlacement;
import fi.dy.masa.litematica.materials.MaterialListSchematic;
import fi.dy.masa.litematica.schematic.ISchematic;
import fi.dy.masa.malilib.gui.BaseScreen;
import fi.dy.masa.malilib.gui.widget.button.GenericButton;
import fi.dy.masa.malilib.gui.widget.list.entry.DataListEntryWidgetData;
import fi.dy.masa.malilib.overlay.message.MessageDispatcher;

import dev.froyln.schematicpreview.materials.BlockReplacer;
import dev.froyln.schematicpreview.materials.MaterialListAccessors;

/**
 * {@code MaterialListEntryWidget} plus a Replace button that swaps every block of this row's
 * type, in the schematic backing the material list, for a block picked from
 * {@link BlockSelectScreen}.
 */
public class ReplaceMaterialListEntryWidget extends MaterialListEntryWidget
{
    private final GenericButton replaceButton;

    public ReplaceMaterialListEntryWidget(MaterialListEntry data, DataListEntryWidgetData constructData, MaterialListBase materialList)
    {
        super(data, constructData, materialList);

        this.replaceButton = GenericButton.create(18, "schematicpreview.gui.replace_block", this::openBlockSelect);
    }

    @Override
    public void reAddSubWidgets()
    {
        super.reAddSubWidgets();

        this.addWidget(this.replaceButton);
    }

    @Override
    public void updateSubWidgetPositions()
    {
        super.updateSubWidgetPositions();

        this.replaceButton.setRight(this.ignoreButton.getX() - 2);
        this.replaceButton.centerVerticallyInside(this);
    }

    private void openBlockSelect()
    {
        ItemStack oldStack = this.data.getStack();

        BaseScreen.openScreenWithParent(new BlockSelectScreen("schematicpreview.gui.replace_block.title",
                oldStack.getDisplayName(), (newStack) -> this.replaceWith(oldStack, newStack)));
    }

    private void replaceWith(ItemStack oldStack, ItemStack newStack)
    {
        ISchematic schematic;
        Collection<String> regionNames;

        if (this.materialList instanceof MaterialListSchematic)
        {
            MaterialListSchematic list = (MaterialListSchematic) this.materialList;
            schematic = MaterialListAccessors.getSchematic(list);
            regionNames = MaterialListAccessors.getRegions(list);
        }
        else
        {
            schematic = MaterialListAccessors.getSchematic((MaterialListPlacement) this.materialList);
            regionNames = schematic.getRegionNames();
        }

        Block newBlock = Block.getBlockFromItem(newStack.getItem());
        long count = BlockReplacer.replace(oldStack, newBlock, newStack.getMetadata(), schematic, regionNames);

        this.materialList.reCreateMaterialList();
        this.listWidget.refreshEntries();

        MessageDispatcher.success().translate("schematicpreview.gui.replace_block.result", count, newStack.getDisplayName());
    }
}
