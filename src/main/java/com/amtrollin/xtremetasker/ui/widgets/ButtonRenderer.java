package com.amtrollin.xtremetasker.ui.widgets;

import com.amtrollin.xtremetasker.ui.style.UiPalette;
import com.amtrollin.xtremetasker.ui.tasklist.TaskRowsRenderer;
import com.amtrollin.xtremetasker.ui.text.TextUtils;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;

public final class ButtonRenderer
{
    private final UiPalette palette;

    public ButtonRenderer(UiPalette palette)
    {
        this.palette = palette;
    }

    public void drawTab(Graphics2D g, Rectangle bounds, String text, boolean active)
    {
        Color bg = active ? palette.TAB_ACTIVE_BG : palette.TAB_INACTIVE_BG;
        drawBevelBox(g, bounds, bg);

        if (active)
        {
            g.setColor(palette.UI_GOLD);
            g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        g.setColor(active ? palette.UI_TEXT : palette.UI_TEXT_DIM);

        FontMetrics fm = g.getFontMetrics();
        String drawText = TextUtils.truncateToWidth(text, fm, bounds.width - 8);
        int tw = fm.stringWidth(drawText);

        int tx = bounds.x + (bounds.width - tw) / 2;
        int ty = centeredTextBaseline(bounds, fm);

        g.drawString(drawText, tx, ty);
    }

    /** Primary action button — used for Roll task / Mark complete. Brighter bg, solid gold border. */
    public void drawPrimaryButton(Graphics2D g, Rectangle bounds, String text)
    {
        if (bounds.width <= 0 || bounds.height <= 0) return;

        // Dark, slightly warm fill — not golden
        Color bg = new Color(55, 44, 28, 245);
        TaskRowsRenderer.drawBevelBoxLogic(g, bounds, bg, palette.UI_EDGE_DARK, palette.UI_EDGE_LIGHT);

        // Outer dark edge
        g.setColor(palette.UI_EDGE_DARK);
        g.drawRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);
        // Inner gold accent at 130 alpha for a subtle decorative border
        g.setColor(new Color(palette.UI_GOLD.getRed(), palette.UI_GOLD.getGreen(), palette.UI_GOLD.getBlue(), 130));
        g.drawRect(bounds.x + 1, bounds.y + 1, bounds.width - 3, bounds.height - 3);

        // White/light text instead of gold
        g.setColor(palette.UI_TEXT);

        FontMetrics fm = g.getFontMetrics();
        String drawText = TextUtils.truncateToWidth(text, fm, bounds.width - 10);
        int tw = fm.stringWidth(drawText);
        int tx = bounds.x + (bounds.width - tw) / 2;
        int ty = centeredTextBaseline(bounds, fm);
        g.drawString(drawText, tx, ty);
    }

    public void drawButton(Graphics2D g, Rectangle bounds, String text, boolean enabled)
    {
        if (bounds.width <= 0 || bounds.height <= 0)
        {
            return;
        }

        Color bg = enabled ? palette.BTN_ENABLED_BG : palette.BTN_DISABLED_BG;
        drawBevelBox(g, bounds, bg);

        if (enabled)
        {
            g.setColor(palette.UI_GOLD);
            g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        g.setColor(enabled ? palette.UI_TEXT : new Color(palette.UI_TEXT_DIM.getRed(), palette.UI_TEXT_DIM.getGreen(), palette.UI_TEXT_DIM.getBlue(), 130));

        FontMetrics fm = g.getFontMetrics();
        String drawText = TextUtils.truncateToWidth(text, fm, bounds.width - 10);
        int tw = fm.stringWidth(drawText);

        int tx = bounds.x + (bounds.width - tw) / 2;
        int ty = centeredTextBaseline(bounds, fm);

        g.drawString(drawText, tx, ty);
    }

    /** Like drawButton but no gold border and dim-gray text — used for help-tab buttons. */
    public void drawPlainButton(Graphics2D g, Rectangle bounds, String text)
    {
        drawPlainButton(g, bounds, text, palette.BTN_ENABLED_BG);
    }

    public void drawPlainButton(Graphics2D g, Rectangle bounds, String text, Color bg)
    {
        drawPlainButton(g, bounds, text, bg, palette.UI_TEXT, null);
    }

    public void drawPlainButton(Graphics2D g, Rectangle bounds, String text, Color bg, Color textColor, Color outline)
    {
        if (bounds.width <= 0 || bounds.height <= 0)
        {
            return;
        }

        drawBevelBox(g, bounds, bg);

        if (outline != null)
        {
            g.setColor(outline);
            g.drawRect(bounds.x + 1, bounds.y + 1, bounds.width - 3, bounds.height - 3);
        }

        g.setColor(textColor);

        FontMetrics fm = g.getFontMetrics();
        String drawText = TextUtils.truncateToWidth(text, fm, bounds.width - 10);
        int tw = fm.stringWidth(drawText);

        int tx = bounds.x + (bounds.width - tw) / 2;
        int ty = centeredTextBaseline(bounds, fm);

        g.drawString(drawText, tx, ty);
    }

    public void drawBevelBox(Graphics2D g, Rectangle r, Color fill)
    {
        TaskRowsRenderer.drawBevelBoxLogic(g, r, fill, palette.UI_EDGE_DARK, palette.UI_EDGE_LIGHT);
    }

    private int centeredTextBaseline(Rectangle bounds, FontMetrics fm)
    {
        return bounds.y + ((bounds.height - fm.getHeight()) / 2) + fm.getAscent();
    }
}
