package com.amtrollin.xtremetasker.models;

import net.runelite.api.Skill;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
public class PrerequisiteStatus
{
    private final String text;
    private final boolean completed;
    private final List<CheckSpan> checkSpans;
    private final List<Skill> skillIcons;
    private final List<MarkerIcon> markerIcons;

    public PrerequisiteStatus(String text, boolean completed)
    {
        this(text, completed, List.of(), List.of(), false);
    }

    public PrerequisiteStatus(String text, boolean completed, List<CheckSpan> checkSpans)
    {
        this(text, completed, checkSpans, List.of(), false);
    }

    public PrerequisiteStatus(String text, boolean completed, List<CheckSpan> checkSpans, List<Skill> skillIcons)
    {
        this(text, completed, checkSpans, skillIcons, false);
    }

    public PrerequisiteStatus(String text, boolean completed, List<CheckSpan> checkSpans, List<Skill> skillIcons, boolean questIcon)
    {
        this(text, completed, checkSpans, skillIcons, questIcon ? List.of(MarkerIcon.QUEST) : List.of());
    }

    public PrerequisiteStatus(String text, boolean completed, List<CheckSpan> checkSpans, List<Skill> skillIcons, List<MarkerIcon> markerIcons)
    {
        this.text = text;
        this.completed = completed;
        this.checkSpans = checkSpans == null ? List.of() : Collections.unmodifiableList(checkSpans);
        this.skillIcons = skillIcons == null ? List.of() : Collections.unmodifiableList(skillIcons);
        this.markerIcons = markerIcons == null ? List.of() : Collections.unmodifiableList(markerIcons);
    }

    public boolean hasQuestIcon()
    {
        return markerIcons.contains(MarkerIcon.QUEST) || markerIcons.contains(MarkerIcon.START_QUEST);
    }

    public enum MarkerIcon
    {
        QUEST,
        START_QUEST,
        ACHIEVEMENT_DIARY,
        COMBAT,
        TOTAL,
        BULLET,
        BARBARIAN_MINIQUEST,
        LAIR_OF_TARN_RAZORLOR,
        MAGE_ARENA_1,
        ENTER_THE_ABYSS,
        VALE_TOTEMS,
        ALFRED_GRIMHANDS_BARCRAWL,
        WILDERNESS,
        CURRENCY
    }

    @Getter
    public static class CheckSpan
    {
        private final int start;
        private final int end;
        private final boolean completed;

        public CheckSpan(int start, int end, boolean completed)
        {
            this.start = start;
            this.end = end;
            this.completed = completed;
        }

    }
}
