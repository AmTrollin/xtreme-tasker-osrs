package com.amtrollin.xtremetasker.tasklist;

import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.tasklist.models.TaskListQuery;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TaskListSorterTest
{
    @Test
    public void timeSortPutsUnknownTicksAfterValuesAscending()
    {
        XtremeTask zero = task("zero", "Zero ticks");
        XtremeTask five = task("five", "Five ticks");
        XtremeTask nullTicks = task("null", "Null ticks");
        XtremeTask ten = task("ten", "Ten ticks");

        TaskListQuery query = new TaskListQuery();
        query.sortByTimeTicks = true;
        query.longestFirst = false;

        List<XtremeTask> tasks = new ArrayList<>(Arrays.asList(zero, ten, nullTicks, five));
        tasks.sort(TaskListSorter.comparator(query, task -> false, null, TaskListSorterTest::ticks));

        assertEquals(five, tasks.get(0));
        assertEquals(ten, tasks.get(1));
        assertTrue(tasks.subList(2, 4).contains(zero));
        assertTrue(tasks.subList(2, 4).contains(nullTicks));
    }

    @Test
    public void timeSortPutsUnknownTicksAfterValuesDescending()
    {
        XtremeTask zero = task("zero", "Zero ticks");
        XtremeTask five = task("five", "Five ticks");
        XtremeTask nullTicks = task("null", "Null ticks");
        XtremeTask ten = task("ten", "Ten ticks");

        TaskListQuery query = new TaskListQuery();
        query.sortByTimeTicks = true;
        query.longestFirst = true;

        List<XtremeTask> tasks = new ArrayList<>(Arrays.asList(zero, five, nullTicks, ten));
        tasks.sort(TaskListSorter.comparator(query, task -> false, null, TaskListSorterTest::ticks));

        assertEquals(ten, tasks.get(0));
        assertEquals(five, tasks.get(1));
        assertTrue(tasks.subList(2, 4).contains(zero));
        assertTrue(tasks.subList(2, 4).contains(nullTicks));
    }

    private static XtremeTask task(String id, String name)
    {
        return new XtremeTask(id, name, TaskSource.COLLECTION_LOG, TaskTier.EASY);
    }

    private static Long ticks(XtremeTask task)
    {
        if (task == null)
        {
            return null;
        }
        if ("five".equals(task.getId()))
        {
            return 5L;
        }
        if ("ten".equals(task.getId()))
        {
            return 10L;
        }
        if ("zero".equals(task.getId()))
        {
            return 0L;
        }
        return null;
    }
}
