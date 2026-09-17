package dev.froyln.schematicpreview.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

import fi.dy.masa.malilib.gui.BaseListScreen;
import fi.dy.masa.malilib.gui.widget.ItemStackWidget;
import fi.dy.masa.malilib.gui.widget.list.DataListWidget;
import fi.dy.masa.malilib.gui.widget.list.entry.BaseDataListEntryWidget;
import fi.dy.masa.malilib.gui.widget.list.entry.DataListEntryWidgetData;
import fi.dy.masa.malilib.render.text.StyledTextLine;

/**
 * Searchable block-variant picker for the material list Replace button: every placeable
 * {@code (Block, metadata)} combination (e.g. each slab type, each wool color) as its own row,
 * not just one row per {@code Block} class. Clicking a row hands the exact variant stack to the
 * callback and returns to the parent screen.
 */
public class BlockSelectScreen extends BaseListScreen<DataListWidget<ItemStack>>
{
    private final Consumer<ItemStack> callback;

    public BlockSelectScreen(String titleKey, Object titleArg, Consumer<ItemStack> callback)
    {
        super(10, 30, 20, 60);

        this.callback = callback;
        this.setTitle(titleKey, titleArg);
    }

    @Override
    protected DataListWidget<ItemStack> createListWidget()
    {
        List<ItemStack> stacks = new ArrayList<>();
        NonNullList<ItemStack> subBlocks = NonNullList.create();

        for (Block block : Block.REGISTRY)
        {
            if (Item.getItemFromBlock(block) == Items.AIR)
            {
                continue;
            }

            subBlocks.clear();
            block.getSubBlocks(CreativeTabs.SEARCH, subBlocks);
            stacks.addAll(subBlocks);
        }

        stacks.sort((a, b) -> a.getDisplayName().compareToIgnoreCase(b.getDisplayName()));

        DataListWidget<ItemStack> listWidget = new DataListWidget<>(() -> stacks, false);
        listWidget.setListEntryWidgetFixedHeight(18);
        listWidget.addDefaultSearchBar();
        listWidget.setEntryFilterStringFunction((stack) -> Collections.singletonList(stack.getDisplayName()));
        listWidget.setDataListEntryWidgetFactory((data, constructData) -> new StackEntryWidget(data, constructData, this::onPick));

        return listWidget;
    }

    private void onPick(ItemStack stack)
    {
        this.callback.accept(stack);
        this.openParentScreen();
    }

    private static final class StackEntryWidget extends BaseDataListEntryWidget<ItemStack>
    {
        private final ItemStackWidget iconWidget;
        private final Consumer<ItemStack> onPick;

        StackEntryWidget(ItemStack data, DataListEntryWidgetData constructData, Consumer<ItemStack> onPick)
        {
            super(data, constructData);

            this.onPick = onPick;
            this.iconWidget = new ItemStackWidget(data);
            this.setText(StyledTextLine.of(data.getDisplayName()));
            this.getTextOffset().setXOffset(22);
        }

        @Override
        public void reAddSubWidgets()
        {
            super.reAddSubWidgets();

            this.addWidget(this.iconWidget);
        }

        @Override
        public void updateSubWidgetPositions()
        {
            super.updateSubWidgetPositions();

            this.iconWidget.setX(this.getX() + 2);
            this.iconWidget.centerVerticallyInside(this);
        }

        @Override
        protected boolean onMouseClicked(int mouseX, int mouseY, int mouseButton)
        {
            if (mouseButton == 0)
            {
                this.onPick.accept(this.data);
                return true;
            }

            return super.onMouseClicked(mouseX, mouseY, mouseButton);
        }
    }
}
