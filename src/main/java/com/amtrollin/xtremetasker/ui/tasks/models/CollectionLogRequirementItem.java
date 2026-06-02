package com.amtrollin.xtremetasker.ui.tasks.models;

public final class CollectionLogRequirementItem
{
    private final String name;
    private final Status status;

    public CollectionLogRequirementItem(String name, boolean obtained)
    {
        this(name, obtained ? Status.OBTAINED : Status.MISSING);
    }

    public CollectionLogRequirementItem(String name, Status status)
    {
        this.name = name;
        this.status = status == null ? Status.MISSING : status;
    }

    public String getName()
    {
        return name;
    }

    public boolean isObtained()
    {
        return status == Status.OBTAINED || status == Status.APPLIED;
    }

    public boolean isApplied()
    {
        return status == Status.APPLIED;
    }

    public boolean isAvailable()
    {
        return status == Status.OBTAINED;
    }

    public Status getStatus()
    {
        return status;
    }

    public enum Status
    {
        MISSING,
        OBTAINED,
        APPLIED
    }
}
