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
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;

import fi.dy.masa.malilib.util.FileNameUtils;

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

            // Not FileNameUtils.generateSimpleSafeFileName: that lowercases the name.
            String name = FileNameUtils.getFileNameWithoutExtension(schematicPath.getFileName().toString())
                                       .replaceAll("[^A-Za-z0-9_-]", "_");
            File file = dir.resolve(name + "_" + FileNameUtils.getDateTimeString() + ".png").toFile();

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
     * Under Wayland the game is an XWayland client and the compositor's clipboard bridge
     * truncates AWT's INCR transfer for large images, so hand the PNG to {@code wl-copy}
     * (a native owner) and fall back to AWT only if it isn't installed. See AGENTS.md Gotchas.
     */
    private static boolean copyViaWlCopy(BufferedImage image)
    {
        try
        {
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(image, "png", png);

            // Discard output: an unread pipe would block the child.
            Process process = new ProcessBuilder("wl-copy", "--type", "image/png")
                    .redirectErrorStream(true)
                    .redirectOutput(new File("/dev/null"))
                    .start();

            try (OutputStream stdin = process.getOutputStream())
            {
                png.writeTo(stdin);
            }

            if (process.waitFor(10, TimeUnit.SECONDS) == false)
            {
                process.destroyForcibly();
                return false;
            }

            return process.exitValue() == 0;
        }
        catch (IOException | InterruptedException e)
        {
            return false;
        }
    }

    /**
     * AWT re-encodes the clipboard image on demand and the JDK JPEG writer mangles ARGB input
     * (inverted colors), so the clipboard gets an opaque {@code TYPE_INT_RGB} composite over
     * the preview's background color.
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
