package com.amtrollin.xtremetasker.ui.rules;

import com.amtrollin.xtremetasker.ui.text.*;
import java.awt.*;
import java.time.*;
import java.time.format.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.runelite.client.ui.FontManager;
import static com.amtrollin.xtremetasker.ui.style.UiPalette.withAlpha;
import static java.awt.Color.white;

public final class RulesTabRenderer {
    private final int panelWidth;
    private final int panelPadding;
    private final int rowHeight;
    private final int listRowSpacing;

    private final Color uiGold;
    private final Color uiTextDim;

    private static final String GITHUB_README_URL =
            "https://github.com/AmTrollin/xtreme-tasker-osrs/blob/master/docs/RULES.md";
    private static final String RULES_TITLE = "Rules";
    private static final String RULES_SUMMARY_LINK_PREFIX = "See the ";
    private static final String RULES_SUMMARY_LINK_TEXT = "Xtreme Tasker Rules GitHub README";
    private static final String RULES_SUMMARY_SUFFIX = ".";

    private static final String LINE_SYNC_PROGRESS_BUTTON_ROW = "[SYNC_PROGRESS_BUTTON_ROW]";
    private static final String LINE_SYNC_CA_FOUND_ACTIONS_ROW = "[SYNC_CA_FOUND_ACTIONS_ROW]";
    private static final String LINE_SYNC_RESULT_FOUND_PREFIX = "[SYNC_RESULT_FOUND]";
    private static final String LINE_SYNC_RESULT_EMPTY_PREFIX = "[SYNC_RESULT_EMPTY]";
    private static final String LINE_SYNC_TIMESTAMP_PREFIX = "[SYNC_TIMESTAMP]";
    private static final String LINE_SYNC_HELPER_GOLD_PREFIX = "[SYNC_HELPER_GOLD]";
    private static final String LINE_SYNC_HELPER_DIM_PREFIX = "[SYNC_HELPER_DIM]";
    private static final String LINE_SYNC_BUTTON_TOP_SPACER = "[SYNC_BUTTON_TOP_SPACER]";
    private static final String SYNC_TITLE_TEXT = "Sync & Review Task Completions";
    private static final DateTimeFormatter SYNC_DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
    private static final DateTimeFormatter SYNC_TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a", Locale.US);
    private static final Color SYNC_FOUND_GREEN = new Color(111, 190, 92);
    private static final String FOUND_COMPLETIONS_BUTTON_LABEL = "Update tasks";

    public RulesTabRenderer(
            int panelWidth,
            int panelPadding,
            int rowHeight,
            int listRowSpacing,
            Color uiGold,
            Color uiTextDim
    ) {
        this.panelWidth = panelWidth;
        this.panelPadding = panelPadding;
        this.rowHeight = rowHeight;
        this.listRowSpacing = listRowSpacing;
        this.uiGold = uiGold;
        this.uiTextDim = uiTextDim;
    }

    public int rowBlock() {
        return rowHeight + listRowSpacing;
    }

    public RulesTabLayout render(
            Graphics2D g,
            FontMetrics fm,
            int panelX,
            int cursorYBaseline,
            Rectangle panelBounds,
            RulesTabLayout.SubTab activeSubTab,
            String lastCombatAchievementSyncResult,
            String lastCombatAchievementSyncResultAtLocalTime,
            int combatAchievementFoundCount,
            int collectionLogFoundCount
    ) {
        RulesTabLayout layout = new RulesTabLayout();
        clearBounds(
                layout.githubReadmeLinkBounds,
                layout.syncProgressButtonBounds, layout.syncCaFoundReviewButtonBounds,
                layout.subTabRulesBounds, layout.subTabDataSyncsBounds
        );
        int bx = panelX + panelPadding;
        int viewportW = panelWidth - 2 * panelPadding;

        int navTop = cursorYBaseline - fm.getAscent() + 2;
        int navH = rowHeight + 8;
        int navW = Math.min(viewportW / 2, Math.max(220, fm.stringWidth("Rules") + fm.stringWidth("Sync") + 90));
        int navX = bx + (viewportW - navW) / 2;
        int navLeftW = navW / 2;
        layout.subTabRulesBounds.setBounds(navX, navTop, navLeftW, navH);
        layout.subTabDataSyncsBounds.setBounds(navX + navLeftW, navTop, navW - navLeftW, navH);
        drawSegmentedControl(
                g,
                fm,
                layout.subTabRulesBounds,
                "Rules",
                layout.subTabDataSyncsBounds,
                "Sync",
                activeSubTab != RulesTabLayout.SubTab.DATA_SYNCS
        );

        int viewportY = navTop + navH + 12;
        int viewportH = (panelBounds.y + panelBounds.height) - viewportY - panelPadding;
        if (viewportH < 0) viewportH = 0;

        layout.viewportBounds.setBounds(bx, viewportY, viewportW, viewportH);

        // Offset the cursor baseline to match the new viewportY
        int adjustedBaseline = viewportY + fm.getAscent();

        if (activeSubTab == RulesTabLayout.SubTab.DATA_SYNCS)
        {
            return renderDataSyncColumns(
                    g,
                    fm,
                    layout,
                    bx,
                    viewportY,
                    viewportW,
                    viewportH,
                    lastCombatAchievementSyncResult,
                    lastCombatAchievementSyncResultAtLocalTime,
                    combatAchievementFoundCount,
                    collectionLogFoundCount
            );
        }

        renderRulesSummary(g, fm, layout, bx, adjustedBaseline, viewportW);
        return layout;
    }

    private void renderRulesSummary(
            Graphics2D g,
            FontMetrics fm,
            RulesTabLayout layout,
            int bx,
            int firstBaselineY,
            int viewportW)
    {
        int rb = rowBlock();
        Shape oldClip = g.getClip();
        g.setClip(layout.viewportBounds);
        Font normalFont = g.getFont();
        Font titleFont = titleFont();

        int drawY = firstBaselineY + Math.max(3, rb / 3);
        g.setFont(titleFont);
        FontMetrics titleFm = g.getFontMetrics();
        g.setColor(uiGold);
        drawCenteredText(g, titleFm, RULES_TITLE, bx, drawY, viewportW, 8);

        drawY += rb;
        g.setFont(normalFont);
        drawRulesSummarySentence(g, fm, layout, bx, drawY, viewportW - 8);

        g.setClip(oldClip);
        g.setFont(normalFont);
    }

    private RulesTabLayout renderDataSyncColumns(
            Graphics2D g,
            FontMetrics fm,
            RulesTabLayout layout,
            int bx,
            int viewportY,
            int viewportW,
            int viewportH,
            String lastCombatAchievementSyncResult,
            String lastCombatAchievementSyncResultAtLocalTime,
            int combatAchievementFoundCount,
            int collectionLogFoundCount
    )
    {
        int contentW = Math.max(120, viewportW);
        Rectangle fullViewport = new Rectangle(bx, viewportY, contentW, viewportH);

        List<String> lines = buildCombinedDataSyncLines(
                fm,
                contentW - 8,
                lastCombatAchievementSyncResult,
                lastCombatAchievementSyncResultAtLocalTime,
                collectionLogFoundCount,
                combatAchievementFoundCount
        );

        Shape oldClip = g.getClip();
        g.setClip(fullViewport);

        int firstBaselineY = viewportY + fm.getAscent() + Math.max(3, rowBlock() / 3);
        drawSyncColumn(g, fm, layout, lines, bx, firstBaselineY, contentW, 0, lines.size(), fullViewport);

        g.setClip(oldClip);
        layout.viewportBounds.setBounds(fullViewport);
        return layout;
    }

    private List<String> buildCombinedDataSyncLines(
            FontMetrics fm,
            int maxWidth,
            String lastCombatAchievementSyncResult,
            String lastCombatAchievementSyncResultAtLocalTime,
            int collectionLogFoundCount,
            int combatAchievementFoundCount)
    {
        List<String> lines = new ArrayList<>();
        boolean hasCaResult = lastCombatAchievementSyncResult != null && !lastCombatAchievementSyncResult.trim().isEmpty();

        lines.add(LINE_SYNC_HELPER_GOLD_PREFIX + SYNC_TITLE_TEXT);
        lines.addAll(prefixWrappedLines(LINE_SYNC_HELPER_DIM_PREFIX, UiText.get("rules.sync.description"), fm, maxWidth));
        lines.add("");
        lines.addAll(prefixWrappedLines(LINE_SYNC_HELPER_DIM_PREFIX, UiText.get("rules.sync.clog_helper"), fm, maxWidth));
        lines.addAll(prefixWrappedLines(LINE_SYNC_HELPER_DIM_PREFIX, UiText.get("rules.sync.limitation"), fm, maxWidth));
        lines.add(LINE_SYNC_BUTTON_TOP_SPACER);
        lines.add(LINE_SYNC_PROGRESS_BUTTON_ROW);
        if (hasCaResult)
        {
            int totalFound = combatAchievementFoundCount + collectionLogFoundCount;
            addSyncTimestampLine(lines, "Last sync", lastCombatAchievementSyncResultAtLocalTime, fm, maxWidth);
            addSyncResultStatusMessageLines(lines, totalFound, fm, maxWidth);
        }
        int totalFound = combatAchievementFoundCount + collectionLogFoundCount;
        if (totalFound > 0)
        {
            lines.add(LINE_SYNC_CA_FOUND_ACTIONS_ROW);
        }
        lines.add("");
        return lines;
    }

    private void drawSyncColumn(
            Graphics2D g,
            FontMetrics fm,
            RulesTabLayout layout,
            List<String> lines,
            int x,
            int firstBaselineY,
            int colW,
            int start,
            int end,
            Rectangle viewport
    )
    {
        Font normalFont = g.getFont();
        int rb = rowBlock();
        int drawY = firstBaselineY;
        int safeEnd = Math.min(lines.size(), end);

        for (int idx = start; idx < safeEnd; idx++)
        {
            String line = lines.get(idx);
            if (LINE_SYNC_BUTTON_TOP_SPACER.equals(line))
            {
                drawY += Math.max(10, rb / 2);
                continue;
            }

            if (LINE_SYNC_PROGRESS_BUTTON_ROW.equals(line))
            {
                int btnW = Math.min(colW - 8, Math.max(164, fm.stringWidth("Sync CAs + ADs") + 72));
                int btnH = rowHeight + 14;
                int btnX = x + Math.max(0, (colW - btnW) / 2);
                int by = drawY - fm.getAscent();
                boolean visible = buttonFitsViewport(by, btnH, viewport);
                if (visible)
                {
                    layout.syncProgressButtonBounds.setBounds(btnX, by, btnW, btnH);
                }
                drawY += Math.max(rb, btnH + listRowSpacing) + 3;
                continue;
            }

            if (LINE_SYNC_CA_FOUND_ACTIONS_ROW.equals(line))
            {
                int reviewW = Math.max(fm.stringWidth(FOUND_COMPLETIONS_BUTTON_LABEL) + 18, 128);
                int btnH = rowHeight + 10;
                int by = drawY - fm.getAscent();
                int groupX = x + Math.max(0, (colW - reviewW) / 2);
                if (!buttonFitsViewport(by, btnH, viewport))
                {
                    drawY += Math.max(rb, btnH + listRowSpacing);
                    continue;
                }
                layout.syncCaFoundReviewButtonBounds.setBounds(groupX, by, reviewW, btnH);
                drawY += Math.max(rb, btnH + listRowSpacing);
                continue;
            }

            if (line.trim().isEmpty())
            {
                drawY += rb;
                continue;
            }

            if (line.startsWith(LINE_SYNC_TIMESTAMP_PREFIX))
            {
                drawCenteredLabeledText(
                        g,
                        fm,
                        line.substring(LINE_SYNC_TIMESTAMP_PREFIX.length()),
                        "Last sync:",
                        uiTextDim,
                        x,
                        drawY,
                        colW,
                        8
                );
                drawY += rb;
                continue;
            }

            if (line.startsWith(LINE_SYNC_RESULT_FOUND_PREFIX)
                    || line.startsWith(LINE_SYNC_RESULT_EMPTY_PREFIX))
            {
                drawCenteredLabeledText(g, fm, markedLineText(line), "Result:", markedLineColor(line), x, drawY, colW, 8);
                drawY += rb;
                continue;
            }

            String markedLine = markedLineText(line);
            if (markedLine != null)
            {
                if (SYNC_TITLE_TEXT.equals(markedLine))
                {
                    Font titleFont = titleFont();
                    g.setFont(titleFont);
                    FontMetrics titleFm = g.getFontMetrics();
                    g.setColor(markedLineColor(line));
                    drawCenteredText(g, titleFm, markedLine, x, drawY, colW, 8);
                    g.setFont(normalFont);
                    fm = g.getFontMetrics();
                    drawY += rb;
                    continue;
                }

                g.setColor(markedLineColor(line));
                drawCenteredText(g, fm, markedLine, x, drawY, colW, 8);
                drawY += rb;
                continue;
            }

            g.setColor(uiTextDim);
            drawCenteredText(g, fm, line, x, drawY, colW, 8);
            drawY += rb;
        }

        g.setFont(normalFont);
    }

    private static boolean buttonFitsViewport(int y, int height, Rectangle viewport)
    {
        return viewport == null || (y >= viewport.y && y + height <= viewport.y + viewport.height);
    }

    private void drawRulesSummarySentence(
            Graphics2D g,
            FontMetrics fm,
            RulesTabLayout layout,
            int x,
            int baseline,
            int maxWidth)
    {
        int lineY = baseline;
        int suffixW = fm.stringWidth(RULES_SUMMARY_SUFFIX);

        g.setColor(uiTextDim);
        drawCenteredText(g, fm, UiText.get("rules.summary.intro"), x, lineY, maxWidth, 0);

        lineY += rowBlock();
        String prefix = TextUtils.truncateToWidth(RULES_SUMMARY_LINK_PREFIX, fm, maxWidth);
        String linkText = TextUtils.truncateToWidth(
                RULES_SUMMARY_LINK_TEXT,
                fm,
                Math.max(0, maxWidth - fm.stringWidth(prefix) - suffixW)
        );
        int totalW = fm.stringWidth(prefix) + fm.stringWidth(linkText) + suffixW;
        int cursorX = x + Math.max(0, (maxWidth - totalW) / 2);

        g.setColor(uiTextDim);
        g.drawString(prefix, cursorX, lineY);
        cursorX += fm.stringWidth(prefix);

        g.setColor(white);
        g.drawString(linkText, cursorX, lineY);
        int linkW = fm.stringWidth(linkText);
        layout.githubReadmeLinkBounds.setBounds(cursorX, lineY - fm.getAscent(), linkW, fm.getHeight());
        g.setColor(withAlpha(uiGold, 180));
        g.drawLine(cursorX, lineY + 2, cursorX + linkW, lineY + 2);
        cursorX += linkW;

        g.setColor(uiTextDim);
        if (cursorX - x + suffixW <= maxWidth)
        {
            g.drawString(RULES_SUMMARY_SUFFIX, cursorX, lineY);
        }
    }

    private void addSyncTimestampLine(
            List<String> lines,
            String label,
            String localTime,
            FontMetrics fm,
            int maxWidth)
    {
        String time = formatSyncTimestamp(localTime);
        if (time != null && !time.trim().isEmpty())
        {
            lines.addAll(prefixWrappedLines(LINE_SYNC_TIMESTAMP_PREFIX, label + ": " + time.trim(), fm, maxWidth));
        }
    }

    private void addSyncResultStatusMessageLines(List<String> lines, int foundCount, FontMetrics fm, int maxWidth)
    {
        String prefix = foundCount > 0 ? LINE_SYNC_RESULT_FOUND_PREFIX : LINE_SYNC_RESULT_EMPTY_PREFIX;
        String completionNoun = foundCount == 1 ? "task completion" : "task completions";
        String message = foundCount > 0
                ? UiText.format("rules.sync.result_found", foundCount, completionNoun)
                : UiText.get("rules.sync.result_empty");
        lines.addAll(prefixWrappedLines(prefix, message, fm, maxWidth));
    }

    private List<String> prefixWrappedLines(String prefix, String text, FontMetrics fm, int maxWidth)
    {
        List<String> prefixed = new ArrayList<>();
        for (String line : TextUtils.wrapText(text, fm, maxWidth))
        {
            prefixed.add(prefix + line);
        }
        return prefixed;
    }

    private String markedLineText(String line)
    {
        if (line.startsWith(LINE_SYNC_RESULT_FOUND_PREFIX))
        {
            return line.substring(LINE_SYNC_RESULT_FOUND_PREFIX.length());
        }
        if (line.startsWith(LINE_SYNC_RESULT_EMPTY_PREFIX))
        {
            return line.substring(LINE_SYNC_RESULT_EMPTY_PREFIX.length());
        }
        if (line.startsWith(LINE_SYNC_TIMESTAMP_PREFIX))
        {
            return line.substring(LINE_SYNC_TIMESTAMP_PREFIX.length());
        }
        if (line.startsWith(LINE_SYNC_HELPER_GOLD_PREFIX))
        {
            return line.substring(LINE_SYNC_HELPER_GOLD_PREFIX.length());
        }
        if (line.startsWith(LINE_SYNC_HELPER_DIM_PREFIX))
        {
            return line.substring(LINE_SYNC_HELPER_DIM_PREFIX.length());
        }
        return null;
    }

    private Color markedLineColor(String line)
    {
        if (line.startsWith(LINE_SYNC_RESULT_FOUND_PREFIX))
        {
            return SYNC_FOUND_GREEN;
        }
        if (line.startsWith(LINE_SYNC_HELPER_GOLD_PREFIX))
        {
            return uiGold;
        }
        return uiTextDim;
    }

    private String cleanTimestamp(String timestamp) {
        if (timestamp == null)
        {
            return "";
        }

        return timestamp.trim()
                .replace('T', ' ')
                .replaceFirst("\\.\\d{1,9}(?=([+-]\\d{2}:\\d{2}|Z)$)", "")
                .replaceFirst("([+-]\\d{2}:\\d{2}|Z)$", "");
    }

    private String formatSyncTimestamp(String timestamp)
    {
        if (timestamp == null || timestamp.trim().isEmpty())
        {
            return "";
        }

        String value = timestamp.trim();
        try
        {
            OffsetDateTime time = OffsetDateTime.parse(value);
            return SYNC_DATE_FORMATTER.format(time) + " at " + SYNC_TIME_FORMATTER.format(time);
        }
        catch (DateTimeParseException ignored)
        {
            // Try the other timestamp shapes we've persisted before falling back to a cleaned string.
        }

        try
        {
            ZonedDateTime time = ZonedDateTime.parse(value);
            return SYNC_DATE_FORMATTER.format(time) + " at " + SYNC_TIME_FORMATTER.format(time);
        }
        catch (DateTimeParseException ignored)
        {
            // Fall through.
        }

        try
        {
            LocalDateTime time = LocalDateTime.parse(value.replace(' ', 'T'));
            return SYNC_DATE_FORMATTER.format(time) + " at " + SYNC_TIME_FORMATTER.format(time);
        }
        catch (DateTimeParseException ignored)
        {
            return cleanTimestamp(value);
        }
    }

    /** Draws a two-segment connected control. leftActive=true highlights the left segment. */
    private void drawSegmentedControl(Graphics2D g, FontMetrics fm,
                                      Rectangle left, String leftLabel,
                                      Rectangle right, String rightLabel,
                                      boolean leftActive) {
        int x = left.x;
        int y = left.y;
        int totalW = left.width + right.width;
        int h = left.height;

        // Outer border (gold, single rect around both)
        Color borderColor = withAlpha(uiGold, 160);
        Color activeBg   = withAlpha(uiGold, 55);
        Color inactiveBg = new Color(20, 16, 10, 210);
        Color divider    = withAlpha(uiGold, 80);

        // Fill segments
        g.setColor(leftActive ? activeBg : inactiveBg);
        g.fillRect(left.x, y, left.width, h);
        g.setColor(leftActive ? inactiveBg : activeBg);
        g.fillRect(right.x, y, right.width, h);

        // Divider between segments
        g.setColor(divider);
        g.drawLine(right.x, y + 2, right.x, y + h - 2);

        // Outer border
        g.setColor(borderColor);
        g.drawRect(x, y, totalW, h);

        // Active segment bottom accent line
        Color accentColor = withAlpha(uiGold, 200);
        if (leftActive) {
            g.setColor(accentColor);
            g.drawLine(left.x + 1, y + h, left.x + left.width - 1, y + h);
        } else {
            g.setColor(accentColor);
            g.drawLine(right.x + 1, y + h, right.x + right.width - 1, y + h);
        }

        // Labels
        for (int i = 0; i < 2; i++) {
            boolean active = (i == 0) == leftActive;
            Rectangle seg = (i == 0) ? left : right;
            String label = (i == 0) ? leftLabel : rightLabel;
            String trunc = TextUtils.truncateToWidth(label, fm, seg.width - 8);
            int tx = seg.x + (seg.width - fm.stringWidth(trunc)) / 2;
            int ty = seg.y + ((seg.height - fm.getHeight()) / 2) + fm.getAscent();
            g.setColor(active ? white : new Color(200, 190, 160, 130));
            g.drawString(trunc, tx, ty);
        }
    }

    public static String githubReadmeUrl() {
        return GITHUB_README_URL;
    }

    private static void clearBounds(Rectangle... bounds)
    {
        for (Rectangle bound : bounds)
        {
            bound.setBounds(0, 0, 0, 0);
        }
    }

    private static void drawCenteredText(Graphics2D g, FontMetrics fm, String text, int x, int baseline, int width, int pad)
    {
        String drawText = TextUtils.truncateToWidth(text, fm, Math.max(0, width - pad));
        g.drawString(drawText, x + Math.max(0, (width - fm.stringWidth(drawText)) / 2), baseline);
    }

    private static void drawCenteredLabeledText(
            Graphics2D g,
            FontMetrics fm,
            String text,
            String label,
            Color valueColor,
            int x,
            int baseline,
            int width,
            int pad)
    {
        String drawText = TextUtils.truncateToWidth(text, fm, Math.max(0, width - pad));
        int textX = x + Math.max(0, (width - fm.stringWidth(drawText)) / 2);
        if (!drawText.startsWith(label))
        {
            g.setColor(valueColor);
            g.drawString(drawText, textX, baseline);
            return;
        }

        g.setColor(white);
        g.drawString(label, textX, baseline);
        g.setColor(valueColor);
        g.drawString(drawText.substring(label.length()), textX + fm.stringWidth(label), baseline);
    }

    private static Font titleFont()
    {
        return FontManager.getRunescapeFont();
    }

}
