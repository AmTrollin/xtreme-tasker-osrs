package com.amtrollin.xtremetasker;

import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.models.verification.TaskVerification;
import com.amtrollin.xtremetasker.verification.CollectionLogService;
import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
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

    // The two-hat redesign is deferred. Keep coverage on the shipped milestones.
    @Test
    public void taskPackRetainsFiveNamedHatMilestones() throws Exception
    {
        List<XtremeTask> tasks = hats();
        assertEquals(5, tasks.size());
        String[] names = {"Get the marksman hat", "Get the ogre forester hat", "Get the ogre expert hat",
                "Upgrade to the dragon archer hat", "Upgrade to the expert dragon archer hat"};
        TaskTier[] tiers = {TaskTier.EASY, TaskTier.MEDIUM, TaskTier.HARD, TaskTier.ELITE, TaskTier.MASTER};
        int[] targetHats = {2983, 2986, 2990, 2993, 2995};
        for (int i = 0; i < tasks.size(); i++)
        {
            XtremeTask task = tasks.get(i);
            assertEquals(names[i], task.getName());
            assertEquals(tiers[i], task.getTier());
            assertEquals(Integer.valueOf(1), task.getVerification().getCount());
            assertArrayEquals(new int[]{targetHats[i]}, task.getVerification().getItemIds());
            assertEquals(Integer.valueOf(targetHats[i]), task.getIconItemId());
            assertEquals("5 Fletching; 30 Cooking; 30 Ranged; Big Chompy Bird Hunting quest", task.getPrereqs());
        }
    }

    @Test
    public void syncOnlySuggestsTheSpecificHatObtained() throws Exception
    {
        List<XtremeTask> tasks = hats();
        for (XtremeTask task : tasks)
        {
            CollectionLogService log = new CollectionLogService();
            XtremeTaskerPlugin plugin = plugin(tasks, log);
            log.storeItem(task.getVerification().getItemIds()[0]);
            List<XtremeTask> candidates = plugin.getSyncCompletionCandidateTasks(TaskSource.COLLECTION_LOG);
            assertEquals("A single named hat must qualify without earlier hats", 1, candidates.size());
            assertEquals(task.getId(), candidates.get(0).getId());
        }
    }

    @Test
    public void currentTaskRequiresOnlyItsNamedHat() throws Exception
    {
        List<XtremeTask> tasks = hats();
        for (XtremeTask task : tasks)
        {
            CollectionLogService log = new CollectionLogService();
            XtremeTaskerPlugin plugin = plugin(tasks, log);
            plugin.setCurrentTaskForTesting(task);
            assertFalse(plugin.isCurrentTaskCompletionCriteriaMet());
            log.storeItem(task.getVerification().getItemIds()[0]);
            assertTrue("The named hat alone must satisfy the task", plugin.isCurrentTaskCompletionCriteriaMet());
            assertFalse("Detection must not mark the task complete", plugin.isTaskCompleted(task));
            plugin.setCurrentTaskForTesting(task);
            assertEquals("An already obtained hat retains completed-before-rolled timing",
                    Long.valueOf(-1L), plugin.getTaskTimeTicks(task));
        }
    }

    @Test
    public void everyOtherHatCannotSubstituteForTheNamedHat() throws Exception
    {
        List<XtremeTask> tasks = hats();
        for (XtremeTask task : tasks)
        {
            CollectionLogService log = new CollectionLogService();
            XtremeTaskerPlugin plugin = plugin(tasks, log);
            plugin.setCurrentTaskForTesting(task);
            int target = task.getVerification().getItemIds()[0];
            for (int id = 2978; id <= 2995; id++)
            {
                if (id != target) log.storeItem(id);
            }
            assertFalse("Seventeen other hats must not satisfy the named hat", plugin.isCurrentTaskCompletionCriteriaMet());
            assertFalse(plugin.getSyncCompletionCandidateTasks(TaskSource.COLLECTION_LOG).stream()
                    .anyMatch(candidate -> candidate.getId().equals(task.getId())));
            log.storeItem(target);
            assertTrue(plugin.isCurrentTaskCompletionCriteriaMet());
        }
    }

    @Test
    public void rollingNamedMilestoneKeepsSelectedTask() throws Exception
    {
        List<XtremeTask> tasks = hats();
        XtremeTaskerPlugin plugin = plugin(tasks, new CollectionLogService());
        Method normalize = XtremeTaskerPlugin.class.getDeclaredMethod("normalizeRolledTask", XtremeTask.class);
        normalize.setAccessible(true);
        for (XtremeTask expected : tasks)
        {
            assertEquals(expected.getId(), ((XtremeTask) normalize.invoke(plugin, expected)).getId());
            plugin.manualCompletedTaskIdsForTesting().add(expected.getId());
        }
    }

    @Test
    public void currentMilestoneRetainsItsNamedTaskInsteadOfPairDecoration() throws Exception
    {
        List<XtremeTask> tasks = hats();
        XtremeTaskerPlugin plugin = plugin(tasks, new CollectionLogService());
        Method decorate = XtremeTaskerPlugin.class.getDeclaredMethod("decorateCurrentSequenceTask", XtremeTask.class);
        decorate.setAccessible(true);
        for (XtremeTask task : tasks)
        {
            XtremeTask decorated = (XtremeTask) decorate.invoke(plugin, task);
            assertEquals(task.getName(), decorated.getName());
            assertEquals(task.getIconItemId(), decorated.getIconItemId());
            assertEquals(task.getWikiUrl(), decorated.getWikiUrl());
        }
    }
}
