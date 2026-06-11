package com.amtrollin.xtremetasker.ui.input;

import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.tasklist.models.TaskListQuery;
import com.amtrollin.xtremetasker.ui.rules.RulesTabLayout;
import com.amtrollin.xtremetasker.ui.rules.RulesTabRenderer;
import com.amtrollin.xtremetasker.ui.tasks.models.TaskControlsLayout;
import lombok.RequiredArgsConstructor;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.util.LinkBrowser;

import java.awt.*;
import java.awt.Cursor;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public final class OverlayMouseHandler extends MouseAdapter {
    private final OverlayInputAccess a;
    private final Runnable onOpenPanel; // resets scroll, clears overrides, etc.

    // Transient icon press state — distinguishes click from drag.
    private boolean pressedOnIcon = false;
    private int iconPressX = 0;
    private int iconPressY = 0;
    private static final int ICON_DRAG_THRESHOLD_SQ = 25; // 5px
    private boolean draggingTaskScrollbar = false;
    private int taskScrollbarGrabOffsetY = 0;
    private boolean draggingRulesScrollbar = false;
    private int rulesScrollbarGrabOffsetY = 0;
    private boolean draggingTaskDetailsScrollbar = false;
    private int taskDetailsScrollbarGrabOffsetY = 0;
    private boolean draggingSyncMismatchScrollbar = false;
    private int syncMismatchScrollbarGrabOffsetY = 0;
    private long lastTaskRowClickHandledAt = 0L;
    private int lastTaskRowClickX = Integer.MIN_VALUE;
    private int lastTaskRowClickY = Integer.MIN_VALUE;
    private int lastTaskRowClickButton = MouseEvent.NOBUTTON;
    private static final int SYNC_MISMATCH_ACTION_COLUMN_W = 112;
    private static final int SYNC_MISMATCH_DUPLICATE_CLICK_TOLERANCE_PX = 6;
    private boolean suppressNextSyncMismatchClicked = false;
    private int suppressSyncMismatchClickX = Integer.MIN_VALUE;
    private int suppressSyncMismatchClickY = Integer.MIN_VALUE;
    private int suppressSyncMismatchClickButton = MouseEvent.NOBUTTON;
    private long syncMismatchReviewOpenedAt = Long.MIN_VALUE;
    private static final long SYNC_MISMATCH_OPENING_GRACE_MS = 250L;

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
            } else {
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
                    e.consume();
                    return e;
                }

                // Wiki button
                if (a.taskDetailsWikiBounds().contains(p)) {
                    XtremeTask t = a.taskDetailsTask();
                    if (t != null) {
                        String url = t.getWikiUrl();
                        if (url != null && !url.trim().isEmpty()) {
                            LinkBrowser.browse(url);
                        }
                    }
                    e.consume();
                    return e;
                }

                // Toggle button in popup
                if (a.taskDetailsToggleBounds().contains(p)) {
                    XtremeTask t = a.taskDetailsTask();
                    if (t != null) {
                        boolean groupedMode = a.useCondensedTaskRows();
                        com.amtrollin.xtremetasker.models.TaskGroupProgress progress = a.plugin().getTaskGroupProgress(t);
                        boolean wasDone = groupedMode ? progress.isComplete() : a.plugin().isTaskCompleted(t);
                        if (!wasDone) {
                            a.animations().startCompletionAnim(t.getId());
                        }

                        if (wasDone) {
                            if (groupedMode && progress.isGrouped()) {
                                a.requestMarkAllIncompleteConfirmation(t, true);
                            } else if (a.plugin().skipSingleIncompleteConfirmation()) {
                                a.plugin().toggleTaskCompletedAndPersist(t);
                            } else {
                                a.requestMarkAllIncompleteConfirmation(t, false);
                            }
                        } else if (groupedMode && progress.isGrouped()) {
                                a.plugin().setTaskGroupCompletedCountAndPersist(t, progress.getTotal());
                        } else {
                            a.plugin().toggleTaskCompletedAndPersist(t);
                        }
                    }
                    e.consume();
                    return e;
                }

                XtremeTask detailTask = a.taskDetailsTask();
                synchronized (a.taskDetailsInstanceRemoveBounds()) {
                    for (Map.Entry<XtremeTask, Rectangle> entry : a.taskDetailsInstanceRemoveBounds().entrySet()) {
                        Rectangle bounds = entry.getValue();
                        XtremeTask instance = entry.getKey();
                        if (bounds != null && instance != null && bounds.contains(p)) {
                            if (a.plugin().isTaskCompleted(instance)) {
                                a.plugin().toggleTaskCompletedAndPersist(instance);
                            }
                            e.consume();
                            return e;
                        }
                    }
                }

                if (detailTask != null && a.taskDetailsIncrementGroupBounds().contains(p)) {
                    int completed = a.plugin().getTaskGroupProgress(detailTask).getCompleted();
                    a.plugin().setTaskGroupCompletedCountAndPersist(detailTask, completed + 1);
                    a.animations().startCompletionAnim(detailTask.getId());
                    e.consume();
                    return e;
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

        if ((a.activeTab() == OverlayInputAccess.MainTab.TASKS || a.activeTab() == OverlayInputAccess.MainTab.CURRENT)
                && button == MouseEvent.BUTTON1) {
            if (a.keyboardHintsButtonBounds().contains(p)) {
                a.setKeyboardHintsOpen(!a.isKeyboardHintsOpen());
                e.consume();
                return e;
            }

            if (a.isKeyboardHintsOpen()) {
                if (a.keyboardHintsPopupBounds().contains(p)) {
                    e.consume();
                    return e;
                }

                a.setKeyboardHintsOpen(false);
            }
        }

        if (tryHandleTaskRowClick(e, p, button)) {
            return e;
        }

        // SEARCH box focus (only when panel is open and click is inside panel)
        if (a.activeTab() == OverlayInputAccess.MainTab.TASKS && button == MouseEvent.BUTTON1) {
            if (a.taskViewModeBounds().contains(p)) {
                if (!isCondenseRepeatedTasksBlocked()) {
                    a.plugin().toggleCondenseRepeatedTasks();
                    a.resetTaskListViewAfterQueryChange();
                }
                e.consume();
                return e;
            }

            if (a.controlsLayout().searchBox.contains(p)) {
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
                    int charIdx = charIndexAt(p.x, a.controlsLayout().searchTextX, a.controlsLayout().searchCharXPositions, text);
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
                a.setActiveTab(OverlayInputAccess.MainTab.CURRENT);
                e.consume();
                return e;
            }
            if (a.tasksTabBounds().contains(p)) {
                a.setActiveTab(OverlayInputAccess.MainTab.TASKS);
                e.consume();
                return e;
            }
            if (a.rulesTabBounds().contains(p)) {
                a.setActiveTab(OverlayInputAccess.MainTab.RULES);
                e.consume();
                return e;
            }

            // TASKS tab clicks
            if (a.activeTab() == OverlayInputAccess.MainTab.TASKS) {
                boolean changed = false;

                // ----------------------------
                // 0) Filters / Sort clear links
                // ----------------------------
                if (a.controlsLayout().clearFilters.width > 0
                        && a.controlsLayout().clearFilters.contains(p)) {
                    TaskListQuery q = a.taskQuery();
                    q.selectAllSources();
                    q.statusFilter = TaskListQuery.StatusFilter.ALL;
                    q.tierScope = TaskListQuery.TierScope.ALL_TIERS;
                    autoDisableCompletionSortIfNeeded();
                    autoDisableDateSortIfNeeded();
                    autoDisableTimeTicksSortIfNeeded();
                    a.resetTaskListViewAfterQueryChange();
                    e.consume();
                    return e;
                }
                if (a.controlsLayout().clearSort.width > 0
                        && a.controlsLayout().clearSort.contains(p)) {
                    TaskListQuery q = a.taskQuery();
                    q.sortByCompletion = false;
                    q.sortByTier = false;
                    q.sortByDate = false;
                    q.sortByTimeTicks = false;
                    a.resetTaskListViewAfterQueryChange();
                    e.consume();
                    return e;
                }
                // ----------------------------
                // 1) SOURCE filter (multi-select; all selected collapses to All)
                // ----------------------------
                if (a.controlsLayout().filterSourceAll.contains(p)) {
                    changed = setSourceFilter(TaskListQuery.SourceFilter.ALL);
                } else if (a.controlsLayout().filterCA.contains(p)) {
                    changed = toggleSourceFilter(TaskListQuery.SourceFilter.CA);
                } else if (a.controlsLayout().filterCL.contains(p)) {
                    changed = toggleSourceFilter(TaskListQuery.SourceFilter.CLOGS);
                } else if (a.controlsLayout().filterDA.contains(p)) {
                    changed = toggleSourceFilter(TaskListQuery.SourceFilter.DAS);
                }

                // ----------------------------
                // 2) STATUS filter (single-select)
                // + auto-clean completion sort when status != ALL
                // ----------------------------
                else if (a.controlsLayout().filterStatusAll.contains(p)) {
                    changed = setStatusFilter(TaskListQuery.StatusFilter.ALL);
                    changed |= autoDisableDateSortIfNeeded();
                    changed |= autoDisableTimeTicksSortIfNeeded();
                } else if (a.controlsLayout().filterIncomplete.contains(p)) {
                    changed = toggleSingleSelectStatus(TaskListQuery.StatusFilter.INCOMPLETE);
                    changed |= autoDisableCompletionSortIfNeeded();
                    changed |= autoDisableDateSortIfNeeded();
                    changed |= autoDisableTimeTicksSortIfNeeded();
                } else if (a.controlsLayout().filterComplete.contains(p)) {
                    changed = toggleSingleSelectStatus(TaskListQuery.StatusFilter.COMPLETE);
                    changed |= autoDisableCompletionSortIfNeeded();
                }

                // ----------------------------
                // 3) TIER scope (single-select)
                // + auto-clean tier sort when tierScope != ALL_TIERS
                // ----------------------------
                else if (a.controlsLayout().filterTierThis.contains(p)) {
                    changed = setTierScope(TaskListQuery.TierScope.THIS_TIER);
                    changed |= autoDisableTierSortIfNeeded();
                } else if (a.controlsLayout().filterTierAll.contains(p)) {
                    changed = setTierScope(TaskListQuery.TierScope.ALL_TIERS);
                }

                // ----------------------------
                // 4) SORT pills (3 buttons)
                // ----------------------------
                boolean dateEnabledScope = a.taskQuery().statusFilter == TaskListQuery.StatusFilter.COMPLETE;
                if (false) { /* dummy to keep else-if chain valid */ }
                else if (a.controlsLayout().sortCompletion.contains(p)) {
                    changed = onClickSortCompletion();
                } else if (a.controlsLayout().sortTier.contains(p)) {
                    changed = onClickSortTier();
                } else if (a.controlsLayout().sortDate.contains(p)) {
                    changed = onClickSortDate();
                } else if (dateEnabledScope && a.controlsLayout().sortTimeTicks.width > 0 && a.controlsLayout().sortTimeTicks.contains(p)) {
                    changed = onClickSortTimeTicks();
                }

                // ----------------------------
                // 5) See New Tasks toggle (session-only, only visible when hasNewTasks)
                // ----------------------------
                else if (a.controlsLayout().filterNewTasks.width > 0 && a.controlsLayout().filterNewTasks.contains(p)) {
                    a.taskQuery().showNewTasksFilter = !a.taskQuery().showNewTasksFilter;
                    if (a.taskQuery().showNewTasksFilter) {
                        // Auto-expand to show new tasks across all tiers/sources/statuses
                        a.taskQuery().tierScope = TaskListQuery.TierScope.ALL_TIERS;
                        a.taskQuery().selectAllSources();
                        a.taskQuery().statusFilter = TaskListQuery.StatusFilter.ALL;
                    }
                    changed = true;
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
        if (a.activeTab() == OverlayInputAccess.MainTab.CURRENT && button == MouseEvent.BUTTON1) {
            XtremeTask current = a.plugin().getCurrentTask();

            if (current != null && a.currentLayout().wikiButtonBounds.contains(p)) {
                String url = current.getWikiUrl();
                if (url != null && !url.trim().isEmpty()) {
                    LinkBrowser.browse(url);
                    e.consume();
                    return e;
                }
            }

            boolean currentCompleted = current != null && a.plugin().isTaskCompleted(current);
            boolean rollEnabled = (current == null) || currentCompleted;
            boolean completeEnabled = (current != null) && !currentCompleted;

            if (completeEnabled && a.currentLayout().completeButtonBounds.contains(p)) {
                if (current != null) {
                    a.animations().startCompletionAnim(current.getId());
                }

                a.plugin().completeCurrentTaskAndPersist();
                e.consume();
                return e;
            }

            if (rollEnabled && a.currentLayout().rollButtonBounds.contains(p)) {
                a.animations().startRoll();
                a.plugin().rollRandomTaskAndPersist();
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
        if (a.activeTab() == OverlayInputAccess.MainTab.RULES && button == MouseEvent.BUTTON1) {
            if (a.rulesLayout().scrollbarRailBounds.width > 0) {
                Rectangle thumb = a.rulesLayout().scrollbarThumbBounds;
                Rectangle rail = a.rulesLayout().scrollbarRailBounds;

                if (thumb.contains(p)) {
                    draggingRulesScrollbar = true;
                    rulesScrollbarGrabOffsetY = p.y - thumb.y;
                    e.consume();
                    return e;
                }

                if (rail.contains(p)) {
                    draggingRulesScrollbar = true;
                    rulesScrollbarGrabOffsetY = Math.max(0, thumb.height / 2);
                    updateRulesScrollbarDrag(e.getY());
                    e.consume();
                    return e;
                }
            }

            // Sub-tab toggles
            if (a.rulesLayout().subTabRulesBounds.contains(p)) {
                a.setRulesSubTab(RulesTabLayout.SubTab.RULES);
                a.rulesScroll().reset();
                e.consume();
                return e;
            }
            if (a.rulesLayout().subTabDataSyncsBounds.contains(p)) {
                a.setRulesSubTab(RulesTabLayout.SubTab.DATA_SYNCS);
                a.rulesScroll().reset();
                e.consume();
                return e;
            }
            if (a.rulesLayout().syncClogsButtonBounds.contains(p)) {
                a.setClogSyncedTasksExpanded(false);
                a.syncMismatchScroll().reset();
                a.plugin().syncCollectionLogsAndPersist();
                e.consume();
                return e;
            }
            if (a.rulesLayout().syncCAsButtonBounds.contains(p)) {
                a.setCaSyncedTasksExpanded(false);
                a.syncMismatchScroll().reset();
                a.plugin().syncCombatAchievementsAndPersist();
                e.consume();
                return e;
            }
            if (a.rulesLayout().syncCaMarkedTasksToggleBounds.contains(p)) {
                a.toggleCaSyncedTasksExpanded();
                e.consume();
                return e;
            }
            if (a.rulesLayout().syncClogMarkedTasksToggleBounds.contains(p)) {
                a.toggleClogSyncedTasksExpanded();
                e.consume();
                return e;
            }
            if (a.rulesLayout().syncCaReviewButtonBounds.contains(p)) {
                a.openSyncMismatchReview(TaskSource.COMBAT_ACHIEVEMENT);
                syncMismatchReviewOpenedAt = e.getWhen();
                rememberSyncMismatchClick(e, p, button);
                e.consume();
                return e;
            }
            if (a.rulesLayout().syncCaReviewIgnoreButtonBounds.contains(p)) {
                a.plugin().dismissSyncMismatchReview(TaskSource.COMBAT_ACHIEVEMENT);
                a.closeSyncMismatchReview();
                e.consume();
                return e;
            }
            if (a.rulesLayout().syncClogReviewButtonBounds.contains(p)) {
                a.openSyncMismatchReview(TaskSource.COLLECTION_LOG);
                syncMismatchReviewOpenedAt = e.getWhen();
                rememberSyncMismatchClick(e, p, button);
                e.consume();
                return e;
            }
            if (a.rulesLayout().syncClogReviewIgnoreButtonBounds.contains(p)) {
                a.plugin().dismissSyncMismatchReview(TaskSource.COLLECTION_LOG);
                a.closeSyncMismatchReview();
                e.consume();
                return e;
            }
            if (a.rulesLayout().taskerFaqLinkBounds.contains(p)) {
                LinkBrowser.browse(RulesTabRenderer.taskerFaqUrl());
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

        if (a.isSyncMismatchDescriptionOpen())
        {
            if (a.syncMismatchDescriptionCloseBounds().contains(p))
            {
                a.closeSyncMismatchDescription();
                rememberSyncMismatchClick(e, p, button);
                e.consume();
                return true;
            }

            if (a.syncMismatchDescriptionBounds().contains(p))
            {
                rememberSyncMismatchClick(e, p, button);
                e.consume();
                return true;
            }

            a.closeSyncMismatchDescription();
            rememberSyncMismatchClick(e, p, button);
            e.consume();
            return true;
        }

        if (a.isSyncMismatchApplyConfirmOpen())
        {
            if (a.syncMismatchConfirmYesBounds().contains(p))
            {
                a.plugin().markSyncMismatchTasksIncompleteAndPersist(a.selectedSyncMismatchTasks());
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
            int mismatchCount = a.plugin().getSyncMismatchTasks(a.syncMismatchReviewSource()).size();
            if (mismatchCount > 0 && a.syncMismatchSelectedCount() >= mismatchCount)
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

        XtremeTask nameTask = syncMismatchTaskAt(p, true);
        if (nameTask != null)
        {
            a.openSyncMismatchDescription(nameTask);
            rememberSyncMismatchClick(e, p, button);
            e.consume();
            return true;
        }

        XtremeTask actionTask = syncMismatchTaskAt(p, false);
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

    private XtremeTask syncMismatchTaskAt(Point p, boolean nameColumn)
    {
        Rectangle viewport = a.syncMismatchViewportBounds();
        if (viewport.width <= 0 || viewport.height <= 0 || !viewport.contains(p))
        {
            return null;
        }

        int actionColumnX = viewport.x + viewport.width - SYNC_MISMATCH_ACTION_COLUMN_W;
        if (nameColumn)
        {
            if (p.x >= actionColumnX)
            {
                return null;
            }
        }
        else if (p.x < actionColumnX)
        {
            return null;
        }

        int rowBlock = a.syncMismatchRowBlock();
        if (rowBlock <= 0)
        {
            return null;
        }

        int rowOffset = (p.y - viewport.y) / rowBlock;
        if (rowOffset < 0)
        {
            return null;
        }

        List<XtremeTask> tasks = a.plugin().getSyncMismatchTasks(a.syncMismatchReviewSource());
        int index = a.syncMismatchScroll().offsetRows + rowOffset;
        if (index < 0 || index >= tasks.size())
        {
            return null;
        }

        XtremeTask task = tasks.get(index);
        if (nameColumn && (task == null || task.getSource() != TaskSource.COMBAT_ACHIEVEMENT))
        {
            return null;
        }
        return task;
    }

    private boolean isSyncMismatchInteractivePoint(Point p)
    {
        return a.syncMismatchCloseBounds().contains(p)
                || a.syncMismatchMarkAllBounds().contains(p)
                || (a.syncMismatchSelectedCount() > 0 && a.syncMismatchApplyBounds().contains(p))
                || a.syncMismatchCancelBounds().contains(p)
                || a.syncMismatchScrollbarThumbBounds().contains(p)
                || a.syncMismatchScrollbarRailBounds().contains(p)
                || syncMismatchTaskAt(p, true) != null
                || syncMismatchTaskAt(p, false) != null
                || (a.isSyncMismatchApplyConfirmOpen() && (
                        a.syncMismatchConfirmYesBounds().contains(p)
                                || a.syncMismatchConfirmNoBounds().contains(p)
                ));
    }

    private boolean tryHandleTaskRowClick(MouseEvent e, Point p, int button)
    {
        if (a.activeTab() != OverlayInputAccess.MainTab.TASKS
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

        Rectangle cb;
        synchronized (a.taskCheckboxBounds()) {
            cb = a.taskCheckboxBounds().get(task);
        }
        boolean clickedCheckbox = (cb != null && cb.contains(p));

        if (button == MouseEvent.BUTTON1 && clickedCheckbox)
        {
            boolean wasDone = a.useCondensedTaskRows()
                    ? a.plugin().getTaskGroupProgress(task).isComplete()
                    : a.plugin().isTaskCompleted(task);
            if (!wasDone) {
                a.animations().startCompletionAnim(task.getId());
            }

            if (wasDone) {
                com.amtrollin.xtremetasker.models.TaskGroupProgress progress = a.plugin().getTaskGroupProgress(task);
                if (a.useCondensedTaskRows() && progress != null && progress.isGrouped()) {
                    a.requestMarkAllIncompleteConfirmation(task, true);
                } else if (a.plugin().skipSingleIncompleteConfirmation()) {
                    a.plugin().toggleTaskCompletedAndPersist(task);
                } else {
                    a.requestMarkAllIncompleteConfirmation(task, false);
                }
            } else if (a.useCondensedTaskRows()) {
                if (a.plugin().getTaskGroupProgress(task).isGrouped()) {
                    a.plugin().toggleTaskGroupProgressAndPersist(task);
                } else {
                    a.plugin().toggleTaskCompletedAndPersist(task);
                }
            } else {
                a.plugin().toggleTaskCompletedAndPersist(task);
            }

            List<XtremeTask> tasksAfter = a.getSortedTasksForTier(a.activeTier());
            a.selectionModel().setSelectionToTask(a.activeTier(), tasksAfter, task);

            rememberTaskRowClick(e, p, button);
            e.consume();
            return true;
        }

        if (button == MouseEvent.BUTTON1 && !clickedCheckbox)
        {
            a.openTaskDetails(task);
            rememberTaskRowClick(e, p, button);
            e.consume();
            return true;
        }

        if (button == MouseEvent.BUTTON3)
        {
            a.openTaskDetails(task);
            rememberTaskRowClick(e, p, button);
            e.consume();
            return true;
        }

        return false;
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
                boolean hoveringSyncMismatch =
                        (a.isSyncMismatchDescriptionOpen() && (
                                a.syncMismatchDescriptionCloseBounds().contains(p)
                                        || a.syncMismatchDescriptionBounds().contains(p)
                        ))
                                || isSyncMismatchInteractivePoint(p);
                updateHandCursor(hoveringSyncMismatch);
                return e;
            }
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
        boolean currentCompleted = current != null && a.plugin().isTaskCompleted(current);
        boolean rollEnabled = (current == null) || currentCompleted;
        boolean completeEnabled = (current != null) && !currentCompleted;

        TaskListQuery tq = a.taskQuery();
        boolean completionDisabled = tq.statusFilter != TaskListQuery.StatusFilter.ALL;
        boolean tierEnabledScope = tq.tierScope == TaskListQuery.TierScope.ALL_TIERS;
        boolean dateEnabledScope = tq.statusFilter == TaskListQuery.StatusFilter.COMPLETE;
        boolean hasAnySort = tq.sortByCompletion || tq.sortByTier || tq.sortByDate || tq.sortByTimeTicks;

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
                        || a.taskDetailsToggleBounds().contains(p)
                        || a.taskDetailsScrollbarThumbBounds().contains(p)
                        || a.taskDetailsScrollbarRailBounds().contains(p)
                        || a.taskDetailsDecrementGroupBounds().contains(p)
                        || a.taskDetailsIncrementGroupBounds().contains(p)
                        || containsAny(a.taskDetailsInstanceRemoveBounds(), p)
                ))
                // RULES tab
                || (a.activeTab() == OverlayInputAccess.MainTab.RULES && (
                        a.rulesLayout().subTabRulesBounds.contains(p)
                        || a.rulesLayout().subTabDataSyncsBounds.contains(p)
                        || a.rulesLayout().taskerFaqLinkBounds.contains(p)
                        || a.rulesLayout().githubReadmeLinkBounds.contains(p)
                        || a.rulesLayout().syncClogsButtonBounds.contains(p)
                        || a.rulesLayout().syncCAsButtonBounds.contains(p)
                        || a.rulesLayout().syncCaMarkedTasksToggleBounds.contains(p)
                        || a.rulesLayout().syncClogMarkedTasksToggleBounds.contains(p)
                        || a.rulesLayout().syncCaReviewButtonBounds.contains(p)
                        || a.rulesLayout().syncCaReviewIgnoreButtonBounds.contains(p)
                        || a.rulesLayout().syncClogReviewButtonBounds.contains(p)
                        || a.rulesLayout().syncClogReviewIgnoreButtonBounds.contains(p)
                        || a.rulesLayout().scrollbarThumbBounds.contains(p)
                        || a.rulesLayout().scrollbarRailBounds.contains(p)
                ))
                // CURRENT tab
                || (a.activeTab() == OverlayInputAccess.MainTab.CURRENT && (
                        a.currentLayout().wikiButtonBounds.contains(p)
                        || (rollEnabled && a.currentLayout().rollButtonBounds.contains(p))
                        || (completeEnabled && a.currentLayout().completeButtonBounds.contains(p))
                        || a.keyboardHintsButtonBounds().contains(p)
                ))
                // TASKS tab
                || (a.activeTab() == OverlayInputAccess.MainTab.TASKS && (
                        // tier tabs
                        containsAny(a.tierTabBounds(), p)
                        || cl.searchBox.contains(p)
                        || (cl.clearFilters.width > 0 && cl.clearFilters.contains(p))
                        || (cl.clearSort.width > 0 && cl.clearSort.contains(p))
                        // filter pills (always clickable)
                        || cl.filterSourceAll.contains(p)
                        || cl.filterCA.contains(p)
                        || cl.filterCL.contains(p)
                        || cl.filterDA.contains(p)
                        || cl.filterStatusAll.contains(p)
                        || cl.filterIncomplete.contains(p)
                        || cl.filterComplete.contains(p)
                        || cl.filterTierThis.contains(p)
                        || cl.filterTierAll.contains(p)
                        // sort pills (conditionally enabled)
                        || (!completionDisabled && cl.sortCompletion.contains(p))
                        || (tierEnabledScope && cl.sortTier.contains(p))
                        || (dateEnabledScope && cl.sortDate.contains(p))
                        || (dateEnabledScope && cl.sortTimeTicks.width > 0 && cl.sortTimeTicks.contains(p))
                        || (hasAnySort && cl.sortReset.contains(p))
                        // new tasks button
                        || cl.filterNewTasks.contains(p)
                        || cl.filterNewTasksHelp.contains(p)
                        // keyboard hints
                        || a.keyboardHintsButtonBounds().contains(p)
                        || (!isCondenseRepeatedTasksBlocked() && a.taskViewModeBounds().contains(p))
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
            newX = Math.max(0, Math.min(newX, canvasW - a.iconBounds().width));
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

        if (draggingRulesScrollbar) {
            updateRulesScrollbarDrag(e.getY());
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
        if (draggingRulesScrollbar) {
            draggingRulesScrollbar = false;
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

    private void updateRulesScrollbarDrag(int mouseY) {
        Rectangle rail = a.rulesLayout().scrollbarRailBounds;
        Rectangle thumb = a.rulesLayout().scrollbarThumbBounds;
        if (rail.height <= 0 || thumb.height <= 0) {
            return;
        }

        int totalRows = a.rulesLayout().totalContentRows;
        int rowBlock = a.rulesRowBlock();
        int viewportH = a.rulesViewportBounds().height;
        int visible = a.rulesScroll().visibleRows(viewportH, rowBlock);
        int maxOffset = Math.max(0, totalRows - visible);
        int trackH = Math.max(0, rail.height - thumb.height);
        if (totalRows <= 0 || visible <= 0 || maxOffset <= 0 || trackH <= 0) {
            a.rulesScroll().setOffsetRows(0, viewportH, rowBlock, totalRows);
            return;
        }

        int thumbY = Math.max(rail.y, Math.min(mouseY - rulesScrollbarGrabOffsetY, rail.y + trackH));
        double frac = (double) (thumbY - rail.y) / (double) trackH;
        int nextOffset = (int) Math.round(frac * maxOffset);
        a.rulesScroll().setOffsetRows(nextOffset, viewportH, rowBlock, totalRows);
    }

    private void updateSyncMismatchScrollbarDrag(int mouseY) {
        Rectangle rail = a.syncMismatchScrollbarRailBounds();
        Rectangle thumb = a.syncMismatchScrollbarThumbBounds();
        if (rail.height <= 0 || thumb.height <= 0) {
            return;
        }

        int totalRows = a.plugin().getSyncMismatchTasks(a.syncMismatchReviewSource()).size();
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

    // =========================
    // Sort + auto-clean helpers
    // =========================

    private boolean autoDisableCompletionSortIfNeeded() {
        TaskListQuery q = a.taskQuery();
        if (q.statusFilter != TaskListQuery.StatusFilter.ALL && q.sortByCompletion) {
            q.sortByCompletion = false;
            return true;
        }
        return false;
    }

    private boolean autoDisableTierSortIfNeeded() {
        TaskListQuery q = a.taskQuery();
        if (q.tierScope != TaskListQuery.TierScope.ALL_TIERS && q.sortByTier) {
            q.sortByTier = false;
            return true;
        }
        return false;
    }

    private boolean autoDisableDateSortIfNeeded() {
        TaskListQuery q = a.taskQuery();
        if (q.statusFilter != TaskListQuery.StatusFilter.COMPLETE && q.sortByDate) {
            q.sortByDate = false;
            return true;
        }
        return false;
    }

    private boolean autoDisableTimeTicksSortIfNeeded() {
        TaskListQuery q = a.taskQuery();
        if (q.statusFilter != TaskListQuery.StatusFilter.COMPLETE && q.sortByTimeTicks) {
            q.sortByTimeTicks = false;
            return true;
        }
        return false;
    }

    private boolean isCondenseRepeatedTasksBlocked() {
        TaskListQuery q = a.taskQuery();
        return q.sortByDate || q.sortByTimeTicks;
    }

    private boolean onClickSortCompletion() {
        TaskListQuery q = a.taskQuery();

        if (q.statusFilter != TaskListQuery.StatusFilter.ALL) {
            return false;
        }

        if (!q.sortByCompletion) {
            // OFF → Incomplete First
            q.sortByCompletion = true;
            q.completedFirst = false;
            return true;
        }

        if (!q.completedFirst) {
            // Incomplete First → Complete First
            q.completedFirst = true;
            return true;
        }

        // Complete First → OFF
        q.sortByCompletion = false;
        return true;
    }

    private boolean onClickSortTier() {
        TaskListQuery q = a.taskQuery();

        if (q.tierScope != TaskListQuery.TierScope.ALL_TIERS) {
            return false;
        }

        if (!q.sortByTier) {
            // OFF → Easy Tier First
            q.sortByTier = true;
            q.easyTierFirst = true;
            return true;
        }

        if (q.easyTierFirst) {
            // Easy Tier First → Master Tier First
            q.easyTierFirst = false;
            return true;
        }

        // Master Tier First → OFF
        q.sortByTier = false;
        return true;
    }

    private boolean onClickSortTimeTicks() {
        TaskListQuery q = a.taskQuery();
        if (!q.sortByTimeTicks) {
            // OFF → Longest first
            q.sortByTimeTicks = true;
            q.longestFirst = true;
            return true;
        }
        if (q.longestFirst) {
            // Longest first → Shortest first
            q.longestFirst = false;
            return true;
        }
        // Shortest first → OFF
        q.sortByTimeTicks = false;
        return true;
    }

    private boolean onClickSortDate() {
        TaskListQuery q = a.taskQuery();

        if (q.statusFilter != TaskListQuery.StatusFilter.COMPLETE) {
            return false;
        }

        if (!q.sortByDate) {
            // OFF → Newest First
            q.sortByDate = true;
            q.newestFirst = true;
            return true;
        }

        if (q.newestFirst) {
            // Newest First → Oldest First
            q.newestFirst = false;
            return true;
        }

        // Oldest First → OFF
        q.sortByDate = false;
        return true;
    }

    private boolean onClickSortReset() {
        TaskListQuery q = a.taskQuery();
        boolean changed = false;

        if (q.sortByCompletion) {
            q.sortByCompletion = false;
            changed = true;
        }
        if (q.sortByTier) {
            q.sortByTier = false;
            changed = true;
        }

        return changed;
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
