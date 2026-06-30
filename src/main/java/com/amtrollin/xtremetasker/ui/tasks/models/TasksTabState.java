package com.amtrollin.xtremetasker.ui.tasks.models;

import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.tasklist.models.TaskListQuery;
import com.amtrollin.xtremetasker.ui.tasklist.*;
import java.awt.Rectangle;
import java.util.Map;

public final class TasksTabState
{
    private final TaskListQuery taskQuery;
    private final TaskControlsLayout controlsLayout;
    private final TaskSelectionModel selectionModel;
    private final TaskListScrollController tasksScroll;
    private final TaskListViewController taskListView;

    private final Map<TaskTier, Rectangle> tierTabBounds;
    private final Map<XtremeTask, Rectangle> taskRowBounds;
    private final Map<XtremeTask, Rectangle> taskCheckboxBounds;
    private final Rectangle taskListViewportBounds;
    private final Rectangle taskScrollbarRailBounds;
    private final Rectangle taskScrollbarThumbBounds;

    public TasksTabState(
            TaskListQuery taskQuery,
            TaskControlsLayout controlsLayout,
            TaskSelectionModel selectionModel,
            TaskListScrollController tasksScroll,
            TaskListViewController taskListView,
            Map<TaskTier, Rectangle> tierTabBounds,
            Map<XtremeTask, Rectangle> taskRowBounds,
            Map<XtremeTask, Rectangle> taskCheckboxBounds,
            Rectangle taskListViewportBounds,
            Rectangle taskScrollbarRailBounds,
            Rectangle taskScrollbarThumbBounds
    )
    {
        this.taskQuery = taskQuery;
        this.controlsLayout = controlsLayout;
        this.selectionModel = selectionModel;
        this.tasksScroll = tasksScroll;
        this.taskListView = taskListView;
        this.tierTabBounds = tierTabBounds;
        this.taskRowBounds = taskRowBounds;
        this.taskCheckboxBounds = taskCheckboxBounds;
        this.taskListViewportBounds = taskListViewportBounds;
        this.taskScrollbarRailBounds = taskScrollbarRailBounds;
        this.taskScrollbarThumbBounds = taskScrollbarThumbBounds;
    }

    public TaskListQuery taskQuery()
    {
        return taskQuery;
    }

    public TaskControlsLayout controlsLayout()
    {
        return controlsLayout;
    }

    public TaskSelectionModel selectionModel()
    {
        return selectionModel;
    }

    public TaskListScrollController tasksScroll()
    {
        return tasksScroll;
    }

    public TaskListViewController taskListView()
    {
        return taskListView;
    }

    public Map<TaskTier, Rectangle> tierTabBounds()
    {
        return tierTabBounds;
    }

    public Map<XtremeTask, Rectangle> taskRowBounds()
    {
        return taskRowBounds;
    }

    public Map<XtremeTask, Rectangle> taskCheckboxBounds()
    {
        return taskCheckboxBounds;
    }

    public Rectangle taskListViewportBounds()
    {
        return taskListViewportBounds;
    }

    public Rectangle taskScrollbarRailBounds()
    {
        return taskScrollbarRailBounds;
    }

    public Rectangle taskScrollbarThumbBounds()
    {
        return taskScrollbarThumbBounds;
    }

}
