package com.amtrollin.xtremetasker.tasklist;

import com.amtrollin.xtremetasker.models.XtremeTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class TaskGroupUtils
{
    private TaskGroupUtils()
    {
    }

    public static List<XtremeTask> collapsePreservingOrder(List<XtremeTask> tasks)
    {
        if (tasks == null || tasks.isEmpty())
        {
            return new ArrayList<>();
        }

        Map<String, XtremeTask> firstByKey = new LinkedHashMap<>();
        for (XtremeTask task : tasks)
        {
            if (task != null)
            {
                firstByKey.putIfAbsent(key(task), task);
            }
        }
        return new ArrayList<>(firstByKey.values());
    }

    public static List<XtremeTask> groupFor(List<XtremeTask> allTasks, XtremeTask target)
    {
        List<XtremeTask> out = new ArrayList<>();
        if (allTasks == null || target == null)
        {
            return out;
        }

        String targetKey = key(target);
        for (XtremeTask task : allTasks)
        {
            if (task != null && targetKey.equals(key(task)))
            {
                out.add(task);
            }
        }
        return out;
    }

    public static String key(XtremeTask task)
    {
        if (task == null)
        {
            return "";
        }

        return normalize(task.getName())
                + "|source=" + Objects.toString(task.getSource(), "")
                + "|tier=" + Objects.toString(task.getTier(), "");
    }

    private static String normalize(String value)
    {
        if (value == null)
        {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
