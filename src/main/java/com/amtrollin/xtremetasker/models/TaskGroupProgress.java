package com.amtrollin.xtremetasker.models;

import lombok.Getter;

@Getter
public final class TaskGroupProgress
{
    private final int completed;
    private final int total;

    public TaskGroupProgress(int completed, int total)
    {
        this.completed = Math.max(0, completed);
        this.total = Math.max(1, total);
    }

    public boolean isGrouped()
    {
        return total > 1;
    }

    public boolean isComplete()
    {
        return completed >= total;
    }

    public String label()
    {
        return "(" + completed + "/" + total + ")";
    }
}
