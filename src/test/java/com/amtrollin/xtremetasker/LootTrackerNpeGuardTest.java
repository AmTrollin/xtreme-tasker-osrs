package com.amtrollin.xtremetasker;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;

public class LootTrackerNpeGuardTest
{
    @Test
    public void rewritesLootTrackerChatTypeWhenLocalPlayerIsMissing()
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        plugin.setClientForTesting(clientWithLocalPlayer(null));

        ChatMessage message = new ChatMessage();
        message.setType(ChatMessageType.GAMEMESSAGE);

        plugin.onChatMessage(message);

        assertEquals(ChatMessageType.CONSOLE, message.getType());
    }

    @Test
    public void leavesLootTrackerChatTypeAloneWhenLocalPlayerExists()
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        plugin.setClientForTesting(clientWithLocalPlayer(player()));

        ChatMessage message = new ChatMessage();
        message.setType(ChatMessageType.GAMEMESSAGE);

        plugin.onChatMessage(message);

        assertEquals(ChatMessageType.GAMEMESSAGE, message.getType());
    }

    @Test
    public void leavesOtherChatTypesAloneWhenLocalPlayerIsMissing()
    {
        XtremeTaskerPlugin plugin = new XtremeTaskerPlugin();
        plugin.setClientForTesting(clientWithLocalPlayer(null));

        ChatMessage message = new ChatMessage();
        message.setType(ChatMessageType.CONSOLE);

        plugin.onChatMessage(message);

        assertEquals(ChatMessageType.CONSOLE, message.getType());
    }

    private static Client clientWithLocalPlayer(Player localPlayer)
    {
        return (Client) Proxy.newProxyInstance(
                Client.class.getClassLoader(),
                new Class<?>[]{Client.class},
                (proxy, method, args) -> "getLocalPlayer".equals(method.getName())
                        ? localPlayer
                        : defaultValue(method.getReturnType())
        );
    }

    private static Player player()
    {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> defaultValue(method.getReturnType())
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
