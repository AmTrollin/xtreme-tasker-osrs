package com.amtrollin.xtremetasker.ui.tasks;

import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;

public class TaskDetailsPopupTest
{
    @Test
    public void syncMismatchCopyKeepsCollectionLogSpecificMessage()
    {
        XtremeTask task = task(TaskSource.COLLECTION_LOG);

        assertEquals("Marked complete · item not seen in-game", TaskDetailsPopup.syncMismatchTitle(task));
        assertEquals("Open your Collection Log to refresh progress, or mark incomplete.",
                TaskDetailsPopup.syncMismatchAction(task));
    }

    @Test
    public void syncMismatchCopyUsesGenericMessageForCombatAchievementsAndDiaries()
    {
        XtremeTask combatAchievement = task(TaskSource.COMBAT_ACHIEVEMENT);
        XtremeTask diary = task(TaskSource.DIARY_ACHIEVEMENT);

        assertEquals("Task not completed in-game", TaskDetailsPopup.syncMismatchTitle(combatAchievement));
        assertEquals("Mark task incomplete to keep task tracking accurate", TaskDetailsPopup.syncMismatchAction(combatAchievement));
        assertEquals("Task not completed in-game", TaskDetailsPopup.syncMismatchTitle(diary));
        assertEquals("Mark task incomplete to keep task tracking accurate", TaskDetailsPopup.syncMismatchAction(diary));
    }

    @Test
    public void taskDescriptionTimeNeverConvertsHoursToDays() throws Exception
    {
        Method method = TaskDetailsPopup.class.getDeclaredMethod("formatDuration", long.class);
        method.setAccessible(true);

        assertEquals("24h", method.invoke(null, 24L * 60L * 60L));
        assertEquals("54h 23m", method.invoke(null, (54L * 60L + 23L) * 60L));
    }

    @Test
    public void collectionLogItemSummaryOnlyShowsTotalObtained()
    {
        java.util.List<com.amtrollin.xtremetasker.ui.tasks.models.CollectionLogRequirementItem> items = java.util.List.of(
                new com.amtrollin.xtremetasker.ui.tasks.models.CollectionLogRequirementItem("Blue halo", false),
                new com.amtrollin.xtremetasker.ui.tasks.models.CollectionLogRequirementItem("Red halo", false),
                new com.amtrollin.xtremetasker.ui.tasks.models.CollectionLogRequirementItem("Green halo", false));
        com.amtrollin.xtremetasker.ui.tasks.models.CollectionLogRequirementPreview preview =
                new com.amtrollin.xtremetasker.ui.tasks.models.CollectionLogRequirementPreview("total obtained: 0 | current task: 2/3", true, true, items);
        for (int count : new int[] {1, 3})
        {
            com.amtrollin.xtremetasker.models.verification.TaskVerification verification = new com.google.gson.Gson().fromJson(
                    "{\"method\":\"collection-log\",\"count\":" + count + "}",
                    com.amtrollin.xtremetasker.models.verification.TaskVerification.class);
            XtremeTask task = new XtremeTask("halos", "Get a halo", TaskSource.COLLECTION_LOG,
                    TaskTier.EASY, null, null, null, null, null, verification, null);
            com.amtrollin.xtremetasker.ui.tasks.models.CollectionLogRequirementPreview result = TaskDetailsPopup.detailPreview(task, preview, false);
            assertEquals("Collection Log items", result.titleText());
            assertEquals("total obtained: 0", result.summaryText());
        }
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
