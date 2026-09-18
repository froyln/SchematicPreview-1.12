package dev.froyln.schematicpreview;

import java.util.List;
import com.google.common.collect.ImmutableList;
import fi.dy.masa.malilib.config.JsonModConfig;
import fi.dy.masa.malilib.config.category.BaseConfigOptionCategory;
import fi.dy.masa.malilib.config.category.ConfigOptionCategory;
import fi.dy.masa.malilib.event.InitializationHandler;
import fi.dy.masa.malilib.gui.BaseScreen;
import fi.dy.masa.malilib.input.ActionResult;
import fi.dy.masa.malilib.input.Hotkey;
import fi.dy.masa.malilib.input.HotkeyCategory;
import fi.dy.masa.malilib.input.HotkeyProvider;
import fi.dy.masa.malilib.registry.Registry;
import dev.froyln.schematicpreview.config.ConfigScreen;
import dev.froyln.schematicpreview.config.Configs;
import dev.froyln.schematicpreview.data.DirectoryIconStore;
import dev.froyln.schematicpreview.render.PreviewCache;

public class InitHandler implements InitializationHandler
{
    @Override
    public void registerModHandlers()
    {
        DirectoryIconStore.load();

        List<ConfigOptionCategory> categories = ImmutableList.of(
                BaseConfigOptionCategory.normal(Reference.MOD_INFO, "Generic", Configs.Generic.OPTIONS),
                BaseConfigOptionCategory.normal(Reference.MOD_INFO, "Menu", Configs.Menu.OPTIONS),
                BaseConfigOptionCategory.normal(Reference.MOD_INFO, "Preview", Configs.Preview.OPTIONS)
        );
        Registry.CONFIG_MANAGER.registerConfigHandler(
                JsonModConfig.createJsonModConfig(Reference.MOD_INFO, Configs.CURRENT_VERSION, categories, null));

        Registry.CONFIG_SCREEN.registerConfigScreenFactory(Reference.MOD_INFO, ConfigScreen::create);
        Registry.CONFIG_TAB.registerConfigTabProvider(Reference.MOD_INFO, () -> ConfigScreen.CONFIG_TABS);

        Registry.HOTKEY_MANAGER.registerHotkeyProvider(new HotkeyProvider()
        {
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
        });

        Registry.TICK_EVENT_DISPATCHER.registerClientTickHandler(() -> {
            PreviewCache.tickClose();
            DirectoryIconStore.tickSave();
        });

        Configs.Generic.OPEN_CONFIG_SCREEN.setHotkeyCallback((action, key) -> {
            BaseScreen.openScreen(ConfigScreen.create());
            return ActionResult.SUCCESS;
        });
    }
}
