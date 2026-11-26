package com.amtrollin.xtremetasker.persistence;

import com.amtrollin.xtremetasker.models.persistence.PersistedState;
import com.google.gson.Gson;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PersistedStateStoreTest
{
    private final Gson gson = new Gson();
    // parse() and the recovery heuristics never touch ConfigManager.
    private final PersistedStateStore store = new PersistedStateStore(null, gson);

    @Test
    public void parseRejectsBlankAndMalformedJson()
    {
        assertNull(store.parse(null, "test"));
        assertNull(store.parse("   ", "test"));
        assertNull(store.parse("{not json", "test"));
    }

    @Test
    public void parseRoundTripsValidState()
    {
        PersistedState state = new PersistedState();
        state.setCurrentTaskId("task_a");
        state.getManualCompletedTaskIds().add("task_b");
        state.getTaskTimeTicksById().put("task_a", 12L);
        state.getCollectionLogItemIds().add(11341);

        PersistedState parsed = store.parse(gson.toJson(state), "test");

        assertNotNull(parsed);
        assertEquals("task_a", parsed.getCurrentTaskId());
        assertEquals(Set.of("task_b"), parsed.getManualCompletedTaskIds());
        assertEquals(Long.valueOf(12L), parsed.getTaskTimeTicksById().get("task_a"));
        assertEquals(Set.of(11341), parsed.getCollectionLogItemIds());
    }

    @Test
    public void parseAcceptsMissingCollections()
    {
        assertNotNull(store.parse("{\"manualCompletedTaskIds\":null,\"taskTimeTicksById\":null}", "test"));
    }

    @Test
    public void parseRejectsBlankIds()
    {
        assertNull(store.parse("{\"manualCompletedTaskIds\":[\" \"]}", "test"));
        assertNull(store.parse("{\"syncMismatchTaskIds\":[\"\"]}", "test"));
    }

    @Test
    public void parseRejectsNegativeValues()
    {
        assertNull(store.parse("{\"taskTimeTicksById\":{\"task_a\":-1}}", "test"));
        assertNull(store.parse("{\"combatAchievementLastSyncSeenAtMillis\":-1}", "test"));
        assertNull(store.parse("{\"currentTaskCollectionLogBaselineCount\":-1}", "test"));
    }

    @Test
    public void parseRejectsNonPositiveCollectionLogItemIds()
    {
        assertNull(store.parse("{\"collectionLogItemIds\":[0]}", "test"));
    }

    @Test
    public void timerTickChangesAreNotRecoveryRelevant()
    {
        PersistedState previous = new PersistedState();
        previous.getManualCompletedTaskIds().add("task_b");
        PersistedState next = new PersistedState();
        next.getManualCompletedTaskIds().add("task_b");
        next.getTaskTimeTicksById().put("task_a", 99L);

        assertFalse(PersistedStateStore.isRecoveryRelevantStateChange(previous, next));
    }

    @Test
    public void completionChangesAreRecoveryRelevant()
    {
        PersistedState previous = new PersistedState();
        PersistedState next = new PersistedState();
        next.getManualCompletedTaskIds().add("task_b");

        assertTrue(PersistedStateStore.isRecoveryRelevantStateChange(previous, next));
    }
}
