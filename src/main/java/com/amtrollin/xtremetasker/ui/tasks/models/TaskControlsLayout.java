package com.amtrollin.xtremetasker.ui.tasks.models;

import java.awt.Rectangle;

public class TaskControlsLayout
{
    public final Rectangle searchBox = new Rectangle();

    // Static section labels; bounds stay empty so headers are not clickable.
    public final Rectangle filtersHeaderBounds = new Rectangle();
    public boolean filtersExpanded = true;

    // Source (single select): ALL / CA / CLOGs / ADs
    public final Rectangle filterSourceAll = new Rectangle();
    public final Rectangle filterCA = new Rectangle();
    public final Rectangle filterCL = new Rectangle();
    public final Rectangle filterDA = new Rectangle();

    // Status (single select): ALL / Incomplete / Complete
    public final Rectangle filterStatusAll = new Rectangle();
    public final Rectangle filterIncomplete = new Rectangle();
    public final Rectangle filterComplete = new Rectangle();

    public final Rectangle filterTierThis = new Rectangle();
    public final Rectangle filterTierAll = new Rectangle();

    public boolean showDateColumn = true;
    public boolean showTimeColumn = true;
    public boolean showTierColumn = true;
    public boolean showSourceColumn = true;
    public final Rectangle columnDate = new Rectangle();
    public final Rectangle columnTime = new Rectangle();
    public final Rectangle columnTier = new Rectangle();
    public final Rectangle columnSource = new Rectangle();

    public final Rectangle sortDate = new Rectangle();
    public final Rectangle sortTimeTicks = new Rectangle();
    public final Rectangle sortTier = new Rectangle();
    public final Rectangle sortSource = new Rectangle();
    public final Rectangle clearFilters = new Rectangle();

    // Session-only: visible only when there are new tasks this session
    public final Rectangle filterNewTasks = new Rectangle();
    public final Rectangle filterNewTasksHelp = new Rectangle();

    // Search text rendering aids — populated each frame by TaskControlsRenderer
    public int searchTextX = 0;
    public int[] searchCharXPositions = new int[0];
}
