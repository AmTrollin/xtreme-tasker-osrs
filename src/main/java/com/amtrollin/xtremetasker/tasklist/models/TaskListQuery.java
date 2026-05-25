package com.amtrollin.xtremetasker.tasklist.models;

public class TaskListQuery
{
    public String searchText = "";
    public boolean searchFocused = false;
    // Search selection (indices into searchText; -1 = no selection)
    public int searchSelStart = -1;
    public int searchSelEnd = -1;

    // =========================
    // Single-select filters
    // =========================
    public enum SourceFilter
    {
        ALL,
        CA,
        CLOGS
    }

    public enum StatusFilter
    {
        ALL,
        INCOMPLETE,
        COMPLETE
    }

    public SourceFilter sourceFilter = SourceFilter.ALL;
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
        return sourceFilter == SourceFilter.ALL || sourceFilter == SourceFilter.CA;
    }

    public boolean isFilterCL()
    {
        return sourceFilter == SourceFilter.ALL || sourceFilter == SourceFilter.CLOGS;
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
