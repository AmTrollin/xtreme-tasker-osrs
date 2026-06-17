package com.amtrollin.xtremetasker.ui.tasks;

import com.amtrollin.xtremetasker.ui.tasks.models.CollectionLogRequirementItem;
import com.amtrollin.xtremetasker.ui.text.TextUtils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CollectionLogIconGridRenderer
{
    private static final Logger log = LoggerFactory.getLogger(CollectionLogIconGridRenderer.class);
    private static final int ICONS_PER_ROW = 8;
    private static final int MAX_ICON_SIZE = 36;
    private static final int MIN_ICON_SIZE = 20;
    private static final int ICON_GAP = 3;
    private static final int ROW_GAP = 6;
    private static final long SLOW_RENDER_LOG_THRESHOLD_NANOS = 8_000_000L;
    private static final long SLOW_RENDER_LOG_INTERVAL_MS = 2_000L;
    private static long lastSlowRenderLogMs = 0L;
    private static final Map<String, LabelFit> LABEL_FIT_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<String, LabelFit>(256, 0.75f, true)
            {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, LabelFit> eldest)
                {
                    return size() > 256;
                }
            });

    private CollectionLogIconGridRenderer()
    {
    }

    public static int measureHeight(int itemCount, int maxWidth)
    {
        return measureHeight(itemCount, maxWidth, ICONS_PER_ROW);
    }

    public static int measureHeight(int itemCount, int maxWidth, int iconsPerRow)
    {
        if (itemCount <= 0)
        {
            return 0;
        }

        int columns = normalizeColumns(iconsPerRow);
        int rows = (itemCount + columns - 1) / columns;
        int iconSize = iconSize(maxWidth, columns);
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
        return render(g, fm, x, yBaseline, maxWidth, items, imageProvider, mousePoint, tooltipBounds,
                textColor, dimTextColor, edgeLight, edgeDark, ICONS_PER_ROW);
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
            Color edgeDark,
            int iconsPerRow
    )
    {
        if (items == null || items.isEmpty())
        {
            return yBaseline;
        }

        long renderStartNanos = items.size() >= 16 ? System.nanoTime() : 0L;
        int columns = normalizeColumns(iconsPerRow);
        int iconSize = iconSize(maxWidth, columns);
        int top = yBaseline - fm.getAscent();
        CollectionLogRequirementItem hoveredItem = null;
        Rectangle hoveredBounds = null;
        Rectangle clipBounds = g.getClipBounds();

        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (int i = 0; i < items.size(); i++)
        {
            CollectionLogRequirementItem item = items.get(i);
            if (item == null)
            {
                continue;
            }

            int col = i % columns;
            int row = i / columns;
            int iconX = x + col * (iconSize + ICON_GAP);
            int iconY = top + row * (iconSize + ROW_GAP);
            Rectangle iconBounds = new Rectangle(iconX, iconY, iconSize, iconSize);
            boolean hovered = mousePoint != null && iconBounds.contains(mousePoint);

            if (clipBounds == null || iconBounds.intersects(clipBounds) || hovered)
            {
                drawItemImage(g, iconBounds, item, imageProvider, dimTextColor);
                drawBadgeText(g, iconBounds, item.getBadgeText(), textColor);

                if (item.isObtained())
                {
                    drawObtainedCheck(g, iconBounds);
                }
            }

            if (hovered)
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
        logSlowRender(renderStartNanos, items.size(), columns, maxWidth);
        return yBaseline + measureHeight(items.size(), maxWidth, columns);
    }

    private static int iconSize(int maxWidth)
    {
        return iconSize(maxWidth, ICONS_PER_ROW);
    }

    private static int iconSize(int maxWidth, int iconsPerRow)
    {
        int columns = normalizeColumns(iconsPerRow);
        int available = Math.max(MIN_ICON_SIZE * columns, maxWidth - (columns - 1) * ICON_GAP);
        int maxIconSize = columns < ICONS_PER_ROW ? 56 : MAX_ICON_SIZE;
        return Math.max(MIN_ICON_SIZE, Math.min(maxIconSize, available / columns));
    }

    private static int normalizeColumns(int iconsPerRow)
    {
        return Math.max(1, iconsPerRow);
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

    private static void drawBadgeText(Graphics2D g, Rectangle bounds, String text, Color textColor)
    {
        if (text == null || text.trim().isEmpty())
        {
            return;
        }

        String badge = text.trim();
        if (badge.length() > 3)
        {
            drawFittedIconLabel(g, bounds, badge);
            return;
        }

        drawFittedIconLabel(g, bounds, badge);
    }

    private static void drawFittedIconLabel(Graphics2D g, Rectangle bounds, String text)
    {
        Font oldFont = g.getFont();
        int labelW = Math.max(1, bounds.width - 2);
        int labelH = Math.max(10, bounds.height - 4);
        LabelFit fit = fitLabel(g, oldFont, text, labelW, labelH);
        g.setFont(fit.font);
        FontMetrics fm = g.getFontMetrics();
        int totalTextH = fit.lines.size() * fm.getHeight();
        int lineY = bounds.y + (bounds.height - totalTextH) / 2 + fm.getAscent();
        boolean shortNumericLabel = text.matches("\\d{1,2}");
        for (String line : fit.lines)
        {
            int lineX = bounds.x + (bounds.width - fm.stringWidth(line)) / 2;
            if (shortNumericLabel)
            {
                lineX -= Math.max(1, bounds.width / 14);
            }
            drawOutlinedText(g, line, lineX, lineY);
            lineY += fm.getHeight();
        }

        g.setFont(oldFont);
    }

    private static void drawOutlinedText(Graphics2D g, String text, int x, int y)
    {
        g.setColor(new Color(0, 0, 0, 230));
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dy = -1; dy <= 1; dy++)
            {
                if (dx == 0 && dy == 0)
                {
                    continue;
                }
                g.drawString(text, x + dx, y + dy);
            }
        }

        g.setColor(new Color(24, 54, 135, 255));
        g.drawString(text, x, y);
    }

    private static LabelFit fitLabel(Graphics2D g, Font baseFont, String text, int maxWidth, int maxHeight)
    {
        String cacheKey = labelFitCacheKey(baseFont, text, maxWidth, maxHeight);
        LabelFit cached = LABEL_FIT_CACHE.get(cacheKey);
        if (cached != null)
        {
            return cached;
        }

        boolean shortNumericLabel = text != null && text.matches("\\d{1,2}");
        float maxSize = shortNumericLabel
                ? Math.min(20f, Math.max(12f, maxHeight * 0.82f))
                : Math.min(15f, Math.max(10f, maxHeight * 0.62f));
        for (float size = maxSize; size >= 7f; size -= 0.5f)
        {
            Font font = baseFont.deriveFont(Font.BOLD, size);
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics();
            List<String> lines = TextUtils.wrapText(text, fm, maxWidth);
            if (lines.size() > 2)
            {
                lines = List.of(
                        TextUtils.truncateToWidth(lines.get(0), fm, maxWidth),
                        TextUtils.truncateToWidth(String.join(" ", lines.subList(1, lines.size())), fm, maxWidth));
            }
            if (!lines.isEmpty() && lines.size() * fm.getHeight() <= maxHeight + 2)
            {
                LabelFit fit = new LabelFit(font, lines);
                LABEL_FIT_CACHE.put(cacheKey, fit);
                return fit;
            }
        }

        Font font = baseFont.deriveFont(Font.BOLD, 7f);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        LabelFit fit = new LabelFit(font, List.of(TextUtils.truncateToWidth(text, fm, maxWidth)));
        LABEL_FIT_CACHE.put(cacheKey, fit);
        return fit;
    }

    private static String labelFitCacheKey(Font font, String text, int maxWidth, int maxHeight)
    {
        String fontName = font == null ? "" : font.getFontName();
        int fontStyle = font == null ? 0 : font.getStyle();
        return fontName + "|" + fontStyle + "|" + maxWidth + "x" + maxHeight + "|" + String.valueOf(text);
    }

    private static void logSlowRender(long renderStartNanos, int itemCount, int columns, int maxWidth)
    {
        if (renderStartNanos <= 0L)
        {
            return;
        }

        long elapsedNanos = System.nanoTime() - renderStartNanos;
        if (elapsedNanos < SLOW_RENDER_LOG_THRESHOLD_NANOS)
        {
            return;
        }

        long nowMs = System.currentTimeMillis();
        if (nowMs - lastSlowRenderLogMs < SLOW_RENDER_LOG_INTERVAL_MS)
        {
            return;
        }

        lastSlowRenderLogMs = nowMs;
        log.debug("Slow collection-log icon grid render: {} icons, {} columns, maxWidth={}, elapsed={}ms",
                itemCount, columns, maxWidth, elapsedNanos / 1_000_000L);
    }

    private static final class LabelFit
    {
        private final Font font;
        private final List<String> lines;

        private LabelFit(Font font, List<String> lines)
        {
            this.font = font;
            this.lines = lines;
        }
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
        int y = anchor.y + anchor.height - 6;
        if (y + h > clamp.y + clamp.height - 4)
        {
            y = anchor.y - h + 3;
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
