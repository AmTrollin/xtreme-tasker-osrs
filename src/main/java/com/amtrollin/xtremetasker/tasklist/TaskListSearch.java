package com.amtrollin.xtremetasker.tasklist;

import com.amtrollin.xtremetasker.models.XtremeTask;

public final class TaskListSearch
{
    private TaskListSearch() {}

    /** Punctuation stripped from both query and haystack before matching. */
    private static final java.util.regex.Pattern PUNCTUATION = java.util.regex.Pattern.compile("['\".,'?!]");

    public static boolean matches(XtremeTask t, String queryLower)
    {
        if (queryLower == null || queryLower.isEmpty())
        {
            return true;
        }

        String normalizedQuery = normalize(queryLower);

        return containsNormalized(t.getName(), normalizedQuery)
                || containsNormalized(t.getDescription(), normalizedQuery)
                || containsNormalized(t.getPrereqs(), normalizedQuery);
    }

    private static boolean containsNormalized(String haystack, String normalizedNeedle)
    {
        if (haystack == null || haystack.isEmpty())
        {
            return false;
        }
        return normalize(haystack.toLowerCase()).contains(normalizedNeedle);
    }

    private static String normalize(String s)
    {
        return PUNCTUATION.matcher(s).replaceAll("");
    }
}
