package dev.froyln.schematicpreview.gui;

import java.lang.reflect.Method;
import javax.annotation.Nullable;

import fi.dy.masa.malilib.gui.BaseScreen;

/**
 * malilib 0.54 (one minor above the 0.53 this addon compiles against) replaced the
 * {@code isFullScreen()} check with a {@code useWindowDimensions} flag that defaults to
 * {@code true} and that its own popup screens ({@code ConfirmActionScreen},
 * {@code BaseTextInputScreen}) never clear - so on open, {@code onScreenResolutionSet} resizes
 * every popup to the whole window while its position stays centered for the small size. The
 * box then hangs off the bottom/right edges and its buttons (placed at
 * {@code y + screenHeight - 26}) land off-screen. Verified in-game: dialog logged as
 * 280x80 after construction, 938x503 once open, in a 938x503 custom-scale window.
 *
 * The setter doesn't exist in 0.53 (no bug there either), so it's looked up reflectively;
 * malilib's own class/method names aren't obfuscated, and the lookup is done once.
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
                // Fall through: the screen opens window-sized, same as without this helper.
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
