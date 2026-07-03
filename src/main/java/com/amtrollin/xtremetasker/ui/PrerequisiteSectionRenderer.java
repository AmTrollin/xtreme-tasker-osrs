package com.amtrollin.xtremetasker.ui;

import com.amtrollin.xtremetasker.models.PrerequisiteStatus;
import com.amtrollin.xtremetasker.models.PrerequisiteStatus.MarkerIcon;
import com.amtrollin.xtremetasker.ui.style.UiPalette;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Function;
import net.runelite.api.Skill;
import static com.amtrollin.xtremetasker.ui.style.UiPalette.withAlpha;
import static com.amtrollin.xtremetasker.ui.text.TextUtils.*;

public final class PrerequisiteSectionRenderer
{
    private final int rowHeight;
    private final Color uiText;
    private final Color uiTextDim;

    public PrerequisiteSectionRenderer(int rowHeight, Color uiText, Color uiTextDim)
    {
        this.rowHeight = rowHeight;
        this.uiText = uiText;
        this.uiTextDim = uiTextDim;
    }

    public int measureTip(String tip, FontMetrics fm, int maxWidth, int maxLines)
    {
        return rowHeight * Math.min(wrapText("Tip: " + safe(tip), fm, Math.max(maxWidth, 40)).size(), maxLines);
    }

    public int drawTip(Graphics2D g, FontMetrics fm, String tip, int x, int y, int maxWidth, int maxLines)
    {
        List<String> lines = wrapText("Tip: " + safe(tip), fm, Math.max(maxWidth, 40));
        g.setColor(uiTextDim);
        for (int i = 0; i < Math.min(lines.size(), maxLines); i++)
        {
            g.drawString(truncateToWidth(lines.get(i), fm, maxWidth), x, y);
            y += rowHeight;
        }
        return y;
    }

    public int measure(
            String prereqs,
            List<PrerequisiteStatus> statuses,
            FontMetrics fm,
            int maxWidth,
            Function<Skill, BufferedImage> skillImageProvider,
            Function<MarkerIcon, BufferedImage> markerImageProvider)
    {
        if (statuses != null && !statuses.isEmpty())
        {
            int total = 0;
            for (PrerequisiteStatus status : statuses)
            {
                BufferedImage markerImage = PrerequisiteIconRenderer.resolveMarkerImage(status, skillImageProvider, markerImageProvider);
                int lineHeight = PrerequisiteIconRenderer.lineHeight(rowHeight, status);
                total += lineHeight * wrapText(status.getText(), fm,
                        PrerequisiteIconRenderer.textWidth(fm, maxWidth, markerImage)).size();
            }
            return total;
        }

        String formatted = formatText(prereqs);
        if (formatted.isEmpty())
        {
            return rowHeight;
        }

        int total = 0;
        for (String para : formatted.split("\n"))
        {
            String p = para.trim();
            if (!p.isEmpty())
            {
                total += rowHeight * wrapText(p, fm, maxWidth).size();
            }
        }
        return total;
    }

    public int render(
            Graphics2D g,
            FontMetrics fm,
            int x,
            int y,
            int maxWidth,
            String prereqs,
            List<PrerequisiteStatus> statuses,
            Function<Skill, BufferedImage> skillImageProvider,
            Function<MarkerIcon, BufferedImage> markerImageProvider,
            Color rawTextColor)
    {
        if (statuses != null && !statuses.isEmpty())
        {
            return drawStatuses(g, fm, x, y, maxWidth, statuses, skillImageProvider, markerImageProvider);
        }

        String formatted = formatText(prereqs);
        if (formatted.isEmpty())
        {
            g.setColor(uiTextDim);
            g.drawString("None", x, y);
            return y + rowHeight;
        }

        g.setColor(rawTextColor);
        for (String para : formatted.split("\n"))
        {
            String p = para.trim();
            if (p.isEmpty())
            {
                continue;
            }

            for (String line : wrapText(p, fm, maxWidth))
            {
                g.drawString(truncateToWidth(line, fm, maxWidth), x, y);
                y += rowHeight;
            }
        }
        return y;
    }

    public static String normalizeText(String prereqs)
    {
        String normalized = safe(prereqs).replace("\r", "").trim();
        return normalized.isEmpty() || normalized.equalsIgnoreCase("none") || normalized.equalsIgnoreCase("n/a") || normalized.equals("-")
                ? ""
                : normalized;
    }

    private static String formatText(String prereqs)
    {
        return safe(prereqs).replace("\r", "").replaceAll("\\s*;\\s*", "\n").replaceAll("\n{2,}", "\n").trim();
    }

    private int drawStatuses(
            Graphics2D g,
            FontMetrics fm,
            int x,
            int y,
            int maxWidth,
            List<PrerequisiteStatus> statuses,
            Function<Skill, BufferedImage> skillImageProvider,
            Function<MarkerIcon, BufferedImage> markerImageProvider)
    {
        for (PrerequisiteStatus status : statuses)
        {
            BufferedImage markerImage = PrerequisiteIconRenderer.resolveMarkerImage(status, skillImageProvider, markerImageProvider);
            int textX = PrerequisiteIconRenderer.textX(fm, x, markerImage);
            int textWidth = PrerequisiteIconRenderer.textWidth(fm, maxWidth, markerImage);
            int lineHeight = PrerequisiteIconRenderer.lineHeight(rowHeight, status);
            boolean firstLine = true;
            for (String line : wrapText(status.getText(), fm, textWidth))
            {
                if (firstLine)
                {
                    PrerequisiteIconRenderer.drawMarker(g, fm, markerImage, x, y);
                }
                drawStatusLine(g, fm, status, truncateToWidth(line, fm, textWidth), textX, y);
                y += lineHeight;
                firstLine = false;
            }
        }
        return y;
    }

    private void drawStatusLine(Graphics2D g, FontMetrics fm, PrerequisiteStatus status, String line, int x, int y)
    {
        boolean hasCheckSpans = status.getCheckSpans() != null && !status.getCheckSpans().isEmpty();
        Color textColor = !hasCheckSpans && status.isCompleted() ? uiTextDim : uiText;
        if (isStartQuestLine(status, line))
        {
            String startText = "Start";
            g.setColor(UiPalette.TIER_COMPLETE_GLOW);
            g.drawString(startText, x, y);
            g.setColor(textColor);
            g.drawString(line.substring(startText.length()), x + fm.stringWidth(startText), y);
        }
        else
        {
            g.setColor(textColor);
            g.drawString(line, x, y);
        }

        if (!hasCheckSpans)
        {
            if (status.isCompleted())
            {
                drawStrikeThrough(g, fm, line, x, y);
            }
            return;
        }

        for (PrerequisiteStatus.CheckSpan span : status.getCheckSpans())
        {
            if (!span.isCompleted() || span.getStart() < 0 || span.getEnd() > status.getText().length() || span.getStart() >= span.getEnd())
            {
                continue;
            }

            String spanText = status.getText().substring(span.getStart(), span.getEnd());
            int lineIndex = line.indexOf(spanText);
            if (lineIndex < 0)
            {
                continue;
            }

            int spanX = x + fm.stringWidth(line.substring(0, lineIndex));
            g.setColor(uiTextDim);
            g.drawString(spanText, spanX, y);
            drawStrikeThrough(g, fm, spanText, spanX, y);
        }
    }

    private static boolean isStartQuestLine(PrerequisiteStatus status, String line)
    {
        return status.getMarkerIcons() != null
                && status.getMarkerIcons().contains(MarkerIcon.START_QUEST)
                && line != null
                && line.startsWith("Start");
    }

    private void drawStrikeThrough(Graphics2D g, FontMetrics fm, String text, int x, int y)
    {
        int strikeY = y - (fm.getAscent() * 3 / 5);
        g.setColor(withAlpha(uiTextDim, 170));
        g.drawLine(x, strikeY, x + fm.stringWidth(text), strikeY);
    }
}
