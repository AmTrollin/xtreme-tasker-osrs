package com.amtrollin.xtremetasker;

import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.models.verification.TaskVerification;
import com.amtrollin.xtremetasker.ui.XtremeTaskerOverlay;
import com.google.gson.Gson;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class TaskDetailsIncompleteActionTest
{
    @Test
    public void expandedViewRemovesOnlyTheSelectedCompletedRepeat()
    {
        for (int completed : new int[]{2, 3})
        {
            Fixture f = new Fixture(false, completed);
            f.overlay.openTaskDetails(f.tasks.get(1));
            f.overlay.handleTaskDetailsMarkIncompleteButton(f.tasks.get(1));
            assertTrue("The X must respond even when other repeats remain incomplete",
                    f.overlay.isTaskDetailsIncompleteConfirmOpen());
            assertTrue("Opening confirmation must not change progress", f.plugin.isTaskCompleted(f.tasks.get(1)));
            f.overlay.confirmTaskDetailsIncompleteSelection();
            f.assertOnlySecondRemoved(completed);
        }
    }

    @Test
    public void condensedViewStillRemovesOnlyTheSelectedCompletedRepeat()
    {
        for (int completed : new int[]{2, 3})
        {
            Fixture f = new Fixture(true, completed);
            f.overlay.openTaskDetails(f.tasks.get(0));
            f.overlay.handleTaskDetailsInstanceMarkIncompleteButton(f.tasks.get(1));
            assertTrue(f.overlay.isTaskDetailsIncompleteConfirmOpen());
            f.overlay.confirmTaskDetailsIncompleteSelection();
            f.assertOnlySecondRemoved(completed);
        }
    }

    @Test
    public void cancellationAndIncompleteInstancesDoNotChangeProgress()
    {
        Fixture f = new Fixture(false, 2);
        f.overlay.openTaskDetails(f.tasks.get(1));
        f.overlay.handleTaskDetailsMarkIncompleteButton(f.tasks.get(1));
        assertTrue(f.overlay.isTaskDetailsIncompleteConfirmOpen());
        f.overlay.closeTaskDetailsIncompleteConfirm();
        assertTrue(f.plugin.isTaskCompleted(f.tasks.get(1)));

        f.overlay.openTaskDetails(f.tasks.get(2));
        f.overlay.handleTaskDetailsMarkIncompleteButton(f.tasks.get(2));
        assertFalse(f.overlay.isTaskDetailsIncompleteConfirmOpen());
        f.overlay.handleTaskDetailsMarkIncompleteButton(null);
        assertFalse(f.overlay.isTaskDetailsIncompleteConfirmOpen());
    }

    private static final class Fixture
    {
        private final XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        private final List<XtremeTask> tasks = plugin.tasksForTesting();
        private final XtremeTaskerOverlay overlay;

        private Fixture(boolean condensed, int completed)
        {
            for (int i = 0; i < 3; i++)
            {
                TaskVerification verification = new Gson().fromJson(
                        "{\"method\":\"collection-log\",\"itemIds\":[10878,10879,10880],\"count\":" + (i + 1) + "}",
                        TaskVerification.class);
                XtremeTask task = new XtremeTask("repeat-" + i, "Get a unique satchel", TaskSource.COLLECTION_LOG,
                        TaskTier.EASY, 10878, null, null, null, null, verification, null);
                tasks.add(task);
                if (i < completed)
                {
                    if (i == 1) plugin.syncedCompletedTaskIdsForTesting().add(task.getId());
                    else plugin.manualCompletedTaskIdsForTesting().add(task.getId());
                }
            }
            overlay = new XtremeTaskerOverlay(null, plugin, null, null)
            {
                @Override
                public boolean useCondensedTaskRows()
                {
                    return condensed;
                }
            };
        }

        private void assertOnlySecondRemoved(int previouslyCompleted)
        {
            assertTrue(plugin.isTaskCompleted(tasks.get(0)));
            assertFalse(plugin.isTaskCompleted(tasks.get(1)));
            assertEquals(previouslyCompleted == 3, plugin.isTaskCompleted(tasks.get(2)));
            assertFalse(overlay.isTaskDetailsIncompleteConfirmOpen());
        }
    }
}
