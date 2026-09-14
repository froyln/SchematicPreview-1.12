package dev.froyln.schematicpreview.config;

import com.google.common.collect.ImmutableList;
import fi.dy.masa.malilib.gui.config.BaseConfigScreen;
import fi.dy.masa.malilib.gui.config.BaseConfigTab;
import fi.dy.masa.malilib.gui.config.ConfigTab;
import dev.froyln.schematicpreview.Reference;

public class ConfigScreen
{
    public static final BaseConfigTab GENERIC = new BaseConfigTab(Reference.MOD_INFO, "generic", 160, Configs.Generic.OPTIONS, ConfigScreen::create);
    public static final BaseConfigTab MENU = new BaseConfigTab(Reference.MOD_INFO, "menu", 160, Configs.Menu.OPTIONS, ConfigScreen::create);
    public static final BaseConfigTab PREVIEW = new BaseConfigTab(Reference.MOD_INFO, "preview", 160, Configs.Preview.OPTIONS, ConfigScreen::create);
    public static final BaseConfigTab HOTKEYS = new BaseConfigTab(Reference.MOD_INFO, "hotkeys", 200, Configs.HOTKEYS, ConfigScreen::create);

    public static final ImmutableList<ConfigTab> CONFIG_TABS = ImmutableList.of(
            GENERIC,
            MENU,
            PREVIEW,
            HOTKEYS
    );

    public static BaseConfigScreen create()
    {
        return new BaseConfigScreen(Reference.MOD_INFO, CONFIG_TABS, GENERIC,
                "schematicpreview.config", Reference.MOD_VERSION);
    }

    public static ImmutableList<ConfigTab> getConfigTabs()
    {
        return CONFIG_TABS;
    }
}
