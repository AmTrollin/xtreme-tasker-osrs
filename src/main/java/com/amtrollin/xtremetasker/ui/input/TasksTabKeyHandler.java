package com.amtrollin.xtremetasker.ui.input;

import lombok.RequiredArgsConstructor;

import java.awt.event.KeyEvent;

@RequiredArgsConstructor
public final class TasksTabKeyHandler
{
    private final OverlayInputAccess a;

    public boolean handleKeyPressed(KeyEvent e)
    {
        if (a.handleTasksKey(e))
        {
            int total = a.getSortedTasksForTier(a.activeTier()).size();
            int viewportH = a.taskListViewportBounds().height;
            int rowBlock = a.taskRowBlock();
            if (total > 0 && viewportH > 0 && rowBlock > 0)
            {
                a.taskListView().ensureSelectionVisible(total, viewportH, rowBlock);
            }
            return true;
        }

        return false;
    }
}
