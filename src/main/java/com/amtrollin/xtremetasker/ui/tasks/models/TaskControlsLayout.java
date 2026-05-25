package com.amtrollin.xtremetasker.ui.tasks.models;

import java.awt.Rectangle;

public class TaskControlsLayout
{
    public final Rectangle searchBox = new Rectangle();

    // Expand/collapse headers (clickable)
    public final Rectangle filtersHeaderBounds = new Rectangle();
    public final Rectangle sortHeaderBounds = new Rectangle();
    public boolean filtersExpanded = false;
    public boolean sortExpanded = false;

    // Source (single select): ALL / CA / CLOGs
    public final Rectangle filterSourceAll = new Rectangle();
    public final Rectangle filterCA = new Rectangle();
    public final Rectangle filterCL = new Rectangle();

    // Status (single select): ALL / Incomplete / Complete
    public final Rectangle filterStatusAll = new Rectangle();
    public final Rectangle filterIncomplete = new Rectangle();
    public final Rectangle filterComplete = new Rectangle();

    public final Rectangle filterTierThis = new Rectangle();
    public final Rectangle filterTierAll = new Rectangle();

    public final Rectangle sortCompletion = new Rectangle();
    public final Rectangle sortTier = new Rectangle();
    public final Rectangle sortDate = new Rectangle();
    public final Rectangle sortTimeTicks = new Rectangle();
    public final Rectangle sortReset = new Rectangle();
    public final Rectangle clearFilters = new Rectangle();
    public final Rectangle clearSort = new Rectangle();
    public String hoverTooltipText = null;
    public final Rectangle hoverTooltipAnchor = new Rectangle(); // optional

    // Session-only: visible only when there are new tasks this session
    public final Rectangle filterNewTasks = new Rectangle();
    public final Rectangle filterNewTasksHelp = new Rectangle();

    // Search text rendering aids — populated each frame by TaskControlsRenderer
    public int searchTextX = 0;
    public int[] searchCharXPositions = new int[0];
}
