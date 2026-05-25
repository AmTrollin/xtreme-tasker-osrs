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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TaskDataTest
{
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
}
