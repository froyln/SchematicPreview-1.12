package dev.froyln.schematicpreview.config;

import fi.dy.masa.malilib.gui.config.liteloader.RedirectingConfigPanel;

public class SchematicPreviewConfigPanel extends RedirectingConfigPanel
{
    public SchematicPreviewConfigPanel()
    {
        super(ConfigScreen::create);
    }
}
