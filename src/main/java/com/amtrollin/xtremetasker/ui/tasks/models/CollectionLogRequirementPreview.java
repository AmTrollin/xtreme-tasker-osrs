package com.amtrollin.xtremetasker.ui.tasks.models;

import com.amtrollin.xtremetasker.enums.TaskTier;

import java.util.ArrayList;
import java.util.Comparator;
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
    private final List<TierSection> tierSections;

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
        this(summaryText, titleText, showSummaryText, showItemList, items, iconColumns,
                secondaryTitleText, secondaryItems, secondaryIconColumns, null);
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
            int secondaryIconColumns,
            List<TierSection> tierSections)
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
        this.tierSections = tierSections == null ? List.of() : List.copyOf(tierSections);
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

    public boolean showTierSections()
    {
        return !tierSections.isEmpty();
    }

    public List<TierSection> tierSections()
    {
        return tierSections;
    }

    public TierSection currentTierSection()
    {
        for (TierSection section : tierSections)
        {
            if (section != null && section.currentTier() && !section.items().isEmpty())
            {
                return section;
            }
        }
        for (TierSection section : tierSections)
        {
            if (section != null && !section.items().isEmpty())
            {
                return section;
            }
        }
        return null;
    }

    public List<TierSection> otherTierSectionsHardestFirst()
    {
        TierSection current = currentTierSection();
        List<TierSection> sections = new ArrayList<>();
        for (TierSection section : tierSections)
        {
            if (section != null && section != current && !section.items().isEmpty())
            {
                sections.add(section);
            }
        }
        sections.sort(Comparator.comparingInt((TierSection section) -> tierSortRank(section.tier())).reversed());
        return sections;
    }

    public int iconColumns()
    {
        return iconColumns;
    }

    public int secondaryIconColumns()
    {
        return secondaryIconColumns;
    }

    private static int tierSortRank(TaskTier tier)
    {
        return tier == null ? -1 : tier.ordinal();
    }

    public static final class TierSection
    {
        private final TaskTier tier;
        private final boolean currentTier;
        private final boolean workingTier;
        private final List<CollectionLogRequirementItem> items;
        private final int iconColumns;

        public TierSection(TaskTier tier, boolean currentTier, List<CollectionLogRequirementItem> items, int iconColumns)
        {
            this(tier, currentTier, currentTier, items, iconColumns);
        }

        public TierSection(TaskTier tier, boolean currentTier, boolean workingTier, List<CollectionLogRequirementItem> items, int iconColumns)
        {
            this.tier = tier;
            this.currentTier = currentTier;
            this.workingTier = workingTier;
            this.items = items == null ? List.of() : List.copyOf(items);
            this.iconColumns = Math.max(1, iconColumns);
        }

        public TaskTier tier()
        {
            return tier;
        }

        public boolean currentTier()
        {
            return currentTier;
        }

        public boolean workingTier()
        {
            return workingTier;
        }

        public List<CollectionLogRequirementItem> items()
        {
            return items;
        }

        public int iconColumns()
        {
            return iconColumns;
        }
    }
}
