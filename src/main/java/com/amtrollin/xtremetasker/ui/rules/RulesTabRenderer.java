package com.amtrollin.xtremetasker.ui.rules;

import com.amtrollin.xtremetasker.ui.text.TextUtils;
import net.runelite.client.ui.FontManager;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static java.awt.Color.white;

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

    private static final String LINE_TASKER_FAQ_BUTTON = "[TASKER_FAQ_BUTTON]";
    private static final String LINE_GITHUB_README_BUTTON = "[GITHUB_README_BUTTON]";
    private static final String LINE_SYNC_CA_BUTTON_ROW = "[SYNC_CA_BUTTON_ROW]";
    private static final String LINE_SYNC_CLOG_BUTTON_ROW = "[SYNC_CLOG_BUTTON_ROW]";
    private static final String LINE_SYNC_CA_FOUND_ACTIONS_ROW = "[SYNC_CA_FOUND_ACTIONS_ROW]";
    private static final String LINE_SYNC_CLOG_FOUND_ACTIONS_ROW = "[SYNC_CLOG_FOUND_ACTIONS_ROW]";
    private static final String LINE_SYNC_CA_REVIEW_ACTIONS_ROW = "[SYNC_CA_REVIEW_ACTIONS_ROW]";
    private static final String LINE_SYNC_CLOG_REVIEW_ACTIONS_ROW = "[SYNC_CLOG_REVIEW_ACTIONS_ROW]";
    private static final String LINE_SYNC_CA_MARKED_TOGGLE_PREFIX = "[SYNC_CA_MARKED_TOGGLE]";
    private static final String LINE_SYNC_CLOG_MARKED_TOGGLE_PREFIX = "[SYNC_CLOG_MARKED_TOGGLE]";
    private static final String LINE_SYNC_SECTION_DIVIDER = "[SYNC_SECTION_DIVIDER]";
    private static final String LINE_DATA_SYNC_TITLE = "[DATA_SYNC_TITLE]";
    private static final String REVIEW_NEEDED_TITLE = "Review needed";
    private static final String SYNC_HELPER_TEXT =
            "Use these buttons to detect tasks you've already completed. Progress sync is separate from task list updates.";
    private static final String CLOG_SYNC_HELPER_TEXT =
            "\n\nOpen your Collection Log in-game before syncing so RuneLite can update your latest Collection Log progress. \nCombat Achievement and Achievement Diary progress are synced automatically.";
    private static final BufferedImage REVIEW_NEEDED_ICON = loadReviewNeededIconSafe();

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
            int collectionLogReviewCount
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
                    collectionLogReviewCount
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

                drawY += rb;
                continue;
            }

            if (LINE_SYNC_SECTION_DIVIDER.equals(line)) {
                int y = drawY - Math.max(4, fm.getAscent() / 2);
                g.setColor(new Color(uiGold.getRed(), uiGold.getGreen(), uiGold.getBlue(), 55));
                g.drawLine(bx, y, bx + viewportW - 8, y);
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
                    || LINE_SYNC_CLOG_REVIEW_ACTIONS_ROW.equals(line)) {
                int gap = 6;
                int reviewW = Math.max(fm.stringWidth("Review") + 18, 76);
                int ignoreW = Math.max(fm.stringWidth("Ignore") + 18, 72);
                int btnH = rowHeight + 10;
                int btnX = bx;
                int by = drawY - fm.getAscent();
                if (by + btnH <= viewportY + viewportH) {
                    if (LINE_SYNC_CA_FOUND_ACTIONS_ROW.equals(line))
                    {
                        layout.syncCaFoundReviewButtonBounds.setBounds(btnX, by, reviewW, btnH);
                        layout.syncCaFoundIgnoreButtonBounds.setBounds(btnX + reviewW + gap, by, ignoreW, btnH);
                    }
                    else if (LINE_SYNC_CLOG_FOUND_ACTIONS_ROW.equals(line))
                    {
                        layout.syncClogFoundReviewButtonBounds.setBounds(btnX, by, reviewW, btnH);
                        layout.syncClogFoundIgnoreButtonBounds.setBounds(btnX + reviewW + gap, by, ignoreW, btnH);
                    }
                    else if (LINE_SYNC_CA_REVIEW_ACTIONS_ROW.equals(line))
                    {
                        layout.syncCaReviewButtonBounds.setBounds(btnX, by, reviewW, btnH);
                        layout.syncCaReviewIgnoreButtonBounds.setBounds(btnX + reviewW + gap, by, ignoreW, btnH);
                    }
                    else
                    {
                        layout.syncClogReviewButtonBounds.setBounds(btnX, by, reviewW, btnH);
                        layout.syncClogReviewIgnoreButtonBounds.setBounds(btnX + reviewW + gap, by, ignoreW, btnH);
                    }
                }

                drawY += rb;
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
            int collectionLogReviewCount
    )
    {
        int gap = 18;
        int dividerX = bx + viewportW / 2;
        int colW = Math.max(120, (viewportW - gap) / 2);
        int leftX = bx;
        int rightX = dividerX + gap / 2;
        int rb = rowBlock();
        List<String> helperLines = new ArrayList<>();
        helperLines.addAll(TextUtils.wrapText(SYNC_HELPER_TEXT, fm, viewportW - 8));
        helperLines.addAll(TextUtils.wrapText(CLOG_SYNC_HELPER_TEXT, fm, viewportW - 8));
        int helperRowsPx = Math.max(1, helperLines.size()) * rb;
        int columnsTopY = viewportY + helperRowsPx + 18;
        int columnsViewportH = Math.max(0, viewportH - (columnsTopY - viewportY));
        Rectangle fullViewport = new Rectangle(layout.viewportBounds);
        Rectangle columnsViewport = new Rectangle(bx, columnsTopY, viewportW, columnsViewportH);

        List<String> caLines = buildCombatAchievementSyncColumn(
                fm,
                colW - 8,
                lastCombatAchievementSyncResult,
                lastCombatAchievementSyncResultAtLocalTime,
                lastCombatAchievementSyncedTaskNames,
                showCombatAchievementSyncedTaskNames,
                combatAchievementFoundCount,
                combatAchievementReviewCount
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
                collectionLogReviewCount
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
            String drawLine = TextUtils.truncateToWidth(line, fm, viewportW - 8);
            g.drawString(drawLine, bx + Math.max(0, (viewportW - fm.stringWidth(drawLine)) / 2), helperY);
            helperY += rb;
        }

        g.setColor(new Color(uiGold.getRed(), uiGold.getGreen(), uiGold.getBlue(), 55));
        g.drawLine(dividerX, columnsTopY, dividerX, viewportY + viewportH - 2);

        drawSyncColumn(g, fm, layout, caLines, leftX, columnsTopY + fm.getAscent(), colW, start, end);
        drawSyncColumn(g, fm, layout, clogLines, rightX, columnsTopY + fm.getAscent(), colW, start, end);

        g.setClip(oldClip);
        layout.viewportBounds.setBounds(columnsViewport);
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
            int combatAchievementReviewCount
    )
    {
        List<String> lines = new ArrayList<>();
        boolean hasCaResult = lastCombatAchievementSyncResult != null && !lastCombatAchievementSyncResult.trim().isEmpty();

        lines.add("Combat Achievements sync");
        lines.add("");
        lines.add(LINE_SYNC_CA_BUTTON_ROW);
        lines.add("");
        if (hasCaResult)
        {
            addSyncResultInfoLines(lines, "Last CA sync", lastCombatAchievementSyncResult, lastCombatAchievementSyncResultAtLocalTime, fm, maxWidth);
        }
        addMarkedTaskLines(lines,
                lastCombatAchievementSyncedTaskNames,
                showCombatAchievementSyncedTaskNames,
                LINE_SYNC_CA_MARKED_TOGGLE_PREFIX,
                LINE_SYNC_CA_FOUND_ACTIONS_ROW,
                fm,
                maxWidth);
        if (combatAchievementFoundCount <= 0)
        {
            lines.remove(LINE_SYNC_CA_FOUND_ACTIONS_ROW);
        }
        addReviewLines(lines, combatAchievementReviewCount, "CA", fm, maxWidth, LINE_SYNC_CA_REVIEW_ACTIONS_ROW);
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
            int collectionLogReviewCount
    )
    {
        List<String> lines = new ArrayList<>();
        boolean hasClogResult = lastCollectionLogSyncResult != null && !lastCollectionLogSyncResult.trim().isEmpty();

        lines.add("Collection Logs + Achievement Diaries sync");
        lines.add("");
        lines.add(LINE_SYNC_CLOG_BUTTON_ROW);
        lines.add("");
        if (collectionLogSyncPending)
        {
            addSyncPendingLines(lines, fm, maxWidth);
        }
        else if (hasClogResult)
        {
            addSyncResultInfoLines(lines, "Last CLOG/AD sync", lastCollectionLogSyncResult, lastCollectionLogSyncResultAtLocalTime, fm, maxWidth);
        }
        addMarkedTaskLines(lines,
                lastCollectionLogSyncedTaskNames,
                showCollectionLogSyncedTaskNames,
                LINE_SYNC_CLOG_MARKED_TOGGLE_PREFIX,
                LINE_SYNC_CLOG_FOUND_ACTIONS_ROW,
                fm,
                maxWidth);
        if (collectionLogFoundCount <= 0)
        {
            lines.remove(LINE_SYNC_CLOG_FOUND_ACTIONS_ROW);
        }
        addReviewLines(lines, collectionLogReviewCount, "CLOG/AD", fm, maxWidth, LINE_SYNC_CLOG_REVIEW_ACTIONS_ROW);
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
            int end
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
                if (LINE_SYNC_CA_BUTTON_ROW.equals(line))
                {
                    layout.syncCAsButtonBounds.setBounds(btnX, by, btnW, btnH);
                }
                else
                {
                    layout.syncClogsButtonBounds.setBounds(btnX, by, btnW, btnH);
                }
                drawY += rb;
                continue;
            }

            if (LINE_SYNC_CA_FOUND_ACTIONS_ROW.equals(line)
                    || LINE_SYNC_CLOG_FOUND_ACTIONS_ROW.equals(line)
                    || LINE_SYNC_CA_REVIEW_ACTIONS_ROW.equals(line)
                    || LINE_SYNC_CLOG_REVIEW_ACTIONS_ROW.equals(line))
            {
                int gap = 6;
                int reviewW = Math.max(fm.stringWidth("Review") + 18, 76);
                int ignoreW = Math.max(fm.stringWidth("Ignore") + 18, 72);
                int btnH = rowHeight + 10;
                int by = drawY - fm.getAscent();
                int groupW = reviewW + gap + ignoreW;
                int groupX = x + Math.max(0, (colW - groupW) / 2);
                if (LINE_SYNC_CA_FOUND_ACTIONS_ROW.equals(line))
                {
                    layout.syncCaFoundReviewButtonBounds.setBounds(groupX, by, reviewW, btnH);
                    layout.syncCaFoundIgnoreButtonBounds.setBounds(groupX + reviewW + gap, by, ignoreW, btnH);
                }
                else if (LINE_SYNC_CLOG_FOUND_ACTIONS_ROW.equals(line))
                {
                    layout.syncClogFoundReviewButtonBounds.setBounds(groupX, by, reviewW, btnH);
                    layout.syncClogFoundIgnoreButtonBounds.setBounds(groupX + reviewW + gap, by, ignoreW, btnH);
                }
                else if (LINE_SYNC_CA_REVIEW_ACTIONS_ROW.equals(line))
                {
                    layout.syncCaReviewButtonBounds.setBounds(groupX, by, reviewW, btnH);
                    layout.syncCaReviewIgnoreButtonBounds.setBounds(groupX + reviewW + gap, by, ignoreW, btnH);
                }
                else
                {
                    layout.syncClogReviewButtonBounds.setBounds(groupX, by, reviewW, btnH);
                    layout.syncClogReviewIgnoreButtonBounds.setBounds(groupX + reviewW + gap, by, ignoreW, btnH);
                }
                drawY += rb;
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
                    drawReviewNeededTitle(g, fm, x + Math.max(0, (colW - textW) / 2), drawY, colW);
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
            addSyncResultInfoLines(lines, "Last CA sync", lastCombatAchievementSyncResult, lastCombatAchievementSyncResultAtLocalTime, fm, maxWidth);
        }
        addMarkedTaskLines(lines,
                lastCombatAchievementSyncedTaskNames,
                showCombatAchievementSyncedTaskNames,
                LINE_SYNC_CA_MARKED_TOGGLE_PREFIX,
                LINE_SYNC_CA_FOUND_ACTIONS_ROW,
                fm,
                maxWidth);
        if (combatAchievementFoundCount <= 0)
        {
            lines.remove(LINE_SYNC_CA_FOUND_ACTIONS_ROW);
        }
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
            addSyncResultInfoLines(lines, "Last CLOG/AD sync", lastCollectionLogSyncResult, lastCollectionLogSyncResultAtLocalTime, fm, maxWidth);
        }
        addMarkedTaskLines(lines,
            lastCollectionLogSyncedTaskNames,
            showCollectionLogSyncedTaskNames,
            LINE_SYNC_CLOG_MARKED_TOGGLE_PREFIX,
            LINE_SYNC_CLOG_FOUND_ACTIONS_ROW,
            fm,
            maxWidth);
        if (collectionLogFoundCount <= 0)
        {
            lines.remove(LINE_SYNC_CLOG_FOUND_ACTIONS_ROW);
        }
        addReviewLines(lines, collectionLogReviewCount, "CLOG/AD", fm, maxWidth, LINE_SYNC_CLOG_REVIEW_ACTIONS_ROW);

        lines.add("");
        lines.add("");
        return lines;
    }

    private void addSyncPendingLines(List<String> lines, FontMetrics fm, int maxWidth)
    {
        lines.addAll(TextUtils.wrapText("Result:", fm, maxWidth));
        lines.addAll(TextUtils.wrapText("....", fm, maxWidth));
    }

    private void addMarkedTaskLines(
            List<String> lines,
            List<String> taskNames,
            boolean expanded,
            String markerPrefix,
            String actionsMarker,
            FontMetrics fm,
            int maxWidth)
    {
        if (taskNames == null || taskNames.isEmpty())
        {
            return;
        }

        String toggleLabel = (expanded ? "Hide" : "Show")
                + " tasks found by sync ("
                + taskNames.size()
                + ")";
        lines.add(markerPrefix + " " + toggleLabel);
        lines.add(actionsMarker);

        if (!expanded)
        {
            return;
        }

        for (String taskName : taskNames)
        {
            if (taskName == null || taskName.trim().isEmpty())
            {
                continue;
            }

            lines.addAll(TextUtils.wrapText("- " + taskName.trim(), fm, maxWidth));
        }
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
            lines.addAll(TextUtils.wrapText(
                    reviewCount + " " + label + " completed task(s) do not match the latest sync. Nothing will be marked incomplete unless you choose it.",
                    fm,
                    maxWidth
            ));
            lines.addAll(TextUtils.wrapText(
                    "Review may not catch every mismatched task.",
                    fm,
                    maxWidth
            ));
            lines.add(actionsMarker);
        }
    }

    private void addSyncResultInfoLines(
            List<String> lines,
            String label,
            String result,
            String localTime,
            FontMetrics fm,
            int maxWidth)
    {
        String time = cleanTimestamp(localTime);
        if (time != null && !time.trim().isEmpty())
        {
            lines.addAll(TextUtils.wrapText(label + ": " + time.trim(), fm, maxWidth));
        }
        addSyncResultLines(lines, result.trim(), fm, maxWidth);
    }

    private void addSyncResultLines(List<String> lines, String result, FontMetrics fm, int maxWidth) {
        int reviewStart = result.indexOf(" Review ");
        if (reviewStart < 0)
        {
            addSyncSummaryLines(lines, result, fm, maxWidth);
            return;
        }

        String summary = result.substring(0, reviewStart).trim();
        if (!summary.isEmpty())
        {
            addSyncSummaryLines(lines, summary, fm, maxWidth);
        }
    }

    private void addSyncSummaryLines(List<String> lines, String summary, FontMetrics fm, int maxWidth) {
        int doneEnd = summary.indexOf("! ");
        if (doneEnd < 0)
        {
            lines.addAll(TextUtils.wrapText(summary, fm, maxWidth));
            return;
        }

        String details = summary.substring(doneEnd + 2).trim();
        lines.addAll(TextUtils.wrapText("Result:", fm, maxWidth));
        if (!details.isEmpty())
        {
            lines.addAll(TextUtils.wrapText(details, fm, maxWidth));
        }
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
