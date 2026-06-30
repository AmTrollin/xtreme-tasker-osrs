package com.amtrollin.xtremetasker.ui.style;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import static com.amtrollin.xtremetasker.ui.style.UiPalette.withAlpha;

public final class UiDraw
{
    private UiDraw()
    {
    }

    public static int centeredTextBaseline(Rectangle bounds, FontMetrics fm)
    {
        return bounds.y + ((bounds.height - fm.getHeight()) / 2) + fm.getAscent();
    }

    public static void drawBevelBox(Graphics2D g, Rectangle r, Color fill, Color edgeDark, Color edgeLight)
    {
        g.setColor(fill);
        g.fillRect(r.x, r.y, r.width, r.height);
        g.setColor(edgeDark);
        g.drawRect(r.x, r.y, r.width, r.height);
        g.setColor(edgeLight);
        g.drawLine(r.x + 1, r.y + 1, r.x + r.width - 2, r.y + 1);
        g.drawLine(r.x + 1, r.y + 1, r.x + 1, r.y + r.height - 2);
        g.setColor(edgeDark);
        g.drawLine(r.x + 1, r.y + r.height - 2, r.x + r.width - 2, r.y + r.height - 2);
        g.drawLine(r.x + r.width - 2, r.y + 1, r.x + r.width - 2, r.y + r.height - 2);
    }

    public static BufferedImage loadImage(String resourcePath)
    {
        try (InputStream in = UiDraw.class.getResourceAsStream(resourcePath))
        {
            return in == null ? null : ImageIO.read(in);
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    public static void drawQuestionIcon(Graphics2D g, BufferedImage icon, int x, int y, int size, Color fill, Color text)
    {
        if (icon != null)
        {
            g.drawImage(icon, x, y, size, size, null);
            return;
        }

        g.setColor(fill);
        g.fillOval(x, y, size, size);
        g.setColor(text);
        drawCenteredQuestionMark(g, x, y, size);
    }

    public static void drawCenteredQuestionMark(Graphics2D g, int x, int y, int size)
    {
        java.awt.font.GlyphVector glyph = g.getFont().createGlyphVector(g.getFontRenderContext(), "?");
        java.awt.geom.Rectangle2D visualBounds = glyph.getVisualBounds();
        float textX = (float) (x + (size - visualBounds.getWidth()) / 2.0 - visualBounds.getX());
        float textY = (float) (y + (size - visualBounds.getHeight()) / 2.0 - visualBounds.getY());
        g.drawString("?", textX, textY);
    }

    public static void drawScrollbar(Graphics2D g, Rectangle rail, int totalRows, int visibleRows, int offsetRows,
            Rectangle railBounds, Rectangle thumbBounds, Color edgeDark, Color edgeLight, Color gold)
    {
        railBounds.setBounds(0, 0, 0, 0);
        thumbBounds.setBounds(0, 0, 0, 0);
        if (totalRows <= visibleRows || totalRows <= 0 || visibleRows <= 0 || rail.height <= 0)
        {
            return;
        }

        railBounds.setBounds(rail);
        g.setColor(new Color(0, 0, 0, 60));
        g.fillRect(rail.x, rail.y, rail.width, rail.height);
        Rectangle thumb = scrollbarThumbBounds(rail, totalRows, visibleRows, offsetRows);
        thumbBounds.setBounds(thumb);
        drawBevelBox(g, thumb, new Color(78, 62, 38, 200), edgeDark, edgeLight);
        g.setColor(withAlpha(gold, 140));
        g.drawRect(thumb.x, thumb.y, thumb.width, thumb.height);
    }

    public static void drawCheckmark(Graphics2D g, Rectangle box, int pad)
    {
        int midX = box.x + box.width / 2 - 1;
        int bottomY = box.y + box.height - pad - 1;
        g.drawLine(box.x + pad, box.y + box.height / 2, midX, bottomY);
        g.drawLine(midX, bottomY, box.x + box.width - pad, box.y + pad);
    }

    public static void drawCloseX(Graphics2D g, Rectangle bounds)
    {
        g.setColor(new Color(200, 200, 200, 180));
        int ccx = bounds.x + bounds.width / 2;
        int ccy = bounds.y + bounds.height / 2;
        int carm = 6;
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        Stroke oldStroke = g.getStroke();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(ccx - carm, ccy - carm, ccx + carm, ccy + carm);
        g.drawLine(ccx + carm, ccy - carm, ccx - carm, ccy + carm);
        g.setStroke(oldStroke);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
    }

    public static void drawHoverText(Graphics2D g, FontMetrics fm, String text, Rectangle bounds, Color color)
    {
        g.setColor(color);
        g.drawString(text, bounds.x, bounds.y - 4);
    }

    public static void drawHoverText(Graphics2D g, FontMetrics fm, String text, Rectangle bounds, int maxRight, Color color)
    {
        g.setColor(color);
        g.drawString(text, Math.min(bounds.x, maxRight - fm.stringWidth(text)), bounds.y - 4);
    }

    public static void drawRightAlignedHoverText(Graphics2D g, FontMetrics fm, String text,
            int minX, int rightX, Rectangle anchorBounds, Color color)
    {
        g.setColor(color);
        g.drawString(text, Math.max(minX, rightX - fm.stringWidth(text)), anchorBounds.y + 1);
    }

    public static Rectangle scrollbarRailBounds(Rectangle viewport, int width)
    {
        return new Rectangle(viewport.x + viewport.width - width, viewport.y, width, viewport.height);
    }

    public static Rectangle scrollbarThumbBounds(Rectangle rail, int totalRows, int visibleRows, int offsetRows)
    {
        if (totalRows <= 0 || visibleRows <= 0 || rail.height <= 0)
        {
            return new Rectangle(rail.x, rail.y, Math.max(0, rail.width - 1), 0);
        }

        int thumbH = Math.min(rail.height, Math.max(12, Math.round(rail.height * ((float) visibleRows / totalRows))));
        int maxOffset = Math.max(1, totalRows - visibleRows);
        int thumbY = rail.y + (int) ((rail.height - thumbH) * ((float) clamp(offsetRows, maxOffset) / maxOffset));
        return new Rectangle(rail.x, thumbY, Math.max(0, rail.width - 1), Math.max(0, thumbH - 1));
    }

    private static int clamp(int v, int max)
    {
        return Math.max(0, Math.min(max, v));
    }
}
