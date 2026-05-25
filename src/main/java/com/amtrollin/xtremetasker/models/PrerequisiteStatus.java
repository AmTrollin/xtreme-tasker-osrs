package com.amtrollin.xtremetasker.models;

public class PrerequisiteStatus
{
    private final String text;
    private final boolean completed;

    public PrerequisiteStatus(String text, boolean completed)
    {
        this.text = text;
        this.completed = completed;
    }

    public String getText()
    {
        return text;
    }

    public boolean isCompleted()
    {
        return completed;
    }
}
