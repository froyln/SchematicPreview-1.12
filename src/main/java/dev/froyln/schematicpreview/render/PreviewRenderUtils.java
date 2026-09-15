package dev.froyln.schematicpreview.render;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;

import fi.dy.masa.malilib.gui.util.ScreenContext;
import fi.dy.masa.malilib.render.ShapeRenderUtils;
import fi.dy.masa.malilib.render.text.StyledTextLine;
import fi.dy.masa.malilib.render.text.TextRenderer;
import fi.dy.masa.malilib.util.StringUtils;

/**
 * Small rendering helpers shared between {@code PreviewWidget} (its own FBO) and
 * {@code PreviewCache}'s shared small-preview FBO, so the two don't duplicate the
 * FBO-blit/placeholder drawing code.
 */
public final class PreviewRenderUtils
{
    private PreviewRenderUtils()
    {
    }

    /**
     * Blits {@code fbo}'s color texture at {@code (x, y, width, height)}. {@code width}/
     * {@code height} are also the pixel dimensions the scene was just rendered at inside the
     * FBO (its own viewport) - which may be smaller than the FBO's actual texture size for a
     * shared, grow-only FBO (see {@code PreviewCache}), so the UV range is derived from that
     * ratio rather than assumed to be the full 0..1 texture.
     */
    public static void blitFramebuffer(Framebuffer fbo, int x, int y, int width, int height, float z)
    {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                                            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableTexture2D();
        GlStateManager.bindTexture(fbo.framebufferTexture);

        double maxU = width / (double) fbo.framebufferWidth;
        double maxV = height / (double) fbo.framebufferHeight;

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(x, y + height, z).tex(0.0, 0.0).endVertex();
        buffer.pos(x + width, y + height, z).tex(maxU, 0.0).endVertex();
        buffer.pos(x + width, y, z).tex(maxU, maxV).endVertex();
        buffer.pos(x, y, z).tex(0.0, maxV).endVertex();
        tessellator.draw();

        GlStateManager.disableBlend();
    }

    public static void renderPlaceholder(int x, int y, int width, int height, float z, String translationKey, ScreenContext ctx)
    {
        ShapeRenderUtils.renderRectangle(x, y, z, width, height, 0x80000000);
        String text = StringUtils.translate(translationKey);
        TextRenderer textRenderer = TextRenderer.INSTANCE;
        int textX = x + Math.max(0, (width - textRenderer.getRenderWidth(text)) / 2);
        int textY = y + Math.max(0, (height - textRenderer.getFontHeight()) / 2);
        textRenderer.renderLine(textX, textY, z + 1f, 0xFFFFFFFF, true, StyledTextLine.of(text), ctx);
    }
}
