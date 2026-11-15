package com.amtrollin.xtremetasker.verification;

import com.amtrollin.xtremetasker.models.PrerequisiteStatus;
import com.amtrollin.xtremetasker.models.QuestPrerequisite;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import org.junit.Test;

import static org.junit.Assert.*;

public class QuestMilestoneTrackingTest
{
    @Test
    public void allBundledMiniquestsRequireTheirOwnFinishedState() throws Exception
    {
        Object[][] cases = {
                {"Alfred Grimhand's Barcrawl miniquest", Quest.ALFRED_GRIMHANDS_BARCRAWL},
                {"Enter the Abyss miniquest", Quest.ENTER_THE_ABYSS},
                {"Lair of Tarn Razorlor miniquest", Quest.LAIR_OF_TARN_RAZORLOR},
                {"Mage Arena 1 miniquest", Quest.MAGE_ARENA_I},
                {"Vale Totems miniquest", Quest.VALE_TOTEMS}
        };
        for (Object[] entry : cases)
        {
            Snapshot snapshot = new Snapshot((Quest) entry[1]);
            PrerequisiteTrackerService service = service(snapshot);
            for (int state : new int[] {1, 0, 2})
            {
                snapshot.questState = state;
                PrerequisiteStatus status = service.evaluate((String) entry[0]).get(0);
                assertEquals(entry[0] + " state " + state, state == 2, status.isCompleted());
                assertTrue(status.isStatusKnown());
                assertFalse(status.getMarkerIcons().contains(PrerequisiteStatus.MarkerIcon.BULLET));
            }
        }
        PrerequisiteStatus unknown = service(new Snapshot(null)).evaluate("Unknown miniquest").get(0);
        assertFalse(unknown.isCompleted());
        assertFalse(unknown.isStatusKnown());
        assertFalse(new PrerequisiteTrackerService().evaluate("Vale Totems miniquest").get(0).isStatusKnown());
    }

    @Test
    public void knightWavesRequiresAllEightWaves() throws Exception
    {
        Snapshot snapshot = new Snapshot(null);
        PrerequisiteTrackerService service = service(snapshot);
        for (int waves : new int[] {0, 1, 7, 8})
        {
            snapshot.varbits.put(VarbitID.KR_KNIGHTWAVES_STATE, waves);
            PrerequisiteStatus status = service.evaluate("Knight Waves Training Grounds").get(0);
            assertEquals(waves >= 8, status.isCompleted());
            assertTrue(status.getMarkerIcons().contains(PrerequisiteStatus.MarkerIcon.MINIQUEST));
        }
    }

    @Test
    public void newCheckpointMappingsAcceptTheirBoundaryAndLaterProgress() throws Exception
    {
        // Independent expectations from the sourced quest steps, including both alternative routes.
        Object[][] cases = {
                {"Between a Rock...", Quest.BETWEEN_A_ROCK, "VARBIT", 299, 80},
                {"Desert Treasure I", Quest.DESERT_TREASURE_I, "VARBIT", 383, 3},
                {"Dragon Slayer I", Quest.DRAGON_SLAYER_I, "VARP", 177, 64},
                {"Elemental Workshop I", Quest.ELEMENTAL_WORKSHOP_I, "VARBIT", 2057, 1},
                {"Recipe for Disaster", Quest.RECIPE_FOR_DISASTER__PIRATE_PETE, "VARBIT", 1895, 80},
                {"In Aid of the Myreque", Quest.IN_AID_OF_THE_MYREQUE, "VARBIT", 1990, 280},
                {"Legends' Quest", Quest.LEGENDS_QUEST, "VARP", 139, 1},
                {"Monkey Madness I", Quest.MONKEY_MADNESS_I, "VARBIT", 125, 3},
                {"Perilous Moons", Quest.PERILOUS_MOONS, "VARBIT", 9819, 10},
                {"Royal Trouble", Quest.ROYAL_TROUBLE, "VARBIT", 2157, 1},
                {"Royal Trouble", Quest.ROYAL_TROUBLE, "VARBIT", 2141, 110},
                {"Troll Stronghold", Quest.TROLL_STRONGHOLD, "VARP", 317, 20},
                {"Troll Stronghold", Quest.TROLL_STRONGHOLD, "VARBIT", 12863, 2},
                {"Watchtower", Quest.WATCHTOWER, "VARP", 212, 6}
        };
        Map<String, QuestPrerequisite> requirements = bundledMilestones();
        assertEquals(20, requirements.size());
        for (Object[] entry : cases)
        {
            String name = (String) entry[0];
            QuestPrerequisite requirement = requirements.get(name);
            assertNotNull(name, requirement);
            assertTrue(name, requirement.getProgress().isValid());
            Snapshot snapshot = new Snapshot((Quest) entry[1]);
            PrerequisiteTrackerService service = service(snapshot);
            Map<Integer, Integer> variables = entry[2].equals("VARP") ? snapshot.varps : snapshot.varbits;
            int id = (Integer) entry[3];
            int boundary = (Integer) entry[4];
            variables.put(id, boundary - 1);
            PrerequisiteStatus before = service.evaluate("", List.of(requirement)).get(0);
            assertTrue(name, before.isStatusKnown());
            assertFalse(name + " before checkpoint", before.isCompleted());
            variables.put(id, boundary);
            assertTrue(name + " at checkpoint", service.evaluate("", List.of(requirement)).get(0).isCompleted());
            variables.put(id, boundary + 1);
            assertTrue(name + " after checkpoint", service.evaluate("", List.of(requirement)).get(0).isCompleted());
            variables.clear();
            snapshot.questState = 2;
            assertTrue(name + " finished", service.evaluate("", List.of(requirement)).get(0).isCompleted());
        }
    }

    @Test
    public void unrelatedDragonSlayerBitsCannotProveTheShortcutIsOpen() throws Exception
    {
        QuestPrerequisite requirement = bundledMilestones().get("Dragon Slayer I");
        Snapshot snapshot = new Snapshot(Quest.DRAGON_SLAYER_I);
        PrerequisiteTrackerService service = service(snapshot);
        snapshot.varps.put(177, (1 << 20) | (1 << 14));
        assertFalse(service.evaluate("", List.of(requirement)).get(0).isCompleted());
        snapshot.varps.merge(177, 1 << 6, (a, b) -> a | b);
        assertTrue(service.evaluate("", List.of(requirement)).get(0).isCompleted());
    }

    @Test
    public void lubufuEvidenceIsPositiveOnlyPersistentAndAccountScoped() throws Exception
    {
        Snapshot snapshot = new Snapshot(Quest.TAI_BWO_WANNAI_TRIO);
        Map<String, Boolean> saved = new HashMap<>();
        QuestUnlockTracker tracker = new QuestUnlockTracker(snapshot.client(), saved::get, saved::put);
        PrerequisiteTrackerService service = service(snapshot);
        inject(service, "questUnlockTracker", tracker);
        QuestPrerequisite requirement = bundledMilestones().get("Tai Bwo Wannai Trio");
        tracker.update("account-a");
        PrerequisiteStatus unknown = service.evaluate("", List.of(requirement)).get(0);
        assertFalse(unknown.isCompleted());
        assertFalse(unknown.isStatusKnown());
        assertEquals(requirement.getDetail(), unknown.getDetail());

        // Bait delivery and a dialogue from somebody else do not prove training.
        snapshot.text.put(InterfaceID.Objectbox.TEXT, "You hand Lubufu 20 raw Karambwanji.");
        snapshot.text.put(InterfaceID.ChatLeft.NAME, "Someone else");
        snapshot.text.put(InterfaceID.ChatLeft.TEXT, "How fares my apprentice?");
        tracker.update("account-a");
        assertFalse(tracker.isKarambwanFishingUnlocked());
        snapshot.text.put(InterfaceID.ChatLeft.NAME, "<col=000080>Lubufu</col>");
        snapshot.text.put(InterfaceID.ChatLeft.TEXT, "How fares my<br>apprentice?");
        tracker.update("account-a");
        assertTrue(service.evaluate("", List.of(requirement)).get(0).isCompleted());
        assertTrue(saved.get("account-a"));

        snapshot.text.clear();
        tracker.update(null);
        assertFalse(tracker.isKarambwanFishingUnlocked());
        tracker.update("account-b");
        assertFalse(service.evaluate("", List.of(requirement)).get(0).isCompleted());
        QuestUnlockTracker restarted = new QuestUnlockTracker(snapshot.client(), saved::get, saved::put);
        restarted.update("account-a");
        assertTrue(restarted.isKarambwanFishingUnlocked());
        assertFalse(saved.containsKey("account-b"));

        // An existing completed diary catch is also positive evidence without speaking to Lubufu.
        snapshot.varbits.put(3588, 1);
        assertTrue(service.evaluate("", List.of(requirement)).get(0).isCompleted());
        snapshot.varbits.clear();
        snapshot.questState = 2;
        assertTrue(service.evaluate("", List.of(requirement)).get(0).isCompleted());
    }

    @Test
    public void lubufuTrainingAndReplacementMessagesAreRemembered() throws Exception
    {
        for (String message : List.of("Lubufu teaches you a new fishing technique.", "Lubufu hands you a Karambwan vessel."))
        {
            Snapshot snapshot = new Snapshot(null);
            snapshot.text.put(InterfaceID.Objectbox.TEXT, message);
            Map<String, Boolean> saved = new HashMap<>();
            QuestUnlockTracker tracker = new QuestUnlockTracker(snapshot.client(), saved::get, saved::put);
            tracker.update(null);
            assertTrue(saved.isEmpty());
            tracker.update("account-a");
            assertTrue(tracker.isKarambwanFishingUnlocked());
        }
    }

    @Test
    public void malformedAlternativesAndMasksAreNotValidChecks()
    {
        for (String json : List.of("{\"anyOf\":[]}", "{\"anyOf\":[null]}",
                "{\"anyOf\":[{\"type\":\"VARBIT\",\"id\":-1,\"minimum\":1}]}",
                "{\"type\":\"VARP\",\"id\":177,\"mask\":64,\"minimum\":65}"))
            assertFalse(new Gson().fromJson(json, QuestPrerequisite.ProgressCheck.class).isValid());
    }

    private static Map<String, QuestPrerequisite> bundledMilestones()
    {
        Map<String, QuestPrerequisite> result = new HashMap<>();
        try (InputStreamReader reader = new InputStreamReader(QuestMilestoneTrackingTest.class
                .getResourceAsStream("/task_data/tasks.json"), StandardCharsets.UTF_8))
        {
            for (JsonElement task : new JsonParser().parse(reader).getAsJsonObject().getAsJsonArray("tasks"))
            {
                if (!task.getAsJsonObject().has("questPrereqs")) continue;
                for (JsonElement entry : task.getAsJsonObject().getAsJsonArray("questPrereqs"))
                {
                    QuestPrerequisite q = new Gson().fromJson(entry, QuestPrerequisite.class);
                    if (q.getAction() == QuestPrerequisite.Action.MILESTONE)
                    {
                        QuestPrerequisite previous = result.putIfAbsent(q.getQuest(), q);
                        if (previous != null) assertEquals("Inherited checkpoints must match: " + q.getQuest(), previous, q);
                    }
                }
            }
        }
        catch (java.io.IOException e) { throw new AssertionError(e); }
        return result;
    }

    private static PrerequisiteTrackerService service(Snapshot snapshot) throws Exception
    {
        PrerequisiteTrackerService service = new PrerequisiteTrackerService();
        inject(service, "client", snapshot.client());
        return service;
    }

    private static void inject(Object object, String name, Object value) throws Exception
    {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }

    private static class Snapshot
    {
        private final Quest expected;
        private int questState; // Script result: 0 in progress, 1 not started, 2 finished.
        private final Map<Integer, Integer> varps = new HashMap<>();
        private final Map<Integer, Integer> varbits = new HashMap<>();
        private final Map<Integer, String> text = new HashMap<>();

        Snapshot(Quest expected) { this.expected = expected; }

        Client client()
        {
            return (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
                    (proxy, method, args) -> {
                        switch (method.getName())
                        {
                            case "runScript":
                                assertNotNull("Unexpected quest-state lookup", expected);
                                assertEquals(expected.getId(), ((Object[]) args[0])[1]);
                                return null;
                            case "getIntStack": return new int[] {questState};
                            case "getVarpValue": return varps.getOrDefault((Integer) args[0], 0);
                            case "getVarbitValue": return varbits.getOrDefault((Integer) args[0], 0);
                            case "getWidget":
                                String value = text.get((Integer) args[0]);
                                if (value == null) return null;
                                return Proxy.newProxyInstance(Widget.class.getClassLoader(), new Class<?>[] {Widget.class},
                                        (widget, call, values) -> {
                                            if (call.getName().equals("getText")) return value;
                                            if (call.getName().equals("isHidden")) return false;
                                            throw new AssertionError(call.getName());
                                        });
                            default: throw new AssertionError(method.getName());
                        }
                    });
        }
    }
}
