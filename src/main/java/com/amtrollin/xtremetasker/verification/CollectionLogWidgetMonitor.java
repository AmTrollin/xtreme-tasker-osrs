package com.amtrollin.xtremetasker.verification;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;

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
    // Script that refreshes collection log item slots for the current page.
    private static final int CLOG_AUTO_SCAN_SCRIPT = 2240;

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private EventBus eventBus;

    @Inject
    private CollectionLogService collectionLogService;

    private int tickClogScriptFired = -1;
    private boolean isAutoScanQueued = false;
    private boolean isAutoScanInProgress = false;

    public void startUp()
    {
        eventBus.register(this);
        reset();
    }

    public void shutDown()
    {
        eventBus.unregister(this);
        reset();
    }

    private void reset()
    {
        tickClogScriptFired = -1;
        isAutoScanQueued = false;
        isAutoScanInProgress = false;
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (tickClogScriptFired != -1 && tickClogScriptFired + 2 < client.getTickCount())
        {
            tickClogScriptFired = -1;
            isAutoScanInProgress = false;
        }
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event)
    {
        if (event.getScriptId() != CLOG_SETUP_SCRIPT)
        {
            return;
        }

        if (isAutoScanQueued || isAutoScanInProgress)
        {
            return;
        }

        // Don't scan when viewing another player's clog via POH adventure log.
        if (client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) == 1)
        {
            return;
        }

        isAutoScanQueued = true;
        clientThread.invokeAtTickEnd(this::runCollectionLogAutoScan);
    }

    private void runCollectionLogAutoScan()
    {
        if (!isAutoScanQueued)
        {
            return;
        }

        isAutoScanQueued = false;

        // Re-check after deferring because the visible log can change during the current script chain.
        if (client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) == 1)
        {
            return;
        }

        isAutoScanInProgress = true;
        tickClogScriptFired = client.getTickCount();
        client.runScript(CLOG_AUTO_SCAN_SCRIPT);
    }

    @Subscribe
    public void onScriptPreFired(ScriptPreFired event)
    {
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

        int itemId = (int) args[1];
        int quantity = (int) args[2];

        if (itemId > 0)
        {
            collectionLogService.storeSeenItem(itemId);
        }

        // quantity > 0 means the item has been obtained at least once.
        if (quantity > 0)
        {
            collectionLogService.storeItem(itemId);
        }
    }
}
