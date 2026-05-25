package com.amtrollin.xtremetasker.ui.tasklist;

import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;

import java.util.List;
import java.util.function.Function;

public final class TaskListViewController
{
    private final TaskSelectionModel selection;
    private final TaskListScrollController scroll;

    public TaskListViewController(TaskSelectionModel selection, TaskListScrollController scroll)
    {
        this.selection = selection;
        this.scroll = scroll;
    }

    public TaskSelectionModel selection()
    {
        return selection;
    }

    public TaskListScrollController scroll()
    {
        return scroll;
    }

    public void resetAfterQueryChange(TaskTier activeTier,
                                      List<XtremeTask> tasksForTier,
                                      boolean completedFirst,
                                      Function<XtremeTask, Boolean> isCompleted)
    {
        scroll.reset();
        selection.normalizeForTier(activeTier, tasksForTier, completedFirst, isCompleted);
    }

    public void onWheel(double preciseWheelRotation,
                        int viewportH,
                        int rowBlock,
                        int totalRows)
    {
        scroll.onWheel(preciseWheelRotation, viewportH, rowBlock, totalRows, selection);
    }

    public void ensureSelectionVisible(int totalRows, int viewportH, int rowBlock)
    {
        scroll.ensureSelectionVisible(totalRows, viewportH, rowBlock, selection);
    }
}
