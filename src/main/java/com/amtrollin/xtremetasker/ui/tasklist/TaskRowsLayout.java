package com.amtrollin.xtremetasker.ui.tasklist;

import com.amtrollin.xtremetasker.models.XtremeTask;

import java.awt.Rectangle;
import java.util.HashMap;
import java.util.Map;

public final class TaskRowsLayout
{
    public final Rectangle viewportBounds = new Rectangle();
    public final Rectangle scrollbarRailBounds = new Rectangle();
    public final Rectangle scrollbarThumbBounds = new Rectangle();

    /** Full clickable row bounds (used for selection + row click). */
    public final Map<XtremeTask, Rectangle> rowBounds = new HashMap<>();

    /** Click target for the checkbox/pip area (used to toggle). */
    public final Map<XtremeTask, Rectangle> checkboxBounds = new HashMap<>();
}
