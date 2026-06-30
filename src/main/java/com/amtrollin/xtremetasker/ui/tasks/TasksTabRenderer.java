package com.amtrollin.xtremetasker.ui.tasks;

import com.amtrollin.xtremetasker.TaskerService;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.tasklist.models.TaskListQuery;
import com.amtrollin.xtremetasker.ui.anim.OverlayAnimations;
import com.amtrollin.xtremetasker.ui.tasklist.TaskRowsLayout;
import com.amtrollin.xtremetasker.ui.tasklist.TaskRowsRenderer;
import com.amtrollin.xtremetasker.ui.text.TaskLabelFormatter;
import com.amtrollin.xtremetasker.ui.text.TextUtils;
import com.amtrollin.xtremetasker.ui.tasks.models.TasksTabState;
import com.amtrollin.xtremetasker.ui.style.UiPalette;
import net.runelite.client.ui.FontManager;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.amtrollin.xtremetasker.ui.style.UiConstants.*;
import static com.amtrollin.xtremetasker.ui.style.UiPalette.withAlpha;

public final class TasksTabRenderer {
    private static final int TASKS_CONTROLS_COLUMN_W = 228;
    private static final long TASK_NAME_TOOLTIP_DELAY_MS = 1000L;
    private static final int TASK_NAME_TOOLTIP_CURSOR_OFFSET_X = 4;
    private static final int TASK_NAME_TOOLTIP_CURSOR_OFFSET_Y = 5;
    private static final int DATE_COL_W = 58;
    private static final int SPENT_COL_W = 48;
    private static final int HEADER_COL_GAP = 6;

    private final UiPalette palette;
    private String hoveredTruncatedTaskKey = null;
    private long hoveredTruncatedTaskSinceMs = 0L;

    public TasksTabRenderer(UiPalette palette) {
        this.palette = palette;
    }

    public void render(
            Graphics2D g,
            FontMetrics fm,
            int panelX,
            int cursorYBaseline,
            Rectangle panelBounds,
            TasksTabState state,
            TaskControlsRenderer controlsRenderer,
            TaskRowsRenderer rowsRenderer,
            TaskerService plugin,
            OverlayAnimations animations,
            List<TaskTier> tierTabs,
            TaskTier activeTier,
            Function<TaskTier, List<XtremeTask>> sortedTasksProvider,
            int hoverX,
            int hoverY
    ) {
        if (!plugin.hasTaskPackLoaded()) {
            g.setColor(palette.UI_TEXT_DIM);
            g.drawString("No tasks loaded.", panelX + PANEL_PADDING, cursorYBaseline);
            return;
        }

        final int panelW = panelBounds.width;
        final int innerW = Math.max(0, panelW - 2 * PANEL_PADDING);

        // -----------------------------
        // Tier tabs row
        // -----------------------------
        int tierTabH = ROW_HEIGHT + 6;
        int tierTabW = (innerW - (tierTabs.size() - 1) * 4) / tierTabs.size();

        int tierTabY = cursorYBaseline - fm.getAscent();
        int x = panelX + PANEL_PADDING;

        synchronized (state.tierTabBounds()) {
            state.tierTabBounds().clear();
        }
        for (TaskTier t : tierTabs) {
            Rectangle r = new Rectangle(x, tierTabY, tierTabW, tierTabH);
            synchronized (state.tierTabBounds()) {
                state.tierTabBounds().put(t, r);
            }

            int pctVal = plugin.getTierPercent(t);
            String pct = pctVal + "%";

            drawTierTabWithPercent(g, r, TaskLabelFormatter.tierLabel(t), pct, pctVal, t == activeTier);

            x += tierTabW + 4;
        }

        cursorYBaseline += tierTabH + 12;

            final int contentTopBaseline = cursorYBaseline;
            final int columnsGap = 8;
            final int controlsColumnW = Math.min(TASKS_CONTROLS_COLUMN_W, Math.max(224, innerW - columnsGap - 280));
            final int controlsColumnX = panelX;
            final int listColumnX = controlsColumnX + controlsColumnW + Math.max(2, columnsGap - 4);
            final int listColumnW = Math.max(220, panelBounds.x + panelBounds.width - PANEL_PADDING - listColumnX);

            controlsRenderer.render(
                g,
                fm,
                controlsColumnX,
                contentTopBaseline,
                state.controlsLayout(),
                state.taskQuery(),
                TaskLabelFormatter.tierLabel(activeTier),
                controlsColumnW,
                hoverX,
                hoverY,
                plugin.hasNewTasks()
            );

            int dividerTop = contentTopBaseline - fm.getAscent();
            int dividerBottom = panelBounds.y + panelBounds.height - PANEL_PADDING;
            if (dividerBottom > dividerTop + 10) {
                int controlsContentRight = state.controlsLayout().searchBox.width > 0
                        ? state.controlsLayout().searchBox.x + state.controlsLayout().searchBox.width
                        : controlsColumnX + controlsColumnW;
                int dividerX = controlsContentRight + Math.max(0, (listColumnX - controlsContentRight) / 2);
                g.setColor(withAlpha(palette.UI_GOLD, 45));
                g.drawLine(dividerX, dividerTop, dividerX, dividerBottom);
            }

            int listCursorBaseline = contentTopBaseline;

        // -----------------------------
        // Progress line
        // -----------------------------
        final int progressPadTop = 8;
        final int progressPadBottom = 8;

            listCursorBaseline += progressPadTop;

        Font oldFont = g.getFont();
        g.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics pfm = g.getFontMetrics();

        int pctVal = plugin.getTierPercent(activeTier);
        String progressLabel = plugin.getTierProgressLabel(activeTier);
        String barLabel = TaskLabelFormatter.tierLabel(activeTier) + ": " + progressLabel;

        int barH = Math.max(20, pfm.getHeight() + 8);
        int barY = listCursorBaseline - pfm.getAscent();
        drawRsProgressBar(g, pfm, listColumnX, barY, listColumnW, barH, pctVal, barLabel);

        g.setFont(oldFont);
        fm = g.getFontMetrics();

        listCursorBaseline += barH + progressPadBottom + 6;

        g.setColor(withAlpha(palette.UI_TEXT_DIM, 170));

        drawTaskTableHeader(g, fm, state.controlsLayout(), state.taskQuery(), listColumnX, listCursorBaseline - 5, listColumnW);

        listCursorBaseline += fm.getHeight() - 1;

        // -----------------------------
        // Tasks list
        // -----------------------------
        List<XtremeTask> tasks = sortedTasksProvider.apply(activeTier);

        int listMaxBottom = panelBounds.y + panelBounds.height - PANEL_PADDING;

        Rectangle listPanelBounds = new Rectangle(
                listColumnX - PANEL_PADDING,
                panelBounds.y,
                listColumnW + (PANEL_PADDING * 2),
                Math.max(0, listMaxBottom + PANEL_PADDING - panelBounds.y)
        );

        if (tasks.isEmpty()) {
            int emptyTop = listCursorBaseline - fm.getAscent();
            int emptyH = Math.max(0, listMaxBottom - emptyTop);

            Rectangle emptyViewport = new Rectangle(listColumnX, emptyTop, listColumnW, emptyH);

            state.taskListViewportBounds().setBounds(emptyViewport);
            state.taskScrollbarRailBounds().setBounds(0, 0, 0, 0);
            state.taskScrollbarThumbBounds().setBounds(0, 0, 0, 0);
            synchronized (state.taskRowBounds()) {
                state.taskRowBounds().clear();
            }
            synchronized (state.taskCheckboxBounds()) {
                state.taskCheckboxBounds().clear();
            }

            g.setColor(palette.UI_TEXT_DIM);
            String msg = "No matches.";
            String filterMsg = emptyStateFilterMessage(state.taskQuery(), TaskLabelFormatter.tierLabel(activeTier));
            int textY = emptyViewport.y + Math.max(ROW_HEIGHT, emptyViewport.height / 3);
            g.drawString(TextUtils.truncateToWidth(msg, fm, emptyViewport.width), emptyViewport.x, textY);
            if (filterMsg != null) {
                g.drawString(TextUtils.truncateToWidth(filterMsg, fm, emptyViewport.width), emptyViewport.x, textY + fm.getHeight() + 2);
            }

            return;
        }

        int listTop = listCursorBaseline - fm.getAscent();
        final int LIST_TOP_INSET = 2;
        listTop += LIST_TOP_INSET;

        int viewportH = Math.max(0, listMaxBottom - listTop);
        int rowBlock = rowsRenderer.rowBlock();

        if (rowBlock > 0) {
            viewportH = (viewportH / rowBlock) * rowBlock;
        }

        int visible = state.tasksScroll().visibleRows(viewportH, rowBlock);
        int maxOffset = Math.max(0, tasks.size() - visible);
        if (state.tasksScroll().offsetRows > maxOffset) {
            state.tasksScroll().offsetRows = maxOffset;
        }

        int sel = state.selectionModel().getSelectedIndex();
        if (sel < 0) sel = 0;
        if (sel > tasks.size() - 1) {
            state.selectionModel().setSelectedIndex(tasks.size() - 1);
            sel = tasks.size() - 1;
        }

        boolean useCondensedRows = plugin.condenseRepeatedTasks();

        TaskRowsLayout layout = rowsRenderer.render(
                g,
                fm,
            listColumnX,
            listCursorBaseline,
                listPanelBounds,
                tasks,
                sel,
                state.tasksScroll().offsetRows,
                hoverX,
                hoverY,
                animations::completionProgress,
                plugin::isTaskCompleted,
                plugin::getCompletionInfo,
                plugin::getTaskTimeTicks,
                state.taskQuery(),
                useCondensedRows ? plugin::getTaskGroupProgress : null,
                useCondensedRows ? plugin::isTaskGroupNew : plugin::isNewTask
        );

        state.taskListViewportBounds().setBounds(layout.viewportBounds);
        state.taskScrollbarRailBounds().setBounds(layout.scrollbarRailBounds);
        state.taskScrollbarThumbBounds().setBounds(layout.scrollbarThumbBounds);

        Rectangle v = layout.viewportBounds;
        if (v.width > 0 && v.height > 0) {
            g.setColor(withAlpha(palette.UI_GOLD, 70));
            g.drawLine(v.x - 2, v.y - 2, v.x + v.width + 2, v.y - 2);
            g.drawLine(v.x + v.width + 2, v.y - 2, v.x + v.width + 2, v.y + v.height + 2);
            g.drawLine(v.x - 2, v.y + v.height + 2, v.x + v.width + 2, v.y + v.height + 2);

            g.setColor(withAlpha(palette.UI_EDGE_LIGHT, 55));
            g.drawLine(v.x - 1, v.y - 1, v.x + v.width + 2, v.y - 1);

            g.setColor(withAlpha(palette.UI_EDGE_DARK, 85));
            g.drawLine(v.x + v.width + 2, v.y - 1, v.x + v.width + 2, v.y + v.height + 2);
            g.drawLine(v.x - 1, v.y + v.height + 2, v.x + v.width + 2, v.y + v.height + 2);
        }

        synchronized (state.taskRowBounds()) {
            state.taskRowBounds().clear();
            state.taskRowBounds().putAll(layout.rowBounds);
        }

        synchronized (state.taskCheckboxBounds()) {
            state.taskCheckboxBounds().clear();
            state.taskCheckboxBounds().putAll(layout.checkboxBounds);
        }

        drawTaskNameHoverTooltip(g, fm, panelBounds, layout, hoverX, hoverY);

    }

    private void drawTierTabWithPercent(Graphics2D g, Rectangle bounds, String leftText, String rightText, int pctValue, boolean active) {
        Color bg = active ? new Color(78, 62, 38, 240) : new Color(32, 26, 17, 235);
        drawBevelBox(g, bounds, bg);

        if (active) {
            g.setColor(palette.UI_GOLD);
            g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        FontMetrics fm = g.getFontMetrics();

        String pct = TextUtils.truncateToWidth(rightText, fm, 34);

        int pctW = fm.stringWidth(pct);
        int pctX = bounds.x + bounds.width - 4 - pctW;

        int leftMaxW = Math.max(0, (pctX - (bounds.x + 4) - 4));
        String tier = TextUtils.truncateToWidth(leftText, fm, leftMaxW);

        int ty = centeredTextBaseline(bounds, fm);

        g.setColor(active ? palette.UI_TEXT : palette.UI_TEXT_DIM);
        g.drawString(tier, bounds.x + 4, ty);

        boolean complete = pctValue >= 100;
        Color pctColor = complete
                ? new Color(120, 200, 140, active ? 230 : 190)
                : (active ? palette.UI_TEXT_DIM : withAlpha(palette.UI_TEXT_DIM, 180));
        g.setColor(pctColor);
        g.drawString(pct, pctX, ty);
    }

    private void drawBevelBox(Graphics2D g, Rectangle r, Color fill) {
        TaskRowsRenderer.drawBevelBoxLogic(g, r, fill, palette.UI_EDGE_DARK, palette.UI_EDGE_LIGHT);
    }

    private int centeredTextBaseline(Rectangle bounds, FontMetrics fm) {
        return bounds.y + ((bounds.height - fm.getHeight()) / 2) + fm.getAscent();
    }

    private void drawRsProgressBar(Graphics2D g, FontMetrics fm, int x, int y, int w, int h, int pctVal, String label) {
        int clamped = Math.min(100, Math.max(0, pctVal));
        int filled = (int) (w * clamped / 100.0);
        boolean complete = clamped >= 100;

        // Track background — very dark, sunken
        g.setColor(new Color(12, 9, 5, 230));
        g.fillRect(x, y, w, h);

        // Fill — two-tone vertical gradient effect
        if (filled > 0) {
            Color fillTop = complete
                    ? new Color(100, 185, 120, 235)
                    : new Color(190, 148, 50, 235);
            Color fillBot = complete
                    ? new Color(60, 130, 80, 235)
                    : new Color(130, 96, 28, 235);
            int half = h / 2;
            g.setColor(fillTop);
            g.fillRect(x, y, filled, half);
            g.setColor(fillBot);
            g.fillRect(x, y + half, filled, h - half);
        }

        // Bevel: highlight top-left, shadow bottom-right
        g.setColor(new Color(110, 90, 52, 190));
        g.drawLine(x, y, x + w - 1, y);           // top
        g.drawLine(x, y, x, y + h - 1);            // left
        g.setColor(new Color(5, 4, 2, 210));
        g.drawLine(x, y + h - 1, x + w - 1, y + h - 1); // bottom
        g.drawLine(x + w - 1, y, x + w - 1, y + h - 1); // right

        // Centered label with drop shadow
        String text = TextUtils.truncateToWidth(label, fm, w - 4);
        int textW = fm.stringWidth(text);
        int textX = x + (w - textW) / 2;
        int textY = y + ((h - fm.getHeight()) / 2) + fm.getAscent();

        g.setColor(new Color(0, 0, 0, 200));
        g.drawString(text, textX + 1, textY + 1);
        g.setColor(complete ? new Color(255, 215, 100, 255) : palette.UI_TEXT);
        g.drawString(text, textX, textY);
    }

    private void drawTaskTableHeader(
            Graphics2D g,
            FontMetrics fm,
            com.amtrollin.xtremetasker.ui.tasks.models.TaskControlsLayout layout,
            TaskListQuery query,
            int x,
            int baselineY,
            int width
    ) {
        FontMetrics badgeFm = g.getFontMetrics(FontManager.getRunescapeSmallFont());
        int badgeReserveW = badgeHeaderReserveWidth(badgeFm, query);

        g.setColor(withAlpha(palette.UI_TEXT_DIM, 170));
        int taskHeaderW = Math.max(0, width - badgeReserveW);
        g.drawString(TextUtils.truncateToWidth("Task", fm, taskHeaderW), x, baselineY);
        drawBadgeColumnHeaders(g, layout, query, baselineY, x, width);
    }

    private void drawBadgeColumnHeaders(Graphics2D g, com.amtrollin.xtremetasker.ui.tasks.models.TaskControlsLayout layout, TaskListQuery query,
                                        int baselineY, int x, int width) {
        Font oldFont = g.getFont();
        g.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics sfm = g.getFontMetrics();
        int pillPadX = 6;
        int top = baselineY - sfm.getAscent() - 2;
        int h = sfm.getHeight() + 4;
        int nextRight = x + width - 13;

        layout.sortDateCompleted.setBounds(0, 0, 0, 0);
        layout.sortTimeSpent.setBounds(0, 0, 0, 0);

        int srcW = Math.max(
                Math.max(sfm.stringWidth("CA"), Math.max(sfm.stringWidth("CL"), sfm.stringWidth("AD"))) + pillPadX * 2,
                sfm.stringWidth("Src") + 4);
        int srcX = nextRight - srcW;
        drawSmallHeaderLabel(g, sfm, new Rectangle(srcX, top, srcW, h), "Src");
        nextRight = srcX - HEADER_COL_GAP;

        int tierW = Math.max(sfm.stringWidth("Master") + pillPadX * 2, sfm.stringWidth("Tier") + 4);
        int tierX = nextRight - tierW;
        drawSmallHeaderLabel(g, sfm, new Rectangle(tierX, top, tierW, h), "Tier");
        nextRight = tierX - HEADER_COL_GAP;

        if (query != null && query.showTimeSpentColumn)
        {
            int spentX = nextRight - SPENT_COL_W;
            layout.sortTimeSpent.setBounds(spentX, top, SPENT_COL_W, h);
            drawSmallHeaderLabel(g, sfm, layout.sortTimeSpent, sortHeaderLabel("Spent", query, TaskListQuery.SortColumn.SPENT));
            nextRight = spentX - HEADER_COL_GAP;
        }

        if (query != null && query.showDateCompletedColumn)
        {
            int dateX = nextRight - DATE_COL_W;
            layout.sortDateCompleted.setBounds(dateX, top, DATE_COL_W, h);
            drawSmallHeaderLabel(g, sfm, layout.sortDateCompleted, sortHeaderLabel("Date", query, TaskListQuery.SortColumn.DATE));
        }
        g.setFont(oldFont);
    }

    private static String sortHeaderLabel(String label, TaskListQuery query, TaskListQuery.SortColumn column)
    {
        if (query == null || query.sortColumn != column || query.sortDirection == TaskListQuery.SortDirection.OFF)
        {
            return label + " -";
        }
        return label + (query.sortDirection == TaskListQuery.SortDirection.DESC ? " v" : " ^");
    }

    private void drawSmallHeaderLabel(Graphics2D g, FontMetrics fm, Rectangle bounds, String text) {
        if (bounds == null || bounds.width <= 0)
        {
            return;
        }
        g.setColor(withAlpha(palette.UI_TEXT_DIM, 160));
        String draw = TextUtils.truncateToWidth(text, fm, bounds.width);
        g.drawString(draw, bounds.x + Math.max(0, (bounds.width - fm.stringWidth(draw)) / 2),
                bounds.y + ((bounds.height - fm.getHeight()) / 2) + fm.getAscent());
    }

    private static int badgeHeaderReserveWidth(FontMetrics fm, TaskListQuery query)
    {
        int sourceW = Math.max(
                Math.max(fm.stringWidth("CA"), Math.max(fm.stringWidth("CL"), fm.stringWidth("AD"))) + 12,
                fm.stringWidth("Src") + 4);
        int tierW = Math.max(fm.stringWidth("Master") + 12, fm.stringWidth("Tier") + 4);

        int width = 13;
        width = addColumnWidth(width, query != null && query.showDateCompletedColumn ? DATE_COL_W : 0);
        width = addColumnWidth(width, query != null && query.showTimeSpentColumn ? SPENT_COL_W : 0);
        width = addColumnWidth(width, tierW);
        width = addColumnWidth(width, sourceW);
        return width;
    }

    private static int addColumnWidth(int current, int width)
    {
        if (width <= 0)
        {
            return current;
        }
        return current == 0 ? width : current + HEADER_COL_GAP + width;
    }

    private void drawTaskNameHoverTooltip(
            Graphics2D g,
            FontMetrics fm,
            Rectangle panelBounds,
            TaskRowsLayout layout,
            int hoverX,
            int hoverY
    ) {
        if (hoverX < 0 || hoverY < 0 || layout.truncatedNameBounds.isEmpty()) {
            resetTaskNameTooltipHover();
            return;
        }

        XtremeTask hoveredTask = null;
        for (Map.Entry<XtremeTask, Rectangle> entry : layout.truncatedNameBounds.entrySet()) {
            Rectangle bounds = entry.getValue();
            if (bounds != null && bounds.contains(hoverX, hoverY)) {
                hoveredTask = entry.getKey();
                break;
            }
        }

        if (hoveredTask == null) {
            resetTaskNameTooltipHover();
            return;
        }

        String name = hoveredTask.getName();
        if (name == null || name.trim().isEmpty()) {
            resetTaskNameTooltipHover();
            return;
        }

        String taskKey = taskHoverKey(hoveredTask);
        long now = System.currentTimeMillis();
        if (!taskKey.equals(hoveredTruncatedTaskKey)) {
            hoveredTruncatedTaskKey = taskKey;
            hoveredTruncatedTaskSinceMs = now;
            return;
        }

        if (now - hoveredTruncatedTaskSinceMs < TASK_NAME_TOOLTIP_DELAY_MS) {
            return;
        }

        final int pad = 7;
        final int maxW = Math.max(120, panelBounds.width - PANEL_PADDING * 2 - 12);
        List<String> lines = TextUtils.wrapText(name, fm, maxW - pad * 2);
        if (lines.isEmpty()) {
            return;
        }

        int contentW = 0;
        for (String line : lines) {
            contentW = Math.max(contentW, fm.stringWidth(line));
        }

        int tooltipW = Math.min(maxW, contentW + pad * 2);
        int tooltipH = pad * 2 + lines.size() * fm.getHeight();
        int tooltipX = hoverX + TASK_NAME_TOOLTIP_CURSOR_OFFSET_X;
        int rightLimit = panelBounds.x + panelBounds.width - PANEL_PADDING;
        if (tooltipX + tooltipW > rightLimit) {
            tooltipX = hoverX - tooltipW - TASK_NAME_TOOLTIP_CURSOR_OFFSET_X;
        }
        tooltipX = Math.max(panelBounds.x + PANEL_PADDING, tooltipX);

        int tooltipY = hoverY + TASK_NAME_TOOLTIP_CURSOR_OFFSET_Y;
        int bottomLimit = panelBounds.y + panelBounds.height - PANEL_PADDING;
        if (tooltipY + tooltipH > bottomLimit) {
            tooltipY = hoverY - tooltipH - TASK_NAME_TOOLTIP_CURSOR_OFFSET_Y;
        }
        tooltipY = Math.max(panelBounds.y + PANEL_PADDING, tooltipY);

        Rectangle tooltipBounds = new Rectangle(tooltipX, tooltipY, tooltipW, tooltipH);
        drawBevelBox(g, tooltipBounds, new Color(45, 36, 24, 248));

        int textY = tooltipY + pad + fm.getAscent();
        g.setColor(palette.UI_TEXT);
        for (String line : lines) {
            g.drawString(line, tooltipX + pad, textY);
            textY += fm.getHeight();
        }
    }

    private void resetTaskNameTooltipHover()
    {
        hoveredTruncatedTaskKey = null;
        hoveredTruncatedTaskSinceMs = 0L;
    }

    private static String taskHoverKey(XtremeTask task)
    {
        String id = task.getId();
        if (id != null && !id.isEmpty())
        {
            return id;
        }

        String name = task.getName();
        return name == null ? "" : name;
    }

    private static String emptyStateFilterMessage(TaskListQuery q, String activeTierLabel) {
        if (q == null) {
            return null;
        }

        List<String> filters = new ArrayList<>();

        if (q.showNewTasksFilter) {
            filters.add("New tasks");
        }

        if (!q.isSourceAllSelected() && q.sourceCASelected) {
            filters.add("CAs");
        }
        if (!q.isSourceAllSelected() && q.sourceClogsSelected) {
            filters.add("CLOGs");
        }
        if (!q.isSourceAllSelected() && q.sourceDasSelected) {
            filters.add("ADs");
        }

        if (q.statusFilter == TaskListQuery.StatusFilter.COMPLETE) {
            filters.add("Complete");
        } else if (q.statusFilter == TaskListQuery.StatusFilter.INCOMPLETE) {
            filters.add("Incomplete");
        }

        if (q.tierScope == TaskListQuery.TierScope.THIS_TIER && activeTierLabel != null && !activeTierLabel.isEmpty()) {
            filters.add(activeTierLabel + " tier");
        }

        if (filters.isEmpty()) {
            return null;
        }

        return "Applied filter(s): " + String.join(" + ", filters);
    }

}
