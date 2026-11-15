package com.amtrollin.xtremetasker;

import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.models.verification.TaskVerification;
import com.amtrollin.xtremetasker.tasklist.CollectionLogSequence;
import com.amtrollin.xtremetasker.verification.CollectionLogService;
import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChompyHatSequenceTest
{
    private List<XtremeTask> hats() throws Exception
    {
        List<XtremeTask> tasks = new ArrayList<>();
        try (InputStreamReader reader = new InputStreamReader(getClass().getClassLoader()
                .getResourceAsStream("task_data/tasks.json"), StandardCharsets.UTF_8))
        {
            for (JsonElement element : new JsonParser().parse(reader).getAsJsonObject().getAsJsonArray("tasks"))
            {
                JsonObject data = element.getAsJsonObject();
                TaskVerification verification = new Gson().fromJson(data.get("verification"), TaskVerification.class);
                if (verification == null || verification.getItemIds() == null) continue;
                boolean containsHat = java.util.Arrays.stream(verification.getItemIds()).anyMatch(id -> id >= 2978 && id <= 2995);
                if (!containsHat) continue;
                tasks.add(new XtremeTask(data.get("id").getAsString(), data.get("name").getAsString(),
                        TaskSource.COLLECTION_LOG, TaskTier.valueOf(data.get("tier").getAsString()),
                        data.get("displayItemId").getAsInt(), null, null, data.get("prereqs").getAsString(),
                        data.get("wikiUrl").getAsString(), verification, null));
            }
        }
        return tasks;
    }

    private XtremeTaskerPlugin plugin(List<XtremeTask> tasks, CollectionLogService log)
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        plugin.tasksForTesting().addAll(tasks);
        plugin.setCollectionLogServiceForTesting(log);
        return plugin;
    }

    @Test
    public void taskPackRetainsFiveNamedMilestonesWhilePairChangesAreDeferred() throws Exception
    {
        List<XtremeTask> tasks = hats();
        assertEquals(5, tasks.size());
        String[] names = {"Get the marksman hat", "Get the ogre forester hat", "Get the ogre expert hat",
                "Upgrade to the dragon archer hat", "Upgrade to the expert dragon archer hat"};
        TaskTier[] tiers = {TaskTier.EASY, TaskTier.MEDIUM, TaskTier.HARD, TaskTier.ELITE, TaskTier.MASTER};
        int[] counts = {6, 9, 13, 16, 18};
        for (int i = 0; i < tasks.size(); i++)
        {
            XtremeTask task = tasks.get(i);
            assertEquals(names[i], task.getName());
            assertEquals(tiers[i], task.getTier());
            assertEquals(Integer.valueOf(counts[i]), task.getVerification().getCount());
            assertEquals(18, task.getVerification().getItemIds().length);
            assertFalse(CollectionLogSequence.isChompyHatPair(task));
            assertArrayEquals(new int[] {2978 + counts[i] - 1}, CollectionLogSequence.stepItemIds(task));
        }
    }

    @Test
    public void syncUsesExistingCollectionLogCountThresholds() throws Exception
    {
        List<XtremeTask> tasks = hats();
        CollectionLogService log = new CollectionLogService();
        XtremeTaskerPlugin plugin = plugin(tasks, log);
        log.storeItem(2995);
        assertTrue("A late hat alone must not satisfy a counted milestone",
                plugin.getSyncCompletionCandidateTasks(TaskSource.COLLECTION_LOG).isEmpty());

        log = new CollectionLogService();
        plugin = plugin(tasks, log);
        for (int id = 2978; id <= 2995; id++)
        {
            log.storeItem(id);
            int obtained = id - 2978 + 1;
            List<XtremeTask> expected = new ArrayList<>();
            for (XtremeTask task : tasks)
                if (task.getVerification().getCount() <= obtained) expected.add(task);
            List<XtremeTask> candidates = plugin.getSyncCompletionCandidateTasks(TaskSource.COLLECTION_LOG);
            assertEquals("Sync must preserve existing milestone thresholds", expected.size(), candidates.size());
            for (int i = 0; i < expected.size(); i++)
                assertEquals(expected.get(i).getId(), candidates.get(i).getId());
        }
    }

    @Test
    public void currentMilestoneRequiresItsConfiguredCountWithoutAutoCompleting() throws Exception
    {
        List<XtremeTask> tasks = hats();
        for (XtremeTask task : tasks)
        {
            CollectionLogService log = new CollectionLogService();
            XtremeTaskerPlugin plugin = plugin(tasks, log);
            plugin.setCurrentTaskForTesting(task);
            int count = task.getVerification().getCount();
            int[] itemIds = task.getVerification().getItemIds();
            for (int i = 0; i < count - 1; i++) log.storeItem(itemIds[i]);
            assertFalse(plugin.isCurrentTaskCompletionCriteriaMet());
            log.storeItem(itemIds[count - 1]);
            assertTrue(plugin.isCurrentTaskCompletionCriteriaMet());
            assertFalse("Meeting the requirement must not mark the task complete", plugin.isTaskCompleted(task));
        }
    }
}
