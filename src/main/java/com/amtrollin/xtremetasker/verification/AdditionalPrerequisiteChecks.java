package com.amtrollin.xtremetasker.verification;

import com.google.gson.Gson;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;

/** Pack-specific counters, access rules, and positive evidence for historical activities.
 * Sources and the distinction between an unmet condition and unavailable evidence are in
 * docs/prerequisite-tracking-audit.md.
 */
@Singleton
public class AdditionalPrerequisiteChecks
{
    private static final Gson GSON = new Gson();
    private static final Pattern KUDOS = Pattern.compile("^Reach ([\\d,]+) kudos with the Varrock Museum$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHOMPY = Pattern.compile("^Reach ([\\d,]+) chompy or jubbly bird kills$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHOMPY_TOTAL = Pattern.compile("^You've scratched up a total of ([\\d,]+)\\b.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHOMPY_KILL = Pattern.compile("^You scratch a notch on your bow for the (?:chompy|jubbly) bird kill\\.$");
    private static final Pattern QUEEN_CREDITS = Pattern.compile("^Kill Queen \\(([\\d,]+)\\)$");
    private static final Set<Integer> PROSPECTOR_HEADS = Set.of(ItemID.MOTHERLODE_REWARD_HAT,
            ItemID.MOTHERLODE_REWARD_HAT_GOLD, ItemID.FOSSIL_MOTHERLODE_REWARD_HAT);
    private static final Set<Integer> VOID_HEADS = Set.of(11663, 11664, 11665, 24183, 24184, 24185,
            26473, 26475, 26477, 27005, 27006, 27007);
    private static final Set<Integer> VOID_TOPS = Set.of(8839, 13072, 24177, 24178, 26463, 26469, 27000, 27003);
    private static final Set<Integer> VOID_LEGS = Set.of(8840, 13073, 24179, 24180, 26465, 26471, 27001, 27004);
    private static final Set<Integer> VOID_GLOVES = Set.of(8842, 24182, 26467, 27002);
    // Completed diary steps prove these historical activities. An unset bit cannot disprove them.
    private static final Map<String, Proof> ACTIVITY_PROOFS = Map.ofEntries(
            proof("Defeat the Giant Mole", VarPlayerID.FALADOR_ACHIEVEMENT_DIARY, 28),
            proof("Defeat the Kalphite Queen", VarPlayerID.DESERT_ACHIEVEMENT_DIARY, 27),
            proof("Defeat the Kalphite Queen and obtain her tattered head", VarPlayerID.DESERT_ACHIEVEMENT_DIARY2, 6),
            proof("Defeat Zulrah", VarPlayerID.WESTERN_ACHIEVEMENT_DIARY2, 3),
            proof("Defeat the Thermonuclear smoke devil", VarPlayerID.WESTERN_ACHIEVEMENT_DIARY2, 7),
            proof("Defeat a Lizardman Shaman in the Lizardman Temple", VarPlayerID.KOUREND_ACHIEVEMENT_DIARY, 28),
            proof("Defeat Skotizo", VarPlayerID.KOUREND_ACHIEVEMENT_DIARY2, 6),
            proof("Defeat a Hydra in the Karuulm Slayer Dungeon", VarPlayerID.KOUREND_ACHIEVEMENT_DIARY2, 8),
            proof("Chambers of Xeric", VarPlayerID.KOUREND_ACHIEVEMENT_DIARY2, 10),
            proof("Defeat each of the Dagannoth Kings", VarPlayerID.FREMENNIK_ACHIEVEMENT_DIARY, 31),
            proof("Defeat all God Wars Dungeon generals except Nex", VarPlayerID.FREMENNIK_ACHIEVEMENT_DIARY2, 3),
            proof("Defeat the Chaos elemental", VarPlayerID.WILDERNESS_ACHIEVEMENT_DIARY, 30),
            proof("Defeat the Crazy Archaeologist, Chaos Fanatic, and Scorpia", VarPlayerID.WILDERNESS_ACHIEVEMENT_DIARY, 31),
            proof("Defeat Callisto/Artio, Venenatis/Spindel, and Vet'ion/Calvar'ion", VarPlayerID.WILDERNESS_ACHIEVEMENT_DIARY2, 3),
            proof("Open the Grand Gold Chest in the final room of Pyramid Plunder", VarPlayerID.DESERT_ACHIEVEMENT_DIARY2, 7),
            proof("Open the Barrows chest while wearing a full Barrows set", VarPlayerID.MORYTANIA_ACHIEVEMENT_DIARY2, 8)
    );

    private final Client client;
    private final CollectionLogService collectionLog;
    private final Function<String, String> readState;
    private final BiConsumer<String, String> writeState;
    private String accountKey;
    private Evidence evidence = new Evidence();
    private boolean exactChompyCount;

    @Inject
    public AdditionalPrerequisiteChecks(Client client, CollectionLogService collectionLog,
                                        net.runelite.client.config.ConfigManager configManager)
    {
        this(client, collectionLog,
                account -> configManager.getConfiguration("xtremetasker", "prerequisiteEvidence_" + account),
                (account, json) -> configManager.setConfiguration("xtremetasker", "prerequisiteEvidence_" + account, json));
    }

    AdditionalPrerequisiteChecks(Client client, CollectionLogService collectionLog,
                                 Function<String, String> readState, BiConsumer<String, String> writeState)
    {
        this.client = client;
        this.collectionLog = collectionLog;
        this.readState = readState;
        this.writeState = writeState;
    }

    /** null means another checker may handle this text; Result.met == null means unknown. */
    Result check(String text)
    {
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        Matcher kudos = KUDOS.matcher(text);
        if (kudos.matches())
        {
            try { return result(client == null ? null : client.getVarbitValue(VarbitID.VM_KUDOS) >= number(kudos.group(1))); }
            catch (NumberFormatException ignored) { return result(null); }
        }
        Matcher chompy = CHOMPY.matcher(text);
        if (chompy.matches())
        {
            int required;
            try { required = number(chompy.group(1)); }
            catch (NumberFormatException ignored) { return result(null); }
            int lowerBound = Math.max(evidence.chompyKills, diaryChompyLowerBound());
            return lowerBound >= required ? result(true) : exactChompyCount ? result(false)
                    : result(null);
        }
        switch (normalized)
        {
            case "access to the warriors' guild":
                if (client == null) return result(null);
                int attack = client.getRealSkillLevel(Skill.ATTACK);
                int strength = client.getRealSkillLevel(Skill.STRENGTH);
                return result(attack + strength >= 130 || attack >= 99 || strength >= 99);
            case "access to the wilderness god wars dungeon":
                return result(client == null ? null : client.getRealSkillLevel(Skill.STRENGTH) >= 60
                        || client.getRealSkillLevel(Skill.AGILITY) >= 60);
            case "access to the bones to peaches spell from the mage training arena":
                return varbitAtLeast(VarbitID.MAGICTRAINING_BONESPEACHES, 1);
            case "access to at least one of the three god spells":
                return result(client == null ? null : client.getVarpValue(VarPlayerID.SARAMAGE) >= 100
                        || client.getVarpValue(VarPlayerID.GUTHMAGE) >= 100 || client.getVarpValue(VarPlayerID.ZAMOMAGE) >= 100
                        // The Wilderness diary also permits casting inside the arena before the 100-cast unlock.
                        || Quest.MAGE_ARENA_I.getState(client) == QuestState.FINISHED);
            case "access to barbarian fishing":
                // This exact pack entry is the Morytania elite bare-handed shark requirement.
                return varbitAtLeast(VarbitID.BRUT_FISHING_S, 1);
            case "access to barbarian fishing, firemaking, and smithing":
                return training(VarbitID.BRUT_FISHING_R, VarbitID.BRUT_FIRE, VarbitID.BRUT_SMITH_SPEAR);
            case "access to barbarian fishing, firemaking, smithing, and herblore":
                // Includes inherited hard-diary rod/spear tasks and elite bare-handed/hasta/pyre tasks.
                return training(VarbitID.BRUT_FISHING_R, VarbitID.BRUT_FISHING_S, VarbitID.BRUT_FIRE,
                        VarbitID.BRUT_CRAFT_SHIP, VarbitID.BRUT_SMITH_SPEAR, VarbitID.BRUT_SMITH_HASTA, VarbitID.BRUT_HERB_POTION);
            case "reach the rank of white knight master":
                return varbitAtLeast(VarbitID.WANTED_BLACK_KNIGHT_DEAD, 1300);
            case "defeat the penance queen and complete barbarian assault":
                return queenDefeated() ? result(true) : result(null);
            case "defeat the penance queen and acquire level 5 in all barbarian assault roles":
                Boolean roles = allRolesLevelFive();
                if (Boolean.FALSE.equals(roles)) return result(false);
                if (Boolean.TRUE.equals(roles) && queenDefeated()) return result(true);
                return result(null);
            case "acquire a prospector helmet from the motherlode mine":
                return evidence.prospectorAcquired || hasProspector() || diaryBit(VarPlayerID.FALADOR_ACHIEVEMENT_DIARY, 31)
                        ? result(true) : result(null);
            case "acquire and wear any complete void set":
                return evidence.voidSetWorn || wearingFullVoid() || diaryBit(VarPlayerID.WESTERN_ACHIEVEMENT_DIARY2, 12)
                        ? result(true) : result(null);
            case "quest cape":
                return hasItem(Set.of(ItemID.SKILLCAPE_QP, ItemID.SKILLCAPE_QP_TRIMMED))
                        ? result(true) : result(null);
            case "defeat a ket-zek in the tzhaar fight cave on the 31st wave":
                return positive(client != null && client.getVarbitValue(VarbitID.ATJUN_HARD_FIGHTCAVE) > 0);
            case "tzhaar fight cave":
                return positive(diaryBit(VarPlayerID.ATJUN_TASKS_4, 2) || hasItem(Set.of(6570)));
            default:
                Proof proof = ACTIVITY_PROOFS.get(normalized);
                return proof == null ? null : positive(diaryBit(proof.varp, proof.bit));
        }
    }

    /** Poll on the client thread after the account identity has stabilized. */
    public void update(String activeAccountKey)
    {
        if (!Objects.equals(accountKey, activeAccountKey))
        {
            accountKey = activeAccountKey;
            evidence = new Evidence();
            exactChompyCount = false;
            if (accountKey != null)
            {
                try
                {
                    Evidence loaded = GSON.fromJson(readState.apply(accountKey), Evidence.class);
                    if (loaded != null && loaded.chompyKills >= 0) evidence = loaded;
                }
                catch (RuntimeException ignored) { /* Invalid cached evidence must not mark anything met. */ }
            }
        }
        if (accountKey == null || client == null) return;
        boolean changed = false;
        if (!evidence.prospectorAcquired && hasProspector()) { evidence.prospectorAcquired = true; changed = true; }
        if (!evidence.voidSetWorn && wearingFullVoid()) { evidence.voidSetWorn = true; changed = true; }
        // Queen credits are spendable. Positive evidence is permanent; zero credits prove nothing.
        if (!evidence.queenDefeated && queenDefeated()) { evidence.queenDefeated = true; changed = true; }
        if (changed) save();
    }

    public void onChatMessage(String activeAccountKey, ChatMessageType type, String message)
    {
        if (accountKey == null || !accountKey.equals(activeAccountKey) || message == null) return;
        String plain = plainText(message);
        Matcher total = CHOMPY_TOTAL.matcher(plain);
        if (type == ChatMessageType.GAMEMESSAGE && total.matches())
        {
            try { evidence.chompyKills = number(total.group(1)); }
            catch (NumberFormatException ignored) { return; }
            exactChompyCount = true;
            save();
        }
        else if (type == ChatMessageType.SPAM && CHOMPY_KILL.matcher(plain).matches()
                && evidence.chompyKills < Integer.MAX_VALUE)
        {
            evidence.chompyKills++;
            save();
        }
    }

    private boolean queenDefeated()
    {
        if (evidence.queenDefeated) return true;
        if (client == null) return false;
        if (client.getVarbitValue(VarbitID.BARBASSAULT_QUEENKILLS_EXTRA_2) > 0
                || client.getVarbitValue(VarbitID.BARBASSAULT_QUEENKILLS_EXTRA) > 0
                || diaryBit(VarPlayerID.KANDARIN_ACHIEVEMENT_DIARY2, 2)) return true;
        // The low credit bit uses generic server variable IF3, so read it only through its reward-shop text.
        return hasQueenCreditText(client.getWidget(InterfaceID.BarbassaultRewardShop.BARBASSAULT_REWARDS));
    }

    private boolean hasQueenCreditText(Widget widget)
    {
        if (widget == null || widget.isHidden()) return false;
        Matcher credits = QUEEN_CREDITS.matcher(plainText(widget.getText()));
        if (credits.matches())
        {
            try { if (number(credits.group(1)) > 0) return true; }
            catch (NumberFormatException ignored) { return false; }
        }
        Widget[] children = widget.getDynamicChildren();
        if (children != null) for (Widget child : children) if (hasQueenCreditText(child)) return true;
        return false;
    }

    private Boolean allRolesLevelFive()
    {
        if (client == null) return null;
        boolean unknown = false;
        for (int id : new int[] {VarbitID.BARBASSULT_ROLELEVEL_ATT, VarbitID.BARBASSULT_ROLELEVEL_DEF,
                VarbitID.BARBASSULT_ROLELEVEL_COL, VarbitID.BARBASSULT_ROLELEVEL_HEAL})
        {
            int level = client.getVarbitValue(id);
            if (level >= 1 && level < 5) return false;
            if (level != 5) unknown = true;
        }
        return unknown ? null : Boolean.TRUE;
    }

    private Result training(int... ids)
    {
        if (client == null) return result(null);
        for (int id : ids) if (client.getVarbitValue(id) < 1) return result(false);
        return result(true);
    }

    private Result varbitAtLeast(int id, int minimum)
    {
        return result(client == null ? null : client.getVarbitValue(id) >= minimum);
    }

    private int diaryChompyLowerBound()
    {
        if (diaryBit(VarPlayerID.WESTERN_ACHIEVEMENT_DIARY2, 13)) return 1000;
        if (diaryBit(VarPlayerID.WESTERN_ACHIEVEMENT_DIARY2, 1)) return 300;
        if (diaryBit(VarPlayerID.WESTERN_ACHIEVEMENT_DIARY, 19)) return 125;
        return diaryBit(VarPlayerID.WESTERN_ACHIEVEMENT_DIARY, 6) ? 30 : 0;
    }

    private boolean hasProspector()
    {
        return hasItem(PROSPECTOR_HEADS) || collectionLog != null && collectionLog.isItemObtained(ItemID.MOTHERLODE_REWARD_HAT);
    }

    private boolean hasItem(Set<Integer> ids)
    {
        if (client == null) return false;
        for (int container : new int[] {InventoryID.INV, InventoryID.WORN, InventoryID.BANK})
            if (contains(client.getItemContainer(container), ids)) return true;
        return false;
    }

    private boolean wearingFullVoid()
    {
        if (client == null) return false;
        ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
        return contains(equipment, VOID_HEADS) && contains(equipment, VOID_TOPS)
                && contains(equipment, VOID_LEGS) && contains(equipment, VOID_GLOVES);
    }

    private static boolean contains(ItemContainer container, Set<Integer> ids)
    {
        if (container == null) return false;
        for (Item item : container.getItems())
            if (item != null && item.getQuantity() > 0 && ids.contains(item.getId())) return true;
        return false;
    }

    private boolean diaryBit(int varp, int bit)
    {
        return client != null && (client.getVarpValue(varp) & (1 << bit)) != 0;
    }

    private void save() { writeState.accept(accountKey, GSON.toJson(evidence)); }
    private static int number(String value) { return Integer.parseInt(value.replace(",", "")); }
    private static String plainText(String text)
    {
        return text == null ? "" : text.replaceAll("<[^>]*>", "").replace('\u00a0', ' ').trim();
    }
    private static Result result(Boolean met) { return new Result(met); }
    private static Result positive(boolean proof)
    {
        return result(proof ? Boolean.TRUE : null);
    }
    private static Map.Entry<String, Proof> proof(String text, int varp, int bit)
    {
        return Map.entry(text.toLowerCase(Locale.ROOT), new Proof(varp, bit));
    }
    private static class Proof
    {
        final int varp, bit;
        Proof(int varp, int bit) { this.varp = varp; this.bit = bit; }
    }
    static class Result
    {
        final Boolean met;
        Result(Boolean met) { this.met = met; }
    }
    private static class Evidence
    {
        int chompyKills;
        boolean queenDefeated;
        boolean prospectorAcquired;
        boolean voidSetWorn;
    }
}
