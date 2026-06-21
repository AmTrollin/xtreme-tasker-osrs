package com.amtrollin.xtremetasker.ui.rules;

import com.amtrollin.xtremetasker.ui.text.TextUtils;
import net.runelite.client.ui.FontManager;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static java.awt.Color.white;

public final class RulesTabRenderer {
    private final int panelWidth;
    private final int panelPadding;
    private final int rowHeight;
    private final int listRowSpacing;

    private final Color uiGold;
    private final Color uiTextDim;
    private static final int SYNC_SCROLLBAR_CONTENT_GUTTER = 12;

    private static final String TASKER_FAQ_URL =
            "https://docs.google.com/document/d/e/2PACX-1vTHfXHzMQFbt_iYAP-O88uRhhz3wigh1KMiiuomU7ftli-rL_c3bRqfGYmUliE1EHcIr3LfMx2UTf2U/pub";

    private static final String GITHUB_README_URL =
            "https://github.com/amtrollin/xtreme-tasker-plugin#readme";

    private static final String LINE_TASKER_FAQ_BUTTON = "[TASKER_FAQ_BUTTON]";
    private static final String LINE_GITHUB_README_BUTTON = "[GITHUB_README_BUTTON]";
    private static final String LINE_SYNC_CA_BUTTON_ROW = "[SYNC_CA_BUTTON_ROW]";
    private static final String LINE_SYNC_CLOG_BUTTON_ROW = "[SYNC_CLOG_BUTTON_ROW]";
    private static final String LINE_SYNC_CA_FOUND_ACTIONS_ROW = "[SYNC_CA_FOUND_ACTIONS_ROW]";
    private static final String LINE_SYNC_CLOG_FOUND_ACTIONS_ROW = "[SYNC_CLOG_FOUND_ACTIONS_ROW]";
    private static final String LINE_SYNC_CA_REVIEW_ACTIONS_ROW = "[SYNC_CA_REVIEW_ACTIONS_ROW]";
    private static final String LINE_SYNC_CLOG_REVIEW_ACTIONS_ROW = "[SYNC_CLOG_REVIEW_ACTIONS_ROW]";
    private static final String LINE_SYNC_CA_TEST_ACTIONS_ROW = "[SYNC_CA_TEST_ACTIONS_ROW]";
    private static final String LINE_SYNC_CLOG_TEST_ACTIONS_ROW = "[SYNC_CLOG_TEST_ACTIONS_ROW]";
    private static final String LINE_SYNC_CA_MARKED_TOGGLE_PREFIX = "[SYNC_CA_MARKED_TOGGLE]";
    private static final String LINE_SYNC_CLOG_MARKED_TOGGLE_PREFIX = "[SYNC_CLOG_MARKED_TOGGLE]";
    private static final String LINE_SYNC_RESULT_LABEL = "[SYNC_RESULT_LABEL]";
    private static final String LINE_SYNC_RESULT_FOUND_PREFIX = "[SYNC_RESULT_FOUND]";
    private static final String LINE_SYNC_RESULT_EMPTY_PREFIX = "[SYNC_RESULT_EMPTY]";
    private static final String LINE_SYNC_RESULT_ERROR_PREFIX = "[SYNC_RESULT_ERROR]";
    private static final String LINE_SYNC_TIMESTAMP_PREFIX = "[SYNC_TIMESTAMP]";
    private static final String LINE_SYNC_FOUND_REVIEW_DIVIDER = "[SYNC_FOUND_REVIEW_DIVIDER]";
    private static final String LINE_SYNC_SECTION_DIVIDER = "[SYNC_SECTION_DIVIDER]";
    private static final String LINE_DATA_SYNC_TITLE = "[DATA_SYNC_TITLE]";
    private static final String REVIEW_NEEDED_TITLE = "Review needed";
    private static final String SYNC_HELPER_TEXT =
            "Use these buttons to detect tasks you've already completed. Progress sync is separate from task list updates.";
    private static final String CLOG_SYNC_HELPER_TEXT =
            "\n\nOpen your Collection Log in game before syncing so RuneLite can update your latest Collection Log progress. \nCombat Achievement and Achievement Diary progress are synced automatically.";
    private static final BufferedImage REVIEW_NEEDED_ICON = loadReviewNeededIconSafe();
    private static final DateTimeFormatter SYNC_DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
    private static final DateTimeFormatter SYNC_TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a", Locale.US);
    private static final Color SYNC_FOUND_GREEN = new Color(111, 190, 92);
    private static final Color SYNC_ERROR_RED = new Color(198, 82, 70);
    private static final String FOUND_COMPLETIONS_BUTTON_LABEL = "Update tasks";
    private static final String FOUND_COMPLETIONS_HELPER =
            "Tasks found via sync are not marked completed automatically. Click \"Update tasks\" to see + update these tasks.";

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
            int scrollOffsetRows,
            RulesTabLayout.SubTab activeSubTab,
            String lastCombatAchievementSyncResult,
            String lastCombatAchievementSyncResultAtLocalTime,
            String lastCollectionLogSyncResult,
            String lastCollectionLogSyncResultAtLocalTime,
                List<String> lastCombatAchievementSyncedTaskNames,
                List<String> lastCollectionLogSyncedTaskNames,
                boolean showCombatAchievementSyncedTaskNames,
                boolean showCollectionLogSyncedTaskNames,
            boolean collectionLogSyncPending,
            int combatAchievementFoundCount,
            int collectionLogFoundCount,
            int combatAchievementReviewCount,
            int collectionLogReviewCount,
            boolean showSyncTestTools
    ) {
        RulesTabLayout layout = new RulesTabLayout();
        layout.taskerFaqLinkBounds.setBounds(0, 0, 0, 0);
        layout.reloadButtonBounds.setBounds(0, 0, 0, 0);
        layout.syncClogsButtonBounds.setBounds(0, 0, 0, 0);
        layout.syncCAsButtonBounds.setBounds(0, 0, 0, 0);
        layout.syncCaFoundReviewButtonBounds.setBounds(0, 0, 0, 0);
        layout.syncCaFoundIgnoreButtonBounds.setBounds(0, 0, 0, 0);
        layout.syncClogFoundReviewButtonBounds.setBounds(0, 0, 0, 0);
        layout.syncClogFoundIgnoreButtonBounds.setBounds(0, 0, 0, 0);
        layout.syncCaReviewButtonBounds.setBounds(0, 0, 0, 0);
        layout.syncCaReviewIgnoreButtonBounds.setBounds(0, 0, 0, 0);
        layout.syncClogReviewButtonBounds.setBounds(0, 0, 0, 0);
        layout.syncClogReviewIgnoreButtonBounds.setBounds(0, 0, 0, 0);
        layout.syncCaTestFoundButtonBounds.setBounds(0, 0, 0, 0);
        layout.syncCaTestReviewButtonBounds.setBounds(0, 0, 0, 0);
        layout.syncClogTestFoundButtonBounds.setBounds(0, 0, 0, 0);
        layout.syncClogTestReviewButtonBounds.setBounds(0, 0, 0, 0);
        layout.syncCaMarkedTasksToggleBounds.setBounds(0, 0, 0, 0);
        layout.syncClogMarkedTasksToggleBounds.setBounds(0, 0, 0, 0);
        layout.scrollbarRailBounds.setBounds(0, 0, 0, 0);
        layout.scrollbarThumbBounds.setBounds(0, 0, 0, 0);
        int bx = panelX + panelPadding;
        int viewportW = panelWidth - 2 * panelPadding;

        layout.subTabRulesBounds.setBounds(0, 0, 0, 0);
        layout.subTabDataSyncsBounds.setBounds(0, 0, 0, 0);

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
                    scrollOffsetRows,
                    lastCombatAchievementSyncResult,
                    lastCombatAchievementSyncResultAtLocalTime,
                    lastCollectionLogSyncResult,
                    lastCollectionLogSyncResultAtLocalTime,
                    lastCombatAchievementSyncedTaskNames,
                    lastCollectionLogSyncedTaskNames,
                    showCombatAchievementSyncedTaskNames,
                    showCollectionLogSyncedTaskNames,
                    collectionLogSyncPending,
                    combatAchievementFoundCount,
                    collectionLogFoundCount,
                    combatAchievementReviewCount,
                    collectionLogReviewCount,
                    showSyncTestTools
            );
        }

        List<String> lines = buildRulesLines(fm, viewportW - 8);
        int rb = rowBlock();
        layout.totalContentRows = contentRows(lines, rb);
        int visibleRows = (rb <= 0) ? 0 : Math.max(0, viewportH / rb);

        int maxOffset = Math.max(0, layout.totalContentRows - visibleRows);

        int start = clamp(scrollOffsetRows, maxOffset);
        int end = Math.min(lines.size(), start + visibleRows);

        Shape oldClip = g.getClip();
        g.setClip(layout.viewportBounds);

        // Fonts for hierarchy
        Font normalFont = g.getFont();
        Font sectionTitleFont = FontManager.getRunescapeBoldFont();

        int drawY = adjustedBaseline;

        for (int idx = start; idx < end; idx++) {
            String line = lines.get(idx);

            if (LINE_SYNC_CA_BUTTON_ROW.equals(line) || LINE_SYNC_CLOG_BUTTON_ROW.equals(line)) {
                int btnW = Math.max(96, viewportW / 3);
                int btnH = rowHeight + 10;
                int btnX = bx;
                int by = drawY - fm.getAscent();

                if (by + btnH <= viewportY + viewportH) {
                    if (LINE_SYNC_CA_BUTTON_ROW.equals(line))
                    {
                        layout.syncCAsButtonBounds.setBounds(btnX, by, btnW, btnH);
                    }
                    else
                    {
                        layout.syncClogsButtonBounds.setBounds(btnX, by, btnW, btnH);
                    }
                }

                drawY += Math.max(rb, btnH + listRowSpacing);
                continue;
            }

            if (LINE_SYNC_SECTION_DIVIDER.equals(line)) {
                int y = drawY - Math.max(4, fm.getAscent() / 2);
                g.setColor(new Color(uiGold.getRed(), uiGold.getGreen(), uiGold.getBlue(), 55));
                g.drawLine(bx, y, bx + viewportW - 8, y);
                drawY += rb;
                continue;
            }

            if (LINE_SYNC_FOUND_REVIEW_DIVIDER.equals(line)) {
                drawSyncFoundReviewDivider(g, fm, bx, drawY, viewportW);
                drawY += rb;
                continue;
            }

            if (line.startsWith(LINE_SYNC_CA_MARKED_TOGGLE_PREFIX)
                    || line.startsWith(LINE_SYNC_CLOG_MARKED_TOGGLE_PREFIX)) {
                String marker = line.startsWith(LINE_SYNC_CA_MARKED_TOGGLE_PREFIX)
                        ? LINE_SYNC_CA_MARKED_TOGGLE_PREFIX
                        : LINE_SYNC_CLOG_MARKED_TOGGLE_PREFIX;
                String label = line.substring(marker.length()).trim();
                int by = drawY - fm.getAscent();
                String drawText = TextUtils.truncateToWidth(label, fm, viewportW - 8);
                g.setColor(white);
                g.drawString(drawText, bx, drawY);
                g.setColor(new Color(uiGold.getRed(), uiGold.getGreen(), uiGold.getBlue(), 180));
                g.drawLine(bx, drawY + 2, bx + Math.max(0, fm.stringWidth(drawText)), drawY + 2);

                int boundsW = Math.min(viewportW - 8, fm.stringWidth(drawText) + 4);
                if (LINE_SYNC_CA_MARKED_TOGGLE_PREFIX.equals(marker)) {
                    layout.syncCaMarkedTasksToggleBounds.setBounds(bx, by, Math.max(0, boundsW), rowHeight + 8);
                } else {
                    layout.syncClogMarkedTasksToggleBounds.setBounds(bx, by, Math.max(0, boundsW), rowHeight + 8);
                }

                drawY += rb;
                continue;
            }

            if (LINE_SYNC_CA_FOUND_ACTIONS_ROW.equals(line)
                    || LINE_SYNC_CLOG_FOUND_ACTIONS_ROW.equals(line)
                    || LINE_SYNC_CA_REVIEW_ACTIONS_ROW.equals(line)
                    || LINE_SYNC_CLOG_REVIEW_ACTIONS_ROW.equals(line)
                    || LINE_SYNC_CA_TEST_ACTIONS_ROW.equals(line)
                    || LINE_SYNC_CLOG_TEST_ACTIONS_ROW.equals(line)) {
                int gap = 6;
                boolean testRow = LINE_SYNC_CA_TEST_ACTIONS_ROW.equals(line) || LINE_SYNC_CLOG_TEST_ACTIONS_ROW.equals(line);
                boolean foundRow = LINE_SYNC_CA_FOUND_ACTIONS_ROW.equals(line) || LINE_SYNC_CLOG_FOUND_ACTIONS_ROW.equals(line);
                int reviewW = Math.max(fm.stringWidth(testRow ? "Fake found" : (foundRow ? FOUND_COMPLETIONS_BUTTON_LABEL : "Review")) + 18,
                        testRow ? 92 : (foundRow ? 128 : 76));
                int ignoreW = Math.max(fm.stringWidth(testRow ? "Fake review" : "Ignore") + 18, testRow ? 96 : 72);
                int btnH = rowHeight + 10;
                int btnX = bx;
                int by = drawY - fm.getAscent();
                if (by + btnH <= viewportY + viewportH) {
                    if (LINE_SYNC_CA_FOUND_ACTIONS_ROW.equals(line))
                    {
                        layout.syncCaFoundReviewButtonBounds.setBounds(btnX, by, reviewW, btnH);
                        layout.syncCaFoundIgnoreButtonBounds.setBounds(0, 0, 0, 0);
                    }
                    else if (LINE_SYNC_CLOG_FOUND_ACTIONS_ROW.equals(line))
                    {
                        layout.syncClogFoundReviewButtonBounds.setBounds(btnX, by, reviewW, btnH);
                        layout.syncClogFoundIgnoreButtonBounds.setBounds(0, 0, 0, 0);
                    }
                    else if (LINE_SYNC_CA_REVIEW_ACTIONS_ROW.equals(line))
                    {
                        layout.syncCaReviewButtonBounds.setBounds(btnX, by, reviewW, btnH);
                        layout.syncCaReviewIgnoreButtonBounds.setBounds(btnX + reviewW + gap, by, ignoreW, btnH);
                    }
                    else if (LINE_SYNC_CLOG_REVIEW_ACTIONS_ROW.equals(line))
                    {
                        layout.syncClogReviewButtonBounds.setBounds(btnX, by, reviewW, btnH);
                        layout.syncClogReviewIgnoreButtonBounds.setBounds(btnX + reviewW + gap, by, ignoreW, btnH);
                    }
                    else if (LINE_SYNC_CA_TEST_ACTIONS_ROW.equals(line))
                    {
                        layout.syncCaTestFoundButtonBounds.setBounds(btnX, by, reviewW, btnH);
                        layout.syncCaTestReviewButtonBounds.setBounds(btnX + reviewW + gap, by, ignoreW, btnH);
                    }
                    else
                    {
                        layout.syncClogTestFoundButtonBounds.setBounds(btnX, by, reviewW, btnH);
                        layout.syncClogTestReviewButtonBounds.setBounds(btnX + reviewW + gap, by, ignoreW, btnH);
                    }
                }

                drawY += Math.max(rb, btnH + listRowSpacing);
                continue;
            }

            // ---- TaskerFAQ button ----
            if (LINE_TASKER_FAQ_BUTTON.equals(line)) {
                int btnW = fm.stringWidth("TaskerFAQ") + 8;
                int btnH = rowHeight + 10;

                int btnX = bx;
                int by = drawY - fm.getAscent();

                layout.taskerFaqLinkBounds.setBounds(btnX, by, btnW, btnH);

                drawY += rb;
                continue;
            }

            // ---- GitHub README button ----
            if (LINE_GITHUB_README_BUTTON.equals(line)) {
                int btnW = fm.stringWidth("Github README") + 8;
                int btnH = rowHeight + 10;

                int btnX = bx;
                int by = drawY - fm.getAscent();

                layout.githubReadmeLinkBounds.setBounds(btnX, by, btnW, btnH);

                drawY += rb;
                continue;
            }

            // ---- Title sections (bigger) ----
            if (LINE_DATA_SYNC_TITLE.equals(line) || line.equals("Rules")) {
                g.setFont(sectionTitleFont.deriveFont(Font.BOLD, 18f));
                FontMetrics tfm = g.getFontMetrics();

                g.setColor(white);

                String title = LINE_DATA_SYNC_TITLE.equals(line) ? "Data Syncs" : line;

                // Left aligned instead of centered
                int textX = bx;

                // Slightly lower baseline so taller font sits nicely
                int baseline = drawY + 4;

                g.drawString(title, textX, baseline);

                g.setColor(new Color(uiGold.getRed(), uiGold.getGreen(), uiGold.getBlue(), 55));

                int lineY = baseline + 6;
                g.drawLine(
                        bx,
                        lineY,
                        panelX + panelBounds.width - panelPadding,
                        lineY
                );

                g.setFont(normalFont);

                drawY += rb + 16; // extra spacing after larger header
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
            boolean isRuleSubheader = line.equals("Xtreme Tasker rules") || line.equals("Official Tasker rules");
            boolean isAllowanceHeader = line.equals("Boss combat training allowance");
            boolean isDataSubtitle = line.equals("Account progress sync")
                    || line.equals("Last sync result")
                    || line.equals("Combat Achievements sync")
                    || line.equals("Collection Logs + Achievement Diaries sync")
                    || line.equals(REVIEW_NEEDED_TITLE);

            // color + font
            if (isRuleSubheader) {
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
                String drawText = TextUtils.truncateToWidth(line, fm, viewportW - 8);
                g.drawString(drawText, bx, drawY);
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
            int scrollOffsetRows,
            String lastCombatAchievementSyncResult,
            String lastCombatAchievementSyncResultAtLocalTime,
            String lastCollectionLogSyncResult,
            String lastCollectionLogSyncResultAtLocalTime,
            List<String> lastCombatAchievementSyncedTaskNames,
            List<String> lastCollectionLogSyncedTaskNames,
            boolean showCombatAchievementSyncedTaskNames,
            boolean showCollectionLogSyncedTaskNames,
            boolean collectionLogSyncPending,
            int combatAchievementFoundCount,
            int collectionLogFoundCount,
            int combatAchievementReviewCount,
            int collectionLogReviewCount,
            boolean showSyncTestTools
    )
    {
        int contentW = Math.max(120, viewportW - SYNC_SCROLLBAR_CONTENT_GUTTER);
        int gap = 18;
        int dividerX = bx + contentW / 2;
        int colW = Math.max(120, (contentW - gap) / 2);
        int leftX = bx;
        int rightX = dividerX + gap / 2;
        int rb = rowBlock();
        List<String> helperLines = new ArrayList<>();
        helperLines.addAll(TextUtils.wrapText(SYNC_HELPER_TEXT, fm, contentW - 8));
        helperLines.addAll(TextUtils.wrapText(CLOG_SYNC_HELPER_TEXT, fm, contentW - 8));
        int helperRowsPx = Math.max(1, helperLines.size()) * rb;
        int columnsTopY = viewportY + helperRowsPx + 18;
        int columnsViewportH = Math.max(0, viewportH - (columnsTopY - viewportY));
        Rectangle fullViewport = new Rectangle(bx, viewportY, contentW, viewportH);
        Rectangle columnsViewport = new Rectangle(bx, columnsTopY, contentW, columnsViewportH);
        Rectangle scrollbarViewport = new Rectangle(bx, columnsTopY, viewportW, columnsViewportH);

        List<String> caLines = buildCombatAchievementSyncColumn(
                fm,
                colW - 8,
                lastCombatAchievementSyncResult,
                lastCombatAchievementSyncResultAtLocalTime,
                lastCombatAchievementSyncedTaskNames,
                showCombatAchievementSyncedTaskNames,
                combatAchievementFoundCount,
                combatAchievementReviewCount,
                showSyncTestTools
        );
        List<String> clogLines = buildCollectionLogSyncColumn(
                fm,
                colW - 8,
                lastCollectionLogSyncResult,
                lastCollectionLogSyncResultAtLocalTime,
                lastCollectionLogSyncedTaskNames,
                showCollectionLogSyncedTaskNames,
                collectionLogSyncPending,
                collectionLogFoundCount,
                collectionLogReviewCount,
                showSyncTestTools
        );

        int caRows = contentRows(caLines, rb);
        int clogRows = contentRows(clogLines, rb);
        layout.totalContentRows = Math.max(caRows, clogRows);
        int visibleRows = rb <= 0 ? 0 : Math.max(0, columnsViewportH / rb);
        int start = clamp(scrollOffsetRows, Math.max(0, layout.totalContentRows - visibleRows));
        int end = visibleRows <= 0 ? 0 : start + visibleRows;

        Shape oldClip = g.getClip();
        g.setClip(fullViewport);

        int helperY = viewportY + fm.getAscent();
        g.setColor(uiTextDim);
        for (String line : helperLines)
        {
            String drawLine = TextUtils.truncateToWidth(line, fm, contentW - 8);
            g.drawString(drawLine, bx + Math.max(0, (contentW - fm.stringWidth(drawLine)) / 2), helperY);
            helperY += rb;
        }

        g.setColor(new Color(uiGold.getRed(), uiGold.getGreen(), uiGold.getBlue(), 55));
        g.drawLine(dividerX, columnsTopY, dividerX, viewportY + viewportH - 2);

        drawSyncColumn(g, fm, layout, caLines, leftX, columnsTopY + fm.getAscent(), colW, start, end, columnsViewport);
        drawSyncColumn(g, fm, layout, clogLines, rightX, columnsTopY + fm.getAscent(), colW, start, end, columnsViewport);

        g.setClip(oldClip);
        layout.viewportBounds.setBounds(scrollbarViewport);
        return layout;
    }

    private List<String> buildCombatAchievementSyncColumn(
            FontMetrics fm,
            int maxWidth,
            String lastCombatAchievementSyncResult,
            String lastCombatAchievementSyncResultAtLocalTime,
            List<String> lastCombatAchievementSyncedTaskNames,
            boolean showCombatAchievementSyncedTaskNames,
            int combatAchievementFoundCount,
            int combatAchievementReviewCount,
            boolean showSyncTestTools
    )
    {
        List<String> lines = new ArrayList<>();
        boolean hasCaResult = lastCombatAchievementSyncResult != null && !lastCombatAchievementSyncResult.trim().isEmpty();

        lines.add("Combat Achievements sync");
        lines.add(LINE_SYNC_CA_BUTTON_ROW);
        lines.add("");
        if (hasCaResult)
        {
            addSyncResultLabelLine(lines);
            addSyncTimestampLine(lines, "Last CA sync", lastCombatAchievementSyncResultAtLocalTime, fm, maxWidth);
            lines.add("");
            addSyncResultStatusMessageLines(lines, combatAchievementFoundCount, "CA", fm, maxWidth);
        }
        if (combatAchievementFoundCount > 0)
        {
            addFoundCompletionsHelperLines(lines, fm, maxWidth);
            lines.add(LINE_SYNC_CA_FOUND_ACTIONS_ROW);
        }
        addReviewDivider(lines, combatAchievementReviewCount);
        addReviewLines(lines, combatAchievementReviewCount, "CA", fm, maxWidth, LINE_SYNC_CA_REVIEW_ACTIONS_ROW);
        addTestToolLines(lines, showSyncTestTools, fm, maxWidth, LINE_SYNC_CA_TEST_ACTIONS_ROW);
        lines.add("");
        return lines;
    }

    private List<String> buildCollectionLogSyncColumn(
            FontMetrics fm,
            int maxWidth,
            String lastCollectionLogSyncResult,
            String lastCollectionLogSyncResultAtLocalTime,
            List<String> lastCollectionLogSyncedTaskNames,
            boolean showCollectionLogSyncedTaskNames,
            boolean collectionLogSyncPending,
            int collectionLogFoundCount,
            int collectionLogReviewCount,
            boolean showSyncTestTools
    )
    {
        List<String> lines = new ArrayList<>();
        boolean hasClogResult = lastCollectionLogSyncResult != null && !lastCollectionLogSyncResult.trim().isEmpty();

        lines.add("Collection Logs + Achievement Diaries sync");
        lines.add(LINE_SYNC_CLOG_BUTTON_ROW);
        lines.add("");
        if (collectionLogSyncPending)
        {
            addSyncPendingLines(lines, fm, maxWidth);
        }
        else if (hasClogResult)
        {
            addSyncResultLabelLine(lines);
            addSyncTimestampLine(lines, "Last CLOG/AD sync", lastCollectionLogSyncResultAtLocalTime, fm, maxWidth);
            lines.add("");
            addSyncResultStatusMessageLines(lines, collectionLogFoundCount, "CLOG/ADs", fm, maxWidth);
        }
        if (collectionLogFoundCount > 0)
        {
            addFoundCompletionsHelperLines(lines, fm, maxWidth);
            lines.add(LINE_SYNC_CLOG_FOUND_ACTIONS_ROW);
        }
        addReviewDivider(lines, collectionLogReviewCount);
        addReviewLines(lines, collectionLogReviewCount, "CLOG/ADs", fm, maxWidth, LINE_SYNC_CLOG_REVIEW_ACTIONS_ROW);
        addTestToolLines(lines, showSyncTestTools, fm, maxWidth, LINE_SYNC_CLOG_TEST_ACTIONS_ROW);
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
            if (LINE_SYNC_CA_BUTTON_ROW.equals(line) || LINE_SYNC_CLOG_BUTTON_ROW.equals(line))
            {
                int btnW = Math.max(96, Math.min(colW - 8, colW / 2 + 28));
                int btnH = rowHeight + 10;
                int btnX = x + Math.max(0, (colW - btnW) / 2);
                int by = drawY - fm.getAscent();
                boolean visible = buttonFitsViewport(by, btnH, viewport);
                if (visible && LINE_SYNC_CA_BUTTON_ROW.equals(line))
                {
                    layout.syncCAsButtonBounds.setBounds(btnX, by, btnW, btnH);
                }
                else if (visible)
                {
                    layout.syncClogsButtonBounds.setBounds(btnX, by, btnW, btnH);
                }
                drawY += Math.max(rb, btnH + listRowSpacing);
                continue;
            }

            if (LINE_SYNC_CA_FOUND_ACTIONS_ROW.equals(line)
                    || LINE_SYNC_CLOG_FOUND_ACTIONS_ROW.equals(line)
                    || LINE_SYNC_CA_REVIEW_ACTIONS_ROW.equals(line)
                    || LINE_SYNC_CLOG_REVIEW_ACTIONS_ROW.equals(line)
                    || LINE_SYNC_CA_TEST_ACTIONS_ROW.equals(line)
                    || LINE_SYNC_CLOG_TEST_ACTIONS_ROW.equals(line))
            {
                int gap = 6;
                boolean testRow = LINE_SYNC_CA_TEST_ACTIONS_ROW.equals(line) || LINE_SYNC_CLOG_TEST_ACTIONS_ROW.equals(line);
                boolean foundRow = LINE_SYNC_CA_FOUND_ACTIONS_ROW.equals(line) || LINE_SYNC_CLOG_FOUND_ACTIONS_ROW.equals(line);
                int reviewW = Math.max(fm.stringWidth(testRow ? "Fake found" : (foundRow ? FOUND_COMPLETIONS_BUTTON_LABEL : "Review")) + 18,
                        testRow ? 92 : (foundRow ? 128 : 76));
                int ignoreW = Math.max(fm.stringWidth(testRow ? "Fake review" : "Ignore") + 18, testRow ? 96 : 72);
                int btnH = rowHeight + 10;
                int by = drawY - fm.getAscent();
                int groupW = foundRow ? reviewW : reviewW + gap + ignoreW;
                int groupX = x + Math.max(0, (colW - groupW) / 2);
                if (!buttonFitsViewport(by, btnH, viewport))
                {
                    drawY += Math.max(rb, btnH + listRowSpacing);
                    continue;
                }
                if (LINE_SYNC_CA_FOUND_ACTIONS_ROW.equals(line))
                {
                    layout.syncCaFoundReviewButtonBounds.setBounds(groupX, by, reviewW, btnH);
                    layout.syncCaFoundIgnoreButtonBounds.setBounds(0, 0, 0, 0);
                }
                else if (LINE_SYNC_CLOG_FOUND_ACTIONS_ROW.equals(line))
                {
                    layout.syncClogFoundReviewButtonBounds.setBounds(groupX, by, reviewW, btnH);
                    layout.syncClogFoundIgnoreButtonBounds.setBounds(0, 0, 0, 0);
                }
                else if (LINE_SYNC_CA_REVIEW_ACTIONS_ROW.equals(line))
                {
                    layout.syncCaReviewButtonBounds.setBounds(groupX, by, reviewW, btnH);
                    layout.syncCaReviewIgnoreButtonBounds.setBounds(groupX + reviewW + gap, by, ignoreW, btnH);
                }
                else if (LINE_SYNC_CLOG_REVIEW_ACTIONS_ROW.equals(line))
                {
                    layout.syncClogReviewButtonBounds.setBounds(groupX, by, reviewW, btnH);
                    layout.syncClogReviewIgnoreButtonBounds.setBounds(groupX + reviewW + gap, by, ignoreW, btnH);
                }
                else if (LINE_SYNC_CA_TEST_ACTIONS_ROW.equals(line))
                {
                    layout.syncCaTestFoundButtonBounds.setBounds(groupX, by, reviewW, btnH);
                    layout.syncCaTestReviewButtonBounds.setBounds(groupX + reviewW + gap, by, ignoreW, btnH);
                }
                else
                {
                    layout.syncClogTestFoundButtonBounds.setBounds(groupX, by, reviewW, btnH);
                    layout.syncClogTestReviewButtonBounds.setBounds(groupX + reviewW + gap, by, ignoreW, btnH);
                }
                drawY += Math.max(rb, btnH + listRowSpacing);
                continue;
            }

            if (line.startsWith(LINE_SYNC_CA_MARKED_TOGGLE_PREFIX)
                    || line.startsWith(LINE_SYNC_CLOG_MARKED_TOGGLE_PREFIX))
            {
                String marker = line.startsWith(LINE_SYNC_CA_MARKED_TOGGLE_PREFIX)
                        ? LINE_SYNC_CA_MARKED_TOGGLE_PREFIX
                        : LINE_SYNC_CLOG_MARKED_TOGGLE_PREFIX;
                String label = line.substring(marker.length()).trim();
                String drawText = TextUtils.truncateToWidth(label, fm, colW - 8);
                int by = drawY - fm.getAscent();
                int textX = x + Math.max(0, (colW - fm.stringWidth(drawText)) / 2);
                g.setColor(white);
                g.drawString(drawText, textX, drawY);
                g.setColor(new Color(uiGold.getRed(), uiGold.getGreen(), uiGold.getBlue(), 180));
                g.drawLine(textX, drawY + 2, textX + Math.max(0, fm.stringWidth(drawText)), drawY + 2);
                int boundsW = Math.min(colW - 8, fm.stringWidth(drawText) + 4);
                int boundsX = textX - 2;
                if (LINE_SYNC_CA_MARKED_TOGGLE_PREFIX.equals(marker))
                {
                    layout.syncCaMarkedTasksToggleBounds.setBounds(boundsX, by, Math.max(0, boundsW), rowHeight + 8);
                }
                else
                {
                    layout.syncClogMarkedTasksToggleBounds.setBounds(boundsX, by, Math.max(0, boundsW), rowHeight + 8);
                }
                drawY += rb;
                continue;
            }

            if (line.trim().isEmpty())
            {
                drawY += rb;
                continue;
            }

            if (LINE_SYNC_FOUND_REVIEW_DIVIDER.equals(line))
            {
                drawSyncFoundReviewDivider(g, fm, x, drawY, colW);
                drawY += rb;
                continue;
            }

            String markedLine = markedLineText(line);
            if (markedLine != null)
            {
                g.setColor(markedLineColor(line));
                String drawText = TextUtils.truncateToWidth(markedLine, fm, colW - 8);
                g.drawString(drawText, x + Math.max(0, (colW - fm.stringWidth(drawText)) / 2), drawY);
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
                    String drawText = TextUtils.truncateToWidth(line, fm, colW - 8);
                    g.drawString(drawText, x + Math.max(0, (colW - fm.stringWidth(drawText)) / 2), drawY);
                }
                g.setFont(normalFont);
                fm = g.getFontMetrics();
            }
            else
            {
                g.setColor(uiTextDim);
                String drawText = TextUtils.truncateToWidth(line, fm, colW - 8);
                g.drawString(drawText, x + Math.max(0, (colW - fm.stringWidth(drawText)) / 2), drawY);
            }
            drawY += rb;
        }

        g.setFont(normalFont);
    }

    private static boolean buttonFitsViewport(int y, int height, Rectangle viewport)
    {
        return viewport == null || (y >= viewport.y && y + height <= viewport.y + viewport.height);
    }

    private List<String> buildRulesLines(FontMetrics fm, int maxWidth) {
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add("Rules");

        lines.addAll(TextUtils.wrapText(
                "Xtreme Tasker adds extra rules on top of official Tasker. "
                        + "These are developed by the Xtreme Tasker creator and documented in the GitHub README.",
                fm,
                maxWidth
        ));
        lines.add(LINE_GITHUB_README_BUTTON);
        lines.add("");
        lines.add("Boss combat training allowance");
        lines.addAll(TextUtils.wrapText(
                "For any task requiring that you kill a boss with a suggested skills section on their "
                        + "\"strategies\" OSRS wiki page, you are allowed to train your combat skills to those "
                        + "suggested skills. You must do this through the Slayer skill, with any slayer master(s) "
                        + "of your choosing.\n"
                        + "It's heavily recommended to be strategic when choosing your slayer master(s) for supplies "
                        + "and equipment throughout the grind. For example, Krystilia's slayer list includes mammoths "
                        + "which drop single-dose prayer potions. This would be especially useful for bosses that "
                        + "require overhead prayers to kill them.",
                fm,
                maxWidth
        ));
        lines.add("");
        lines.add("Official Tasker rules");
        lines.addAll(TextUtils.wrapText(
                "Xtreme Tasker is built on top of official Tasker, a well-established ruleset "
                        + "developed by the Tasker community. All official Tasker rules apply in full — "
                        + "refer to the Rules and Overview section of the TaskerFAQ for all tasks, "
                        + "including combat achievements.",
                fm,
                maxWidth
        ));
        lines.add(LINE_TASKER_FAQ_BUTTON);
        lines.add("");
        lines.add("");
        return lines;
    }

    private List<String> buildDataSyncLines(FontMetrics fm, int maxWidth) {
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.addAll(TextUtils.wrapText(SYNC_HELPER_TEXT, fm, maxWidth));
        return lines;
    }

    private List<String> buildDataSyncLines(
            FontMetrics fm,
            int maxWidth,
            String lastCombatAchievementSyncResult,
            String lastCombatAchievementSyncResultAtLocalTime,
            String lastCollectionLogSyncResult,
            String lastCollectionLogSyncResultAtLocalTime,
            List<String> lastCombatAchievementSyncedTaskNames,
            List<String> lastCollectionLogSyncedTaskNames,
            boolean showCombatAchievementSyncedTaskNames,
            boolean showCollectionLogSyncedTaskNames,
            boolean collectionLogSyncPending,
            int combatAchievementFoundCount,
            int collectionLogFoundCount,
            int combatAchievementReviewCount,
            int collectionLogReviewCount) {
        List<String> lines = buildDataSyncLines(fm, maxWidth);
        boolean hasCaResult = lastCombatAchievementSyncResult != null && !lastCombatAchievementSyncResult.trim().isEmpty();
        boolean hasClogResult = lastCollectionLogSyncResult != null && !lastCollectionLogSyncResult.trim().isEmpty();

        lines.add(LINE_SYNC_SECTION_DIVIDER);
        lines.add("Combat Achievements sync");
        lines.add(LINE_SYNC_CA_BUTTON_ROW);
        lines.add("");
        if (hasCaResult)
        {
            addSyncResultLabelLine(lines);
            addSyncTimestampLine(lines, "Last CA sync", lastCombatAchievementSyncResultAtLocalTime, fm, maxWidth);
            lines.add("");
            addSyncResultStatusMessageLines(lines, combatAchievementFoundCount, "CA", fm, maxWidth);
        }
        if (combatAchievementFoundCount > 0)
        {
            addFoundCompletionsHelperLines(lines, fm, maxWidth);
            lines.add(LINE_SYNC_CA_FOUND_ACTIONS_ROW);
        }
        addReviewDivider(lines, combatAchievementReviewCount);
        addReviewLines(lines, combatAchievementReviewCount, "CA", fm, maxWidth, LINE_SYNC_CA_REVIEW_ACTIONS_ROW);
        if (combatAchievementReviewCount > 0)
        {
            lines.add("");
        }

        lines.add(LINE_SYNC_SECTION_DIVIDER);
        lines.add("Collection Logs + Achievement Diaries sync");
        lines.addAll(TextUtils.wrapText(CLOG_SYNC_HELPER_TEXT, fm, maxWidth));
        lines.add(LINE_SYNC_CLOG_BUTTON_ROW);
        lines.add("");
        if (collectionLogSyncPending)
        {
            addSyncPendingLines(lines, fm, maxWidth);
        }
        else if (hasClogResult)
        {
            addSyncResultLabelLine(lines);
            addSyncTimestampLine(lines, "Last CLOG/AD sync", lastCollectionLogSyncResultAtLocalTime, fm, maxWidth);
            lines.add("");
            addSyncResultStatusMessageLines(lines, collectionLogFoundCount, "CLOG/ADs", fm, maxWidth);
        }
        if (collectionLogFoundCount > 0)
        {
            addFoundCompletionsHelperLines(lines, fm, maxWidth);
            lines.add(LINE_SYNC_CLOG_FOUND_ACTIONS_ROW);
        }
        addReviewDivider(lines, collectionLogReviewCount);
        addReviewLines(lines, collectionLogReviewCount, "CLOG/ADs", fm, maxWidth, LINE_SYNC_CLOG_REVIEW_ACTIONS_ROW);

        lines.add("");
        lines.add("");
        return lines;
    }

    private void addSyncPendingLines(List<String> lines, FontMetrics fm, int maxWidth)
    {
        lines.add(LINE_SYNC_RESULT_LABEL + "Result:");
        lines.add(LINE_SYNC_RESULT_EMPTY_PREFIX + "....");
    }

    private void addReviewLines(
            List<String> lines,
            int reviewCount,
            String label,
            FontMetrics fm,
            int maxWidth,
            String actionsMarker)
    {
        if (reviewCount > 0)
        {
            lines.add("");
            lines.add(REVIEW_NEEDED_TITLE);
            lines.addAll(prefixWrappedLines(
                    LINE_SYNC_RESULT_ERROR_PREFIX,
                    reviewCount + " " + label + " completed tasks not found completed in game via sync.",
                    fm,
                    maxWidth
            ));
            lines.addAll(prefixWrappedLines(
                    LINE_SYNC_RESULT_EMPTY_PREFIX,
                    "Click review to see + update these mismatched tasks. Note: sync may not catch every mismatched task.",
                    fm,
                    maxWidth
            ));
            lines.add(actionsMarker);
        }
    }

    private void addReviewDivider(List<String> lines, int reviewCount)
    {
        if (reviewCount > 0)
        {
            lines.add(LINE_SYNC_FOUND_REVIEW_DIVIDER);
        }
    }

    private void addTestToolLines(
            List<String> lines,
            boolean showSyncTestTools,
            FontMetrics fm,
            int maxWidth,
            String actionsMarker)
    {
        if (!showSyncTestTools)
        {
            return;
        }

        lines.add("");
        lines.add("Test tools");
        lines.addAll(TextUtils.wrapText("Stage fake review rows for UI testing. Real progress is not read.", fm, maxWidth));
        lines.add(actionsMarker);
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

    private void addSyncResultLabelLine(List<String> lines)
    {
        lines.add(LINE_SYNC_RESULT_LABEL + "Result:");
    }

    private void addSyncResultStatusMessageLines(List<String> lines, int foundCount, String sourceLabel, FontMetrics fm, int maxWidth)
    {
        String prefix = foundCount > 0 ? LINE_SYNC_RESULT_FOUND_PREFIX : LINE_SYNC_RESULT_EMPTY_PREFIX;
        String message = foundCount > 0
                ? "Sync found " + foundCount + " new " + sourceLabel + " task completion(s)!"
                : "Sync did not find new " + sourceLabel + " task completion(s)";
        lines.addAll(prefixWrappedLines(prefix, message, fm, maxWidth));
    }

    private void addFoundCompletionsHelperLines(List<String> lines, FontMetrics fm, int maxWidth)
    {
        lines.addAll(prefixWrappedLines(LINE_SYNC_RESULT_EMPTY_PREFIX, FOUND_COMPLETIONS_HELPER, fm, maxWidth));
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
        return uiTextDim;
    }

    private void drawSyncFoundReviewDivider(Graphics2D g, FontMetrics fm, int x, int baseline, int maxWidth)
    {
        String divider = ".....";
        String drawText = TextUtils.truncateToWidth(divider, fm, Math.max(0, maxWidth - 8));
        g.setColor(uiTextDim);
        g.drawString(drawText, x + Math.max(0, (maxWidth - fm.stringWidth(drawText)) / 2), baseline);
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
        Color borderColor = new Color(uiGold.getRed(), uiGold.getGreen(), uiGold.getBlue(), 160);
        Color activeBg   = new Color(uiGold.getRed(), uiGold.getGreen(), uiGold.getBlue(), 55);
        Color inactiveBg = new Color(20, 16, 10, 210);
        Color divider    = new Color(uiGold.getRed(), uiGold.getGreen(), uiGold.getBlue(), 80);

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
        Color accentColor = new Color(uiGold.getRed(), uiGold.getGreen(), uiGold.getBlue(), 200);
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

    private static int clamp(int v, int max) {
        return Math.max(0, Math.min(max, v));
    }

    private int contentRows(List<String> lines, int rowBlock)
    {
        if (lines == null || lines.isEmpty() || rowBlock <= 0)
        {
            return 0;
        }

        int totalPx = 0;
        for (String line : lines)
        {
            totalPx += lineHeightPx(line, rowBlock);
        }
        return Math.max(lines.size(), (totalPx + rowBlock - 1) / rowBlock);
    }

    private int lineHeightPx(String line, int rowBlock)
    {
        if (LINE_DATA_SYNC_TITLE.equals(line) || "Rules".equals(line))
        {
            return rowBlock + 16;
        }
        return rowBlock;
    }

    // Expose URL so overlay can use it for clicks without duplicating string
    public static String taskerFaqUrl() {
        return TASKER_FAQ_URL;
    }

    public static String githubReadmeUrl() {
        return GITHUB_README_URL;
    }

    private int centeredBaselineInRow(int rowTopY, int rowBlockH, FontMetrics tfm) {
        return rowTopY + ((rowBlockH - tfm.getHeight()) / 2) + tfm.getAscent();
    }

}
