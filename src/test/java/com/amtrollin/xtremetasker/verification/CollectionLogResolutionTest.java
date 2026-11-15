package com.amtrollin.xtremetasker.verification;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.ScriptID;
import net.runelite.api.VarClientStr;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPreFired;
import org.junit.Test;

import static org.junit.Assert.*;

public class CollectionLogResolutionTest
{
    @Test
    public void coldDefinitionsYieldAtTimeBudgetAndEventuallyResolve() throws Exception
    {
        Fixture f = new Fixture(100, id -> id == 100 ? "Target" : "Other");
        f.costPerRead = 3_000_000L;
        f.popup("Target");
        f.chat("Target");
        assertEquals(0, f.reads.get());
        for (int tick = 1; tick <= 100; tick++)
        {
            f.tick();
            assertEquals("A slow definition must yield for the rest of the tick", tick, f.reads.get());
            assertEquals(tick == 100, f.service.isItemObtained(100));
        }
        f.tick();
        assertEquals("Duplicate popup/chat must not restart the scan", 100, f.reads.get());
    }

    @Test
    public void burstOfDuplicateNotificationsWithTenThousandTrackedItemsIsBounded() throws Exception
    {
        Fixture f = new Fixture(10_000, id -> id == 10_000 ? "Target" : "Other");
        for (int i = 0; i < 100; i++)
        {
            f.popup("Target");
            f.chat("Target");
        }
        assertEquals(0, f.reads.get());
        int ticks = 0;
        while (!f.service.isItemObtained(10_000) && ticks++ < 400)
        {
            int before = f.reads.get();
            f.tick();
            assertTrue(f.reads.get() - before <= 32);
        }
        assertTrue(f.service.isItemObtained(10_000));
        assertEquals(10_000, f.reads.get());
        f.popup("Target");
        f.chat("Target");
        f.tick();
        assertEquals("Resolved names must use the cache", 10_000, f.reads.get());
        assertEquals(1, f.service.getCapturedItemCount());
    }

    @Test
    public void secondMatchOnLaterTickPreventsFalseUnlock() throws Exception
    {
        Fixture f = new Fixture(65, id -> id == 1 || id == 65 ? "Target" : "Other");
        f.popup("Target");
        for (int i = 0; i < 3; i++)
        {
            f.tick();
            assertEquals("Do not commit a match before checking every tracked slot", 0,
                    f.service.getCapturedItemCount());
        }
        assertEquals(65, f.reads.get());
    }

    @Test
    public void queuedDistinctDropsAreBothResolvedWithoutExceedingBudget() throws Exception
    {
        Fixture f = new Fixture(65, id -> id == 1 ? "First" : id == 65 ? "Second" : "Other");
        f.chat("First");
        f.popup("Second");
        for (int tick = 0; tick < 6; tick++)
        {
            int before = f.reads.get();
            f.tick();
            assertTrue(f.reads.get() - before <= 32);
        }
        assertTrue(f.service.isItemObtained(1));
        assertTrue(f.service.isItemObtained(65));
        assertTrue(f.service.getObtainedItemOrder(1) < f.service.getObtainedItemOrder(65));
    }

    @Test
    public void aliasesOfSameSlotDoNotCreateAmbiguousMatch() throws Exception
    {
        Fixture f = new Fixture(1, id -> "Green satchel");
        f.service.setTrackedItemIdsSupplier(() -> new int[]{10878, 25619});
        f.popup("Green satchel");
        f.tick();
        assertEquals(1, f.service.countObtained(new int[]{10878, 25619}));
    }

    @Test
    public void resetDiscardsPartiallyScannedMatchesAndQueuedDrops() throws Exception
    {
        Fixture f = new Fixture(65, id -> id == 1 ? "First" : "Second");
        f.popup("First");
        f.chat("Second");
        f.tick();
        assertEquals(32, f.reads.get());
        f.service.resetCachedItemIds();
        f.tick();
        assertEquals(32, f.reads.get());
        assertEquals(0, f.service.getCapturedItemCount());
    }

    private static final class Fixture
    {
        private final CollectionLogService service = new CollectionLogService();
        private final AtomicInteger reads = new AtomicInteger();
        private final AtomicLong clock = new AtomicLong();
        private long costPerRead;
        private String popupName;

        private Fixture(int count, IntFunction<String> names) throws Exception
        {
            Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(),
                    new Class<?>[]{Client.class}, (proxy, method, args) -> {
                        if (method.getName().equals("getVarcStrValue"))
                        {
                            return ((Integer) args[0]) == VarClientStr.NOTIFICATION_TOP_TEXT
                                    ? "Collection log" : "New item: " + popupName;
                        }
                        if (method.getName().equals("getItemDefinition"))
                        {
                            reads.incrementAndGet();
                            clock.addAndGet(costPerRead);
                            String name = names.apply((Integer) args[0]);
                            return Proxy.newProxyInstance(ItemComposition.class.getClassLoader(),
                                    new Class<?>[]{ItemComposition.class}, (item, itemMethod, itemArgs) -> {
                                        if (itemMethod.getName().equals("getName")) return name;
                                        throw new AssertionError(itemMethod.getName());
                                    });
                        }
                        throw new AssertionError(method.getName());
                    });
            setField("client", client);
            setField("nanoTime", (java.util.function.LongSupplier) clock::get);
            int[] ids = IntStream.rangeClosed(1, count).toArray();
            service.setTrackedItemIdsSupplier(() -> ids);
        }

        private void setField(String name, Object value) throws Exception
        {
            Field field = CollectionLogService.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(service, value);
        }

        private void popup(String name)
        {
            popupName = name;
            service.onScriptPreFired(new ScriptPreFired(ScriptID.NOTIFICATION_START));
            service.onScriptPreFired(new ScriptPreFired(ScriptID.NOTIFICATION_DELAY));
        }

        private void chat(String name)
        {
            ChatMessage message = new ChatMessage();
            message.setType(ChatMessageType.GAMEMESSAGE);
            message.setMessage("New collection log item: " + name + ".");
            service.onChatMessage(message);
        }

        private void tick()
        {
            service.onGameTick(new GameTick());
        }
    }
}
