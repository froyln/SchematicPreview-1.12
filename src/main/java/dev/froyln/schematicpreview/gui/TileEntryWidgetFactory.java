package dev.froyln.schematicpreview.gui;

import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;

import fi.dy.masa.malilib.gui.widget.list.BaseFileBrowserWidget;
import fi.dy.masa.malilib.gui.widget.list.BaseFileBrowserWidget.DirectoryEntry;
import fi.dy.masa.malilib.gui.widget.list.ListEntryWidgetFactory;
import fi.dy.masa.malilib.gui.widget.list.entry.BaseListEntryWidget;
import fi.dy.masa.malilib.gui.widget.list.entry.DataListEntryWidgetData;
import fi.dy.masa.malilib.gui.widget.list.entry.DataListEntryWidgetFactory;

import dev.froyln.schematicpreview.config.Configs;
import dev.froyln.schematicpreview.config.PreviewType;

/**
 * Grid layout for {@link PreviewType#isTile()} types. Malilib's own {@code ListEntryWidgetFactory}
 * is hard-coded to one column (no multi-column body layout exists anywhere in malilib), so this
 * is a from-scratch replacement rather than an extension of anything.
 *
 * <p>Can't call {@code BaseFileBrowserWidget.createListEntryWidget} (the usual way one entry
 * widget gets built) - it's {@code protected} and this class lives in a different package.
 * Instead it's handed the exact same {@code DataListEntryWidgetFactory} lambda the widget's own
 * default (single-column) path uses, and calls it directly with a manually-built
 * {@link DataListEntryWidgetData}.
 *
 * <p>{@link #getTotalListWidgetCount()} returns the number of <em>rows</em>, not raw entries -
 * {@code BaseListWidget} uses that count for scroll clamping and keyboard paging, so scrolling
 * and page up/down naturally work in row units. The one place this doesn't fully reconcile is
 * {@code BaseListWidget.updateScrollBarHeight()}'s per-widget-height branch, which would index
 * the flat entry-widget list by row number - that only affects the scrollbar thumb's rendered
 * size (cosmetic), not the actual scroll range, so it's left alone.
 */
public class TileEntryWidgetFactory implements ListEntryWidgetFactory
{
    private final BaseFileBrowserWidget listWidget;
    private final DataListEntryWidgetFactory<DirectoryEntry> entryFactory;
    private final PreviewType type;

    public TileEntryWidgetFactory(BaseFileBrowserWidget listWidget, DataListEntryWidgetFactory<DirectoryEntry> entryFactory, PreviewType type)
    {
        this.listWidget = listWidget;
        this.entryFactory = entryFactory;
        this.type = type;
    }

    @Override
    public int getTotalListWidgetCount()
    {
        int columns = this.type.getColumns();
        int entries = this.listWidget.getFilteredDataList().size();

        return (entries + columns - 1) / columns;
    }

    @Override
    public void createEntryWidgets(int startX, int startY, int usableSpace, int startIndex, Consumer<BaseListEntryWidget> widgetConsumer)
    {
        List<DirectoryEntry> entries = this.listWidget.getFilteredDataList();
        int columns = this.type.getColumns();
        int gapX = Configs.Menu.ENTRY_GAP_X.getIntegerValue();
        int gapY = Configs.Menu.ENTRY_GAP_Y.getIntegerValue();
        int cellWidth = (this.listWidget.getEntryWidgetWidth() - (columns - 1) * gapX) / columns;
        int cellHeight = this.type.getHeight(cellWidth);
        int totalRows = this.getTotalListWidgetCount();
        int usedHeight = 0;

        for (int row = startIndex; row < totalRows; ++row)
        {
            if (usedHeight + cellHeight > usableSpace)
            {
                break;
            }

            int y = startY + (row - startIndex) * (cellHeight + gapY);
            boolean addedAny = false;

            for (int col = 0; col < columns; ++col)
            {
                int dataIndex = row * columns + col;

                if (dataIndex >= entries.size())
                {
                    break;
                }

                int x = startX + col * (cellWidth + gapX);
                int originalIndex = this.listWidget.getOriginalListIndexFor(dataIndex);
                DataListEntryWidgetData constructData = new DataListEntryWidgetData(x, y, cellWidth, cellHeight,
                                                                                    dataIndex, originalIndex, this.listWidget);
                @Nullable BaseListEntryWidget widget = this.entryFactory.createWidget(entries.get(dataIndex), constructData);

                if (widget != null)
                {
                    widgetConsumer.accept(widget);
                    addedAny = true;
                }
            }

            if (addedAny == false)
            {
                break;
            }

            usedHeight += cellHeight + gapY;
        }
    }
}
