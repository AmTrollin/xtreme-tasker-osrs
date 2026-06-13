package com.amtrollin.xtremetasker.ui.tasks.models;

public final class CollectionLogRequirementItem
{
    private final int itemId;
    private final String name;
    private final Status status;

    public CollectionLogRequirementItem(String name, boolean obtained)
    {
        this(-1, name, obtained ? Status.OBTAINED : Status.MISSING);
    }

    public CollectionLogRequirementItem(String name, Status status)
    {
        this(-1, name, status);
    }

    public CollectionLogRequirementItem(int itemId, String name, Status status)
    {
        this.itemId = itemId;
        this.name = name;
        this.status = status == null ? Status.MISSING : status;
    }

    public int getItemId()
    {
        return itemId;
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
