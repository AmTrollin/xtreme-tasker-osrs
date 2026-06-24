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
        FAVOUR,
        BARBARIAN_MINIQUEST,
        LAIR_OF_TARN_RAZORLOR,
        MAGE_ARENA_1,
        ENTER_THE_ABYSS,
        VALE_TOTEMS,
        ALFRED_GRIMHANDS_BARCRAWL,
        JAGEX_ACCOUNT,
        THERMONUCLEAR_SMOKE_DEVIL,
        RAID_WIKI,
        GIANT_MOLE,
        PROSPECTOR_HELMET,
        LIZARDMAN_SHAMAN,
        BONES_TO_PEACHES,
        ZULRAH,
        CHAOS_ELEMENTAL,
        CHOMPY_BIRD,
        KALPHITE_QUEEN,
        PENANCE_QUEEN,
        PYRAMID_PLUNDER,
        SKOTIZO,
        HYDRA,
        WHITE_KNIGHT,
        KET_ZEK,
        TZHAAR_FIGHT_CAVE,
        BARROWS_CHEST,
        CHAMBERS_OF_XERIC,
        CRAZY_ARCHAEOLOGIST,
        CHAOS_FANATIC,
        SCORPIA,
        SARADOMIN_STRIKE,
        CLAWS_OF_GUTHIX,
        FLAMES_OF_ZAMORAK,
        DAGANNOTH_REX,
        DAGANNOTH_SUPREME,
        DAGANNOTH_PRIME,
        CALLISTO,
        VENENATIS,
        VETION,
        KRIL_TSUTSAROTH,
        KREEARRA,
        COMMANDER_ZILYANA,
        GENERAL_GRAARDOR,
        VOID_TOP,
        VOID_ROBE,
        VOID_GLOVES,
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
