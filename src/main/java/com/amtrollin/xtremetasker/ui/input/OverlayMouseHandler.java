package com.amtrollin.xtremetasker.ui.input;

import com.amtrollin.xtremetasker.ui.XtremeTaskerOverlay;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.tasklist.models.TaskListQuery;
import com.amtrollin.xtremetasker.ui.rules.*;
import com.amtrollin.xtremetasker.ui.tasks.models.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import net.runelite.api.widgets.*;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.util.LinkBrowser;
import static com.amtrollin.xtremetasker.ui.style.UiConstants.ICON_ANCHOR_PAD;

@RequiredArgsConstructor
public final class OverlayMouseHandler extends MouseAdapter {
    private final XtremeTaskerOverlay a;
    private final Runnable onOpenPanel; // resets scroll, clears overrides, etc.

    // Transient icon press state — distinguishes click from drag.
    private boolean pressedOnIcon = false;
    private int iconPressX = 0;
    private int iconPressY = 0;
    private static final int ICON_DRAG_THRESHOLD_SQ = 25; // 5px
    private boolean draggingTaskScrollbar = false;
    private int taskScrollbarGrabOffsetY = 0;
    private boolean draggingCurrentScrollbar = false;
    private int currentScrollbarGrabOffsetY = 0;
    private boolean draggingTaskDetailsScrollbar = false;
    private int taskDetailsScrollbarGrabOffsetY = 0;
    private boolean draggingSyncMismatchScrollbar = false;
    private int syncMismatchScrollbarGrabOffsetY = 0;
    private long lastTaskRowClickHandledAt = 0L;
    private int lastTaskRowClickX = Integer.MIN_VALUE;
    private int lastTaskRowClickY = Integer.MIN_VALUE;
    private int lastTaskRowClickButton = MouseEvent.NOBUTTON;
    private static final int SYNC_MISMATCH_DUPLICATE_CLICK_TOLERANCE_PX = 6;
    private boolean suppressNextSyncMismatchClicked = false;
    private int suppressSyncMismatchClickX = Integer.MIN_VALUE;
    private int suppressSyncMismatchClickY = Integer.MIN_VALUE;
    private int suppressSyncMismatchClickButton = MouseEvent.NOBUTTON;
    private long syncMismatchReviewOpenedAt = Long.MIN_VALUE;
    private static final long SYNC_MISMATCH_OPENING_GRACE_MS = 250L;
    private boolean suppressNextTaskDetailsIncompleteConfirmClicked = false;
    private int suppressTaskDetailsIncompleteConfirmClickX = Integer.MIN_VALUE;
    private int suppressTaskDetailsIncompleteConfirmClickY = Integer.MIN_VALUE;
    private int suppressTaskDetailsIncompleteConfirmClickButton = MouseEvent.NOBUTTON;

    @Override
    public MouseEvent mousePressed(MouseEvent e) {
        if (!a.plugin().isOverlayEnabled()) {
            return e;
        }

        Point p = e.getPoint();
        int button = e.getButton();

        // icon pressed — record for click-vs-drag detection
        if (button == MouseEvent.BUTTON1 && a.iconBounds().contains(p) && !isSyncMismatchLayerActive()) {
            pressedOnIcon = true;
            iconPressX = e.getX();
            iconPressY = e.getY();
            a.setIconDragOffset(e.getX() - a.iconBounds().x, e.getY() - a.iconBounds().y);
            e.consume();
            return e;
        }



        if (!a.isPanelOpen()) {
            return e;
        }

        if (a.isMarkAllIncompleteConfirmOpen() && button == MouseEvent.BUTTON1) {
            if (a.markAllIncompleteYesBounds().contains(p)) {
                XtremeTask task = a.markAllIncompleteConfirmationTask();
                if (task != null) {
                    if (a.markAllIncompleteConfirmationGroupMode()) {
                        a.plugin().setTaskGroupCompletedCountAndPersist(task, 0);
                    } else if (a.plugin().isTaskCompleted(task)) {
                        if (a.markIncompleteDontShowChecked()) {
                            a.plugin().setSkipSingleIncompleteConfirmation(true);
                        }
                        a.plugin().toggleTaskCompletedAndPersist(task);
                    }
                }
                a.closeMarkAllIncompleteConfirmation();
                e.consume();
                return e;
            }

            if (a.markIncompleteDontShowBounds().contains(p)) {
                a.setMarkIncompleteDontShowChecked(!a.markIncompleteDontShowChecked());
                e.consume();
                return e;
            }

            if (a.markAllIncompleteNoBounds().contains(p)) {
                a.closeMarkAllIncompleteConfirmation();
                e.consume();
                return e;
            }

            if (a.markAllIncompleteConfirmBounds().contains(p)) {
                e.consume();
                return e;
            }

            e.consume();
            return e;
        }

        if (tryHandleTaskDetailsIncompleteConfirmClick(e, p, button)) {
            return e;
        }

        if (tryHandleSyncMismatchClick(e, p, button)) {
            return e;
        }

        // ------------------------------------------------------------
        // DETAILS POPUP click handling (highest priority)
        // ------------------------------------------------------------
        if (a.isTaskDetailsOpen() && button == MouseEvent.BUTTON1) {
            boolean popupHasSize = a.taskDetailsBounds().width > 0 && a.taskDetailsBounds().height > 0;
            boolean insidePopup = popupHasSize && a.taskDetailsBounds().contains(p);

            if (!insidePopup) {
                // Click outside popup — close it, then fall through so the
                // row click (or other action) is still processed this frame.
                a.closeTaskDetails();
                if (a.isTaskDetailsIncompleteConfirmOpen())
                {
                    rememberTaskDetailsIncompleteConfirmOpeningClick(e, p, button);
                    e.consume();
                    return e;
                }
            } else {
                if (a.isTaskDetailsWikiMenuOpen()) {
                    WikiLink link = a.taskDetailsWikiLinkAt(p);
                    if (link != null) {
                        LinkBrowser.browse(link.url());
                        e.consume();
                        return e;
                    }
                }

                if (a.taskDetailsScrollbarRailBounds().width > 0) {
                    Rectangle thumb = a.taskDetailsScrollbarThumbBounds();
                    Rectangle rail = a.taskDetailsScrollbarRailBounds();

                    if (thumb.contains(p)) {
                        draggingTaskDetailsScrollbar = true;
                        taskDetailsScrollbarGrabOffsetY = p.y - thumb.y;
                        e.consume();
                        return e;
                    }

                    if (rail.contains(p)) {
                        draggingTaskDetailsScrollbar = true;
                        taskDetailsScrollbarGrabOffsetY = Math.max(0, thumb.height / 2);
                        updateTaskDetailsScrollbarDrag(e.getY());
                        e.consume();
                        return e;
                    }
                }

                // Close button
                if (a.taskDetailsCloseBounds().contains(p)) {
                    a.closeTaskDetails();
                    if (a.isTaskDetailsIncompleteConfirmOpen())
                    {
                        rememberTaskDetailsIncompleteConfirmOpeningClick(e, p, button);
                    }
                    e.consume();
                    return e;
                }

                // Wiki button
                if (a.taskDetailsWikiBounds().contains(p)) {
                    XtremeTask t = a.taskDetailsTask();
                    if (t != null) {
                        if (a.isTaskDetailsWikiMenuOpen()) {
                            a.closeTaskDetailsWikiMenu();
                        } else {
                            List<WikiLink> links = a.taskDetailsWikiLinks(t);
                            if (links.size() > 1) {
                                a.openTaskDetailsWikiMenu();
                            } else {
                                String url = !links.isEmpty() ? links.get(0).url() : t.getWikiUrl();
                                if (url != null && !url.trim().isEmpty()) {
                                    LinkBrowser.browse(url);
                                }
                            }
                        }
                    }
                    e.consume();
                    return e;
                }

                if (a.taskDetailsMarkIncompleteBounds().contains(p)) {
                    a.handleTaskDetailsMarkIncompleteButton(a.taskDetailsTask());
                    rememberTaskDetailsIncompleteConfirmOpeningClick(e, p, button);
                    e.consume();
                    return e;
                }

                for (Map.Entry<XtremeTask, Rectangle> entry : a.taskDetailsInstanceRemoveBounds().entrySet()) {
                    Rectangle bounds = entry.getValue();
                    if (bounds != null && bounds.contains(p)) {
                        a.handleTaskDetailsInstanceMarkIncompleteButton(entry.getKey());
                        rememberTaskDetailsIncompleteConfirmOpeningClick(e, p, button);
                        e.consume();
                        return e;
                    }
                }

                // General click inside popup — consume and stop
                e.consume();
                return e;
            }
        }

        // click outside closes (do this early)
        if (button == MouseEvent.BUTTON1
                && a.panelBounds().width > 0 && a.panelBounds().height > 0
                && !a.panelBounds().contains(p)) {
            a.setPanelOpen(false);
            a.setDraggingPanel(false);
            e.consume();
            return e;
        }

        // X close button
        if (button == MouseEvent.BUTTON1 && a.panelCloseBounds().contains(p)) {
            a.setPanelOpen(false);
            e.consume();
            return e;
        }

        if (button == MouseEvent.BUTTON1 && a.panelModeToggleBounds().contains(p)) {
            a.setCompactPanelMode(!a.isCompactPanelMode());
            a.setActiveTab(XtremeTaskerOverlay.MainTab.CURRENT);
            e.consume();
            return e;
        }

        if (tryHandleTaskRowClick(e, p, button)) {
            return e;
        }

        // SEARCH box focus (only when panel is open and click is inside panel)
        if (a.activeTab() == XtremeTaskerOverlay.MainTab.TASKS && button == MouseEvent.BUTTON1) {
            if (containsControl(a.controlsLayout().searchBox, p)) {
                Point searchPoint = a.toPanelLogicalPoint(p);
                a.taskQuery().searchFocused = true;
                a.client().getCanvas().requestFocusInWindow();

                int clickCount = e.getClickCount();
                String text = a.taskQuery().searchText != null ? a.taskQuery().searchText : "";
                if (clickCount >= 3) {
                    // Triple-click: select all
                    a.taskQuery().searchSelStart = 0;
                    a.taskQuery().searchSelEnd = text.length();
                } else if (clickCount == 2) {
                    // Double-click: select word at click position
                    int charIdx = charIndexAt(searchPoint.x, a.controlsLayout().searchTextX, a.controlsLayout().searchCharXPositions, text);
                    int wordStart = charIdx;
                    while (wordStart > 0 && !Character.isWhitespace(text.charAt(wordStart - 1))) wordStart--;
                    int wordEnd = charIdx;
                    while (wordEnd < text.length() && !Character.isWhitespace(text.charAt(wordEnd))) wordEnd++;
                    a.taskQuery().searchSelStart = wordStart;
                    a.taskQuery().searchSelEnd = wordEnd;
                } else {
                    // Single click: clear selection
                    a.taskQuery().searchSelStart = -1;
                    a.taskQuery().searchSelEnd = -1;
                }

                e.consume();
                return e;
            } else {
                // Clicking elsewhere inside panel (but not search) unfocuses search.
                a.taskQuery().searchFocused = false;
                a.taskQuery().searchSelStart = -1;
                a.taskQuery().searchSelEnd = -1;
            }
        }

        // drag panel
        if (button == MouseEvent.BUTTON1 && a.panelDragBarBounds().contains(p)) {
            a.setDraggingPanel(true);
            a.setDragOffset(e.getX() - a.panelBounds().x, e.getY() - a.panelBounds().y);
            e.consume();
            return e;
        }

        // main tab switch
        if (button == MouseEvent.BUTTON1) {
            if (a.currentTabBounds().contains(p)) {
                a.setActiveTab(XtremeTaskerOverlay.MainTab.CURRENT);
                e.consume();
                return e;
            }
            if (a.tasksTabBounds().contains(p)) {
                a.setActiveTab(XtremeTaskerOverlay.MainTab.TASKS);
                e.consume();
                return e;
            }
            if (a.rulesTabBounds().contains(p)) {
                a.setActiveTab(XtremeTaskerOverlay.MainTab.RULES);
                e.consume();
                return e;
            }

            // TASKS tab clicks
            if (a.activeTab() == XtremeTaskerOverlay.MainTab.TASKS) {
                boolean changed = false;

                // ----------------------------
                // 0) Filters / Sort clear links
                // ----------------------------
                if (a.controlsLayout().clearFilters.width > 0
                        && containsControl(a.controlsLayout().clearFilters, p)) {
                    TaskListQuery q = a.taskQuery();
                    q.selectAllSources();
                    q.statusFilter = TaskListQuery.StatusFilter.ALL;
                    q.tierScope = TaskListQuery.TierScope.ALL_TIERS;
                    a.resetTaskListViewAfterQueryChange();
                    e.consume();
                    return e;
                }
                // ----------------------------
                // 1) SOURCE filter (multi-select; all selected collapses to All)
                // ----------------------------
                if (containsControl(a.controlsLayout().filterSourceAll, p)) {
                    changed = setSourceFilter(TaskListQuery.SourceFilter.ALL);
                } else if (containsControl(a.controlsLayout().filterCA, p)) {
                    changed = toggleSourceFilter(TaskListQuery.SourceFilter.CA);
                } else if (containsControl(a.controlsLayout().filterCL, p)) {
                    changed = toggleSourceFilter(TaskListQuery.SourceFilter.CLOGS);
                } else if (containsControl(a.controlsLayout().filterDA, p)) {
                    changed = toggleSourceFilter(TaskListQuery.SourceFilter.DAS);
                }

                // ----------------------------
                // 2) STATUS filter (single-select)
                // ----------------------------
                else if (containsControl(a.controlsLayout().filterStatusAll, p)) {
                    changed = setStatusFilter(TaskListQuery.StatusFilter.ALL);
                } else if (containsControl(a.controlsLayout().filterIncomplete, p)) {
                    changed = toggleSingleSelectStatus(TaskListQuery.StatusFilter.INCOMPLETE);
                } else if (containsControl(a.controlsLayout().filterComplete, p)) {
                    changed = toggleSingleSelectStatus(TaskListQuery.StatusFilter.COMPLETE);
                }

                // ----------------------------
                // 3) TIER scope (single-select)
                // ----------------------------
                else if (containsControl(a.controlsLayout().filterTierThis, p)) {
                    changed = setTierScope(TaskListQuery.TierScope.THIS_TIER);
                } else if (containsControl(a.controlsLayout().filterTierAll, p)) {
                    changed = setTierScope(TaskListQuery.TierScope.ALL_TIERS);
                }
                // ----------------------------
                // 5) See New Tasks toggle (session-only, only visible when hasNewTasks)
                // ----------------------------
                else if (a.controlsLayout().filterNewTasks.width > 0 && containsControl(a.controlsLayout().filterNewTasks, p)) {
                    a.taskQuery().showNewTasksFilter = !a.taskQuery().showNewTasksFilter;
                    if (a.taskQuery().showNewTasksFilter) {
                        // Auto-expand to show new tasks across all tiers/sources/statuses
                        a.taskQuery().tierScope = TaskListQuery.TierScope.ALL_TIERS;
                        a.taskQuery().selectAllSources();
                        a.taskQuery().statusFilter = TaskListQuery.StatusFilter.ALL;
                    }
                    changed = true;
                }
                else if (containsControl(a.controlsLayout().displayDateCompleted, p)) {
                    a.taskQuery().showDateCompletedColumn = !a.taskQuery().showDateCompletedColumn;
                    clearHiddenSort(TaskListQuery.SortColumn.DATE);
                    changed = true;
                }
                else if (containsControl(a.controlsLayout().displayTimeSpent, p)) {
                    a.taskQuery().showTimeSpentColumn = !a.taskQuery().showTimeSpentColumn;
                    clearHiddenSort(TaskListQuery.SortColumn.SPENT);
                    changed = true;
                }
                else if (containsControl(a.controlsLayout().sortDateCompleted, p)) {
                    changed = a.taskQuery().toggleSort(TaskListQuery.SortColumn.DATE);
                }
                else if (containsControl(a.controlsLayout().sortTimeSpent, p)) {
                    changed = a.taskQuery().toggleSort(TaskListQuery.SortColumn.SPENT);
                }

                if (changed) {
                    a.resetTaskListViewAfterQueryChange();
                    e.consume();
                    return e;
                }

                // ----------------------------
                // 5) Tier tabs
                // ----------------------------
                TaskTier clickedTier = null;
                synchronized (a.tierTabBounds()) {
                    for (Map.Entry<TaskTier, Rectangle> entry : a.tierTabBounds().entrySet()) {
                        if (entry.getValue() != null && entry.getValue().contains(p)) {
                            clickedTier = entry.getKey();
                            break;
                        }
                    }
                }

                if (clickedTier != null) {
                    a.setActiveTier(clickedTier);
                    a.resetTaskListViewAfterQueryChange();
                    e.consume();
                    return e;
                }

                if (!a.isTaskDetailsOpen() && a.taskScrollbarRailBounds().width > 0) {
                    Rectangle thumb = a.taskScrollbarThumbBounds();
                    Rectangle rail = a.taskScrollbarRailBounds();

                    if (thumb.contains(p)) {
                        draggingTaskScrollbar = true;
                        taskScrollbarGrabOffsetY = p.y - thumb.y;
                        e.consume();
                        return e;
                    }

                    if (rail.contains(p)) {
                        draggingTaskScrollbar = true;
                        taskScrollbarGrabOffsetY = Math.max(0, thumb.height / 2);
                        updateTaskScrollbarDrag(e.getY());
                        e.consume();
                        return e;
                    }
                }
            }
        }

        // CURRENT tab clicks
        if (a.activeTab() == XtremeTaskerOverlay.MainTab.CURRENT && button == MouseEvent.BUTTON1) {
            XtremeTask current = a.plugin().getCurrentTask();

            if (a.currentLayout().scrollbarRailBounds.width > 0) {
                Rectangle thumb = a.currentLayout().scrollbarThumbBounds;
                Rectangle rail = a.currentLayout().scrollbarRailBounds;

                if (thumb.contains(p)) {
                    draggingCurrentScrollbar = true;
                    currentScrollbarGrabOffsetY = p.y - thumb.y;
                    e.consume();
                    return e;
                }

                if (rail.contains(p)) {
                    draggingCurrentScrollbar = true;
                    currentScrollbarGrabOffsetY = Math.max(0, thumb.height / 2);
                    updateCurrentScrollbarDrag(e.getY());
                    e.consume();
                    return e;
                }
            }

            if (current != null && a.currentLayout().wikiButtonBounds.contains(p)) {
                String url = current.getWikiUrl();
                if (url != null && !url.trim().isEmpty()) {
                    LinkBrowser.browse(url);
                    e.consume();
                    return e;
                }
            }

            boolean currentCompleted = false;
            boolean rollEnabled = (current == null) || currentCompleted;
            boolean completeEnabled = (current != null) && !currentCompleted;
            boolean canUndoRecentCompletion = a.plugin().canUndoRecentTaskCompletion();

            if (completeEnabled && a.currentLayout().completeButtonBounds.contains(p)) {
                if (current != null) {
                    a.animations().startCompletionAnim(current.getId());
                }

                a.plugin().completeCurrentTaskAndPersist();
                e.consume();
                return e;
            }

            if (canUndoRecentCompletion && a.currentLayout().undoButtonBounds.contains(p)) {
                a.plugin().undoCurrentTaskCompletionAndPersist();
                e.consume();
                return e;
            }

            if (rollEnabled && a.currentLayout().rollButtonBounds.contains(p)) {
                a.requestRollTask();
                e.consume();
                return e;
            }
        }

        // ------------------------------------------------------------
        // TASKS list row click behavior (checkbox toggles, row opens)
        // ------------------------------------------------------------
        if (tryHandleTaskRowClick(e, p, button)) {
            return e;
        }

        // RULES button click
        if (a.activeTab() == XtremeTaskerOverlay.MainTab.RULES && button == MouseEvent.BUTTON1) {
            // Sub-tab toggles
            if (a.rulesLayout().subTabRulesBounds.contains(p)) {
                a.setRulesSubTab(RulesTabLayout.SubTab.RULES);
                e.consume();
                return e;
            }
            if (a.rulesLayout().subTabDataSyncsBounds.contains(p)) {
                a.setRulesSubTab(RulesTabLayout.SubTab.DATA_SYNCS);
                e.consume();
                return e;
            }
            if (a.rulesLayout().syncProgressButtonBounds.contains(p)) {
                a.syncMismatchScroll().reset();
                a.plugin().syncAccountProgressAndPersist();
                e.consume();
                return e;
            }
            if (a.rulesLayout().syncCaFoundReviewButtonBounds.contains(p)) {
                a.openSyncCompletionCandidateReview(null);
                syncMismatchReviewOpenedAt = e.getWhen();
                rememberSyncMismatchClick(e, p, button);
                e.consume();
                return e;
            }
            if (a.rulesLayout().githubReadmeLinkBounds.contains(p)) {
                LinkBrowser.browse(RulesTabRenderer.githubReadmeUrl());
                e.consume();
                return e;
            }
        }

        if (a.panelBounds().contains(p)) {
            e.consume();
        }

        return e;
    }

    @Override
    public MouseEvent mouseClicked(MouseEvent e) {
        if (!a.plugin().isOverlayEnabled() || !a.isPanelOpen()) {
            return e;
        }

        Point p = e.getPoint();
        int button = e.getButton();
        if (tryHandleTaskDetailsIncompleteConfirmClick(e, p, button)) {
            return e;
        }

        if (tryHandleSyncMismatchClick(e, p, button)) {
            return e;
        }

        if (a.isMarkAllIncompleteConfirmOpen() || a.isTaskDetailsOpen()) {
            return e;
        }

        tryHandleTaskRowClick(e, p, button);
        return e;
    }

    private boolean handCursorActive = false;

    private boolean tryHandleTaskDetailsIncompleteConfirmClick(MouseEvent e, Point p, int button)
    {
        if (!a.isTaskDetailsIncompleteConfirmOpen() || button != MouseEvent.BUTTON1)
        {
            return false;
        }

        if (isSuppressedTaskDetailsIncompleteConfirmClicked(e, p, button))
        {
            e.consume();
            return true;
        }

        if (a.taskDetailsIncompleteConfirmYesBounds().contains(p))
        {
            a.confirmTaskDetailsIncompleteSelection();
            e.consume();
            return true;
        }

        if (a.taskDetailsIncompleteConfirmNoBounds().contains(p))
        {
            a.closeTaskDetailsIncompleteConfirm();
            e.consume();
            return true;
        }

        if (a.taskDetailsIncompleteConfirmBounds().contains(p))
        {
            e.consume();
            return true;
        }

        a.closeTaskDetailsIncompleteConfirm();
        e.consume();
        return true;
    }

    private boolean isSuppressedTaskDetailsIncompleteConfirmClicked(MouseEvent e, Point p, int button)
    {
        if (!suppressNextTaskDetailsIncompleteConfirmClicked
                || (e.getID() != MouseEvent.MOUSE_RELEASED && e.getID() != MouseEvent.MOUSE_CLICKED))
        {
            return false;
        }

        int dx = Math.abs(p.x - suppressTaskDetailsIncompleteConfirmClickX);
        int dy = Math.abs(p.y - suppressTaskDetailsIncompleteConfirmClickY);
        boolean samePhysicalClick = button == suppressTaskDetailsIncompleteConfirmClickButton
                && dx <= SYNC_MISMATCH_DUPLICATE_CLICK_TOLERANCE_PX
                && dy <= SYNC_MISMATCH_DUPLICATE_CLICK_TOLERANCE_PX;
        if (samePhysicalClick)
        {
            suppressNextTaskDetailsIncompleteConfirmClicked = e.getID() != MouseEvent.MOUSE_CLICKED;
        }
        return samePhysicalClick;
    }

    private void rememberTaskDetailsIncompleteConfirmOpeningClick(MouseEvent e, Point p, int button)
    {
        if (e.getID() != MouseEvent.MOUSE_PRESSED)
        {
            return;
        }

        suppressNextTaskDetailsIncompleteConfirmClicked = true;
        suppressTaskDetailsIncompleteConfirmClickX = p.x;
        suppressTaskDetailsIncompleteConfirmClickY = p.y;
        suppressTaskDetailsIncompleteConfirmClickButton = button;
    }

    private boolean tryHandleSyncMismatchClick(MouseEvent e, Point p, int button)
    {
        if (!a.isSyncMismatchReviewOpen() || button != MouseEvent.BUTTON1)
        {
            return false;
        }

        if (isSuppressedSyncMismatchClicked(e, p, button))
        {
            e.consume();
            return true;
        }

        if (a.syncMismatchReviewBounds().width <= 0 || a.syncMismatchReviewBounds().height <= 0)
        {
            if (isSyncMismatchReviewOpening(e.getWhen()))
            {
                rememberSyncMismatchClick(e, p, button);
                e.consume();
                return true;
            }

            a.closeSyncMismatchReview();
            syncMismatchReviewOpenedAt = Long.MIN_VALUE;
            return false;
        }

        if (a.isSyncMismatchApplyConfirmOpen())
        {
            if (a.syncMismatchConfirmYesBounds().contains(p))
            {
                if (a.isSyncCompletionCandidateReviewOpen())
                {
                    a.plugin().markSyncCompletionCandidateTasksCompleteAndPersist(a.selectedSyncMismatchTasks());
                }
                else
                {
                    a.plugin().markSyncMismatchTasksIncompleteAndPersist(a.selectedSyncMismatchTasks());
                }
                a.clearSyncMismatchSelection();
                a.closeSyncMismatchApplyConfirm();
                rememberSyncMismatchClick(e, p, button);
                e.consume();
                return true;
            }

            if (a.syncMismatchConfirmNoBounds().contains(p))
            {
                a.closeSyncMismatchApplyConfirm();
                rememberSyncMismatchClick(e, p, button);
                e.consume();
                return true;
            }

            if (a.syncMismatchConfirmBounds().contains(p) || a.syncMismatchReviewBounds().contains(p))
            {
                rememberSyncMismatchClick(e, p, button);
                e.consume();
                return true;
            }
        }

        if (a.syncMismatchCloseBounds().contains(p))
        {
            a.closeSyncMismatchReview();
            rememberSyncMismatchClick(e, p, button);
            e.consume();
            return true;
        }

        if (a.syncMismatchMarkAllBounds().contains(p))
        {
            int selectedCount = a.syncMismatchSelectedCount();
            int selectableCount = a.syncMismatchSelectableCount();
            if (selectableCount > 0 && selectedCount >= selectableCount)
            {
                a.clearSyncMismatchSelection();
            }
            else
            {
                a.selectAllSyncMismatchTasks();
            }
            rememberSyncMismatchClick(e, p, button);
            e.consume();
            return true;
        }

        if (a.syncMismatchApplyBounds().contains(p))
        {
            a.requestSyncMismatchApplyConfirm();
            rememberSyncMismatchClick(e, p, button);
            e.consume();
            return true;
        }

        if (a.syncMismatchCancelBounds().contains(p))
        {
            a.closeSyncMismatchReview();
            rememberSyncMismatchClick(e, p, button);
            e.consume();
            return true;
        }

        if (a.syncMismatchScrollbarRailBounds().width > 0)
        {
            Rectangle thumb = a.syncMismatchScrollbarThumbBounds();
            Rectangle rail = a.syncMismatchScrollbarRailBounds();

            if (thumb.contains(p))
            {
                draggingSyncMismatchScrollbar = true;
                syncMismatchScrollbarGrabOffsetY = p.y - thumb.y;
                rememberSyncMismatchClick(e, p, button);
                e.consume();
                return true;
            }

            if (rail.contains(p))
            {
                draggingSyncMismatchScrollbar = true;
                syncMismatchScrollbarGrabOffsetY = Math.max(0, thumb.height / 2);
                updateSyncMismatchScrollbarDrag(e.getY());
                rememberSyncMismatchClick(e, p, button);
                e.consume();
                return true;
            }
        }

        XtremeTask actionTask = syncMismatchTaskAt(p);
        if (actionTask != null)
        {
            a.toggleSyncMismatchTaskSelected(actionTask);
            rememberSyncMismatchClick(e, p, button);
            e.consume();
            return true;
        }

        if (a.syncMismatchReviewBounds().contains(p))
        {
            rememberSyncMismatchClick(e, p, button);
            e.consume();
            return true;
        }

        a.closeSyncMismatchReview();
        rememberSyncMismatchClick(e, p, button);
        e.consume();
        return true;
    }

    private boolean isSyncMismatchLayerActive()
    {
        return a.isPanelOpen() && a.isSyncMismatchReviewOpen();
    }

    private boolean isSyncMismatchReviewOpening(long eventTime)
    {
        return syncMismatchReviewOpenedAt != Long.MIN_VALUE
                && eventTime >= syncMismatchReviewOpenedAt
                && eventTime - syncMismatchReviewOpenedAt <= SYNC_MISMATCH_OPENING_GRACE_MS;
    }

    private boolean isSuppressedSyncMismatchClicked(MouseEvent e, Point p, int button)
    {
        if (e.getID() != MouseEvent.MOUSE_CLICKED || !suppressNextSyncMismatchClicked)
        {
            return false;
        }

        int dx = Math.abs(p.x - suppressSyncMismatchClickX);
        int dy = Math.abs(p.y - suppressSyncMismatchClickY);
        boolean samePhysicalClick = button == suppressSyncMismatchClickButton
                && dx <= SYNC_MISMATCH_DUPLICATE_CLICK_TOLERANCE_PX
                && dy <= SYNC_MISMATCH_DUPLICATE_CLICK_TOLERANCE_PX;
        if (samePhysicalClick)
        {
            suppressNextSyncMismatchClicked = false;
        }
        return samePhysicalClick;
    }

    private void rememberSyncMismatchClick(MouseEvent e, Point p, int button)
    {
        if (e.getID() == MouseEvent.MOUSE_PRESSED)
        {
            suppressNextSyncMismatchClicked = true;
            suppressSyncMismatchClickX = p.x;
            suppressSyncMismatchClickY = p.y;
            suppressSyncMismatchClickButton = button;
        }
        else if (e.getID() == MouseEvent.MOUSE_CLICKED)
        {
            suppressNextSyncMismatchClicked = false;
        }
    }

    private XtremeTask syncMismatchTaskAt(Point p)
    {
        Map<XtremeTask, Rectangle> bounds = a.syncMismatchTaskBounds();
        synchronized (bounds)
        {
            for (Map.Entry<XtremeTask, Rectangle> entry : bounds.entrySet())
            {
                if (entry.getValue() != null && entry.getValue().contains(p))
                {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private boolean isSyncMismatchInteractivePoint(Point p)
    {
        return a.syncMismatchCloseBounds().contains(p)
                || a.syncMismatchMarkAllBounds().contains(p)
                || (a.syncMismatchSelectedCount() > 0 && a.syncMismatchApplyBounds().contains(p))
                || a.syncMismatchCancelBounds().contains(p)
                || a.syncMismatchScrollbarThumbBounds().contains(p)
                || a.syncMismatchScrollbarRailBounds().contains(p)
                || syncMismatchTaskAt(p) != null
                || (a.isSyncMismatchApplyConfirmOpen() && (
                        a.syncMismatchConfirmYesBounds().contains(p)
                                || a.syncMismatchConfirmNoBounds().contains(p)
                ));
    }

    private boolean tryHandleTaskRowClick(MouseEvent e, Point p, int button)
    {
        if (a.activeTab() != XtremeTaskerOverlay.MainTab.TASKS
                || a.isTaskDetailsOpen()
                || (button != MouseEvent.BUTTON1 && button != MouseEvent.BUTTON3))
        {
            return false;
        }

        if (isDuplicateTaskRowClick(e, p, button))
        {
            e.consume();
            return true;
        }

        XtremeTask clickedTask = null;
        synchronized (a.taskRowBounds()) {
            for (Map.Entry<XtremeTask, Rectangle> entry : a.taskRowBounds().entrySet())
            {
                if (entry.getValue() != null && entry.getValue().contains(p))
                {
                    clickedTask = entry.getKey();
                    break;
                }
            }
        }

        if (clickedTask == null)
        {
            return false;
        }

        XtremeTask task = clickedTask;
        List<XtremeTask> tasksBefore = a.getSortedTasksForTier(a.activeTier());
        a.selectionModel().setSelectionToTask(a.activeTier(), tasksBefore, task);

        a.openTaskDetails(task);
        rememberTaskRowClick(e, p, button);
        e.consume();
        return true;
    }

    private boolean isDuplicateTaskRowClick(MouseEvent e, Point p, int button)
    {
        long when = e.getWhen();
        return button == lastTaskRowClickButton
                && p.x == lastTaskRowClickX
                && p.y == lastTaskRowClickY
                && when >= lastTaskRowClickHandledAt
                && when - lastTaskRowClickHandledAt < 140L;
    }

    private void rememberTaskRowClick(MouseEvent e, Point p, int button)
    {
        lastTaskRowClickHandledAt = e.getWhen();
        lastTaskRowClickX = p.x;
        lastTaskRowClickY = p.y;
        lastTaskRowClickButton = button;
    }

    @Override
    public MouseEvent mouseMoved(MouseEvent e)
    {
        if (!a.plugin().isOverlayEnabled())
        {
            return e;
        }

        Point p = e.getPoint();
        if (!a.isPanelOpen())
        {
            if (a.isTaskDetailsWikiMenuOpen()) {
                a.closeTaskDetailsWikiMenu();
            }
            updateHandCursor(a.iconBounds().contains(p));
            return e;
        }

        if (a.isSyncMismatchReviewOpen())
        {
            if (a.syncMismatchReviewBounds().width <= 0 || a.syncMismatchReviewBounds().height <= 0)
            {
                if (!isSyncMismatchReviewOpening(e.getWhen()))
                {
                    a.closeSyncMismatchReview();
                    syncMismatchReviewOpenedAt = Long.MIN_VALUE;
                }
                updateHandCursor(false);
                return e;
            }
            else
            {
                updateHandCursor(isSyncMismatchInteractivePoint(p));
                return e;
            }
        }

        if (a.isTaskDetailsIncompleteConfirmOpen())
        {
            updateHandCursor(
                    a.taskDetailsIncompleteConfirmYesBounds().contains(p)
                            || a.taskDetailsIncompleteConfirmNoBounds().contains(p)
            );
            return e;
        }

        if (a.isMarkAllIncompleteConfirmOpen())
        {
            updateHandCursor(
                    a.markAllIncompleteYesBounds().contains(p)
                            || a.markAllIncompleteNoBounds().contains(p)
                            || a.markIncompleteDontShowBounds().contains(p)
            );
            return e;
        }

        // State needed for conditional clickability
        XtremeTask current = a.plugin().getCurrentTask();
        boolean currentCompleted = false;
        boolean rollEnabled = (current == null) || currentCompleted;
        boolean completeEnabled = (current != null) && !currentCompleted;
        boolean canUndoRecentCompletion = a.plugin().canUndoRecentTaskCompletion();

        TaskControlsLayout cl = a.controlsLayout();

        boolean hovering =
                // UI chrome
                a.panelCloseBounds().contains(p)
                || a.currentTabBounds().contains(p)
                || a.tasksTabBounds().contains(p)
                || a.rulesTabBounds().contains(p)
                || a.iconBounds().contains(p)
                // task details popup
                || (a.isTaskDetailsOpen() && (
                        a.taskDetailsCloseBounds().contains(p)
                        || a.taskDetailsWikiBounds().contains(p)
                        || (a.isTaskDetailsWikiMenuOpen() && a.taskDetailsWikiMenuBounds().contains(p))
                        || a.taskDetailsMarkIncompleteBounds().contains(p)
                        || taskDetailsInstanceRemoveContains(p)
                        || a.taskDetailsScrollbarThumbBounds().contains(p)
                        || a.taskDetailsScrollbarRailBounds().contains(p)
                ))
                // RULES tab
                || (a.activeTab() == XtremeTaskerOverlay.MainTab.RULES && (
                        a.rulesLayout().subTabRulesBounds.contains(p)
                        || a.rulesLayout().subTabDataSyncsBounds.contains(p)
                        || a.rulesLayout().githubReadmeLinkBounds.contains(p)
                        || a.rulesLayout().syncProgressButtonBounds.contains(p)
                        || a.rulesLayout().syncCaFoundReviewButtonBounds.contains(p)
                ))
                // CURRENT tab
                || (a.activeTab() == XtremeTaskerOverlay.MainTab.CURRENT && (
                        a.currentLayout().wikiButtonBounds.contains(p)
                        || (rollEnabled && a.currentLayout().rollButtonBounds.contains(p))
                        || (completeEnabled && a.currentLayout().completeButtonBounds.contains(p))
                        || (canUndoRecentCompletion && a.currentLayout().undoButtonBounds.contains(p))
                        || a.currentLayout().scrollbarThumbBounds.contains(p)
                        || a.currentLayout().scrollbarRailBounds.contains(p)
                ))
                // TASKS tab
                || (a.activeTab() == XtremeTaskerOverlay.MainTab.TASKS && (
                        // tier tabs
                        containsAny(a.tierTabBounds(), p)
                        || containsControl(cl.searchBox, p)
                        || (cl.clearFilters.width > 0 && containsControl(cl.clearFilters, p))
                        // filter pills (always clickable)
                        || containsControl(cl.filterSourceAll, p)
                        || containsControl(cl.filterCA, p)
                        || containsControl(cl.filterCL, p)
                        || containsControl(cl.filterDA, p)
                        || containsControl(cl.filterStatusAll, p)
                        || containsControl(cl.filterIncomplete, p)
                        || containsControl(cl.filterComplete, p)
                        || containsControl(cl.filterTierThis, p)
                        || containsControl(cl.filterTierAll, p)
                        // new tasks button
                        || containsControl(cl.filterNewTasks, p)
                        || containsControl(cl.filterNewTasksHelp, p)
                        // column display toggles
                        || containsControl(cl.displayDateCompleted, p)
                        || containsControl(cl.displayTimeSpent, p)
                        // task list sortable columns
                        || containsControl(cl.sortDateCompleted, p)
                        || containsControl(cl.sortTimeSpent, p)
                        // task list scrollbar
                        || (!a.isTaskDetailsOpen() && (
                                a.taskScrollbarThumbBounds().contains(p)
                                || a.taskScrollbarRailBounds().contains(p)
                        ))
                        // task rows in scroll list
                        || (!a.isTaskDetailsOpen() && containsAny(a.taskRowBounds(), p))
                ));


        updateHandCursor(hovering);

        return e;
    }

    private void updateHandCursor(boolean hovering)
    {
        if (hovering && !handCursorActive)
        {
            a.client().getCanvas().setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            handCursorActive = true;
        }
        else if (!hovering && handCursorActive)
        {
            a.client().getCanvas().setCursor(Cursor.getDefaultCursor());
            handCursorActive = false;
        }
    }

    private boolean taskDetailsInstanceRemoveContains(Point p)
    {
        return containsAny(a.taskDetailsInstanceRemoveBounds(), p);
    }

    private boolean containsControl(Rectangle bounds, Point p) {
        if (bounds == null || p == null) {
            return false;
        }
        Point logical = a.toPanelLogicalPoint(p);
        return bounds.contains(logical);
    }

    private static boolean containsAny(Map<?, Rectangle> bounds, Point p) {
        synchronized (bounds) {
            for (Rectangle r : bounds.values()) {
                if (r != null && r.contains(p)) {
                    return true;
                }
            }
        }

        return false;
    }



    @Override
    public MouseEvent mouseDragged(MouseEvent e) {
        if (!a.plugin().isOverlayEnabled()) {
            return e;
        }

        // Icon drag
        if (pressedOnIcon || a.isDraggingIcon()) {
            int dx = e.getX() - iconPressX;
            int dy = e.getY() - iconPressY;
            if (!a.isDraggingIcon() && (dx * dx + dy * dy) < ICON_DRAG_THRESHOLD_SQ) {
                return e; // below threshold — not a drag yet
            }
            a.setDraggingIcon(true);

            int canvasW = a.client().getCanvasWidth();
            int canvasH = a.client().getCanvasHeight();
            int newX = e.getX() - a.iconDragOffsetX();
            int newY = e.getY() - a.iconDragOffsetY();
            newX = Math.max(0, Math.min(newX, iconDragMaxX(canvasW, a.iconBounds().width)));
            newY = Math.max(0, Math.min(newY, canvasH - a.iconBounds().height));
            a.setIconOverride(newX, newY);
            e.consume();
            return e;
        }

        if (draggingTaskScrollbar) {
            updateTaskScrollbarDrag(e.getY());
            e.consume();
            return e;
        }

        if (draggingCurrentScrollbar) {
            updateCurrentScrollbarDrag(e.getY());
            e.consume();
            return e;
        }

        if (draggingTaskDetailsScrollbar) {
            updateTaskDetailsScrollbarDrag(e.getY());
            e.consume();
            return e;
        }

        if (draggingSyncMismatchScrollbar) {
            updateSyncMismatchScrollbarDrag(e.getY());
            e.consume();
            return e;
        }

        // Panel drag
        if (!a.isPanelOpen() || !a.isDraggingPanel()) {
            return e;
        }

        int canvasW = a.client().getCanvasWidth();
        int canvasH = a.client().getCanvasHeight();

        int newX = e.getX() - a.dragOffsetX();
        int newY = e.getY() - a.dragOffsetY();

        newX = Math.max(0, Math.min(newX, canvasW - a.panelBounds().width));
        newY = Math.max(0, Math.min(newY, canvasH - a.panelBounds().height));

        a.setPanelOverride(newX, newY);

        e.consume();
        return e;
    }

    private int iconDragMaxX(int canvasWidth, int iconWidth) {
        int maxX = canvasWidth - iconWidth;
        if (!a.client().isResized()) {
            Widget xpOrb = a.client().getWidget(ComponentID.MINIMAP_XP_ORB);
            if (xpOrb != null) {
                Rectangle b = xpOrb.getBounds();
                maxX = Math.min(maxX, b.x - ICON_ANCHOR_PAD - iconWidth);
            }
        }
        return Math.max(0, maxX);
    }

    @Override
    public MouseEvent mouseReleased(MouseEvent e) {
        if (a.isDraggingPanel()) {
            a.setDraggingPanel(false);
            a.persistPanelPosition();
            e.consume();
        }
        if (draggingTaskScrollbar) {
            draggingTaskScrollbar = false;
            e.consume();
        }
        if (draggingCurrentScrollbar) {
            draggingCurrentScrollbar = false;
            e.consume();
        }
        if (draggingTaskDetailsScrollbar) {
            draggingTaskDetailsScrollbar = false;
            e.consume();
        }
        if (draggingSyncMismatchScrollbar) {
            draggingSyncMismatchScrollbar = false;
            e.consume();
        }
        if (pressedOnIcon) {
            if (a.isDraggingIcon()) {
                // Drag ended — save position.
                a.persistIconPosition();
                a.setDraggingIcon(false);
            } else {
                // It was a click — toggle panel.
                boolean next = !a.isPanelOpen();
                a.setPanelOpen(next);
                if (next) {
                    onOpenPanel.run();
                }
            }
            pressedOnIcon = false;
            e.consume();
        }
        return e;
    }

    @Override
    public MouseEvent mouseExited(MouseEvent e) {
        updateHandCursor(false);
        return e;
    }

    private void updateTaskScrollbarDrag(int mouseY) {
        Rectangle rail = a.taskScrollbarRailBounds();
        Rectangle thumb = a.taskScrollbarThumbBounds();
        if (rail.height <= 0 || thumb.height <= 0) {
            return;
        }

        List<XtremeTask> tasks = a.getSortedTasksForTier(a.activeTier());
        int totalRows = tasks.size();
        int rowBlock = a.taskRowBlock();
        int viewportH = a.taskListViewportBounds().height;
        int visible = a.tasksScroll().visibleRows(viewportH, rowBlock);
        int maxOffset = Math.max(0, totalRows - visible);
        int trackH = Math.max(0, rail.height - thumb.height);
        if (totalRows <= 0 || visible <= 0 || maxOffset <= 0 || trackH <= 0) {
            a.tasksScroll().setOffsetRows(0, viewportH, rowBlock, totalRows);
            return;
        }

        int thumbY = Math.max(rail.y, Math.min(mouseY - taskScrollbarGrabOffsetY, rail.y + trackH));
        double frac = (double) (thumbY - rail.y) / (double) trackH;
        int nextOffset = (int) Math.round(frac * maxOffset);
        a.tasksScroll().setOffsetRows(nextOffset, viewportH, rowBlock, totalRows);
    }

    private void updateTaskDetailsScrollbarDrag(int mouseY) {
        Rectangle rail = a.taskDetailsScrollbarRailBounds();
        Rectangle thumb = a.taskDetailsScrollbarThumbBounds();
        if (rail.height <= 0 || thumb.height <= 0) {
            return;
        }

        int totalRows = a.taskDetailsTotalContentRows();
        int rowBlock = a.taskDetailsRowBlock();
        int viewportH = a.taskDetailsViewportBounds().height;
        int visible = a.taskDetailsScroll().visibleRows(viewportH, rowBlock);
        int maxOffset = Math.max(0, totalRows - visible);
        int trackH = Math.max(0, rail.height - thumb.height);
        if (totalRows <= 0 || visible <= 0 || maxOffset <= 0 || trackH <= 0) {
            a.taskDetailsScroll().setOffsetRows(0, viewportH, rowBlock, totalRows);
            return;
        }

        int thumbY = Math.max(rail.y, Math.min(mouseY - taskDetailsScrollbarGrabOffsetY, rail.y + trackH));
        double frac = (double) (thumbY - rail.y) / (double) trackH;
        int nextOffset = (int) Math.round(frac * maxOffset);
        a.taskDetailsScroll().setOffsetRows(nextOffset, viewportH, rowBlock, totalRows);
    }

    private void updateCurrentScrollbarDrag(int mouseY) {
        Rectangle rail = a.currentLayout().scrollbarRailBounds;
        Rectangle thumb = a.currentLayout().scrollbarThumbBounds;
        if (rail.height <= 0 || thumb.height <= 0) {
            return;
        }

        int totalRows = (a.currentLayout().totalContentPx + com.amtrollin.xtremetasker.ui.style.UiConstants.ROW_HEIGHT - 1)
                / com.amtrollin.xtremetasker.ui.style.UiConstants.ROW_HEIGHT;
        int rowBlock = a.currentRowBlock();
        int viewportH = a.currentViewportBounds().height;
        int visible = a.currentScroll().visibleRows(viewportH, rowBlock);
        int maxOffset = Math.max(0, totalRows - visible);
        int trackH = Math.max(0, rail.height - thumb.height);
        if (totalRows <= 0 || visible <= 0 || maxOffset <= 0 || trackH <= 0) {
            a.currentScroll().setOffsetRows(0, viewportH, rowBlock, totalRows);
            return;
        }

        int thumbY = Math.max(rail.y, Math.min(mouseY - currentScrollbarGrabOffsetY, rail.y + trackH));
        double frac = (double) (thumbY - rail.y) / (double) trackH;
        int nextOffset = (int) Math.round(frac * maxOffset);
        a.currentScroll().setOffsetRows(nextOffset, viewportH, rowBlock, totalRows);
        a.client().getCanvas().repaint();
    }

    private void updateSyncMismatchScrollbarDrag(int mouseY) {
        Rectangle rail = a.syncMismatchScrollbarRailBounds();
        Rectangle thumb = a.syncMismatchScrollbarThumbBounds();
        if (rail.height <= 0 || thumb.height <= 0) {
            return;
        }

        int totalRows = a.syncMismatchVisibleTaskCount();
        int rowBlock = a.syncMismatchRowBlock();
        int viewportH = a.syncMismatchViewportBounds().height;
        int visible = a.syncMismatchScroll().visibleRows(viewportH, rowBlock);
        int maxOffset = Math.max(0, totalRows - visible);
        int trackH = Math.max(0, rail.height - thumb.height);
        if (totalRows <= 0 || visible <= 0 || maxOffset <= 0 || trackH <= 0) {
            a.syncMismatchScroll().setOffsetRows(0, viewportH, rowBlock, totalRows);
            return;
        }

        int thumbY = Math.max(rail.y, Math.min(mouseY - syncMismatchScrollbarGrabOffsetY, rail.y + trackH));
        double frac = (double) (thumbY - rail.y) / (double) trackH;
        int nextOffset = (int) Math.round(frac * maxOffset);
        a.syncMismatchScroll().setOffsetRows(nextOffset, viewportH, rowBlock, totalRows);
        a.client().getCanvas().repaint();
    }

    // =========================
    // Source filter helpers
    // =========================
    private boolean setSourceFilter(TaskListQuery.SourceFilter next) {
        TaskListQuery q = a.taskQuery();
        boolean beforeCA = q.sourceCASelected;
        boolean beforeClogs = q.sourceClogsSelected;
        boolean beforeDas = q.sourceDasSelected;
        if (next == TaskListQuery.SourceFilter.ALL) {
            q.selectAllSources();
        } else {
            q.setOnlySource(next);
        }
        return beforeCA != q.sourceCASelected || beforeClogs != q.sourceClogsSelected || beforeDas != q.sourceDasSelected;
    }

    private boolean toggleSourceFilter(TaskListQuery.SourceFilter clicked) {
        return a.taskQuery().toggleSource(clicked);
    }

    private boolean setStatusFilter(TaskListQuery.StatusFilter next) {
        TaskListQuery q = a.taskQuery();
        if (q.statusFilter == next) {
            return false;
        }
        q.statusFilter = next;
        return true;
    }

    private boolean toggleSingleSelectStatus(TaskListQuery.StatusFilter clicked) {
        TaskListQuery q = a.taskQuery();
        TaskListQuery.StatusFilter next = (q.statusFilter == clicked)
                ? TaskListQuery.StatusFilter.ALL
                : clicked;

        if (q.statusFilter == next) {
            return false;
        }

        q.statusFilter = next;
        return true;
    }

    private boolean setTierScope(TaskListQuery.TierScope next) {
        TaskListQuery q = a.taskQuery();
        if (q.tierScope == next) return false;
        q.tierScope = next;
        return true;
    }

    private void clearHiddenSort(TaskListQuery.SortColumn column)
    {
        TaskListQuery q = a.taskQuery();
        if (!q.isColumnVisible(column) && q.sortColumn == column)
        {
            q.sortDirection = TaskListQuery.SortDirection.OFF;
        }
    }

    /** Returns the character index in {@code text} closest to pixel {@code clickX}. */
    private static int charIndexAt(int clickX, int textX, int[] charPositions, String text) {
        int relX = clickX - textX;
        if (charPositions == null || charPositions.length == 0) return 0;
        for (int i = 0; i < charPositions.length - 1; i++) {
            int midpoint = (charPositions[i] + charPositions[i + 1]) / 2;
            if (relX <= midpoint) return i;
        }
        return text.length();
    }
}
