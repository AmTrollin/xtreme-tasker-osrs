package com.amtrollin.xtremetasker.verification;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
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
    private static final String ANCIENT_PAGE_ITEM_NAME = "Ancient page";
    private static final String MEDALLION_FRAGMENT_ITEM_NAME = "Medallion fragment";
    private static final int MEDALLION_OF_THE_DEEP_ITEM_ID = 32386;

    // Matches "New item added to your collection log: Mark of grace x1."
    // Also handles no-quantity variant: "New item added to your collection log: Mark of grace."
    private static final Pattern CLOG_NEW_ITEM_PATTERN = Pattern.compile(
            "New item added to your collection log:\\s*(.+?)(?:\\s+x[\\d,]+)?\\s*\\.?\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CLOG_RECEIVED_ITEM_PATTERN = Pattern.compile(
            "^You have received\\s+(?:[\\d,]+\\s*x\\s*)?(.+?)\\s*\\.?\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MEDALLION_OF_THE_DEEP_ASSEMBLED_PATTERN = Pattern.compile(
            "\\byou\\s+assemble\\s+the\\s+Medallion\\s+of\\s+the\\s+Deep\\b",
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

            Map.entry(27382, 27352), // Tumeken's guardian - Akkhito -> Tumeken's guardian
            Map.entry(27383, 27352), // Tumeken's guardian - Babi -> Tumeken's guardian
            Map.entry(27387, 27352), // Tumeken's guardian - Elidinis' Damaged Guardian -> Tumeken's guardian
            Map.entry(27354, 27352), // Tumeken's guardian - Elidinis' Guardian -> Tumeken's guardian
            Map.entry(27384, 27352), // Tumeken's guardian - Kephriti -> Tumeken's guardian
            Map.entry(27386, 27352), // Tumeken's guardian - Tumeken's Damaged Guardian -> Tumeken's guardian
            Map.entry(27385, 27352), // Tumeken's guardian - Zebo -> Tumeken's guardian

            Map.entry(10860, 10859), // Tea flask animation item -> Tea flask
            Map.entry(10861, 10859), // Tea flask animation item -> Tea flask
            Map.entry(25617, 10859), // Tea flask interface item -> Tea flask

            Map.entry(25618, 10877), // Plain satchel interface item -> Plain satchel
            Map.entry(25619, 10878), // Green satchel interface item -> Green satchel
            Map.entry(25620, 10879), // Red satchel interface item -> Red satchel
            Map.entry(25621, 10880), // Black satchel interface item -> Black satchel
            Map.entry(25622, 10881), // Gold satchel interface item -> Gold satchel
            Map.entry(25623, 10882), // Rune satchel interface item -> Rune satchel

            Map.entry(27693, 27019), // Ore pack shop/interface item -> Ore pack collection log item
            Map.entry(27031, 27029)  // Smiths gloves (i) -> Smiths gloves
    );

    @Inject
    private EventBus eventBus;

    @Inject
    private Client client;

    @Inject
    private CollectionLogWidgetMonitor widgetMonitor;

    @Inject
    private ItemManager itemManager;

    private final Set<Integer> obtainedItems = new HashSet<>();
    private final Set<Integer> seenItems = new HashSet<>();
    private final Map<Integer, Long> obtainedItemOrder = new HashMap<>();
    private long nextObtainedItemOrder = 1L;
    private int pendingAncientPageDropCountSinceLastSync = 0;
    private int pendingMedallionFragmentDropCountSinceLastSync = 0;
    private Runnable cacheChangeListener;
    private int cacheChangeBatchDepth = 0;
    private boolean cacheChangePending = false;

    public void setCacheChangeListener(Runnable cacheChangeListener)
    {
        this.cacheChangeListener = cacheChangeListener;
    }

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

        if (MEDALLION_OF_THE_DEEP_ASSEMBLED_PATTERN.matcher(clean).find())
        {
            storeItem(MEDALLION_OF_THE_DEEP_ITEM_ID);
            return;
        }

        Matcher m = CLOG_NEW_ITEM_PATTERN.matcher(clean);
        if (m.find())
        {
            String itemName = m.group(1).trim();
            resolveAndStoreByName(itemName);
            return;
        }

        Matcher receivedMatcher = CLOG_RECEIVED_ITEM_PATTERN.matcher(clean);
        if (isCollectionLogOpen() && receivedMatcher.find())
        {
            String itemName = receivedMatcher.group(1).trim();
            resolveAndStoreByName(itemName);
        }
    }

    private boolean isCollectionLogOpen()
    {
        Widget collectionLog = client == null ? null : client.getWidget(ComponentID.COLLECTION_LOG_CONTAINER);
        return collectionLog != null && !collectionLog.isHidden();
    }

    private void resolveAndStoreByName(String itemName)
    {
        if (ANCIENT_PAGE_ITEM_NAME.equalsIgnoreCase(itemName))
        {
            pendingAncientPageDropCountSinceLastSync++;
            notifyCacheChanged();
            log.debug("Collection log chat capture deferred ambiguous Ancient page drop until CLOG sync");
            return;
        }

        if (MEDALLION_FRAGMENT_ITEM_NAME.equalsIgnoreCase(itemName))
        {
            pendingMedallionFragmentDropCountSinceLastSync++;
            notifyCacheChanged();
            log.debug("Collection log chat capture deferred ambiguous Medallion fragment drop until CLOG sync");
            return;
        }

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

    public void storeItem(int itemId)
    {
        if (itemId > 0)
        {
            if (markObtainedItem(itemId, null))
            {
                notifyCacheChanged();
            }
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

    public void beginCacheChangeBatch()
    {
        cacheChangeBatchDepth++;
    }

    public void endCacheChangeBatch()
    {
        if (cacheChangeBatchDepth <= 0)
        {
            cacheChangeBatchDepth = 0;
            return;
        }

        cacheChangeBatchDepth--;
        if (cacheChangeBatchDepth == 0 && cacheChangePending)
        {
            cacheChangePending = false;
            notifyCacheChanged();
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

    public int getPendingAncientPageDropCountSinceLastSync()
    {
        return Math.max(0, pendingAncientPageDropCountSinceLastSync);
    }

    public int getPendingMedallionFragmentDropCountSinceLastSync()
    {
        return Math.max(0, pendingMedallionFragmentDropCountSinceLastSync);
    }

    public void clearPendingAncientPageDropCountSinceLastSync()
    {
        if (pendingAncientPageDropCountSinceLastSync > 0)
        {
            pendingAncientPageDropCountSinceLastSync = 0;
            notifyCacheChanged();
        }
    }

    public void clearPendingMedallionFragmentDropCountSinceLastSync()
    {
        if (pendingMedallionFragmentDropCountSinceLastSync > 0)
        {
            pendingMedallionFragmentDropCountSinceLastSync = 0;
            notifyCacheChanged();
        }
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

    public void removeCachedItemIds(Set<Integer> itemIds)
    {
        if (itemIds == null || itemIds.isEmpty())
        {
            return;
        }

        boolean changed = false;
        for (Integer itemId : itemIds)
        {
            if (itemId == null || itemId <= 0)
            {
                continue;
            }

            int canonicalItemId = canonicalCollectionLogItemId(itemId);
            changed |= obtainedItems.remove(itemId);
            changed |= obtainedItems.remove(canonicalItemId);
            changed |= seenItems.remove(itemId);
            changed |= seenItems.remove(canonicalItemId);
            changed |= obtainedItemOrder.remove(itemId) != null;
            changed |= obtainedItemOrder.remove(canonicalItemId) != null;
        }

        if (changed)
        {
            notifyCacheChanged();
        }
    }

    private void reset()
    {
        obtainedItems.clear();
        seenItems.clear();
        obtainedItemOrder.clear();
        nextObtainedItemOrder = 1L;
        pendingAncientPageDropCountSinceLastSync = 0;
        pendingMedallionFragmentDropCountSinceLastSync = 0;
        cacheChangeBatchDepth = 0;
        cacheChangePending = false;
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

    private boolean markObtainedItem(int itemId, Long restoredOrder)
    {
        int canonicalItemId = canonicalCollectionLogItemId(itemId);
        boolean changed = obtainedItems.add(itemId);
        changed |= obtainedItems.add(canonicalItemId);

        Long order = restoredOrder != null && restoredOrder > 0 ? restoredOrder : existingOrder(itemId, canonicalItemId);
        if (order == null)
        {
            order = nextObtainedItemOrder++;
            changed = true;
        }

        changed |= obtainedItemOrder.putIfAbsent(itemId, order) == null;
        changed |= obtainedItemOrder.putIfAbsent(canonicalItemId, order) == null;
        nextObtainedItemOrder = Math.max(nextObtainedItemOrder, order + 1);
        return changed;
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

    public int canonicalItemId(int itemId)
    {
        return canonicalCollectionLogItemId(itemId);
    }

    private void notifyCacheChanged()
    {
        if (cacheChangeBatchDepth > 0)
        {
            cacheChangePending = true;
            return;
        }

        if (cacheChangeListener != null)
        {
            cacheChangeListener.run();
        }
    }
}
