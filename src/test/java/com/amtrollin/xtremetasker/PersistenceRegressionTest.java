package com.amtrollin.xtremetasker;

import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.models.persistence.PersistedState;
import com.amtrollin.xtremetasker.verification.CollectionLogService;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PersistenceRegressionTest
{
    @Test
    public void accountNameKeySeparatesCharactersSharingLegacyHash()
    {
        String xtremeTaskrKey = accountNameKey("XtremeTaskr");
        String xtremeTaskrDifferentCaseKey = accountNameKey("xtremetaskr");
        String amTrollinKey = accountNameKey("AmTrollin");

        assertEquals("Character key should be stable across display-name case changes",
                xtremeTaskrKey, xtremeTaskrDifferentCaseKey);
        assertFalse("Different characters must not share the same scoped save key",
                xtremeTaskrKey.equals(amTrollinKey));
        assertEquals("17438951129000919538",
                legacyAccountKeyFromScopedKey("17438951129000919538_" + xtremeTaskrKey));
    }

    @Test
    public void currentTaskGetterResolvesPersistedIdAfterTasksBecomeAvailable() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        plugin.setCollectionLogServiceForTesting(new CollectionLogService());

        XtremeTask task = new XtremeTask(
                "collection_log_easy_get-1-unique-from-tempoross_001_test",
                "Get 1 unique from Tempoross",
                TaskSource.COLLECTION_LOG,
                TaskTier.EASY
        );

        PersistedState state = new PersistedState();
        state.setCurrentTaskId(task.getId());

        Method applyPersistedState = XtremeTaskerPlugin.class.getDeclaredMethod("applyPersistedState", PersistedState.class);
        applyPersistedState.setAccessible(true);
        applyPersistedState.invoke(plugin, state);

        assertNull("Current task object is not hydrated before the task pack is available",
                plugin.getCurrentTask());

        plugin.tasksForTesting().add(task);

        assertEquals("Getter should rehydrate the persisted current task id when tasks are available",
                task.getId(), plugin.getCurrentTask().getId());
    }

    @Test
    public void currentTaskGetterMigratesLegacyGeneratedIdToExplicitTaskId() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        plugin.setCollectionLogServiceForTesting(new CollectionLogService());

        XtremeTask task = new XtremeTask(
                "collection_log_easy_get-1-unique-from-tempoross_001_646f8dc95b",
                "Get 1 unique from Tempoross",
                TaskSource.COLLECTION_LOG,
                TaskTier.EASY
        );
        String legacyId = legacyGeneratedId(task);

        PersistedState state = new PersistedState();
        state.setCurrentTaskId(legacyId);
        state.getTaskTimeTicksById().put(legacyId, 42L);

        Method applyPersistedState = XtremeTaskerPlugin.class.getDeclaredMethod("applyPersistedState", PersistedState.class);
        applyPersistedState.setAccessible(true);
        applyPersistedState.invoke(plugin, state);

        plugin.tasksForTesting().add(task);

        XtremeTask current = plugin.getCurrentTask();
        assertNotNull("Legacy generated id should resolve to the explicit-id task", current);
        assertEquals(task.getId(), current.getId());
        assertEquals(Long.valueOf(42L), plugin.getTaskTimeTicks(task));
    }

    @Test
    public void syncCompletingCurrentTaskKeepsCurrentTimerEligible() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        plugin.setCollectionLogServiceForTesting(new CollectionLogService());

        XtremeTask task = new XtremeTask(
                "collection_log_easy_get-1-unique-from-tempoross_001_test",
                "Get 1 unique from Tempoross",
                TaskSource.COLLECTION_LOG,
                TaskTier.EASY
        );

        plugin.tasksForTesting().add(task);
        plugin.setCurrentTaskForTesting(task);

        plugin.markSyncCompletionCandidateTasksCompleteAndPersist(List.of(task));

        assertTrue("Sync completion should mark the task complete",
                plugin.isTaskCompleted(task));
        assertNotNull("Sync completion should not leave the Current slot",
                plugin.getCurrentTask());
        assertFalse("Sync completion should not become the undoable recent-completion state",
                plugin.canUndoRecentTaskCompletion());
        assertEquals("Synced current task should keep accruing timer ticks until the user hits Complete",
                task.getId(), currentTimerTaskId(plugin));
    }

    @Test
    public void persistedCompletedCurrentTaskStillResolvesAsCurrent() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        plugin.setCollectionLogServiceForTesting(new CollectionLogService());

        XtremeTask task = new XtremeTask(
                "collection_log_easy_get-1-unique-from-tempoross_001_test",
                "Get 1 unique from Tempoross",
                TaskSource.COLLECTION_LOG,
                TaskTier.EASY
        );

        PersistedState state = new PersistedState();
        state.setCurrentTaskId(task.getId());
        state.getSyncedCompletedTaskIds().add(task.getId());

        Method applyPersistedState = XtremeTaskerPlugin.class.getDeclaredMethod("applyPersistedState", PersistedState.class);
        applyPersistedState.setAccessible(true);
        applyPersistedState.invoke(plugin, state);

        plugin.tasksForTesting().add(task);

        assertNotNull("Completed persisted current task should still resolve as Current",
                plugin.getCurrentTask());
        assertEquals("Completed persisted current task should keep accruing timer ticks until the user hits Complete",
                task.getId(), currentTimerTaskId(plugin));
    }

    @Test
    public void completedBeforeRolledCurrentTaskDoesNotStartTimer() throws Exception
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        plugin.setCollectionLogServiceForTesting(new CollectionLogService());

        XtremeTask task = new XtremeTask(
                "collection_log_easy_get-1-unique-from-tempoross_001_test",
                "Get 1 unique from Tempoross",
                TaskSource.COLLECTION_LOG,
                TaskTier.EASY
        );

        PersistedState state = new PersistedState();
        state.setCurrentTaskId(task.getId());
        state.getCompletedBeforeRolledTaskIds().add(task.getId());

        Method applyPersistedState = XtremeTaskerPlugin.class.getDeclaredMethod("applyPersistedState", PersistedState.class);
        applyPersistedState.setAccessible(true);
        applyPersistedState.invoke(plugin, state);

        plugin.tasksForTesting().add(task);

        assertNull("Completed-before-rolled task should not accrue timer ticks",
                currentTimerTaskId(plugin));
    }

    private static String accountNameKey(String characterName)
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        return plugin.accountNameKey(characterName);
    }

    private static String legacyAccountKeyFromScopedKey(String accountKey)
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        return plugin.legacyAccountKeyFromScopedKey(accountKey);
    }

    private static String legacyGeneratedId(XtremeTask task) throws Exception
    {
        Method ensureId = XtremeTaskerPlugin.class.getDeclaredMethod(
                "ensureId",
                String.class,
                String.class,
                TaskSource.class,
                TaskTier.class
        );
        ensureId.setAccessible(true);
        return (String) ensureId.invoke(null, null, task.getName(), task.getSource(), task.getTier());
    }

    private static String currentTimerTaskId(XtremeTaskerPlugin plugin) throws Exception
    {
        Method method = XtremeTaskerPlugin.class.getDeclaredMethod("currentTimerTaskId");
        method.setAccessible(true);
        return (String) method.invoke(plugin);
    }
}
