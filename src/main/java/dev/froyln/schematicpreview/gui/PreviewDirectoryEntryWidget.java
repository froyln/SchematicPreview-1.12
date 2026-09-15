package dev.froyln.schematicpreview.gui;

import javax.annotation.Nullable;

import fi.dy.masa.malilib.gui.icon.FileBrowserIconProvider;
import fi.dy.masa.malilib.gui.util.ScreenContext;
import fi.dy.masa.malilib.gui.widget.list.BaseFileBrowserWidget;
import fi.dy.masa.malilib.gui.widget.list.BaseFileBrowserWidget.DirectoryEntry;
import fi.dy.masa.malilib.gui.widget.list.BaseFileBrowserWidget.DirectoryEntryType;
import fi.dy.masa.malilib.gui.widget.list.entry.DataListEntryWidgetData;
import fi.dy.masa.malilib.gui.widget.list.entry.DirectoryEntryWidget;
import fi.dy.masa.malilib.render.text.StyledTextLine;
import fi.dy.masa.malilib.render.text.StyledTextUtils;
import fi.dy.masa.malilib.util.data.LeftRight;

import dev.froyln.schematicpreview.config.PreviewType;
import dev.froyln.schematicpreview.render.PreviewCache;

/**
 * A {@link DirectoryEntryWidget} that shows a live 3D preview (via {@link PreviewCache}'s
 * shared small-preview FBO, never one of its own) instead of the static file-type icon, for
 * file entries when the active {@link PreviewType} calls for one. Directories and
 * {@link PreviewType#LIST} are unchanged from vanilla behavior - directory thumbnails are a
 * later task (see AGENTS.md -> Architecture).
 */
public class PreviewDirectoryEntryWidget extends DirectoryEntryWidget
{
    private static final int TILE_TEXT_STRIP_HEIGHT = 12;
    private static final int PREVIEW_PADDING = 2;

    private final PreviewType previewType;
    private final boolean showPreview;
    @Nullable private StyledTextLine clampedTileName;

    public PreviewDirectoryEntryWidget(DirectoryEntry entry, DataListEntryWidgetData constructData,
                                       BaseFileBrowserWidget fileBrowserWidget,
                                       @Nullable FileBrowserIconProvider iconProvider, PreviewType previewType)
    {
        super(entry, constructData, fileBrowserWidget, iconProvider);

        this.previewType = previewType;
        this.showPreview = entry.getType() == DirectoryEntryType.FILE && previewType.hasPreview();

        // Deliberately not clearing the vanilla type icon here: it stays as-is and shows
        // through as the fallback whenever renderSmallPreview declines to draw (still loading,
        // failed to parse, or over previewMaxVolume) - PreviewCache draws over it once ready.
        if (this.showPreview)
        {
            if (previewType.isTile())
            {
                int maxWidth = this.getWidth() - PREVIEW_PADDING * 2;
                this.clampedTileName = StyledTextUtils.clampStyledTextToMaxWidth(this.fullNameText, maxWidth, LeftRight.RIGHT, " ...");
            }
            else
            {
                int previewSize = this.getHeight() - PREVIEW_PADDING * 2;
                this.textOffset.setXOffset(previewSize + PREVIEW_PADDING + 3);
            }
        }
    }

    @Override
    public void renderAt(int x, int y, float z, ScreenContext ctx)
    {
        super.renderAt(x, y, z, ctx);

        if (this.showPreview == false)
        {
            return;
        }

        if (this.previewType.isTile())
        {
            int previewHeight = this.getHeight() - TILE_TEXT_STRIP_HEIGHT;
            PreviewCache.renderSmallPreview(this.data.getFullPath(), x, y, this.getWidth(), previewHeight, z + 0.5f);
        }
        else
        {
            int previewSize = this.getHeight() - PREVIEW_PADDING * 2;
            PreviewCache.renderSmallPreview(this.data.getFullPath(), x + PREVIEW_PADDING, y + PREVIEW_PADDING,
                                            previewSize, previewSize, z + 0.5f);
        }
    }

    @Override
    protected void renderInfoColumns(int x, int y, float z, ScreenContext ctx)
    {
        if (this.showPreview && this.previewType.isTile())
        {
            if (this.clampedTileName != null)
            {
                int textY = y + this.getHeight() - TILE_TEXT_STRIP_HEIGHT + 2;
                int textX = x + Math.max(0, (this.getWidth() - this.clampedTileName.renderWidth) / 2);
                this.renderTextLine(textX, textY, z + 0.5f, this.getTextSettings().getTextColor(), this.clampedTileName, ctx);
            }

            return;
        }

        super.renderInfoColumns(x, y, z, ctx);
    }
}
