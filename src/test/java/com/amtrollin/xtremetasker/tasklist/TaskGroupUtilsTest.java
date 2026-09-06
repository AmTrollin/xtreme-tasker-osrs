package com.amtrollin.xtremetasker.tasklist;

import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.models.verification.TaskVerification;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TaskGroupUtilsTest
{
    private static final int[] ANCIENT_PAGE_ITEM_IDS = {
            11341, 11342, 11343, 11344, 11345, 11346, 11347, 11348, 11349,
            11350, 11351, 11352, 11353, 11354, 11355, 11356, 11357, 11358,
            11359, 11360, 11361, 11362, 11363, 11364, 11365, 11366
    };

    @Test
    public void condensedAncientPageTasksRemainSeparatedByTier()
    {
        List<XtremeTask> tasks = new ArrayList<>();
        addTasks(tasks, TaskTier.EASY, 4);
        addTasks(tasks, TaskTier.MEDIUM, 4);
        addTasks(tasks, TaskTier.HARD, 3);
        addTasks(tasks, TaskTier.ELITE, 2);

        List<XtremeTask> condensed = TaskGroupUtils.collapsePreservingOrder(tasks);

        assertEquals(4, condensed.size());
        assertEquals(TaskTier.EASY, condensed.get(0).getTier());
        assertEquals(TaskTier.MEDIUM, condensed.get(1).getTier());
        assertEquals(TaskTier.HARD, condensed.get(2).getTier());
        assertEquals(TaskTier.ELITE, condensed.get(3).getTier());
        assertEquals(4, TaskGroupUtils.groupFor(tasks, condensed.get(0)).size());
        assertEquals(4, TaskGroupUtils.groupFor(tasks, condensed.get(1)).size());
        assertEquals(3, TaskGroupUtils.groupFor(tasks, condensed.get(2)).size());
        assertEquals(2, TaskGroupUtils.groupFor(tasks, condensed.get(3)).size());
    }

    private static void addTasks(List<XtremeTask> tasks, TaskTier tier, int count)
    {
        for (int i = 0; i < count; i++)
        {
            TaskVerification verification = new Gson().fromJson(
                    "{\"method\":\"collection-log\",\"itemIds\":"
                            + new Gson().toJson(ANCIENT_PAGE_ITEM_IDS)
                            + ",\"count\":2}",
                    TaskVerification.class);
            tasks.add(new XtremeTask(
                    "ancient_pages_" + tier + "_" + i,
                    "Get 2 unique Ancient pages",
                    TaskSource.COLLECTION_LOG,
                    tier,
                    null,
                    null,
                    null,
                    null,
                    null,
                    verification,
                    null));
        }
    }
}
