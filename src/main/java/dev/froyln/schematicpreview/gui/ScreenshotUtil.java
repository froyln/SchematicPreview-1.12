package dev.froyln.schematicpreview.gui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;

/**
 * File-save and clipboard export for a captured preview image ({@link PreviewWidget#captureImage()}).
 * Both are one-shot, stateless operations - no caller keeps a reference to anything here.
 */
public final class ScreenshotUtil
{
    private ScreenshotUtil()
    {
    }

    @Nullable
    public static File save(BufferedImage image, Path schematicPath)
    {
        try
        {
            Path dir = Minecraft.getMinecraft().gameDir.toPath().resolve("screenshots").resolve("schematicpreview");
            Files.createDirectories(dir);

            String name = schematicPath.getFileName().toString();
            int dot = name.lastIndexOf('.');

            if (dot > 0)
            {
                name = name.substring(0, dot);
            }

            name = name.replaceAll("[^A-Za-z0-9_-]", "_");
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            File file = dir.resolve(name + "_" + timestamp + ".png").toFile();

            ImageIO.write(image, "png", file);
            return file;
        }
        catch (IOException e)
        {
            return null;
        }
    }

    public static boolean copyToClipboard(BufferedImage image)
    {
        // wl-copy serves exactly the PNG bytes it is given, so the transparent capture goes
        // through as-is; only the AWT fallback needs the opaque version (see toOpaque).
        if (System.getenv("WAYLAND_DISPLAY") != null && copyViaWlCopy(image))
        {
            return true;
        }

        try
        {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new TransferableImage(toOpaque(image)), null);
            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    /**
     * On a Wayland session the game (Java 8 AWT, LWJGL 2) is an X11 client under XWayland, and
     * the compositor's X11-to-Wayland clipboard bridge drops AWT's chunked (INCR) selection
     * transfer partway for anything bigger than a few hundred KB: native Wayland apps (Discord,
     * any Chromium/Electron app, browsers) paste a truncated PNG - blank or only the top strip
     * decodes - while X11 readers of the very same clipboard get it whole. Measured on Hyprland:
     * three Wayland-side reads of one 6.25 MB AWT-owned PNG returned 4.8 / 2.9 / 5.5 MB, all
     * corrupt; owned by {@code wl-copy} instead, 3/3 intact. So when a Wayland display is
     * present, hand the finished PNG bytes to {@code wl-copy} (wl-clipboard, standard on every
     * Wayland desktop) - a native Wayland owner - and only fall back to AWT if it isn't there.
     * {@code wl-copy} reads stdin to EOF, forks a daemon to serve the clipboard, and exits.
     */
    private static boolean copyViaWlCopy(BufferedImage image)
    {
        try
        {
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(image, "png", png);

            Process process = new ProcessBuilder("wl-copy", "--type", "image/png")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.PIPE)
                    .start();

            try (OutputStream stdin = process.getOutputStream())
            {
                png.writeTo(stdin);
            }

            return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
        }
        catch (IOException | InterruptedException e)
        {
            return false;
        }
    }

    /**
     * The clipboard must get an opaque {@code TYPE_INT_RGB} image, never the ARGB capture: AWT
     * re-encodes the image into every format it advertises (PNG, JPEG, GIF...) on demand, and the
     * JDK's JPEG encoder mangles 4-channel ARGB input - any app that pastes the JPEG flavor (most
     * do; only PNG-aware ones take the PNG) gets inverted pink/cyan colors on black. Verified on
     * the game's own JRE: ARGB (220,200,150) reads back as (200,100,142), RGB reads back exact.
     * Composited over the same dark grey the on-screen preview clears to, so a paste looks like
     * what was on screen. The saved PNG file keeps the transparent capture.
     */
    private static BufferedImage toOpaque(BufferedImage image)
    {
        BufferedImage opaque = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = opaque.createGraphics();
        g.setColor(CLIPBOARD_BACKGROUND);
        g.fillRect(0, 0, opaque.getWidth(), opaque.getHeight());
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return opaque;
    }

    private static final Color CLIPBOARD_BACKGROUND = new Color(13, 13, 13);

    private static final class TransferableImage implements Transferable
    {
        private final BufferedImage image;

        private TransferableImage(BufferedImage image)
        {
            this.image = image;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors()
        {
            return new DataFlavor[] { DataFlavor.imageFlavor };
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor)
        {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException
        {
            if (DataFlavor.imageFlavor.equals(flavor) == false)
            {
                throw new UnsupportedFlavorException(flavor);
            }

            return this.image;
        }
    }
}
