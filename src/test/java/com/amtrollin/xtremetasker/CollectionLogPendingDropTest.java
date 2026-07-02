package com.amtrollin.xtremetasker;

import com.amtrollin.xtremetasker.verification.CollectionLogService;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import org.junit.Test;

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

    private static ChatMessage chat(String text)
    {
        ChatMessage message = new ChatMessage();
        message.setType(ChatMessageType.GAMEMESSAGE);
        message.setMessage(text);
        return message;
    }
}
