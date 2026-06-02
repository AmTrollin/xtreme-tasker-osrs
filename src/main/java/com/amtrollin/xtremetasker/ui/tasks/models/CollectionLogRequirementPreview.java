package com.amtrollin.xtremetasker.ui.tasks.models;

import java.util.List;

public final class CollectionLogRequirementPreview
{
    private final String summaryText;
    private final String currentProgressText;
    private final boolean showSummaryText;
    private final boolean showItemList;
    private final List<CollectionLogRequirementItem> items;

    public CollectionLogRequirementPreview(String summaryText, boolean showSummaryText, boolean showItemList, List<CollectionLogRequirementItem> items)
    {
        this(summaryText, "", showSummaryText, showItemList, items);
    }

    public CollectionLogRequirementPreview(String summaryText, String currentProgressText, boolean showSummaryText, boolean showItemList, List<CollectionLogRequirementItem> items)
    {
        this.summaryText = summaryText == null ? "" : summaryText;
        this.currentProgressText = currentProgressText == null ? "" : currentProgressText;
        this.showSummaryText = showSummaryText;
        this.showItemList = showItemList;
        this.items = items == null ? List.of() : items;
    }

    public List<CollectionLogRequirementItem> getItems()
    {
        return items;
    }

    public boolean hasItems()
    {
        return showSummaryText() || showCurrentProgressText() || showItemList();
    }

    public boolean showItemList()
    {
        return showItemList && !items.isEmpty();
    }

    public boolean showSummaryText()
    {
        return showSummaryText && !summaryText.isEmpty();
    }

    public String summaryText()
    {
        return summaryText;
    }

    public boolean showCurrentProgressText()
    {
        return !currentProgressText.isEmpty();
    }

    public String currentProgressText()
    {
        return currentProgressText;
    }
}
