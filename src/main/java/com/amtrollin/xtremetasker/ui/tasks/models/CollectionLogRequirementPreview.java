package com.amtrollin.xtremetasker.ui.tasks.models;

import java.util.List;

public final class CollectionLogRequirementPreview
{
    private final int requiredCount;
    private final int totalCount;
    private final List<CollectionLogRequirementItem> items;

    public CollectionLogRequirementPreview(int requiredCount, int totalCount, List<CollectionLogRequirementItem> items)
    {
        this.requiredCount = requiredCount;
        this.totalCount = totalCount;
        this.items = items == null ? List.of() : items;
    }

    public List<CollectionLogRequirementItem> getItems()
    {
        return items;
    }

    public boolean hasItems()
    {
        return !items.isEmpty();
    }

    public String requirementText()
    {
        if (totalCount <= 1)
        {
            return "Need 1";
        }
        if (requiredCount >= totalCount)
        {
            return totalCount == 2 ? "Need both" : "Need all " + totalCount;
        }
        return "Need " + requiredCount + " of " + totalCount;
    }
}
