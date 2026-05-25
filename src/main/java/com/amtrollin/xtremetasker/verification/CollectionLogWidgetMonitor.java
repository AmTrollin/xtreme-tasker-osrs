package com.amtrollin.xtremetasker.verification;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Captures obtained collection log item IDs by listening to script 4100, which fires
 * once per item slot whenever a collection log page is rendered. args[1] = item ID,
 * args[2] = quantity (0 means not yet obtained).
 *
 * Also auto-triggers a page scan when the collection log is opened (script 7797),
 * so items are captured without requiring manual page navigation.
 *
 * Approach sourced from RuneProfile / WikiSync / OSRS-Taskman plugins (BSD 2-Clause).
 */
@Singleton
public class CollectionLogWidgetMonitor
{
    // Script fired for each item slot in the collection log. args[1]=itemId, args[2]=quantity.
    private static final int CLOG_ITEM_DRAW_SCRIPT = 4100;
    // Script fired when the collection log interface is set up / a page is loaded.
    private static final int CLOG_SETUP_SCRIPT = 7797;
    // Widget ID of the collection log search/navigation control used to trigger a re-render.
    private static final int CLOG_SEARCH_WIDGET_ID = 40697932;
    private static final String COLLECTION_LOG_LABEL = "Collection Log";

    @Inject
    private Client client;

    @Inject
    private EventBus eventBus;

    @Inject
    private CollectionLogService collectionLogService;

    private int tickClogScriptFired = -1;
    private boolean isAutoScanInProgress = false;
    private int pendingCollectionLogClickTicks = -1;

    public void startUp()
    {
        eventBus.register(this);
        reset();
    }

    public void shutDown()
    {
        eventBus.unregister(this);
    }

    private void reset()
    {
        tickClogScriptFired = -1;
        isAutoScanInProgress = false;
        pendingCollectionLogClickTicks = -1;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() != GameState.HOPPING && event.getGameState() != GameState.LOGGED_IN)
        {
            reset();
        }
    }

    /**
     * After 2 ticks with no script-4100 activity, the auto-scan is considered complete.
     * Resetting the flag allows another auto-scan if the clog is closed and reopened.
     */
    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (pendingCollectionLogClickTicks >= 0)
        {
            pendingCollectionLogClickTicks--;
            if (pendingCollectionLogClickTicks <= 0)
            {
                pendingCollectionLogClickTicks = -1;
                clickVisibleCollectionLogWidget();
            }
        }

        if (tickClogScriptFired != -1 && tickClogScriptFired + 2 < client.getTickCount())
        {
            tickClogScriptFired = -1;
            isAutoScanInProgress = false;
        }
    }

    /**
     * Best-effort helper used before CLOG sync. If the collection log is already open,
     * refresh its current page. Otherwise click a visible Collection Log button, or open
     * the journal tab and try that click on the next tick.
     */
    public boolean requestCollectionLogOpenOrRefresh()
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return false;
        }

        if (isCollectionLogOpen())
        {
            requestCurrentPageScan();
            return true;
        }

        if (clickVisibleCollectionLogWidget())
        {
            return true;
        }

        if (clickJournalTabWidget())
        {
            pendingCollectionLogClickTicks = 1;
            return true;
        }

        return false;
    }

    /**
     * When the collection log page is set up, auto-trigger a re-render so script 4100
     * fires for all items on the current page — without requiring manual navigation.
     * Skips if triggered from a POH Adventure Log (viewing another player's clog).
     */
    @Subscribe
    public void onScriptPostFired(ScriptPostFired event)
    {
        if (event.getScriptId() != CLOG_SETUP_SCRIPT)
        {
            return;
        }

        if (isAutoScanInProgress)
        {
            return;
        }

        // Don't scan when viewing another player's clog via POH adventure log.
        if (client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) == 1)
        {
            return;
        }

        isAutoScanInProgress = true;
        requestCurrentPageScan();
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

        // quantity > 0 means the item has been obtained at least once.
        if (quantity > 0)
        {
            collectionLogService.storeItem(itemId);
        }
    }

    private boolean isCollectionLogOpen()
    {
        Widget collectionLog = client.getWidget(WidgetInfo.COLLECTION_LOG);
        return isVisible(collectionLog);
    }

    private void requestCurrentPageScan()
    {
        client.menuAction(-1, CLOG_SEARCH_WIDGET_ID, MenuAction.CC_OP, 1, -1, "Search", null);
        client.runScript(2240);
    }

    private boolean clickJournalTabWidget()
    {
        return clickWidget(client.getWidget(WidgetInfo.FIXED_VIEWPORT_QUESTS_TAB), "Quest List", 1)
                || clickWidget(client.getWidget(WidgetInfo.RESIZABLE_VIEWPORT_QUESTS_TAB), "Quest List", 1)
                || clickWidget(client.getWidget(WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_QUESTS_ICON), "Quest List", 1);
    }

    private boolean clickVisibleCollectionLogWidget()
    {
        Widget[] roots = client.getWidgetRoots();
        if (roots == null)
        {
            return false;
        }

        for (Widget root : roots)
        {
            if (clickVisibleCollectionLogWidget(root))
            {
                return true;
            }
        }
        return false;
    }

    private boolean clickVisibleCollectionLogWidget(Widget widget)
    {
        if (!isVisible(widget))
        {
            return false;
        }

        if (widget.getId() != WidgetInfo.COLLECTION_LOG.getId()
                && widget.getId() != WidgetInfo.COLLECTION_LOG_ENTRY.getId()
                && widgetHasCollectionLogAction(widget))
        {
            int op = collectionLogActionOp(widget);
            return clickWidget(widget, COLLECTION_LOG_LABEL, op);
        }

        Widget[] children = widget.getChildren();
        if (children != null)
        {
            for (Widget child : children)
            {
                if (clickVisibleCollectionLogWidget(child))
                {
                    return true;
                }
            }
        }

        Widget[] dynamicChildren = widget.getDynamicChildren();
        if (dynamicChildren != null)
        {
            for (Widget child : dynamicChildren)
            {
                if (clickVisibleCollectionLogWidget(child))
                {
                    return true;
                }
            }
        }

        Widget[] nestedChildren = widget.getNestedChildren();
        if (nestedChildren != null)
        {
            for (Widget child : nestedChildren)
            {
                if (clickVisibleCollectionLogWidget(child))
                {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean widgetHasCollectionLogAction(Widget widget)
    {
        if (containsCollectionLog(widget.getText()) || containsCollectionLog(widget.getName()))
        {
            return hasAnyAction(widget);
        }

        String[] actions = widget.getActions();
        if (actions == null)
        {
            return false;
        }

        for (String action : actions)
        {
            if (containsCollectionLog(action))
            {
                return true;
            }
        }
        return false;
    }

    private int collectionLogActionOp(Widget widget)
    {
        String[] actions = widget.getActions();
        if (actions == null)
        {
            return 1;
        }

        for (int i = 0; i < actions.length; i++)
        {
            if (containsCollectionLog(actions[i]))
            {
                return i + 1;
            }
        }
        return 1;
    }

    private boolean clickWidget(Widget widget, String action, int op)
    {
        if (!isVisible(widget))
        {
            return false;
        }

        client.menuAction(-1, widget.getId(), MenuAction.CC_OP, Math.max(1, op), -1, action, null);
        return true;
    }

    private boolean isVisible(Widget widget)
    {
        return widget != null
                && !widget.isHidden()
                && widget.getBounds() != null
                && widget.getBounds().width > 0
                && widget.getBounds().height > 0;
    }

    private boolean hasAnyAction(Widget widget)
    {
        String[] actions = widget.getActions();
        if (actions == null)
        {
            return false;
        }

        for (String action : actions)
        {
            if (action != null && !action.trim().isEmpty())
            {
                return true;
            }
        }
        return false;
    }

    private boolean containsCollectionLog(String text)
    {
        return text != null && text.toLowerCase(java.util.Locale.ROOT).contains("collection log");
    }
}
