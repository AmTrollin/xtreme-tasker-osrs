package com.amtrollin.xtremetasker.ui.tasks;

import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TaskDetailsPopupTest
{
    @Test
    public void syncMismatchCopyKeepsCollectionLogSpecificMessage()
    {
        XtremeTask task = task(TaskSource.COLLECTION_LOG);

        assertEquals("Not enough Collection Log items obtained", TaskDetailsPopup.syncMismatchTitle(task));
        assertEquals("Sync your Collection Log via Help tab or mark task incomplete",
                TaskDetailsPopup.syncMismatchAction(task));
    }

    @Test
    public void syncMismatchCopyUsesGenericMessageForCombatAchievementsAndDiaries()
    {
        XtremeTask combatAchievement = task(TaskSource.COMBAT_ACHIEVEMENT);
        XtremeTask diary = task(TaskSource.DIARY_ACHIEVEMENT);

        assertEquals("Task not completed in game", TaskDetailsPopup.syncMismatchTitle(combatAchievement));
        assertEquals("Mark task incomplete to keep task tracking accurate", TaskDetailsPopup.syncMismatchAction(combatAchievement));
        assertEquals("Task not completed in game", TaskDetailsPopup.syncMismatchTitle(diary));
        assertEquals("Mark task incomplete to keep task tracking accurate", TaskDetailsPopup.syncMismatchAction(diary));
    }

    private static XtremeTask task(TaskSource source)
    {
        return new XtremeTask(
                "test_" + source.name().toLowerCase(),
                "Test task",
                source,
                TaskTier.EASY,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
