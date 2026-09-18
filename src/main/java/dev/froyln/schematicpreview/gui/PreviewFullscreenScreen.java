package dev.froyln.schematicpreview.gui;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.function.Consumer;

import fi.dy.masa.malilib.gui.BaseScreen;
import fi.dy.masa.malilib.gui.widget.button.GenericButton;
import fi.dy.masa.malilib.overlay.message.MessageDispatcher;

/**
 * Fullscreen view of a schematic preview. Asks {@code PreviewCache} for the same path's
 * {@code PreviewRenderer} the side panel used, so entering/leaving fullscreen never
 * re-tessellates.
 */
public class PreviewFullscreenScreen extends BaseScreen
{
    private static final int TOP_MARGIN = 16;
    private static final int BUTTON_SIZE = 14;

    private final Path path;
    private PreviewWidget widget;
    private GenericButton saveButton;
    private GenericButton copyButton;

    public PreviewFullscreenScreen(Path path)
    {
        this.path = path;

        // malilib's default 0xB0000000 is meant as a translucent overlay over the paused game
        // world behind a normal GUI - this screen is a focused view of the preview itself, so
        // it should read as a solid backdrop, not let the live world show through around it.
        this.backgroundColor = 0xFF000000;
    }

    @Override
    protected void reAddActiveWidgets()
    {
        super.reAddActiveWidgets();

        this.widget = this.addWidget(new PreviewWidget(this.getX(), this.getY() + TOP_MARGIN,
                                                        this.getScreenWidth(), this.getScreenHeight() - TOP_MARGIN, this.path));

        this.saveButton = this.addWidget(GenericButton.create(BUTTON_SIZE, BUTTON_SIZE, SchematicPreviewIcons.SAVE));
        this.saveButton.setRenderButtonBackgroundTexture(true);
        this.saveButton.translateAndAddHoverString("schematicpreview.button.save_screenshot");
        this.saveButton.setActionListener((mouseButton, w) -> {
            this.onSave();
            return true;
        });

        this.copyButton = this.addWidget(GenericButton.create(BUTTON_SIZE, BUTTON_SIZE, SchematicPreviewIcons.COPY));
        this.copyButton.setRenderButtonBackgroundTexture(true);
        this.copyButton.translateAndAddHoverString("schematicpreview.button.copy_screenshot");
        this.copyButton.setActionListener((mouseButton, w) -> {
            this.onCopy();
            return true;
        });

        this.positionButtons();
    }

    @Override
    protected void updateWidgetPositions()
    {
        super.updateWidgetPositions();

        if (this.widget != null)
        {
            this.widget.setPositionAndSize(this.getX(), this.getY() + TOP_MARGIN,
                                           this.getScreenWidth(), this.getScreenHeight() - TOP_MARGIN);
        }

        this.positionButtons();
    }

    private void positionButtons()
    {
        if (this.saveButton != null)
        {
            this.saveButton.setPosition(this.getX() + 2, this.getY() + 2);
        }

        if (this.copyButton != null)
        {
            this.copyButton.setPosition(this.getX() + 2 + BUTTON_SIZE + 2, this.getY() + 2);
        }
    }

    /**
     * Routes to {@link PreviewWidget#requestCapture}, which resolves on the widget's next render
     * frame rather than synchronously - see the comment on {@code serviceCaptureRequest} there
     * for why a capture can't just run straight from this button click.
     */
    private void requestImage(Consumer<BufferedImage> onImage)
    {
        if (this.widget == null)
        {
            MessageDispatcher.warning().translate("schematicpreview.message.preview_not_ready");
            return;
        }

        this.widget.requestCapture(image -> {
            if (image == null)
            {
                MessageDispatcher.warning().translate("schematicpreview.message.preview_not_ready");
            }
            else
            {
                onImage.accept(image);
            }
        });
    }

    private void onSave()
    {
        this.requestImage(image -> {
            File file = ScreenshotUtil.save(image, this.path);

            if (file != null)
            {
                MessageDispatcher.success().translate("schematicpreview.message.screenshot_saved", file.getName());
            }
            else
            {
                MessageDispatcher.error().translate("schematicpreview.message.screenshot_failed");
            }
        });
    }

    private void onCopy()
    {
        this.requestImage(image -> {
            if (ScreenshotUtil.copyToClipboard(image))
            {
                MessageDispatcher.success().translate("schematicpreview.message.image_copied");
            }
            else
            {
                MessageDispatcher.error().translate("schematicpreview.message.screenshot_failed");
            }
        });
    }

    @Override
    public void onGuiClosed()
    {
        if (this.widget != null)
        {
            this.widget.close();
        }

        super.onGuiClosed();
    }
}
