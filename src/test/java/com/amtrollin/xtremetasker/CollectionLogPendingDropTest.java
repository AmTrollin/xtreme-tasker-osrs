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
        service.onChatMessage(chat("New collection log item: Ancient page x1."));

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
    public void legacyCollectionLogPopupStillCreatesPendingDrops()
    {
        CollectionLogService service = new CollectionLogService();

        service.onChatMessage(chat("New item added to your collection log: Ancient page."));

        assertEquals(1, service.getPendingAncientPageDropCountSinceLastSync());
    }

    @Test
    public void broadcastLikeMessagesDoNotMarkTomeOfWaterObtained()
    {
        CollectionLogService service = new CollectionLogService();

        service.onChatMessage(chat("Player received a special drop: Tome of water (empty)."));
        service.onChatMessage(chat("[Player] New collection log item: Tome of water (empty)."));

        assertEquals(0, service.countObtained(new int[]{25576}));
    }

    @Test
    public void genericReceivedMessagesDoNotMarkTomeOfWaterObtained()
    {
        CollectionLogService service = new CollectionLogService();

        service.onChatMessage(chat("You have received Tome of water (empty)."));

        assertEquals(0, service.countObtained(new int[]{25576}));
    }

    @Test
    public void plainItemTextDoesNotMarkTomeOfWaterObtained()
    {
        CollectionLogService service = new CollectionLogService();

        service.onChatMessage(chat("Tome of water (empty)"));

        assertEquals(0, service.countObtained(new int[]{25576}));
    }

    @Test
    public void publicChatDoesNotMarkTomeOfWaterObtained()
    {
        CollectionLogService service = new CollectionLogService();
        ChatMessage message = chat("damn, still no tome of water");
        message.setType(ChatMessageType.PUBLICCHAT);

        service.onChatMessage(message);

        assertEquals(0, service.countObtained(new int[]{25576}));
    }

    private static ChatMessage chat(String text)
    {
        ChatMessage message = new ChatMessage();
        message.setType(ChatMessageType.GAMEMESSAGE);
        message.setMessage(text);
        return message;
    }
}
