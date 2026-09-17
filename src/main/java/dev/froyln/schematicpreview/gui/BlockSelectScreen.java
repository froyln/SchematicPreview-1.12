package dev.froyln.schematicpreview.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

import fi.dy.masa.malilib.gui.BaseListScreen;
import fi.dy.masa.malilib.gui.widget.ItemStackWidget;
import fi.dy.masa.malilib.gui.widget.list.DataListWidget;
import fi.dy.masa.malilib.gui.widget.list.entry.BaseDataListEntryWidget;
import fi.dy.masa.malilib.gui.widget.list.entry.DataListEntryWidgetData;
import fi.dy.masa.malilib.render.text.StyledTextLine;

/**
 * Searchable {@code Block.REGISTRY} picker for the material list Replace button. Clicking a row
 * hands the block to the callback and returns to the parent screen.
 */
public class BlockSelectScreen extends BaseListScreen<DataListWidget<Block>>
{
    private final Consumer<Block> callback;

    public BlockSelectScreen(String titleKey, Object titleArg, Consumer<Block> callback)
    {
        super(10, 30, 20, 60);

        this.callback = callback;
        this.setTitle(titleKey, titleArg);
    }

    @Override
    protected DataListWidget<Block> createListWidget()
    {
        List<Block> blocks = new ArrayList<>();

        for (Block block : Block.REGISTRY)
        {
            blocks.add(block);
        }

        blocks.sort((a, b) -> displayName(a).compareToIgnoreCase(displayName(b)));

        DataListWidget<Block> listWidget = new DataListWidget<>(() -> blocks, false);
        listWidget.setListEntryWidgetFixedHeight(18);
        listWidget.addDefaultSearchBar();
        listWidget.setEntryFilterStringFunction((block) -> Collections.singletonList(displayName(block)));
        listWidget.setDataListEntryWidgetFactory((data, constructData) -> new BlockEntryWidget(data, constructData, this::onPick));

        return listWidget;
    }

    private void onPick(Block block)
    {
        this.callback.accept(block);
        this.openParentScreen();
    }

    private static String displayName(Block block)
    {
        return new ItemStack(block).getDisplayName();
    }

    private static final class BlockEntryWidget extends BaseDataListEntryWidget<Block>
    {
        private final ItemStackWidget iconWidget;
        private final Consumer<Block> onPick;

        BlockEntryWidget(Block data, DataListEntryWidgetData constructData, Consumer<Block> onPick)
        {
            super(data, constructData);

            this.onPick = onPick;
            this.iconWidget = new ItemStackWidget(new ItemStack(data));
            this.setText(StyledTextLine.of(displayName(data)));
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
