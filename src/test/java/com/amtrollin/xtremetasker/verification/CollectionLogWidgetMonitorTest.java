package com.amtrollin.xtremetasker.verification;

import net.runelite.api.Client;
import net.runelite.api.events.ScriptPostFired;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class CollectionLogWidgetMonitorTest
{
    @Test
    public void collectionLogSetupRunsFullScanBeforeCallbackReturns() throws Exception
    {
        AtomicInteger scannedScript = new AtomicInteger(-1);
        Client client = (Client) Proxy.newProxyInstance(
                Client.class.getClassLoader(),
                new Class<?>[]{Client.class},
                (proxy, method, args) -> {
                    if ("getTickCount".equals(method.getName()))
                    {
                        return 100;
                    }
                    if ("runScript".equals(method.getName()))
                    {
                        Object[] scriptArgs = (Object[]) args[0];
                        scannedScript.set((Integer) scriptArgs[0]);
                        return null;
                    }
                    return primitiveDefault(method.getReturnType());
                });

        CollectionLogWidgetMonitor monitor = new CollectionLogWidgetMonitor();
        setField(monitor, "client", client);
        setField(monitor, "collectionLogService", new CollectionLogService());

        monitor.onScriptPostFired(new ScriptPostFired(7797));

        assertEquals("Full CLOG scan must run while setup/render context is active", 2240, scannedScript.get());
    }

    @Test
    public void hostCollectionLogCannotCaptureItemsOrMarkFullSync() throws Exception
    {
        AtomicInteger hostBook = new AtomicInteger(1);
        AtomicInteger scripts = new AtomicInteger();
        Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
                (proxy, method, args) -> {
                    if ("getVarbitValue".equals(method.getName()))
                    {
                        assertEquals(net.runelite.api.gameval.VarbitID.COLLECTION_POH_HOST_BOOK_OPEN, args[0]);
                        return hostBook.get();
                    }
                    if ("getTickCount".equals(method.getName())) return 100;
                    if ("runScript".equals(method.getName())) scripts.incrementAndGet();
                    return primitiveDefault(method.getReturnType());
                });
        CollectionLogService service = new CollectionLogService();
        CollectionLogWidgetMonitor monitor = new CollectionLogWidgetMonitor();
        setField(monitor, "client", client);
        setField(monitor, "collectionLogService", service);
        net.runelite.api.ScriptEvent script = (net.runelite.api.ScriptEvent) Proxy.newProxyInstance(
                net.runelite.api.ScriptEvent.class.getClassLoader(), new Class<?>[]{net.runelite.api.ScriptEvent.class},
                (proxy, method, args) -> "getArguments".equals(method.getName())
                        ? new Object[]{4100, 10878, 1} : primitiveDefault(method.getReturnType()));
        net.runelite.api.events.ScriptPreFired item = new net.runelite.api.events.ScriptPreFired(4100);
        item.setScriptEvent(script);
        monitor.onScriptPreFired(new net.runelite.api.events.ScriptPreFired(7797));
        monitor.onScriptPreFired(item);
        monitor.onScriptPostFired(new ScriptPostFired(net.runelite.api.ScriptID.COLLECTION_DRAW_LIST));
        monitor.onScriptPostFired(new ScriptPostFired(7797));
        assertEquals(0, service.getCapturedItemCount());
        assertEquals(0, service.getSeenItemCount());
        assertEquals(false, service.hasFullSyncSeen());
        assertEquals(0, scripts.get());

        hostBook.set(0);
        monitor.onScriptPreFired(item);
        monitor.onScriptPreFired(new net.runelite.api.events.ScriptPreFired(7797));
        monitor.onScriptPostFired(new ScriptPostFired(7797));
        assertEquals(true, service.isItemObtained(10878));
        assertEquals(true, service.hasFullSyncSeen());
        assertEquals(1, scripts.get());
    }

    private static void setField(Object target, String name, Object value) throws Exception
    {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object primitiveDefault(Class<?> type)
    {
        if (!type.isPrimitive() || type == void.class)
        {
            return null;
        }
        if (type == boolean.class)
        {
            return false;
        }
        if (type == char.class)
        {
            return '\0';
        }
        if (type == byte.class)
        {
            return (byte) 0;
        }
        if (type == short.class)
        {
            return (short) 0;
        }
        if (type == int.class)
        {
            return 0;
        }
        if (type == long.class)
        {
            return 0L;
        }
        if (type == float.class)
        {
            return 0F;
        }
        return 0D;
    }
}
