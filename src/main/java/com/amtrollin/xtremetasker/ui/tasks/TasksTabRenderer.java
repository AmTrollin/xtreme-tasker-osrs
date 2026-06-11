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
import java.util.function.Function;

import static com.amtrollin.xtremetasker.ui.style.UiConstants.*;

public final class TasksTabRenderer {
    private static final int TASKS_CONTROLS_COLUMN_W = 298;

    private final UiPalette palette;

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
            int hoverY,
            boolean keyboardHintsOpen
    ) {
        if (!plugin.hasTaskPackLoaded()) {
            g.setColor(palette.UI_TEXT_DIM);
            g.drawString("No tasks loaded.", panelX + PANEL_PADDING, cursorYBaseline);
            return;
        }

        final int panelW = panelBounds.width;
        final int innerW = Math.max(0, panelW - 2 * PANEL_PADDING);
        state.taskViewModeBounds().setBounds(0, 0, 0, 0);

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
            final int columnsGap = 10;
            final int controlsColumnW = Math.min(TASKS_CONTROLS_COLUMN_W, Math.max(250, innerW - columnsGap - 220));
            final int listColumnW = Math.max(220, innerW - controlsColumnW - columnsGap);
            final int controlsColumnX = panelX + PANEL_PADDING;
            final int listColumnX = controlsColumnX + controlsColumnW + columnsGap;

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
            int dividerBottom = panelBounds.y + panelBounds.height - PANEL_PADDING - fm.getHeight() - 8;
            if (dividerBottom > dividerTop + 10) {
                int dividerX = listColumnX - (columnsGap / 2);
                g.setColor(new Color(palette.UI_GOLD.getRed(), palette.UI_GOLD.getGreen(), palette.UI_GOLD.getBlue(), 45));
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

        g.setColor(new Color(
                palette.UI_TEXT_DIM.getRed(),
                palette.UI_TEXT_DIM.getGreen(),
                palette.UI_TEXT_DIM.getBlue(),
                170
        ));

        int hintVisualOffset = -5;
        String taskHint = "Task list: ";
        boolean condenseBlocked = state.taskQuery().sortByDate || state.taskQuery().sortByTimeTicks;
        boolean condensedView = plugin.condenseRepeatedTasks()
                && !condenseBlocked;
        int modeReserve = drawTaskViewModeHint(
                g,
                fm,
            listColumnX,
            listCursorBaseline + hintVisualOffset,
            listColumnW,
                condensedView,
                condenseBlocked,
                hoverX,
                hoverY,
                state.taskViewModeBounds()
        );
        g.setColor(new Color(
                palette.UI_TEXT_DIM.getRed(),
                palette.UI_TEXT_DIM.getGreen(),
                palette.UI_TEXT_DIM.getBlue(),
                170
        ));
        g.drawString(
            TextUtils.truncateToWidth(taskHint, fm, Math.max(0, listColumnW - modeReserve - 12)),
            listColumnX,
            listCursorBaseline + hintVisualOffset
        );

        listCursorBaseline += fm.getHeight() - 1;

        // -----------------------------
        // Tasks list
        // -----------------------------
        List<XtremeTask> tasks = sortedTasksProvider.apply(activeTier);

        int hintPaddingBottom = 10;

        int hintBaselineY = panelBounds.y + panelBounds.height - hintPaddingBottom;
        int keyboardButtonTop = hintBaselineY - fm.getAscent() - 3;
        int listMaxBottom = keyboardButtonTop - 3;

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

            displayKeyboardHintsButton(g, fm, panelX, hintBaselineY, innerW, state.keyboardHintsButtonBounds(), hoverX, hoverY);
            if (keyboardHintsOpen) {
                displayKeyboardHintsPopup(g, fm, panelBounds, state.keyboardHintsButtonBounds(), state.keyboardHintsPopupBounds());
            } else {
                state.keyboardHintsPopupBounds().setBounds(0, 0, 0, 0);
            }
            return;
        }

        int listTop = listCursorBaseline - fm.getAscent();
        final int LIST_TOP_INSET = 5;
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

        boolean useCondensedRows = plugin.condenseRepeatedTasks()
                && !state.taskQuery().sortByDate
                && !state.taskQuery().sortByTimeTicks;

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
                useCondensedRows ? plugin::getTaskGroupProgress : null,
                useCondensedRows ? plugin::isTaskGroupNew : plugin::isNewTask,
                plugin::getCompletionInfo,
                plugin::getTaskTimeTicks,
                state.taskQuery().sortByDate,
                state.taskQuery().sortByTimeTicks
        );

        state.taskListViewportBounds().setBounds(layout.viewportBounds);
        state.taskScrollbarRailBounds().setBounds(layout.scrollbarRailBounds);
        state.taskScrollbarThumbBounds().setBounds(layout.scrollbarThumbBounds);

        Rectangle v = layout.viewportBounds;
        if (v.width > 0 && v.height > 0) {
            g.setColor(new Color(palette.UI_GOLD.getRed(), palette.UI_GOLD.getGreen(), palette.UI_GOLD.getBlue(), 70));
            g.drawRect(v.x - 2, v.y - 2, v.width + 4, v.height + 4);

            g.setColor(new Color(palette.UI_EDGE_LIGHT.getRed(), palette.UI_EDGE_LIGHT.getGreen(), palette.UI_EDGE_LIGHT.getBlue(), 55));
            g.drawLine(v.x - 1, v.y - 1, v.x + v.width + 2, v.y - 1);
            g.drawLine(v.x - 1, v.y - 1, v.x - 1, v.y + v.height + 2);

            g.setColor(new Color(palette.UI_EDGE_DARK.getRed(), palette.UI_EDGE_DARK.getGreen(), palette.UI_EDGE_DARK.getBlue(), 85));
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

        displayKeyboardHintsButton(g, fm, panelX, hintBaselineY, innerW, state.keyboardHintsButtonBounds(), hoverX, hoverY);
        if (keyboardHintsOpen) {
            displayKeyboardHintsPopup(g, fm, panelBounds, state.keyboardHintsButtonBounds(), state.keyboardHintsPopupBounds());
        } else {
            state.keyboardHintsPopupBounds().setBounds(0, 0, 0, 0);
        }
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
                : (active ? palette.UI_TEXT_DIM : new Color(palette.UI_TEXT_DIM.getRed(), palette.UI_TEXT_DIM.getGreen(), palette.UI_TEXT_DIM.getBlue(), 180));
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

    private void displayKeyboardHintsButton(
            Graphics2D g,
            FontMetrics fm,
            int panelX,
            int hintBaselineY,
            int innerW,
            Rectangle buttonBounds,
            int hoverX,
            int hoverY
    ) {
        String label = "[Keyboard hints]";
        int padX = 9;
        int buttonH = fm.getHeight() + 6;
        int buttonW = padX * 2 + fm.stringWidth(label);
        int buttonX = panelX + PANEL_PADDING + (innerW - buttonW) / 2;
        int buttonY = hintBaselineY - fm.getAscent() - 3;
        buttonBounds.setBounds(buttonX, buttonY, buttonW, buttonH);

        boolean hovered = buttonBounds.contains(hoverX, hoverY);
        g.setColor(hovered
                ? new Color(palette.UI_TEXT_DIM.getRed(), palette.UI_TEXT_DIM.getGreen(), palette.UI_TEXT_DIM.getBlue(), 230)
                : new Color(palette.UI_TEXT_DIM.getRed(), palette.UI_TEXT_DIM.getGreen(), palette.UI_TEXT_DIM.getBlue(), 180));
        g.drawString(label, buttonX + padX, hintBaselineY);
    }

    private void displayKeyboardHintsPopup(
            Graphics2D g,
            FontMetrics fm,
            Rectangle panelBounds,
            Rectangle buttonBounds,
            Rectangle popupBounds
    ) {
        String title = "Keyboard hints";
        String[][] sections = {
                {"Tasks:", "Space/Enter - toggle selected task status", "Up/Down - move through the task list", "Left/Right - switch tier tab"},
                {"Filter:", "1/2/3/4 - toggle source (all, CAs, CLs, ADs)", "Q/W/E - toggle status (all, complete, incomplete)", "A - toggle tier"},
                {"Sorts:", "S - source", "T - tier", "D - completion date", "M - time spent", "R - reset sorting"}
        };

        int pad = 10;
        int contentW = fm.stringWidth(title);
        int lineCount = 1;
        for (String[] section : sections) {
            lineCount += section.length;
            for (String line : section) {
                contentW = Math.max(contentW, fm.stringWidth(line));
            }
        }

        int w = Math.min(panelBounds.width - (PANEL_PADDING * 2), contentW + pad * 2);
        int sectionGap = 5;
        int h = pad * 2 + fm.getHeight() * lineCount + sectionGap * (sections.length - 1) + 5;
        int x = panelBounds.x + (panelBounds.width - w) / 2;
        int y = buttonBounds.y - h - 8;
        popupBounds.setBounds(x, y, w, h);

        drawBevelBox(g, popupBounds, new Color(45, 36, 24, 248));

        int textX = x + pad;
        int textY = y + pad + fm.getAscent();
        g.setColor(palette.UI_GOLD);
        g.drawString(TextUtils.truncateToWidth(title, fm, w - pad * 2), textX, textY);
        textY += fm.getHeight() + 5;

        for (String[] section : sections) {
            g.setColor(palette.UI_TEXT);
            g.drawString(TextUtils.truncateToWidth(section[0], fm, w - pad * 2), textX, textY);
            textY += fm.getHeight();

            g.setColor(new Color(palette.UI_TEXT_DIM.getRed(), palette.UI_TEXT_DIM.getGreen(), palette.UI_TEXT_DIM.getBlue(), 190));
            for (int i = 1; i < section.length; i++) {
                g.drawString(TextUtils.truncateToWidth(section[i], fm, w - pad * 2), textX, textY);
                textY += fm.getHeight();
            }
            textY += sectionGap;
        }
    }

    private static String emptyStateFilterMessage(TaskListQuery q, String activeTierLabel) {
        if (q == null) {
            return null;
        }

        List<String> filters = new ArrayList<>();

        if (q.showNewTasksFilter) {
            filters.add("New tasks");
        }

        if (q.sourceFilter == TaskListQuery.SourceFilter.CA) {
            filters.add("CAs");
        } else if (q.sourceFilter == TaskListQuery.SourceFilter.CLOGS) {
            filters.add("CLs");
        } else if (q.sourceFilter == TaskListQuery.SourceFilter.DAS) {
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

    private int drawTaskViewModeHint(
            Graphics2D g,
            FontMetrics fm,
            int x,
            int baselineY,
            int width,
            boolean condensedView,
            boolean disabled,
            int hoverX,
            int hoverY,
            Rectangle viewModeBounds
    ) {
        final int iconGap = 5;
        final int iconSize = fm.getAscent() + 2;
        String label = disabled ? "Condense repeated tasks" : (condensedView ? "Separate repeated tasks" : "Condense repeated tasks");
        int labelW = fm.stringWidth(label);
        int totalW = labelW + iconGap + iconSize;
        int startX = x + Math.max(0, width - totalW);
        int top = baselineY - fm.getAscent();
        viewModeBounds.setBounds(startX - 4, top - 2, totalW + 8, Math.max(iconSize, fm.getHeight()) + 4);
        boolean hovered = viewModeBounds.contains(hoverX, hoverY);

        g.setColor(new Color(
                palette.UI_TEXT_DIM.getRed(),
                palette.UI_TEXT_DIM.getGreen(),
                palette.UI_TEXT_DIM.getBlue(),
                disabled ? (hovered ? 145 : 105) : (hovered ? 230 : 170)
        ));
        g.drawString(label, startX, baselineY);

        if (disabled && hovered)
        {
            String tooltip = "Cannot condense with date and/or time spent filters";
            String shown = TextUtils.truncateToWidth(tooltip, fm, width);
            int tipX = x + Math.max(0, width - fm.stringWidth(shown));
            int tipY = top - 4;
            g.setColor(new Color(
                    palette.UI_TEXT_DIM.getRed(),
                    palette.UI_TEXT_DIM.getGreen(),
                    palette.UI_TEXT_DIM.getBlue(),
                    190
            ));
            g.drawString(shown, tipX, tipY);
        }

        int iconX = startX + labelW + iconGap;
        int iconY = baselineY - fm.getAscent() - 2;

        Color iconFill = disabled ? palette.UI_TEXT_DIM : palette.UI_GOLD;
        int iconAlpha = disabled ? (hovered ? 80 : 55) : 120;
        g.setColor(new Color(iconFill.getRed(), iconFill.getGreen(), iconFill.getBlue(), iconAlpha));
        g.fillOval(iconX, iconY, iconSize, iconSize);
        drawToggleIcon(g, iconX, iconY, iconSize);

        return totalW;
    }

    private void drawToggleIcon(Graphics2D g, int x, int y, int size)
    {
        Color iconColor = new Color(20, 15, 10, 220);
        Stroke oldStroke = g.getStroke();
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(iconColor);

        int midY = y + size / 2;
        int left = x + 4;
        int right = x + size - 4;
        int arrow = 3;

        g.drawLine(left, midY - 2, right, midY - 2);
        g.drawLine(right, midY - 2, right - arrow, midY - 5);
        g.drawLine(right, midY - 2, right - arrow, midY + 1);

        g.drawLine(right, midY + 3, left, midY + 3);
        g.drawLine(left, midY + 3, left + arrow, midY);
        g.drawLine(left, midY + 3, left + arrow, midY + 6);

        g.setStroke(oldStroke);
    }

}
