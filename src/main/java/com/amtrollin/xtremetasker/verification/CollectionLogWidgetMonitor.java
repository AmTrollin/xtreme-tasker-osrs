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
    @Inject
    private Client client;

    @Inject
    private EventBus eventBus;

    @Inject
    private CollectionLogService collectionLogService;

    private int tickClogScriptFired = -1;
    private int openSetupCacheBatches = 0;
    private int setupCacheBatchOpenedTick = -1;
    private int loggedItemDrawCount = 0;
    private int capturedSeenThisSession = 0;
    private int capturedObtainedThisSession = 0;

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
        loggedItemDrawCount = 0;
        capturedSeenThisSession = 0;
        capturedObtainedThisSession = 0;
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
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event)
    {
        if (event.getScriptId() == ScriptID.COLLECTION_DRAW_LIST)
        {
            log.debug("XtremeTasker CLOG sync debug: collection draw-list script {} post-fired; scanning visible widget page",
                    ScriptID.COLLECTION_DRAW_LIST);
            scanCollectionLogEntryItemsWidget();
            return;
        }

        if (event.getScriptId() != CLOG_SETUP_SCRIPT)
        {
            return;
        }

        log.debug("XtremeTasker CLOG sync debug: setup script {} post-fired; open batches before end={}",
                CLOG_SETUP_SCRIPT, openSetupCacheBatches);
        endSetupCacheBatch();

        // The visible page is scanned by COLLECTION_DRAW_LIST and item draw script 4100.
        // Do not run the old full auto-scan here; it can report stale/off-page item state.
    }

    @Subscribe
    public void onScriptPreFired(ScriptPreFired event)
    {
        if (event.getScriptId() == CLOG_SETUP_SCRIPT)
        {
            log.debug("XtremeTasker CLOG sync debug: setup script {} pre-fired at tick {}",
                    CLOG_SETUP_SCRIPT, client.getTickCount());
            beginSetupCacheBatch();
            return;
        }

        if (event.getScriptId() != CLOG_ITEM_DRAW_SCRIPT)
        {
            return;
        }

        tickClogScriptFired = client.getTickCount();

        Object[] args = event.getScriptEvent().getArguments();
        if (args == null || args.length < 3)
        {
            log.debug("XtremeTasker CLOG sync debug: item draw script {} fired with missing args length={}",
                    CLOG_ITEM_DRAW_SCRIPT, args == null ? -1 : args.length);
            return;
        }

        Integer itemId = scriptIntArg(args, 1);
        Integer quantity = scriptIntArg(args, 2);
        if (itemId == null || quantity == null)
        {
            log.debug("XtremeTasker CLOG sync debug: item draw script {} fired with nonnumeric item/quantity args length={} arg1={} arg2={}",
                    CLOG_ITEM_DRAW_SCRIPT, args.length, argSummary(args, 1), argSummary(args, 2));
            return;
        }

        if (loggedItemDrawCount < 60)
        {
            loggedItemDrawCount++;
            log.debug("XtremeTasker CLOG sync debug: item draw slot itemId={} quantity={} tick={} argsLength={}",
                    itemId, quantity, tickClogScriptFired, args.length);
        }

        if (itemId > 0)
        {
            collectionLogService.storeSeenItem(itemId);
            capturedSeenThisSession++;
        }

        // quantity > 0 means the item has been obtained at least once.
        if (quantity > 0)
        {
            collectionLogService.storeItem(itemId);
            capturedObtainedThisSession++;
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
            log.debug("XtremeTasker CLOG sync debug: entry items widget unavailable after draw-list scan");
            return;
        }

        WidgetScanCount count = new WidgetScanCount();
        Set<Widget> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        scanCollectionLogItemWidget(items, visited, count);
        log.debug("XtremeTasker CLOG sync debug: widget fallback scan found itemWidgets={} seenCaptured={} obtainedCaptured={}",
                count.itemWidgets, count.seenCaptured, count.obtainedCaptured);
    }

    private void scanCollectionLogItemWidget(Widget widget, Set<Widget> visited, WidgetScanCount count)
    {
        if (widget == null || !visited.add(widget))
        {
            return;
        }

        int itemId = widget.getItemId();
        int quantity = widget.getItemQuantity();
        if (itemId > 0)
        {
            count.itemWidgets++;
            collectionLogService.storeSeenItem(itemId);
            capturedSeenThisSession++;
            count.seenCaptured++;
        }

        scanCollectionLogItemWidgets(widget.getChildren(), visited, count);
        scanCollectionLogItemWidgets(widget.getDynamicChildren(), visited, count);
        scanCollectionLogItemWidgets(widget.getStaticChildren(), visited, count);
        scanCollectionLogItemWidgets(widget.getNestedChildren(), visited, count);
    }

    private void scanCollectionLogItemWidgets(Widget[] widgets, Set<Widget> visited, WidgetScanCount count)
    {
        if (widgets == null || widgets.length == 0)
        {
            return;
        }

        for (Widget widget : widgets)
        {
            scanCollectionLogItemWidget(widget, visited, count);
        }
    }

    private static final class WidgetScanCount
    {
        private int itemWidgets;
        private int seenCaptured;
        private int obtainedCaptured;
    }

    private static Object argSummary(Object[] args, int index)
    {
        if (args == null || index < 0 || index >= args.length)
        {
            return "<missing>";
        }
        Object arg = args[index];
        return arg == null ? "<null>" : arg + " (" + arg.getClass().getSimpleName() + ")";
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
