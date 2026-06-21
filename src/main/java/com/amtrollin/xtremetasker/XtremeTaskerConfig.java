package com.amtrollin.xtremetasker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("xtremetasker")
public interface XtremeTaskerConfig extends Config {

    enum RollSourceFilter {
        ALL("All tasks"),
        CA_ONLY("CAs only"),
        CLOG_ONLY("CLOGS + ADs only");

        private final String label;
        RollSourceFilter(String label) { this.label = label; }

        @Override
        public String toString() { return label; }
    }
    @ConfigItem(
            keyName = "showTaskHud",
            name = "Show task HUD",
            description = "Show a small overlay in the top-left corner with your current task"
    )
    default boolean showTaskHud() {
        return true;
    }

    @ConfigItem(
            keyName = "rollSourceFilter",
            name = "Roll source",
            description = "Restrict random rolls to only Combat Achievement tasks, Collection Log and Achievement Diary tasks, or all tasks"
    )
    default RollSourceFilter rollSourceFilter() {
        return RollSourceFilter.ALL;
    }

    @ConfigItem(
            keyName = "condenseRepeatedTasks",
            name = "Condense repeated tasks",
            description = "Condense repeated task rolls into one row with grouped completion controls. You can also separate repeated tasks from the Tasks tab."
    )
    default boolean condenseRepeatedTasks() {
        return true;
    }

    @ConfigItem(
            keyName = "enableTaskSkipping",
            name = "Enable task skipping",
            description = "Show a Skip button on the Current task page. Skipping rerolls your current task and increments your skipped task count."
    )
    default boolean enableTaskSkipping() {
        return false;
    }

    @ConfigItem(
            keyName = "showSyncTestTools",
            name = "Show sync test tools",
            description = "Show dev-only buttons on the Sync tab for staging fake CA, Collection Log, and Achievement Diary sync reviews."
    )
    default boolean showSyncTestTools() {
        return false;
    }

    // Tips config temporarily disabled — always off for now
    // @ConfigItem(
    //         keyName = "showTips",
    //         name = "Show task tips",
    //         description = "Show optional tips below the task description"
    // )
    // default boolean showTips() {
    //     return true;
    // }

}
