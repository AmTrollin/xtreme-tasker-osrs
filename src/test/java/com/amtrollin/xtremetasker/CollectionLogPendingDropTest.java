package com.amtrollin.xtremetasker;

import com.amtrollin.xtremetasker.verification.CollectionLogService;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
    public void nonExactCollectionLogMessagesDoNotCreatePendingDrops()
    {
        CollectionLogService service = new CollectionLogService();

        service.onChatMessage(chat("New item added to your collection log: Ancient page."));
        service.onChatMessage(chat("You have received Ancient page."));
        service.onChatMessage(chat("Other player received: New collection log item: Ancient page."));

        assertEquals(0, service.getPendingAncientPageDropCountSinceLastSync());
    }

    @Test
    public void broadcastCollectionLogTextDoesNotCreatePendingDrops()
    {
        CollectionLogService service = new CollectionLogService();
        ChatMessage message = chat("New collection log item: Ancient page.");
        message.setType(ChatMessageType.BROADCAST);

        service.onChatMessage(message);

        assertEquals(0, service.getPendingAncientPageDropCountSinceLastSync());
    }

    @Test
    public void fullSyncPrunesObtainedItemsThatAreSeenMissing()
    {
        CollectionLogService service = new CollectionLogService();
        service.storeItem(10878);

        service.beginFullSyncCapture();
        service.storeSeenItem(10878);
        int pruned = service.finishFullSyncCapture();

        assertEquals(1, pruned);
        assertFalse(service.isItemObtained(10878));
        assertTrue(service.hasSeenItem(10878));
    }

    @Test
    public void fullSyncDoesNotPruneObtainedItemsThatWereNotSeen()
    {
        CollectionLogService service = new CollectionLogService();
        service.storeItem(10878);

        service.beginFullSyncCapture();
        service.storeSeenItem(10879);
        int pruned = service.finishFullSyncCapture();

        assertEquals(0, pruned);
        assertTrue(service.isItemObtained(10878));
    }

    @Test
    public void fullSyncDoesNotPruneItemsSeenObtained()
    {
        CollectionLogService service = new CollectionLogService();
        service.storeItem(10878);

        service.beginFullSyncCapture();
        service.storeSeenItem(10878);
        service.storeItem(10878);
        int pruned = service.finishFullSyncCapture();

        assertEquals(0, pruned);
        assertTrue(service.isItemObtained(10878));
    }

    private static ChatMessage chat(String text)
    {
        ChatMessage message = new ChatMessage();
        message.setType(ChatMessageType.GAMEMESSAGE);
        message.setMessage(text);
        return message;
    }
}
