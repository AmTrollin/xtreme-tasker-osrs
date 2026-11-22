package com.amtrollin.xtremetasker.ui.style;

import com.amtrollin.xtremetasker.enums.TaskTier;

import java.awt.Color;

/**
 * Centralized palette for the plugin UI.
 * Keep fields public so renderers/overlay can share a single source of truth.
 */
public final class UiPalette
{
    public static final UiPalette DEFAULT = new UiPalette();

    // Core
    public final Color UI_BG = new Color(38, 30, 20, 245);
    public final Color UI_EDGE_DARK = new Color(18, 14, 9, 240);
    public final Color UI_EDGE_LIGHT = new Color(95, 78, 46, 235);
    public final Color UI_GOLD = new Color(200, 170, 90, 235);
    public final Color UI_TEXT = new Color(235, 225, 195, 255);
    public final Color UI_TEXT_DIM = new Color(200, 190, 160, 200);

    // Tabs
    public final Color TAB_ACTIVE_BG = new Color(70, 55, 33, 240);
    public final Color TAB_INACTIVE_BG = new Color(35, 28, 18, 235);

    // Buttons
    public final Color BTN_ENABLED_BG = new Color(62, 52, 36, 245);
    public final Color BTN_DISABLED_BG = new Color(30, 25, 18, 220);

    // Rows
    public final Color ROW_DONE_BG = new Color(255, 255, 255, 22);
    public final Color ROW_LINE = new Color(255, 255, 255, 18);

    // Completion pip
    public final Color PIP_RING = new Color(255, 255, 255, 120);
    public final Color PIP_DONE_FILL = new Color(200, 170, 90, 220);
    public final Color PIP_DONE_RING = new Color(240, 220, 140, 230);
    public final Color STRIKE_COLOR = new Color(200, 190, 160, 150);

    // Hover + selection
    public final Color ROW_HOVER_BG = new Color(255, 255, 255, 14);
    public final Color ROW_SELECTED_BG = new Color(200, 170, 90, 22);
    public final Color ROW_SELECTED_OUTLINE = new Color(200, 170, 90, 160);

    // Inputs + pills
    public final Color INPUT_BG = new Color(28, 22, 14, 235);
    public final Color INPUT_FOCUS_OUTLINE = new Color(200, 170, 90, 200);
    public final Color PILL_ON_BG = new Color(78, 62, 38, 240);
    public final Color PILL_OFF_BG = new Color(32, 26, 17, 235);

    public static final Color TIER_COMPLETE_GLOW =
            new Color(120, 200, 140);

    public static Color withAlpha(Color color, int alpha)
    {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    public static Color tierColor(TaskTier tier)
    {
        if (tier == null) return new Color(180, 180, 180, 210);
        switch (tier)
        {
            case EASY: return new Color(100, 220, 80, 230);
            case MEDIUM: return new Color(230, 150, 40, 230);
            case HARD: return new Color(220, 70, 60, 230);
            case ELITE: return new Color(160, 35, 35, 230);
            case MASTER:
            case GRANDMASTER: return new Color(170, 80, 210, 230);
            default: return new Color(180, 180, 180, 210);
        }
    }

    private UiPalette() {}
}
