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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    private static final Map<Integer, Integer> COLLECTION_LOG_ITEM_ALIASES = Map.ofEntries(
            Map.entry(24656, 20851), // Olmlet - Enraged Tektiny -> Olmlet
            Map.entry(24658, 20851), // Olmlet - Flying Vespina -> Olmlet
            Map.entry(22376, 20851), // Olmlet - Puppadile -> Olmlet
            Map.entry(22378, 20851), // Olmlet - Tektiny -> Olmlet
            Map.entry(22380, 20851), // Olmlet - Vanguard -> Olmlet
            Map.entry(22382, 20851), // Olmlet - Vasa minirio -> Olmlet
            Map.entry(22384, 20851), // Olmlet - Vespina -> Olmlet

            Map.entry(25749, 22473), // Lil' zik - Lil' Bloat -> Lil' zik
            Map.entry(25748, 22473), // Lil' zik - Lil' Maiden -> Lil' zik
            Map.entry(25750, 22473), // Lil' zik - Lil' Nylo -> Lil' zik
            Map.entry(25751, 22473), // Lil' zik - Lil' Sot -> Lil' zik
            Map.entry(25752, 22473), // Lil' zik - Lil' Xarp -> Lil' zik

            Map.entry(22481, 22323), // Sanguinesti staff (uncharged) -> Sanguinesti staff
            Map.entry(22486, 22325), // Scythe of vitur (uncharged) -> Scythe of vitur
            Map.entry(28549, 28547), // Tumeken's shadow (uncharged) -> Tumeken's shadow

            Map.entry(27382, 27352), // Tumeken's guardian - Akkhito -> Tumeken's guardian
            Map.entry(27383, 27352), // Tumeken's guardian - Babi -> Tumeken's guardian
            Map.entry(27387, 27352), // Tumeken's guardian - Elidinis' Damaged Guardian -> Tumeken's guardian
            Map.entry(27354, 27352), // Tumeken's guardian - Elidinis' Guardian -> Tumeken's guardian
            Map.entry(27384, 27352), // Tumeken's guardian - Kephriti -> Tumeken's guardian
            Map.entry(27386, 27352), // Tumeken's guardian - Tumeken's Damaged Guardian -> Tumeken's guardian
            Map.entry(27385, 27352), // Tumeken's guardian - Zebo -> Tumeken's guardian

            Map.entry(25618, 10877), // Plain satchel interface item -> Plain satchel
            Map.entry(25619, 10878), // Green satchel interface item -> Green satchel
            Map.entry(25620, 10879), // Red satchel interface item -> Red satchel
            Map.entry(25621, 10880), // Black satchel interface item -> Black satchel
            Map.entry(25622, 10881), // Gold satchel interface item -> Gold satchel
            Map.entry(25623, 10882)  // Rune satchel interface item -> Rune satchel
    );

    @Inject
    private EventBus eventBus;

    @Inject
    private CollectionLogWidgetMonitor widgetMonitor;

    @Inject
    private ItemManager itemManager;

    private final Set<Integer> obtainedItems = new HashSet<>();
    private final Set<Integer> seenItems = new HashSet<>();
    private final Map<Integer, Long> obtainedItemOrder = new HashMap<>();
    private long nextObtainedItemOrder = 1L;

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
        if (results == null || results.isEmpty())
        {
            log.debug("Collection log chat capture could not resolve item ID for '{}'", itemName);
            return;
        }

        for (ItemPrice result : results)
        {
            if (itemName.equalsIgnoreCase(result.getName()))
            {
                log.debug("Collection log chat capture: '{}' -> item ID {}", itemName, result.getId());
                storeItem(result.getId());
                return;
            }
        }

        String normalizedTarget = normalizeItemName(itemName);
        List<ItemPrice> normalizedMatches = new ArrayList<>();
        for (ItemPrice result : results)
        {
            if (normalizedTarget.equals(normalizeItemName(result.getName())))
            {
                normalizedMatches.add(result);
            }
        }

        if (normalizedMatches.size() == 1)
        {
            ItemPrice resolved = normalizedMatches.get(0);
            log.debug("Collection log chat capture (normalized): '{}' -> item ID {}", itemName, resolved.getId());
            storeItem(resolved.getId());
            return;
        }

        log.debug("Collection log chat capture ignored ambiguous match for '{}' ({} candidates)", itemName, results.size());
    }

    private static String normalizeItemName(String value)
    {
        if (value == null)
        {
            return "";
        }

        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public boolean isItemObtained(int itemId)
    {
        return obtainedItems.contains(itemId)
                || obtainedItems.contains(canonicalCollectionLogItemId(itemId));
    }

    public boolean hasSeenItem(int itemId)
    {
        return seenItems.contains(itemId)
                || seenItems.contains(canonicalCollectionLogItemId(itemId));
    }

    public boolean requestCollectionLogOpenOrRefresh()
    {
        return widgetMonitor.requestCollectionLogOpenOrRefresh();
    }

    public boolean isCollectionLogScanInProgress()
    {
        return widgetMonitor.isCollectionLogScanInProgress();
    }

    public void storeItem(int itemId)
    {
        if (itemId > 0)
        {
            markObtainedItem(itemId, null);
        }
    }

    public void storeSeenItem(int itemId)
    {
        if (itemId > 0)
        {
            seenItems.add(itemId);
            seenItems.add(canonicalCollectionLogItemId(itemId));
        }
    }

    public boolean hasSeenAll(int[] itemIds)
    {
        if (itemIds == null || itemIds.length == 0)
        {
            return false;
        }

        Set<Integer> requiredCanonicalItemIds = new HashSet<>();
        for (int itemId : itemIds)
        {
            if (itemId > 0)
            {
                requiredCanonicalItemIds.add(canonicalCollectionLogItemId(itemId));
            }
        }

        if (requiredCanonicalItemIds.isEmpty())
        {
            return false;
        }

        for (int itemId : requiredCanonicalItemIds)
        {
            if (!hasSeenItem(itemId))
            {
                return false;
            }
        }

        return true;
    }

    public long countObtained(int[] itemIds)
    {
        if (itemIds == null || itemIds.length == 0)
        {
            return 0;
        }

        Set<Integer> countedCanonicalItemIds = new HashSet<>();
        long count = 0;
        for (int itemId : itemIds)
        {
            int canonicalItemId = canonicalCollectionLogItemId(itemId);
            if (canonicalItemId > 0
                    && countedCanonicalItemIds.add(canonicalItemId)
                    && isItemObtained(canonicalItemId))
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

    public Map<Integer, Long> getCachedItemOrder()
    {
        return java.util.Collections.unmodifiableMap(obtainedItemOrder);
    }

    public void restoreCachedItemIds(Set<Integer> itemIds)
    {
        restoreCachedItemState(itemIds, null);
    }

    public void restoreCachedItemState(Set<Integer> itemIds, Map<Integer, Long> itemOrder)
    {
        if (itemIds == null || itemIds.isEmpty())
        {
            return;
        }

        for (Integer itemId : itemIds)
        {
            if (itemId != null && itemId > 0)
            {
                Long restoredOrder = itemOrder == null ? null : itemOrder.get(itemId);
                if (restoredOrder == null && itemOrder != null)
                {
                    restoredOrder = itemOrder.get(canonicalCollectionLogItemId(itemId));
                }
                markObtainedItem(itemId, restoredOrder);
                seenItems.add(itemId);
                seenItems.add(canonicalCollectionLogItemId(itemId));
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
        seenItems.clear();
        obtainedItemOrder.clear();
        nextObtainedItemOrder = 1L;
    }

    public long getObtainedItemOrder(int itemId)
    {
        Long order = obtainedItemOrder.get(itemId);
        if (order != null)
        {
            return order;
        }
        order = obtainedItemOrder.get(canonicalCollectionLogItemId(itemId));
        return order == null ? Long.MAX_VALUE : order;
    }

    private void markObtainedItem(int itemId, Long restoredOrder)
    {
        int canonicalItemId = canonicalCollectionLogItemId(itemId);
        obtainedItems.add(itemId);
        obtainedItems.add(canonicalItemId);

        Long order = restoredOrder != null && restoredOrder > 0 ? restoredOrder : existingOrder(itemId, canonicalItemId);
        if (order == null)
        {
            order = nextObtainedItemOrder++;
        }

        obtainedItemOrder.putIfAbsent(itemId, order);
        obtainedItemOrder.putIfAbsent(canonicalItemId, order);
        nextObtainedItemOrder = Math.max(nextObtainedItemOrder, order + 1);
    }

    private Long existingOrder(int itemId, int canonicalItemId)
    {
        Long order = obtainedItemOrder.get(itemId);
        if (order != null)
        {
            return order;
        }
        return obtainedItemOrder.get(canonicalItemId);
    }

    private int canonicalCollectionLogItemId(int itemId)
    {
        return COLLECTION_LOG_ITEM_ALIASES.getOrDefault(itemId, itemId);
    }
}
