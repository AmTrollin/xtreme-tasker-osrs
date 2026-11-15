# Prerequisite tracking audit

Reviewed 2026-11-01. These checks supplement the existing skill, quest, miniquest, diary, currency, and structured quest-milestone checks. Task names, IDs, and task-completion verification blocks are unchanged.

## Result semantics

- **Met:** the available evidence proves the requirement.
- **Unmet:** a readable current value proves it is below the requirement.
- **Unknown:** the condition is unsupported, its data has not been read, or available evidence cannot disprove historical completion.

An unset diary-step bit never proves that a boss has never been killed. An unavailable bank never proves an item was never acquired. Positive historical evidence is saved using the plugin's stabilized account/character identity; session-only exact counts are discarded when that identity is cleared or changes. Unknown is an internal tracking state only: requirements without sufficient evidence stay unchecked, without additional UI labels or verification prompts.

## Counters and permanent unlocks

| Bundled requirement | Check | Source |
| --- | --- | --- |
| 50 / 153 Museum kudos | `VM_KUDOS` (varbit 3637) reaches the authored threshold | [RuneLite variables](https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/gameval/VarbitID.java) |
| White Knight Master | `WANTED_BLACK_KNIGHT_DEAD` (1057) reaches 1,300; use the current value because killing White Knights can reduce rank | [RuneLite variables](https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/gameval/VarbitID.java), [rank requirement](https://oldschool.runescape.wiki/w/White_Knight_rank) |
| Bones to Peaches | `MAGICTRAINING_BONESPEACHES` (1505) is at least 1 | [RuneLite variables](https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/gameval/VarbitID.java) |
| Any god spell | Mage Arena I finished, or at least one of `SARAMAGE`, `GUTHMAGE`, `ZAMOMAGE` (varps 268–270) reaches 100 | [RuneLite variables](https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/gameval/VarPlayerID.java), [Wilderness Diary](https://oldschool.runescape.wiki/w/Wilderness_Diary) |

The god-spell prerequisite does not demand the outside-arena unlock: the diary permits casting inside the arena. Owning a god staff alone is not proof of the quest or spell unlock.

### Chompy / jubbly kills

There is no assumed direct kill-count varp. Read the game's `You've scratched up a total of ...` message from **Check kills** on an ogre bow, accepting only `GAMEMESSAGE`. Subsequent `SPAM` notch notifications increment the count. Ignore public chat, messages for an inactive identity, and malformed/overflowing counts. The reader format and Chompy notch message are corroborated by the [Chompy Hunter implementation](https://github.com/PJGJ210/chompy-hunter/blob/8539e578510cd737460933b6f75f582f1aec3490/src/main/java/ejedev/chompyhunter/ChompyHunterPlugin.java).

The persisted count is a lower bound after relogging, since additional kills could have happened elsewhere. A new bow reading establishes an exact session count again. Higher completed hat-claim diary steps also prove lower kill requirements:

| Threshold | Saved step |
| --- | --- |
| 30 | Western varp 1182, bit 6 |
| 125 | Western varp 1182, bit 19 |
| 300 | Western varp 1183, bit 1 |
| 1,000 | Western varp 1183, bit 13 |

These flags come from Quest Helper's [easy](https://github.com/Zoinkwiz/quest-helper/blob/master/src/main/java/com/questhelper/helpers/achievementdiaries/westernprovinces/WesternEasy.java), [medium](https://github.com/Zoinkwiz/quest-helper/blob/master/src/main/java/com/questhelper/helpers/achievementdiaries/westernprovinces/WesternMedium.java), [hard](https://github.com/Zoinkwiz/quest-helper/blob/master/src/main/java/com/questhelper/helpers/achievementdiaries/westernprovinces/WesternHard.java), and [elite](https://github.com/Zoinkwiz/quest-helper/blob/master/src/main/java/com/questhelper/helpers/achievementdiaries/westernprovinces/WesternElite.java) diary definitions.

## Access and Barbarian Training

Warriors' Guild access uses real Attack + Strength >= 130, or a real level >= 99 in either skill. It never substitutes boosted levels. [Guild requirements](https://oldschool.runescape.wiki/w/Warriors%27_Guild)

Wilderness God Wars Dungeon access uses real Strength >= 60 or real Agility >= 60, matching the pack's existing base-level requirements. [Diary helper](https://github.com/Zoinkwiz/quest-helper/blob/master/src/main/java/com/questhelper/helpers/achievementdiaries/wilderness/WildernessMedium.java)

The three authored combined Barbarian Training lines refer to specific diary actions:

| Pack entry | Required training flags, each >= 1 |
| --- | --- |
| Fishing (Morytania elite) | `BRUT_FISHING_S` (3757), bare-handed fishing |
| Fishing, Firemaking, Smithing (Kandarin hard) | `BRUT_FISHING_R` (3758), `BRUT_FIRE` (3759), `BRUT_SMITH_SPEAR` (3762) |
| Fishing, Firemaking, Smithing, Herblore (Kandarin elite, including inherited hard requirements) | 3758, 3757, 3759, `BRUT_CRAFT_SHIP` (3764), 3762, `BRUT_SMITH_HASTA` (3763), `BRUT_HERB_POTION` (3761) |

One means Otto has taught the activity; two means the demonstration was performed; three means the training was reported back. These are **access** checks, so do not demand three. Fishing rod training does not substitute for bare-handed training, and spear training does not substitute for hasta training. [Training state definitions](https://github.com/Zoinkwiz/quest-helper/blob/master/src/main/java/com/questhelper/helpers/miniquests/barbariantraining/BarbarianTraining.java)

## Barbarian Assault

Role levels are varbits 3251 (Attacker), 3253 (Defender), 3254 (Collector), and 3255 (Healer). All four must be five. Values outside 1–5 are treated as unavailable. In particular, **3252 is horn status**, not Defender level. [Reward-shop client script](https://github.com/RuneStar/cs2-scripts/blob/master/scripts/%5Bproc%2Cscript1367%5D.cs2), [RuneLite variable names](https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/gameval/VarbitID.java)

Queen-kill **credits are spendable**. The client script combines `varbit2460 * 16 + varbit4767 * 2 + var263`; `var263` is generic scratch data and must not be trusted outside the reward shop. Positive dedicated credit bits, a visible shop row `Kill Queen (N)` with N > 0, or the completed Kandarin granite-body purchase step (varp 1179, bit 2) prove a previous Queen kill. Remember that evidence even after credits are spent. Zero credits remain unknown. [Credit encoding](https://github.com/RuneStar/cs2-scripts/blob/master/scripts/%5Bproc%2Cscript1370%5D.cs2), [shop display](https://github.com/RuneStar/cs2-scripts/blob/master/scripts/%5Bproc%2Cscript1367%5D.cs2), [granite-body diary step](https://github.com/Zoinkwiz/quest-helper/blob/master/src/main/java/com/questhelper/helpers/achievementdiaries/kandarin/KandarinHard.java)

The combined elite requirement needs both Queen-kill evidence and all four role levels. A known role below five proves the combination unmet; otherwise missing evidence stays unknown.

## Equipment

- **Acquire a prospector helmet:** inventory/equipment/available bank evidence for normal, golden, or fossil-island variants; a synced original collection-log entry; or the saved full-prospector Mining Guild diary step. Remember positive acquisition evidence.
- **Acquire and wear complete void:** all four working pieces in the equipment container, or Western elite's completed void step (varp 1183, bit 12). Accept normal, elite, locked, and League cosmetic variants; reject broken pieces and pieces merely sitting in inventory. Remember observing the complete equipped set.
- **Quest Cape:** verify the actual quest point cape or trimmed cape in inventory/equipment/available bank data. Do not accept its hood. This checks possession, not eligibility inferred from an assumed list of all current quests. Absence of item evidence stays unknown.

Item identities come from [RuneLite ItemID](https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/gameval/ItemID.java). Historical diary evidence is defined in [Falador hard](https://github.com/Zoinkwiz/quest-helper/blob/master/src/main/java/com/questhelper/helpers/achievementdiaries/falador/FaladorHard.java) and [Western elite](https://github.com/Zoinkwiz/quest-helper/blob/master/src/main/java/com/questhelper/helpers/achievementdiaries/westernprovinces/WesternElite.java).

## Historical activities

These checks use individual completed diary steps as **positive evidence only**. They do not claim to read a lifetime boss log or observe every new boss kill. Without the matching flag, the row remains unknown.

| Activity | Varp / bit | Quest Helper source |
| --- | --- | --- |
| Giant Mole | 1186 / 28 | [Falador hard](https://github.com/Zoinkwiz/quest-helper/blob/master/src/main/java/com/questhelper/helpers/achievementdiaries/falador/FaladorHard.java) |
| Kalphite Queen | 1198 / 27 | [Desert hard](https://github.com/Zoinkwiz/quest-helper/blob/master/src/main/java/com/questhelper/helpers/achievementdiaries/desert/DesertHard.java) |
| Kalphite Queen head | 1199 / 6 | [Desert elite](https://github.com/Zoinkwiz/quest-helper/blob/master/src/main/java/com/questhelper/helpers/achievementdiaries/desert/DesertElite.java) |
| Zulrah | 1183 / 3 | [Western hard](https://github.com/Zoinkwiz/quest-helper/blob/master/src/main/java/com/questhelper/helpers/achievementdiaries/westernprovinces/WesternHard.java) |
| Thermonuclear smoke devil | 1183 / 7 | [Western elite](https://github.com/Zoinkwiz/quest-helper/blob/master/src/main/java/com/questhelper/helpers/achievementdiaries/westernprovinces/WesternElite.java) |
| Lizardman Shaman in the temple | 2085 / 28 | [Kourend hard](https://github.com/Zoinkwiz/quest-helper/blob/master/src/main/java/com/questhelper/helpers/achievementdiaries/kourend/KourendHard.java) |
| Skotizo / Hydra / Chambers of Xeric | 2086 / 6, 8, 10 | [Kourend elite](https://github.com/Zoinkwiz/quest-helper/blob/master/src/main/java/com/questhelper/helpers/achievementdiaries/kourend/KourendElite.java) |
| All Dagannoth Kings | 1184 / 31 | [Fremennik elite](https://github.com/Zoinkwiz/quest-helper/blob/master/src/main/java/com/questhelper/helpers/achievementdiaries/fremennik/FremennikElite.java) |
| GWD generals except Nex | 1185 / 3 | [Fremennik elite](https://github.com/Zoinkwiz/quest-helper/blob/master/src/main/java/com/questhelper/helpers/achievementdiaries/fremennik/FremennikElite.java) |
| Chaos Elemental / three Wilderness demi-bosses | 1192 / 30, 31 | [Wilderness hard](https://github.com/Zoinkwiz/quest-helper/blob/master/src/main/java/com/questhelper/helpers/achievementdiaries/wilderness/WildernessHard.java) |
| Callisto/Artio, Venenatis/Spindel, Vet'ion/Calvar'ion | 1193 / 3 | [Wilderness elite](https://github.com/Zoinkwiz/quest-helper/blob/master/src/main/java/com/questhelper/helpers/achievementdiaries/wilderness/WildernessElite.java) |
| Final-room Pyramid Plunder gold chest | 1199 / 7 | [Desert elite](https://github.com/Zoinkwiz/quest-helper/blob/master/src/main/java/com/questhelper/helpers/achievementdiaries/desert/DesertElite.java) |
| Barrows chest in full Barrows | 1181 / 8 | [Morytania elite](https://github.com/Zoinkwiz/quest-helper/blob/master/src/main/java/com/questhelper/helpers/achievementdiaries/morytania/MorytaniaElite.java) |

The wave-31 Ket-Zek requirement uses positive `ATJUN_HARD_FIGHTCAVE` (varbit 3601). The pack's `TzHaar Fight Cave` entry uses the Karamja elite fire-cape step or possession of a fire cape. [Karamja hard](https://github.com/Zoinkwiz/quest-helper/blob/master/src/main/java/com/questhelper/helpers/achievementdiaries/karamja/KaramjaHard.java), [Karamja elite](https://github.com/Zoinkwiz/quest-helper/blob/master/src/main/java/com/questhelper/helpers/achievementdiaries/karamja/KaramjaElite.java)

## Remaining manual entries and coverage

- `See Wiki (Based on Raid)` has no authored condition to evaluate.
- `Own a Jagex Account or set up the older RuneScape Authenticator` has no verified reader in this implementation.

With all relevant evidence available, the bundled raw-prerequisite coverage test permits only those two descriptions to remain unknown. Other supported entries can still be unknown when evidence is unavailable, as described above. The audit also fixed abbreviated names for Desert Treasure II, The Hand in the Sand, and Shades of Mort'ton, and recognizes directly named quests without a `quest` suffix. No task names were changed.

Validation covers counter boundaries, both guild access routes, all four BA roles, spent credits, public-chat rejection, account switching, corrupted evidence, unavailable banks, complete versus partial/broken void, quest cape versus hood, signed diary bit 31, unknown OR alternatives, and bundled prerequisite coverage. Source-based automated tests do not replace live in-game verification.
