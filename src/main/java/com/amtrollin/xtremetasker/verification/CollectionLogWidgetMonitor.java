package com.amtrollin.xtremetasker.verification;
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
            scanCollectionLogEntryItemsWidget();
            return;
        }

        if (event.getScriptId() != CLOG_SETUP_SCRIPT)
        {
            return;
        }

        endSetupCacheBatch();
    }

    @Subscribe
    public void onScriptPreFired(ScriptPreFired event)
    {
        if (event.getScriptId() == CLOG_SETUP_SCRIPT)
        {
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
            return;
        }

        Integer itemId = scriptIntArg(args, 1);
        Integer quantity = scriptIntArg(args, 2);
        if (itemId == null || quantity == null)
        {
            return;
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

    private void scanCollectionLogItemWidget(Widget widget, Set<Widget> visited)
    {
        if (widget == null || !visited.add(widget))
        {
            return;
        }

        int itemId = widget.getItemId();
        if (itemId > 0)
        {
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
