package com.amtrollin.xtremetasker.models;

public final class TaskGroupProgress
{
    private final int completed;
    private final int total;

    public TaskGroupProgress(int completed, int total)
    {
        this.completed = Math.max(0, completed);
        this.total = Math.max(1, total);
    }

    public int getCompleted()
    {
        return completed;
    }

    public int getTotal()
    {
        return total;
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
