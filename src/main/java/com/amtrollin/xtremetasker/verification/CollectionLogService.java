package com.amtrollin.xtremetasker.verification;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.item.ItemPrice;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Singleton
public class CollectionLogService
{
    // Matches "New item added to your collection log: Mark of grace x1."
    // Also handles no-quantity variant: "New item added to your collection log: Mark of grace."
    private static final Pattern CLOG_NEW_ITEM_PATTERN = Pattern.compile(
            "New item added to your collection log:\\s*(.+?)(?:\\s+x[\\d,]+)?\\s*\\.?\\s*$",
            Pattern.CASE_INSENSITIVE
    );

    @Inject
    private EventBus eventBus;

    @Inject
    private CollectionLogWidgetMonitor widgetMonitor;

    @Inject
    private ItemManager itemManager;

    private final Set<Integer> obtainedItems = new HashSet<>();

    public void startUp()
    {
        eventBus.register(this);
        widgetMonitor.startUp();
        reset();
    }

    public void shutDown()
    {
        eventBus.unregister(this);
        widgetMonitor.shutDown();
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        // Capture newly obtained collection log items from the in-game notification.
        // This fires even when the Collection Log interface is closed.
        ChatMessageType type = event.getType();
        if (type != ChatMessageType.GAMEMESSAGE && type != ChatMessageType.SPAM)
        {
            return;
        }

        String raw = event.getMessage();
        if (raw == null)
        {
            return;
        }

        // Strip any HTML colour tags RuneLite may inject.
        String clean = raw.replaceAll("<[^>]+>", "").trim();

        Matcher m = CLOG_NEW_ITEM_PATTERN.matcher(clean);
        if (!m.find())
        {
            return;
        }

        String itemName = m.group(1).trim();
        resolveAndStoreByName(itemName);
    }

    private void resolveAndStoreByName(String itemName)
    {
        List<ItemPrice> results = itemManager.search(itemName);
        for (ItemPrice result : results)
        {
            if (itemName.equalsIgnoreCase(result.getName()))
            {
                log.debug("Collection log chat capture: '{}' -> item ID {}", itemName, result.getId());
                storeItem(result.getId());
                return;
            }
        }

        // Fallback: if no exact match, take the best result so we at least capture something.
        if (!results.isEmpty())
        {
            log.debug("Collection log chat capture (fuzzy): '{}' -> item ID {}", itemName, results.get(0).getId());
            storeItem(results.get(0).getId());
        }
        else
        {
            log.debug("Collection log chat capture could not resolve item ID for '{}'", itemName);
        }
    }

    public boolean isItemObtained(int itemId)
    {
        return obtainedItems.contains(itemId);
    }

    public boolean requestCollectionLogOpenOrRefresh()
    {
        return widgetMonitor.requestCollectionLogOpenOrRefresh();
    }

    public void storeItem(int itemId)
    {
        if (itemId > 0)
        {
            obtainedItems.add(itemId);
        }
    }

    public long countObtained(int[] itemIds)
    {
        if (itemIds == null || itemIds.length == 0)
        {
            return 0;
        }

        long count = 0;
        for (int itemId : itemIds)
        {
            if (isItemObtained(itemId))
            {
                count++;
            }
        }
        return count;
    }

    public int getCapturedItemCount()
    {
        return obtainedItems.size();
    }

    public Set<Integer> getCachedItemIds()
    {
        return java.util.Collections.unmodifiableSet(obtainedItems);
    }

    public void restoreCachedItemIds(Set<Integer> itemIds)
    {
        if (itemIds == null || itemIds.isEmpty())
        {
            return;
        }

        for (Integer itemId : itemIds)
        {
            if (itemId != null && itemId > 0)
            {
                obtainedItems.add(itemId);
            }
        }
    }

    public void resetCachedItemIds()
    {
        reset();
    }

    private void reset()
    {
        obtainedItems.clear();
    }
}
