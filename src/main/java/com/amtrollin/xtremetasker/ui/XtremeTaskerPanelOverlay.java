package com.amtrollin.xtremetasker.ui;

import java.awt.*;
import net.runelite.client.ui.overlay.*;

/**
 * A thin ALWAYS_ON_TOP overlay that delegates panel rendering to XtremeTaskerOverlay.
 * The main overlay (XtremeTaskerOverlay) stays at UNDER_WIDGETS so the XT icon doesn't
 * appear over the game menus; this overlay draws the panel above all widgets.
 */
public class XtremeTaskerPanelOverlay extends Overlay {
    private final XtremeTaskerOverlay base;

    public XtremeTaskerPanelOverlay(XtremeTaskerOverlay base) {
        this.base = base;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
    }

    @Override
    public Dimension render(Graphics2D g) {
        return base.renderPanel(g);
    }
}
