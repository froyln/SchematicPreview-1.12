package dev.froyln.schematicpreview;

import fi.dy.masa.malilib.event.ClientTickHandler;

import dev.froyln.schematicpreview.render.PreviewCache;

/**
 * Will also drive the directory icon store's dirty-save once that exists (task 4).
 */
public class SchematicPreviewTickHandler implements ClientTickHandler
{
    @Override
    public void onClientTick()
    {
        PreviewCache.tickClose();
    }
}
