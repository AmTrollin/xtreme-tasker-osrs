package com.amtrollin.xtremetasker.verification;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;

/** Remembers positive evidence for unlocks without a verified direct progress reader. */
@Singleton
public class QuestUnlockTracker
{
    private final Client client;
    private final Function<String, Boolean> readUnlock;
    private final BiConsumer<String, Boolean> writeUnlock;
    private String accountKey;
    private boolean karambwanFishing;

    @Inject
    public QuestUnlockTracker(Client client, ConfigManager configManager)
    {
        this(client,
                account -> Boolean.parseBoolean(configManager.getConfiguration("xtremetasker", "karambwanFishing_" + account)),
                (account, unlocked) -> configManager.setConfiguration("xtremetasker", "karambwanFishing_" + account, unlocked));
    }

    QuestUnlockTracker(Client client, Function<String, Boolean> readUnlock, BiConsumer<String, Boolean> writeUnlock)
    {
        this.client = client;
        this.readUnlock = readUnlock;
        this.writeUnlock = writeUnlock;
    }

    /** Called on the client thread using the plugin's stabilized account identity. */
    public void update(String activeAccountKey)
    {
        if (!Objects.equals(accountKey, activeAccountKey))
        {
            accountKey = activeAccountKey;
            karambwanFishing = accountKey != null && Boolean.TRUE.equals(readUnlock.apply(accountKey));
        }
        if (accountKey == null || karambwanFishing) return;

        String message = widgetText(InterfaceID.Objectbox.TEXT);
        String speaker = widgetText(InterfaceID.ChatLeft.NAME);
        String dialogue = widgetText(InterfaceID.ChatLeft.TEXT);
        // These lines establish training, unlike handing over bait or owning a tradeable vessel.
        // Source: https://oldschool.runescape.wiki/w/Transcript:Tai_Bwo_Wannai_Trio
        if (message.equals("Lubufu teaches you a new fishing technique.")
                || message.equals("Lubufu hands you a Karambwan vessel.")
                || speaker.equals("Lubufu") && (dialogue.equals("How fares my apprentice?")
                    || dialogue.equals("Feel free to practise on my fishing spot.")))
        {
            karambwanFishing = true;
            writeUnlock.accept(accountKey, true);
        }
    }

    public boolean isKarambwanFishingUnlocked()
    {
        return accountKey != null && karambwanFishing;
    }

    private String widgetText(int id)
    {
        Widget widget = client.getWidget(id);
        if (widget == null || widget.isHidden() || widget.getText() == null) return "";
        return widget.getText().replaceAll("<br\\s*/?>", " ").replaceAll("<[^>]*>", "")
                .replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }
}
