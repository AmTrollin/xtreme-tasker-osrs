package com.amtrollin.xtremetasker.verification;

import lombok.extern.slf4j.Slf4j;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
@Slf4j
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
    private int scanRequestedTick = -1;
    private int scanSeenSlotCount = 0;
    private int scanObtainedSlotCount = 0;
    private final List<String> scanRawArgSamples = new ArrayList<>();
    private final List<String> scanSeenSamples = new ArrayList<>();
    private final List<String> scanObtainedSamples = new ArrayList<>();

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
        resetScanLogState();
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
            logScanSummary("item draw scripts settled");
            tickClogScriptFired = -1;
            isAutoScanInProgress = false;
        }

        if (isAutoScanInProgress
                && scanRequestedTick != -1
                && tickClogScriptFired == -1
                && scanRequestedTick + 4 < client.getTickCount())
        {
            log.info("CLOG scan requested but no item draw scripts fired within 4 ticks");
            isAutoScanInProgress = false;
            resetScanLogState();
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
            log.info("CLOG sync refresh requested while Collection Log is already open");
            requestCurrentPageScan();
            return true;
        }

        if (clickVisibleCollectionLogWidget())
        {
            log.info("CLOG sync clicked visible Collection Log widget");
            return true;
        }

        if (clickJournalTabWidget())
        {
            log.info("CLOG sync clicked journal tab; Collection Log click pending");
            pendingCollectionLogClickTicks = 1;
            return true;
        }

        log.info("CLOG sync could not find a visible Collection Log or journal widget to click");
        return false;
    }

    public boolean isCollectionLogScanInProgress()
    {
        if (pendingCollectionLogClickTicks >= 0)
        {
            return true;
        }

        if (isAutoScanInProgress)
        {
            return true;
        }

        return tickClogScriptFired != -1
                && tickClogScriptFired + 2 >= client.getTickCount();
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
            log.info("CLOG setup script fired while auto-scan was already in progress");
            return;
        }

        // Don't scan when viewing another player's clog via POH adventure log.
        if (client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) == 1)
        {
            log.info("CLOG setup script ignored because POH host book is open");
            return;
        }

        log.info("CLOG setup script fired; requesting current page scan");
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
            addSample(scanRawArgSamples, args == null ? "null" : Arrays.deepToString(args));
            return;
        }

        addSample(scanRawArgSamples, Arrays.deepToString(args));

        int itemId = (int) args[1];
        int quantity = (int) args[2];

        if (itemId > 0)
        {
            scanSeenSlotCount++;
            addSample(scanSeenSamples, itemId + "x" + quantity);
            collectionLogService.storeSeenItem(itemId);
        }

        // quantity > 0 means the item has been obtained at least once.
        if (quantity > 0)
        {
            scanObtainedSlotCount++;
            addSample(scanObtainedSamples, itemId + "x" + quantity);
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
        scanRequestedTick = client.getTickCount();
        scanSeenSlotCount = 0;
        scanObtainedSlotCount = 0;
        scanRawArgSamples.clear();
        scanSeenSamples.clear();
        scanObtainedSamples.clear();
        log.info("CLOG current page scan requested at tick {}", scanRequestedTick);
        client.menuAction(-1, CLOG_SEARCH_WIDGET_ID, MenuAction.CC_OP, 1, -1, "Search", null);
        client.runScript(2240);
    }

    private void addSample(List<String> samples, String value)
    {
        if (samples.size() < 12)
        {
            samples.add(value);
        }
    }

    private void logScanSummary(String reason)
    {
        log.info("CLOG scan summary ({}): seenSlots={}, obtainedSlots={}, rawArgSample={}, seenSample={}, obtainedSample={}",
                reason, scanSeenSlotCount, scanObtainedSlotCount, scanRawArgSamples, scanSeenSamples, scanObtainedSamples);
        resetScanLogState();
    }

    private void resetScanLogState()
    {
        scanRequestedTick = -1;
        scanSeenSlotCount = 0;
        scanObtainedSlotCount = 0;
        scanRawArgSamples.clear();
        scanSeenSamples.clear();
        scanObtainedSamples.clear();
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
