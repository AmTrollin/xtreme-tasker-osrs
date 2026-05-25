package com.amtrollin.xtremetasker.ui.tasklist;

public final class TaskListScrollController
{
    private final int scrollRowsPerNotch;

    public int offsetRows = 0;
    public double wheelRemainderRows = 0.0;
    private int suppressEnsureTicks = 0;


    public TaskListScrollController(int scrollRowsPerNotch)
    {
        this.scrollRowsPerNotch = Math.max(1, scrollRowsPerNotch);
    }

    public int visibleRows(int viewportH, int rowBlock)
    {
        if (rowBlock <= 0 || viewportH <= 0)
        {
            return 0;
        }
        return Math.max(0, viewportH / rowBlock);
    }

    /**
     * Apply wheel scroll in "rows". Keeps remainder for smooth trackpads.
     * Returns the new offset.
     */
    public int onWheel(double preciseWheelRotation, int viewportH, int rowBlock, int totalRows, SelectionModel selection)
    {
        if (preciseWheelRotation == 0.0 || viewportH <= 0 || rowBlock <= 0 || totalRows <= 0)
        {
            return offsetRows;
        }

        int visible = visibleRows(viewportH, rowBlock);
        int maxOffset = Math.max(0, totalRows - visible);

        double rows = (preciseWheelRotation * scrollRowsPerNotch) + wheelRemainderRows;
        int deltaRows = (rows > 0) ? (int) Math.floor(rows) : (int) Math.ceil(rows);
        wheelRemainderRows = rows - deltaRows;

        if (deltaRows == 0)
        {
            return offsetRows;
        }

        offsetRows = clamp(offsetRows + deltaRows, maxOffset);
        // suppress ensureSelectionVisible for a few frames/ticks so scroll doesn't snap back
        suppressEnsureTicks = 6; // tune: 4-10 is fine
        return offsetRows;
    }

    /**
     * Ensures selected index is within visible window; updates offsetRows if needed.
     */
    public void ensureSelectionVisible(int totalRows, int viewportH, int rowBlock, SelectionModel selection)
    {
        if (suppressEnsureTicks > 0)
        {
            suppressEnsureTicks--;
            return;
        }

        if (selection == null || totalRows <= 0)
        {
            return;
        }

        int visible = visibleRows(viewportH, rowBlock);
        if (visible <= 0)
        {
            return;
        }

        int maxOffset = Math.max(0, totalRows - visible);

        int sel = clamp(selection.getSelectedIndex(), totalRows - 1);
        selection.setSelectedIndex(sel);

        if (sel < offsetRows)
        {
            offsetRows = sel;
        }
        else if (sel >= offsetRows + visible)
        {
            offsetRows = sel - visible + 1;
        }

        offsetRows = clamp(offsetRows, maxOffset);
    }

    public int setOffsetRows(int nextOffsetRows, int viewportH, int rowBlock, int totalRows)
    {
        int visible = visibleRows(viewportH, rowBlock);
        int maxOffset = Math.max(0, totalRows - visible);
        offsetRows = clamp(nextOffsetRows, maxOffset);
        wheelRemainderRows = 0.0;
        suppressEnsureTicks = 6;
        return offsetRows;
    }



    public void reset()
    {
        offsetRows = 0;
        wheelRemainderRows = 0.0;
    }

    private static int clamp(int v, int max)
    {
        return Math.max(0, Math.min(max, v));
    }
}
