package com.amtrollin.xtremetasker.ui.widgets;

import com.amtrollin.xtremetasker.ui.style.*;
import com.amtrollin.xtremetasker.ui.text.TextUtils;
import java.awt.*;
import static com.amtrollin.xtremetasker.ui.style.UiDraw.centeredTextBaseline;
import static com.amtrollin.xtremetasker.ui.style.UiPalette.withAlpha;

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

        drawCenteredText(g, bounds, text, 8);
    }

    /** Primary action button — used for Roll task / Mark complete. Brighter bg, solid gold border. */
    public void drawPrimaryButton(Graphics2D g, Rectangle bounds, String text)
    {
        drawPrimaryButton(g, bounds, text, null);
    }

    public void drawPrimaryButton(Graphics2D g, Rectangle bounds, String text, Color borderAccent)
    {
        if (bounds.width <= 0 || bounds.height <= 0) return;

        // Dark, slightly warm fill — not golden
        Color bg = new Color(55, 44, 28, 245);
        UiDraw.drawBevelBox(g, bounds, bg, palette.UI_EDGE_DARK, palette.UI_EDGE_LIGHT);

        // Outer dark edge
        g.setColor(palette.UI_EDGE_DARK);
        g.drawRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);
        // Inner gold accent at 130 alpha for a subtle decorative border
        Color accent = borderAccent == null ? palette.UI_GOLD : borderAccent;
        g.setColor(withAlpha(accent, borderAccent == null ? 130 : 230));
        g.drawRect(bounds.x + 1, bounds.y + 1, bounds.width - 3, bounds.height - 3);

        // White/light text instead of gold
        g.setColor(palette.UI_TEXT);

        drawCenteredText(g, bounds, text, 10);
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

        g.setColor(enabled ? palette.UI_TEXT : withAlpha(palette.UI_TEXT_DIM, 130));

        drawCenteredText(g, bounds, text, 10);
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

        drawCenteredText(g, bounds, text, 10);
    }

    public void drawBevelBox(Graphics2D g, Rectangle r, Color fill)
    {
        UiDraw.drawBevelBox(g, r, fill, palette.UI_EDGE_DARK, palette.UI_EDGE_LIGHT);
    }

    private void drawCenteredText(Graphics2D g, Rectangle bounds, String text, int pad)
    {
        FontMetrics fm = g.getFontMetrics();
        String drawText = TextUtils.truncateToWidth(text, fm, bounds.width - pad);
        g.drawString(drawText, bounds.x + (bounds.width - fm.stringWidth(drawText)) / 2, centeredTextBaseline(bounds, fm));
    }
}
