package com.amtrollin.xtremetasker.verification;

import net.runelite.api.Client;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.client.callback.ClientThread;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.Queue;

import static org.junit.Assert.assertEquals;

public class CollectionLogWidgetMonitorTest
{
    @Test
    public void collectionLogSetupDefersAutoScanUntilTickEnd() throws Exception
    {
        FakeClient client = new FakeClient();
        CapturingClientThread clientThread = new CapturingClientThread();
        CollectionLogWidgetMonitor monitor = newMonitor(client, clientThread);

        monitor.onScriptPostFired(new ScriptPostFired(7797));
        monitor.onScriptPostFired(new ScriptPostFired(7797));

        assertEquals("setup scripts should queue only one scan", 1, clientThread.queuedCount());
        assertEquals("runScript must not be called from the script callback", 0, client.runScriptCount);

        clientThread.runNext();

        assertEquals(1, client.runScriptCount);
        assertEquals(2240, client.lastScriptId);
    }

    private static CollectionLogWidgetMonitor newMonitor(FakeClient client, ClientThread clientThread) throws Exception
    {
        CollectionLogWidgetMonitor monitor = new CollectionLogWidgetMonitor();
        setField(monitor, "client", client.proxy());
        setField(monitor, "clientThread", clientThread);
        return monitor;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception
    {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class CapturingClientThread extends ClientThread
    {
        private final Queue<Runnable> queuedRunnables = new ArrayDeque<>();

        @Override
        public void invokeAtTickEnd(Runnable runnable)
        {
            queuedRunnables.add(runnable);
        }

        int queuedCount()
        {
            return queuedRunnables.size();
        }

        void runNext()
        {
            queuedRunnables.remove().run();
        }
    }

    private static final class FakeClient
    {
        private int runScriptCount;
        private int lastScriptId;

        Client proxy()
        {
            return (Client) Proxy.newProxyInstance(
                    Client.class.getClassLoader(),
                    new Class<?>[]{Client.class},
                    this::invoke
            );
        }

        private Object invoke(Object proxy, Method method, Object[] args)
        {
            if ("runScript".equals(method.getName()))
            {
                runScriptCount++;
                Object[] scriptArgs = (Object[]) args[0];
                lastScriptId = (int) scriptArgs[0];
                return null;
            }

            return defaultValue(method.getReturnType());
        }

        private static Object defaultValue(Class<?> returnType)
        {
            if (!returnType.isPrimitive())
            {
                return null;
            }

            if (returnType == boolean.class)
            {
                return false;
            }
            if (returnType == char.class)
            {
                return '\0';
            }
            if (returnType == byte.class)
            {
                return (byte) 0;
            }
            if (returnType == short.class)
            {
                return (short) 0;
            }
            if (returnType == int.class)
            {
                return 0;
            }
            if (returnType == long.class)
            {
                return 0L;
            }
            if (returnType == float.class)
            {
                return 0f;
            }
            if (returnType == double.class)
            {
                return 0d;
            }
            return null;
        }
    }
}
