package com.amtrollin.xtremetasker.ui.widgets;

import com.amtrollin.xtremetasker.ui.style.UiPalette;
import com.amtrollin.xtremetasker.ui.text.TextUtils;
import java.awt.*;
import net.runelite.client.ui.FontManager;

/** Consistent expandable section rows in the task details popup. */
public final class DisclosureHeader
{
    public static final int HEIGHT = 18;

    private DisclosureHeader() {}

    public static void draw(Graphics2D g, String text, int x, int baseline, int width,
            boolean expanded, Rectangle hitBounds, Point mouse, Rectangle viewport)
    {
        draw(g, text, x, baseline, width, expanded, hitBounds, mouse, viewport,
                new Color(140, 190, 220), new Color(190, 225, 255));
    }

    public static void draw(Graphics2D g, String text, int x, int baseline, int width,
            boolean expanded, Rectangle hitBounds, Point mouse, Rectangle viewport,
            Color color, Color hoverColor)
    {
        Font original = g.getFont();
        g.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics fm = g.getFontMetrics();
        hitBounds.setBounds(x, baseline - fm.getAscent(), width, HEIGHT);
        boolean hover = mouse != null && viewport != null && viewport.contains(mouse) && hitBounds.contains(mouse);
        g.setColor(hover ? hoverColor : color);
        int cy = baseline - fm.getAscent() / 2;
        g.drawLine(x + 1, cy, x + 7, cy);
        if (!expanded) g.drawLine(x + 4, cy - 3, x + 4, cy + 3);
        String label = TextUtils.truncateToWidth(text, fm, Math.max(1, width - 13));
        g.drawString(label, x + 13, baseline);
        if (hover) g.drawLine(x + 13, baseline + 2, x + 13 + fm.stringWidth(label), baseline + 2);
        g.setFont(original);
    }

    public static void check(Graphics2D g, int x, int baseline)
    {
        g.setColor(UiPalette.TIER_COMPLETE_GLOW);
        g.drawLine(x, baseline - 4, x + 3, baseline - 1);
        g.drawLine(x + 3, baseline - 1, x + 9, baseline - 8);
    }
}
