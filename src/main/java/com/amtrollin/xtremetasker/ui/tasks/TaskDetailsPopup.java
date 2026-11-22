package com.amtrollin.xtremetasker.ui.tasks;

import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.models.*;
import com.amtrollin.xtremetasker.models.PrerequisiteStatus.MarkerIcon;
import com.amtrollin.xtremetasker.models.verification.TaskVerification;
import com.amtrollin.xtremetasker.ui.PrerequisiteSectionRenderer;
import com.amtrollin.xtremetasker.ui.style.*;
import com.amtrollin.xtremetasker.ui.tasklist.*;
import com.amtrollin.xtremetasker.ui.tasks.models.*;
import com.amtrollin.xtremetasker.ui.text.*;
import com.amtrollin.xtremetasker.ui.widgets.ButtonRenderer;
import com.amtrollin.xtremetasker.ui.widgets.DisclosureHeader;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.runelite.api.Skill;
import net.runelite.client.ui.FontManager;
import static com.amtrollin.xtremetasker.ui.style.UiConstants.ROW_HEIGHT;
import static com.amtrollin.xtremetasker.ui.style.UiDraw.centeredTextBaseline;
import static com.amtrollin.xtremetasker.ui.style.UiPalette.withAlpha;
import static com.amtrollin.xtremetasker.ui.text.TextUtils.*;

public final class TaskDetailsPopup
{
    private static final int INSTANCE_BLOCK_PAD_BOTTOM = 6;
    private static final String ACHIEVEMENT_DIARY_NOTE = UiText.get("current.achievement_diary_note");
    private static final BufferedImage QUESTION_ICON = UiDraw.loadImage("/icons/notifications/OSRS_question.png");
    private static final DateTimeFormatter COMPLETION_DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter COMPLETION_DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a").withZone(ZoneId.systemDefault());

    private final UiPalette palette;
    private final TaskListScrollController scroll;
    private final CollectionLogRequirementRenderer collectionLogRequirementRenderer;
    private final PrerequisiteSectionRenderer prerequisiteSectionRenderer;

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
    private boolean requirementsExpanded;
    private boolean historyExpanded;
    private final Rectangle requirementsToggleBounds = new Rectangle();
    private final Rectangle historyToggleBounds = new Rectangle();

    private final Rectangle mismatchDialogBounds = new Rectangle();
    private final Rectangle mismatchIgnoreBounds = new Rectangle();
    private boolean mismatchDialogOpen;
    private java.util.function.Predicate<XtremeTask> mismatchIgnored = t -> false;
    private java.util.function.Consumer<XtremeTask> ignoreMismatch = t -> {};

    public void configureMismatchActions(java.util.function.Predicate<XtremeTask> ignored,
            java.util.function.Consumer<XtremeTask> ignore)
    {
        mismatchIgnored = ignored;
        ignoreMismatch = ignore;
    }

    public boolean mismatchDialogContains(Point point) { return mismatchDialogBounds.contains(point); }
    public boolean mismatchIgnoreContains(Point point) { return mismatchIgnoreBounds.contains(point); }
    public void ignoreMismatch()
    {
        if (task != null) ignoreMismatch.accept(task);
        mismatchDialogOpen = false;
        mismatchDialogBounds.setBounds(0, 0, 0, 0);
        mismatchIgnoreBounds.setBounds(0, 0, 0, 0);
    }

    public boolean toggleSectionAt(java.awt.Point point)
    {
        if (!viewportBounds.contains(point) || wikiMenuOpen) return false;
        if (requirementsToggleBounds.contains(point))
        {
            requirementsExpanded = !requirementsExpanded;
            requirementsToggleBounds.setBounds(0, 0, 0, 0);
            return true;
        }
        if (historyToggleBounds.contains(point))
        {
            historyExpanded = !historyExpanded;
            historyToggleBounds.setBounds(0, 0, 0, 0);
            instanceRemoveBounds.clear();
            return true;
        }
        return false;
    }

    public boolean sectionToggleContains(java.awt.Point point)
    {
        return viewportBounds.contains(point)
                && (requirementsToggleBounds.contains(point) || historyToggleBounds.contains(point));
    }

    public TaskDetailsPopup(UiPalette palette, TaskListScrollController scroll)
    {
        this.palette = palette;
        this.scroll = scroll;
        this.collectionLogRequirementRenderer = new CollectionLogRequirementRenderer(
                ROW_HEIGHT,
                180,
                palette.UI_GOLD,
                palette.UI_TEXT,
                palette.UI_TEXT_DIM,
                palette.UI_EDGE_LIGHT,
                palette.UI_EDGE_DARK);
        this.prerequisiteSectionRenderer = new PrerequisiteSectionRenderer(ROW_HEIGHT, palette.UI_TEXT, palette.UI_TEXT_DIM);
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
        mismatchDialogOpen = false;
        mismatchDialogBounds.setBounds(0, 0, 0, 0);
        mismatchIgnoreBounds.setBounds(0, 0, 0, 0);
        requirementsExpanded = false;
        historyExpanded = false;
        requirementsToggleBounds.setBounds(0, 0, 0, 0);
        historyToggleBounds.setBounds(0, 0, 0, 0);
        scroll.reset();
        closeWikiMenu();
    }

    public void close()
    {
        task = null;
        mismatchDialogOpen = false;
        mismatchDialogBounds.setBounds(0, 0, 0, 0);
        mismatchIgnoreBounds.setBounds(0, 0, 0, 0);
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
        requirementsToggleBounds.setBounds(0, 0, 0, 0);
        historyToggleBounds.setBounds(0, 0, 0, 0);
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
            Function<XtremeTask, Boolean> syncMismatchProvider,
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

        boolean compactDetails = task.getSource() == TaskSource.COLLECTION_LOG;
        requirementsToggleBounds.setBounds(0, 0, 0, 0);
        historyToggleBounds.setBounds(0, 0, 0, 0);
        String title = safe(task.getName());
        boolean done = isCompleted.apply(task);
        TaskGroupProgress groupProgress = groupProgressProvider == null ? null : groupProgressProvider.apply(task);
        boolean grouped = groupProgress != null && groupProgress.isGrouped();
        boolean syncMismatch = !mismatchIgnored.test(task) && syncMismatchProvider != null
                && Boolean.TRUE.equals(syncMismatchProvider.apply(task));
        boolean headerComplete = mismatchIgnored.test(task) || (grouped ? groupProgress.isComplete() : done);
        refreshWikiLinks(task, wikiLinksProvider);
        final int pad = 12;
        final int closeW = 28;
        final String wikiOpenText = "Open wiki";
        final String wikiCloseText = "Close";
        final String wikiText = wikiMenuOpen ? wikiCloseText : wikiOpenText;
        final int gap = 8;
        final int rightReserve = closeW + gap;
        Font titleFont = FontManager.getRunescapeBoldFont();
        FontMetrics titleFm = g.getFontMetrics(titleFont);
        int completionReserve = headerComplete || syncMismatch ? 16 : 0;
        int titleDesiredW = titleFm.stringWidth(title) + (pad * 2) + rightReserve + completionReserve + 8;

        // Recompute logical bounds every frame. The overlay may scale input bounds
        // after rendering, so carrying them back into layout would compound scale.
        int maxPopupW = Math.max(280, panelBounds.width - pad * 4);
        int w = Math.min(maxPopupW, Math.max(Math.max(300, (int) (panelBounds.width * 0.50)), titleDesiredW));
        if (compactDetails) w = Math.min(maxPopupW, Math.max(300, (int) (panelBounds.width * 0.50)));
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


        // Title reserves space for the close action.
        int titleMaxW = Math.max(0, bounds.width - (pad * 2) - rightReserve - completionReserve);

        int btnH = ROW_HEIGHT + 8;
        int btnY = yTop - 2;
        // Vertically center title within the button row height using the larger title font
        int titleBaseline = btnY + ((btnH - titleFm.getHeight()) / 2) + titleFm.getAscent();

        g.setColor(syncMismatch ? new Color(245, 92, 82, 245)
                : headerComplete ? UiPalette.TIER_COMPLETE_GLOW : new Color(225, 198, 120));
        g.setFont(titleFont);
        String drawTitle = TextUtils.truncateToWidth(title, titleFm, titleMaxW);
        int titleRight = bounds.x + bounds.width - pad - rightReserve;
        int titleX = x + completionReserve;
        Rectangle mismatchIconBounds = new Rectangle(x, titleBaseline - titleFm.getAscent(), 12, titleFm.getHeight());
        if (syncMismatch)
        {
            g.fillRect(x + 4, titleBaseline - 11, 3, 7);
            g.fillRect(x + 4, titleBaseline - 2, 3, 2);
        }
        else if (headerComplete) DisclosureHeader.check(g, x, titleBaseline);
        g.drawString(drawTitle, titleX, titleBaseline);
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

        g.setFont(FontManager.getRunescapeSmallFont());
        fm = g.getFontMetrics();

        final int badgeH = ROW_HEIGHT + 4;
        final int metaYTop = headerBottomY + 8;

        String tierLabel = task.getTier() == null ? "?" : TaskLabelFormatter.tierLabel(task.getTier());
        Font metadataFont = g.getFont();
        g.setFont(metadataFont.deriveFont(Math.max(1f, metadataFont.getSize2D() - 1f)));
        FontMetrics metadataFm = g.getFontMetrics();
        g.setColor(palette.UI_TEXT_DIM);
        String metadata = TextUtils.truncateToWidth(
                (TaskLabelFormatter.sourceLabel(task.getSource()) + " · " + tierLabel).toUpperCase(java.util.Locale.ROOT),
                metadataFm, Math.max(1, titleRight - x));
        g.drawString(metadata, x, titleBaseline + ROW_HEIGHT + 2);
        g.setFont(metadataFont);

        // Wiki button shares the left edge with the title and metadata.
        int wikiX = x;
        int wikiY = metaYTop + 10;
        wikiBounds.setBounds(wikiX, wikiY, wikiW, badgeH);
        drawBevelBox(g, wikiBounds, palette.BTN_DISABLED_BG);
        g.setColor(palette.UI_TEXT);
        int wikiTextW = fm.stringWidth(wikiText);
        g.drawString(wikiText, wikiBounds.x + (wikiBounds.width - wikiTextW) / 2, centeredTextBaseline(wikiBounds, fm));

        markIncompleteBounds.setBounds(0, 0, 0, 0);
        // Content area
        fm = g.getFontMetrics();
        int contentLeft = bounds.x + pad;
        int wikiDividerY = wikiY + badgeH + 8;
        g.setColor(withAlpha(palette.UI_GOLD, 90));
        g.fillRect(contentLeft, wikiDividerY, bounds.width - pad * 2, 2);
        int contentTop = wikiDividerY + 18;
        final int scrollbarContentGap = 8;
        int contentW = bounds.width - (pad * 2) - scrollbarContentGap;
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
        String prereqs = PrerequisiteSectionRenderer.normalizeText(task.getPrereqs());
        List<PrerequisiteStatus> prerequisiteStatuses = (prerequisiteStatusProvider == null)
                ? List.of()
                : prerequisiteStatusProvider.apply(task);
        if (prerequisiteStatuses == null) prerequisiteStatuses = List.of();

        // Count total content pixel height for scroll math
        boolean groupedCollectionLog = compactDetails && grouped;
        if (compactDetails && requirementPreview != null) requirementPreview = detailPreview(task, requirementPreview, groupedCollectionLog);
        boolean hasRequirementPreview = requirementPreview != null && requirementPreview.hasItems();
        List<PrerequisiteStatus> detailEntries = PrerequisiteSectionRenderer.detailEntries(prereqs, prerequisiteStatuses);
        boolean showHistory = !grouped || !compactDetails || historyExpanded;
        boolean showAchievementDiaryNote = isAchievementDiaryTask(task);
        String desc = showAchievementDiaryNote
                ? diaryTaskDescription(task)
                : safe(task.getDescription()).replace("\r", "").trim();
        boolean showDescriptionSection = !desc.isEmpty();
        String taskTip = showTips ? safe(task.getTip()).replace("\r", "").trim() : "";
        boolean hasTaskTip = !taskTip.isEmpty();
        boolean showGroupedProgressSection = grouped && !groupedCollectionLog;
        int contentLeading = Math.max(4, g.getFontMetrics(FontManager.getRunescapeFont()).getAscent() - fm.getAscent());
        int totalPx = contentLeading;
        if (showGroupedProgressSection)
        {
            totalPx += ROW_HEIGHT; // "Progress" header
            totalPx += ROW_HEIGHT + 8; // progress editor
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
        }
        if (showDescriptionSection)
        {
            totalPx += 6 + 12; // divider gap before next section
        }
        if (hasRequirementPreview)
        {
            totalPx += collectionLogRequirementRenderer.measure(requirementPreview, fm, contentW, !compactDetails);
            totalPx += 6 + 12; // divider gap before requirements
        }
        totalPx += prerequisiteSectionRenderer.measureDetails(detailEntries, requirementsExpanded, fm, contentW,
                prerequisiteSkillImageProvider, prerequisiteMarkerImageProvider);
        totalPx += prerequisiteSectionRenderer.measureSuggestedStats(task.getSuggestedStats(), fm, contentW, prerequisiteSkillImageProvider);
        if (hasTaskTip) totalPx += 20 + prerequisiteSectionRenderer.measureTip(taskTip, fm, contentW, Integer.MAX_VALUE);
        if (groupedCollectionLog)
        {
            totalPx += ROW_HEIGHT;
            if (!instanceHistoryLines.isEmpty())
            {
                totalPx += DisclosureHeader.HEIGHT;
                if (showHistory) totalPx += 8 + instanceHistoryLines.size() * (ROW_HEIGHT + INSTANCE_BLOCK_PAD_BOTTOM);
            }
            totalPx += 18;
        }
        if (!groupedCollectionLog && !instanceHistoryLines.isEmpty())
        {
            totalPx += 6 + 12; // divider gap before completed instance history
            totalPx += compactDetails ? DisclosureHeader.HEIGHT : ROW_HEIGHT; // history header
            if (showHistory) totalPx += 8 + (ROW_HEIGHT + INSTANCE_BLOCK_PAD_BOTTOM) * instanceHistoryLines.size();
        }
        else if (!groupedCollectionLog && (completionLine != null || timeTakenLine != null))
        {
            totalPx += 6 + 12; // divider gap before completion
            totalPx += ROW_HEIGHT; // standalone completion header
            if (showHistory) totalPx += ROW_HEIGHT + INSTANCE_BLOCK_PAD_BOTTOM;
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

        int y = contentTop + fm.getAscent() + contentLeading - scrollPx;

        if (showGroupedProgressSection)
        {
            g.setColor(palette.UI_GOLD);
            drawSectionHeading(g, "Progress", contentLeft, y);
            y += ROW_HEIGHT;
            drawGroupProgressEditor(g, fm, contentLeft, y - fm.getAscent() + 1, contentW, groupProgress, mouse);
            y += ROW_HEIGHT + 8;

            y += 18; // spacing between sections
        }

        if (showDescriptionSection)
        {
            g.setColor(palette.UI_GOLD);
            drawSectionHeading(g, "Description", contentLeft, y);
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
            y += 18; // spacing between sections
        }

        if (hasRequirementPreview)
        {

            y = collectionLogRequirementRenderer.render(
                    g,
                    fm,
                    contentLeft,
                    y,
                    contentW,
                    requirementPreview,
                    collectionLogItemImageProvider,
                    mousePoint,
                    viewportBounds,
                    !compactDetails);

            y += 18; // spacing between sections
        }

        y = prerequisiteSectionRenderer.renderDetails(g, fm, contentLeft, y, contentW,
                detailEntries, requirementsExpanded, requirementsToggleBounds, mousePoint, viewportBounds,
                prerequisiteSkillImageProvider, prerequisiteMarkerImageProvider);

        y = prerequisiteSectionRenderer.renderSuggestedStats(g, fm, contentLeft, y, contentW, task.getSuggestedStats(), prerequisiteSkillImageProvider);
        if (hasTaskTip)
        {
            y += 20;
            y = prerequisiteSectionRenderer.drawTip(g, fm, taskTip, contentLeft, y, contentW, Integer.MAX_VALUE);
        }

        if (groupedCollectionLog)
        {
            y += 18; // spacing between sections
            y = drawInstanceProgress(g, fm, contentLeft, y, contentW, groupProgress, syncMismatch, mouse);
            if (!instanceHistoryLines.isEmpty())
            {
                DisclosureHeader.draw(g, "Completion history (" + instanceHistoryLines.size() + ")",
                        contentLeft, y, contentW, historyExpanded, historyToggleBounds, mousePoint, viewportBounds);
                y += DisclosureHeader.HEIGHT;
                if (showHistory)
                {
                    y += 8;
                    for (InstanceHistoryLine line : instanceHistoryLines)
                    {
                        drawInstanceHistoryLine(g, fm, contentLeft + 12, y, contentW - 12, line, mousePoint);
                        y += ROW_HEIGHT + INSTANCE_BLOCK_PAD_BOTTOM;
                    }
                }
            }
        }

        if (!groupedCollectionLog && !instanceHistoryLines.isEmpty())
        {
            y += 18; // spacing between sections

            g.setColor(palette.UI_GOLD);
            if (compactDetails)
            {
                DisclosureHeader.draw(g, "Completion history (" + instanceHistoryLines.size() + ")",
                        contentLeft, y, contentW, historyExpanded, historyToggleBounds, mousePoint, viewportBounds);
                y += DisclosureHeader.HEIGHT;
            }
            else
            {
                drawSectionHeading(g, "Completed instances", contentLeft, y);
                y += ROW_HEIGHT;
            }

            g.setColor(palette.UI_TEXT_DIM);
            if (showHistory) y += 8;
            for (InstanceHistoryLine line : showHistory ? instanceHistoryLines : List.<InstanceHistoryLine>of())
            {
                drawInstanceHistoryLine(g, fm, contentLeft + 12, y, contentW - 12, line, mousePoint);
                y += ROW_HEIGHT + INSTANCE_BLOCK_PAD_BOTTOM;
            }
        }
        else if (!groupedCollectionLog && (completionLine != null || timeTakenLine != null))
        {
            y += 18; // spacing between sections

            g.setColor(palette.UI_GOLD);
            drawSectionHeading(g, "Completed", contentLeft, y);
            y += ROW_HEIGHT;
            if (showHistory)
            {
                InstanceHistoryLine line = new InstanceHistoryLine(task,
                        instanceCompletionDateText(completionInfo) + " | " + instanceTimeSpentText(completionInfo, ticks));
                drawInstanceHistoryLine(g, fm, contentLeft, y, contentW, line, mousePoint);
                Rectangle action = instanceRemoveBounds.remove(task);
                if (action != null) markIncompleteBounds.setBounds(action.intersection(viewportBounds));
                y += ROW_HEIGHT + INSTANCE_BLOCK_PAD_BOTTOM;
            }
        }

        g.setClip(oldClip);

        // Scrollbar (drawn outside clip)
        UiDraw.drawScrollbar(g, new Rectangle(bounds.x + bounds.width - pad / 2 - scrollBarW, viewportTop, scrollBarW, viewportH),
                totalContentRows, visibleRows, scroll.offsetRows, scrollbarRailBounds, scrollbarThumbBounds,
                palette.UI_EDGE_DARK, palette.UI_EDGE_LIGHT, palette.UI_GOLD);

        if (!wikiMenuOpen && mouse != null && viewportBounds.contains(mouse.getX(), mouse.getY())
                && groupProgressHelpBounds.contains(mouse.getX(), mouse.getY()) && grouped)
        {
            drawTooltip(g, fm, UiText.format("task_details.group_progress_help", groupProgress.getTotal()),
                    groupProgressHelpBounds.x, groupProgressHelpBounds.y + groupProgressHelpBounds.height + 6, true);
        }

        prerequisiteSectionRenderer.renderHover(g, fm, mousePoint, viewportBounds, bounds);

        Rectangle warningHoverArea = new Rectangle(mismatchDialogBounds);
        // Bridge the small gap between the icon and dialog so Ignore remains reachable.
        if (!warningHoverArea.isEmpty()) warningHoverArea.add(mismatchIconBounds);
        boolean overIcon = mouse != null && mismatchIconBounds.contains(mouse.getX(), mouse.getY());
        boolean overDialog = mismatchDialogOpen && mouse != null
                && warningHoverArea.contains(mouse.getX(), mouse.getY());
        mismatchDialogOpen = syncMismatch && !wikiMenuOpen && (overIcon || overDialog);
        mismatchDialogBounds.setBounds(0, 0, 0, 0);
        mismatchIgnoreBounds.setBounds(0, 0, 0, 0);
        if (syncMismatch && !wikiMenuOpen && mismatchDialogOpen)
        {
            String warning = groupedCollectionLog
                    ? "Task completions differ from items seen in-game. Open your Collection Log to refresh, or mark instance(s) incomplete."
                    : syncMismatchTitle(task) + " " + syncMismatchAction(task);
            List<String> lines = TextUtils.wrapText(warning, fm, bounds.width - 24);
            int dialogHeight = lines.size() * ROW_HEIGHT + 42;
            mismatchDialogBounds.setBounds(bounds.x, Math.max(0, bounds.y - dialogHeight - 6), bounds.width, dialogHeight);
            drawBevelBox(g, mismatchDialogBounds, new Color(35, 28, 19, 255));
            g.setColor(new Color(245, 92, 82));
            int lineY = mismatchDialogBounds.y + 12 + fm.getAscent();
            for (String line : lines)
            {
                g.drawString(line, bounds.x + 12, lineY);
                lineY += ROW_HEIGHT;
            }
            int ignoreWidth = fm.stringWidth("Ignore") + 16;
            mismatchIgnoreBounds.setBounds(bounds.x + (bounds.width - ignoreWidth) / 2,
                    mismatchDialogBounds.y + dialogHeight - 24, ignoreWidth, 18);
            drawBevelBox(g, mismatchIgnoreBounds, palette.BTN_ENABLED_BG);
            g.setColor(new Color(190, 190, 190));
            g.drawString("Ignore", mismatchIgnoreBounds.x + (mismatchIgnoreBounds.width - fm.stringWidth("Ignore")) / 2,
                    centeredTextBaseline(mismatchIgnoreBounds, fm));
        }

        if (wikiMenuOpen)
        {
            drawWikiMenu(g, fm, mouse);
        }

    }

    static CollectionLogRequirementPreview detailPreview(XtremeTask task, CollectionLogRequirementPreview preview, boolean grouped)
    {
        String summary = preview.summaryText();
        // Instance progress is shown below; the item summary only reports obtained items.
        if (summary.startsWith("total obtained:")) summary = summary.split("\\|", 2)[0].trim();
        int iconColumns = !grouped && preview.getItems().size() == 1
                ? Math.min(6, preview.iconColumns()) : preview.iconColumns();
        return new CollectionLogRequirementPreview(
                summary, "Collection Log items",
                !summary.isEmpty(), preview.showItemList(), preview.getItems(), iconColumns,
                preview.secondaryTitleText(), preview.secondaryItems(), preview.secondaryIconColumns(), preview.tierSections());
    }

    private static void drawSectionHeading(Graphics2D g, String text, int x, int y)
    {
        Font original = g.getFont();
        g.setFont(FontManager.getRunescapeFont());
        g.drawString(text, x, y);
        g.setFont(original);
    }

    private int drawInstanceProgress(Graphics2D g, FontMetrics fm, int x, int y, int width,
            TaskGroupProgress progress, boolean syncMismatch, net.runelite.api.Point mouse)
    {
        Font original = g.getFont();
        g.setFont(FontManager.getRunescapeFont());
        FontMetrics headingFm = g.getFontMetrics();
        String label = "Task completions: ";
        String count = progress.getCompleted() + "/" + progress.getTotal();
        g.setColor(palette.UI_GOLD);
        g.drawString(label, x, y);
        int countX = x + headingFm.stringWidth(label);
        g.setFont(original);
        g.setColor(palette.UI_TEXT_DIM);
        String numerator = String.valueOf(progress.getCompleted());
        g.setColor(syncMismatch ? new Color(245, 92, 82, 245) : palette.UI_TEXT_DIM);
        g.drawString(numerator, countX, y);
        g.setColor(palette.UI_TEXT_DIM);
        g.drawString("/" + progress.getTotal(), countX + fm.stringWidth(numerator), y);
        drawGroupProgressHelpIcon(g, fm, countX + fm.stringWidth(count) + 6,
                y - headingFm.getAscent(), ROW_HEIGHT, x + width, mouse);
        g.setFont(original);
        return y + ROW_HEIGHT;
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

    static String syncMismatchTitle(XtremeTask task)
    {
        return task != null && task.getSource() == TaskSource.COLLECTION_LOG
                ? UiText.get("task_details.sync_mismatch.collection_log_title")
                : UiText.get("task_details.sync_mismatch.other_title");
    }

    static String syncMismatchAction(XtremeTask task)
    {
        return task != null && task.getSource() == TaskSource.COLLECTION_LOG
                ? UiText.get("task_details.sync_mismatch.collection_log_action")
                : UiText.get("task_details.sync_mismatch.other_action");
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

        boolean hover = mouse != null && actionBounds.contains(mouse);
        String hoverText = "Mark incomplete";
        int hoverX = actionX - actionGap - fm.stringWidth(hoverText);
        int textRight = hover ? hoverX - actionGap : actionX - actionGap;
        String text = TextUtils.truncateToWidth(line.text, fm, Math.max(0, textRight - x));
        g.setColor(palette.UI_TEXT_DIM);
        g.drawString(text, x, baselineY + 2);

        drawMarkIncompleteAction(g, actionBounds, hover);
        if (hover)
        {
            g.setColor(palette.UI_TEXT_DIM);
            g.drawString(hoverText, hoverX, baselineY + 2);
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

    }


    private void drawBevelBox(Graphics2D g, Rectangle r, Color fill)
    {
        UiDraw.drawBevelBox(g, r, fill, palette.UI_EDGE_DARK, palette.UI_EDGE_LIGHT);
    }

    private void drawQuestionIcon(Graphics2D g, int x, int y, int size)
    {
        UiDraw.drawQuestionIcon(g, QUESTION_ICON, x, y, size, withAlpha(palette.UI_GOLD, 140), new Color(20, 15, 10, 220));
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
        int y = anchorY;

        x = Math.max(bounds.x + 6,
                Math.min(x, bounds.x + bounds.width - w - 6));
        y = Math.max(bounds.y + 6, y);

        Rectangle r = new Rectangle(x, y, w, h);

        Shape oldClip = g.getClip();
        g.setClip(null);

        drawBevelBox(g, r, new Color(35, 28, 19, 255));
        g.setColor(palette.UI_TEXT);
        int lineY = r.y + padY + fm.getAscent();
        for (String line : lines)
        {
            g.drawString(line, r.x + padX, lineY);
            lineY += fm.getHeight();
        }

        g.setClip(oldClip);
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
        if (ticks != null && ticks < 0)
        {
            return UiText.get("current.time_spent_completed_before_roll");
        }

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
            return UiText.get("current.time_spent_synced");
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
        if (ticks != null && ticks < 0)
        {
            return "N/A (Completed before rolled)";
        }

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
            return "N/A (synced)";
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
        return remMinutes > 0
                ? hours + "h " + remMinutes + "m"
                : hours + "h";
    }
}
