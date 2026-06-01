package com.amtrollin.xtremetasker;

import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.tasklist.TaskListPipeline;
import com.amtrollin.xtremetasker.tasklist.models.TaskListQuery;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TaskDataTest
{
    // Temporary release allowlist for known CLOG entries that still need explicit verification blocks.
    // Keep shrinking this list until it is empty.
    private static final Set<String> KNOWN_CLOG_VERIFICATION_GAPS = Set.of(
            "collection_log_medium_1-graceful-recolor_001_34396fc6b4",
            "collection_log_master_get-1-cox-log-slot_001_e59260645f",
            "collection_log_master_get-1-cox-log-slot_002_353c388cb7",
            "collection_log_master_get-1-cox-log-slot_003_71b29b6c97",
            "collection_log_master_get-1-cox-log-slot_004_22824dfd4e",
            "collection_log_master_get-1-cox-log-slot_005_1e3865ac06",
            "collection_log_master_get-1-cox-log-slot_006_fa04819e49",
            "collection_log_master_get-1-cox-log-slot_007_ad500edf82",
            "collection_log_master_get-1-cox-log-slot_008_cbf224d69a",
            "collection_log_master_get-1-tob-log-slot_001_dcfb7e1d8d",
            "collection_log_master_get-1-tob-log-slot_002_3819822a90",
            "collection_log_master_get-1-tob-log-slot_003_804f2faeb8",
            "collection_log_master_get-1-tob-log-slot_004_c088be6991",
            "collection_log_master_get-1-tob-log-slot_005_c9dd6923b0",
            "collection_log_master_get-1-tob-log-slot_006_d43145bb7c",
            "collection_log_master_get-1-tob-log-slot_007_cb9466f94c",
            "collection_log_master_get-1-toa-log-slot_001_6d92dacb40",
            "collection_log_master_get-1-toa-log-slot_002_17df3889c5",
            "collection_log_master_get-1-toa-log-slot_003_7b6fff0932",
            "collection_log_master_get-1-toa-log-slot_004_38a7a9ab01",
            "collection_log_master_get-1-toa-log-slot_005_5add26e17c",
            "collection_log_master_get-1-toa-log-slot_006_c79b3c7aa2",
            "collection_log_master_get-1-toa-log-slot_007_b687e6ac01",
            "collection_log_master_get-1-toa-log-slot_008_bbf4cf3be3",
            "collection_log_master_get-1-toa-log-slot_009_346a153e9d",
            "collection_log_master_get-1-wildy-unique_001_fb9b585d48",
            "collection_log_master_get-1-wildy-unique_002_424f53f7fa",
            "collection_log_master_get-1-wildy-unique_003_5dc6b41f38",
            "collection_log_master_get-1-wildy-unique_004_1dc61ee7fa",
            "collection_log_master_get-1-wildy-unique_005_852bea39e4",
            "collection_log_master_get-1-wildy-unique_006_f946294fb6",
            "collection_log_master_get-1-wildy-unique_007_af69a6f0f0",
            "collection_log_master_get-1-wildy-unique_008_55a8c90903",
            "collection_log_master_get-1-wildy-unique_009_1e5f4ef2c5",
            "collection_log_master_get-1-wildy-unique_010_646ed2dbe8"
    );

    @Test
    public void taskPackHasValidReleaseShape()
    {
        JsonObject pack = loadTaskPack();

        assertTrue("tasks.json version must be positive", pack.get("version").getAsInt() > 0);
        assertTrue("tasks.json must include a tasks array", pack.get("tasks").isJsonArray());

        JsonArray tasks = pack.getAsJsonArray("tasks");
        assertTrue("tasks.json must include tasks", tasks.size() > 0);

        Set<String> ids = new HashSet<>();
        for (int i = 0; i < tasks.size(); i++)
        {
            JsonObject task = tasks.get(i).getAsJsonObject();

            String id = requiredString(task, "id", i);
            requiredString(task, "name", i);
            String source = requiredString(task, "source", i);
            String tier = requiredString(task, "tier", i);

            assertTrue("duplicate task id: " + id, ids.add(id));
            assertTrue("invalid source for task " + id, "COLLECTION_LOG".equals(source) || "COMBAT_ACHIEVEMENT".equals(source));
            assertTrue("invalid tier for task " + id,
                    "EASY".equals(tier)
                            || "MEDIUM".equals(tier)
                            || "HARD".equals(tier)
                            || "ELITE".equals(tier)
                            || "MASTER".equals(tier)
                            || "GRANDMASTER".equals(tier));
        }
    }

    @Test
    public void taskSearchMatchesTwoCharacterTermsInAnyOrder()
    {
        List<XtremeTask> tasks = Arrays.asList(
                new XtremeTask("cape", "1 level 99 cape", TaskSource.COLLECTION_LOG, TaskTier.MASTER),
                new XtremeTask("other", "Get bolt racks from Barrows", TaskSource.COLLECTION_LOG, TaskTier.EASY)
        );

        TaskListQuery query = new TaskListQuery();
        query.searchText = "99";
        List<XtremeTask> results = TaskListPipeline.apply(tasks, query, task -> false);
        assertEquals(1, results.size());
        assertEquals("cape", results.get(0).getId());

        query.searchText = "99 level";
        List<XtremeTask> reversedResults = TaskListPipeline.apply(tasks, query, task -> false);
        assertEquals(1, reversedResults.size());
        assertEquals("cape", reversedResults.get(0).getId());

        query.searchText = "level 99";
        List<XtremeTask> orderedResults = TaskListPipeline.apply(tasks, query, task -> false);
        assertEquals(reversedResults.get(0).getId(), orderedResults.get(0).getId());
    }

    @Test
    public void collectionLogTasksHaveVerificationCoverage()
    {
        JsonObject pack = loadTaskPack();
        JsonArray tasks = pack.getAsJsonArray("tasks");

        List<String> missingCoverage = new ArrayList<>();
        for (JsonElement taskElement : tasks)
        {
            JsonObject task = taskElement.getAsJsonObject();
            if (!"COLLECTION_LOG".equals(optionalString(task, "source")))
            {
                continue;
            }

            String taskId = optionalString(task, "id");
            JsonObject verification = task.has("verification") && task.get("verification").isJsonObject()
                    ? task.getAsJsonObject("verification")
                    : null;

            if (verification == null)
            {
                missingCoverage.add(taskId + " (missing verification)");
                continue;
            }

            String method = optionalString(verification, "method");
            if ("collection-log".equals(method))
            {
                boolean hasItemIds = verification.has("itemIds")
                        && verification.get("itemIds").isJsonArray()
                        && verification.getAsJsonArray("itemIds").size() > 0;
                if (!hasItemIds)
                {
                    missingCoverage.add(taskId + " (collection-log missing itemIds)");
                }
            }
        }

        Set<String> actualMissingIds = missingCoverage.stream()
                .map(entry -> entry.split(" ")[0])
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> unexpectedMissing = new HashSet<>(actualMissingIds);
        unexpectedMissing.removeAll(KNOWN_CLOG_VERIFICATION_GAPS);

        Set<String> staleAllowlist = new HashSet<>(KNOWN_CLOG_VERIFICATION_GAPS);
        staleAllowlist.removeAll(actualMissingIds);

        assertTrue(
                "Unexpected CLOG verification gaps found: " + unexpectedMissing
                        + "\nCurrent gaps: " + missingCoverage,
                unexpectedMissing.isEmpty());
        assertTrue(
                "KNOWN_CLOG_VERIFICATION_GAPS has stale IDs (remove these): " + staleAllowlist,
                staleAllowlist.isEmpty());
    }

    private static JsonObject loadTaskPack()
    {
        InputStream in = TaskDataTest.class.getClassLoader().getResourceAsStream("task_data/tasks.json");
        assertNotNull("task_data/tasks.json must be bundled as a test resource", in);

        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
        {
            JsonElement root = new JsonParser().parse(reader);
            assertTrue("tasks.json root must be an object", root.isJsonObject());
            return root.getAsJsonObject();
        }
        catch (Exception e)
        {
            throw new AssertionError("tasks.json must parse as JSON", e);
        }
    }

    private static String requiredString(JsonObject task, String key, int index)
    {
        assertTrue("task " + index + " missing " + key,
                task.has(key) && !task.get(key).isJsonNull() && !task.get(key).getAsString().trim().isEmpty());
        return task.get(key).getAsString();
    }

    private static String optionalString(JsonObject task, String key)
    {
        if (task == null || !task.has(key) || task.get(key).isJsonNull())
        {
            return null;
        }

        String value = task.get(key).getAsString();
        return value == null ? null : value.trim();
    }
}
