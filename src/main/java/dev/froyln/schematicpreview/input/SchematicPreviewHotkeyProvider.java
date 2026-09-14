package dev.froyln.schematicpreview.input;

import java.util.List;
import com.google.common.collect.ImmutableList;
import fi.dy.masa.malilib.input.Hotkey;
import fi.dy.masa.malilib.input.HotkeyCategory;
import fi.dy.masa.malilib.input.HotkeyProvider;
import dev.froyln.schematicpreview.Reference;
import dev.froyln.schematicpreview.config.Configs;

public class SchematicPreviewHotkeyProvider implements HotkeyProvider
{
    public static final SchematicPreviewHotkeyProvider INSTANCE = new SchematicPreviewHotkeyProvider();

    @Override
    public List<? extends Hotkey> getAllHotkeys()
    {
        return Configs.HOTKEYS;
    }

    @Override
    public List<HotkeyCategory> getHotkeysByCategories()
    {
        return ImmutableList.of(new HotkeyCategory(Reference.MOD_INFO, "schematicpreview.hotkeys.category.generic", Configs.HOTKEYS));
    }
}
