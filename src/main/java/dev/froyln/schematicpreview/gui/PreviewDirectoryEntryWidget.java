package dev.froyln.schematicpreview.gui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import fi.dy.masa.malilib.gui.BaseScreen;
import fi.dy.masa.malilib.gui.icon.FileBrowserIconProvider;
import fi.dy.masa.malilib.gui.util.ScreenContext;
import fi.dy.masa.malilib.gui.widget.list.BaseFileBrowserWidget;
import fi.dy.masa.malilib.gui.widget.list.BaseFileBrowserWidget.DirectoryEntry;
import fi.dy.masa.malilib.gui.widget.list.BaseFileBrowserWidget.DirectoryEntryType;
import fi.dy.masa.malilib.gui.widget.list.entry.DataListEntryWidgetData;
import fi.dy.masa.malilib.gui.widget.list.entry.DirectoryEntryWidget;
import fi.dy.masa.malilib.render.ItemRenderUtils;
import fi.dy.masa.malilib.render.text.StyledTextLine;
import fi.dy.masa.malilib.render.text.StyledTextUtils;
import fi.dy.masa.malilib.util.data.LeftRight;

import dev.froyln.schematicpreview.config.PreviewType;
import dev.froyln.schematicpreview.data.DirectoryIconStore;
import dev.froyln.schematicpreview.data.IconPosition;
import dev.froyln.schematicpreview.render.PreviewCache;

/**
 * A {@link DirectoryEntryWidget} that shows a live 3D preview (via {@link PreviewCache}'s
 * shared small-preview FBO, never one of its own) instead of the static file-type icon, for
 * file entries when the active {@link PreviewType} calls for one. Directory entries get the
 * same treatment for a custom icon ({@link DirectoryIconStore}) or, absent one, a preview of
 * their first schematic file - see AGENTS.md -> Architecture.
 */
public class PreviewDirectoryEntryWidget extends DirectoryEntryWidget
{
    private static final int TILE_TEXT_STRIP_HEIGHT = 12;
    private static final int PREVIEW_PADDING = 2;

    private final PreviewType previewType;
    private final DirectoryEntryType entryType;
    @Nullable private final DirectoryIconStore.Entry iconEntry;
    @Nullable private final Path firstSchematicInDir;
    private final boolean showBigVisual;
    @Nullable private StyledTextLine clampedTileName;

    public PreviewDirectoryEntryWidget(DirectoryEntry entry, DataListEntryWidgetData constructData,
                                       BaseFileBrowserWidget fileBrowserWidget,
                                       @Nullable FileBrowserIconProvider iconProvider, PreviewType previewType)
    {
        super(entry, constructData, fileBrowserWidget, iconProvider);

        this.previewType = previewType;
        this.entryType = entry.getType();
        this.iconEntry = this.entryType == DirectoryEntryType.DIRECTORY ? DirectoryIconStore.get(entry.getFullPath()) : null;

        boolean wantsSchematicVisual = previewType.hasPreview() &&
                (this.iconEntry == null || this.iconEntry.position == IconPosition.DEFAULT_WITH_SCHEMATIC);
        this.firstSchematicInDir = (this.entryType == DirectoryEntryType.DIRECTORY && wantsSchematicVisual)
                ? findFirstSchematic(entry.getFullPath(), fileBrowserWidget) : null;

        boolean directoryBigIcon = this.iconEntry != null && this.iconEntry.position == IconPosition.CENTER;
        boolean directorySchematicVisual = this.entryType == DirectoryEntryType.DIRECTORY &&
                wantsSchematicVisual && this.firstSchematicInDir != null;

        this.showBigVisual = (this.entryType == DirectoryEntryType.FILE && previewType.hasPreview()) ||
                              directoryBigIcon || directorySchematicVisual;

        if (this.entryType == DirectoryEntryType.DIRECTORY)
        {
            this.translateAndAddHoverString("schematicpreview.button.change_directory_icon");
        }

        // Deliberately not clearing the vanilla type icon here: it stays as-is and shows
        // through as the fallback whenever nothing above draws over it (still loading, failed
        // to parse, over previewMaxVolume, or a directory with neither a custom icon nor a
        // schematic to preview).
        if (this.showBigVisual)
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

    @Nullable
    private static Path findFirstSchematic(Path directory, BaseFileBrowserWidget fileBrowserWidget)
    {
        try (Stream<Path> stream = Files.list(directory))
        {
            return stream.filter(Files::isRegularFile)
                          .filter(BrowserWidgetAccessors.getFileFilter(fileBrowserWidget))
                          .sorted()
                          .findFirst()
                          .orElse(null);
        }
        catch (IOException ignore)
        {
            return null;
        }
    }

    @Override
    protected boolean onMouseClicked(int mouseX, int mouseY, int mouseButton)
    {
        if (mouseButton == 1 && this.entryType == DirectoryEntryType.DIRECTORY && this.isIconAreaClick(mouseX, mouseY))
        {
            BaseScreen.openScreenWithParent(new DirectoryIconEditScreen(this.data.getFullPath()));
            return true;
        }

        return super.onMouseClicked(mouseX, mouseY, mouseButton);
    }

    private boolean isIconAreaClick(int mouseX, int mouseY)
    {
        if (this.previewType.isTile())
        {
            int previewHeight = this.getHeight() - TILE_TEXT_STRIP_HEIGHT;
            return mouseY - this.getY() < previewHeight;
        }

        return mouseX - this.getX() < this.textOffset.getXOffset();
    }

    @Override
    public void renderAt(int x, int y, float z, ScreenContext ctx)
    {
        super.renderAt(x, y, z, ctx);

        if (this.entryType == DirectoryEntryType.DIRECTORY)
        {
            this.renderDirectoryVisual(x, y, z);
        }
        else if (this.showBigVisual)
        {
            this.renderPreviewBox(this.data.getFullPath(), x, y, z);
        }
    }

    private void renderPreviewBox(Path schematicPath, int x, int y, float z)
    {
        if (this.previewType.isTile())
        {
            int previewHeight = this.getHeight() - TILE_TEXT_STRIP_HEIGHT;
            PreviewCache.renderSmallPreview(schematicPath, x, y, this.getWidth(), previewHeight, z + 0.5f);
        }
        else
        {
            int previewSize = this.getHeight() - PREVIEW_PADDING * 2;
            PreviewCache.renderSmallPreview(schematicPath, x + PREVIEW_PADDING, y + PREVIEW_PADDING,
                                            previewSize, previewSize, z + 0.5f);
        }
    }

    private void renderDirectoryVisual(int x, int y, float z)
    {
        if (this.iconEntry == null)
        {
            if (this.firstSchematicInDir != null)
            {
                this.renderPreviewBox(this.firstSchematicInDir, x, y, z);
            }

            return;
        }

        Item item = Item.getByNameOrId(this.iconEntry.itemId);

        if (item == null)
        {
            return;
        }

        if (this.iconEntry.position == IconPosition.DEFAULT_WITH_SCHEMATIC && this.firstSchematicInDir != null)
        {
            this.renderPreviewBox(this.firstSchematicInDir, x, y, z);
        }

        ItemStack stack = new ItemStack(item);

        if (this.iconEntry.position == IconPosition.CENTER)
        {
            // Same box geometry renderPreviewBox() draws a schematic preview into - keeps the
            // icon inside the area textOffset actually reserved for it above, instead of
            // floating in the middle of a much wider list/list-preview row.
            int boxX = x;
            int boxY = y;
            int boxWidth;
            int boxHeight;

            if (this.previewType.isTile())
            {
                boxWidth = this.getWidth();
                boxHeight = this.getHeight() - TILE_TEXT_STRIP_HEIGHT;
            }
            else
            {
                boxWidth = boxHeight = this.getHeight() - PREVIEW_PADDING * 2;
                boxX = x + PREVIEW_PADDING;
                boxY = y + PREVIEW_PADDING;
            }

            int scale = Math.max(1, Math.min(boxWidth, boxHeight) / 20);
            int drawX = boxX + (boxWidth - 16 * scale) / 2;
            int drawY = boxY + (boxHeight - 16 * scale) / 2;
            ItemRenderUtils.renderStackAt(stack, drawX, drawY, z + 0.6f, scale, this.mc);
        }
        else
        {
            ItemRenderUtils.renderStackAt(stack, x + 2, y + (this.getHeight() - 16) / 2, z + 0.6f, 1f, this.mc);
        }
    }

    @Override
    protected void renderInfoColumns(int x, int y, float z, ScreenContext ctx)
    {
        if (this.showBigVisual && this.previewType.isTile())
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
