package com.amtrollin.xtremetasker.ui.rules;

import com.amtrollin.xtremetasker.ui.text.TextUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static java.awt.Color.white;
import static com.amtrollin.xtremetasker.ui.style.UiPalette.withAlpha;

public final class RulesTabRenderer {
    private final int panelWidth;
    private final int panelPadding;
    private final int rowHeight;
    private final int listRowSpacing;

    private final Color uiGold;
    private final Color uiTextDim;

    private static final String TASKER_FAQ_URL =
            "https://docs.google.com/document/d/e/2PACX-1vTHfXHzMQFbt_iYAP-O88uRhhz3wigh1KMiiuomU7ftli-rL_c3bRqfGYmUliE1EHcIr3LfMx2UTf2U/pub";

    private static final String GITHUB_README_URL =
            "https://github.com/amtrollin/xtreme-tasker-plugin#readme";

    private static final String LINE_SYNC_PROGRESS_BUTTON_ROW = "[SYNC_PROGRESS_BUTTON_ROW]";
    private static final String LINE_SYNC_CA_FOUND_ACTIONS_ROW = "[SYNC_CA_FOUND_ACTIONS_ROW]";
    private static final String LINE_SYNC_RESULT_LABEL = "[SYNC_RESULT_LABEL]";
    private static final String LINE_SYNC_RESULT_FOUND_PREFIX = "[SYNC_RESULT_FOUND]";
    private static final String LINE_SYNC_RESULT_EMPTY_PREFIX = "[SYNC_RESULT_EMPTY]";
    private static final String LINE_SYNC_RESULT_ERROR_PREFIX = "[SYNC_RESULT_ERROR]";
    private static final String LINE_SYNC_TIMESTAMP_PREFIX = "[SYNC_TIMESTAMP]";
    private static final String LINE_SYNC_TIMESTAMP_DIVIDER = "[SYNC_TIMESTAMP_DIVIDER]";
    private static final String LINE_SYNC_HELPER_GOLD_PREFIX = "[SYNC_HELPER_GOLD]";
    private static final String LINE_SYNC_HELPER_DIM_PREFIX = "[SYNC_HELPER_DIM]";
    private static final String LINE_SYNC_BUTTON_TOP_SPACER = "[SYNC_BUTTON_TOP_SPACER]";
    private static final String LINE_SYNC_RESULT_TIGHT_SPACER = "[SYNC_RESULT_TIGHT_SPACER]";
    private static final String LINE_RULES_TOP_SPACER = "[RULES_TOP_SPACER]";
    private static final String REVIEW_NEEDED_TITLE = "Review needed";
    private static final String SYNC_HELPER_TEXT = "Sync account progress here.";
    private static final String CLOG_SYNC_HELPER_TEXT =
            "For CLOGs, open in game CLOG before syncing so plugin can see latest data.";
    private static final String SYNC_LIMITATION_HELPER_TEXT =
            "Note: syncs may not catch all tasks needing updates.";
    private static final BufferedImage REVIEW_NEEDED_ICON = loadReviewNeededIconSafe();
    private static final DateTimeFormatter SYNC_DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
    private static final DateTimeFormatter SYNC_TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a", Locale.US);
    private static final Color SYNC_FOUND_GREEN = new Color(111, 190, 92);
    private static final Color SYNC_ERROR_RED = new Color(198, 82, 70);
    private static final String FOUND_COMPLETIONS_BUTTON_LABEL = "Update tasks";
    private static final List<String> RULES_COPY_LINES = loadRulesCopyLinesSafe();

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
            String lastCollectionLogSyncResult,
            String lastCollectionLogSyncResultAtLocalTime,
            boolean collectionLogSyncPending,
            int combatAchievementFoundCount,
            int collectionLogFoundCount
    ) {
        RulesTabLayout layout = new RulesTabLayout();
        clearBounds(
                layout.taskerFaqLinkBounds, layout.githubReadmeLinkBounds, layout.reloadButtonBounds,
                layout.syncProgressButtonBounds, layout.syncCaFoundReviewButtonBounds,
                layout.syncCaReviewButtonBounds, layout.syncCaReviewIgnoreButtonBounds,
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
                    lastCollectionLogSyncResult,
                    lastCollectionLogSyncResultAtLocalTime,
                    collectionLogSyncPending,
                    combatAchievementFoundCount,
                    collectionLogFoundCount
            );
        }

        List<String> lines = buildRulesLines(fm, viewportW - 8);
        int rb = rowBlock();

        Shape oldClip = g.getClip();
        g.setClip(layout.viewportBounds);

        Font normalFont = g.getFont();

        int drawY = adjustedBaseline;

        for (String line : lines) {
            if (LINE_RULES_TOP_SPACER.equals(line)) {
                drawY += Math.max(3, rb / 3);
                continue;
            }

            // ---- spacing ----
            if (line.trim().isEmpty()) {
                drawY += rb;
                continue;
            }

            String markedLine = markedLineText(line);
            if (markedLine != null) {
                g.setColor(markedLineColor(line));
                g.setFont(normalFont);
                fm = g.getFontMetrics();
                String drawText = TextUtils.truncateToWidth(markedLine, fm, viewportW - 8);
                g.drawString(drawText, bx, drawY);
                drawY += rb;
                continue;
            }

            // Section titles within Rules copy
            boolean isRulesTitle = line.equals("Rules:");
            boolean isRuleSubheader = line.equals("Xtreme Tasker rules") || line.equals("Official Tasker rules");
            boolean isAllowanceHeader = line.equals("Boss combat training allowance");
            boolean isDataSubtitle = line.equals("Last sync result")
                    || line.equals("Combat Achievements sync")
                    || line.equals("Collection Logs + Achievement Diaries sync")
                    || line.equals(REVIEW_NEEDED_TITLE);

            // color + font
            if (isRulesTitle) {
                g.setColor(white);
                g.setFont(normalFont);
            } else if (isRuleSubheader) {
                g.setColor(uiGold);
                g.setFont(normalFont);
            } else if (isAllowanceHeader) {
                g.setColor(uiGold);
                g.setFont(normalFont);
            } else if (isDataSubtitle) {
                g.setColor(uiGold);
                g.setFont(normalFont);
            } else {
                g.setColor(uiTextDim);
                g.setFont(normalFont);
            }
            fm = g.getFontMetrics();

            if (line.equals(REVIEW_NEEDED_TITLE)) {
                drawReviewNeededTitle(g, fm, bx, drawY, viewportW);
            } else {
                drawRulesLineWithInlineLinks(g, fm, layout, line, bx, drawY, viewportW - 8);
            }

            g.setFont(normalFont);
            drawY += rb;
        }

        g.setClip(oldClip);
        g.setFont(normalFont);
        return layout;
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
            String lastCollectionLogSyncResult,
            String lastCollectionLogSyncResultAtLocalTime,
            boolean collectionLogSyncPending,
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
                lastCollectionLogSyncResult,
                lastCollectionLogSyncResultAtLocalTime,
                collectionLogSyncPending,
                collectionLogFoundCount,
                combatAchievementFoundCount
        );

        Shape oldClip = g.getClip();
        g.setClip(fullViewport);

        drawSyncColumn(g, fm, layout, lines, bx, viewportY + fm.getAscent(), contentW, 0, lines.size(), fullViewport);

        g.setClip(oldClip);
        layout.viewportBounds.setBounds(fullViewport);
        return layout;
    }

    private List<String> buildCombinedDataSyncLines(
            FontMetrics fm,
            int maxWidth,
            String lastCombatAchievementSyncResult,
            String lastCombatAchievementSyncResultAtLocalTime,
            String lastCollectionLogSyncResult,
            String lastCollectionLogSyncResultAtLocalTime,
            boolean collectionLogSyncPending,
            int collectionLogFoundCount,
            int combatAchievementFoundCount)
    {
        List<String> lines = new ArrayList<>();
        boolean hasCaResult = lastCombatAchievementSyncResult != null && !lastCombatAchievementSyncResult.trim().isEmpty();
        boolean hasClogResult = lastCollectionLogSyncResult != null && !lastCollectionLogSyncResult.trim().isEmpty();

        lines.addAll(prefixWrappedLines(LINE_SYNC_HELPER_GOLD_PREFIX, SYNC_HELPER_TEXT, fm, maxWidth));
        lines.addAll(prefixWrappedLines(LINE_SYNC_HELPER_DIM_PREFIX, CLOG_SYNC_HELPER_TEXT, fm, maxWidth));
        lines.addAll(prefixWrappedLines(LINE_SYNC_HELPER_DIM_PREFIX, SYNC_LIMITATION_HELPER_TEXT, fm, maxWidth));
        lines.add(LINE_SYNC_BUTTON_TOP_SPACER);
        lines.add(LINE_SYNC_PROGRESS_BUTTON_ROW);
        if (collectionLogSyncPending)
        {
            addSyncPendingLines(lines, fm, maxWidth);
        }
        else if (hasCaResult || hasClogResult)
        {
            int totalFound = combatAchievementFoundCount + collectionLogFoundCount;
            String latestTime = hasCaResult ? lastCombatAchievementSyncResultAtLocalTime : lastCollectionLogSyncResultAtLocalTime;
            addSyncTimestampLine(lines, "Last sync", latestTime, fm, maxWidth);
            lines.add(LINE_SYNC_TIMESTAMP_DIVIDER);
            lines.add(LINE_SYNC_RESULT_TIGHT_SPACER);
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
                drawY += Math.max(4, rb / 3);
                continue;
            }

            if (LINE_SYNC_PROGRESS_BUTTON_ROW.equals(line))
            {
                int btnW = Math.min(colW - 8, Math.max(164, fm.stringWidth("SYNC") + 72));
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

            if (LINE_SYNC_TIMESTAMP_DIVIDER.equals(line))
            {
                int dividerW = Math.max(40, colW / 2);
                int dividerX = x + Math.max(0, (colW - dividerW) / 2);
                int dividerY = drawY - rb + fm.getDescent() + 8;
                g.setColor(withAlpha(uiGold, 105));
                g.drawLine(dividerX, dividerY, dividerX + dividerW, dividerY);
                drawY += Math.max(4, rb / 3);
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

            if (LINE_SYNC_RESULT_TIGHT_SPACER.equals(line))
            {
                drawY += Math.max(5, rb / 2);
                continue;
            }

            if (line.trim().isEmpty())
            {
                drawY += rb;
                continue;
            }

            String markedLine = markedLineText(line);
            if (markedLine != null)
            {
                g.setColor(markedLineColor(line));
                drawCenteredText(g, fm, markedLine, x, drawY, colW, 8);
                drawY += rb;
                continue;
            }

            boolean isTitle = line.equals("Combat Achievements sync")
                    || line.equals("Collection Logs + Achievement Diaries sync")
                    || line.equals(REVIEW_NEEDED_TITLE);
            if (isTitle)
            {
                g.setColor(uiGold);
                g.setFont(normalFont);
                fm = g.getFontMetrics();
                if (line.equals(REVIEW_NEEDED_TITLE))
                {
                    int textW = fm.stringWidth(REVIEW_NEEDED_TITLE);
                    drawReviewNeededTitle(g, fm, x + Math.max(0, (colW - textW) / 2) - 14, drawY, colW);
                }
                else
                {
                    drawCenteredText(g, fm, line, x, drawY, colW, 8);
                }
                g.setFont(normalFont);
                fm = g.getFontMetrics();
            }
            else
            {
                g.setColor(uiTextDim);
                drawCenteredText(g, fm, line, x, drawY, colW, 8);
            }
            drawY += rb;
        }

        g.setFont(normalFont);
    }

    private static boolean buttonFitsViewport(int y, int height, Rectangle viewport)
    {
        return viewport == null || (y >= viewport.y && y + height <= viewport.y + viewport.height);
    }

    private void drawRulesLineWithInlineLinks(
            Graphics2D g,
            FontMetrics fm,
            RulesTabLayout layout,
            String line,
            int x,
            int baseline,
            int maxWidth)
    {
        String linkText = null;
        Rectangle linkBounds = null;
        if (line.contains("GitHub README"))
        {
            linkText = "GitHub README";
            linkBounds = layout.githubReadmeLinkBounds;
        }
        else if (line.contains("TaskerFAQ"))
        {
            linkText = "TaskerFAQ";
            linkBounds = layout.taskerFaqLinkBounds;
        }

        if (linkText == null)
        {
            String drawText = TextUtils.truncateToWidth(line, fm, maxWidth);
            g.drawString(drawText, x, baseline);
            return;
        }

        int linkStart = line.indexOf(linkText);
        String before = line.substring(0, linkStart);
        String after = line.substring(linkStart + linkText.length());
        int cursorX = x;

        g.setColor(uiTextDim);
        g.drawString(before, cursorX, baseline);
        cursorX += fm.stringWidth(before);

        g.setColor(white);
        g.drawString(linkText, cursorX, baseline);
        int linkW = fm.stringWidth(linkText);
        linkBounds.setBounds(cursorX, baseline - fm.getAscent(), linkW, fm.getHeight());
        g.setColor(withAlpha(uiGold, 180));
        g.drawLine(cursorX, baseline + 2, cursorX + linkW, baseline + 2);
        cursorX += linkW;

        g.setColor(uiTextDim);
        g.drawString(TextUtils.truncateToWidth(after, fm, Math.max(0, maxWidth - (cursorX - x))), cursorX, baseline);
    }

    private List<String> buildRulesLines(FontMetrics fm, int maxWidth) {
        List<String> lines = new ArrayList<>();
        lines.add(LINE_RULES_TOP_SPACER);
        for (String rawLine : RULES_COPY_LINES)
        {
            String line = rawLine.trim();
            if (line.isEmpty())
            {
                lines.add("");
            }
            else
            {
                lines.addAll(TextUtils.wrapText(line, fm, maxWidth));
            }
        }
        return lines;
    }

    private void addSyncPendingLines(List<String> lines, FontMetrics fm, int maxWidth)
    {
        lines.add(LINE_SYNC_RESULT_LABEL + "Result:");
        lines.add(LINE_SYNC_RESULT_EMPTY_PREFIX + "....");
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
                ? "Sync found " + foundCount + " new " + completionNoun + "!"
                : "Sync did not find new task completions";
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
        if (line.startsWith(LINE_SYNC_RESULT_LABEL))
        {
            return line.substring(LINE_SYNC_RESULT_LABEL.length());
        }
        if (line.startsWith(LINE_SYNC_RESULT_FOUND_PREFIX))
        {
            return line.substring(LINE_SYNC_RESULT_FOUND_PREFIX.length());
        }
        if (line.startsWith(LINE_SYNC_RESULT_EMPTY_PREFIX))
        {
            return line.substring(LINE_SYNC_RESULT_EMPTY_PREFIX.length());
        }
        if (line.startsWith(LINE_SYNC_RESULT_ERROR_PREFIX))
        {
            return line.substring(LINE_SYNC_RESULT_ERROR_PREFIX.length());
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
        if (line.startsWith(LINE_SYNC_RESULT_LABEL))
        {
            return white;
        }
        if (line.startsWith(LINE_SYNC_RESULT_FOUND_PREFIX))
        {
            return SYNC_FOUND_GREEN;
        }
        if (line.startsWith(LINE_SYNC_RESULT_ERROR_PREFIX))
        {
            return SYNC_ERROR_RED;
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

    private void drawReviewNeededTitle(Graphics2D g, FontMetrics fm, int x, int baseline, int maxWidth) {
        int textX = x;
        if (REVIEW_NEEDED_ICON != null)
        {
            int iconSize = Math.min(18, Math.max(12, rowHeight + 2));
            int iconY = baseline - fm.getAscent() + ((fm.getHeight() - iconSize) / 2);
            g.drawImage(REVIEW_NEEDED_ICON, x, iconY, iconSize, iconSize, null);
            textX = x + iconSize + 5;
        }

        String drawText = TextUtils.truncateToWidth(REVIEW_NEEDED_TITLE, fm, Math.max(0, maxWidth - (textX - x) - 8));
        g.drawString(drawText, textX, baseline + 2);
    }

    private static BufferedImage loadReviewNeededIconSafe() {
        try (InputStream in = RulesTabRenderer.class.getResourceAsStream("/icons/notifications/OSRS_exclamation.png"))
        {
            return in == null ? null : ImageIO.read(in);
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    private static List<String> loadRulesCopyLinesSafe()
    {
        try (InputStream in = RulesTabRenderer.class.getResourceAsStream("/ui/rules.txt"))
        {
            if (in == null)
            {
                return List.of("Rules:", "Rules text unavailable.");
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
            String[] lines = text.split("\n", -1);
            List<String> out = new ArrayList<>(lines.length);
            for (String line : lines)
            {
                out.add(line);
            }
            return out;
        }
        catch (Exception ignored)
        {
            return List.of("Rules:", "Rules text unavailable.");
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

    // Expose URL so overlay can use it for clicks without duplicating string
    public static String taskerFaqUrl() {
        return TASKER_FAQ_URL;
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

}
