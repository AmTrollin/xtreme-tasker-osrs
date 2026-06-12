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

        // ── Current task area starts below progress ────────────────────────────
        final int topDivY = cursorYBaseline - fm.getAscent();
        cursorYBaseline += 10;

        // ── Rolling state ──────────────────────────────────────────────────────
        if (rolling)
        {
            return renderRollingCurrentSplit(
                    g,
                    fm,
                    panelX,
                    topDivY,
                    panelBounds,
                    current,
                    currentLineProvider,
                    layout
            );
        }

        if (current != null)
        {
            return renderActiveCurrentSplit(
                    g,
                    fm,
                    panelX,
                    topDivY,
                    panelBounds,
                    current,
                    currentCompleted,
                    currentLineProvider,
                    prerequisiteStatusProvider,
                    collectionLogRequirementPreviewProvider,
                    currentSource,
                    mousePoint,
                    scrollOffsetPx,
                    viewportH,
                    showTips,
                    taskIcon,
                    taskTimeTicks,
                    layout
            );
        }

        return renderEmptyCurrentSplit(
                g,
                fm,
                panelX,
                topDivY,
                panelBounds,
                rollSourceFilter,
                rollSkipNotice,
                mousePoint,
                recentCompletedTask,
                recentCompletionInfo,
                recentTaskTimeTicks,
                layout
        );

    }

    private CurrentTabLayout renderRollingCurrentSplit(
            Graphics2D g,
            FontMetrics fm,
            int panelX,
            int topDivY,
            Rectangle panelBounds,
            XtremeTask current,
            Function<XtremeTask, String> currentLineProvider,
            CurrentTabLayout layout
    )
    {
        int innerW = panelWidth - 2 * panelPadding;
        int gap = 12;
        int leftW = Math.max(190, (innerW - gap) / 2);
        int rightW = Math.max(180, innerW - leftW - gap);
        int leftX = panelX + panelPadding;
        int rightX = leftX + leftW + gap;

        int contentTop = topDivY + 10;
        int hintFooterH = fm.getHeight() + panelPadding + 22;
        int contentBottom = panelBounds.y + panelBounds.height - hintFooterH;
        int contentH = Math.max(rowHeight * 7, contentBottom - contentTop);

        Rectangle leftCard = new Rectangle(leftX, contentTop - 6, leftW, contentH + 12);
        drawBevelBox(g, leftCard, new Color(26, 17, 10, 225));

        int dividerX = rightX - gap / 2;
        g.setColor(new Color(uiGold.getRed(), uiGold.getGreen(), uiGold.getBlue(), 55));
        g.drawLine(dividerX, leftCard.y, dividerX, leftCard.y + leftCard.height);

        drawRollingIdentityColumn(g, leftCard, current, currentLineProvider);

        layout.viewportBounds.setBounds(rightX, contentTop, rightW, contentH);
        layout.totalContentPx = 0;
        return layout;
    }

    private CurrentTabLayout renderEmptyCurrentSplit(
            Graphics2D g,
            FontMetrics fm,
            int panelX,
            int topDivY,
            Rectangle panelBounds,
            XtremeTaskerConfig.RollSourceFilter rollSourceFilter,
            String rollSkipNotice,
            java.awt.Point mousePoint,
            XtremeTask recentCompletedTask,
            CompletionInfo recentCompletionInfo,
            Long recentTaskTimeTicks,
            CurrentTabLayout layout
    )
    {
        int innerW = panelWidth - 2 * panelPadding;
        int gap = 12;
        int leftW = Math.max(190, (innerW - gap) / 2);
        int rightW = Math.max(180, innerW - leftW - gap);
        int leftX = panelX + panelPadding;
        int rightX = leftX + leftW + gap;

        int contentTop = topDivY + 10;
        int hintFooterH = fm.getHeight() + panelPadding + 22;
        int contentBottom = panelBounds.y + panelBounds.height - hintFooterH;
        int contentH = Math.max(rowHeight * 7, contentBottom - contentTop);

        Rectangle leftCard = new Rectangle(leftX, contentTop - 6, leftW, contentH + 12);
        drawBevelBox(g, leftCard, new Color(26, 17, 10, 225));

        int dividerX = rightX - gap / 2;
        g.setColor(new Color(uiGold.getRed(), uiGold.getGreen(), uiGold.getBlue(), 55));
        g.drawLine(dividerX, leftCard.y, dividerX, leftCard.y + leftCard.height);

        drawEmptyCurrentIdentityColumn(
                g,
                fm,
                leftCard,
                rollSourceFilter,
                rollSkipNotice,
                mousePoint,
                recentCompletedTask,
                recentCompletionInfo,
                recentTaskTimeTicks,
                layout
        );

        layout.viewportBounds.setBounds(rightX, contentTop, rightW, contentH);
        layout.totalContentPx = 0;
        return layout;
    }

    private CurrentTabLayout renderActiveCurrentSplit(
            Graphics2D g,
            FontMetrics fm,
            int panelX,
            int topDivY,
            Rectangle panelBounds,
            XtremeTask current,
            boolean currentCompleted,
            Function<XtremeTask, String> currentLineProvider,
            Function<XtremeTask, List<PrerequisiteStatus>> prerequisiteStatusProvider,
            Function<XtremeTask, CollectionLogRequirementPreview> collectionLogRequirementPreviewProvider,
            TaskSource currentSource,
            java.awt.Point mousePoint,
            int scrollOffsetPx,
            int viewportH,
            boolean showTips,
            java.awt.image.BufferedImage taskIcon,
            Long taskTimeTicks,
            CurrentTabLayout layout
    )
    {
        int innerW = panelWidth - 2 * panelPadding;
        int gap = 12;
        int leftW = Math.max(190, (innerW - gap) / 2);
        int rightW = Math.max(180, innerW - leftW - gap);
        int leftX = panelX + panelPadding;
        int rightX = leftX + leftW + gap;

        int contentTop = topDivY + 10;
        int hintFooterH = fm.getHeight() + panelPadding + 22;
        int contentBottom = panelBounds.y + panelBounds.height - hintFooterH;
        int contentH = Math.max(rowHeight * 7, contentBottom - contentTop);

        Rectangle leftCard = new Rectangle(leftX, contentTop - 6, leftW, contentH + 12);
        drawBevelBox(g, leftCard, new Color(26, 17, 10, 225));

        int dividerX = rightX - gap / 2;
        g.setColor(new Color(uiGold.getRed(), uiGold.getGreen(), uiGold.getBlue(), 55));
        g.drawLine(dividerX, leftCard.y, dividerX, leftCard.y + leftCard.height);

        int wikiTop = leftCard.y + 10;
        drawDetailsWikiButton(g, fm, rightX, rightW, wikiTop, current, layout);

        drawCurrentTaskIdentityColumn(
                g,
                fm,
                leftCard,
                current,
                currentCompleted,
                currentLineProvider,
                currentSource,
                mousePoint,
                taskIcon,
                taskTimeTicks,
                layout
        );

        int detailsX = rightX + DETAILS_INSET_X;
        int detailsW = Math.max(40, rightW - DETAILS_INSET_X * 2 - 12);
        int detailsTop = contentTop;
        if (layout.wikiButtonBounds.width > 0)
        {
            detailsTop = Math.max(detailsTop, layout.wikiButtonBounds.y + layout.wikiButtonBounds.height + 8);
        }
        int detailsH = Math.max(0, contentTop + contentH - detailsTop);
        int totalPx = measureCurrentDetails(
                g,
                fm,
                detailsW,
                current,
                prerequisiteStatusProvider,
                collectionLogRequirementPreviewProvider,
                showTips
        );
        layout.totalContentPx = totalPx;

        int vpH = viewportH > 0 ? Math.min(viewportH, detailsH) : detailsH;
        layout.viewportBounds.setBounds(rightX, detailsTop, rightW, vpH);

        int clampedScroll = Math.max(0, Math.min(scrollOffsetPx, Math.max(0, totalPx - vpH)));
        Shape oldClip = g.getClip();
        drawCurrentViewportFrame(g, new Rectangle(rightX, contentTop, rightW, contentH));
        g.setClip(layout.viewportBounds);

        int y = detailsTop + fm.getAscent() + 4 - clampedScroll;
        drawCurrentDetails(
                g,
                fm,
                detailsX,
                y,
                detailsW,
                current,
                prerequisiteStatusProvider,
                collectionLogRequirementPreviewProvider,
                showTips
        );

        g.setClip(oldClip);
        drawCurrentScrollbar(g, totalPx, vpH, clampedScroll, layout.viewportBounds);

        return layout;
    }

    private void drawDetailsWikiButton(
            Graphics2D g,
            FontMetrics fm,
            int rightX,
            int rightW,
            int yTop,
            XtremeTask current,
            CurrentTabLayout layout
    )
    {
        String wikiUrl = current.getWikiUrl();
        if (wikiUrl == null || wikiUrl.trim().isEmpty())
        {
            return;
        }

        String wikiLabel = "Wiki";
        int wikiW = Math.max(44, fm.stringWidth(wikiLabel) + 18);
        int wikiH = rowHeight + 6;
        int wikiX = rightX + DETAILS_INSET_X;
        layout.wikiButtonBounds.setBounds(wikiX, yTop, wikiW, wikiH);
        drawBevelBox(g, layout.wikiButtonBounds, new Color(30, 25, 18, 220));

        int textW = fm.stringWidth(wikiLabel);
        g.setColor(uiText);
        g.drawString(wikiLabel,
                layout.wikiButtonBounds.x + (layout.wikiButtonBounds.width - textW) / 2,
                centeredTextBaseline(layout.wikiButtonBounds, fm));
    }

    private void drawEmptyCurrentIdentityColumn(
            Graphics2D g,
            FontMetrics fm,
            Rectangle card,
            XtremeTaskerConfig.RollSourceFilter rollSourceFilter,
            String rollSkipNotice,
            java.awt.Point mousePoint,
            XtremeTask recentCompletedTask,
            CompletionInfo recentCompletionInfo,
            Long recentTaskTimeTicks,
            CurrentTabLayout layout
    )
    {
        Font savedFont = g.getFont();
        Font prefixFont = FontManager.getRunescapeFont();
        Font titleFont = FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 18f);
        Font smallFont = FontManager.getRunescapeSmallFont();
        FontMetrics prefixFm = g.getFontMetrics(prefixFont);
        FontMetrics titleFm = g.getFontMetrics(titleFont);
        FontMetrics smallFm = g.getFontMetrics(smallFont);

        int x = card.x + 18;
        int innerW = card.width - 36;
        int y = card.y + Math.max(18, card.height / 18);

        g.setFont(prefixFont);
        g.setColor(Color.WHITE);
        g.drawString("No current task", x, y + prefixFm.getAscent());
        y += prefixFm.getHeight() + Math.max(24, card.height / 11);

        if (recentCompletedTask != null && recentCompletionInfo != null)
        {
            y = drawRecentCompletionSummary(g, fm, x - panelPadding, y, innerW, recentCompletedTask, recentCompletionInfo, recentTaskTimeTicks);
            y += Math.max(22, card.height / 12);
        }
        else
        {
            g.setFont(titleFont);
            String title = "No active task";
            int titleX = x + Math.max(0, (innerW - titleFm.stringWidth(title)) / 2);
            g.setColor(uiGold);
            g.drawString(title, titleX, y + titleFm.getAscent());
            y += titleFm.getHeight() + 12;

            g.setFont(prefixFont);
            String prompt = truncateToWidth("Roll a task when you're ready.", prefixFm, innerW);
            int promptX = x + Math.max(0, (innerW - prefixFm.stringWidth(prompt)) / 2);
            g.setColor(uiTextDim);
            g.drawString(prompt, promptX, y + prefixFm.getAscent());
            y += prefixFm.getHeight() + Math.max(28, card.height / 10);
        }

        int buttonH = rowHeight + 10;
        int buttonW = Math.max(110, Math.min(innerW, card.width - 36));
        int buttonX = card.x + (card.width - buttonW) / 2;
        int buttonY = Math.min(card.y + card.height - buttonH - 54, y);
        layout.rollButtonBounds.setBounds(buttonX, buttonY, buttonW, buttonH);

        int noticeBaselineY = buttonY + buttonH + Math.max(rowHeight + 2, card.height / 15);
        g.setFont(smallFont);
        if (rollSkipNotice != null && !rollSkipNotice.isEmpty())
        {
            List<String> noticeLines = wrapText(rollSkipNotice, smallFm, innerW);
            g.setColor(new Color(uiGold.getRed(), uiGold.getGreen(), uiGold.getBlue(), 210));
            for (String line : noticeLines)
            {
                int lineX = x + Math.max(0, (innerW - smallFm.stringWidth(line)) / 2);
                g.drawString(line, lineX, noticeBaselineY);
                noticeBaselineY += smallFm.getHeight();
            }
        }
        else if (rollSourceFilter != null && rollSourceFilter != XtremeTaskerConfig.RollSourceFilter.ALL)
        {
            final int iconSize = smallFm.getAscent() + 2;
            final int iconGap = 4;
            String filterLabel = rollSourceFilter == XtremeTaskerConfig.RollSourceFilter.CA_ONLY ? "Combat Achievement" : "CLOG/AD";
            String notice = "Rolling " + filterLabel + " tasks only";
            int noticeW = smallFm.stringWidth(notice);
            int rowW = noticeW + iconGap + iconSize;
            int noticeX = x + Math.max(0, (innerW - rowW) / 2);

            g.setColor(new Color(uiGold.getRed(), uiGold.getGreen(), uiGold.getBlue(), 200));
            g.drawString(notice, noticeX, noticeBaselineY);

            int iconX = noticeX + noticeW + iconGap;
            int iconY = noticeBaselineY - smallFm.getAscent();
            drawQuestionIcon(g, iconX, iconY, iconSize);
            layout.rollSourceIconBounds.setBounds(iconX, iconY, iconSize, iconSize);

            if (mousePoint != null && layout.rollSourceIconBounds.contains(mousePoint))
            {
                String tip = "You can change this in the plugin settings";
                int tipW = smallFm.stringWidth(tip) + 10;
                int tipX = iconX + (iconSize - tipW) / 2;
                if (tipX < card.x + 8) tipX = card.x + 8;
                if (tipX + tipW > card.x + card.width - 8) tipX = card.x + card.width - 8 - tipW;
                g.setColor(uiTextDim);
                g.drawString(tip, tipX + 5, iconY + iconSize + smallFm.getAscent() + 2);
            }
        }

        g.setFont(savedFont);
    }

    private void drawRollingIdentityColumn(
            Graphics2D g,
            Rectangle card,
            XtremeTask current,
            Function<XtremeTask, String> currentLineProvider
    )
    {
        Font savedFont = g.getFont();
        Font prefixFont = FontManager.getRunescapeFont();
        Font nameFont = FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 18f);
        FontMetrics prefixFm = g.getFontMetrics(prefixFont);
        FontMetrics nameFm = g.getFontMetrics(nameFont);

        int x = card.x + 18;
        int innerW = card.width - 36;
        int y = card.y + Math.max(18, card.height / 18);

        g.setFont(prefixFont);
        g.setColor(Color.WHITE);
        g.drawString("Rolling...", x, y + prefixFm.getAscent());

        String animName = currentLineProvider != null && current != null ? currentLineProvider.apply(current) : "...";
        animName = animName == null || animName.trim().isEmpty() ? "..." : animName.trim();
        List<String> nameLines = wrapText(animName, nameFm, innerW);
        int lineCount = Math.max(1, Math.min(nameLines.size(), 2));
        int blockH = lineCount * nameFm.getHeight();
        int nameY = card.y + (card.height - blockH) / 2;

        g.setFont(nameFont);
        g.setColor(uiGold);
        for (int i = 0; i < lineCount; i++)
        {
            String line = truncateToWidth(nameLines.get(i), nameFm, innerW);
            int lineX = x + Math.max(0, (innerW - nameFm.stringWidth(line)) / 2);
            g.drawString(line, lineX, nameY + nameFm.getAscent());
            nameY += nameFm.getHeight();
        }

        g.setFont(savedFont);
    }

    private void drawCurrentTaskIdentityColumn(
            Graphics2D g,
            FontMetrics fm,
            Rectangle card,
            XtremeTask current,
            boolean currentCompleted,
            Function<XtremeTask, String> currentLineProvider,
            TaskSource currentSource,
            java.awt.Point mousePoint,
            java.awt.image.BufferedImage taskIcon,
            Long taskTimeTicks,
            CurrentTabLayout layout
    )
    {
        Font savedFont = g.getFont();
        Font prefixFont = FontManager.getRunescapeFont();
        Font nameFont = FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 18f);
        Font timerFont = FontManager.getRunescapeFont().deriveFont(Font.PLAIN, 14f);
        FontMetrics prefixFm = g.getFontMetrics(prefixFont);
        FontMetrics nameFm = g.getFontMetrics(nameFont);
        FontMetrics timerFm = g.getFontMetrics(timerFont);

        int x = card.x + 18;
        int innerW = card.width - 36;
        int y = card.y + Math.max(18, card.height / 18);

        g.setFont(prefixFont);
        g.setColor(Color.WHITE);
        g.drawString("Current task:", x, y + prefixFm.getAscent());
        y += prefixFm.getHeight() + Math.max(14, card.height / 24);

        int metaTop = y;
        drawBadgesRightAligned(g, fm, card.x + card.width - 12, metaTop, currentSource, current.getTier(), mousePoint);
        y += rowHeight + Math.max(20, card.height / 13);

        int iconSize = 58;
        if (taskIcon != null)
        {
            int iconX = x + Math.max(0, (innerW - iconSize) / 2);
            g.drawImage(taskIcon, iconX, y, iconSize, iconSize, null);
            y += iconSize + Math.max(10, card.height / 30);
        }

        g.setFont(nameFont);
        String name = currentLineProvider != null ? currentLineProvider.apply(current) : current.getName();
        List<String> nameLines = wrapText(name, nameFm, innerW);
        g.setColor(uiGold);
        int drawn = 0;
        for (String line : nameLines)
        {
            if (drawn >= 2)
            {
                break;
            }

            String drawLine = truncateToWidth(line, nameFm, innerW);
            int lineX = x + Math.max(0, (innerW - nameFm.stringWidth(drawLine)) / 2);
            g.drawString(drawLine, lineX, y + nameFm.getAscent());
            y += nameFm.getHeight();
            drawn++;
        }

        boolean showTimer = taskTimeTicks != null && taskTimeTicks > 0;
        if (showTimer)
        {
            y += Math.max(8, card.height / 36);
            long seconds = Math.round(taskTimeTicks * 0.6);
            String timerText = formatTicks(seconds);
            int timerX = x + Math.max(0, (innerW - timerFm.stringWidth(timerText)) / 2);
            g.setFont(timerFont);
            g.setColor(uiTextDim);
            g.drawString(timerText, timerX, y + timerFm.getAscent());
            y += timerFm.getHeight();
        }

        int buttonH = rowHeight + 10;
        int buttonW = Math.max(110, Math.min(innerW, card.width - 36));
        int buttonX = card.x + (card.width - buttonW) / 2;
        int buttonY = Math.min(card.y + card.height - buttonH - 34, y + Math.max(30, card.height / 10));
        if (!currentCompleted)
        {
            layout.completeButtonBounds.setBounds(buttonX, buttonY, buttonW, buttonH);
        }
        else
        {
            layout.rollButtonBounds.setBounds(buttonX, buttonY, buttonW, buttonH);
        }

        g.setFont(savedFont);
    }

    private int measureCurrentDetails(
            Graphics2D g,
            FontMetrics fm,
            int maxW,
            XtremeTask current,
            Function<XtremeTask, List<PrerequisiteStatus>> prerequisiteStatusProvider,
            Function<XtremeTask, CollectionLogRequirementPreview> collectionLogRequirementPreviewProvider,
            boolean showTips
    )
    {
        int totalPx = 0;
        CollectionLogRequirementPreview requirementPreview = collectionLogRequirementPreviewProvider == null
                ? null
                : collectionLogRequirementPreviewProvider.apply(current);
        boolean hasRequirementPreview = requirementPreview != null && requirementPreview.hasItems();
        boolean showAchievementDiaryNote = isAchievementDiaryTask(current);
        boolean hideDescription = hasRequirementPreview || current.getSource() == TaskSource.COLLECTION_LOG;
        String desc = showAchievementDiaryNote
                ? diaryTaskDescription(current)
                : (hideDescription ? null : current.getDescription());
        boolean hasDesc = desc != null && !desc.trim().isEmpty();
        String tip = showTips ? current.getTip() : null;
        boolean hasTip = tip != null && !tip.trim().isEmpty();
        if (hasTip)
        {
            tip = tip.trim();
        }

        if (hasDesc)
        {
            totalPx += rowHeight;
            totalPx += rowHeight * Math.min(wrapText(desc, fm, maxW).size(), 7);
            totalPx += 8;
        }

        totalPx += rowHeight;
        String prereqs = current.getPrereqs();
        boolean hasPrereqs = prereqs != null && !prereqs.trim().isEmpty();
        if (hasPrereqs)
        {
            List<PrerequisiteStatus> statuses = prerequisiteStatusProvider == null
                    ? List.of()
                    : prerequisiteStatusProvider.apply(current);
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
                for (PrerequisiteStatus status : statuses)
                {
                    totalPx += rowHeight * wrapText("- " + status.getText(), fm, maxW).size();
                }
            }
        }
        else
        {
            totalPx += rowHeight;
        }
        totalPx += 8;

        if (hasRequirementPreview)
        {
            totalPx += rowHeight;
            if (requirementPreview.showSummaryText())
            {
                totalPx += rowHeight;
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
            if (hasDesc)
            {
                totalPx += rowHeight;
            }
            totalPx += rowHeight * Math.min(wrapText(tip, fm, Math.max(hasDesc ? maxW - 8 : maxW, 40)).size(), 5);
            totalPx += 8;
        }

        return totalPx + fm.getAscent() + 8;
    }

    private int drawCurrentDetails(
            Graphics2D g,
            FontMetrics fm,
            int x,
            int y,
            int maxW,
            XtremeTask current,
            Function<XtremeTask, List<PrerequisiteStatus>> prerequisiteStatusProvider,
            Function<XtremeTask, CollectionLogRequirementPreview> collectionLogRequirementPreviewProvider,
            boolean showTips
    )
    {
        CollectionLogRequirementPreview requirementPreview = collectionLogRequirementPreviewProvider == null
                ? null
                : collectionLogRequirementPreviewProvider.apply(current);
        boolean hasRequirementPreview = requirementPreview != null && requirementPreview.hasItems();
        boolean showAchievementDiaryNote = isAchievementDiaryTask(current);
        boolean hideDescription = hasRequirementPreview || current.getSource() == TaskSource.COLLECTION_LOG;
        String desc = showAchievementDiaryNote
                ? diaryTaskDescription(current)
                : (hideDescription ? null : current.getDescription());
        boolean hasDesc = desc != null && !desc.trim().isEmpty();
        String prereqs = current.getPrereqs();
        boolean hasPrereqs = prereqs != null && !prereqs.trim().isEmpty();
        String tip = showTips ? current.getTip() : null;
        boolean hasTip = tip != null && !tip.trim().isEmpty();
        if (hasTip)
        {
            tip = tip.trim();
        }

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
            List<PrerequisiteStatus> statuses = prerequisiteStatusProvider == null
                    ? List.of()
                    : prerequisiteStatusProvider.apply(current);

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
            if (hasDesc)
            {
                y += rowHeight;
            }
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

        return y;
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
        int lineH = fm.getHeight() + 4;

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

        final int scrollBarW = 5;
        int railX = viewport.x + viewport.width - scrollBarW - 2;
        int railY = viewport.y;
        int railH = Math.max(1, viewport.height);

        g.setColor(new Color(0, 0, 0, 60));
        g.fillRect(railX, railY, scrollBarW, railH);

        float thumbRatio = (float) viewportH / (float) totalPx;
        int thumbH = Math.max(12, (int) (railH * thumbRatio));
        int maxScrollPx = Math.max(1, totalPx - viewportH);
        float scrollRatio = Math.max(0f, Math.min(1f, (float) scrollPx / (float) maxScrollPx));
        int thumbY = railY + (int) ((railH - thumbH) * scrollRatio);

        Rectangle thumb = new Rectangle(railX, thumbY, Math.max(0, scrollBarW - 1), Math.max(0, thumbH - 1));
        drawBevelBox(g, thumb, new Color(78, 62, 38, 200));

        g.setColor(new Color(uiGold.getRed(), uiGold.getGreen(), uiGold.getBlue(), 140));
        g.drawRect(thumb.x, thumb.y, thumb.width, thumb.height);
    }

    private void drawCurrentViewportFrame(Graphics2D g, Rectangle viewport)
    {
        if (viewport.width <= 0 || viewport.height <= 0)
        {
            return;
        }

        int x = viewport.x + DETAILS_INSET_X;
        int y = viewport.y - 6;
        int w = viewport.width - 2 * DETAILS_INSET_X - 12;
        int h = viewport.height + 12;
        if (w <= 0)
        {
            return;
        }

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
                drawBadgeHoverText(g, fm, sourceLabel(src), srcBounds, panelX + panelWidth - panelPadding);
            }
            x += w + badgeGap;
        }
        if (tier != null)
        {
            TaskRowsRenderer.drawSourceBadge(g, x, yTop, tierLabel(tier), edgeDark, edgeLight, uiGold, uiText);
        }
    }

    private void drawBadgesRightAligned(Graphics2D g, FontMetrics fm, int rightX, int yTop, TaskSource src, TaskTier tier, java.awt.Point mousePoint)
    {
        if (src == null && tier == null) return;

        FontMetrics sfm = g.getFontMetrics(FontManager.getRunescapeSmallFont());
        String srcText = src != null ? shortSource(src) : null;
        String tierText = tier != null ? tierLabel(tier) : null;
        int badgeGap = srcText != null && tierText != null ? 4 : 0;
        int srcW = srcText == null ? 0 : Math.max(24, sfm.stringWidth(srcText) + 14);
        int tierW = tierText == null ? 0 : Math.max(24, sfm.stringWidth(tierText) + 14);
        int x = rightX - srcW - badgeGap - tierW;

        if (srcText != null)
        {
            int actualW = TaskRowsRenderer.drawSourceBadge(g, x, yTop, srcText, edgeDark, edgeLight, uiGold, uiText);
            Rectangle srcBounds = new Rectangle(x, yTop, actualW, rowHeight + 4);
            if (mousePoint != null && srcBounds.contains(mousePoint))
            {
                drawBadgeHoverText(g, fm, sourceLabel(src), srcBounds, rightX);
            }
            x += actualW + badgeGap;
        }
        if (tierText != null)
        {
            TaskRowsRenderer.drawSourceBadge(g, x, yTop, tierText, edgeDark, edgeLight, uiGold, uiText);
        }
    }

    private void drawBadgeHoverText(Graphics2D g, FontMetrics fm, String text, Rectangle badgeBounds, int maxRight)
    {
        int x = Math.min(badgeBounds.x, maxRight - fm.stringWidth(text));
        g.setColor(uiTextDim);
        g.drawString(text, x, badgeBounds.y - 4);
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
        return "Achievement Diary: " + detail + ". " + ACHIEVEMENT_DIARY_NOTE;
    }

    private static String diaryTaskDescription(XtremeTask task)
    {
        String description = task == null ? null : task.getDescription();
        if (description != null && !description.trim().isEmpty())
        {
            return description.trim();
        }

        return achievementDiaryDescription(task);
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
