package dev.froyln.schematicpreview.gui;

import java.lang.reflect.Method;
import javax.annotation.Nullable;

import fi.dy.masa.malilib.gui.BaseScreen;

/**
 * malilib 0.54 resizes popups to the whole window unless {@code useWindowDimensions} is
 * cleared; 0.53 (what we compile against) has no such setter, so it's looked up reflectively.
 * See AGENTS.md Gotchas.
 */
public final class PopupScreenCompat
{
    @Nullable private static final Method SET_USE_WINDOW_DIMENSIONS = findSetter();

    private PopupScreenCompat()
    {
    }

    /**
     * Keeps {@code screen} at the size its constructor chose instead of the window size.
     * Call before opening it. Returns the screen for chaining.
     */
    public static <T extends BaseScreen> T keepPopupSize(T screen)
    {
        if (SET_USE_WINDOW_DIMENSIONS != null)
        {
            try
            {
                SET_USE_WINDOW_DIMENSIONS.invoke(screen, false);
            }
            catch (ReflectiveOperationException | RuntimeException e)
            {
            }
        }

        return screen;
    }

    @Nullable
    private static Method findSetter()
    {
        try
        {
            return BaseScreen.class.getMethod("setUseWindowDimensions", boolean.class);
        }
        catch (NoSuchMethodException e)
        {
            return null;
        }
    }
}
