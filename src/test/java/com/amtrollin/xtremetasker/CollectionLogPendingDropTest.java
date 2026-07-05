package com.amtrollin.xtremetasker;

import com.amtrollin.xtremetasker.verification.CollectionLogService;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;

public class CollectionLogPendingDropTest
{
    @Test
    public void ambiguousAncientPageDropsArePendingUntilFullSync()
    {
        CollectionLogService service = new CollectionLogService();

        service.onChatMessage(chat("New collection log item: Ancient page."));
        service.onChatMessage(chat("New collection log item: Ancient page"));

        assertEquals(2, service.getPendingAncientPageDropCountSinceLastSync());

        service.markFullSyncSeen();

        assertEquals(0, service.getPendingAncientPageDropCountSinceLastSync());
    }

    @Test
    public void ambiguousMedallionFragmentDropsArePendingUntilFullSync()
    {
        CollectionLogService service = new CollectionLogService();

        service.onChatMessage(chat("New collection log item: Medallion fragment."));

        assertEquals(1, service.getPendingMedallionFragmentDropCountSinceLastSync());

        service.markFullSyncSeen();

        assertEquals(0, service.getPendingMedallionFragmentDropCountSinceLastSync());
    }

    @Test
    public void nonGameMessagesDoNotCreatePendingDrops()
    {
        CollectionLogService service = new CollectionLogService();
        ChatMessage message = chat("New collection log item: Ancient page.");
        message.setType(ChatMessageType.PUBLICCHAT);

        service.onChatMessage(message);

        assertEquals(0, service.getPendingAncientPageDropCountSinceLastSync());
    }

    @Test
    public void oldAndRewardMessagesDoNotCreatePendingDrops()
    {
        CollectionLogService service = new CollectionLogService();

        service.onChatMessage(chat("New item added to your collection log: Ancient page."));
        service.onChatMessage(chat("You have received Ancient page."));
        service.onChatMessage(chat("Other player received: New collection log item: Ancient page."));

        assertEquals(0, service.getPendingAncientPageDropCountSinceLastSync());
    }

    @Test
    public void regionalBroadcastForLocalPlayerCreatesPendingDrop() throws Exception
    {
        CollectionLogService service = new CollectionLogService();
        setClient(service, clientWithLocalPlayerName("Pool User"));

        service.onChatMessage(broadcast("[North kingdom] Pool User received a new collection log item: Ancient page."));

        assertEquals(1, service.getPendingAncientPageDropCountSinceLastSync());
    }

    @Test
    public void regionalBroadcastForOtherPlayerDoesNotCreatePendingDrop() throws Exception
    {
        CollectionLogService service = new CollectionLogService();
        setClient(service, clientWithLocalPlayerName("Pool User"));

        service.onChatMessage(broadcast("[North kingdom] Other User received a new collection log item: Ancient page."));

        assertEquals(0, service.getPendingAncientPageDropCountSinceLastSync());
    }

    @Test
    public void plainBroadcastWithoutLocalPlayerContextDoesNotCreatePendingDrop() throws Exception
    {
        CollectionLogService service = new CollectionLogService();
        setClient(service, clientWithLocalPlayerName("Pool User"));

        service.onChatMessage(broadcast("New collection log item: Ancient page."));

        assertEquals(0, service.getPendingAncientPageDropCountSinceLastSync());
    }

    private static ChatMessage chat(String text)
    {
        ChatMessage message = new ChatMessage();
        message.setType(ChatMessageType.GAMEMESSAGE);
        message.setMessage(text);
        return message;
    }

    private static ChatMessage broadcast(String text)
    {
        ChatMessage message = chat(text);
        message.setType(ChatMessageType.BROADCAST);
        return message;
    }

    private static void setClient(CollectionLogService service, Client client) throws Exception
    {
        Field field = CollectionLogService.class.getDeclaredField("client");
        field.setAccessible(true);
        field.set(service, client);
    }

    private static Client clientWithLocalPlayerName(String name)
    {
        return (Client) Proxy.newProxyInstance(
                Client.class.getClassLoader(),
                new Class<?>[]{Client.class},
                (proxy, method, args) -> "getLocalPlayer".equals(method.getName())
                        ? player(name)
                        : defaultValue(method.getReturnType())
        );
    }

    private static Player player(String name)
    {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> "getName".equals(method.getName())
                        ? name
                        : defaultValue(method.getReturnType())
        );
    }

    private static Object defaultValue(Class<?> type)
    {
        if (type == Boolean.TYPE)
        {
            return false;
        }
        if (type == Byte.TYPE)
        {
            return (byte) 0;
        }
        if (type == Short.TYPE)
        {
            return (short) 0;
        }
        if (type == Integer.TYPE)
        {
            return 0;
        }
        if (type == Long.TYPE)
        {
            return 0L;
        }
        if (type == Float.TYPE)
        {
            return 0f;
        }
        if (type == Double.TYPE)
        {
            return 0d;
        }
        if (type == Character.TYPE)
        {
            return '\0';
        }
        return null;
    }
}
