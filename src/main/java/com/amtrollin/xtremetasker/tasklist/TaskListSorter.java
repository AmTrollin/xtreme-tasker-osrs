package com.amtrollin.xtremetasker.tasklist;

import com.amtrollin.xtremetasker.enums.*;
import com.amtrollin.xtremetasker.models.*;
import com.amtrollin.xtremetasker.models.verification.TaskVerification;
import com.amtrollin.xtremetasker.tasklist.models.TaskListQuery;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class TaskListSorter
{
    private TaskListSorter() {}

    public static Comparator<XtremeTask> comparator(TaskListQuery q, TaskListFilter.CompletionLookup completed)
    {
        return comparator(q, completed, null, null);
    }

    public static Comparator<XtremeTask> comparator(
            TaskListQuery q,
            TaskListFilter.CompletionLookup completed,
            Function<XtremeTask, CompletionInfo> completionInfoProvider,
            Function<XtremeTask, Long> taskTicksProvider)
    {
        TaskListQuery.SortColumn sortColumn = q == null || q.sortColumn == null
                ? TaskListQuery.SortColumn.DATE
                : q.sortColumn;
        TaskListQuery.SortDirection sortDirection = q == null || q.sortDirection == null
                ? TaskListQuery.SortDirection.OFF
                : q.sortDirection;

        return (a, b) ->
        {
            int cmp;
            boolean directionApplied = false;
            if (sortDirection == TaskListQuery.SortDirection.OFF)
            {
                cmp = compareTaskName(a, b, true);
            }
            else switch (sortColumn)
            {
                case DATE:
                    cmp = compareDateCompleted(a, b, completionInfoProvider, sortDirection);
                    directionApplied = true;
                    break;
                case SPENT:
                    cmp = compareTimeSpent(a, b, taskTicksProvider, sortDirection);
                    directionApplied = true;
                    break;
                default:
                    cmp = compareTaskName(a, b, true);
                    break;
            }

            if (!directionApplied && sortDirection == TaskListQuery.SortDirection.DESC)
            {
                cmp = -cmp;
            }

            return cmp;
        };
    }

    private static int compareTaskName(XtremeTask a, XtremeTask b, boolean preserveCountedSequence)
    {
        if (preserveCountedSequence)
        {
            int sequenceCmp = compareCountedCollectionLogSequence(a, b);
            if (sequenceCmp != 0) return sequenceCmp;
        }

        int nameCmp = safeName(a).compareToIgnoreCase(safeName(b));
        if (nameCmp != 0) return nameCmp;

        int sourceCmp = Integer.compare(sourceRank(a == null ? null : a.getSource()), sourceRank(b == null ? null : b.getSource()));
        if (sourceCmp != 0) return sourceCmp;

        int tierCmp = Integer.compare(tierRank(a == null ? null : a.getTier()), tierRank(b == null ? null : b.getTier()));
        if (tierCmp != 0) return tierCmp;

        return safeId(a).compareToIgnoreCase(safeId(b));
    }

    private static int compareDateCompleted(
            XtremeTask a,
            XtremeTask b,
            Function<XtremeTask, CompletionInfo> completionInfoProvider,
            TaskListQuery.SortDirection sortDirection)
    {
        long at = completionTimestamp(a, completionInfoProvider);
        long bt = completionTimestamp(b, completionInfoProvider);
        int missingCmp = Boolean.compare(at == Long.MAX_VALUE, bt == Long.MAX_VALUE);
        if (missingCmp != 0) return missingCmp;

        int dateCmp = Long.compare(at, bt);
        if (sortDirection == TaskListQuery.SortDirection.DESC)
        {
            dateCmp = -dateCmp;
        }
        if (dateCmp != 0) return dateCmp;
        return compareTaskName(a, b, false);
    }

    private static int compareTimeSpent(
            XtremeTask a,
            XtremeTask b,
            Function<XtremeTask, Long> taskTicksProvider,
            TaskListQuery.SortDirection sortDirection)
    {
        long at = taskTicks(a, taskTicksProvider);
        long bt = taskTicks(b, taskTicksProvider);
        int missingCmp = Boolean.compare(at == Long.MAX_VALUE, bt == Long.MAX_VALUE);
        if (missingCmp != 0) return missingCmp;

        int timeCmp = Long.compare(at, bt);
        if (sortDirection == TaskListQuery.SortDirection.DESC)
        {
            timeCmp = -timeCmp;
        }
        if (timeCmp != 0) return timeCmp;
        return compareTaskName(a, b, false);
    }

    private static long completionTimestamp(XtremeTask task, Function<XtremeTask, CompletionInfo> completionInfoProvider)
    {
        CompletionInfo info = completionInfoProvider == null || task == null ? null : completionInfoProvider.apply(task);
        return info != null && info.timestamp > 0 ? info.timestamp : Long.MAX_VALUE;
    }

    private static long taskTicks(XtremeTask task, Function<XtremeTask, Long> taskTicksProvider)
    {
        Long ticks = taskTicksProvider == null || task == null ? null : taskTicksProvider.apply(task);
        return ticks != null && ticks > 0 ? ticks : Long.MAX_VALUE;
    }

    private static String safeName(XtremeTask task)
    {
        return task == null || task.getName() == null ? "" : task.getName();
    }

    private static String safeId(XtremeTask task)
    {
        return task == null || task.getId() == null ? "" : task.getId();
    }

    private static int tierRank(TaskTier tier)
    {
        if (tier == null)
        {
            return Integer.MAX_VALUE;
        }
        switch (tier)
        {
            case EASY:
                return 0;
            case MEDIUM:
                return 1;
            case HARD:
                return 2;
            case ELITE:
                return 3;
            case MASTER:
                return 4;
            case GRANDMASTER:
                return 5;
            default:
                return Integer.MAX_VALUE;
        }
    }

    private static int sourceRank(TaskSource source)
    {
        if (source == null)
        {
            return Integer.MAX_VALUE;
        }
        switch (source)
        {
            case COMBAT_ACHIEVEMENT:
                return 0;
            case COLLECTION_LOG:
                return 1;
            case DIARY_ACHIEVEMENT:
                return 2;
            default:
                return Integer.MAX_VALUE;
        }
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
