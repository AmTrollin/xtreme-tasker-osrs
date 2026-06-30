package com.amtrollin.xtremetasker.ui.input;

import com.amtrollin.xtremetasker.ui.XtremeTaskerOverlay;
import java.awt.event.KeyEvent;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class TasksTabKeyHandler
{
    private final XtremeTaskerOverlay a;

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
