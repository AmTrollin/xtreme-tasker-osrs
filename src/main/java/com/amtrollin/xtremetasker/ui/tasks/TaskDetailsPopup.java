package com.amtrollin.xtremetasker.ui.tasks;

import com.amtrollin.xtremetasker.models.CompletionInfo;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.models.PrerequisiteStatus;
import com.amtrollin.xtremetasker.models.TaskGroupProgress;
import com.amtrollin.xtremetasker.models.verification.TaskVerification;
import com.amtrollin.xtremetasker.ui.style.UiPalette;
import com.amtrollin.xtremetasker.ui.tasklist.TaskListScrollController;
import com.amtrollin.xtremetasker.ui.tasklist.TaskRowsRenderer;
import com.amtrollin.xtremetasker.ui.text.TaskLabelFormatter;
import com.amtrollin.xtremetasker.ui.text.TextUtils;
import com.amtrollin.xtremetasker.ui.tasks.models.CollectionLogRequirementItem;
import com.amtrollin.xtremetasker.ui.tasks.models.CollectionLogRequirementPreview;
import net.runelite.client.ui.FontManager;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.amtrollin.xtremetasker.ui.style.UiConstants.ROW_HEIGHT;

public final class TaskDetailsPopup
{
    private static final int INSTANCE_BLOCK_PAD_BOTTOM = 6;
    private static final String ACHIEVEMENT_DIARY_NOTE = "Obtained from Diary Achievement rewards.";
    private static final BufferedImage QUESTION_ICON = loadQuestionIconSafe();

    private final UiPalette palette;
    private final TaskListScrollController scroll;

    private XtremeTask task = null;

    private final Rectangle bounds = new Rectangle();
    private final Rectangle viewportBounds = new Rectangle();
    private final Rectangle closeBounds = new Rectangle();
    private final Rectangle wikiBounds = new Rectangle();
    private final Rectangle toggleBounds = new Rectangle();
    private final Rectangle scrollbarRailBounds = new Rectangle();
    private final Rectangle scrollbarThumbBounds = new Rectangle();
    private final Rectangle decrementGroupBounds = new Rectangle();
    private final Rectangle incrementGroupBounds = new Rectangle();
    private final Rectangle groupProgressHelpBounds = new Rectangle();
    private final Map<XtremeTask, Rectangle> instanceRemoveBounds = new LinkedHashMap<>();

    private int totalContentRows = 0;

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
    }

    public void close()
    {
        task = null;
        scroll.reset();
        bounds.setBounds(0, 0, 0, 0);
        viewportBounds.setBounds(0, 0, 0, 0);
        closeBounds.setBounds(0, 0, 0, 0);
        wikiBounds.setBounds(0, 0, 0, 0);
        toggleBounds.setBounds(0, 0, 0, 0);
        scrollbarRailBounds.setBounds(0, 0, 0, 0);
        scrollbarThumbBounds.setBounds(0, 0, 0, 0);
        decrementGroupBounds.setBounds(0, 0, 0, 0);
        incrementGroupBounds.setBounds(0, 0, 0, 0);
        groupProgressHelpBounds.setBounds(0, 0, 0, 0);
        instanceRemoveBounds.clear();
        totalContentRows = 0;
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

    public Rectangle toggleBounds()
    {
        return toggleBounds;
    }

    public Rectangle scrollbarRailBounds()
    {
        return scrollbarRailBounds;
    }

    public Rectangle scrollbarThumbBounds()
    {
        return scrollbarThumbBounds;
    }

    public Rectangle decrementGroupBounds()
    {
        return decrementGroupBounds;
    }

    public Rectangle incrementGroupBounds()
    {
        return incrementGroupBounds;
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
            Function<XtremeTask, CollectionLogRequirementPreview> collectionLogRequirementPreviewProvider,
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

        // Popup bounds (smaller)
        if (bounds.width <= 0 || bounds.height <= 0)
        {
            int w = (int) (panelBounds.width * 0.82);
            int h = (int) (panelBounds.height * 0.70);
            int x = panelBounds.x + (panelBounds.width - w) / 2;
            int y = panelBounds.y + (panelBounds.height - h) / 2;
            bounds.setBounds(x, y, w, h);
        }

        // Background
        drawBevelBox(g, bounds, new Color(45, 36, 24, 245));

        final int pad = 12;
        final int x = bounds.x + pad;
        final int yTop = bounds.y + pad;

        // Header font (used for buttons/layout), larger title font for task name
        g.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics headerFm = g.getFontMetrics();
        FontMetrics titleFm = g.getFontMetrics(FontManager.getRunescapeFont());
        g.setColor(palette.UI_GOLD);

        String title = safe(task.getName());

        final int closeW = 28;
        final String wikiText = "Open wiki";
        final int wikiW = headerFm.stringWidth(wikiText) + 20;
        final int gap = 8;

        // Icon in header
        final int iconSize = 24;
        final int iconGap = 4;
        boolean hasIcon = taskIcon != null;
        int iconReserve = hasIcon ? iconSize + iconGap : 0;

        final int rightReserve = closeW + gap;
        int titleMaxW = Math.max(0, bounds.width - (pad * 2) - rightReserve - iconReserve);

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

        g.setColor(palette.UI_GOLD);
        g.setFont(FontManager.getRunescapeFont());
        g.drawString(TextUtils.truncateToWidth(title, titleFm, titleMaxW), titleX, titleBaseline);
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
        g.setColor(new Color(palette.UI_GOLD.getRed(), palette.UI_GOLD.getGreen(), palette.UI_GOLD.getBlue(), 55));
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

        // Wiki button — right-aligned on badge row
        int wikiX = bounds.x + bounds.width - pad - wikiW;
        wikiBounds.setBounds(wikiX, metaYTop, wikiW, badgeH);
        drawBevelBox(g, wikiBounds, palette.BTN_DISABLED_BG);
        g.setColor(palette.UI_TEXT);
        int wikiTextW = fm.stringWidth(wikiText);
        g.drawString(wikiText, wikiBounds.x + (wikiBounds.width - wikiTextW) / 2, centeredTextBaseline(wikiBounds, fm));

        if (mouse != null && srcBadgeBounds.contains(mouse.getX(), mouse.getY()))
        {
            drawBadgeHoverText(g, fm, TaskLabelFormatter.sourceLabel(task.getSource()), srcBadgeBounds);
        }

        // Content area
        fm = g.getFontMetrics();
        int contentLeft = bounds.x + pad;
        int contentTop = metaYTop + badgeH + 12;
        int contentW = bounds.width - (pad * 2);
        decrementGroupBounds.setBounds(0, 0, 0, 0);
        incrementGroupBounds.setBounds(0, 0, 0, 0);
        groupProgressHelpBounds.setBounds(0, 0, 0, 0);
        instanceRemoveBounds.clear();

        // Footer geometry (computed early to size the viewport)
        boolean done = isCompleted.apply(task);
        TaskGroupProgress groupProgress = groupProgressProvider == null ? null : groupProgressProvider.apply(task);
        boolean grouped = groupProgress != null && groupProgress.isGrouped();
        List<XtremeTask> groupInstances = (grouped && taskGroupProvider != null) ? taskGroupProvider.apply(task) : List.of();
        CompletionInfo completionInfo = (completionInfoProvider != null) ? completionInfoProvider.apply(task) : null;
        Long ticks = (taskTicksProvider != null) ? taskTicksProvider.apply(task) : null;
        String completionLine = buildCompletionLine(completionInfo, ticks);
        String timeTakenLine = done ? buildTimeSpentLine(completionInfo, ticks) : null;
        CollectionLogRequirementPreview requirementPreview = collectionLogRequirementPreviewProvider == null
                ? null
                : collectionLogRequirementPreviewProvider.apply(task);
        List<InstanceHistoryLine> instanceHistoryLines = grouped
                ? buildInstanceHistoryLines(groupInstances, isCompleted, completionInfoProvider, taskTicksProvider)
                : List.of();
        int footerPad = 12;
        int footerH = ROW_HEIGHT + 10;
        int footerY = bounds.y + bounds.height - footerH - footerPad;

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
                ? ACHIEVEMENT_DIARY_NOTE
                : (hideDescription ? "" : safe(task.getDescription()).replace("\r", "").trim());
        String taskTip = showTips ? safe(task.getTip()).replace("\r", "").trim() : "";
        int totalPx = 0;
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
        }
        if (showDescriptionSection)
        {
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
                totalPx += ROW_HEIGHT * TextUtils.wrapText("- " + status.getText(), fm, contentW).size();
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
        if (hasRequirementPreview)
        {
            totalPx += 6 + 12; // divider gap before eligible CLog items
            totalPx += ROW_HEIGHT; // "Eligible Collection Log Items" header
            if (requirementPreview.showSummaryText())
            {
                totalPx += ROW_HEIGHT; // counter summary
            }
            if (requirementPreview.showItemList())
            {
                for (CollectionLogRequirementItem item : requirementPreview.getItems())
                {
                    totalPx += ROW_HEIGHT * TextUtils.wrapText("- " + safe(item.getName()), fm, contentW).size();
                }
            }
        }
        if (!taskTip.isEmpty())
        {
            List<String> tipLines = TextUtils.wrapText(taskTip, fm, Math.max(contentW, 40));
            if (showDescriptionSection)
            {
                totalPx += ROW_HEIGHT; // blank line before tip
            }
            totalPx += ROW_HEIGHT * tipLines.size();
            totalPx += 6;
        }
        if (!instanceHistoryLines.isEmpty())
        {
            totalPx += 6 + 12; // divider gap before repeated-task progress
            totalPx += ROW_HEIGHT; // "Progress" header
            totalPx += ROW_HEIGHT + 8; // progress editor
            totalPx += 10; // spacer before completed instance history
            totalPx += ROW_HEIGHT; // "Completed Instances" header
            totalPx += (ROW_HEIGHT * 2 + INSTANCE_BLOCK_PAD_BOTTOM) * instanceHistoryLines.size();
        }
        else if (groupProgress != null && groupProgress.isGrouped())
        {
            totalPx += 6 + 12; // divider gap before repeated-task progress
            totalPx += ROW_HEIGHT; // "Progress" header
            totalPx += ROW_HEIGHT + 8; // progress editor
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

        // Clamp scroll offset
        int visibleRows = viewportH > 0 ? viewportH / ROW_HEIGHT : 1;
        int maxScrollOffset = Math.max(0, totalContentRows - visibleRows);
        if (scroll.offsetRows > maxScrollOffset) scroll.offsetRows = maxScrollOffset;
        int scrollPx = scroll.offsetRows * ROW_HEIGHT;
        boolean needsScroll = totalContentRows > visibleRows;

        // Clip to viewport then draw with scroll offset applied
        Shape oldClip = g.getClip();
        g.setClip(contentLeft, viewportTop, contentW, viewportH);

        int y = contentTop + fm.getAscent() - scrollPx;

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
        }

        if (showDescriptionSection)
        {
            y += 6;
            g.setColor(new Color(palette.UI_GOLD.getRed(), palette.UI_GOLD.getGreen(), palette.UI_GOLD.getBlue(), 35));
            g.drawLine(contentLeft, y - (fm.getAscent() / 2), contentLeft + contentW, y - (fm.getAscent() / 2));
            y += 12;
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
                String lineText = "- " + status.getText();
                for (String line : TextUtils.wrapText(lineText, fm, contentW))
                {
                    String drawLine = TextUtils.truncateToWidth(line, fm, contentW);
                    g.setColor(status.isCompleted() ? palette.UI_TEXT_DIM : palette.UI_TEXT);
                    g.drawString(drawLine, contentLeft, y);

                    if (status.isCompleted())
                    {
                        drawStrikeThrough(g, fm, drawLine, contentLeft, y);
                    }

                    y += ROW_HEIGHT;
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

        if (hasRequirementPreview)
        {
            y += 6;
            g.setColor(new Color(palette.UI_GOLD.getRed(), palette.UI_GOLD.getGreen(), palette.UI_GOLD.getBlue(), 35));
            g.drawLine(contentLeft, y - (fm.getAscent() / 2), contentLeft + contentW, y - (fm.getAscent() / 2));
            y += 12;

            g.setColor(palette.UI_GOLD);
            g.drawString(collectionLogRequirementTitle(requirementPreview), contentLeft, y);
            y += ROW_HEIGHT;

            if (requirementPreview.showSummaryText())
            {
                g.setColor(palette.UI_TEXT_DIM);
                g.drawString(TextUtils.truncateToWidth(requirementPreview.summaryText(), fm, contentW), contentLeft, y);
                y += ROW_HEIGHT;
            }

            if (requirementPreview.showItemList())
            {
                for (CollectionLogRequirementItem item : requirementPreview.getItems())
                {
                    String lineText = "- " + safe(item.getName());
                    for (String line : TextUtils.wrapText(lineText, fm, contentW))
                    {
                        String drawLine = TextUtils.truncateToWidth(line, fm, contentW);
                        g.setColor(item.isObtained() ? palette.UI_TEXT_DIM : palette.UI_TEXT);
                        g.drawString(drawLine, contentLeft, y);

                        if (item.isObtained())
                        {
                            drawStrikeThrough(g, fm, drawLine, contentLeft, y);
                        }

                        y += ROW_HEIGHT;
                    }
                }
            }
        }

        if (!taskTip.isEmpty())
        {
            if (showDescriptionSection)
            {
                y += ROW_HEIGHT; // blank line before tip
            }
            List<String> tipLines = TextUtils.wrapText(taskTip, fm, Math.max(contentW - 8, 40));
            g.setColor(palette.UI_TEXT_DIM);
            if (!tipLines.isEmpty())
            {
                g.drawString(TextUtils.truncateToWidth("Tip: " + tipLines.get(0), fm, contentW), contentLeft, y);
                y += ROW_HEIGHT;
                for (int i = 1; i < tipLines.size(); i++)
                {
                    g.drawString(TextUtils.truncateToWidth(tipLines.get(i), fm, contentW), contentLeft, y);
                    y += ROW_HEIGHT;
                }
            }
            y += 6;
        }

        if (groupProgress != null && groupProgress.isGrouped())
        {
            y += 6;
            g.setColor(new Color(palette.UI_GOLD.getRed(), palette.UI_GOLD.getGreen(), palette.UI_GOLD.getBlue(), 35));
            g.drawLine(contentLeft, y - (fm.getAscent() / 2), contentLeft + contentW, y - (fm.getAscent() / 2));
            y += 12;

            g.setColor(palette.UI_GOLD);
            g.drawString("Progress", contentLeft, y);
            y += ROW_HEIGHT;
            drawGroupProgressEditor(g, fm, contentLeft, y - fm.getAscent() + 1, contentW, groupProgress, mouse);
            y += ROW_HEIGHT + 8;
        }

        if (!instanceHistoryLines.isEmpty())
        {
            y += 10;

            g.setColor(palette.UI_GOLD);
            g.drawString("Completed Instances", contentLeft, y);
            y += ROW_HEIGHT;

            g.setColor(palette.UI_TEXT_DIM);
            for (InstanceHistoryLine line : instanceHistoryLines)
            {
                drawInstanceHistoryLine(g, fm, contentLeft, y, contentW, line);
                y += ROW_HEIGHT * 2 + INSTANCE_BLOCK_PAD_BOTTOM;
            }
        }
        else if (completionLine != null || timeTakenLine != null)
        {
            y += 6;
            g.setColor(new Color(palette.UI_GOLD.getRed(), palette.UI_GOLD.getGreen(), palette.UI_GOLD.getBlue(), 35));
            g.drawLine(contentLeft, y - (fm.getAscent() / 2), contentLeft + contentW, y - (fm.getAscent() / 2));
            y += 12;

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
        scrollbarRailBounds.setBounds(0, 0, 0, 0);
        scrollbarThumbBounds.setBounds(0, 0, 0, 0);
        if (needsScroll)
        {
            int sbX = bounds.x + bounds.width - pad / 2 - scrollBarW;
            scrollbarRailBounds.setBounds(sbX, viewportTop, scrollBarW, viewportH);
            g.setColor(new Color(18, 14, 9, 200));
            g.fillRect(sbX, viewportTop, scrollBarW, viewportH);
            float thumbRatio = (float) visibleRows / totalContentRows;
            int thumbH = Math.max(14, (int) (viewportH * thumbRatio));
            float scrollRatio = maxScrollOffset > 0 ? (float) scroll.offsetRows / maxScrollOffset : 0f;
            int thumbY = viewportTop + (int) ((viewportH - thumbH) * scrollRatio);
            scrollbarThumbBounds.setBounds(sbX, thumbY, scrollBarW, thumbH);
            g.setColor(new Color(
                    palette.UI_GOLD.getRed(), palette.UI_GOLD.getGreen(), palette.UI_GOLD.getBlue(), 160));
            g.fillRoundRect(sbX, thumbY, scrollBarW, thumbH, 3, 3);
        }
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

        boolean footerDone = grouped ? groupProgress.isComplete() : done;
        String toggleText = grouped
                ? (footerDone ? "Mark all incomplete" : "Mark all complete")
                : (footerDone ? "Mark incomplete" : "Mark complete");
        int btnW = grouped ? 160 : 140;

        int btnX = bounds.x + (bounds.width - btnW) / 2;

        toggleBounds.setBounds(btnX, footerY, btnW, footerH);

        drawPopupButton(
                g,
                fm,
                toggleBounds,
                toggleText,
                !footerDone
        );
    }

    private void drawPopupButton(Graphics2D g, FontMetrics fm, Rectangle bounds, String text, boolean enabled)
    {
        Color bg = enabled ? new Color(32, 26, 17, 235) : new Color(32, 26, 17, 140);
        drawBevelBox(g, bounds, bg);

        if (enabled)
        {
            g.setColor(new Color(palette.UI_GOLD.getRed(), palette.UI_GOLD.getGreen(), palette.UI_GOLD.getBlue(), 200));
            g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        g.setColor(enabled ? palette.UI_TEXT : new Color(palette.UI_TEXT_DIM.getRed(), palette.UI_TEXT_DIM.getGreen(), palette.UI_TEXT_DIM.getBlue(), 140));

        String drawText = TextUtils.truncateToWidth(text, fm, bounds.width - 10);
        int tw = fm.stringWidth(drawText);

        g.drawString(drawText,
                bounds.x + (bounds.width - tw) / 2,
                centeredTextBaseline(bounds, fm));
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
        final int btn = 18;
        final int gap = 6;
        decrementGroupBounds.setBounds(0, 0, 0, 0);

        String text = "Completed: " + progress.getCompleted() + "/" + progress.getTotal();
        int textX = x;
        int textW = Math.max(0, contentW - (textX - x));
        g.setColor(palette.UI_TEXT);
        String drawText = TextUtils.truncateToWidth(text, fm, textW);
        int textBaseline = rowTop + ((rowH - fm.getHeight()) / 2) + fm.getAscent();
        g.drawString(drawText,
                textX,
                textBaseline);

        int plusX = textX + fm.stringWidth(drawText) + gap;
        incrementGroupBounds.setBounds(plusX, rowTop, btn, btn);
        drawTinyButton(g, fm, incrementGroupBounds, "+", progress.getCompleted() < progress.getTotal());

        drawGroupProgressHelpIcon(g, fm, plusX + btn + gap, rowTop, rowH, x + contentW, mouse);
    }

    private void drawBadgeHoverText(Graphics2D g, FontMetrics fm, String text, Rectangle badgeBounds)
    {
        g.setColor(palette.UI_TEXT_DIM);
        int x = badgeBounds.x;
        int y = badgeBounds.y - 4;
        g.drawString(text, x, y);
    }

    private void drawStrikeThrough(Graphics2D g, FontMetrics fm, String text, int x, int baselineY)
    {
        int lineW = fm.stringWidth(text);
        int strikeY = baselineY - (fm.getAscent() * 3 / 5);
        g.setColor(new Color(
                palette.UI_TEXT_DIM.getRed(),
                palette.UI_TEXT_DIM.getGreen(),
                palette.UI_TEXT_DIM.getBlue(),
                170
        ));
        g.drawLine(x, strikeY, x + lineW, strikeY);
    }

    private void drawInstanceHistoryLine(Graphics2D g, FontMetrics fm, int x, int baselineY, int contentW, InstanceHistoryLine line)
    {
        final int btn = 18;
        int blockTop = baselineY - fm.getAscent();
        int buttonTop = blockTop + 1;
        Rectangle removeBounds = new Rectangle(x, buttonTop, btn, btn);
        instanceRemoveBounds.put(line.task, removeBounds);
        drawTinyButton(g, fm, removeBounds, "-", true);

        int textX = x + btn + 8;
        int textW = Math.max(0, contentW - (textX - x));
        g.setColor(palette.UI_TEXT_DIM);
        int textYOffset = 2;
        g.drawString(TextUtils.truncateToWidth(line.completedLine, fm, textW), textX, baselineY + textYOffset);
        g.drawString(TextUtils.truncateToWidth(line.timeLine, fm, textW), textX, baselineY + ROW_HEIGHT + textYOffset);
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

    private void drawTinyButton(Graphics2D g, FontMetrics fm, Rectangle bounds, String text, boolean enabled)
    {
        Color bg = enabled ? new Color(32, 26, 17, 235) : new Color(32, 26, 17, 140);
        drawBevelBox(g, bounds, bg);
        g.setColor(enabled ? palette.UI_TEXT : new Color(palette.UI_TEXT_DIM.getRed(), palette.UI_TEXT_DIM.getGreen(), palette.UI_TEXT_DIM.getBlue(), 140));
        int tw = fm.stringWidth(text);
        g.drawString(text, bounds.x + (bounds.width - tw) / 2, centeredTextBaseline(bounds, fm));
    }

    private int drawBevelBadge(Graphics2D g, FontMetrics fm, int x, int yTop, String text, boolean activeLook)
    {
        final int padX = 8;
        final int h = ROW_HEIGHT + 4;
        int textW = fm.stringWidth(text);
        int w = Math.max(26, textW + padX * 2);

        Rectangle r = new Rectangle(x, yTop, w, h);

        Color bg = activeLook ? new Color(78, 62, 38, 240) : new Color(32, 26, 17, 235);
        drawBevelBox(g, r, bg);

        g.setColor(new Color(palette.UI_GOLD.getRed(), palette.UI_GOLD.getGreen(), palette.UI_GOLD.getBlue(), 160));
        g.drawRect(r.x, r.y, r.width, r.height);

        g.setColor(palette.UI_TEXT);
        g.drawString(text, r.x + (r.width - textW) / 2, centeredTextBaseline(r, fm));

        return w;
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

        g.setColor(new Color(palette.UI_GOLD.getRed(), palette.UI_GOLD.getGreen(), palette.UI_GOLD.getBlue(), 140));
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

    private void drawTooltip(Graphics2D g, FontMetrics fm, String text, int anchorX, int anchorY)
    {
        drawTooltip(g, fm, text, anchorX, anchorY, false);
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

    private static boolean isAchievementDiaryTask(XtremeTask task)
    {
        TaskVerification verification = task == null ? null : task.getVerification();
        return verification != null && verification.getType() == TaskVerification.VerificationType.ACHIEVEMENT_DIARY;
    }

    private static String collectionLogRequirementTitle(CollectionLogRequirementPreview requirementPreview)
    {
        return requirementPreview != null && requirementPreview.showSummaryText() && !requirementPreview.showItemList()
                ? "Collection Log Progress"
                : "Eligible Collection Log Items";
    }

    private static String buildCompletionLine(CompletionInfo info, Long ticks)
    {
        if (info == null) return null;
        if (info.timestamp <= 0) return "Completed: date unknown";
        String date = new SimpleDateFormat("MMM d, yyyy").format(new Date(info.timestamp));
        return "Completed: " + date + completionSourceSuffix(info, ticks);
    }

    private static List<InstanceHistoryLine> buildInstanceHistoryLines(
            List<XtremeTask> instances,
            Function<XtremeTask, Boolean> isCompleted,
            Function<XtremeTask, CompletionInfo> completionInfoProvider,
            Function<XtremeTask, Long> taskTicksProvider
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
            String prefix = (i + 1) + ". ";
            String dateText = instanceCompletionDateText(completed.info, completed.ticks);

            lines.add(new InstanceHistoryLine(
                    completed.task,
                    prefix + dateText,
                    buildInstanceTimeSpentLine(completed.info, completed.ticks)));
        }
        return lines;
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

    private static String buildInstanceTimeSpentLine(CompletionInfo info, Long ticks)
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
        private final String completedLine;
        private final String timeLine;

        private InstanceHistoryLine(XtremeTask task, String completedLine, String timeLine)
        {
            this.task = task;
            this.completedLine = completedLine;
            this.timeLine = timeLine;
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

    private static String instanceCompletionDateText(CompletionInfo info, Long ticks)
    {
        if (info == null)
        {
            return "Date unknown";
        }

        if (info.timestamp <= 0)
        {
            return "Date unknown";
        }
        return new SimpleDateFormat("MMM d, yyyy").format(new Date(info.timestamp)) + completionSourceSuffix(info, ticks);
    }

    private static String completionSourceSuffix(CompletionInfo info, Long ticks)
    {
        if (info == null || info.timestamp <= 0 || info.source == null)
        {
            return "";
        }

        if (info.source == CompletionInfo.Source.MANUAL)
        {
            if (ticks != null && ticks > 0)
            {
                return "";
            }
            return " (marked)";
        }
        if (info.source == CompletionInfo.Source.SYNCED)
        {
            return " (synced)";
        }
        return "";
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
