package com.amtrollin.xtremetasker.tasklist.models;

public class TaskListQuery
{
    public String searchText = "";
    public boolean searchFocused = false;
    // Search selection (indices into searchText; -1 = no selection)
    public int searchSelStart = -1;
    public int searchSelEnd = -1;

    // =========================
    // Source filters
    // =========================
    public enum SourceFilter
    {
        ALL,
        CA,
        CLOGS,
        DAS
    }

    public enum StatusFilter
    {
        ALL,
        INCOMPLETE,
        COMPLETE
    }

    public SourceFilter sourceFilter = SourceFilter.ALL;
    public boolean sourceCASelected = true;
    public boolean sourceClogsSelected = true;
    public boolean sourceDasSelected = true;
    public StatusFilter statusFilter = StatusFilter.ALL;

    // TaskListQuery
    public boolean sortByCompletion = false; // enabled/disabled
    public boolean completedFirst = false;   // direction (only meaningful if sortByCompletion == true)

    public boolean sortByTier = false;       // enabled/disabled (only allowed when tierScope == ALL_TIERS)
    public boolean easyTierFirst = true; // direction when sortByTier == true

    public boolean sortByDate = false;       // enabled/disabled (only allowed when statusFilter == COMPLETE)
    public boolean newestFirst = true; // direction when sortByDate == true

    public boolean sortByTimeTicks = false;  // enabled/disabled
    public boolean longestFirst = true;      // direction when sortByTimeTicks == true

    // =========================
    // Optional: compatibility helpers
    // (lets you keep old filter logic temporarily)
    // =========================
    public boolean isFilterCA()
    {
        return isSourceAllSelected() || sourceCASelected;
    }

    public boolean isFilterCL()
    {
        return isSourceAllSelected() || sourceClogsSelected;
    }

    public boolean isFilterDA()
    {
        return isSourceAllSelected() || sourceDasSelected;
    }

    public boolean isSourceAllSelected()
    {
        return sourceCASelected && sourceClogsSelected && sourceDasSelected;
    }

    public void selectAllSources()
    {
        sourceCASelected = true;
        sourceClogsSelected = true;
        sourceDasSelected = true;
        sourceFilter = SourceFilter.ALL;
    }

    public void setOnlySource(SourceFilter source)
    {
        if (source == null || source == SourceFilter.ALL)
        {
            selectAllSources();
            return;
        }

        sourceCASelected = source == SourceFilter.CA;
        sourceClogsSelected = source == SourceFilter.CLOGS;
        sourceDasSelected = source == SourceFilter.DAS;
        sourceFilter = source;
    }

    public boolean toggleSource(SourceFilter source)
    {
        if (source == null)
        {
            return false;
        }

        boolean beforeCA = sourceCASelected;
        boolean beforeClogs = sourceClogsSelected;
        boolean beforeDas = sourceDasSelected;

        if (source == SourceFilter.ALL)
        {
            selectAllSources();
            return beforeCA != sourceCASelected || beforeClogs != sourceClogsSelected || beforeDas != sourceDasSelected;
        }

        if (isSourceAllSelected())
        {
            setOnlySource(source);
            return true;
        }

        switch (source)
        {
            case CA:
                sourceCASelected = !sourceCASelected;
                break;
            case CLOGS:
                sourceClogsSelected = !sourceClogsSelected;
                break;
            case DAS:
                sourceDasSelected = !sourceDasSelected;
                break;
            default:
                break;
        }

        if (!sourceCASelected && !sourceClogsSelected && !sourceDasSelected)
        {
            selectAllSources();
        }
        else if (isSourceAllSelected())
        {
            sourceFilter = SourceFilter.ALL;
        }
        else
        {
            updateLegacySourceFilter();
        }

        return beforeCA != sourceCASelected || beforeClogs != sourceClogsSelected || beforeDas != sourceDasSelected;
    }

    public void updateLegacySourceFilter()
    {
        if (isSourceAllSelected())
        {
            sourceFilter = SourceFilter.ALL;
        }
        else if (sourceCASelected && !sourceClogsSelected && !sourceDasSelected)
        {
            sourceFilter = SourceFilter.CA;
        }
        else if (!sourceCASelected && sourceClogsSelected && !sourceDasSelected)
        {
            sourceFilter = SourceFilter.CLOGS;
        }
        else if (!sourceCASelected && !sourceClogsSelected && sourceDasSelected)
        {
            sourceFilter = SourceFilter.DAS;
        }
        else
        {
            sourceFilter = SourceFilter.ALL;
        }
    }

    public boolean isFilterIncomplete()
    {
        return statusFilter == StatusFilter.ALL || statusFilter == StatusFilter.INCOMPLETE;
    }

    public boolean isFilterComplete()
    {
        return statusFilter == StatusFilter.ALL || statusFilter == StatusFilter.COMPLETE;
    }

    public enum TierScope
    {
        THIS_TIER,
        ALL_TIERS
    }

    public TierScope tierScope = TierScope.THIS_TIER;

    // Session-only: show only new tasks. Cleared when newTaskIds is cleared (logout).
    public boolean showNewTasksFilter = false;
}
