package com.amtrollin.xtremetasker.tasklist;

import com.amtrollin.xtremetasker.models.XtremeTask;
import com.amtrollin.xtremetasker.models.verification.TaskVerification;
import java.util.*;

public final class TaskGroupUtils
{
    private static final int ANCIENT_PAGE_FIRST_ITEM_ID = 11341;
    private static final int ANCIENT_PAGE_LAST_ITEM_ID = 11366;

    private TaskGroupUtils()
    {
    }

    public static List<XtremeTask> collapsePreservingOrder(List<XtremeTask> tasks)
    {
        if (tasks == null || tasks.isEmpty())
        {
            return new ArrayList<>();
        }

        Map<String, XtremeTask> firstByKey = new LinkedHashMap<>();
        for (XtremeTask task : tasks)
        {
            if (task != null)
            {
                firstByKey.putIfAbsent(key(task), task);
            }
        }
        return new ArrayList<>(firstByKey.values());
    }

    public static List<XtremeTask> groupFor(List<XtremeTask> allTasks, XtremeTask target)
    {
        List<XtremeTask> out = new ArrayList<>();
        if (allTasks == null || target == null)
        {
            return out;
        }

        String targetKey = key(target);
        for (XtremeTask task : allTasks)
        {
            if (task != null && targetKey.equals(key(task)))
            {
                out.add(task);
            }
        }
        return out;
    }

    public static String key(XtremeTask task)
    {
        if (task == null)
        {
            return "";
        }

        String countedCollectionLogKey = countedCollectionLogKey(task);
        if (countedCollectionLogKey != null)
        {
            return countedCollectionLogKey;
        }

        return normalize(task.getName())
                + "|source=" + Objects.toString(task.getSource(), "")
                + "|tier=" + Objects.toString(task.getTier(), "");
    }

    private static String countedCollectionLogKey(XtremeTask task)
    {
        TaskVerification verification = task.getVerification();
        if (verification == null
                || verification.getType() != TaskVerification.VerificationType.COLLECTION_LOG
                || verification.getCount() == null)
        {
            return null;
        }

        int[] itemIds = verification.getItemIds();
        if (itemIds == null || itemIds.length == 0)
        {
            return null;
        }

        int[] sortedItemIds = Arrays.stream(itemIds)
                .filter(itemId -> itemId > 0)
                .distinct()
                .sorted()
                .toArray();
        String items = Arrays.stream(sortedItemIds)
                .mapToObj(String::valueOf)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        if (items.isEmpty())
        {
            return null;
        }

        return "counted-cl|source=" + Objects.toString(task.getSource(), "")
                + (isAncientPageRequirement(sortedItemIds) ? "" : "|tier=" + Objects.toString(task.getTier(), ""))
                + "|items=" + items;
    }

    private static boolean isAncientPageRequirement(int[] itemIds)
    {
        int expectedCount = ANCIENT_PAGE_LAST_ITEM_ID - ANCIENT_PAGE_FIRST_ITEM_ID + 1;
        if (itemIds == null || itemIds.length != expectedCount)
        {
            return false;
        }

        for (int i = 0; i < itemIds.length; i++)
        {
            if (itemIds[i] != ANCIENT_PAGE_FIRST_ITEM_ID + i)
            {
                return false;
            }
        }
        return true;
    }

    private static String normalize(String value)
    {
        if (value == null)
        {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
