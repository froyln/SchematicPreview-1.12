package dev.froyln.schematicpreview.config;

import com.google.common.collect.ImmutableList;
import fi.dy.masa.malilib.config.option.BooleanConfig;
import fi.dy.masa.malilib.config.option.ConfigOption;
import fi.dy.masa.malilib.config.option.DoubleConfig;
import fi.dy.masa.malilib.config.option.HotkeyConfig;
import fi.dy.masa.malilib.config.option.IntegerConfig;
import fi.dy.masa.malilib.config.option.OptionListConfig;

public class Configs
{
    public static final int CURRENT_VERSION = 1;

    public static class Generic
    {
        public static final BooleanConfig ENABLED = new BooleanConfig("enabled", true, "schematicpreview.config.comment.enabled");
        public static final HotkeyConfig OPEN_CONFIG_SCREEN = new HotkeyConfig("openConfigScreen", "RIGHT_SHIFT,F8");

        public static final ImmutableList<ConfigOption<?>> OPTIONS = ImmutableList.of(
                ENABLED,
                OPEN_CONFIG_SCREEN
        );

        public static final ImmutableList<HotkeyConfig> HOTKEYS = ImmutableList.of(
                OPEN_CONFIG_SCREEN
        );
    }

    public static class Menu
    {
        public static final OptionListConfig<PreviewType> PREVIEW_TYPE = new OptionListConfig<>(
                "previewType", PreviewType.LIST, ImmutableList.copyOf(PreviewType.values()), "schematicpreview.config.comment.previewType");
        public static final IntegerConfig ENTRY_GAP_X = new IntegerConfig("entryGapX", 2, 0, 10, "schematicpreview.config.comment.entryGapX");
        public static final IntegerConfig ENTRY_GAP_Y = new IntegerConfig("entryGapY", 2, 0, 10, "schematicpreview.config.comment.entryGapY");
        public static final IntegerConfig LIST_ENTRY_HEIGHT = new IntegerConfig("listEntryHeight", 15, 10, 80, "schematicpreview.config.comment.listEntryHeight");
        public static final IntegerConfig LIST_PREVIEW_ENTRY_HEIGHT = new IntegerConfig("listPreviewEntryHeight", 35, 15, 80, "schematicpreview.config.comment.listPreviewEntryHeight");
        public static final DoubleConfig TILE_HEIGHT_RATIO = new DoubleConfig("tileHeightRatio", 1.0, 0.3, 5.0, "schematicpreview.config.comment.tileHeightRatio");

        public static final ImmutableList<ConfigOption<?>> OPTIONS = ImmutableList.of(
                PREVIEW_TYPE,
                ENTRY_GAP_X,
                ENTRY_GAP_Y,
                LIST_ENTRY_HEIGHT,
                LIST_PREVIEW_ENTRY_HEIGHT,
                TILE_HEIGHT_RATIO
        );
    }

    public static class Preview
    {
        public static final IntegerConfig PREVIEW_MAX_VOLUME = new IntegerConfig("previewMaxVolume", 125000, 0, Integer.MAX_VALUE, "schematicpreview.config.comment.previewMaxVolume");
        public static final BooleanConfig RENDER_TILE_ENTITIES = new BooleanConfig("renderTileEntities", true, "schematicpreview.config.comment.renderTileEntities");
        public static final DoubleConfig PREVIEW_FOV = new DoubleConfig("previewFov", 50.0, 30.0, 100.0, "schematicpreview.config.comment.previewFov");
        public static final DoubleConfig PREVIEW_ROTATION_Y = new DoubleConfig("previewRotationY", -45.0, -180.0, 180.0, "schematicpreview.config.comment.previewRotationY");
        public static final DoubleConfig PREVIEW_ROTATION_X = new DoubleConfig("previewRotationX", 30.0, -90.0, 90.0, "schematicpreview.config.comment.previewRotationX");

        public static final ImmutableList<ConfigOption<?>> OPTIONS = ImmutableList.of(
                PREVIEW_MAX_VOLUME,
                RENDER_TILE_ENTITIES,
                PREVIEW_FOV,
                PREVIEW_ROTATION_Y,
                PREVIEW_ROTATION_X
        );
    }

    public static final ImmutableList<HotkeyConfig> HOTKEYS = ImmutableList.of(
            Generic.OPEN_CONFIG_SCREEN
    );
}
