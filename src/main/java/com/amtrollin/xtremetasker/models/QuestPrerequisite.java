package com.amtrollin.xtremetasker.models;

import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** Authored quest access requirement; a milestone is deliberately distinct from merely starting. */
@Getter
@EqualsAndHashCode
public final class QuestPrerequisite
{
    public enum Action { COMPLETE, START, MILESTONE, COMPLETE_SUBQUEST }
    public enum ObservedUnlock { KARAMBWAN_FISHING }

    private final String quest;
    private final Action action;
    private final String detail;
    // Optional child quest whose state is checked, while the parent is shown in the UI.
    private final String stateQuest;
    private final String sourceUrl;
    private final ProgressCheck progress;
    private final String progressSourceUrl;
    private final ObservedUnlock observedUnlock;

    public enum VariableType { VARBIT, VARP }

    @Getter
    @EqualsAndHashCode
    public static final class ProgressCheck
    {
        private final VariableType type;
        private final int id;
        private final int minimum;
        // Some older quests pack independent flags into one varplayer.
        private final Integer mask;
        // Alternative ways to unlock access; every child must be a valid check.
        private final List<ProgressCheck> anyOf;

        public ProgressCheck(VariableType type, int id, int minimum)
        {
            this.type = type;
            this.id = id;
            this.minimum = minimum;
            this.mask = null;
            this.anyOf = null;
        }

        public boolean isValid()
        {
            if (anyOf != null)
                return type == null && mask == null && id == 0 && minimum == 0 && !anyOf.isEmpty()
                        && anyOf.stream().allMatch(check -> check != null && check.isValid());
            return type != null && id >= 0 && minimum > 0
                    && (mask == null || mask > 0 && (minimum & ~mask) == 0);
        }
    }

    public QuestPrerequisite(String quest, Action action, String detail, String stateQuest, String sourceUrl)
    {
        this(quest, action, detail, stateQuest, sourceUrl, null, null);
    }

    public QuestPrerequisite(String quest, Action action, String detail, String stateQuest, String sourceUrl,
                             ProgressCheck progress, String progressSourceUrl)
    {
        this(quest, action, detail, stateQuest, sourceUrl, progress, progressSourceUrl, null);
    }

    public QuestPrerequisite(String quest, Action action, String detail, String stateQuest, String sourceUrl,
                            ProgressCheck progress, String progressSourceUrl, ObservedUnlock observedUnlock)
    {
        this.observedUnlock = observedUnlock;
        this.progress = progress;
        this.progressSourceUrl = progressSourceUrl;
        this.quest = quest;
        this.action = action;
        this.detail = detail;
        this.stateQuest = stateQuest;
        this.sourceUrl = sourceUrl;
    }

    public String title()
    {
        if (action == Action.COMPLETE) return "Complete " + quest;
        if (action == Action.START) return "Start " + quest;
        return "Reach milestone in " + quest;
    }
}
