package com.amtrollin.xtremetasker.ui.tasks.models;

import lombok.Getter;

@Getter
public final class CollectionLogRequirementItem
{
    private final int itemId;
    private final String name;
    private final Status status;
    private final boolean dimWhenMissing;

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
        this(itemId, name, status, false);
    }

    public CollectionLogRequirementItem(int itemId, String name, Status status, boolean dimWhenMissing)
    {
        this.itemId = itemId;
        this.name = name;
        this.status = status == null ? Status.MISSING : status;
        this.dimWhenMissing = dimWhenMissing;
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
        return status == Status.OBTAINED || status == Status.READY;
    }

    public boolean isReady()
    {
        return status == Status.READY;
    }

    public enum Status
    {
        MISSING,
        READY,
        OBTAINED,
        APPLIED
    }
}
