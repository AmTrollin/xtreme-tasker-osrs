package com.amtrollin.xtremetasker.tasklist;

import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.models.verification.TaskVerification;
import com.amtrollin.xtremetasker.tasklist.models.TaskListQuery;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Collectors;

public final class TaskListSorter
{
    private TaskListSorter() {}

    public static Comparator<XtremeTask> comparator(TaskListQuery q, TaskListFilter.CompletionLookup completed)
    {
        return (a, b) ->
        {
            int sequenceCmp = compareCountedCollectionLogSequence(a, b);
            if (sequenceCmp != 0) return sequenceCmp;

            String an = a.getName() == null ? "" : a.getName();
            String bn = b.getName() == null ? "" : b.getName();
            return an.compareToIgnoreCase(bn);
        };
    }

    private static int compareCountedCollectionLogSequence(XtremeTask a, XtremeTask b)
    {
        TaskVerification av = a == null ? null : a.getVerification();
        TaskVerification bv = b == null ? null : b.getVerification();
        if (!isCountedCollectionLog(av) || !isCountedCollectionLog(bv))
        {
            return 0;
        }

        if (a.getSource() != b.getSource() || a.getTier() != b.getTier())
        {
            return 0;
        }

        String aItems = canonicalItems(av.getItemIds());
        String bItems = canonicalItems(bv.getItemIds());
        if (aItems.isEmpty() || !Objects.equals(aItems, bItems))
        {
            return 0;
        }

        int aCount = av.getCount() == null ? 1 : av.getCount();
        int bCount = bv.getCount() == null ? 1 : bv.getCount();
        return Integer.compare(aCount, bCount);
    }

    private static boolean isCountedCollectionLog(TaskVerification verification)
    {
        return verification != null
                && verification.getType() == TaskVerification.VerificationType.COLLECTION_LOG
                && verification.getCount() != null;
    }

    private static String canonicalItems(int[] itemIds)
    {
        if (itemIds == null || itemIds.length == 0)
        {
            return "";
        }

        return Arrays.stream(itemIds)
                .filter(itemId -> itemId > 0)
                .distinct()
                .sorted()
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(","));
    }

}
