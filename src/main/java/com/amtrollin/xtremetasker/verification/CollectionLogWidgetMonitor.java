package com.amtrollin.xtremetasker.verification;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Captures obtained collection log item IDs by listening to script 4100, which fires
 * once per item slot whenever a collection log page is rendered. args[1] = item ID,
 * args[2] = quantity (0 means not yet obtained).
 */
@Slf4j
@Singleton
public class CollectionLogWidgetMonitor
{
    // Script fired for each item slot in the collection log. args[1]=itemId, args[2]=quantity.
    private static final int CLOG_ITEM_DRAW_SCRIPT = 4100;
    // Script fired when the collection log interface is set up / a page is loaded.
    private static final int CLOG_SETUP_SCRIPT = 7797;
    // Collection log refresh script. We only trust it while the real CLOG UI is active.
    private static final int CLOG_AUTO_SCAN_SCRIPT = 2240;

    @Inject
    private Client client;

    @Inject
    private EventBus eventBus;

    @Inject
    private CollectionLogService collectionLogService;

    private int tickClogScriptFired = -1;
    private int openSetupCacheBatches = 0;
    private int setupCacheBatchOpenedTick = -1;
    private int ignoredOutsideCollectionLogCount = 0;
    private int loggedAutoScanCount = 0;
    private boolean isAutoScanInProgress = false;

    public void startUp()
    {
        eventBus.register(this);
        reset();
    }

    public void shutDown()
    {
        eventBus.unregister(this);
        forceCloseSetupCacheBatches();
        reset();
    }

    private void reset()
    {
        tickClogScriptFired = -1;
        openSetupCacheBatches = 0;
        setupCacheBatchOpenedTick = -1;
        ignoredOutsideCollectionLogCount = 0;
        loggedAutoScanCount = 0;
        isAutoScanInProgress = false;
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        int currentTick = client.getTickCount();
        if (openSetupCacheBatches > 0 && setupCacheBatchOpenedTick + 2 < currentTick)
        {
            forceCloseSetupCacheBatches();
        }

        if (tickClogScriptFired != -1 && tickClogScriptFired + 2 < currentTick)
        {
            tickClogScriptFired = -1;
        }

        if (isAutoScanInProgress && tickClogScriptFired != -1 && tickClogScriptFired + 5 < currentTick)
        {
            isAutoScanInProgress = false;
        }
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event)
    {
        if (event.getScriptId() == CLOG_AUTO_SCAN_SCRIPT)
        {
            if (isCollectionLogInterfaceActive())
            {
                logAutoScan("post", true, null);
                scanCollectionLogEntryItemsWidget();
                collectionLogService.markSyncSeen();
            }
            else
            {
                logAutoScan("post", false, null);
            }
            return;
        }

        if (event.getScriptId() == ScriptID.COLLECTION_DRAW_LIST)
        {
            scanCollectionLogEntryItemsWidget();
            return;
        }

        if (event.getScriptId() != CLOG_SETUP_SCRIPT)
        {
            return;
        }

        endSetupCacheBatch();
        runCollectionLogAutoScanIfAvailable();
    }

    @Subscribe
    public void onScriptPreFired(ScriptPreFired event)
    {
        if (event.getScriptId() == CLOG_SETUP_SCRIPT)
        {
            beginSetupCacheBatch();
            return;
        }

        if (event.getScriptId() == CLOG_AUTO_SCAN_SCRIPT)
        {
            Object[] args = event.getScriptEvent() == null ? null : event.getScriptEvent().getArguments();
            logAutoScan("pre", isCollectionLogInterfaceActive(), args);
            return;
        }

        if (event.getScriptId() != CLOG_ITEM_DRAW_SCRIPT)
        {
            return;
        }

        if (!isCollectionLogInterfaceActive())
        {
            logIgnoredItemDrawOutsideCollectionLog(event);
            return;
        }

        tickClogScriptFired = client.getTickCount();

        Object[] args = event.getScriptEvent() == null ? null : event.getScriptEvent().getArguments();
        if (args == null || args.length < 3)
        {
            return;
        }

        Integer itemId = scriptIntArg(args, 1);
        Integer quantity = scriptIntArg(args, 2);
        if (itemId == null || quantity == null)
        {
            return;
        }

        boolean wasObtained = itemId > 0 && collectionLogService.isItemObtained(itemId);
        if (itemId > 0 && (quantity > 0 || wasObtained))
        {
            log.info("XtremeTasker CLOG diagnostic: script4100 itemId={} quantity={} cachedObtainedBefore={}",
                    itemId,
                    quantity,
                    wasObtained);
        }

        if (itemId > 0)
        {
            collectionLogService.storeSeenItem(itemId);
        }

        // quantity > 0 means the item has been obtained at least once.
        if (quantity > 0)
        {
            collectionLogService.storeItem(itemId);
        }
        else
        {
            collectionLogService.storeUnobtainedItem(itemId);
        }
        collectionLogService.markSyncSeen();
    }

    private void scanCollectionLogEntryItemsWidget()
    {
        Widget items = client == null ? null : client.getWidget(ComponentID.COLLECTION_LOG_ENTRY_ITEMS);
        if (items == null || items.isHidden())
        {
            return;
        }

        Set<Widget> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        scanCollectionLogItemWidget(items, visited);
    }

    private void runCollectionLogAutoScanIfAvailable()
    {
        if (isAutoScanInProgress || !isCollectionLogInterfaceActive())
        {
            return;
        }

        isAutoScanInProgress = true;
        tickClogScriptFired = client.getTickCount();
        collectionLogService.beginCacheChangeBatch();
        try
        {
            log.info("XtremeTasker CLOG diagnostic: running gated script2240 activeCollectionLog=true");
            client.runScript(CLOG_AUTO_SCAN_SCRIPT);
            scanCollectionLogEntryItemsWidget();
            collectionLogService.markSyncSeen();
        }
        finally
        {
            collectionLogService.endCacheChangeBatch();
            isAutoScanInProgress = false;
        }
    }

    private void scanCollectionLogItemWidget(Widget widget, Set<Widget> visited)
    {
        if (widget == null || !visited.add(widget))
        {
            return;
        }

        int itemId = widget.getItemId();
        if (itemId > 0)
        {
            if (collectionLogService.isItemObtained(itemId))
            {
                log.info("XtremeTasker CLOG diagnostic: widget seen-only cachedObtained itemId={} widgetQuantity={} widgetId={} childIndex={}",
                        itemId,
                        widget.getItemQuantity(),
                        widget.getId(),
                        widget.getIndex());
            }
            collectionLogService.storeSeenItem(itemId);
        }

        scanCollectionLogItemWidgets(widget.getChildren(), visited);
        scanCollectionLogItemWidgets(widget.getDynamicChildren(), visited);
        scanCollectionLogItemWidgets(widget.getStaticChildren(), visited);
        scanCollectionLogItemWidgets(widget.getNestedChildren(), visited);
    }

    private void scanCollectionLogItemWidgets(Widget[] widgets, Set<Widget> visited)
    {
        if (widgets == null || widgets.length == 0)
        {
            return;
        }

        for (Widget widget : widgets)
        {
            scanCollectionLogItemWidget(widget, visited);
        }
    }

    private boolean isCollectionLogInterfaceActive()
    {
        return openSetupCacheBatches > 0
                || isWidgetVisible(ComponentID.COLLECTION_LOG_CONTAINER)
                || isWidgetVisible(ComponentID.COLLECTION_LOG_ENTRY_ITEMS);
    }

    private boolean isWidgetVisible(int componentId)
    {
        Widget widget = client == null ? null : client.getWidget(componentId);
        return widget != null && !widget.isHidden();
    }

    private void logIgnoredItemDrawOutsideCollectionLog(ScriptPreFired event)
    {
        if (ignoredOutsideCollectionLogCount >= 20)
        {
            return;
        }

        Object[] args = event.getScriptEvent().getArguments();
        Integer itemId = scriptIntArg(args, 1);
        Integer quantity = scriptIntArg(args, 2);
        if (itemId == null || itemId <= 0 || quantity == null || quantity <= 0)
        {
            return;
        }

        ignoredOutsideCollectionLogCount++;
        log.info("XtremeTasker CLOG diagnostic: ignored script4100 outside collection log itemId={} quantity={}",
                itemId,
                quantity);
    }

    private void logAutoScan(String phase, boolean activeCollectionLog, Object[] args)
    {
        if (loggedAutoScanCount >= 20)
        {
            return;
        }

        loggedAutoScanCount++;
        log.info("XtremeTasker CLOG diagnostic: script2240 {} activeCollectionLog={} containerVisible={} entryItemsVisible={} args={}",
                phase,
                activeCollectionLog,
                isWidgetVisible(ComponentID.COLLECTION_LOG_CONTAINER),
                isWidgetVisible(ComponentID.COLLECTION_LOG_ENTRY_ITEMS),
                args == null ? "<unavailable>" : argsSummary(args));
    }

    private static String argsSummary(Object[] args)
    {
        if (args == null)
        {
            return "<null>";
        }

        StringBuilder sb = new StringBuilder("[");
        int limit = Math.min(args.length, 8);
        for (int i = 0; i < limit; i++)
        {
            if (i > 0)
            {
                sb.append(", ");
            }
            Object arg = args[i];
            sb.append(i).append('=').append(arg == null ? "<null>" : arg + " (" + arg.getClass().getSimpleName() + ")");
        }
        if (args.length > limit)
        {
            sb.append(", ... len=").append(args.length);
        }
        sb.append(']');
        return sb.toString();
    }

    private static Integer scriptIntArg(Object[] args, int index)
    {
        if (args == null || index < 0 || index >= args.length || !(args[index] instanceof Number))
        {
            return null;
        }

        return ((Number) args[index]).intValue();
    }

    private void beginSetupCacheBatch()
    {
        if (openSetupCacheBatches == 0)
        {
            setupCacheBatchOpenedTick = client.getTickCount();
        }

        openSetupCacheBatches++;
        collectionLogService.beginCacheChangeBatch();
    }

    private void endSetupCacheBatch()
    {
        if (openSetupCacheBatches <= 0)
        {
            openSetupCacheBatches = 0;
            setupCacheBatchOpenedTick = -1;
            return;
        }

        openSetupCacheBatches--;
        collectionLogService.endCacheChangeBatch();
        if (openSetupCacheBatches == 0)
        {
            setupCacheBatchOpenedTick = -1;
        }
    }

    private void forceCloseSetupCacheBatches()
    {
        while (openSetupCacheBatches > 0)
        {
            openSetupCacheBatches--;
            collectionLogService.endCacheChangeBatch();
        }
        setupCacheBatchOpenedTick = -1;
    }
}
