package com.amtrollin.xtremetasker.ui.tasks.models;

import java.util.List;

public final class CollectionLogRequirementPreview
{
    private final String summaryText;
    private final String titleText;
    private final boolean showSummaryText;
    private final boolean showItemList;
    private final List<CollectionLogRequirementItem> items;
    private final int iconColumns;
    private final String secondaryTitleText;
    private final List<CollectionLogRequirementItem> secondaryItems;
    private final int secondaryIconColumns;

    public CollectionLogRequirementPreview(String summaryText, boolean showSummaryText, boolean showItemList, List<CollectionLogRequirementItem> items)
    {
        this(summaryText, null, showSummaryText, showItemList, items);
    }

    public CollectionLogRequirementPreview(String summaryText, String titleText, boolean showSummaryText, boolean showItemList, List<CollectionLogRequirementItem> items)
    {
        this(summaryText, titleText, showSummaryText, showItemList, items, 8);
    }

    public CollectionLogRequirementPreview(String summaryText, String titleText, boolean showSummaryText, boolean showItemList, List<CollectionLogRequirementItem> items, int iconColumns)
    {
        this(summaryText, titleText, showSummaryText, showItemList, items, iconColumns, null, null, 1);
    }

    public CollectionLogRequirementPreview(
            String summaryText,
            String titleText,
            boolean showSummaryText,
            boolean showItemList,
            List<CollectionLogRequirementItem> items,
            int iconColumns,
            String secondaryTitleText,
            List<CollectionLogRequirementItem> secondaryItems,
            int secondaryIconColumns)
    {
        this.summaryText = summaryText == null ? "" : summaryText;
        this.titleText = titleText == null ? "" : titleText;
        this.showSummaryText = showSummaryText;
        this.showItemList = showItemList;
        this.items = items == null ? List.of() : items;
        this.iconColumns = Math.max(1, iconColumns);
        this.secondaryTitleText = secondaryTitleText == null ? "" : secondaryTitleText;
        this.secondaryItems = secondaryItems == null ? List.of() : secondaryItems;
        this.secondaryIconColumns = Math.max(1, secondaryIconColumns);
    }

    public List<CollectionLogRequirementItem> getItems()
    {
        return items;
    }

    public boolean hasItems()
    {
        return showSummaryText() || showItemList() || showSecondaryItemList();
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

    public String titleText()
    {
        return titleText;
    }

    public String secondaryTitleText()
    {
        return secondaryTitleText;
    }

    public List<CollectionLogRequirementItem> secondaryItems()
    {
        return secondaryItems;
    }

    public boolean showSecondaryItemList()
    {
        return !secondaryItems.isEmpty();
    }

    public int iconColumns()
    {
        return iconColumns;
    }

    public int secondaryIconColumns()
    {
        return secondaryIconColumns;
    }
}
