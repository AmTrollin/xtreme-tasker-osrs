package com.amtrollin.xtremetasker.ui.tasks.models;

public final class CollectionLogRequirementItem
{
    private final String name;
    private final boolean obtained;

    public CollectionLogRequirementItem(String name, boolean obtained)
    {
        this.name = name;
        this.obtained = obtained;
    }

    public String getName()
    {
        return name;
    }

    public boolean isObtained()
    {
        return obtained;
    }
}
