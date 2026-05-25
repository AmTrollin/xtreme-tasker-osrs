package com.amtrollin.xtremetasker.ui.style;

/**
 * Overlay string constants (keep all user-facing strings in one place).
 * Add more as you find literals in the overlay.
 */
public final class UiText
{
    private UiText() {}

    // Icon
    public static final String ICON_LABEL = "XT";

    // Header
    public static final String TITLE = "Xtreme Tasker";

    // Main tabs
    public static final String TAB_CURRENT = "Current";
    public static final String TAB_TASKS = "Tasks";
    public static final String TAB_RULES = "Help";

    // Current tab
    public static final String BTN_COMPLETED = "Completed";
    public static final String BTN_MARK_COMPLETE = "Mark complete";
    public static final String BTN_ROLL_TASK = "Roll task";

    public static final String CURRENT_HINT_KEYS = "Keys: R - roll, C - complete, W - wiki";
    public static final String CURRENT_NONE = "Click \"Roll task\" to get a task";
    public static final String CURRENT_PREFIX_DONE = "Just completed: ";
    public static final String CURRENT_PREFIX_ACTIVE = "Current: ";
    public static final String CURRENT_ROLLING = "Rolling...";
    public static final String CURRENT_ROLLING_PREFIX = "Rolling: ";

    // Tasks tab
    public static final String TASKS_NONE_LOADED = "No tasks loaded.";
    public static final String TASKS_PROGRESS_LABEL_SUFFIX = " progress: ";
    public static final String TASKS_CLICK_HINT = "Click task to toggle done:";
    public static final String TASKS_KEYS_HINT = "Keys: Up/Down - scroll, Space/Enter - toggle, Left/Right - tier";

    // Tier labels (used by prettyTier)
    public static final String TIER_EASY = "Easy";
    public static final String TIER_MEDIUM = "Medium";
    public static final String TIER_HARD = "Hard";
    public static final String TIER_ELITE = "Elite";
    public static final String TIER_MASTER = "Master";
}
