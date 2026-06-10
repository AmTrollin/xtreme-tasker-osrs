package com.amtrollin.xtremetasker.ui.text;

import com.amtrollin.xtremetasker.enums.TaskSource;
import com.amtrollin.xtremetasker.enums.TaskTier;

public final class TaskLabelFormatter
{
    private TaskLabelFormatter() {}

    public static String tierLabel(TaskTier tier)
    {
        if (tier == null)
        {
            return "";
        }

        switch (tier)
        {
            case EASY:
                return "Easy";
            case MEDIUM:
                return "Medium";
            case HARD:
                return "Hard";
            case ELITE:
                return "Elite";
            case MASTER:
                return "Master";
            case GRANDMASTER:
                return "Master";
            default:
                return tier.name();
        }
    }

    public static String shortTier(TaskTier tier)
    {
        if (tier == null)
        {
            return "?";
        }

        switch (tier)
        {
            case EASY:
                return "E";
            case MEDIUM:
                return "M";
            case HARD:
                return "H";
            case ELITE:
                return "EL";
            case MASTER:
                return "MA";
            case GRANDMASTER:
                return "GM";
            default:
                return tier.name();
        }
    }

    public static String shortSource(TaskSource source)
    {
        if (source == null)
        {
            return "?";
        }

        switch (source)
        {
            case COMBAT_ACHIEVEMENT:
                return "CA";
            case COLLECTION_LOG:
                return "CL";
            case DIARY_ACHIEVEMENT:
                return "DA";
            default:
                String n = source.name();
                return n.length() >= 2 ? n.substring(0, 2) : n;
        }
    }

    public static String sourceLabel(TaskSource source)
    {
        if (source == null)
        {
            return "Unknown source";
        }

        switch (source)
        {
            case COMBAT_ACHIEVEMENT:
                return "Combat Achievement";
            case COLLECTION_LOG:
                return "Collection Log";
            case DIARY_ACHIEVEMENT:
                return "Diary Achievement";
            default:
                return source.name();
        }
    }

    public static String fullTierLabel(TaskTier tier)
    {
        if (tier == null)
        {
            return "Unknown tier";
        }

        switch (tier)
        {
            case EASY:
                return "Easy";
            case MEDIUM:
                return "Medium";
            case HARD:
                return "Hard";
            case ELITE:
                return "Elite";
            case MASTER:
                return "Master";
            case GRANDMASTER:
                return "Grandmaster";
            default:
                return tier.name();
        }
    }
}
