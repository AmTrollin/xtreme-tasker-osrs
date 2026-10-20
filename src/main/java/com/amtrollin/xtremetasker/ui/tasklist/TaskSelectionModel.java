package com.amtrollin.xtremetasker.ui.tasklist;

import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.tasklist.TaskGroupUtils;
import java.util.*;
import java.util.function.Function;
import lombok.Getter;

public final class TaskSelectionModel
{
    private final EnumMap<TaskTier, Integer> selectedIndexByTier = new EnumMap<>(TaskTier.class);

    @Getter
    private TaskTier activeTier = TaskTier.EASY;

    public void setActiveTier(TaskTier tier)
    {
        if (tier != null)
        {
            activeTier = tier;
        }
    }

    public int getSelectedIndex()
    {
        Integer idx = selectedIndexByTier.get(activeTier);
        return idx == null ? 0 : idx;
    }

    public void setSelectedIndex(int idx)
    {
        selectedIndexByTier.put(activeTier, Math.max(0, idx));
    }

    public void normalizeForTier(TaskTier tier,
                                 List<XtremeTask> tasks,
                                 Function<XtremeTask, Boolean> isCompleted)
    {
        if (tier == null)
        {
            return;
        }
        setActiveTier(tier);

        if (tasks == null || tasks.isEmpty())
        {
            selectedIndexByTier.put(tier, 0);
            return;
        }

        Integer existing = selectedIndexByTier.get(tier);
        if (existing == null)
        {
            int start = 0;
            if (isCompleted != null)
            {
                for (int i = 0; i < tasks.size(); i++)
                {
                    Boolean done = isCompleted.apply(tasks.get(i));
                    if (done == null || !done)
                    {
                        start = i;
                        break;
                    }
                }
            }
            selectedIndexByTier.put(tier, clamp(start, tasks.size() - 1));
        }
        else
        {
            selectedIndexByTier.put(tier, clamp(existing, tasks.size() - 1));
        }
    }

    public void setSelectionToTask(TaskTier tier, List<XtremeTask> tasks, XtremeTask target)
    {
        if (tier == null || tasks == null || target == null)
        {
            return;
        }

        setActiveTier(tier);
        String id = target.getId();
        if (id == null)
        {
            return;
        }

        for (int i = 0; i < tasks.size(); i++)
        {
            XtremeTask t = tasks.get(i);
            if (t != null && id.equals(t.getId()))
            {
                selectedIndexByTier.put(tier, i);
                return;
            }
        }

        String groupKey = TaskGroupUtils.key(target);
        for (int i = 0; i < tasks.size(); i++)
        {
            XtremeTask t = tasks.get(i);
            if (t != null && groupKey.equals(TaskGroupUtils.key(t)))
            {
                selectedIndexByTier.put(tier, i);
                return;
            }
        }
    }

    private static int clamp(int v, int max)
    {
        return Math.max(0, Math.min(max, v));
    }
}
