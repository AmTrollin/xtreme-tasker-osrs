package com.amtrollin.xtremetasker.ui.tasklist;

import com.amtrollin.xtremetasker.enums.TaskTier;
import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.tasklist.TaskGroupUtils;
import lombok.Getter;

import java.util.EnumMap;
import java.util.List;
import java.util.function.Function;

public final class TaskSelectionModel implements SelectionModel
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

    @Override
    public int getSelectedIndex()
    {
        Integer idx = selectedIndexByTier.get(activeTier);
        return idx == null ? 0 : idx;
    }

    @Override
    public void setSelectedIndex(int idx)
    {
        selectedIndexByTier.put(activeTier, Math.max(0, idx));
    }

    public void normalizeForTier(TaskTier tier,
                                 List<XtremeTask> tasks,
                                 boolean completedFirst,
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
            // default: first incomplete if incomplete-first sorting; otherwise first row
            int start = 0;
            if (!completedFirst && isCompleted != null)
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

    public void moveUp(int count)
    {
        if (count <= 0)
        {
            setSelectedIndex(0);
            return;
        }
        setSelectedIndex(clamp(getSelectedIndex() - 1, count - 1));
    }

    public void moveDown(int count)
    {
        if (count <= 0)
        {
            setSelectedIndex(0);
            return;
        }
        setSelectedIndex(clamp(getSelectedIndex() + 1, count - 1));
    }

    public void pageUp(int count, int page)
    {
        if (count <= 0)
        {
            setSelectedIndex(0);
            return;
        }
        setSelectedIndex(clamp(getSelectedIndex() - page, count - 1));
    }

    public void pageDown(int count, int page)
    {
        if (count <= 0)
        {
            setSelectedIndex(0);
            return;
        }
        setSelectedIndex(clamp(getSelectedIndex() + page, count - 1));
    }

    private static int clamp(int v, int max)
    {
        return Math.max(0, Math.min(max, v));
    }
}
