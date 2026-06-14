package com.amtrollin.xtremetasker.tasklist;

import com.amtrollin.xtremetasker.models.CompletionInfo;
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

    public interface CompletionInfoLookup
    {
        /** Returns the CompletionInfo for a task, or null if not completed. */
        CompletionInfo getInfo(XtremeTask task);
    }

    public interface TicksLookup
    {
        /** Returns accumulated in-game ticks for a task, or null if none. */
        Long getTicks(XtremeTask task);
    }

    public static Comparator<XtremeTask> comparator(TaskListQuery q, TaskListFilter.CompletionLookup completed)
    {
        return comparator(q, completed, null, null);
    }

    public static Comparator<XtremeTask> comparator(
            TaskListQuery q,
            TaskListFilter.CompletionLookup completed,
            CompletionInfoLookup infoLookup)
    {
        return comparator(q, completed, infoLookup, null);
    }

    public static Comparator<XtremeTask> comparator(
            TaskListQuery q,
            TaskListFilter.CompletionLookup completed,
            CompletionInfoLookup infoLookup,
            TicksLookup ticksLookup)
    {
        final boolean sortByCompletion = q.sortByCompletion;
        final boolean completedFirst = q.completedFirst;

        // Only meaningful when all tiers are shown
        final boolean sortByTier = q.sortByTier && q.tierScope == TaskListQuery.TierScope.ALL_TIERS;

        // Only meaningful when status filter == COMPLETE
        final boolean sortByDate = q.sortByDate
                && q.statusFilter == TaskListQuery.StatusFilter.COMPLETE
                && infoLookup != null;

        final boolean sortByTimeTicks = q.sortByTimeTicks && ticksLookup != null;

        return (a, b) ->
        {
            // 1) Completion sort (optional)
            if (sortByCompletion)
            {
                boolean aDone = completed.isCompleted(a);
                boolean bDone = completed.isCompleted(b);

                int aKey = aDone ? 1 : 0;
                int bKey = bDone ? 1 : 0;

                if (completedFirst)
                {
                    aKey = 1 - aKey;
                    bKey = 1 - bKey;
                }

                int cmp = Integer.compare(aKey, bKey);
                if (cmp != 0) return cmp;
            }

            // 2) Tier sort
            if (sortByTier)
            {
                int aTier = tierRank(a);
                int bTier = tierRank(b);

                int cmp = q.easyTierFirst
                        ? Integer.compare(aTier, bTier)
                        : Integer.compare(bTier, aTier);

                if (cmp != 0) return cmp;
            }

            // 3) Date completed sort
            if (sortByDate)
            {
                CompletionInfo aInfo = infoLookup.getInfo(a);
                CompletionInfo bInfo = infoLookup.getInfo(b);

                long aTs = (aInfo != null && aInfo.timestamp > 0) ? aInfo.timestamp : -1L;
                long bTs = (bInfo != null && bInfo.timestamp > 0) ? bInfo.timestamp : -1L;

                // Unknown timestamps go last regardless of sort direction
                if (aTs < 0 && bTs < 0)
                {
                    // both unknown — fall through to alpha
                }
                else if (aTs < 0)
                {
                    return 1; // a goes after b
                }
                else if (bTs < 0)
                {
                    return -1; // a goes before b
                }
                else
                {
                    int cmp = q.newestFirst
                            ? Long.compare(bTs, aTs)
                            : Long.compare(aTs, bTs);
                    if (cmp != 0) return cmp;
                }
            }

            // 4) Time spent (ticks) sort
            if (sortByTimeTicks)
            {
                Long aTicks = ticksLookup.getTicks(a);
                Long bTicks = ticksLookup.getTicks(b);
                long aT = aTicks != null ? aTicks : -1L;
                long bT = bTicks != null ? bTicks : -1L;
                // Tasks with no ticks go last
                if (aT < 0 && bT < 0) { /* fall through */ }
                else if (aT < 0) return 1;
                else if (bT < 0) return -1;
                else
                {
                    int cmp = q.longestFirst
                            ? Long.compare(bT, aT)
                            : Long.compare(aT, bT);
                    if (cmp != 0) return cmp;
                }
            }

            // 5) Counted collection-log sequences stay in progression order.
            int sequenceCmp = compareCountedCollectionLogSequence(a, b);
            if (sequenceCmp != 0) return sequenceCmp;

            // 6) Always alphabetical fallback
            String an = a.getName() == null ? "" : a.getName();
            String bn = b.getName() == null ? "" : b.getName();
            return an.compareToIgnoreCase(bn);
        };
    }

    private static int tierRank(XtremeTask t)
    {
        if (t.getTier() == null) return Integer.MAX_VALUE;
        return t.getTier().ordinal();
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
