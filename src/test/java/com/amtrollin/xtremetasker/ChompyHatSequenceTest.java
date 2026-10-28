package com.amtrollin.xtremetasker;

import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.models.verification.TaskVerification;
import com.amtrollin.xtremetasker.tasklist.CollectionLogSequence;
import com.amtrollin.xtremetasker.ui.XtremeTaskerOverlay;
import com.amtrollin.xtremetasker.ui.tasks.models.CollectionLogRequirementPreview;
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
                assertEquals("Get the next 2 tiers of Chompy bird hat", data.get("name").getAsString());
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
    public void taskPackReplacesOldHatsWithNineOrderedPairs() throws Exception
    {
        List<XtremeTask> tasks = hats();
        assertEquals(9, tasks.size());
        TaskTier[] tiers = {TaskTier.EASY, TaskTier.EASY, TaskTier.EASY,
                TaskTier.MEDIUM, TaskTier.MEDIUM, TaskTier.HARD, TaskTier.HARD, TaskTier.ELITE, TaskTier.ELITE};
        for (int i = 0; i < tasks.size(); i++)
        {
            XtremeTask task = tasks.get(i);
            assertEquals(tiers[i], task.getTier());
            assertEquals(Integer.valueOf((i + 1) * 2), task.getVerification().getCount());
            assertArrayEquals(new int[] {2978 + i * 2, 2979 + i * 2}, CollectionLogSequence.stepItemIds(task));
            assertEquals(Integer.valueOf(2979 + i * 2), task.getIconItemId());
            assertEquals("Big Chompy Bird Hunting quest; 5 Fletching; 30 Cooking; 30 Ranged", task.getPrereqs());
            assertEquals("https://oldschool.runescape.wiki/w/Chompy_bird_hunting", task.getWikiUrl());
        }
    }

    @Test
    public void syncRequiresEveryPairInOrderAcrossTiers() throws Exception
    {
        List<XtremeTask> tasks = hats();
        CollectionLogService log = new CollectionLogService();
        XtremeTaskerPlugin plugin = plugin(tasks, log);
        log.storeItem(2994);
        log.storeItem(2995);
        assertTrue("The final pair cannot bypass missing earlier steps",
                plugin.getSyncCompletionCandidateTasks(TaskSource.COLLECTION_LOG).isEmpty());

        log = new CollectionLogService();
        plugin = plugin(tasks, log);
        for (int id = 2978; id <= 2995; id++)
        {
            log.storeItem(id);
            List<XtremeTask> candidates = plugin.getSyncCompletionCandidateTasks(TaskSource.COLLECTION_LOG);
            int pairs = (id - 2978 + 1) / 2;
            assertEquals("Each task needs both hats, including across tiers", pairs, candidates.size());
            for (int i = 0; i < pairs; i++) assertEquals(tasks.get(i).getId(), candidates.get(i).getId());
        }
    }

    @Test
    public void currentTaskRequiresItsOwnTwoHatsAndUsesTheirLabelAndIcon() throws Exception
    {
        List<XtremeTask> tasks = hats();
        Method decorate = XtremeTaskerPlugin.class.getDeclaredMethod("decorateCurrentSequenceTask", XtremeTask.class);
        decorate.setAccessible(true);
        for (XtremeTask task : tasks)
        {
            CollectionLogService log = new CollectionLogService();
            XtremeTaskerPlugin plugin = plugin(tasks, log);
            plugin.setCurrentTaskForTesting(task);
            int[] pair = CollectionLogSequence.stepItemIds(task);
            log.storeItem(pair[1]);
            assertFalse("The endpoint alone is insufficient", plugin.isCurrentTaskCompletionCriteriaMet());
            log.storeItem(pair[0]);
            assertTrue(plugin.isCurrentTaskCompletionCriteriaMet());
            assertFalse(plugin.isTaskCompleted(task));
            XtremeTask decorated = (XtremeTask) decorate.invoke(plugin, task);
            assertEquals(Integer.valueOf(pair[1]), decorated.getIconItemId());
            assertTrue(decorated.getName().contains(CollectionLogSequence.chompyHatName(pair[0]) + " & "
                    + CollectionLogSequence.chompyHatName(pair[1])));
            assertEquals(task.getWikiUrl(), decorated.getWikiUrl());
            plugin.setCurrentTaskForTesting(task);
            assertEquals("Already obtained pairs should retain completed-before-rolled timing",
                    Long.valueOf(-1L), plugin.getTaskTimeTicks(task));
        }
    }

    @Test
    public void rollingAnyLaterHatStepSelectsFirstUnfinishedPair() throws Exception
    {
        List<XtremeTask> tasks = hats();
        XtremeTaskerPlugin plugin = plugin(tasks, new CollectionLogService());
        Method normalize = XtremeTaskerPlugin.class.getDeclaredMethod("normalizeRolledTask", XtremeTask.class);
        normalize.setAccessible(true);
        for (XtremeTask expected : tasks)
        {
            assertEquals(expected.getId(), ((XtremeTask) normalize.invoke(plugin, tasks.get(8))).getId());
            plugin.manualCompletedTaskIdsForTesting().add(expected.getId());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void tierPreviewShowsAllEighteenHatImages() throws Exception
    {
        List<XtremeTask> tasks = hats();
        XtremeTaskerPlugin plugin = plugin(tasks, new CollectionLogService());
        XtremeTaskerOverlay overlay = new XtremeTaskerOverlay(null, plugin, null, null);
        Method sections = XtremeTaskerOverlay.class.getDeclaredMethod("sequenceTierSections", XtremeTask.class);
        sections.setAccessible(true);
        List<CollectionLogRequirementPreview.TierSection> preview =
                (List<CollectionLogRequirementPreview.TierSection>) sections.invoke(overlay, tasks.get(0));
        assertEquals(4, preview.size());
        int[] sizes = {6, 4, 4, 4};
        int expectedId = 2978;
        for (int i = 0; i < sizes.length; i++)
        {
            assertEquals(sizes[i], preview.get(i).items().size());
            for (com.amtrollin.xtremetasker.ui.tasks.models.CollectionLogRequirementItem item : preview.get(i).items())
            {
                assertEquals(expectedId++, item.getItemId());
                assertTrue(item.getName().contains(CollectionLogSequence.chompyHatName(item.getItemId())));
            }
        }
        Method summary = XtremeTaskerOverlay.class.getDeclaredMethod("fullSequencePreviewSummaryText", XtremeTask.class);
        summary.setAccessible(true);
        assertEquals("Next: Ogre bowman & Bowman", summary.invoke(overlay, tasks.get(0)));
    }
}
