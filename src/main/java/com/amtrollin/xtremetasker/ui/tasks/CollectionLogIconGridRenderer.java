package com.amtrollin.xtremetasker.ui.tasks;

import com.amtrollin.xtremetasker.ui.tasks.models.CollectionLogRequirementItem;
import com.amtrollin.xtremetasker.ui.text.TextUtils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Function;

public final class CollectionLogIconGridRenderer
{
    private static final int ICONS_PER_ROW = 8;
    private static final int MAX_ICON_SIZE = 36;
    private static final int MIN_ICON_SIZE = 20;
    private static final int ICON_GAP = 3;
    private static final int ROW_GAP = 6;

    private CollectionLogIconGridRenderer()
    {
    }

    public static int measureHeight(int itemCount, int maxWidth)
    {
        if (itemCount <= 0)
        {
            return 0;
        }

        int rows = (itemCount + ICONS_PER_ROW - 1) / ICONS_PER_ROW;
        int iconSize = iconSize(maxWidth);
        return rows * iconSize + Math.max(0, rows - 1) * ROW_GAP;
    }

    public static int render(
            Graphics2D g,
            FontMetrics fm,
            int x,
            int yBaseline,
            int maxWidth,
            List<CollectionLogRequirementItem> items,
            Function<Integer, BufferedImage> imageProvider,
            Point mousePoint,
            Rectangle tooltipBounds,
            Color textColor,
            Color dimTextColor,
            Color edgeLight,
            Color edgeDark
    )
    {
        if (items == null || items.isEmpty())
        {
            return yBaseline;
        }

        int iconSize = iconSize(maxWidth);
        int top = yBaseline - fm.getAscent();
        CollectionLogRequirementItem hoveredItem = null;
        Rectangle hoveredBounds = null;

        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (int i = 0; i < items.size(); i++)
        {
            CollectionLogRequirementItem item = items.get(i);
            if (item == null)
            {
                continue;
            }

            int col = i % ICONS_PER_ROW;
            int row = i / ICONS_PER_ROW;
            int iconX = x + col * (iconSize + ICON_GAP);
            int iconY = top + row * (iconSize + ROW_GAP);
            Rectangle iconBounds = new Rectangle(iconX, iconY, iconSize, iconSize);

            drawItemImage(g, iconBounds, item, imageProvider, dimTextColor);

            if (item.isObtained())
            {
                drawObtainedCheck(g, iconBounds);
            }

            if (mousePoint != null && iconBounds.contains(mousePoint))
            {
                hoveredItem = item;
                hoveredBounds = iconBounds;
            }
        }

        if (hoveredItem != null && hoveredBounds != null)
        {
            drawTooltip(g, fm, hoveredItem.getName(), hoveredBounds, tooltipBounds, textColor, edgeLight, edgeDark);
        }

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        return yBaseline + measureHeight(items.size(), maxWidth);
    }

    private static int iconSize(int maxWidth)
    {
        int available = Math.max(MIN_ICON_SIZE * ICONS_PER_ROW, maxWidth - (ICONS_PER_ROW - 1) * ICON_GAP);
        return Math.max(MIN_ICON_SIZE, Math.min(MAX_ICON_SIZE, available / ICONS_PER_ROW));
    }

    private static void drawItemImage(
            Graphics2D g,
            Rectangle bounds,
            CollectionLogRequirementItem item,
            Function<Integer, BufferedImage> imageProvider,
            Color dimTextColor
    )
    {
        BufferedImage image = null;
        if (imageProvider != null && item.getItemId() > 0)
        {
            image = imageProvider.apply(item.getItemId());
        }

        if (image == null)
        {
            g.setColor(dimTextColor);
            String fallback = "?";
            FontMetrics fm = g.getFontMetrics();
            g.drawString(fallback,
                    bounds.x + (bounds.width - fm.stringWidth(fallback)) / 2,
                    bounds.y + ((bounds.height - fm.getHeight()) / 2) + fm.getAscent());
            return;
        }

        int drawSize = Math.max(1, Math.min(bounds.width, bounds.height));
        int drawX = bounds.x + (bounds.width - drawSize) / 2;
        int drawY = bounds.y + (bounds.height - drawSize) / 2;
        g.drawImage(image, drawX, drawY, drawSize, drawSize, null);
    }

    private static void drawObtainedCheck(Graphics2D g, Rectangle bounds)
    {
        int size = Math.max(12, bounds.width / 2);
        int x = bounds.x + bounds.width - size;
        int y = bounds.y + bounds.height - size;
        int x1 = x + Math.max(3, size / 4);
        int y1 = y + size / 2;
        int x2 = x + size / 2 - 1;
        int y2 = y + size - Math.max(3, size / 4);
        int x3 = x + size - Math.max(3, size / 5);
        int y3 = y + Math.max(3, size / 4);

        Stroke oldStroke = g.getStroke();
        g.setStroke(new BasicStroke(Math.max(3f, bounds.width / 10f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(10, 20, 10, 230));
        g.drawLine(x1, y1, x2, y2);
        g.drawLine(x2, y2, x3, y3);

        g.setStroke(new BasicStroke(Math.max(1.7f, bounds.width / 15f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(92, 230, 112, 250));
        g.drawLine(x1, y1, x2, y2);
        g.drawLine(x2, y2, x3, y3);
        g.setStroke(oldStroke);
    }

    private static void drawTooltip(
            Graphics2D g,
            FontMetrics fm,
            String text,
            Rectangle anchor,
            Rectangle bounds,
            Color textColor,
            Color edgeLight,
            Color edgeDark
    )
    {
        if (text == null || text.trim().isEmpty())
        {
            return;
        }

        Rectangle clamp = bounds != null && bounds.width > 0 && bounds.height > 0
                ? bounds
                : g.getClipBounds();
        if (clamp == null)
        {
            return;
        }

        int padX = 7;
        int padY = 5;
        int maxTextW = Math.max(40, Math.min(180, clamp.width - 12 - padX * 2));
        List<String> lines = TextUtils.wrapText(text, fm, maxTextW);
        if (lines.isEmpty())
        {
            return;
        }

        int textW = 0;
        for (String line : lines)
        {
            textW = Math.max(textW, fm.stringWidth(line));
        }

        int w = textW + padX * 2;
        int h = fm.getHeight() * lines.size() + padY * 2;
        int x = anchor.x + anchor.width / 2 - w / 2;
        int y = anchor.y - h - 4;
        if (y < clamp.y + 4)
        {
            y = anchor.y + anchor.height + 4;
        }

        x = Math.max(clamp.x + 4, Math.min(x, clamp.x + clamp.width - w - 4));
        y = Math.max(clamp.y + 4, Math.min(y, clamp.y + clamp.height - h - 4));

        g.setColor(new Color(25, 18, 10, 245));
        g.fillRect(x, y, w, h);
        g.setColor(new Color(edgeLight.getRed(), edgeLight.getGreen(), edgeLight.getBlue(), 100));
        g.drawRect(x, y, w - 1, h - 1);
        g.setColor(new Color(edgeDark.getRed(), edgeDark.getGreen(), edgeDark.getBlue(), 180));
        g.drawLine(x + 1, y + h - 1, x + w - 1, y + h - 1);

        g.setColor(textColor);
        int lineY = y + padY + fm.getAscent();
        for (String line : lines)
        {
            g.drawString(line, x + padX, lineY);
            lineY += fm.getHeight();
        }
    }
}
