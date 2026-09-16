package dev.froyln.schematicpreview;

import fi.dy.masa.malilib.event.ClientTickHandler;

import dev.froyln.schematicpreview.data.DirectoryIconStore;
import dev.froyln.schematicpreview.render.PreviewCache;

public class SchematicPreviewTickHandler implements ClientTickHandler
{
    @Override
    public void onClientTick()
    {
        PreviewCache.tickClose();
        DirectoryIconStore.tickSave();
    }
}
