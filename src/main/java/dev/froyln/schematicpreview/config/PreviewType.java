package dev.froyln.schematicpreview.config;

import java.util.Locale;
import java.util.function.IntUnaryOperator;
import fi.dy.masa.malilib.config.value.OptionListConfigValue;

public enum PreviewType implements OptionListConfigValue
{
    LIST(1, w -> Configs.Menu.LIST_ENTRY_HEIGHT.getIntegerValue()),
    LIST_PREVIEW(1, w -> Configs.Menu.LIST_PREVIEW_ENTRY_HEIGHT.getIntegerValue()),
    TILE_5(5, PreviewType::tileWidthToHeight),
    TILE_4(4, PreviewType::tileWidthToHeight),
    TILE_3(3, PreviewType::tileWidthToHeight);

    private final int columns;
    private final IntUnaryOperator widthToHeight;

    PreviewType(int columns, IntUnaryOperator widthToHeight)
    {
        this.columns = columns;
        this.widthToHeight = widthToHeight;
    }

    public int getColumns()
    {
        return this.columns;
    }

    public int getHeight(int width)
    {
        return this.widthToHeight.applyAsInt(width);
    }

    public boolean isList()
    {
        return this == LIST || this == LIST_PREVIEW;
    }

    public boolean isTile()
    {
        return this == TILE_5 || this == TILE_4 || this == TILE_3;
    }

    public boolean hasPreview()
    {
        return this == LIST_PREVIEW || this.isTile();
    }

    @Override
    public String getName()
    {
        return this.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String getDisplayName()
    {
        return this.name();
    }

    private static int tileWidthToHeight(int width)
    {
        return (int) (width * Configs.Menu.TILE_HEIGHT_RATIO.getDoubleValue());
    }
}
