package com.amtrollin.xtremetasker.ui.tasks;

import com.amtrollin.xtremetasker.models.CompletionInfo;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.models.PrerequisiteStatus;
import com.amtrollin.xtremetasker.models.PrerequisiteStatus.MarkerIcon;
import com.amtrollin.xtremetasker.models.TaskGroupProgress;
import com.amtrollin.xtremetasker.models.verification.TaskVerification;
import com.amtrollin.xtremetasker.ui.style.UiPalette;
import com.amtrollin.xtremetasker.ui.PrerequisiteIconRenderer;
import com.amtrollin.xtremetasker.ui.tasklist.TaskListScrollController;
import com.amtrollin.xtremetasker.ui.tasklist.TaskRowsRenderer;
import com.amtrollin.xtremetasker.ui.text.TaskLabelFormatter;
import com.amtrollin.xtremetasker.ui.text.TextUtils;
import com.amtrollin.xtremetasker.ui.tasks.models.CollectionLogRequirementPreview;
import com.amtrollin.xtremetasker.ui.tasks.models.WikiLink;
import com.amtrollin.xtremetasker.ui.widgets.ButtonRenderer;
import net.runelite.api.Skill;
import net.runelite.client.ui.FontManager;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.amtrollin.xtremetasker.ui.style.UiConstants.ROW_HEIGHT;
import static com.amtrollin.xtremetasker.ui.style.UiPalette.withAlpha;

public final class TaskDetailsPopup
{
    private static final int INSTANCE_BLOCK_PAD_BOTTOM = 6;
    private static final String ACHIEVEMENT_DIARY_NOTE = "Synced from in game diary completion.";
    private static final String MEDALLION_ASSEMBLY_TITLE_PREFIX = "Need all ";
    private static final int SECONDARY_SECTION_GAP = 6;
    private static final int MEDALLION_ASSEMBLY_SECTION_GAP = 12;
    private static final int TIER_SECTION_ICON_GAP = 5;
    private static final int TIER_SECTION_LABEL_TOP_GAP = 4;
    private static final int OTHER_SEQUENCE_LABEL_TOP_GAP = 5;
    private static final String OTHER_SEQUENCE_CLOGS_DIVIDER = "___";
    private static final String OTHER_SEQUENCE_CLOGS_LABEL = "Other clogs in this task sequence, but different tier:";
    private static final BufferedImage QUESTION_ICON = loadQuestionIconSafe();
    private static final DateTimeFormatter COMPLETION_DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter COMPLETION_DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a").withZone(ZoneId.systemDefault());

    private final UiPalette palette;
    private final TaskListScrollController scroll;

    private XtremeTask task = null;

    private final Rectangle bounds = new Rectangle();
    private final Rectangle viewportBounds = new Rectangle();
    private final Rectangle closeBounds = new Rectangle();
    private final Rectangle wikiBounds = new Rectangle();
    private final Rectangle wikiMenuBounds = new Rectangle();
    private final Rectangle markIncompleteBounds = new Rectangle();
    private final Rectangle scrollbarRailBounds = new Rectangle();
    private final Rectangle scrollbarThumbBounds = new Rectangle();
    private final Rectangle groupProgressHelpBounds = new Rectangle();
    private final Map<XtremeTask, Rectangle> instanceRemoveBounds = new LinkedHashMap<>();
    private final List<WikiLink> wikiLinks = new ArrayList<>();
    private final List<Rectangle> wikiLinkBounds = new ArrayList<>();

    private int totalContentRows = 0;
    private boolean wikiMenuOpen = false;

    public TaskDetailsPopup(UiPalette palette, TaskListScrollController scroll)
    {
        this.palette = palette;
        this.scroll = scroll;
    }

    public boolean isOpen()
    {
        return task != null;
    }

    public XtremeTask task()
    {
        return task;
    }

    public void open(XtremeTask task)
    {
        if (task == null)
        {
            return;
        }

        this.task = task;
        scroll.reset();
        closeWikiMenu();
    }

    public void close()
    {
        task = null;
        scroll.reset();
        bounds.setBounds(0, 0, 0, 0);
        viewportBounds.setBounds(0, 0, 0, 0);
        closeBounds.setBounds(0, 0, 0, 0);
        wikiBounds.setBounds(0, 0, 0, 0);
        wikiMenuBounds.setBounds(0, 0, 0, 0);
        markIncompleteBounds.setBounds(0, 0, 0, 0);
        scrollbarRailBounds.setBounds(0, 0, 0, 0);
        scrollbarThumbBounds.setBounds(0, 0, 0, 0);
        groupProgressHelpBounds.setBounds(0, 0, 0, 0);
        instanceRemoveBounds.clear();
        wikiLinks.clear();
        wikiLinkBounds.clear();
        totalContentRows = 0;
        wikiMenuOpen = false;
    }

    public Rectangle bounds()
    {
        return bounds;
    }

    public Rectangle viewportBounds()
    {
        return viewportBounds;
    }

    public Rectangle closeBounds()
    {
        return closeBounds;
    }

    public Rectangle wikiBounds()
    {
        return wikiBounds;
    }

    public Rectangle wikiMenuBounds()
    {
        return wikiMenuBounds;
    }

    public boolean isWikiMenuOpen()
    {
        return wikiMenuOpen;
    }

    public void openWikiMenu()
    {
        wikiMenuOpen = wikiLinks.size() > 1;
    }

    public void closeWikiMenu()
    {
        wikiMenuOpen = false;
        wikiMenuBounds.setBounds(0, 0, 0, 0);
        wikiLinkBounds.clear();
    }

    public WikiLink wikiLinkAt(java.awt.Point p)
    {
        if (p == null || !wikiMenuOpen)
        {
            return null;
        }

        for (int i = 0; i < wikiLinkBounds.size() && i < wikiLinks.size(); i++)
        {
            if (wikiLinkBounds.get(i).contains(p))
            {
                return wikiLinks.get(i);
            }
        }
        return null;
    }

    public Rectangle markIncompleteBounds()
    {
        return markIncompleteBounds;
    }

    public Rectangle scrollbarRailBounds()
    {
        return scrollbarRailBounds;
    }

    public Rectangle scrollbarThumbBounds()
    {
        return scrollbarThumbBounds;
    }

    public Map<XtremeTask, Rectangle> instanceRemoveBounds()
    {
        return instanceRemoveBounds;
    }

    public int totalContentRows()
    {
        return totalContentRows;
    }

    public TaskListScrollController scroll()
    {
        return scroll;
    }

    public void render(
            Graphics2D g,
            FontMetrics fm,
            Rectangle panelBounds,
            Function<XtremeTask, Boolean> isCompleted,
            Function<XtremeTask, CompletionInfo> completionInfoProvider,
            Function<XtremeTask, Long> taskTicksProvider,
            Function<XtremeTask, TaskGroupProgress> groupProgressProvider,
            Function<XtremeTask, List<XtremeTask>> taskGroupProvider,
            Function<XtremeTask, List<PrerequisiteStatus>> prerequisiteStatusProvider,
            Function<Skill, BufferedImage> prerequisiteSkillImageProvider,
            Function<MarkerIcon, BufferedImage> prerequisiteMarkerImageProvider,
            Function<XtremeTask, CollectionLogRequirementPreview> collectionLogRequirementPreviewProvider,
            Function<XtremeTask, String> collectionLogSequenceLabelProvider,
            Function<XtremeTask, Boolean> collectionLogSyncMismatchProvider,
            Function<Integer, BufferedImage> collectionLogItemImageProvider,
            Function<XtremeTask, List<WikiLink>> wikiLinksProvider,
            net.runelite.api.Point mouse,
            java.awt.image.BufferedImage taskIcon,
            boolean showTips
    )
    {
        XtremeTask task = this.task;
        if (task == null)
        {
            return;
        }

        // Dim panel behind popup
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(panelBounds.x, panelBounds.y, panelBounds.width, panelBounds.height);

        String title = safe(task.getName());
        boolean done = isCompleted.apply(task);
        TaskGroupProgress groupProgress = groupProgressProvider == null ? null : groupProgressProvider.apply(task);
        boolean grouped = groupProgress != null && groupProgress.isGrouped();
        boolean headerComplete = grouped ? groupProgress.isComplete() : done;
        refreshWikiLinks(task, wikiLinksProvider);
        final int pad = 12;
        final int closeW = 28;
        final String wikiOpenText = "Open wiki";
        final String wikiCloseText = "Close";
        final String wikiText = wikiMenuOpen ? wikiCloseText : wikiOpenText;
        final int gap = 8;
        final int iconSize = 24;
        final int iconGap = 4;
        final int checkSize = 12;
        final int checkGap = 6;
        boolean hasIcon = taskIcon != null;
        int iconReserve = hasIcon ? iconSize + iconGap : 0;
        int completeReserve = headerComplete ? checkSize + checkGap : 0;
        final int rightReserve = closeW + gap;
        FontMetrics titleFm = g.getFontMetrics(FontManager.getRunescapeFont());
        int titleDesiredW = titleFm.stringWidth(title) + (pad * 2) + rightReserve + iconReserve + completeReserve + 8;

        // Recompute logical bounds every frame. The overlay may scale input bounds
        // after rendering, so carrying them back into layout would compound scale.
        int maxPopupW = Math.max(280, panelBounds.width - pad * 4);
        int w = Math.min(maxPopupW, Math.max(Math.max(300, (int) (panelBounds.width * 0.50)), titleDesiredW));
        int h = (int) (panelBounds.height * 0.70);
        int popupX = panelBounds.x + (panelBounds.width - w) / 2;
        int popupY = panelBounds.y + (panelBounds.height - h) / 2;
        bounds.setBounds(popupX, popupY, w, h);

        // Background
        drawBevelBox(g, bounds, new Color(45, 36, 24, 245));

        final int x = bounds.x + pad;
        final int yTop = bounds.y + pad;

        // Header font (used for buttons/layout), larger title font for task name
        g.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics headerFm = g.getFontMetrics();
        g.setColor(palette.UI_GOLD);

        final int wikiW = Math.max(headerFm.stringWidth(wikiOpenText), headerFm.stringWidth(wikiCloseText)) + 20;
        boolean collectionLogMismatch = collectionLogSyncMismatchProvider != null
                && Boolean.TRUE.equals(collectionLogSyncMismatchProvider.apply(task));

        // Icon in header
        int titleMaxW = Math.max(0, bounds.width - (pad * 2) - rightReserve - iconReserve - completeReserve);

        int btnH = ROW_HEIGHT + 8;
        int btnY = yTop - 2;
        // Vertically center title within the button row height using the larger title font
        int titleBaseline = btnY + ((btnH - titleFm.getHeight()) / 2) + titleFm.getAscent();

        // Draw icon
        int titleX = x;
        if (hasIcon) {
            int iconY = btnY + (btnH - iconSize) / 2;
            g.drawImage(taskIcon, x, iconY, iconSize, iconSize, null);
            titleX = x + iconSize + iconGap;
        }

        g.setColor(headerComplete ? UiPalette.TIER_COMPLETE_GLOW : palette.UI_GOLD);
        g.setFont(FontManager.getRunescapeFont());
        String drawTitle = TextUtils.truncateToWidth(title, titleFm, titleMaxW);
        g.drawString(drawTitle, titleX, titleBaseline);
        if (headerComplete)
        {
            int checkX = titleX + titleFm.stringWidth(drawTitle) + checkGap;
            int checkY = titleBaseline - titleFm.getAscent() + (titleFm.getHeight() - checkSize) / 2;
            drawHeaderCheckMark(g, checkX, checkY, checkSize);
        }
        g.setFont(FontManager.getRunescapeSmallFont());

        int closeX = bounds.x + bounds.width - pad - closeW;
        closeBounds.setBounds(closeX, btnY, closeW, btnH);
        wikiBounds.setBounds(0, 0, 0, 0);

        g.setColor(new Color(200, 200, 200, 180));
        int ccx = closeBounds.x + closeBounds.width / 2;
        int ccy = closeBounds.y + closeBounds.height / 2;
        int carm = 6; // matches header: closeSize(16)/2 - 2
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Stroke oldStroke = g.getStroke();
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(ccx - carm, ccy - carm, ccx + carm, ccy + carm);
        g.drawLine(ccx + carm, ccy - carm, ccx - carm, ccy + carm);
        g.setStroke(oldStroke);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);

        int headerBottomY = bounds.y + pad + btnH + 6;
        g.setColor(withAlpha(palette.UI_GOLD, 55));
        g.drawLine(bounds.x + pad, headerBottomY, bounds.x + bounds.width - pad, headerBottomY);

        g.setFont(FontManager.getRunescapeSmallFont());
        fm = g.getFontMetrics();

        final int badgeH = ROW_HEIGHT + 4;
        final int metaYTop = headerBottomY + 8;

        int metaX = bounds.x + pad;

        String srcBadge = TaskLabelFormatter.shortSource(task.getSource());
        int srcBadgeW = TaskRowsRenderer.drawSourceBadge(g, metaX, metaYTop, srcBadge, palette.UI_EDGE_DARK, palette.UI_EDGE_LIGHT, palette.UI_GOLD, palette.UI_TEXT);
        Rectangle srcBadgeBounds = new Rectangle(metaX, metaYTop, srcBadgeW, badgeH);
        metaX += srcBadgeW + 4;

        String tierBadge = (task.getTier() == null) ? "?" : TaskLabelFormatter.tierLabel(task.getTier());
        int tierBadgeW = TaskRowsRenderer.drawSourceBadge(g, metaX, metaYTop, tierBadge, palette.UI_EDGE_DARK, palette.UI_EDGE_LIGHT, palette.UI_GOLD, palette.UI_TEXT);
        metaX += tierBadgeW + 4;

        // Wiki button — right-aligned on badge row
        int wikiX = bounds.x + bounds.width - pad - wikiW;
        wikiBounds.setBounds(wikiX, metaYTop, wikiW, badgeH);
        drawBevelBox(g, wikiBounds, palette.BTN_DISABLED_BG);
        g.setColor(palette.UI_TEXT);
        int wikiTextW = fm.stringWidth(wikiText);
        g.drawString(wikiText, wikiBounds.x + (wikiBounds.width - wikiTextW) / 2, centeredTextBaseline(wikiBounds, fm));

        markIncompleteBounds.setBounds(0, 0, 0, 0);
        if (headerComplete && !grouped)
        {
            int actionSize = Math.min(badgeH, 18);
            int actionX = metaX;
            int actionY = metaYTop + (badgeH - actionSize) / 2;
            if (actionX + actionSize <= wikiX - 4)
            {
                markIncompleteBounds.setBounds(actionX, actionY, actionSize, actionSize);
                drawMarkIncompleteAction(g, markIncompleteBounds,
                        mouse != null && markIncompleteBounds.contains(mouse.getX(), mouse.getY()));
            }
        }

        if (mouse != null && srcBadgeBounds.contains(mouse.getX(), mouse.getY()))
        {
            drawBadgeHoverText(g, fm, TaskLabelFormatter.sourceLabel(task.getSource()), srcBadgeBounds);
        }
        if (mouse != null && markIncompleteBounds.contains(mouse.getX(), mouse.getY()))
        {
            drawBadgeHoverText(g, fm, "Mark incomplete", markIncompleteBounds);
        }

        // Content area
        fm = g.getFontMetrics();
        int contentLeft = bounds.x + pad;
        int contentTop = metaYTop + badgeH + 12;
        int contentW = bounds.width - (pad * 2);
        groupProgressHelpBounds.setBounds(0, 0, 0, 0);
        instanceRemoveBounds.clear();
        java.awt.Point mousePoint = mouse == null ? null : new java.awt.Point(mouse.getX(), mouse.getY());

        // Footer geometry (computed early to size the viewport)
        List<XtremeTask> groupInstances = (grouped && taskGroupProvider != null) ? taskGroupProvider.apply(task) : List.of();
        CompletionInfo completionInfo = (completionInfoProvider != null) ? completionInfoProvider.apply(task) : null;
        Long ticks = (taskTicksProvider != null) ? taskTicksProvider.apply(task) : null;
        String completionLine = buildCompletionLine(completionInfo);
        String timeTakenLine = done ? buildTimeSpentLine(completionInfo, ticks) : null;
        CollectionLogRequirementPreview requirementPreview = collectionLogRequirementPreviewProvider == null
                ? null
                : collectionLogRequirementPreviewProvider.apply(task);
        List<InstanceHistoryLine> instanceHistoryLines = grouped
                ? buildInstanceHistoryLines(groupInstances, isCompleted, completionInfoProvider, taskTicksProvider, collectionLogSequenceLabelProvider)
                : List.of();
        int footerPad = 12;
        int footerY = bounds.y + bounds.height - footerPad;

        // Scrollable viewport between content top and footer divider
        final int scrollBarW = 5;
        int viewportTop = contentTop;
        int viewportH = Math.max(0, footerY - 8 - viewportTop);
        viewportBounds.setBounds(contentLeft, viewportTop, contentW, viewportH);

        // Snapshot prereq data once (used for both counting and drawing)
        String prereqs = safe(task.getPrereqs()).replace("\r", "").trim();
        if (prereqs.equalsIgnoreCase("none") || prereqs.equalsIgnoreCase("n/a") || prereqs.equals("-")) prereqs = "";
        List<PrerequisiteStatus> prerequisiteStatuses = (prerequisiteStatusProvider == null)
                ? List.of()
                : prerequisiteStatusProvider.apply(task);
        if ((prerequisiteStatuses == null || prerequisiteStatuses.isEmpty()) && !prereqs.isEmpty())
        {
            prereqs = prereqs.replaceAll("\\s*;\\s*", "\n").replaceAll("\n{2,}", "\n").trim();
        }

        // Count total content pixel height for scroll math
        boolean hasRequirementPreview = requirementPreview != null && requirementPreview.hasItems();
        boolean showAchievementDiaryNote = isAchievementDiaryTask(task);
        boolean hideDescription = hasRequirementPreview || task.getSource() == TaskSource.COLLECTION_LOG;
        boolean showDescriptionSection = !hideDescription || showAchievementDiaryNote;
        String desc = showAchievementDiaryNote
                ? diaryTaskDescription(task)
                : (hideDescription ? "" : safe(task.getDescription()).replace("\r", "").trim());
        String taskTip = showTips ? safe(task.getTip()).replace("\r", "").trim() : "";
        boolean hasTaskTip = !taskTip.isEmpty();
        boolean tipInDescriptionSection = hasTaskTip && showDescriptionSection && !hasRequirementPreview;
        boolean tipInRequirementSection = hasTaskTip && hasRequirementPreview;
        boolean showGroupedProgressSection = groupProgress != null && groupProgress.isGrouped();
        boolean showGroupedCollectionLogMismatch = showGroupedProgressSection && collectionLogMismatch;
        boolean showStandaloneSyncMismatch = collectionLogMismatch && !showGroupedProgressSection;
        int totalPx = 0;
        if (showGroupedProgressSection)
        {
            totalPx += ROW_HEIGHT; // "Progress" header
            totalPx += ROW_HEIGHT + 8; // progress editor
            if (showGroupedCollectionLogMismatch)
            {
                totalPx += ROW_HEIGHT; // blank line before warning
                totalPx += ROW_HEIGHT; // warning text
                totalPx += 6; // button bottom gap
            }
            totalPx += 6 + 12; // divider gap before next section
        }
        if (showDescriptionSection)
        {
            totalPx += ROW_HEIGHT; // "Description" header
            if (desc.isEmpty())
            {
                totalPx += ROW_HEIGHT;
            }
            else
            {
                totalPx += ROW_HEIGHT * TextUtils.wrapText(desc, fm, contentW).size();
            }
            if (tipInDescriptionSection)
            {
                totalPx += ROW_HEIGHT; // blank line before tip
                totalPx += measureTipHeight(taskTip, fm, contentW);
            }
        }
        if (showDescriptionSection)
        {
            totalPx += 6 + 12; // divider gap before next section
        }
        if (showStandaloneSyncMismatch)
        {
            totalPx += ROW_HEIGHT;
            totalPx += 6 + 12;
        }
        if (hasRequirementPreview)
        {
            if (tipInRequirementSection)
            {
                totalPx += measureTipHeight(taskTip, fm, contentW);
                totalPx += 6;
            }
            totalPx += ROW_HEIGHT; // collection log requirement header
            if (requirementPreview.showSummaryText())
            {
                totalPx += ROW_HEIGHT; // counter summary
            }
            if (requirementPreview.showTierSections())
            {
                totalPx += measureCollectionLogTierSections(requirementPreview, fm, contentW);
            }
            else if (requirementPreview.showItemList())
            {
                totalPx += CollectionLogIconGridRenderer.measureHeight(
                        requirementPreview.getItems().size(),
                        contentW,
                        requirementPreview.iconColumns());
            }
            if (requirementPreview.showSecondaryItemList())
            {
                totalPx += secondarySectionGap(requirementPreview);
                totalPx += ROW_HEIGHT;
                totalPx += CollectionLogIconGridRenderer.measureHeight(
                        requirementPreview.secondaryItems().size(),
                        contentW,
                        requirementPreview.secondaryIconColumns());
            }
            totalPx += 6 + 12; // divider gap before "Prereqs"
        }
        totalPx += ROW_HEIGHT; // "Prereqs" header
        if ((prerequisiteStatuses == null || prerequisiteStatuses.isEmpty()) && prereqs.isEmpty())
        {
            totalPx += ROW_HEIGHT; // "None"
        }
        else if (prerequisiteStatuses != null && !prerequisiteStatuses.isEmpty())
        {
            for (PrerequisiteStatus status : prerequisiteStatuses)
            {
                BufferedImage markerImage = PrerequisiteIconRenderer.resolveMarkerImage(status, prerequisiteSkillImageProvider, prerequisiteMarkerImageProvider);
                int lineHeight = PrerequisiteIconRenderer.lineHeight(ROW_HEIGHT, status);
                totalPx += lineHeight * TextUtils.wrapText(status.getText(), fm,
                        PrerequisiteIconRenderer.textWidth(fm, contentW, markerImage)).size();
            }
        }
        else
        {
            for (String para : prereqs.split("\n"))
            {
                String p = para.trim();
                if (p.isEmpty()) continue;
                totalPx += ROW_HEIGHT * TextUtils.wrapText(p, fm, contentW).size();
            }
        }
        if (!instanceHistoryLines.isEmpty())
        {
            totalPx += 6 + 12; // divider gap before completed instance history
            totalPx += ROW_HEIGHT; // "Completed instances" header
            totalPx += (ROW_HEIGHT + INSTANCE_BLOCK_PAD_BOTTOM) * instanceHistoryLines.size();
        }
        else if (completionLine != null || timeTakenLine != null)
        {
            totalPx += 6 + 12; // divider gap before completion
            totalPx += ROW_HEIGHT; // "Completion" header
            if (completionLine != null)
            {
                totalPx += ROW_HEIGHT;
            }
            if (timeTakenLine != null)
            {
                totalPx += ROW_HEIGHT;
            }
        }
        totalContentRows = (totalPx + ROW_HEIGHT - 1) / ROW_HEIGHT;

        int visibleRows = viewportH > 0 ? viewportH / ROW_HEIGHT : 1;
        int maxScrollOffset = Math.max(0, totalContentRows - visibleRows);
        if (scroll.offsetRows > maxScrollOffset)
        {
            scroll.offsetRows = maxScrollOffset;
        }
        int scrollPx = scroll.offsetRows * ROW_HEIGHT;
        boolean needsScroll = totalContentRows > visibleRows;

        // Clip to viewport then draw with scroll offset applied
        Shape oldClip = g.getClip();
        g.setClip(contentLeft, viewportTop, contentW, viewportH);

        int y = contentTop + fm.getAscent() - scrollPx;

        if (showGroupedProgressSection)
        {
            g.setColor(palette.UI_GOLD);
            g.drawString("Progress", contentLeft, y);
            y += ROW_HEIGHT;
            drawGroupProgressEditor(g, fm, contentLeft, y - fm.getAscent() + 1, contentW, groupProgress, mouse);
            y += ROW_HEIGHT + 8;

            if (showGroupedCollectionLogMismatch)
            {
                y += ROW_HEIGHT;
                y = drawCollectionLogMismatchText(
                        g,
                        fm,
                        contentLeft,
                        y,
                        contentW);
            }

            y = drawSectionDivider(g, fm, contentLeft, y + 6, contentW);
        }

        if (showDescriptionSection)
        {
            g.setColor(palette.UI_GOLD);
            g.drawString("Description", contentLeft, y);
            y += ROW_HEIGHT;

            if (desc.isEmpty())
            {
                g.setColor(palette.UI_TEXT_DIM);
                g.drawString("None", contentLeft, y);
                y += ROW_HEIGHT;
            }
            else
            {
                g.setColor(palette.UI_TEXT);
                for (String line : TextUtils.wrapText(desc, fm, contentW))
                {
                    g.drawString(TextUtils.truncateToWidth(line, fm, contentW), contentLeft, y);
                    y += ROW_HEIGHT;
                }
            }

            if (tipInDescriptionSection)
            {
                y += ROW_HEIGHT;
                y = drawTaskTip(g, fm, taskTip, contentLeft, y, contentW);
            }
        }

        if (showDescriptionSection)
        {
            y = drawSectionDivider(g, fm, contentLeft, y + 6, contentW);
        }

        if (showStandaloneSyncMismatch)
        {
            y = drawCollectionLogMismatchText(
                    g,
                    fm,
                    contentLeft,
                    y,
                    contentW);

            y = drawSectionDivider(g, fm, contentLeft, y, contentW);
        }

        if (hasRequirementPreview)
        {
            if (tipInRequirementSection)
            {
                y = drawTaskTip(g, fm, taskTip, contentLeft, y, contentW);
                y += 6;
            }

            g.setColor(palette.UI_GOLD);
            g.drawString(collectionLogRequirementTitle(requirementPreview), contentLeft, y);
            y += ROW_HEIGHT;

            if (requirementPreview.showSummaryText())
            {
                drawCollectionLogSummaryText(g, fm, requirementPreview.summaryText(), contentLeft, y, contentW);
                y += ROW_HEIGHT;
                if (requirementPreview.showTierSections())
                {
                    y += TIER_SECTION_ICON_GAP;
                }
            }

            if (requirementPreview.showTierSections())
            {
                y = drawCollectionLogTierSections(
                        g,
                        fm,
                        requirementPreview,
                        contentLeft,
                        y,
                        contentW,
                        collectionLogItemImageProvider,
                        mousePoint,
                        viewportBounds);
            }
            else if (requirementPreview.showItemList())
            {
                y = CollectionLogIconGridRenderer.render(
                        g,
                        fm,
                        contentLeft,
                        y,
                        contentW,
                        requirementPreview.getItems(),
                        collectionLogItemImageProvider,
                        mousePoint,
                        viewportBounds,
                        palette.UI_TEXT,
                        palette.UI_TEXT_DIM,
                        palette.UI_EDGE_LIGHT,
                        palette.UI_EDGE_DARK,
                        requirementPreview.iconColumns());
            }

            if (requirementPreview.showSecondaryItemList())
            {
                y += secondarySectionGap(requirementPreview);
                g.setColor(palette.UI_GOLD);
                g.drawString(TextUtils.truncateToWidth(requirementPreview.secondaryTitleText(), fm, contentW), contentLeft, y);
                y += ROW_HEIGHT;
                y = CollectionLogIconGridRenderer.render(
                        g,
                        fm,
                        contentLeft,
                        y,
                        contentW,
                        requirementPreview.secondaryItems(),
                        collectionLogItemImageProvider,
                        mousePoint,
                        viewportBounds,
                        palette.UI_TEXT,
                        palette.UI_TEXT_DIM,
                        palette.UI_EDGE_LIGHT,
                        palette.UI_EDGE_DARK,
                        requirementPreview.secondaryIconColumns());
            }

            y = drawSectionDivider(g, fm, contentLeft, y + 6, contentW);
        }

        g.setColor(palette.UI_GOLD);
        g.drawString("Prereqs", contentLeft, y);
        y += ROW_HEIGHT;

        if ((prerequisiteStatuses == null || prerequisiteStatuses.isEmpty()) && prereqs.isEmpty())
        {
            g.setColor(palette.UI_TEXT_DIM);
            g.drawString("None", contentLeft, y);
            y += ROW_HEIGHT;
        }
        else if (prerequisiteStatuses != null && !prerequisiteStatuses.isEmpty())
        {
            for (PrerequisiteStatus status : prerequisiteStatuses)
            {
                BufferedImage markerImage = PrerequisiteIconRenderer.resolveMarkerImage(status, prerequisiteSkillImageProvider, prerequisiteMarkerImageProvider);
                int textX = PrerequisiteIconRenderer.textX(fm, contentLeft, markerImage);
                int textW = PrerequisiteIconRenderer.textWidth(fm, contentW, markerImage);
                int lineHeight = PrerequisiteIconRenderer.lineHeight(ROW_HEIGHT, status);
                boolean firstLine = true;
                for (String line : TextUtils.wrapText(status.getText(), fm, textW))
                {
                    if (firstLine)
                    {
                        PrerequisiteIconRenderer.drawMarker(g, fm, markerImage, contentLeft, y);
                    }
                    String drawLine = TextUtils.truncateToWidth(line, fm, textW);
                    drawPrerequisiteStatusLine(g, fm, status, drawLine, textX, y);

                    y += lineHeight;
                    firstLine = false;
                }
            }
        }
        else
        {
            g.setColor(palette.UI_TEXT);
            for (String para : prereqs.split("\n"))
            {
                String p = para.trim();
                if (p.isEmpty()) continue;

                for (String line : TextUtils.wrapText(p, fm, contentW))
                {
                    g.drawString(TextUtils.truncateToWidth(line, fm, contentW), contentLeft, y);
                    y += ROW_HEIGHT;
                }
            }
        }

        if (!instanceHistoryLines.isEmpty())
        {
            y = drawSectionDivider(g, fm, contentLeft, y + 6, contentW);

            g.setColor(palette.UI_GOLD);
            g.drawString("Completed instances", contentLeft, y);
            y += ROW_HEIGHT;

            g.setColor(palette.UI_TEXT_DIM);
            for (InstanceHistoryLine line : instanceHistoryLines)
            {
                drawInstanceHistoryLine(g, fm, contentLeft, y, contentW, line, mousePoint);
                y += ROW_HEIGHT + INSTANCE_BLOCK_PAD_BOTTOM;
            }
        }
        else if (completionLine != null || timeTakenLine != null)
        {
            y = drawSectionDivider(g, fm, contentLeft, y + 6, contentW);

            g.setColor(palette.UI_GOLD);
            g.drawString("Completion", contentLeft, y);
            y += ROW_HEIGHT;
            if (completionLine != null)
            {
                g.setColor(palette.UI_TEXT_DIM);
                g.drawString(TextUtils.truncateToWidth(completionLine, fm, contentW), contentLeft, y);
                y += ROW_HEIGHT;
            }
            if (timeTakenLine != null)
            {
                g.setColor(palette.UI_TEXT_DIM);
                g.drawString(TextUtils.truncateToWidth(timeTakenLine, fm, contentW), contentLeft, y);
                y += ROW_HEIGHT;
            }
        }

        g.setClip(oldClip);

        // Scrollbar (drawn outside clip)
        ButtonRenderer.drawScrollbar(g, new Rectangle(bounds.x + bounds.width - pad / 2 - scrollBarW, viewportTop, scrollBarW, viewportH),
                totalContentRows, visibleRows, scroll.offsetRows, scrollbarRailBounds, scrollbarThumbBounds,
                palette.UI_EDGE_DARK, palette.UI_EDGE_LIGHT, palette.UI_GOLD);
        int footerX = bounds.x + footerPad;
        int footerW = bounds.width - (footerPad * 2);

        g.setColor(new Color(
                palette.UI_GOLD.getRed(),
                palette.UI_GOLD.getGreen(),
                palette.UI_GOLD.getBlue(),
                45
        ));
        g.drawLine(
                footerX,
                footerY - 6,
                footerX + footerW,
                footerY - 6
        );

        if (wikiMenuOpen)
        {
            drawWikiMenu(g, fm, mouse);
        }

    }

    private void refreshWikiLinks(XtremeTask task, Function<XtremeTask, List<WikiLink>> wikiLinksProvider)
    {
        wikiLinks.clear();
        if (wikiLinksProvider != null)
        {
            List<WikiLink> provided = wikiLinksProvider.apply(task);
            if (provided != null)
            {
                for (WikiLink link : provided)
                {
                    if (link != null && link.isValid())
                    {
                        wikiLinks.add(link);
                    }
                }
            }
        }

        if (wikiLinks.size() <= 1)
        {
            closeWikiMenu();
        }
    }

    private void drawWikiMenu(Graphics2D g, FontMetrics fm, net.runelite.api.Point mouse)
    {
        wikiLinkBounds.clear();
        if (wikiLinks.size() <= 1 || wikiBounds.width <= 0 || wikiBounds.height <= 0)
        {
            closeWikiMenu();
            return;
        }

        final int rowH = ROW_HEIGHT + 4;
        final int padX = 8;
        int menuW = wikiBounds.width;
        for (WikiLink link : wikiLinks)
        {
            menuW = Math.max(menuW, fm.stringWidth(link.label()) + padX * 2);
        }
        int menuH = rowH * wikiLinks.size();
        int menuX = Math.max(bounds.x + 6, Math.min(wikiBounds.x + wikiBounds.width - menuW, bounds.x + bounds.width - menuW - 6));
        int menuY = wikiBounds.y - menuH - 2;
        if (menuY < bounds.y + 6)
        {
            menuY = wikiBounds.y + wikiBounds.height + 2;
        }

        wikiMenuBounds.setBounds(menuX, menuY, menuW, menuH);
        g.setColor(new Color(24, 19, 13, 245));
        g.fillRect(wikiMenuBounds.x, wikiMenuBounds.y, wikiMenuBounds.width, wikiMenuBounds.height);

        int mouseX = mouse == null ? Integer.MIN_VALUE : mouse.getX();
        int mouseY = mouse == null ? Integer.MIN_VALUE : mouse.getY();
        for (int i = 0; i < wikiLinks.size(); i++)
        {
            Rectangle row = new Rectangle(menuX, menuY + i * rowH, menuW, rowH);
            wikiLinkBounds.add(row);
            if (row.contains(mouseX, mouseY))
            {
                g.setColor(withAlpha(palette.UI_GOLD, 45));
                g.fillRect(row.x, row.y, row.width, row.height);
            }

            g.setColor(palette.UI_TEXT);
            String label = TextUtils.truncateToWidth(wikiLinks.get(i).label(), fm, row.width - padX * 2);
            g.drawString(label, row.x + padX, centeredTextBaseline(row, fm));
        }
    }

    private int measureTipHeight(String tip, FontMetrics fm, int maxWidth)
    {
        return ROW_HEIGHT * TextUtils.wrapText("Tip: " + safe(tip), fm, Math.max(maxWidth, 40)).size();
    }

    private int drawTaskTip(Graphics2D g, FontMetrics fm, String tip, int x, int y, int maxWidth)
    {
        List<String> tipLines = TextUtils.wrapText("Tip: " + safe(tip), fm, Math.max(maxWidth, 40));
        g.setColor(palette.UI_TEXT_DIM);
        for (String line : tipLines)
        {
            g.drawString(TextUtils.truncateToWidth(line, fm, maxWidth), x, y);
            y += ROW_HEIGHT;
        }
        return y;
    }

    private void drawMarkIncompleteAction(Graphics2D g, Rectangle bounds, boolean hover)
    {
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        Stroke oldStroke = g.getStroke();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(hover ? 2f : 1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(hover ? new Color(245, 92, 82, 230) : new Color(190, 88, 72, 170));
        int pad = Math.max(4, bounds.width / 4);
        g.drawLine(bounds.x + pad, bounds.y + pad, bounds.x + bounds.width - pad, bounds.y + bounds.height - pad);
        g.drawLine(bounds.x + bounds.width - pad, bounds.y + pad, bounds.x + pad, bounds.y + bounds.height - pad);
        g.setStroke(oldStroke);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
    }

    private int drawCollectionLogMismatchText(
            Graphics2D g,
            FontMetrics fm,
            int contentLeft,
            int y,
            int contentW
    )
    {
        g.setColor(new Color(245, 92, 82, 245));
        g.drawString(TextUtils.truncateToWidth("Not enough CLOG(s) obtained", fm, contentW), contentLeft, y);
        y += ROW_HEIGHT;
        g.drawString(TextUtils.truncateToWidth("Go to the Sync page or mark incomplete.", fm, contentW), contentLeft, y);
        return y + ROW_HEIGHT + 6;
    }

    private void drawGroupProgressEditor(
            Graphics2D g,
            FontMetrics fm,
            int x,
            int rowTop,
            int contentW,
            TaskGroupProgress progress,
            net.runelite.api.Point mouse
    )
    {
        final int rowH = 18;
        final int gap = 6;
        String text = "Completed: " + progress.getCompleted() + "/" + progress.getTotal();
        int textX = x;
        int textW = Math.max(0, contentW - (textX - x));
        g.setColor(palette.UI_TEXT);
        String drawText = TextUtils.truncateToWidth(text, fm, textW);
        int textBaseline = rowTop + ((rowH - fm.getHeight()) / 2) + fm.getAscent();
        g.drawString(drawText,
                textX,
                textBaseline);

        int helpX = textX + fm.stringWidth(drawText) + gap;
        drawGroupProgressHelpIcon(g, fm, helpX, rowTop, rowH, x + contentW, mouse);
    }

    private void drawBadgeHoverText(Graphics2D g, FontMetrics fm, String text, Rectangle badgeBounds)
    {
        g.setColor(palette.UI_TEXT_DIM);
        int x = badgeBounds.x;
        int y = badgeBounds.y - 4;
        g.drawString(text, x, y);
    }

    private void drawRightAlignedHoverText(Graphics2D g, FontMetrics fm, String text, int minX, int rightX, Rectangle anchorBounds)
    {
        g.setColor(palette.UI_TEXT_DIM);
        int x = Math.max(minX, rightX - fm.stringWidth(text));
        int y = anchorBounds.y + 1;
        g.drawString(text, x, y);
    }

    private void drawPrerequisiteStatusLine(
            Graphics2D g,
            FontMetrics fm,
            PrerequisiteStatus status,
            String drawLine,
            int x,
            int baselineY
    )
    {
        boolean hasCheckSpans = status.getCheckSpans() != null && !status.getCheckSpans().isEmpty();
        Color textColor = !hasCheckSpans && status.isCompleted() ? palette.UI_TEXT_DIM : palette.UI_TEXT;
        if (isStartQuestLine(status, drawLine))
        {
            String startText = "Start";
            g.setColor(UiPalette.TIER_COMPLETE_GLOW);
            g.drawString(startText, x, baselineY);
            g.setColor(textColor);
            g.drawString(drawLine.substring(startText.length()), x + fm.stringWidth(startText), baselineY);
        }
        else
        {
            g.setColor(textColor);
            g.drawString(drawLine, x, baselineY);
        }

        if (!hasCheckSpans)
        {
            if (status.isCompleted())
            {
                drawStrikeThrough(g, fm, drawLine, x, baselineY);
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
            int lineIndex = drawLine.indexOf(spanText);
            if (lineIndex < 0)
            {
                continue;
            }

            int spanX = x + fm.stringWidth(drawLine.substring(0, lineIndex));
            g.setColor(palette.UI_TEXT_DIM);
            g.drawString(spanText, spanX, baselineY);
            drawStrikeThrough(g, fm, spanText, spanX, baselineY);
        }
    }

    private static boolean isStartQuestLine(PrerequisiteStatus status, String drawLine)
    {
        return status.getMarkerIcons() != null
                && status.getMarkerIcons().contains(MarkerIcon.START_QUEST)
                && drawLine != null
                && drawLine.startsWith("Start");
    }

    private void drawStrikeThrough(Graphics2D g, FontMetrics fm, String text, int x, int baselineY)
    {
        int lineW = fm.stringWidth(text);
        int strikeY = baselineY - (fm.getAscent() * 3 / 5);
        g.setColor(withAlpha(palette.UI_TEXT_DIM, 170));
        g.drawLine(x, strikeY, x + lineW, strikeY);
    }

    private int drawSectionDivider(Graphics2D g, FontMetrics fm, int x, int y, int width)
    {
        g.setColor(withAlpha(palette.UI_GOLD, 35));
        g.drawLine(x, y - (fm.getAscent() / 2), x + width, y - (fm.getAscent() / 2));
        return y + 12;
    }

    private void drawHeaderCheckMark(Graphics2D g, int x, int y, int size)
    {
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        Stroke oldStroke = g.getStroke();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(UiPalette.TIER_COMPLETE_GLOW);

        int x1 = x + Math.max(1, size / 8);
        int y1 = y + (size / 2) + Math.max(1, size / 8);
        int x2 = x + (size / 2) - 1;
        int y2 = y + size - Math.max(2, size / 4);
        int x3 = x + size - Math.max(1, size / 8);
        int y3 = y + Math.max(1, size / 5);

        g.drawLine(x1, y1, x2, y2);
        g.drawLine(x2, y2, x3, y3);

        g.setStroke(oldStroke);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA != null ? oldAA : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
    }

    private void drawInstanceHistoryLine(
            Graphics2D g,
            FontMetrics fm,
            int x,
            int baselineY,
            int contentW,
            InstanceHistoryLine line,
            java.awt.Point mouse
    )
    {
        int actionSize = Math.min(14, ROW_HEIGHT);
        int actionGap = 6;
        int actionInset = 4;
        int actionX = x + contentW - actionSize - actionInset;
        int actionY = baselineY - fm.getAscent() + (ROW_HEIGHT - actionSize) / 2;
        Rectangle actionBounds = new Rectangle(actionX, actionY, actionSize, actionSize);
        instanceRemoveBounds.put(line.task, actionBounds);

        g.setColor(palette.UI_TEXT_DIM);
        g.drawString(TextUtils.truncateToWidth(line.text, fm, Math.max(0, contentW - actionSize - actionGap - actionInset)),
                x, baselineY + 2);

        boolean hover = mouse != null && actionBounds.contains(mouse);
        drawMarkIncompleteAction(g, actionBounds, hover);
        if (hover)
        {
            drawRightAlignedHoverText(g, fm, "Mark incomplete", x, x + contentW, actionBounds);
        }
    }

    private void drawGroupProgressHelpIcon(
            Graphics2D g,
            FontMetrics fm,
            int iconX,
            int rowTop,
            int rowH,
            int rowRight,
            net.runelite.api.Point mouse
    )
    {
        final int iconSize = fm.getAscent() + 2;
        if (iconX + iconSize > rowRight)
        {
            groupProgressHelpBounds.setBounds(0, 0, 0, 0);
            return;
        }

        int iconY = rowTop + (rowH - iconSize) / 2;
        groupProgressHelpBounds.setBounds(iconX, iconY, iconSize, iconSize);

        drawQuestionIcon(g, iconX, iconY, iconSize);

        if (mouse != null && groupProgressHelpBounds.contains(mouse.getX(), mouse.getY()))
        {
            drawTooltip(
                    g,
                    fm,
                    "Some tasks occur multiple times; this tracks completed instances.",
                    iconX + iconSize,
                    iconY,
                    true
            );
        }
    }

    private void drawBevelBox(Graphics2D g, Rectangle r, Color fill)
    {
        TaskRowsRenderer.drawBevelBoxLogic(g, r, fill, palette.UI_EDGE_DARK, palette.UI_EDGE_LIGHT);
    }

    private int centeredTextBaseline(Rectangle bounds, FontMetrics fm)
    {
        return bounds.y + ((bounds.height - fm.getHeight()) / 2) + fm.getAscent();
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

        g.setColor(withAlpha(palette.UI_GOLD, 140));
        g.fillOval(x, y, size, size);
        g.setColor(new Color(20, 15, 10, 220));
        drawCenteredQuestionMark(g, x, y, size);
    }

    private static BufferedImage loadQuestionIconSafe()
    {
        try (InputStream in = TaskDetailsPopup.class.getResourceAsStream("/icons/notifications/OSRS_question.png"))
        {
            return in == null ? null : ImageIO.read(in);
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    private void drawTooltip(Graphics2D g, FontMetrics fm, String text, int anchorX, int anchorY, boolean alignLeft)
    {
        if (text == null || text.trim().isEmpty()) return;

        final int padX = 8;
        final int padY = 6;

        int maxTextW = Math.max(40, bounds.width - 24 - (padX * 2));
        List<String> lines = TextUtils.wrapText(text, fm, maxTextW);
        if (lines.isEmpty()) return;

        int tw = 0;
        for (String line : lines)
        {
            tw = Math.max(tw, fm.stringWidth(line));
        }
        int w = tw + padX * 2;
        int h = (fm.getHeight() * lines.size()) + padY * 2;

        int x = alignLeft ? anchorX - padX : anchorX - w / 2;
        int y = anchorY - h + 2;

        x = Math.max(bounds.x + 6,
                Math.min(x, bounds.x + bounds.width - w - 6));
        y = Math.max(bounds.y + 6, y);

        Rectangle r = new Rectangle(x, y, w, h);

        Shape oldClip = g.getClip();
        g.setClip(bounds);

        g.setColor(palette.UI_TEXT_DIM);
        int lineY = r.y + padY + fm.getAscent();
        for (String line : lines)
        {
            g.drawString(line, r.x + padX, lineY);
            lineY += fm.getHeight();
        }

        g.setClip(oldClip);
    }

    private static String safe(String s)
    {
        return s == null ? "" : s;
    }

    private static int secondarySectionGap(CollectionLogRequirementPreview requirementPreview)
    {
        String title = requirementPreview == null ? "" : safe(requirementPreview.secondaryTitleText()).trim();
        return title.startsWith(MEDALLION_ASSEMBLY_TITLE_PREFIX)
                && title.toLowerCase().contains("fragments to assemble")
                ? MEDALLION_ASSEMBLY_SECTION_GAP
                : SECONDARY_SECTION_GAP;
    }

    private int measureCollectionLogTierSections(CollectionLogRequirementPreview requirementPreview, FontMetrics fm, int maxWidth)
    {
        if (requirementPreview == null || !requirementPreview.showTierSections())
        {
            return 0;
        }

        int total = 0;
        CollectionLogRequirementPreview.TierSection current = requirementPreview.currentTierSection();
        if (current != null)
        {
            total += CollectionLogIconGridRenderer.measureHeight(current.items().size(), maxWidth, current.iconColumns());
        }

        List<CollectionLogRequirementPreview.TierSection> otherSections = requirementPreview.otherTierSectionsHardestFirst();
        if (!otherSections.isEmpty())
        {
            total += SECONDARY_SECTION_GAP;
            total += ROW_HEIGHT;
            total += OTHER_SEQUENCE_LABEL_TOP_GAP;
            total += ROW_HEIGHT * TextUtils.wrapText(OTHER_SEQUENCE_CLOGS_LABEL, fm, maxWidth).size();
        }
        for (int i = 0; i < otherSections.size(); i++)
        {
            CollectionLogRequirementPreview.TierSection section = otherSections.get(i);
            if (i > 0)
            {
                total += SECONDARY_SECTION_GAP;
            }
            total += TIER_SECTION_LABEL_TOP_GAP;
            total += ROW_HEIGHT;
            total += TIER_SECTION_ICON_GAP;
            total += CollectionLogIconGridRenderer.measureHeight(section.items().size(), maxWidth, section.iconColumns());
        }
        return total;
    }

    private int drawCollectionLogTierSections(
            Graphics2D g,
            FontMetrics fm,
            CollectionLogRequirementPreview requirementPreview,
            int x,
            int y,
            int maxWidth,
            Function<Integer, BufferedImage> collectionLogItemImageProvider,
            java.awt.Point mousePoint,
            Rectangle viewportBounds)
    {
        CollectionLogRequirementPreview.TierSection current = requirementPreview.currentTierSection();
        if (current != null)
        {
            y = CollectionLogIconGridRenderer.render(
                    g,
                    fm,
                    x,
                    y,
                    maxWidth,
                    current.items(),
                    collectionLogItemImageProvider,
                    mousePoint,
                    viewportBounds,
                    palette.UI_TEXT,
                    palette.UI_TEXT_DIM,
                    palette.UI_EDGE_LIGHT,
                    palette.UI_EDGE_DARK,
                    current.iconColumns());
        }

        List<CollectionLogRequirementPreview.TierSection> sections = requirementPreview.otherTierSectionsHardestFirst();
        if (!sections.isEmpty())
        {
            y += SECONDARY_SECTION_GAP;
            y += ROW_HEIGHT;
            y += OTHER_SEQUENCE_LABEL_TOP_GAP;
            g.setColor(palette.UI_TEXT_DIM);
            for (String line : TextUtils.wrapText(OTHER_SEQUENCE_CLOGS_LABEL, fm, maxWidth))
            {
                g.drawString(TextUtils.truncateToWidth(line, fm, maxWidth), x, y);
                y += ROW_HEIGHT;
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

            drawCollectionLogTierLabel(g, fm, section, x, y);
            y += ROW_HEIGHT;
            y += TIER_SECTION_ICON_GAP;
            Composite oldComposite = g.getComposite();
            if (!section.currentTier())
            {
                g.setComposite(AlphaComposite.SrcOver.derive(0.45f));
            }
            y = CollectionLogIconGridRenderer.render(
                    g,
                    fm,
                    x,
                    y,
                    maxWidth,
                    section.items(),
                    collectionLogItemImageProvider,
                    mousePoint,
                    viewportBounds,
                    palette.UI_TEXT,
                    palette.UI_TEXT_DIM,
                    palette.UI_EDGE_LIGHT,
                    palette.UI_EDGE_DARK,
                    section.iconColumns());
            g.setComposite(oldComposite);
        }
        return y;
    }

    private void drawCollectionLogTierLabel(Graphics2D g, FontMetrics fm, CollectionLogRequirementPreview.TierSection section, int x, int baseline)
    {
        String text = section.tier() == null
                ? "TIER"
                : TaskLabelFormatter.tierLabel(section.tier()).toUpperCase();
        g.setColor(palette.UI_TEXT_DIM);
        g.drawString(TextUtils.truncateToWidth(text, fm, Math.max(0, 180)), x, baseline);
    }

    private void drawCollectionLogSummaryText(Graphics2D g, FontMetrics fm, String summaryText, int x, int y, int maxWidth)
    {
        String text = safe(summaryText);
        String separator = " | ";
        int separatorIndex = text.indexOf(separator);
        if (separatorIndex < 0)
        {
            g.setColor(palette.UI_TEXT_DIM);
            g.drawString(TextUtils.truncateToWidth(text, fm, maxWidth), x, y);
            return;
        }

        String prefix = text.substring(0, separatorIndex + separator.length());
        String suffix = text.substring(separatorIndex + separator.length());
        int prefixWidth = fm.stringWidth(prefix);
        if (prefixWidth >= maxWidth)
        {
            g.setColor(palette.UI_TEXT_DIM);
            g.drawString(TextUtils.truncateToWidth(text, fm, maxWidth), x, y);
            return;
        }

        g.setColor(palette.UI_TEXT_DIM);
        g.drawString(prefix, x, y);
        drawCollectionLogSummarySuffix(g, fm, suffix, x + prefixWidth, y, maxWidth - prefixWidth);
    }

    private void drawCollectionLogSummarySuffix(Graphics2D g, FontMetrics fm, String suffix, int x, int y, int maxWidth)
    {
        String currentTaskPrefix = "current task: ";
        if (!suffix.startsWith(currentTaskPrefix))
        {
            g.setColor(palette.UI_TEXT_DIM);
            g.drawString(TextUtils.truncateToWidth(suffix, fm, maxWidth), x, y);
            return;
        }

        int prefixWidth = fm.stringWidth(currentTaskPrefix);
        if (prefixWidth >= maxWidth)
        {
            g.setColor(palette.UI_TEXT_DIM);
            g.drawString(TextUtils.truncateToWidth(suffix, fm, maxWidth), x, y);
            return;
        }

        String progress = suffix.substring(currentTaskPrefix.length());
        g.setColor(palette.UI_TEXT_DIM);
        g.drawString(currentTaskPrefix, x, y);
        g.setColor(isCollectionLogProgressComplete(progress) ? UiPalette.TIER_COMPLETE_GLOW : palette.UI_TEXT_DIM);
        g.drawString(TextUtils.truncateToWidth(progress, fm, maxWidth - prefixWidth), x + prefixWidth, y);
    }

    private static boolean isCollectionLogProgressComplete(String progress)
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

    private static String collectionLogRequirementTitle(CollectionLogRequirementPreview requirementPreview)
    {
        if (requirementPreview != null && requirementPreview.titleText() != null && !requirementPreview.titleText().trim().isEmpty())
        {
            return requirementPreview.titleText();
        }
        return requirementPreview != null && requirementPreview.showSummaryText() && !requirementPreview.showItemList()
                ? "Collection Log Progress"
                : "Eligible Collection Log items";
    }

    private static String buildCompletionLine(CompletionInfo info)
    {
        if (info == null) return null;
        if (info.timestamp <= 0) return "Date unknown";
        return COMPLETION_DATE_TIME_FORMAT.format(Instant.ofEpochMilli(info.timestamp));
    }

    private static List<InstanceHistoryLine> buildInstanceHistoryLines(
            List<XtremeTask> instances,
            Function<XtremeTask, Boolean> isCompleted,
            Function<XtremeTask, CompletionInfo> completionInfoProvider,
            Function<XtremeTask, Long> taskTicksProvider,
            Function<XtremeTask, String> collectionLogSequenceLabelProvider
    )
    {
        if (instances == null || instances.isEmpty())
        {
            return List.of();
        }

        java.util.ArrayList<CompletedInstance> completedInstances = new java.util.ArrayList<>(instances.size());
        for (int i = 0; i < instances.size(); i++)
        {
            XtremeTask instance = instances.get(i);
            boolean done = isCompleted != null && Boolean.TRUE.equals(isCompleted.apply(instance));
            if (!done)
            {
                continue;
            }

            CompletionInfo info = completionInfoProvider == null ? null : completionInfoProvider.apply(instance);
            Long ticks = taskTicksProvider == null ? null : taskTicksProvider.apply(instance);

            completedInstances.add(new CompletedInstance(instance, info, ticks, i));
        }

        completedInstances.sort((a, b) -> {
            int byTimestamp = Long.compare(instanceSortTimestamp(a.info), instanceSortTimestamp(b.info));
            return byTimestamp != 0 ? byTimestamp : Integer.compare(a.originalIndex, b.originalIndex);
        });

        java.util.ArrayList<InstanceHistoryLine> lines = new java.util.ArrayList<>(completedInstances.size());
        for (int i = 0; i < completedInstances.size(); i++)
        {
            CompletedInstance completed = completedInstances.get(i);
            String prefix = instanceHistoryPrefix(completed.task, i, collectionLogSequenceLabelProvider);
            String dateText = instanceCompletionDateText(completed.info);
            String timeText = instanceTimeSpentText(completed.info, completed.ticks);

            lines.add(new InstanceHistoryLine(
                    completed.task,
                    prefix + dateText + " | " + timeText));
        }
        return lines;
    }

    private static String instanceHistoryPrefix(
            XtremeTask task,
            int index,
            Function<XtremeTask, String> collectionLogSequenceLabelProvider)
    {
        String sequenceLabel = collectionLogSequenceLabelProvider == null ? "" : safe(collectionLogSequenceLabelProvider.apply(task)).trim();
        if (!sequenceLabel.isEmpty())
        {
            return titleCaseSequenceLabel(sequenceLabel) + ": ";
        }
        return (index + 1) + ". ";
    }

    private static String titleCaseSequenceLabel(String label)
    {
        if (label == null || label.isEmpty())
        {
            return "";
        }
        return label.substring(0, 1).toUpperCase(java.util.Locale.ROOT)
                + label.substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    private static long instanceSortTimestamp(CompletionInfo info)
    {
        return info != null && info.timestamp > 0 ? info.timestamp : Long.MAX_VALUE;
    }

    private static String buildTimeSpentLine(CompletionInfo info, Long ticks)
    {
        if (ticks != null && ticks > 0)
        {
            return "Time spent: " + formatDuration(Math.round(ticks * 0.6));
        }

        if (info != null && info.source == CompletionInfo.Source.MANUAL)
        {
            return "Time spent: unknown (marked)";
        }
        if (info != null && info.source == CompletionInfo.Source.SYNCED)
        {
            return "Time spent: unknown (synced)";
        }
        return "Time spent: unknown";
    }

    private static final class InstanceHistoryLine
    {
        private final XtremeTask task;
        private final String text;

        private InstanceHistoryLine(XtremeTask task, String text)
        {
            this.task = task;
            this.text = text;
        }
    }

    private static final class CompletedInstance
    {
        private final XtremeTask task;
        private final CompletionInfo info;
        private final Long ticks;
        private final int originalIndex;

        private CompletedInstance(XtremeTask task, CompletionInfo info, Long ticks, int originalIndex)
        {
            this.task = task;
            this.info = info;
            this.ticks = ticks;
            this.originalIndex = originalIndex;
        }
    }

    private static String instanceCompletionDateText(CompletionInfo info)
    {
        if (info == null)
        {
            return "Date unknown";
        }

        if (info.timestamp <= 0)
        {
            return "Date unknown";
        }
        return COMPLETION_DATE_FORMAT.format(Instant.ofEpochMilli(info.timestamp));
    }

    private static String instanceTimeSpentText(CompletionInfo info, Long ticks)
    {
        if (ticks != null && ticks > 0)
        {
            return formatDuration(Math.round(ticks * 0.6));
        }

        if (info != null && info.source == CompletionInfo.Source.MANUAL)
        {
            return "unknown (marked)";
        }
        if (info != null && info.source == CompletionInfo.Source.SYNCED)
        {
            return "unknown (synced)";
        }
        return "unknown";
    }

    private static String formatDuration(long seconds)
    {
        if (seconds < 60)
        {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        long remSeconds = seconds % 60;
        if (minutes < 60)
        {
            return remSeconds > 0
                    ? minutes + "m " + remSeconds + "s"
                    : minutes + "m";
        }
        long hours = minutes / 60;
        long remMinutes = minutes % 60;
        if (hours < 24)
        {
            return remMinutes > 0
                    ? hours + "h " + remMinutes + "m"
                    : hours + "h";
        }
        long days = hours / 24;
        long remHours = hours % 24;
        return remHours > 0
                ? days + "d " + remHours + "h"
                : days + "d";
    }
}
