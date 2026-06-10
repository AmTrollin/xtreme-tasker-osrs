package com.amtrollin.xtremetasker.ui.current;

import com.amtrollin.xtremetasker.XtremeTaskerConfig;
import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.CompletionInfo;
import net.runelite.client.ui.FontManager;
import com.amtrollin.xtremetasker.models.PrerequisiteStatus;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.models.verification.TaskVerification;
import com.amtrollin.xtremetasker.ui.tasklist.TaskRowsRenderer;
import com.amtrollin.xtremetasker.ui.tasks.models.CollectionLogRequirementItem;
import com.amtrollin.xtremetasker.ui.tasks.models.CollectionLogRequirementPreview;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

import static com.amtrollin.xtremetasker.ui.text.TaskLabelFormatter.sourceLabel;
import static com.amtrollin.xtremetasker.ui.text.TaskLabelFormatter.shortSource;
import static com.amtrollin.xtremetasker.ui.text.TaskLabelFormatter.tierLabel;
import static com.amtrollin.xtremetasker.ui.text.TextUtils.truncateToWidth;
import static com.amtrollin.xtremetasker.ui.text.TextUtils.wrapText;

public final class CurrentTabRenderer
{
    private static final String ACHIEVEMENT_DIARY_NOTE = "Synced from in-game diary completion.";
    private static final int DETAILS_INSET_X = 10;
    private static final BufferedImage QUESTION_ICON = loadQuestionIconSafe();

    private final int panelWidth;
    private final int panelPadding;
    private final int rowHeight;

    private final Color uiGold;
    private final Color uiText;
    private final Color uiTextDim;
    private final Color tabActiveBg;
    private final Color edgeLight;
    private final Color edgeDark;

    private final String wikiButtonText;

    public CurrentTabRenderer(
            int panelWidth,
            int panelPadding,
            int rowHeight,
            Color uiGold,
            Color uiText,
            Color uiTextDim,
            Color tabActiveBg,
            Color edgeLight,
            Color edgeDark,
            String wikiButtonText
    )
    {
        this.panelWidth = panelWidth;
        this.panelPadding = panelPadding;
        this.rowHeight = rowHeight;
        this.uiGold = uiGold;
        this.uiText = uiText;
        this.uiTextDim = uiTextDim;
        this.tabActiveBg = tabActiveBg;
        this.edgeLight = edgeLight;
        this.edgeDark = edgeDark;
        this.wikiButtonText = wikiButtonText;
    }

    /**
     * Render Current tab.
     * Returns layout with bounds for wiki / roll / complete buttons so Overlay can handle clicks.
     * @param scrollOffsetPx pixels already scrolled in the scrollable body (0 = top)
     * @param viewportH      height of the scrollable body region in pixels (0 = auto/no clip)
     */
    public CurrentTabLayout render(
            Graphics2D g,
            FontMetrics fm,
            int panelX,
            int cursorYBaseline,
            Rectangle panelBounds,
            boolean hasTasksLoaded,
            XtremeTask current,
            boolean currentCompleted,
            boolean rolling,
            Function<TaskTier, String> tierProgressLabel,
            Function<TaskTier, Integer> tierPercent, // optional, can be null
            Function<XtremeTask, String> currentLineProvider,
            Function<XtremeTask, List<PrerequisiteStatus>> prerequisiteStatusProvider,
            Function<XtremeTask, CollectionLogRequirementPreview> collectionLogRequirementPreviewProvider,
            Function<TaskTier, List<XtremeTask>> tasksForTierProvider,
            TaskTier tierForProgress,
            TaskSource currentSource,
            XtremeTaskerConfig.RollSourceFilter rollSourceFilter,
            String rollSkipNotice,
            java.awt.Point mousePoint,
            int scrollOffsetPx,
            int viewportH,
            boolean showTips,
            java.awt.image.BufferedImage taskIcon,
            Long taskTimeTicks,
            XtremeTask recentCompletedTask,
            CompletionInfo recentCompletionInfo,
            Long recentTaskTimeTicks
    )
    {
        CurrentTabLayout layout = new CurrentTabLayout();

        layout.wikiButtonBounds.setBounds(0, 0, 0, 0);
        layout.rollButtonBounds.setBounds(0, 0, 0, 0);
        layout.completeButtonBounds.setBounds(0, 0, 0, 0);
        layout.rollSourceIconBounds.setBounds(0, 0, 0, 0);
        layout.viewportBounds.setBounds(0, 0, 0, 0);
        layout.totalContentPx = 0;

        if (!hasTasksLoaded)
        {
            g.setColor(uiTextDim);
            g.drawString("No tasks loaded.", panelX + panelPadding, cursorYBaseline);
            return layout;
        }

        if (tierForProgress == null)
        {
            tierForProgress = TaskTier.EASY;
        }

        // ── Tier progress line (always outside scroll) ─────────────────────────
        String progress = prettyTier(tierForProgress) + " tier progress: " + (tierProgressLabel == null ? "" : tierProgressLabel.apply(tierForProgress));
        progress = truncateToWidth(progress, fm, panelWidth - 2 * panelPadding);

        g.setColor(uiTextDim);
        g.drawString(progress, panelX + panelPadding, cursorYBaseline);
        cursorYBaseline += rowHeight + 14;

        // ── Divider above current task ─────────────────────────────────────────
        final int topDivY = cursorYBaseline - fm.getAscent();
        {
            int divX = panelX + panelPadding;
            int divW = panelWidth - 2 * panelPadding;
            g.setColor(new Color(uiGold.getRed(), uiGold.getGreen(), uiGold.getBlue(), 55));
            g.drawLine(divX, topDivY, divX + divW, topDivY);
        }
        cursorYBaseline += 10;

        // ── Rolling state ──────────────────────────────────────────────────────
        if (rolling)
        {
            g.setColor(uiText);
            g.drawString("Rolling...", panelX + panelPadding, cursorYBaseline);
            cursorYBaseline += rowHeight + 4;

            Font savedRollFont = g.getFont();
            g.setFont(FontManager.getRunescapeFont());
            FontMetrics rollFm = g.getFontMetrics();
            String animName = currentLineProvider != null ? currentLineProvider.apply(current) : "...";
            animName = truncateToWidth(animName, rollFm, panelWidth - 2 * panelPadding);
            g.setColor(uiGold);
            g.drawString(animName, panelX + panelPadding, cursorYBaseline);
            g.setFont(savedRollFont);

            return layout;
        }

        // ── Task name row ──────────────────────────────────────────────────────
        Font savedNameFont = g.getFont();
        if (current != null) g.setFont(FontManager.getRunescapeBoldFont());
        FontMetrics nameFm = g.getFontMetrics();

        // "Current task:" label uses the smaller regular font
        Font prefixFont = FontManager.getRunescapeFont();
        FontMetrics prefixFm = g.getFontMetrics(prefixFont);

        // Task name uses a larger bold font
        Font nameLargeFont = (current != null) ? FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 18f) : g.getFont();
        FontMetrics nameLargeFm = g.getFontMetrics(nameLargeFont);

        // Timer font — slightly larger than the small font
        Font timerFont = FontManager.getRunescapeFont().deriveFont(Font.PLAIN, 14f);
        FontMetrics timerFm = g.getFontMetrics(timerFont);
        boolean showTimer = current != null && taskTimeTicks != null && taskTimeTicks > 0;
        final int timerGap = 6;

        // Wiki button dimensions (computed early so name can use full width)
        String wikiUrl = current != null ? current.getWikiUrl() : null;
        boolean hasWiki = wikiUrl != null && !wikiUrl.trim().isEmpty();
        int wikiW = hasWiki ? fm.stringWidth(wikiButtonText) + 16 : 0;

        int maxNameW = panelWidth - 2 * panelPadding;

        // Icon after task name (smaller)
        final int taskIconSize = 40;
        final int taskIconGap = 6;
        boolean hasIcon = taskIcon != null && current != null;
        int iconReserve = hasIcon ? taskIconSize + taskIconGap : 0;

        // Classic layout: fixed vertical block between dividers
        final int vertPad = 10;
        final int lineGap = 4;
        final int nameLineH = hasIcon ? taskIconSize : nameLargeFm.getHeight();
        final int blockH = prefixFm.getHeight() + lineGap + nameLineH
                + (showTimer ? timerGap + timerFm.getHeight() : 0);
        final int blockTopY  = topDivY + vertPad;
        final int line1Base  = blockTopY + prefixFm.getAscent();
        final int line2TopY  = blockTopY + prefixFm.getHeight() + lineGap;
        final int line2Base  = line2TopY + (nameLineH + nameLargeFm.getAscent() - nameLargeFm.getDescent()) / 2;
        final int timerTopY  = line2TopY + nameLineH + timerGap;
        final int timerBase  = timerTopY + timerFm.getAscent();

        // Fill region between the two dividers with dark brown
        if (current != null)
        {
            int fillBotY = blockTopY + blockH + vertPad + prefixFm.getHeight() + lineGap - fm.getAscent();
            g.setColor(new Color(30, 20, 12, 210));
            g.fillRect(panelX + panelPadding, topDivY + 1, panelWidth - 2 * panelPadding, fillBotY - topDivY - 1);
        }

        if (current != null)
        {
            // Line 1: "Current task:" left-aligned, smaller regular font

            g.setFont(prefixFont);
            g.setColor(Color.WHITE);
            int labelX = panelX + panelPadding + 6;
            g.drawString("Current task:", labelX, line1Base);

            // Line 2: icon + name, larger bold font, centered
            g.setFont(nameLargeFont);
            String name = currentLineProvider != null ? currentLineProvider.apply(current) : "";
            name = truncateToWidth(name, nameLargeFm, maxNameW - iconReserve);
            int nameW = nameLargeFm.stringWidth(name);
            int blockW = iconReserve + nameW;
            int blockStartX = panelX + panelPadding + Math.max(0, (maxNameW - blockW) / 2);

            if (hasIcon)
            {
                g.drawImage(taskIcon, blockStartX, line2TopY, taskIconSize, taskIconSize, null);
            }

            int nameX = blockStartX + iconReserve;
            g.setColor(uiGold);
            g.drawString(name, nameX, line2Base);

            // Timer line: centered below name+icon, inside brown block
            if (showTimer)
            {
                long seconds = Math.round(taskTimeTicks * 0.6);
                String timerText = formatTicks(seconds);
                int timerW = timerFm.stringWidth(timerText);
                int timerX = panelX + panelPadding + (maxNameW - timerW) / 2;
                g.setFont(timerFont);
                g.setColor(uiTextDim);
                g.drawString(timerText, timerX, timerBase);
            }
        }
        else
        {
            drawEmptyCurrentHeader(g, prefixFm, panelX, maxNameW, line1Base);
        }

        if (current != null) g.setFont(savedNameFont);

        // Place bottom divider just after the content block, with padding.
        // Adding prefixFm.getHeight()+lineGap to the bottom pad mathematically
        // centers the name/icon row (not the label) between the two dividers.
        if (current != null)
        {
            cursorYBaseline = blockTopY + blockH + vertPad + prefixFm.getHeight() + lineGap;
        }
        else
        {
            cursorYBaseline = line1Base + prefixFm.getHeight() + prefixFm.getDescent() + 42;
        }

        // ── Divider + badges row (outside scroll) ──────────────────────────────
        if (current != null)
        {
            int divX = panelX + panelPadding;
            int divW = panelWidth - 2 * panelPadding;
            int divY = cursorYBaseline - fm.getAscent();
            g.setColor(new Color(uiGold.getRed(), uiGold.getGreen(), uiGold.getBlue(), 55));
            g.drawLine(divX, divY, divX + divW, divY);
            cursorYBaseline += 10;

            // Badges left-aligned; wiki button right-aligned on the same baseline
            int badgeRowY = cursorYBaseline - fm.getAscent();
            drawBadgesLeftAligned(g, fm, panelX, badgeRowY, currentSource, current.getTier(), mousePoint);

            if (hasWiki)
            {
                int btnH = rowHeight + 6;
                int wikiX = panelX + panelWidth - panelPadding - wikiW;
                int wikiY = badgeRowY + (20 - btnH) / 2;
                layout.wikiButtonBounds.setBounds(wikiX, wikiY, wikiW, btnH);

                drawBevelBox(g, layout.wikiButtonBounds, new Color(30, 25, 18, 220));

                int textW = fm.stringWidth(wikiButtonText);
                g.setColor(uiText);
                g.drawString(wikiButtonText,
                        layout.wikiButtonBounds.x + (layout.wikiButtonBounds.width - textW) / 2,
                        centeredTextBaseline(layout.wikiButtonBounds, fm));
            }

            cursorYBaseline += 20 + 12;

            int buttonHeight = rowHeight + 10;
            int innerW = panelWidth - 2 * panelPadding;
            int buttonWidth = innerW / 2;
            int btnX = panelX + panelPadding + (innerW - buttonWidth) / 2;
            int btnY = cursorYBaseline - fm.getAscent();

            if (!currentCompleted)
            {
                layout.completeButtonBounds.setBounds(btnX, btnY, buttonWidth, buttonHeight);
            }
            else
            {
                layout.rollButtonBounds.setBounds(btnX, btnY, buttonWidth, buttonHeight);
            }

            cursorYBaseline += buttonHeight + 16;
        }

        // ── Scrollable details body (description + prereqs) ────────────────────
        if (current != null)
        {
            int x = panelX + panelPadding + DETAILS_INSET_X;
            int maxW = panelWidth - 2 * (panelPadding + DETAILS_INSET_X);

            // Measure total content height first (needed for scroll clamping + scrollbar)
            int totalPx = 0;
            CollectionLogRequirementPreview requirementPreview = collectionLogRequirementPreviewProvider == null
                    ? null
                    : collectionLogRequirementPreviewProvider.apply(current);
            boolean hasRequirementPreview = requirementPreview != null && requirementPreview.hasItems();
            boolean showAchievementDiaryNote = isAchievementDiaryTask(current);
            boolean hideDescription = hasRequirementPreview || current.getSource() == TaskSource.COLLECTION_LOG;
            String desc = showAchievementDiaryNote
                    ? achievementDiaryDescription(current)
                    : (hideDescription ? null : current.getDescription());
            boolean hasDesc = desc != null && !desc.trim().isEmpty();
            String tip = showTips ? current.getTip() : null;
            boolean hasTip = tip != null && !tip.trim().isEmpty();
            if (hasTip) tip = tip.trim();
            if (hasDesc)
            {
                totalPx += rowHeight; // "Description" header
                List<String> descLines = wrapText(desc, fm, maxW);
                totalPx += rowHeight * Math.min(descLines.size(), 7);
                totalPx += 8;
            }
            totalPx += rowHeight; // "Prereqs" header
            String prereqs = current.getPrereqs();
            boolean hasPrereqs = prereqs != null && !prereqs.trim().isEmpty();
            if (hasPrereqs)
            {
                List<PrerequisiteStatus> statuses = (prerequisiteStatusProvider == null)
                        ? List.of() : prerequisiteStatusProvider.apply(current);
                if (statuses == null || statuses.isEmpty())
                {
                    String formatted = prereqs.replace("\r", "").replaceAll("\\s*;\\s*", "\n").replaceAll("\n{2,}", "\n").trim();
                    for (String line : formatted.split("\n"))
                    {
                        totalPx += rowHeight * wrapText(line, fm, maxW).size();
                    }
                }
                else
                {
                    for (PrerequisiteStatus s : statuses)
                    {
                        totalPx += rowHeight * wrapText("- " + s.getText(), fm, maxW).size();
                    }
                }
            }
            else
            {
                totalPx += rowHeight; // "None"
            }
            totalPx += 8;
            if (hasRequirementPreview)
            {
                totalPx += rowHeight; // "Eligible Collection Log Items" header
                if (requirementPreview.showSummaryText())
                {
                    totalPx += rowHeight; // counter summary
                }
                if (requirementPreview.showItemList())
                {
                    for (CollectionLogRequirementItem item : requirementPreview.getItems())
                    {
                        totalPx += rowHeight * wrapText("- " + collectionLogRequirementItemText(item), fm, maxW).size();
                    }
                }
                totalPx += 8;
            }
            if (hasTip)
            {
                if (hasDesc) totalPx += rowHeight; // blank line before tip only when desc present
                List<String> tipMeasureLines = wrapText(tip, fm, Math.max(hasDesc ? maxW - 8 : maxW, 40));
                totalPx += rowHeight * Math.min(tipMeasureLines.size(), 5);
                totalPx += 8;
            }
            totalPx += fm.getAscent() + 8;

            layout.totalContentPx = totalPx;

            // Viewport top = cursorYBaseline minus ascent so text starts at cursorYBaseline
            int vpTop = cursorYBaseline - fm.getAscent();
            int hintFooterH = fm.getHeight() + panelPadding + 22;
            int actualAvailableH = Math.max(10, panelBounds.y + panelBounds.height - hintFooterH - vpTop);
            int vpH = viewportH > 0 ? Math.min(viewportH, actualAvailableH) : Math.min(totalPx, actualAvailableH);
            layout.viewportBounds.setBounds(panelX, vpTop, panelWidth, vpH);

            // Clamp scroll
            int clampedScroll = Math.max(0, Math.min(scrollOffsetPx, Math.max(0, totalPx - vpH)));

            // Clip to viewport
            Shape oldClip = g.getClip();
            drawCurrentViewportFrame(g, layout.viewportBounds);
            g.setClip(panelX, vpTop, panelWidth, vpH);

            // Draw content shifted by scroll
            int y = cursorYBaseline - clampedScroll;

            if (hasDesc)
            {
                g.setColor(uiGold);
                g.drawString("Description", x, y);
                y += rowHeight;

                g.setColor(uiText);
                y = drawWrapped(g, fm, desc, x, y, maxW, 7);
                y += 8;
            }

            g.setColor(uiGold);
            g.drawString("Prereqs", x, y);
            y += rowHeight;

            if (hasPrereqs)
            {
                List<PrerequisiteStatus> statuses = (prerequisiteStatusProvider == null)
                        ? List.of() : prerequisiteStatusProvider.apply(current);

                if (statuses == null || statuses.isEmpty())
                {
                    g.setColor(uiTextDim);
                    String formatted = prereqs.replace("\r", "").replaceAll("\\s*;\\s*", "\n").replaceAll("\n{2,}", "\n").trim();
                    y = drawWrapped(g, fm, formatted, x, y, maxW, 6);
                }
                else
                {
                    g.setColor(uiTextDim);
                    y = drawPrerequisites(g, fm, x, y, maxW, statuses, 6);
                }
            }
            else
            {
                g.setColor(uiTextDim);
                g.drawString("None", x, y);
                y += rowHeight;
            }
            y += 8;

            if (hasRequirementPreview)
            {
                y = drawCollectionLogRequirementPreview(g, fm, x, y, maxW, requirementPreview);
                y += 8;
            }

            if (hasTip)
            {
                if (hasDesc) y += rowHeight; // blank line before tip only when desc present
                int tipIndent = hasDesc ? 8 : 0;
                List<String> tipLines = wrapText(tip, fm, Math.max(maxW - tipIndent, 40));
                g.setColor(uiTextDim);
                if (!tipLines.isEmpty())
                {
                    g.drawString("Tip: " + tipLines.get(0), x + tipIndent, y);
                    y += rowHeight;
                    for (int i = 1; i < Math.min(tipLines.size(), 5); i++)
                    {
                        g.drawString(tipLines.get(i), x + tipIndent, y);
                        y += rowHeight;
                    }
                }
                y += 8;
            }

            g.setClip(oldClip);

            drawCurrentScrollbar(g, totalPx, vpH, clampedScroll, layout.viewportBounds);
        }
        else
        {
            // No active task — recent completion, roll button, then filter notice below it
            int buttonHeight = rowHeight + 10;
            int innerW = panelWidth - 2 * panelPadding;
            int buttonWidth = innerW / 2;
            int btnX = panelX + panelPadding + (innerW - buttonWidth) / 2;
            int btnY = cursorYBaseline - fm.getAscent() + 18;

            if (recentCompletedTask != null && recentCompletionInfo != null)
            {
                btnY = drawRecentCompletionSummary(
                        g,
                        fm,
                        panelX,
                        cursorYBaseline - fm.getAscent() + 8,
                        innerW,
                        recentCompletedTask,
                        recentCompletionInfo,
                        recentTaskTimeTicks
                ) + 14;
            }

            layout.rollButtonBounds.setBounds(btnX, btnY, buttonWidth, buttonHeight);

            // Roll-source filter notice below the button
            Font savedFont = g.getFont();
            g.setFont(FontManager.getRunescapeSmallFont());
            FontMetrics sfm = g.getFontMetrics();
            int noticeBaselineY = btnY + buttonHeight + rowHeight;

            if (rollSkipNotice != null && !rollSkipNotice.isEmpty())
            {
                int maxNoticeW = panelWidth - 2 * panelPadding;
                List<String> noticeLines = wrapText(rollSkipNotice, sfm, maxNoticeW);
                g.setColor(new Color(uiGold.getRed(), uiGold.getGreen(), uiGold.getBlue(), 210));
                for (String line : noticeLines)
                {
                    int lineX = panelX + panelPadding + (maxNoticeW - sfm.stringWidth(line)) / 2;
                    g.drawString(line, lineX, noticeBaselineY);
                    noticeBaselineY += sfm.getHeight();
                }
            }
            else if (rollSourceFilter != null && rollSourceFilter != XtremeTaskerConfig.RollSourceFilter.ALL)
            {
                final int iconSize = sfm.getAscent() + 2;
                final int iconGap = 4;
                String filterLabel = rollSourceFilter == XtremeTaskerConfig.RollSourceFilter.CA_ONLY ? "Combat Achievement" : "CLOG/DA";
                String notice = "Rolling " + filterLabel + " tasks only";
                int noticeW = sfm.stringWidth(notice);
                int rowW = noticeW + iconGap + iconSize;
                int noticeX = panelX + (panelWidth - rowW) / 2;

                g.setColor(new Color(uiGold.getRed(), uiGold.getGreen(), uiGold.getBlue(), 200));
                g.drawString(notice, noticeX, noticeBaselineY);

                int iconX = noticeX + noticeW + iconGap;
                int iconY = noticeBaselineY - sfm.getAscent();

                drawQuestionIcon(g, iconX, iconY, iconSize);

                layout.rollSourceIconBounds.setBounds(iconX, iconY, iconSize, iconSize);

                if (mousePoint != null && layout.rollSourceIconBounds.contains(mousePoint))
                {
                    String tip = "You can change this in the plugin settings";
                    int tipW = sfm.stringWidth(tip) + 10;
                    int tipH = sfm.getHeight() + 4;
                    int tipX = iconX + (iconSize - tipW) / 2;
                    int tipY = iconY + iconSize + 2;
                    if (tipX < panelX + panelPadding) tipX = panelX + panelPadding;
                    if (tipX + tipW > panelX + panelWidth) tipX = panelX + panelWidth - tipW;
                    g.setColor(uiTextDim);
                    g.drawString(tip, tipX + 5, tipY + ((tipH - sfm.getHeight()) / 2) + sfm.getAscent());
                }
            }

            g.setFont(savedFont);
        }

        return layout;
    }

    private static String prettyTier(TaskTier t)
    {
        if (t == null) return "";
        return tierLabel(t);
    }

    private void drawEmptyCurrentHeader(Graphics2D g, FontMetrics fm, int panelX, int maxW, int baselineY)
    {
        String title = "No active task";
        g.setColor(uiText);
        g.drawString(title, panelX + panelPadding, baselineY);

        String prompt = "Roll a task when you're ready.";
        prompt = truncateToWidth(prompt, fm, maxW);
        g.setColor(uiTextDim);
        g.drawString(prompt, panelX + panelPadding, baselineY + fm.getHeight());
    }

    private void drawCenteredQuestionMark(Graphics2D g, int x, int y, int size)
    {
        java.awt.font.GlyphVector glyph = g.getFont().createGlyphVector(g.getFontRenderContext(), "?");
        java.awt.geom.Rectangle2D visualBounds = glyph.getVisualBounds();
        float textX = (float) (x + (size - visualBounds.getWidth()) / 2.0 - visualBounds.getX());
        float textY = (float) (y + (size - visualBounds.getHeight()) / 2.0 - visualBounds.getY());
        g.drawString("?", textX, textY);
    }

    private void drawQuestionIcon(Graphics2D g, int x, int y, int size)
    {
        if (QUESTION_ICON != null)
        {
            g.drawImage(QUESTION_ICON, x, y, size, size, null);
            return;
        }

        g.setColor(new Color(uiGold.getRed(), uiGold.getGreen(), uiGold.getBlue(), 140));
        g.fillOval(x, y, size, size);
        g.setColor(new Color(20, 15, 10, 220));
        drawCenteredQuestionMark(g, x, y, size);
    }

    private static BufferedImage loadQuestionIconSafe()
    {
        try (InputStream in = CurrentTabRenderer.class.getResourceAsStream("/icons/notifications/OSRS_question.png"))
        {
            return in == null ? null : ImageIO.read(in);
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    private int drawRecentCompletionSummary(
            Graphics2D g,
            FontMetrics fm,
            int panelX,
            int topY,
            int innerW,
            XtremeTask task,
            CompletionInfo info,
            Long ticks
    )
    {
        int x = panelX + panelPadding;
        int y = topY;
        int lineH = fm.getHeight();

        String label = "Most recent completed:";
        label = truncateToWidth(label, fm, innerW);
        g.setColor(uiGold);
        g.drawString(label, x, y);
        y += lineH;

        String name = truncateToWidth(task.getName() + completionSourceSuffix(info), fm, innerW);
        g.setColor(uiText);
        g.drawString(name, x, y);
        y += lineH;

        String completed = formatCompletionSummary(info);
        completed = truncateToWidth(completed, fm, innerW);
        g.setColor(uiTextDim);
        g.drawString(completed, x, y);
        y += lineH;

        if (ticks != null && ticks > 0)
        {
            String time = "Time spent: " + formatTicks(Math.round(ticks * 0.6));
            time = truncateToWidth(time, fm, innerW);
            g.drawString(time, x, y);
            y += lineH;
        }

        return y;
    }

    private static String formatCompletionSummary(CompletionInfo info)
    {
        if (info.timestamp <= 0)
        {
            return "Completed: date unknown";
        }
        return "Completed: " + new SimpleDateFormat("MMM d, h:mm a").format(new Date(info.timestamp));
    }

    private static String completionSourceSuffix(CompletionInfo info)
    {
        if (info == null || info.timestamp <= 0 || info.source == null)
        {
            return "";
        }

        if (info.source == CompletionInfo.Source.MANUAL)
        {
            return "";
        }

        if (info.source == CompletionInfo.Source.SYNCED)
        {
            return " (synced)";
        }

        return "";
    }

    private static String formatTicks(long seconds)
    {
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        long remSeconds = seconds % 60;
        if (minutes < 60) return remSeconds > 0 ? minutes + "m " + remSeconds + "s" : minutes + "m";
        long hours = minutes / 60;
        long remMinutes = minutes % 60;
        if (hours < 24) return remMinutes > 0 ? hours + "h " + remMinutes + "m" : hours + "h";
        long days = hours / 24;
        long remHours = hours % 24;
        return remHours > 0 ? days + "d " + remHours + "h" : days + "d";
    }

    private void drawBevelBox(Graphics2D g, Rectangle r, Color fill)
    {
        TaskRowsRenderer.drawBevelBoxLogic(g, r, fill, edgeDark, edgeLight);
    }

    private int centeredTextBaseline(Rectangle bounds, FontMetrics fm)
    {
        return bounds.y + ((bounds.height - fm.getHeight()) / 2) + fm.getAscent();
    }

    private int drawWrapped(Graphics2D g, FontMetrics fm, String text, int x, int yBaseline, int maxWidth, int maxLines)
    {
        List<String> lines = wrapText(text, fm, maxWidth);
        int y = yBaseline;
        int drawn = 0;

        for (String line : lines)
        {
            if (drawn >= maxLines) break;

            if (line.isEmpty())
            {
                y += rowHeight;
                drawn++;
                continue;
            }

            g.drawString(truncateToWidth(line, fm, maxWidth), x, y);
            y += rowHeight;
            drawn++;
        }

        return y;
    }

    private int drawPrerequisites(
            Graphics2D g,
            FontMetrics fm,
            int x,
            int yBaseline,
            int maxWidth,
            List<PrerequisiteStatus> statuses,
            int maxLines
    )
    {
        int y = yBaseline;
        int drawn = 0;

        for (PrerequisiteStatus status : statuses)
        {
            String lineText = "- " + status.getText();
            for (String line : wrapText(lineText, fm, maxWidth))
            {
                if (drawn >= maxLines)
                {
                    return y;
                }

                String drawLine = truncateToWidth(line, fm, maxWidth);
                g.setColor(status.isCompleted() ? uiTextDim : uiText);
                g.drawString(drawLine, x, y);

                if (status.isCompleted())
                {
                    int lineW = fm.stringWidth(drawLine);
                    int strikeY = y - (fm.getAscent() * 3 / 5);
                    g.setColor(new Color(uiTextDim.getRed(), uiTextDim.getGreen(), uiTextDim.getBlue(), 170));
                    g.drawLine(x, strikeY, x + lineW, strikeY);
                }

                y += rowHeight;
                drawn++;
            }
        }

        return y;
    }

    private int drawCollectionLogRequirementPreview(
            Graphics2D g,
            FontMetrics fm,
            int x,
            int yBaseline,
            int maxWidth,
            CollectionLogRequirementPreview requirementPreview
    )
    {
        int y = yBaseline;

        g.setColor(uiGold);
        g.drawString(collectionLogRequirementTitle(requirementPreview), x, y);
        y += rowHeight;

        if (requirementPreview.showSummaryText())
        {
            drawCollectionLogSummaryText(g, fm, requirementPreview.summaryText(), x, y, maxWidth);
            y += rowHeight;
        }

        if (requirementPreview.showItemList())
        {
            for (CollectionLogRequirementItem item : requirementPreview.getItems())
            {
                String lineText = "- " + collectionLogRequirementItemText(item);
                for (String line : wrapText(lineText, fm, maxWidth))
                {
                    String drawLine = truncateToWidth(line, fm, maxWidth);
                    g.setColor(item.isApplied() ? uiTextDim : item.isAvailable() ? uiGold : uiText);
                    g.drawString(drawLine, x, y);

                    if (item.isApplied())
                    {
                        drawStrikeThrough(g, fm, drawLine, x, y);
                    }

                    y += rowHeight;
                }
            }
        }

        return y;
    }

    private void drawCollectionLogSummaryText(Graphics2D g, FontMetrics fm, String summaryText, int x, int y, int maxWidth)
    {
        String text = safe(summaryText);
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
        g.setColor(uiGold);
        g.drawString(truncateToWidth(suffix, fm, maxWidth - prefixWidth), x + prefixWidth, y);
    }

    private static String collectionLogRequirementItemText(CollectionLogRequirementItem item)
    {
        if (item == null)
        {
            return "";
        }
        return safe(item.getName()) + (item.isAvailable() ? " (not yet applied)" : "");
    }

    private void drawCurrentScrollbar(Graphics2D g, int totalPx, int viewportH, int scrollPx, Rectangle viewport)
    {
        if (totalPx <= viewportH || viewportH <= 0)
        {
            return;
        }

        final int scrollBarW = 4;
        int railX = viewport.x + viewport.width - panelPadding - 2;
        int railY = viewport.y + 4;
        int railH = Math.max(1, viewport.height - 8);

        g.setColor(new Color(uiGold.getRed(), uiGold.getGreen(), uiGold.getBlue(), 45));
        g.fillRoundRect(railX, railY, scrollBarW, railH, 4, 4);

        float thumbRatio = (float) viewportH / (float) totalPx;
        int thumbH = Math.max(14, (int) (railH * thumbRatio));
        int maxScrollPx = Math.max(1, totalPx - viewportH);
        float scrollRatio = Math.max(0f, Math.min(1f, (float) scrollPx / (float) maxScrollPx));
        int thumbY = railY + (int) ((railH - thumbH) * scrollRatio);

        g.setColor(new Color(uiGold.getRed(), uiGold.getGreen(), uiGold.getBlue(), 175));
        g.fillRoundRect(railX, thumbY, scrollBarW, thumbH, 4, 4);
    }

    private void drawCurrentViewportFrame(Graphics2D g, Rectangle viewport)
    {
        if (viewport.width <= 0 || viewport.height <= 0)
        {
            return;
        }

        int x = viewport.x + panelPadding + DETAILS_INSET_X;
        int y = viewport.y - 6;
        int w = viewport.width - 2 * (panelPadding + DETAILS_INSET_X);
        int h = viewport.height + 12;

        g.setColor(new Color(uiGold.getRed(), uiGold.getGreen(), uiGold.getBlue(), 62));
        g.drawLine(x, y, x + w, y);
        g.drawLine(x, y + h, x + w, y + h);
    }

    private static String collectionLogRequirementTitle(CollectionLogRequirementPreview requirementPreview)
    {
        return requirementPreview != null && requirementPreview.showSummaryText() && !requirementPreview.showItemList()
                ? "Collection Log Progress"
                : "Eligible Collection Log Items";
    }

    private void drawBadgesLeftAligned(Graphics2D g, FontMetrics fm, int panelX, int yTop, TaskSource src, TaskTier tier, java.awt.Point mousePoint)
    {
        if (src == null && tier == null) return;
        int x = panelX + panelPadding;
        final int badgeGap = 4;
        if (src != null)
        {
            String srcText = shortSource(src);
            int w = TaskRowsRenderer.drawSourceBadge(g, x, yTop, srcText, edgeDark, edgeLight, uiGold, uiText);
            Rectangle srcBounds = new Rectangle(x, yTop, w, rowHeight + 4);
            if (mousePoint != null && srcBounds.contains(mousePoint))
            {
                drawBadgeHoverText(g, fm, sourceLabel(src), srcBounds);
            }
            x += w + badgeGap;
        }
        if (tier != null)
        {
            TaskRowsRenderer.drawSourceBadge(g, x, yTop, tierLabel(tier), edgeDark, edgeLight, uiGold, uiText);
        }
    }

    private void drawBadgeHoverText(Graphics2D g, FontMetrics fm, String text, Rectangle badgeBounds)
    {
        g.setColor(uiTextDim);
        g.drawString(text, badgeBounds.x, badgeBounds.y - 4);
    }

    private void drawBadgesNearText(
            Graphics2D g, FontMetrics fm,
            int panelX, int rowBaselineY,
            TaskSource src,
            TaskTier tier,
            String lineText
    )
    {
        if (src == null && tier == null) return;

        final int h = 20;
        final int gap = 8;
        final int badgeGap = 4;

        FontMetrics sfm = g.getFontMetrics(FontManager.getRunescapeSmallFont());
        String srcText = src != null ? shortSource(src) : null;
        String tierText = tier != null ? tierLabel(tier) : null;

        int srcW = srcText != null ? Math.max(26, sfm.stringWidth(srcText) + 16) : 0;
        int tierW = tierText != null ? Math.max(26, sfm.stringWidth(tierText) + 16) : 0;
        int totalW = srcW + (srcText != null && tierText != null ? badgeGap : 0) + tierW;

        int contentLeft = panelX + panelPadding;
        int contentRight = panelX + panelWidth - panelPadding;
        int lineW = (lineText == null) ? 0 : fm.stringWidth(lineText);
        int x = Math.min(contentLeft + lineW + gap, contentRight - totalW);

        final int verticalNudge = -2;
        int y = (rowBaselineY - fm.getAscent())
                + (rowHeight - h) / 2
                + verticalNudge;

        if (srcText != null) {
            srcW = TaskRowsRenderer.drawSourceBadge(g, x, y, srcText, edgeDark, edgeLight, uiGold, uiText);
            x += srcW + badgeGap;
        }
        if (tierText != null) {
            TaskRowsRenderer.drawSourceBadge(g, x, y, tierText, edgeDark, edgeLight, uiGold, uiText);
        }
    }

    private void drawStrikeThrough(Graphics2D g, FontMetrics fm, String text, int x, int baselineY)
    {
        int lineW = fm.stringWidth(text);
        int strikeY = baselineY - (fm.getAscent() * 3 / 5);
        g.setColor(new Color(uiTextDim.getRed(), uiTextDim.getGreen(), uiTextDim.getBlue(), 170));
        g.drawLine(x, strikeY, x + lineW, strikeY);
    }

    private static String safe(String text)
    {
        return text == null ? "" : text;
    }

    private static boolean isAchievementDiaryTask(XtremeTask task)
    {
        TaskVerification verification = task == null ? null : task.getVerification();
        return verification != null && verification.getType() == TaskVerification.VerificationType.ACHIEVEMENT_DIARY;
    }

    private static String achievementDiaryDescription(XtremeTask task)
    {
        TaskVerification verification = task == null ? null : task.getVerification();
        if (verification == null)
        {
            return ACHIEVEMENT_DIARY_NOTE;
        }

        String region = titleCase(verification.getRegion());
        String difficulty = titleCase(verification.getDifficulty());
        if (region == null && difficulty == null)
        {
            return ACHIEVEMENT_DIARY_NOTE;
        }

        String detail = region == null ? difficulty : difficulty == null ? region : region + " - " + difficulty;
        return "Diary Achievement: " + detail + ". " + ACHIEVEMENT_DIARY_NOTE;
    }

    private static String titleCase(String value)
    {
        if (value == null || value.trim().isEmpty())
        {
            return null;
        }

        String trimmed = value.trim().replace('-', ' ').replace('_', ' ');
        StringBuilder out = new StringBuilder(trimmed.length());
        boolean capitalize = true;
        for (int i = 0; i < trimmed.length(); i++)
        {
            char ch = trimmed.charAt(i);
            if (Character.isWhitespace(ch))
            {
                out.append(ch);
                capitalize = true;
                continue;
            }

            out.append(capitalize ? Character.toUpperCase(ch) : Character.toLowerCase(ch));
            capitalize = false;
        }
        return out.toString();
    }

}
