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
