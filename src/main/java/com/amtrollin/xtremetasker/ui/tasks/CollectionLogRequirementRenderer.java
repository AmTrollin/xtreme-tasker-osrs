package com.amtrollin.xtremetasker.ui.tasks;

import com.amtrollin.xtremetasker.ui.style.UiPalette;
import com.amtrollin.xtremetasker.ui.tasks.models.CollectionLogRequirementPreview;
import com.amtrollin.xtremetasker.ui.text.UiText;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Function;
import static com.amtrollin.xtremetasker.ui.text.TaskLabelFormatter.tierLabel;
import static com.amtrollin.xtremetasker.ui.text.TextUtils.*;

public final class CollectionLogRequirementRenderer
{
    private static final String MEDALLION_ASSEMBLY_TITLE_PREFIX = "Need all ";
    private static final int SECONDARY_SECTION_GAP = 6;
    private static final int MEDALLION_ASSEMBLY_SECTION_GAP = 12;
    private static final int TIER_SECTION_ICON_GAP = 5;
    private static final int TIER_SECTION_LABEL_TOP_GAP = 4;
    private static final int OTHER_SEQUENCE_LABEL_TOP_GAP = 5;
    private static final String OTHER_SEQUENCE_CLOGS_LABEL = UiText.get("current.other_sequence_clogs");

    private final int rowHeight;
    private final int tierLabelMaxWidth;
    private final Color uiGold;
    private final Color uiText;
    private final Color uiTextDim;
    private final Color edgeLight;
    private final Color edgeDark;

    public CollectionLogRequirementRenderer(
            int rowHeight,
            int tierLabelMaxWidth,
            Color uiGold,
            Color uiText,
            Color uiTextDim,
            Color edgeLight,
            Color edgeDark)
    {
        this.rowHeight = rowHeight;
        this.tierLabelMaxWidth = tierLabelMaxWidth;
        this.uiGold = uiGold;
        this.uiText = uiText;
        this.uiTextDim = uiTextDim;
        this.edgeLight = edgeLight;
        this.edgeDark = edgeDark;
    }

    public int measure(CollectionLogRequirementPreview preview, FontMetrics fm, int maxWidth)
    {
        if (preview == null || !preview.hasItems())
        {
            return 0;
        }

        int total = rowHeight;
        if (preview.showSummaryText())
        {
            total += rowHeight * summaryTextLineCount(preview.summaryText());
        }
        if (preview.showTierSections())
        {
            total += measureTierSections(preview, fm, maxWidth);
        }
        else if (preview.showItemList())
        {
            total += CollectionLogIconGridRenderer.measureHeight(preview.getItems().size(), maxWidth, preview.iconColumns());
        }
        if (preview.showSecondaryItemList())
        {
            total += secondarySectionGap(preview) + rowHeight;
            total += CollectionLogIconGridRenderer.measureHeight(preview.secondaryItems().size(), maxWidth, preview.secondaryIconColumns());
        }
        return total;
    }

    public int render(
            Graphics2D g,
            FontMetrics fm,
            int x,
            int y,
            int maxWidth,
            CollectionLogRequirementPreview preview,
            Function<Integer, BufferedImage> imageProvider,
            Point mousePoint,
            Rectangle tooltipBounds)
    {
        g.setColor(uiGold);
        g.drawString(title(preview), x, y);
        y += rowHeight;

        if (preview.showSummaryText())
        {
            y = drawSummaryText(g, fm, preview.summaryText(), x, y, maxWidth);
            if (preview.showTierSections())
            {
                y += TIER_SECTION_ICON_GAP;
            }
        }

        if (preview.showTierSections())
        {
            y = drawTierSections(g, fm, preview, x, y, maxWidth, imageProvider, mousePoint, tooltipBounds);
        }
        else if (preview.showItemList())
        {
            y = CollectionLogIconGridRenderer.render(g, fm, x, y, maxWidth, preview.getItems(),
                    imageProvider, mousePoint, tooltipBounds, uiText, uiTextDim, edgeLight, edgeDark, preview.iconColumns());
        }

        if (preview.showSecondaryItemList())
        {
            y += secondarySectionGap(preview);
            g.setColor(uiGold);
            g.drawString(truncateToWidth(preview.secondaryTitleText(), fm, maxWidth), x, y);
            y += rowHeight;
            y = CollectionLogIconGridRenderer.render(g, fm, x, y, maxWidth, preview.secondaryItems(),
                    imageProvider, mousePoint, tooltipBounds, uiText, uiTextDim, edgeLight, edgeDark, preview.secondaryIconColumns());
        }

        return y;
    }

    private int measureTierSections(CollectionLogRequirementPreview preview, FontMetrics fm, int maxWidth)
    {
        int total = 0;
        CollectionLogRequirementPreview.TierSection current = preview.currentTierSection();
        if (current != null)
        {
            total += CollectionLogIconGridRenderer.measureHeight(current.items().size(), maxWidth, current.iconColumns());
        }

        List<CollectionLogRequirementPreview.TierSection> otherSections = preview.otherTierSectionsHardestFirst();
        if (!otherSections.isEmpty())
        {
            total += SECONDARY_SECTION_GAP + rowHeight + OTHER_SEQUENCE_LABEL_TOP_GAP;
            total += rowHeight * wrapText(OTHER_SEQUENCE_CLOGS_LABEL, fm, maxWidth).size();
        }
        for (int i = 0; i < otherSections.size(); i++)
        {
            CollectionLogRequirementPreview.TierSection section = otherSections.get(i);
            if (i > 0)
            {
                total += SECONDARY_SECTION_GAP;
            }
            total += TIER_SECTION_LABEL_TOP_GAP + rowHeight + TIER_SECTION_ICON_GAP;
            total += CollectionLogIconGridRenderer.measureHeight(section.items().size(), maxWidth, section.iconColumns());
        }
        return total;
    }

    private int drawTierSections(
            Graphics2D g,
            FontMetrics fm,
            CollectionLogRequirementPreview preview,
            int x,
            int y,
            int maxWidth,
            Function<Integer, BufferedImage> imageProvider,
            Point mousePoint,
            Rectangle tooltipBounds)
    {
        CollectionLogRequirementPreview.TierSection current = preview.currentTierSection();
        if (current != null)
        {
            y = CollectionLogIconGridRenderer.render(g, fm, x, y, maxWidth, current.items(),
                    imageProvider, mousePoint, tooltipBounds, uiText, uiTextDim, edgeLight, edgeDark, current.iconColumns());
        }

        List<CollectionLogRequirementPreview.TierSection> sections = preview.otherTierSectionsHardestFirst();
        if (!sections.isEmpty())
        {
            y += SECONDARY_SECTION_GAP + rowHeight + OTHER_SEQUENCE_LABEL_TOP_GAP;
            g.setColor(uiTextDim);
            for (String line : wrapText(OTHER_SEQUENCE_CLOGS_LABEL, fm, maxWidth))
            {
                g.drawString(truncateToWidth(line, fm, maxWidth), x, y);
                y += rowHeight;
            }
        }
        for (int i = 0; i < sections.size(); i++)
        {
            CollectionLogRequirementPreview.TierSection section = sections.get(i);
            if (i > 0)
            {
                y += SECONDARY_SECTION_GAP;
            }
            y += TIER_SECTION_LABEL_TOP_GAP;
            drawTierLabel(g, fm, section, x, y);
            y += rowHeight + TIER_SECTION_ICON_GAP;

            Composite oldComposite = g.getComposite();
            if (!section.currentTier())
            {
                g.setComposite(AlphaComposite.SrcOver.derive(0.45f));
            }
            y = CollectionLogIconGridRenderer.render(g, fm, x, y, maxWidth, section.items(),
                    imageProvider, mousePoint, tooltipBounds, uiText, uiTextDim, edgeLight, edgeDark, section.iconColumns());
            g.setComposite(oldComposite);
        }
        return y;
    }

    private void drawTierLabel(Graphics2D g, FontMetrics fm, CollectionLogRequirementPreview.TierSection section, int x, int baseline)
    {
        String text = section.tier() == null ? "TIER" : tierLabel(section.tier()).toUpperCase();
        g.setColor(uiTextDim);
        g.drawString(truncateToWidth(text, fm, Math.max(0, tierLabelMaxWidth)), x, baseline);
    }

    private int drawSummaryText(Graphics2D g, FontMetrics fm, String summaryText, int x, int y, int maxWidth)
    {
        for (String line : summaryTextLines(summaryText))
        {
            drawSummaryLine(g, fm, line, x, y, maxWidth);
            y += rowHeight;
        }
        return y;
    }

    private void drawSummaryLine(Graphics2D g, FontMetrics fm, String text, int x, int y, int maxWidth)
    {
        if (isPendingOpenClogSummaryLine(text))
        {
            g.setColor(UiPalette.TIER_COMPLETE_GLOW);
            g.drawString(truncateToWidth(text, fm, maxWidth), x, y);
            return;
        }

        String separator = " | ";
        int separatorIndex = text.indexOf(separator);
        if (separatorIndex < 0)
        {
            g.setColor(uiTextDim);
            g.drawString(truncateToWidth(text, fm, maxWidth), x, y);
            return;
        }

        String prefix = text.substring(0, separatorIndex + separator.length());
        String suffix = text.substring(separatorIndex + separator.length());
        int prefixWidth = fm.stringWidth(prefix);
        if (prefixWidth >= maxWidth)
        {
            g.setColor(uiTextDim);
            g.drawString(truncateToWidth(text, fm, maxWidth), x, y);
            return;
        }

        g.setColor(uiTextDim);
        g.drawString(prefix, x, y);
        drawSummarySuffix(g, fm, suffix, x + prefixWidth, y, maxWidth - prefixWidth);
    }

    private void drawSummarySuffix(Graphics2D g, FontMetrics fm, String suffix, int x, int y, int maxWidth)
    {
        String currentTaskPrefix = "current task: ";
        if (!suffix.startsWith(currentTaskPrefix))
        {
            g.setColor(uiTextDim);
            g.drawString(truncateToWidth(suffix, fm, maxWidth), x, y);
            return;
        }

        int prefixWidth = fm.stringWidth(currentTaskPrefix);
        if (prefixWidth >= maxWidth)
        {
            g.setColor(uiTextDim);
            g.drawString(truncateToWidth(suffix, fm, maxWidth), x, y);
            return;
        }

        String progress = suffix.substring(currentTaskPrefix.length());
        g.setColor(uiTextDim);
        g.drawString(currentTaskPrefix, x, y);
        g.setColor(isProgressComplete(progress) ? UiPalette.TIER_COMPLETE_GLOW : uiTextDim);
        g.drawString(truncateToWidth(progress, fm, maxWidth - prefixWidth), x + prefixWidth, y);
    }

    private static int secondarySectionGap(CollectionLogRequirementPreview preview)
    {
        String title = preview == null ? "" : safe(preview.secondaryTitleText()).trim();
        return title.startsWith(MEDALLION_ASSEMBLY_TITLE_PREFIX)
                && title.toLowerCase().contains("fragments to assemble")
                ? MEDALLION_ASSEMBLY_SECTION_GAP
                : SECONDARY_SECTION_GAP;
    }

    private static int summaryTextLineCount(String summaryText)
    {
        return summaryTextLines(summaryText).size();
    }

    private static List<String> summaryTextLines(String summaryText)
    {
        String text = safe(summaryText);
        return text.isEmpty() ? List.of() : List.of(text.split("\\R"));
    }

    private static boolean isPendingOpenClogSummaryLine(String text)
    {
        return text != null && text.matches(UiText.get("clog.pending_summary.regex"));
    }

    private static boolean isProgressComplete(String progress)
    {
        if (progress == null)
        {
            return false;
        }

        String[] parts = progress.trim().split("/");
        if (parts.length != 2)
        {
            return false;
        }

        try
        {
            int current = Integer.parseInt(parts[0].trim());
            int required = Integer.parseInt(parts[1].trim());
            return required > 0 && current >= required;
        }
        catch (NumberFormatException e)
        {
            return false;
        }
    }

    private static String title(CollectionLogRequirementPreview preview)
    {
        if (preview != null && preview.titleText() != null && !preview.titleText().trim().isEmpty())
        {
            return preview.titleText();
        }
        return preview != null && preview.showSummaryText() && !preview.showItemList()
                ? "Collection Log Progress"
                : "Eligible Collection Log items";
    }
}
