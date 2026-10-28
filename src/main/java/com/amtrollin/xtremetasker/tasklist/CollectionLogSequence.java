package com.amtrollin.xtremetasker.tasklist;

import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.models.verification.TaskVerification;
import java.util.Arrays;
import java.util.Locale;

/** Resolves the exact item slots required by a collection-log sequence step. */
public final class CollectionLogSequence
{
    private static final String[] CHOMPY_HAT_NAMES = {
        "Ogre bowman", "Bowman", "Ogre yeoman", "Yeoman", "Ogre marksman", "Marksman",
        "Ogre woodsman", "Woodsman", "Ogre forester", "Forester", "Ogre bowmaster", "Bowmaster",
        "Ogre expert", "Expert", "Ogre dragon archer", "Dragon archer",
        "Expert ogre dragon archer", "Expert dragon archer"
    };

    public static String chompyHatName(int itemId)
    {
        int index = itemId - 2978;
        return index >= 0 && index < CHOMPY_HAT_NAMES.length ? CHOMPY_HAT_NAMES[index] : "";
    }

    private CollectionLogSequence() {}

    public static boolean isChompyHatPair(XtremeTask task)
    {
        return task != null && task.getName() != null
                && task.getName().toLowerCase(Locale.ROOT).startsWith("get the next 2 tiers of chompy bird hat");
    }

    public static int[] stepItemIds(XtremeTask task)
    {
        TaskVerification verification = task == null ? null : task.getVerification();
        if (verification == null || verification.getItemIds() == null || verification.getCount() == null)
        {
            return new int[0];
        }
        int end = verification.getCount();
        int size = isChompyHatPair(task) ? 2 : 1;
        return end < size || end > verification.getItemIds().length ? new int[0]
                : Arrays.copyOfRange(verification.getItemIds(), end - size, end);
    }
}
