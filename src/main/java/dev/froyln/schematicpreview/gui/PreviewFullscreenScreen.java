package dev.froyln.schematicpreview.gui;

import java.nio.file.Path;

import fi.dy.masa.malilib.gui.BaseScreen;

/**
 * Fullscreen view of a schematic preview. Asks {@code PreviewCache} for the same path's
 * {@code PreviewRenderer} the side panel used, so entering/leaving fullscreen never
 * re-tessellates.
 */
public class PreviewFullscreenScreen extends BaseScreen
{
    private static final int TOP_MARGIN = 16;

    private final Path path;
    private PreviewWidget widget;

    public PreviewFullscreenScreen(Path path)
    {
        this.path = path;
    }

    @Override
    protected void reAddActiveWidgets()
    {
        super.reAddActiveWidgets();

        this.widget = this.addWidget(new PreviewWidget(this.getX(), this.getY() + TOP_MARGIN,
                                                        this.getScreenWidth(), this.getScreenHeight() - TOP_MARGIN, this.path));
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
