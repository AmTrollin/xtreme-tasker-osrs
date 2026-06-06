package com.amtrollin.xtremetasker;

import com.amtrollin.xtremetasker.models.persistence.PersistedState;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PersistenceRegressionTest
{
    @Test
    public void completionDropWithDifferentCurrentTaskIsSuspicious()
    {
        PersistedState previous = stateWithManualCompletions(12, "task_12");
        PersistedState next = stateWithManualCompletions(6, "task_6");

        assertTrue("Changing current task must not allow a stale snapshot to drop completed tasks",
                isSuspiciousCompletionRegression(previous, next));
    }

    @Test
    public void smallCompletionDropIsNotSuspicious()
    {
        PersistedState previous = stateWithManualCompletions(12, "task_12");
        PersistedState next = stateWithManualCompletions(10, "task_10");

        assertFalse("Small completion removals are handled by normal user flows",
                isSuspiciousCompletionRegression(previous, next));
    }

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

    private static PersistedState stateWithManualCompletions(int completedCount, String currentTaskId)
    {
        PersistedState state = new PersistedState();
        Set<String> ids = new HashSet<>();
        for (int i = 1; i <= completedCount; i++)
        {
            ids.add("task_" + i);
        }
        state.setManualCompletedTaskIds(ids);
        state.setCurrentTaskId(currentTaskId);
        return state;
    }

    private static boolean isSuspiciousCompletionRegression(PersistedState previous, PersistedState next)
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        return plugin.isSuspiciousCompletionRegression(previous, next);
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
}
