package dev.froyln.schematicpreview;

import fi.dy.masa.malilib.event.ClientTickHandler;

/**
 * Empty for now - will drive {@code PreviewCache.tickClose()} and the directory icon
 * store's dirty-save once those exist (task 2/4).
 */
public class SchematicPreviewTickHandler implements ClientTickHandler
{
    @Override
    public void onClientTick()
    {
    }
}
