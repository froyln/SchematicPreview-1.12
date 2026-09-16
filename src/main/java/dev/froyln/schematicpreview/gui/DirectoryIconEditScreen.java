package dev.froyln.schematicpreview.gui;

import java.nio.file.Path;
import javax.annotation.Nullable;

import net.minecraft.item.Item;

import fi.dy.masa.malilib.gui.BaseTextInputScreen;
import fi.dy.masa.malilib.gui.widget.button.GenericButton;
import fi.dy.masa.malilib.gui.widget.util.TextFieldValidator;
import fi.dy.masa.malilib.util.StringUtils;

import dev.froyln.schematicpreview.data.DirectoryIconStore;
import dev.froyln.schematicpreview.data.IconPosition;

/**
 * Right-click-on-directory screen (see {@link PreviewDirectoryEntryWidget}): an item id and a
 * position cycle, persisted through {@link DirectoryIconStore}. Leaving the field empty and
 * pressing OK removes any existing custom icon for the directory.
 */
public class DirectoryIconEditScreen extends BaseTextInputScreen
{
    private final Path directory;
    private final GenericButton positionButton;
    private IconPosition position;

    public DirectoryIconEditScreen(Path directory)
    {
        super("schematicpreview.title.directory_icon", existingItemIdOrEmpty(directory));

        this.directory = directory;
        this.baseHeight += 26;

        DirectoryIconStore.Entry existing = DirectoryIconStore.get(directory);
        this.position = existing != null ? existing.position : IconPosition.DEFAULT;

        this.textField.setTextValidator(new ItemIdValidator());
        this.setLabelText("schematicpreview.gui.change_directory_icon");

        this.positionButton = GenericButton.create(20, this::getPositionButtonLabel, this::cyclePosition);
        this.positionButton.setWidth(Math.max(100, this.positionButton.getWidth()));
        this.positionButton.setAutomaticWidth(false);
    }

    @Override
    protected void reAddActiveWidgets()
    {
        super.reAddActiveWidgets();

        this.addWidget(this.positionButton);
    }

    @Override
    protected void updateWidgetPositions()
    {
        super.updateWidgetPositions();

        this.positionButton.setPosition(this.textField.getX(), this.okButton.getY() + 26);
    }

    @Override
    protected boolean applyValue()
    {
        String itemId = this.textField.getText().trim();

        if (itemId.isEmpty())
        {
            DirectoryIconStore.remove(this.directory);
            return true;
        }

        return DirectoryIconStore.set(this.directory, itemId, this.position);
    }

    private void cyclePosition()
    {
        this.position = this.position.next();
    }

    private String getPositionButtonLabel()
    {
        return StringUtils.translate("schematicpreview.gui.change_directory_icon.pos",
                                      StringUtils.translate("schematicpreview.position." + this.position.jsonName));
    }

    @Nullable
    private static String existingItemIdOrEmpty(Path directory)
    {
        DirectoryIconStore.Entry entry = DirectoryIconStore.get(directory);
        return entry != null ? entry.itemId : "";
    }

    private static final class ItemIdValidator implements TextFieldValidator
    {
        @Override
        public boolean isValidInput(String text)
        {
            return text.trim().isEmpty() || Item.getByNameOrId(text.trim()) != null;
        }

        @Override
        public String getErrorMessage(String text)
        {
            return StringUtils.translate("schematicpreview.message.invalid_item_id", text);
        }
    }
}
