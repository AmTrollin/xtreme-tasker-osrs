package com.amtrollin.xtremetasker.verification;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.Quest;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import org.junit.Test;

import static org.junit.Assert.*;

public class AdditionalPrerequisiteChecksTest
{
    private static final String QUEEN = "Defeat the Penance Queen and complete Barbarian Assault";
    private static final String ROLES = "Defeat the Penance Queen and acquire level 5 in all Barbarian Assault roles";
    private static final String VOID = "Acquire and wear any complete void set";
    private static final String PROSPECTOR = "Acquire a prospector helmet from the Motherlode Mine";
    private static final String WARRIORS = "Access to the Warriors' Guild";

    @Test
    public void savedCountersAndUnlocksRespectTheirThresholds()
    {
        Snapshot s = new Snapshot();
        AdditionalPrerequisiteChecks checks = s.checks();
        Object[][] cases = {
                {"Reach 50 kudos with the Varrock Museum", VarbitID.VM_KUDOS, 50},
                {"Reach 153 kudos with the Varrock Museum", VarbitID.VM_KUDOS, 153},
                {"Reach the rank of White Knight Master", VarbitID.WANTED_BLACK_KNIGHT_DEAD, 1300},
                {"Access to the Bones to Peaches spell from the Mage Training Arena", VarbitID.MAGICTRAINING_BONESPEACHES, 1},
                {"Access to Barbarian Fishing", VarbitID.BRUT_FISHING_S, 1}
        };
        for (Object[] entry : cases)
        {
            String text = (String) entry[0];
            int variable = (Integer) entry[1];
            int minimum = (Integer) entry[2];
            s.varbits.put(variable, minimum - 1);
            assertEquals(text, Boolean.FALSE, checks.check(text).met);
            s.varbits.put(variable, minimum);
            assertEquals(text, Boolean.TRUE, checks.check(text).met);
        }
    }

    @Test
    public void accessRulesAcceptBothRoutesAndTheNinetyNineException()
    {
        Snapshot s = new Snapshot();
        AdditionalPrerequisiteChecks checks = s.checks();
        s.skills.put(Skill.ATTACK, 65);
        s.skills.put(Skill.STRENGTH, 64);
        assertEquals(Boolean.FALSE, checks.check(WARRIORS).met);
        s.skills.put(Skill.STRENGTH, 65);
        assertEquals(Boolean.TRUE, checks.check(WARRIORS).met);
        s.skills.put(Skill.ATTACK, 99);
        s.skills.put(Skill.STRENGTH, 1);
        assertEquals(Boolean.TRUE, checks.check(WARRIORS).met);
        s.skills.put(Skill.ATTACK, 1);
        s.skills.put(Skill.STRENGTH, 99);
        assertEquals(Boolean.TRUE, checks.check(WARRIORS).met);

        String dungeon = "Access to the Wilderness God Wars Dungeon";
        s.skills.put(Skill.STRENGTH, 59);
        s.skills.put(Skill.AGILITY, 59);
        assertEquals(Boolean.FALSE, checks.check(dungeon).met);
        s.skills.put(Skill.STRENGTH, 60);
        assertEquals(Boolean.TRUE, checks.check(dungeon).met);
        s.skills.put(Skill.STRENGTH, 59);
        s.skills.put(Skill.AGILITY, 60);
        assertEquals(Boolean.TRUE, checks.check(dungeon).met);
    }

    @Test
    public void godSpellsRequireOneCompletedHundredCastUnlock()
    {
        for (int variable : new int[] {VarPlayerID.SARAMAGE, VarPlayerID.GUTHMAGE, VarPlayerID.ZAMOMAGE})
        {
            Snapshot s = new Snapshot();
            AdditionalPrerequisiteChecks checks = s.checks();
            s.varps.put(variable, 99);
            assertEquals(Boolean.FALSE, checks.check("Access to at least one of the three god spells").met);
            s.varps.put(variable, 100);
            assertEquals(Boolean.TRUE, checks.check("Access to at least one of the three god spells").met);
        }
        Snapshot completedQuest = new Snapshot();
        completedQuest.questState = 2;
        assertEquals("Arena casting does not require the outside-arena unlock", Boolean.TRUE,
                completedQuest.checks().check("Access to at least one of the three god spells").met);
    }

    @Test
    public void abbreviatedQuestNamesResolveToTheCorrectCompletionState() throws Exception
    {
        Object[][] aliases = {
                {"Desert Treasure 2 quest", Quest.DESERT_TREASURE_II__THE_FALLEN_EMPIRE},
                {"Hand in the Sand quest", Quest.THE_HAND_IN_THE_SAND},
                {"Shades of Morton quest", Quest.SHADES_OF_MORTTON}
        };
        Snapshot s = new Snapshot();
        PrerequisiteTrackerService service = new PrerequisiteTrackerService();
        inject(service, "client", s.client);
        for (Object[] alias : aliases)
        {
            s.expectedQuestId = ((Quest) alias[1]).getId();
            for (int state : new int[] {1, 0, 2})
            {
                s.questState = state;
                assertTrue(service.evaluate((String) alias[0]).get(0).isStatusKnown());
                assertEquals(state == 2, service.evaluate((String) alias[0]).get(0).isCompleted());
            }
        }
    }

    @Test
    public void combinedTrainingRequiresEveryRelevantUnlockButNotQuestCompletion()
    {
        Snapshot s = new Snapshot();
        AdditionalPrerequisiteChecks checks = s.checks();
        String text = "Access to Barbarian Fishing, Firemaking, Smithing, and Herblore";
        int[] variables = {3758, 3757, 3759, 3764, 3762, 3763, 3761};
        for (int variable : variables) s.varbits.put(variable, 1);
        assertEquals(Boolean.TRUE, checks.check(text).met);
        for (int variable : variables)
        {
            s.varbits.put(variable, 0);
            assertEquals("Missing training " + variable, Boolean.FALSE, checks.check(text).met);
            s.varbits.put(variable, 1);
        }
        s.varbits.put(3757, 0); // Hard diary needs rod fishing; elite also needs bare-handed fishing.
        assertEquals(Boolean.TRUE, checks.check("Access to Barbarian Fishing, Firemaking, and Smithing").met);
        assertEquals(Boolean.FALSE, checks.check(text).met);
    }

    @Test
    public void queenCreditsProveCompletionAndSurviveSpendingButNeverLeakAccounts()
    {
        Snapshot s = new Snapshot();
        AdditionalPrerequisiteChecks checks = s.checks();
        checks.update("a");
        s.varps.put(263, 999); // Generic scratch variable is meaningless outside the BA shop.
        assertNull(checks.check(QUEEN).met);
        s.varbits.put(4767, 1);
        assertEquals(Boolean.TRUE, checks.check(QUEEN).met);
        checks.update("a");
        s.varbits.clear();
        assertEquals(Boolean.TRUE, checks.check(QUEEN).met);
        checks.update(null);
        checks.update("b");
        assertNull(checks.check(QUEEN).met);
        checks.update("a");
        assertEquals(Boolean.TRUE, checks.check(QUEEN).met);
    }

    @Test
    public void queenCreditShopTextIsReadOnlyWhenVisible()
    {
        Snapshot s = new Snapshot();
        AdditionalPrerequisiteChecks checks = s.checks();
        W row = new W("<col=00ff00>Kill Queen (1)</col>");
        W root = new W("");
        root.children = new Widget[] {row.widget()};
        root.hidden = true;
        s.widgets.put(InterfaceID.BarbassaultRewardShop.BARBASSAULT_REWARDS, root.widget());
        checks.update("a");
        assertNull(checks.check(QUEEN).met);
        root.hidden = false;
        checks.update("a");
        s.widgets.clear();
        assertEquals(Boolean.TRUE, checks.check(QUEEN).met);
    }

    @Test
    public void allFourRolesAndQueenEvidenceAreRequired()
    {
        Snapshot s = new Snapshot();
        AdditionalPrerequisiteChecks checks = s.checks();
        int[] roles = {3251, 3253, 3254, 3255};
        for (int role : roles) s.varbits.put(role, 5);
        assertNull(checks.check(ROLES).met);
        s.varbits.put(4767, 1);
        assertEquals(Boolean.TRUE, checks.check(ROLES).met);
        for (int role : roles)
        {
            s.varbits.put(role, 4);
            s.varbits.put(3252, 5); // Horn status must never substitute for Defender level.
            assertEquals("Role " + role, Boolean.FALSE, checks.check(ROLES).met);
            s.varbits.put(role, 5);
        }
        s.varbits.put(3253, 0); // Role data absent, rather than a valid level below five.
        assertNull(checks.check(ROLES).met);
    }

    @Test
    public void chompyBowReadingsAndKillsMaintainAProfileSpecificCount()
    {
        Snapshot s = new Snapshot();
        AdditionalPrerequisiteChecks checks = s.checks();
        String count = "You've scratched up a total of 299 notches on your bow.";
        checks.onChatMessage("a", ChatMessageType.GAMEMESSAGE, count);
        checks.update("a");
        assertNull(checks.check(chompy(300)).met);
        checks.onChatMessage("a", ChatMessageType.PUBLICCHAT, count);
        assertNull(checks.check(chompy(300)).met);
        checks.onChatMessage("a", ChatMessageType.GAMEMESSAGE, count);
        assertEquals(Boolean.TRUE, checks.check(chompy(125)).met);
        assertEquals(Boolean.FALSE, checks.check(chompy(300)).met);
        checks.onChatMessage("a", ChatMessageType.SPAM, "You scratch a notch on your bow for the chompy bird kill.");
        assertEquals(Boolean.TRUE, checks.check(chompy(300)).met);
        checks.update(null);
        checks.update("b");
        assertNull(checks.check(chompy(30)).met);
        checks.onChatMessage("a", ChatMessageType.GAMEMESSAGE, count);
        assertNull(checks.check(chompy(30)).met);
        checks.update("a");
        assertEquals(Boolean.TRUE, checks.check(chompy(300)).met);
        assertNull("Persisted counts are lower bounds after relogging", checks.check(chompy(1000)).met);
        checks.onChatMessage("a", ChatMessageType.GAMEMESSAGE, "You've scratched up a total of 1,000 notches on your bow.");
        assertEquals(Boolean.TRUE, checks.check(chompy(1000)).met);
        checks.onChatMessage("a", ChatMessageType.GAMEMESSAGE, "You've scratched up a total of 9999999999999999999999 notches.");
        assertEquals(Boolean.TRUE, checks.check(chompy(1000)).met);
    }

    @Test
    public void higherChompyDiaryStepsAlsoProveLowerKillRequirements()
    {
        Snapshot s = new Snapshot();
        AdditionalPrerequisiteChecks checks = s.checks();
        s.varps.put(1183, 1 << 1); // Western hard hat, 300 kills.
        assertEquals(Boolean.TRUE, checks.check(chompy(30)).met);
        assertEquals(Boolean.TRUE, checks.check(chompy(125)).met);
        assertEquals(Boolean.TRUE, checks.check(chompy(300)).met);
        assertNull(checks.check(chompy(1000)).met);
    }

    @Test
    public void activityFlagsArePositiveEvidenceWithIndependentBitChecks()
    {
        Snapshot s = new Snapshot();
        AdditionalPrerequisiteChecks checks = s.checks();
        Object[][] cases = {
                {"Defeat the Giant Mole", 1186, 28},
                {"Defeat the Kalphite Queen", 1198, 27},
                {"Defeat the Kalphite Queen and obtain her tattered head", 1199, 6},
                {"Defeat Zulrah", 1183, 3},
                {"Defeat the Thermonuclear smoke devil", 1183, 7},
                {"Defeat a Lizardman Shaman in the Lizardman Temple", 2085, 28},
                {"Defeat Skotizo", 2086, 6},
                {"Defeat a Hydra in the Karuulm Slayer Dungeon", 2086, 8},
                {"Chambers of Xeric", 2086, 10},
                {"Defeat each of the Dagannoth Kings", 1184, 31},
                {"Defeat all God Wars Dungeon generals except Nex", 1185, 3},
                {"Defeat the Chaos elemental", 1192, 30},
                {"Defeat the Crazy Archaeologist, Chaos Fanatic, and Scorpia", 1192, 31},
                {"Defeat Callisto/Artio, Venenatis/Spindel, and Vet'ion/Calvar'ion", 1193, 3},
                {"Open the Grand Gold Chest in the final room of Pyramid Plunder", 1199, 7},
                {"Open the Barrows chest while wearing a full Barrows set", 1181, 8}
        };
        for (Object[] entry : cases)
        {
            String text = (String) entry[0];
            int varp = (Integer) entry[1];
            int bit = (Integer) entry[2];
            s.varps.clear();
            s.varps.put(varp, 1 << (bit - 1));
            assertNull(text + " has no matching evidence", checks.check(text).met);
            s.varps.put(varp, 1 << bit);
            assertEquals(text, Boolean.TRUE, checks.check(text).met);
        }
        s.varbits.put(3601, 1);
        assertEquals(Boolean.TRUE, checks.check("Defeat a Ket-Zek in the TzHaar Fight Cave on the 31st wave").met);
    }

    @Test
    public void acquiredEquipmentCanBeVerifiedFromBankOrCollectionLog()
    {
        Snapshot s = new Snapshot();
        AdditionalPrerequisiteChecks checks = s.checks();
        checks.update("a");
        assertNull(checks.check(PROSPECTOR).met);
        s.items.put(InventoryID.BANK, new Item[] {new Item(ItemID.MOTHERLODE_REWARD_HAT_GOLD, 1)});
        checks.update("a");
        s.items.clear();
        assertEquals(Boolean.TRUE, checks.check(PROSPECTOR).met);
        checks.update("b");
        assertNull(checks.check(PROSPECTOR).met);

        CollectionLogService log = new CollectionLogService();
        log.storeItem(ItemID.MOTHERLODE_REWARD_HAT);
        AdditionalPrerequisiteChecks logged = new AdditionalPrerequisiteChecks(s.client, log, s.saved::get, s.saved::put);
        assertEquals(Boolean.TRUE, logged.check(PROSPECTOR).met);
    }

    @Test
    public void voidRequiresWearingAnEntireWorkingSetAndRemembersThatAction()
    {
        Snapshot s = new Snapshot();
        AdditionalPrerequisiteChecks checks = s.checks();
        checks.update("a");
        Item[] full = {new Item(11663, 1), new Item(13072, 1), new Item(13073, 1), new Item(8842, 1)};
        s.items.put(InventoryID.INV, full);
        assertNull(checks.check(VOID).met);
        s.items.put(InventoryID.WORN, Arrays.copyOf(full, 3));
        assertNull(checks.check(VOID).met);
        s.items.put(InventoryID.WORN, full);
        checks.update("a");
        s.items.clear();
        assertEquals(Boolean.TRUE, checks.check(VOID).met);
        checks.update("b");
        assertNull(checks.check(VOID).met);
        full[1] = new Item(20467, 1); // Broken elite top.
        s.items.put(InventoryID.WORN, full);
        assertNull(checks.check(VOID).met);
    }

    @Test
    public void questCapeRequiresCapeEvidenceAndNeverAcceptsTheHood()
    {
        Snapshot s = new Snapshot();
        AdditionalPrerequisiteChecks checks = s.checks();
        s.items.put(InventoryID.INV, new Item[] {new Item(ItemID.SKILLCAPE_QP_HOOD, 1)});
        assertNull(checks.check("Quest Cape").met);
        s.items.put(InventoryID.INV, new Item[] {new Item(ItemID.SKILLCAPE_QP, 1)});
        assertEquals(Boolean.TRUE, checks.check("Quest Cape").met);
        s.items.put(InventoryID.INV, new Item[] {new Item(ItemID.SKILLCAPE_QP_TRIMMED, 1)});
        assertEquals(Boolean.TRUE, checks.check("Quest Cape").met);
    }

    @Test
    public void unknownRequirementsDifferFromKnownUnmetRequirementsInTheSharedTracker() throws Exception
    {
        Snapshot s = new Snapshot();
        PrerequisiteTrackerService service = new PrerequisiteTrackerService();
        inject(service, "client", s.client);
        inject(service, "additionalPrerequisiteChecks", s.checks());
        for (String text : List.of("See Wiki (Based on Raid)", "Own a Jagex Account or set up the older RuneScape Authenticator",
                "Unrecognised requirement", "Defeat Zulrah"))
        {
            assertFalse(service.evaluate(text).get(0).isCompleted());
            assertFalse(text, service.evaluate(text).get(0).isStatusKnown());
        }
        assertTrue(service.evaluate("Reach 50 kudos with the Varrock Museum").get(0).isStatusKnown());
        assertFalse(service.evaluate("Reach 50 kudos with the Varrock Museum").get(0).isCompleted());
        s.varbits.put(3637, 50);
        assertTrue(service.evaluate("Reach 50 kudos with the Varrock Museum").get(0).isCompleted());
        assertFalse(service.evaluate("5 Magic or unknown requirement").get(0).isStatusKnown());
        s.skills.put(Skill.MAGIC, 5);
        assertTrue(service.evaluate("5 Magic or unknown requirement").get(0).isStatusKnown());
        assertTrue(service.evaluate("5 Magic or unknown requirement").get(0).isCompleted());
    }

    @Test
    public void malformedEvidenceAndUnavailableClientDataDoNotProveRequirements()
    {
        Snapshot s = new Snapshot();
        s.saved.put("a", "{not json");
        AdditionalPrerequisiteChecks checks = s.checks();
        checks.update("a");
        assertNull(checks.check(chompy(30)).met);
        assertNull(checks.check(QUEEN).met);
        assertNull(checks.check("Reach 99999999999999999999 kudos with the Varrock Museum").met);
        assertNull(checks.check("Reach 99999999999999999999 chompy or jubbly bird kills").met);
        AdditionalPrerequisiteChecks disconnected = new AdditionalPrerequisiteChecks(null, null, s.saved::get, s.saved::put);
        assertNull(disconnected.check(WARRIORS).met);
        assertNull(disconnected.check("Reach 50 kudos with the Varrock Museum").met);
    }

    @Test
    public void bundledPrerequisitesOnlyLeaveDocumentedManualChecksUnknown() throws Exception
    {
        Snapshot s = new Snapshot();
        s.defaultVarbit = 5;
        s.defaultVarp = -1; // All saved diary bits set, including signed bit 31.
        s.questState = 2;
        for (Skill skill : Skill.values()) s.skills.put(skill, 99);
        s.items.put(InventoryID.INV, new Item[] {new Item(ItemID.SKILLCAPE_QP, 1)});
        AdditionalPrerequisiteChecks checks = s.checks();
        checks.update("a");
        checks.onChatMessage("a", ChatMessageType.GAMEMESSAGE, "You've scratched up a total of 5,000 notches on your bow.");
        PrerequisiteTrackerService service = new PrerequisiteTrackerService();
        inject(service, "client", s.client);
        inject(service, "additionalPrerequisiteChecks", checks);
        Set<String> unknown = new TreeSet<>();
        try (InputStreamReader reader = new InputStreamReader(getClass().getResourceAsStream("/task_data/tasks.json"), StandardCharsets.UTF_8))
        {
            for (JsonElement task : new JsonParser().parse(reader).getAsJsonObject().getAsJsonArray("tasks"))
                if (task.getAsJsonObject().has("prereqs"))
                    service.evaluate(task.getAsJsonObject().get("prereqs").getAsString()).stream()
                            .filter(status -> !status.isStatusKnown()).forEach(status -> unknown.add(status.getText()));
        }
        assertEquals(Set.of("See Wiki (Based on Raid)",
                "Own a Jagex Account or set up the older RuneScape Authenticator"), unknown);
    }

    private static String chompy(int count) { return "Reach " + count + " chompy or jubbly bird kills"; }
    private static void inject(Object object, String name, Object value) throws Exception
    {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }

    private static class Snapshot
    {
        final Map<Integer, Integer> varbits = new HashMap<>(), varps = new HashMap<>();
        final Map<Skill, Integer> skills = new HashMap<>();
        final Map<Integer, Item[]> items = new HashMap<>();
        final Map<Integer, Widget> widgets = new HashMap<>();
        final Map<String, String> saved = new HashMap<>();
        int defaultVarbit, defaultVarp;
        int questState = 1;
        Integer expectedQuestId;
        final Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
                (proxy, method, args) -> {
                    switch (method.getName())
                    {
                        case "getVarbitValue": return varbits.getOrDefault((Integer) args[0], defaultVarbit);
                        case "getVarpValue": return varps.getOrDefault((Integer) args[0], defaultVarp);
                        case "runScript":
                            if (expectedQuestId != null) assertEquals(expectedQuestId, ((Object[]) args[0])[1]);
                            return null;
                        case "getIntStack": return new int[] {questState};
                        case "getTotalLevel": return 2277;
                        case "getLocalPlayer": return Proxy.newProxyInstance(net.runelite.api.Player.class.getClassLoader(),
                                new Class<?>[] {net.runelite.api.Player.class}, (player, call, params) -> {
                                    if (call.getName().equals("getCombatLevel")) return 126;
                                    throw new AssertionError(call.getName());
                                });
                        case "getRealSkillLevel": return skills.getOrDefault((Skill) args[0], 1);
                        case "getWidget": return widgets.get((Integer) args[0]);
                        case "getItemContainer":
                            Item[] contents = items.get((Integer) args[0]);
                            if (contents == null) return null;
                            return Proxy.newProxyInstance(ItemContainer.class.getClassLoader(), new Class<?>[] {ItemContainer.class},
                                    (container, call, values) -> {
                                        if (call.getName().equals("getItems")) return contents;
                                        throw new AssertionError(call.getName());
                                    });
                        default: throw new AssertionError(method.getName());
                    }
                });

        AdditionalPrerequisiteChecks checks()
        {
            return new AdditionalPrerequisiteChecks(client, null, saved::get, saved::put);
        }
    }

    private static class W
    {
        final String text;
        boolean hidden;
        Widget[] children;
        W(String text) { this.text = text; }
        Widget widget()
        {
            return (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(), new Class<?>[] {Widget.class},
                    (proxy, call, args) -> {
                        switch (call.getName())
                        {
                            case "getText": return text;
                            case "isHidden": return hidden;
                            case "getDynamicChildren": return children;
                            default: throw new AssertionError(call.getName());
                        }
                    });
        }
    }
}
