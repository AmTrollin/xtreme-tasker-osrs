package com.amtrollin.xtremetasker.verification;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.ScriptID;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.HashSet;
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
    // Legacy script that refreshes CLOG item slots beyond the visible page.
    private static final int CLOG_AUTO_SCAN_SCRIPT = 2240;
    private static final int CLOG_SEARCH_COMPONENT = 40697932;

    @Inject
    private Client client;

    @Inject
    private EventBus eventBus;

    @Inject
    private CollectionLogService collectionLogService;

    private int tickClogScriptFired = -1;
    private boolean isAutoScanInProgress = false;
    private boolean isExecutingClogAutoScanScript = false;
    private int openSetupCacheBatches = 0;
    private int setupCacheBatchOpenedTick = -1;
    private int loggedItemDrawCount = 0;
    private final Set<Integer> loggedCollectionLogScriptIds = new HashSet<>();
    private final Set<Integer> loggedAutoScanNestedScriptIds = new HashSet<>();
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
        isAutoScanInProgress = false;
        isExecutingClogAutoScanScript = false;
        openSetupCacheBatches = 0;
        setupCacheBatchOpenedTick = -1;
        loggedItemDrawCount = 0;
        loggedCollectionLogScriptIds.clear();
        loggedAutoScanNestedScriptIds.clear();
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
            isAutoScanInProgress = false;
            isExecutingClogAutoScanScript = false;
        }
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event)
    {
        logCollectionLogScriptDiscovery("post", event.getScriptId());

        if (event.getScriptId() == CLOG_AUTO_SCAN_SCRIPT)
        {
            isExecutingClogAutoScanScript = false;
        }

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

        if (isAutoScanInProgress)
        {
            log.debug("XtremeTasker CLOG sync debug: auto scan already in progress");
            return;
        }

        // Don't scan when viewing another player's clog via POH adventure log.
        if (client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) == 1)
        {
            log.debug("XtremeTasker CLOG sync debug: skipped auto scan because POH host book varbit is set");
            return;
        }

        runCollectionLogAutoScan();
    }

    private void runCollectionLogAutoScan()
    {
        isAutoScanInProgress = true;
        tickClogScriptFired = client.getTickCount();
        int seenBefore = collectionLogService.getSeenItemCount();
        int obtainedBefore = collectionLogService.getCapturedItemCount();
        collectionLogService.beginCacheChangeBatch();
        try
        {
            log.debug("XtremeTasker CLOG sync debug: starting full auto scan script={} searchComponent={}",
                    CLOG_AUTO_SCAN_SCRIPT, CLOG_SEARCH_COMPONENT);
            client.menuAction(-1, CLOG_SEARCH_COMPONENT, MenuAction.CC_OP, 1, -1, "Search", null);
            client.runScript(CLOG_AUTO_SCAN_SCRIPT);
            scanCollectionLogEntryItemsWidget();
            collectionLogService.markFullSyncSeen();
        }
        finally
        {
            collectionLogService.endCacheChangeBatch();
            log.info("XtremeTasker CLOG sync diagnostic: full auto scan seen {}->{} obtained {}->{} scriptSlotDraws={} seenCaptures={} obtainedCaptures={}",
                    seenBefore,
                    collectionLogService.getSeenItemCount(),
                    obtainedBefore,
                    collectionLogService.getCapturedItemCount(),
                    loggedItemDrawCount,
                    capturedSeenThisSession,
                    capturedObtainedThisSession);
        }
    }

    @Subscribe
    public void onScriptPreFired(ScriptPreFired event)
    {
        logCollectionLogScriptDiscovery("pre", event.getScriptId());

        if (event.getScriptId() == CLOG_AUTO_SCAN_SCRIPT)
        {
            isExecutingClogAutoScanScript = true;
            return;
        }

        logClogAutoScanNestedScript(event);

        if (event.getScriptId() == CLOG_SETUP_SCRIPT)
        {
            log.debug("XtremeTasker CLOG sync debug: setup script {} pre-fired", CLOG_SETUP_SCRIPT);
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
            log.debug("XtremeTasker CLOG sync debug: item draw slot itemId={} quantity={} argsLength={}",
                    itemId, quantity, args.length);
        }

        boolean wasObtained = itemId > 0 && collectionLogService.isItemObtained(itemId);

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
            if (!wasObtained)
            {
                log.info("XtremeTasker CLOG sync diagnostic: marked obtained source=script{} itemId={} quantity={}",
                        CLOG_ITEM_DRAW_SCRIPT,
                        itemId,
                        quantity);
            }
        }
        else
        {
            collectionLogService.storeUnobtainedItem(itemId);
            if (wasObtained)
            {
                log.info("XtremeTasker CLOG sync diagnostic: cleared obtained source=script{} itemId={} quantity={}",
                        CLOG_ITEM_DRAW_SCRIPT,
                        itemId,
                        quantity);
            }
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

    private void logCollectionLogScriptDiscovery(String phase, int scriptId)
    {
        if (client == null || !isCollectionLogInterfaceAvailable())
        {
            return;
        }

        if (!loggedCollectionLogScriptIds.add(scriptId))
        {
            return;
        }

        log.info("XtremeTasker CLOG script discovery: phase={} scriptId={} name={} collectionContainerVisible={} entryItemsVisible={}",
                phase,
                scriptId,
                collectionLogScriptName(scriptId),
                isWidgetVisible(ComponentID.COLLECTION_LOG_CONTAINER),
                isWidgetVisible(ComponentID.COLLECTION_LOG_ENTRY_ITEMS));
    }

    private void logClogAutoScanNestedScript(ScriptPreFired event)
    {
        if (!isExecutingClogAutoScanScript)
        {
            return;
        }

        int scriptId = event.getScriptId();
        if (scriptId == CLOG_SETUP_SCRIPT
                || scriptId == CLOG_AUTO_SCAN_SCRIPT
                || scriptId == ScriptID.COLLECTION_DRAW_LIST)
        {
            return;
        }

        if (!loggedAutoScanNestedScriptIds.add(scriptId))
        {
            return;
        }

        Object[] args = event.getScriptEvent() == null ? null : event.getScriptEvent().getArguments();
        log.info("XtremeTasker CLOG autoscan nested script: scriptId={} name={} argsCount={} args={}",
                scriptId,
                collectionLogScriptName(scriptId),
                args == null ? -1 : args.length,
                argsSummary(args));
    }

    private boolean isCollectionLogInterfaceAvailable()
    {
        return isWidgetVisible(ComponentID.COLLECTION_LOG_CONTAINER)
                || isWidgetVisible(ComponentID.COLLECTION_LOG_ENTRY_ITEMS)
                || openSetupCacheBatches > 0
                || isExecutingClogAutoScanScript
                || tickClogScriptFired == client.getTickCount();
    }

    private boolean isWidgetVisible(int componentId)
    {
        Widget widget = client.getWidget(componentId);
        return widget != null && !widget.isHidden();
    }

    private static String collectionLogScriptName(int scriptId)
    {
        if (scriptId == ScriptID.COLLECTION_DRAW_LIST)
        {
            return "COLLECTION_DRAW_LIST";
        }
        if (scriptId == CLOG_SETUP_SCRIPT)
        {
            return "COLLECTION_LOG_SETUP";
        }
        if (scriptId == CLOG_AUTO_SCAN_SCRIPT)
        {
            return "COLLECTION_LOG_AUTO_SCAN";
        }
        if (scriptId == CLOG_ITEM_DRAW_SCRIPT)
        {
            return "COLLECTION_LOG_ITEM_DRAW";
        }
        return "unknown";
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

            log.debug("XtremeTasker CLOG sync debug: widget fallback recorded seen-only itemId={} quantity={} widgetId={} childIndex={}",
                    itemId,
                    quantity,
                    widget.getId(),
                    widget.getIndex());
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
            sb.append(i).append('=').append(argSummary(args, i));
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
