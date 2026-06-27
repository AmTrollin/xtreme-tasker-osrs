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

    public enum SortColumn
    {
        DATE,
        SPENT
    }

    public enum SortDirection
    {
        OFF,
        ASC,
        DESC
    }

    public boolean sourceCASelected = true;
    public boolean sourceClogsSelected = true;
    public boolean sourceDasSelected = true;
    public StatusFilter statusFilter = StatusFilter.ALL;
    public SortColumn sortColumn = SortColumn.DATE;
    public SortDirection sortDirection = SortDirection.OFF;
    public boolean showDateCompletedColumn = true;
    public boolean showTimeSpentColumn = true;

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
    }

    public void setCollectionLogAndDiarySources()
    {
        sourceCASelected = false;
        sourceClogsSelected = true;
        sourceDasSelected = true;
    }

    public boolean toggleSort(SortColumn column)
    {
        if (column == null)
        {
            return false;
        }

        if (sortColumn == column)
        {
            switch (sortDirection)
            {
                case OFF:
                    sortDirection = SortDirection.ASC;
                    break;
                case ASC:
                    sortDirection = SortDirection.DESC;
                    break;
                case DESC:
                default:
                    sortDirection = SortDirection.OFF;
                    break;
            }
            return true;
        }

        sortColumn = column;
        sortDirection = SortDirection.ASC;
        return true;
    }

    public boolean isColumnVisible(SortColumn column)
    {
        if (column == null)
        {
            return false;
        }
        switch (column)
        {
            case DATE:
                return showDateCompletedColumn;
            case SPENT:
                return showTimeSpentColumn;
            default:
                return true;
        }
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
        return beforeCA != sourceCASelected || beforeClogs != sourceClogsSelected || beforeDas != sourceDasSelected;
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
