package com.amtrollin.xtremetasker.verification;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.ScriptPreFired;
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
@Slf4j
public class CollectionLogWidgetMonitor
{
    // Script fired for each item slot in the collection log. args[1]=itemId, args[2]=quantity.
    private static final int CLOG_ITEM_DRAW_SCRIPT = 4100;

    @Inject
    private EventBus eventBus;

    @Inject
    private CollectionLogService collectionLogService;

    public void startUp()
    {
        eventBus.register(this);
    }

    public void shutDown()
    {
        eventBus.unregister(this);
    }

    @Subscribe
    public void onScriptPreFired(ScriptPreFired event)
    {
        if (event.getScriptId() != CLOG_ITEM_DRAW_SCRIPT)
        {
            return;
        }

        Object[] args = event.getScriptEvent().getArguments();
        if (args == null || args.length < 3)
        {
            return;
        }

        int itemId = (int) args[1];
        int quantity = (int) args[2];

        if (quantity > 0 || isTeaFlaskDiagnosticRange(itemId))
        {
            log.info("Xtreme Tasker tea-flask-clog-diagnostic widget script=4100 itemId={} quantity={}",
                    itemId, quantity);
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
    }

    private static boolean isTeaFlaskDiagnosticRange(int itemId)
    {
        return (itemId >= 10850 && itemId <= 10890)
                || (itemId >= 25600 && itemId <= 25630);
    }
}
