package com.amtrollin.xtremetasker.ui.current;

import java.awt.Rectangle;

public final class CurrentTabLayout
{
    public final Rectangle wikiButtonBounds = new Rectangle();
    public final Rectangle rollButtonBounds = new Rectangle();
    public final Rectangle completeButtonBounds = new Rectangle();
    public final Rectangle rollSourceIconBounds = new Rectangle(); // "?" icon for hover tooltip
    public final Rectangle viewportBounds = new Rectangle();       // scrollable content area
    public int totalContentPx = 0;                                 // total scrollable height in pixels
}
