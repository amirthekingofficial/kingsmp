# 👑 KingSMP: Rework & Balance Directive v2.0
> **For**: Gemini 3.8 (implementation)
> **Target Version**: Minecraft `26.2` ("Chaos Cubed") | Fabric Loader `>=0.19.5` | Fabric API `0.160.0+26.2` | Java SE `25`
> **Mod ID**: `kingsmp`
> **Document Purpose**: Full implementation directive to evolve KingSMP from a single-crown power-stacking mod into a multi-pillar fantasy server mod (Crowns, Professions, branching Ranks) with a fixed, sustainable economy.

---

## 0. Non-Negotiable Engineering Constraints

These apply to every system below, no exceptions:

1. **100% Server-Sided.** Nothing in this rework may require a client-side mod, resource pack, or shader to function correctly. Custom item models (crowns, etc.) should degrade gracefully to vanilla-look items if no resource pack is present — never require one. All GUIs must be built from vanilla `ScreenHandler`/container types so vanilla clients can use them.
2. **Commands must be short, memorizable, and forgiving.** Prefer one root command per system with clear subcommands and tab-completion (`/king`, `/trade`, `/rank`), not sprawling flags. Every command needs a `/… help` subcommand. Typo-tolerant aliasing where cheap (e.g. `/k` → `/king`).
3. **No dragons in this pass.** Do not implement dragon taming/bonding/eggs-as-mounts. Leave hooks (a commented `// TODO: dragon pillar` marker in relevant manager classes) but ship nothing dragon-related this version.
4. **Preserve the existing Silver economy** (`/bal`, `/pay`, `/sell`, `/shop`, `/price`) as the single virtual currency. Do not introduce a second currency. Fix its balance and inflation problems (Section 4) rather than replacing it.
5. **All new systems must persist through the existing save pipeline** (`world/kingsmp/` JSON/NBT, 6000-tick autosave, 15s deferred-mutation throttle via `KingSMPMod.saveNow()`). New data classes plug into that pipeline; do not create a second save system.
6. **Every numeric constant must live in a single config file** (`kingsmp-config.json` or equivalent), not hardcoded in logic classes, so the server owner can retune without a rebuild.
7. **Atomic transactions everywhere.** Any action that checks-then-consumes multiple resources (crown upgrades, profession crafting, faction costs) MUST fully validate all costs before deducting anything. This was Flaw 3 in the original mod (`tryCharge` inventory eraser bug) — the fix pattern below is mandatory for all new consumption code, not just crowns:
   ```java
   // Pattern: validate-all, then commit-all. Never interleave check+consume per item.
   if (!hasAll(player, costs)) { fail(); return; }
   for (Cost c : costs) { remove(player, c); }
   grant(player, reward);
   ```

---

## 1. Overview of the New Pillar Structure

The old mod had one power axis: Crown Tier 1–5, strictly stacked, which produced the "God-King" problem (Section 12, Flaw 1 of the original audit). The rework splits progression into **three parallel pillars** so power is *lateral*, not just *vertical*:

| Pillar | Who it's for | Zero-sum? | Core loop |
|---|---|---|---|
| **Crowns** (Section 2) | PvP/political players | Yes — 5 slots total | Elections, regicide, usurpation |
| **Ranks** (Section 3) | Faction/social players | No — everyone can rank up | Branching faction trees, role abilities |
| **Professions** (Section 4) | Everyone, including solo/PvE players | No — everyone can master trades | Gathering/crafting loops, trade-specific unlocks |

A player's identity should read as a combination, e.g. *"Seneschal Elira, Master Blacksmith"* rather than a single number. Implement a title-composition system (Section 5) that concatenates Rank title + Profession title for display in tab list, chat prefix, and `/who`.

---

## 2. The Five Crowns (replaces single Crown Tier 1–5)

### 2.1 Design intent
Replace the linear Tier 1→5 Crown with **five distinct, independently-electable Crowns**, each a different combat/utility archetype matching the custom models in the server assets (`crown.json`, `end_crown.json`, `ice_crown.json`, `lava_crown.json`, `skull_crown.json`). This keeps everything players already love (elections, regicide, usurpation, un-droppable helmet item) but removes the single-axis stacking that made a maxed King unkillable.

### 2.2 The five Crowns

| Crown | Archetype | Base item / model | Signature strength | Built-in weakness |
|---|---|---|---|---|
| **Crown of Skulls** | War-King (tank/frontline) | `carved_pumpkin` reskin, `skull_crown.json` (CMD 5.0) | High HP (+6 Hearts max), Strength II, Knockback Resist (+0.30) | No ranged option; slow to disengage (movement speed −10% while worn) |
| **Crown of Gold** | Merchant-King (economy) | `crown.json` (CMD 1.0) | Market authority (see 2.5), shop discount, sees all global listings | No combat buffs at all beyond vanilla; Resistance 0 |
| **Crown of the End** | Void-King (territory) | `end_crown.json` (CMD 2.0) | Extra faction outposts (+2), claim-radius buff, spawner yield bonus on owned land | Loses all bonuses outside claimed territory |
| **Crown of Lava** | Pyromancer-King (nether/utility) | `lava_crown.json` (CMD 4.0) | Fire immunity, faster nether travel (Speed II in Nether, Speed I elsewhere), "Ember Burst" activated AoE ability (see 2.6) | Takes +20% damage from water/ranged sources; ability has a long cooldown |
| **Crown of Ice** | Wraith-King (mobility/stealth) | `ice_crown.json` (CMD 3.0) | Speed II, once-per-day "Vanish" (breaks combat tag + 3s invisibility), reduced fall damage | Lowest HP pool of the five; no innate resistance |

**Only one player may hold each Crown at a time.** Up to 5 Kings total, exactly as before — this preserves your existing election cadence, broadcast strings, and leaderboard systems almost unchanged.

### 2.3 Stat caps (apply to ALL five Crowns — hard caps, not suggestions)

- **Resistance**: capped at **Resistance I**. Never grant Resistance II or higher to any Crown, permanently, under any circumstance.
- **Strength / Speed**: capped at **Strength II** / **Speed II**. (Speed III is confirmed to break PvP hit registration on `26.2` netcode — do not exceed it even on Crown of Ice; give Ice its edge via the Vanish ability instead of raw Speed III.)
- **Health**: linear scaling per Crown, no exponential jump at max tier:
  - Base Crown: +2 Hearts (+4 HP)
  - No further "tiers" — a Crown is fully powered the moment it's worn. (Upgrade *paths* still exist — see 2.4 — but they add **utility**, not raw HP beyond this cap, except where a Crown's kit is explicitly HP-focused like Crown of Skulls, capped at +6 Hearts / +12 HP total for Skulls only.)
- **No permanent Regeneration II or higher** on any Crown. If a "surge" mechanic is wanted for flavor, it must be conditional (see 2.6), never passive.

### 2.4 Upgrade paths (replaces the old 5-tier gold/diamond sink)

Each Crown still has a 3-step internal upgrade path (not "power tiers" — **utility unlocks**), using a validate-all-then-consume pattern (see Section 0.7):

- **Step 1 → 2**: cosmetic glow/particle unlock + minor QoL (e.g. Skulls gets +1 armor point; Gold gets a market fee rebate; End gets +1 outpost slot).
- **Step 2 → 3**: unlocks the Crown's signature activated ability (Ember Burst, Vanish, Last Stand).
- **Step 3 (max)**: unlocks a **passive minor faction-wide aura** for the King's own faction only (e.g. Skulls King's faction gets Resistance I while within 30 blocks of the King — encourages Kings to actually fight alongside their people instead of turtling).

Costs should be pulled from the existing material sink list (diamonds, netherite, nether stars, echo shards, heavy cores) but rebalanced across the new 3-step path per Crown so the *total* material sink across all 5 Crowns is roughly equal to the original single Crown's full Tier 1→5 cost. Don't 5x the total material drain by accident.

### 2.5 Crown of Gold specifics (Merchant-King)
- Sees a live global `/shop` listing overlay (all active player market listings, server-wide) via a dedicated GUI page.
- Can set a server-wide market tax rate between 0–5% (replaces the old per-faction tax exploit — see Section 4.4). This tax is deducted from actual sale proceeds, never minted.
- Personal shop discount: 10% off `/shop` NPC purchase prices only (never off player-to-player market).

### 2.6 Conditional "Climax" abilities (replaces permanent Regen II / Res II)
Instead of passive god-mode stats, give power-spike moments that are earned, not standing:

- **Crown of Skulls — "Last Stand"**: when HP drops below 30%, gain Resistance I + Regeneration I for 8 seconds. 120s internal cooldown.
- **Crown of Lava — "Ember Burst"**: activated AoE fire pulse (short range, moderate damage + ignite) on a 90s cooldown.
- **Crown of Ice — "Vanish"**: once per real-world day, break combat tag instantly + 3s invisibility + speed burst. Long cooldown (24h) is intentional — this is an escape valve, not a combat loop.
- **Crown of Gold / Crown of the End**: no combat climax ability by design — their power expresses through economy/territory, not personal combat. Do not add one "for fairness"; fairness here comes from the pillar being different, not equal in a fight.

### 2.7 Degradation & usurpation (fixes)
- Remove the old numeric-tier degradation bug entirely (Flaw 2: Tier 5 never degraded). Since Crowns no longer have 5 combat-power tiers, degradation instead applies **only to the 3-step upgrade path** (Section 2.4): on death, a King's Crown drops one upgrade step (Step 3 → Step 2 → Step 1), never fully removing the Crown's core identity, and — critically — **this must actually execute on every death**, with no `level == max` exemption. Unit-test this path explicitly.
- Usurpation logic (non-King picks up a dropped Crown, is crowned if `< 5` active Kings) stays as-is, but now applies per-Crown-type: picking up a *Crown of Ice* makes you the Wraith-King, specifically, not "a King" generically.

---

## 3. Branching Faction Ranks (replaces flat Recruit→Knight→Commander→King)

### 3.1 Design intent
Flat rank ladders make "promotion" just mean "bigger number." Replace with **four branching paths**, each 4 rungs deep, so rank progression is a *choice of identity*, not a grind toward one ceiling. A path's top rung is also the **eligibility gate** for running in that Crown's election — this is what ties your social/faction game directly into the political endgame.

### 3.2 The four paths

| Path | Rungs (low → high) | Passive buffs (cumulative, capped per Section 2.3 rules) | Top rung grants |
|---|---|---|---|
| **Military** | Recruit → Man-at-Arms → Knight → Commander | Knight: Resistance I. Commander: Resistance I + Speed I (inherits Knight's Resistance — do NOT let Commander lose a lower rank's perk, this was a gap in the original spec) | Eligible to run for **Crown of Skulls** |
| **Logistics** | Recruit → Steward → Quartermaster → Seneschal | Quartermaster: faction bank withdraw access. Seneschal: personal shop discount + sets faction tax rate | Eligible to run for **Crown of Gold** |
| **Scout** | Recruit → Outrider → Ranger → Pathfinder | Ranger: no fall damage. Pathfinder: can place temporary faction waypoints, reduced `/rtp` cooldown | Eligible to run for **Crown of Ice** |
| **Occult** | Recruit → Acolyte → Chanter → Warden of Lava | Chanter: can cast a short faction-wide buff once per cooldown. Warden of Lava: fire resistance, buff cooldown reduced | Eligible to run for **Crown of Lava** |

*(Note: Crown of the End is granted via the Faction Leader's own outpost/territory standing, not a rank path — a faction's actual leader, regardless of path, is the one eligible to run for it. Confirm this with the design owner before implementation if it feels inconsistent; documented here as the current intent.)*

### 3.3 Rules
- A player picks ONE path after reaching Recruit; switching paths should be allowed but costs Silver and resets progress in the new path to its first rung (prevents free-switching abuse before elections).
- Path rung-up requires a small task/cost gate (time-in-faction + a modest Silver/material cost), not just leader approval, so it can't be instantly abused to mass-produce election-eligible members.
- Command surface: `/rank path <military|logistics|scout|occult>`, `/rank info`, `/rank promote <player>` (leader-only, only promotes within the member's chosen path).

---

## 4. Professions (new pillar — everyone participates, no rank/crown required)

### 4.1 Design intent
The original economy has exactly one loop: mine/gather → `/sell` for Silver. This is fine as a floor but gives solo/PvE players nothing to master. Professions add craft-and-specialize gameplay that also deepens the economy (see 4.4 for how this interacts with inflation).

### 4.2 The trades (ship all of these in this pass)

| Trade | Core loop | Feeds into | Max-level unlock |
|---|---|---|---|
| **Fisher** | Fish tiered catches per biome (junk/common/rare/"Leviathan" catches requiring a boat + short mini-event) | Cook, Alchemist (rare fish reagents) | "Lucky Line" — small chance of a bonus rare catch on every cast |
| **Farmer** | Grow/harvest crops, unlock cross-breed rare strains | Cook, faction food buffs | Yield +25% on owned/claimed farmland |
| **Miner/Prospector** | Mine ore with bonus yield chance, temporary "vein sense" ability | Blacksmith | Short-duration ore x-ray pulse, long cooldown |
| **Blacksmith** | Craft/upgrade gear beyond vanilla stats; sole trade able to repair/upgrade Crown items (Section 2.4 upgrade materials should route through a Blacksmith crafting station, not raw `/king upgrade` menu consumption — this makes non-Kings economically essential to Kings) | consumes Miner output | "Masterwork" craft: small chance any craft comes out with a bonus enchant/stat |
| **Alchemist** | Brew potions beyond vanilla duration/potency caps (within Section 2.3 hard caps — an Alchemist potion must never let a player exceed Resistance I etc. server-wide) | Chanter faction buffs | Can brew a bottled version of a Crown's Step-2 minor buff, sellable to non-Kings for a short duration |
| **Hunter/Trapper** | Bonus mob drop rates, can tag rare "Elite" mob spawns for bounty rewards | Alchemist, Cook | Can see Elite mob spawns on a personal minimap ping |
| **Merchant/Trader** | Runs caravan routes between faction outposts, can operate a personal shop stall at reduced/no listing fee | ties into Crown of Gold's market vision | Reduced market fee (see 4.4) on own listings only |

### 4.3 Progression mechanics
- Simple XP-per-action leveling per trade (e.g. catching a fish, smelting ore, brewing a potion grants trade XP). No unlock should require PvP or crown/rank standing — professions must be fully accessible to solo players.
- Players may progress multiple trades simultaneously but at a reduced XP rate past the 2nd concurrent trade being actively leveled, to encourage specialization without hard-locking choice.
- Command surface: `/trade info`, `/trade board` (shows all trades and current level), `/trade recipes <trade>`.

### 4.4 Economy fixes (apply regardless of professions — these are bugfixes to existing systems)

These directly address the original audit's Flaws 4, 5, and 7. Implement all of them:

1. **Kill the faction tax money-printer (Flaw 7).** The current `removeSilver(uuid, amount, true)` faction tax mints new Silver into the Faction King's balance from nothing, up to 5,000/day. **Fix**: tax must be deducted *from the actual transaction proceeds* (e.g. a cut of a `/market` sale or `/sell` payout), never created from the void. Remove the daily-mint cap entirely since there's no more minting.
2. **Add a flat 5% fee on all player-to-player `/market` sales**, deducted at sale time, removed from the economy entirely (a true sink, not redirected to anyone) — this is your primary anti-inflation lever. Merchant/Trader profession max-level unlock reduces this to ~3% on their own listings only, as a real, felt benefit for the trade.
3. **Fix gamble crate EV (Flaw 4).** Retune all five crate loot tables so **average Return-to-Player lands between 70–85%** of ticket price, and Legendary-tier rewards must always be worth meaningfully more (4–8x ticket price) than Common-tier rewards — audit every existing crate table line-by-line against this rule; the original End Crate and Nether Crate legendary drops were worth LESS than their own common drops, which must not happen anywhere in the new tables.
4. **Fix quest reward/difficulty inversion (Flaw 8).** Use the harmonized table already drafted: Easy ~150 Silver (10 Zombies/32 Coal/16 Iron), Medium ~350 Silver + 1 Diamond (25 Zombies/12 Endermen/24 Lapis), Hard ~750 Silver + 3 Diamonds (8 Wither Skeletons/16 Diamond Ore/6 Ancient Debris). Verify no Easy quest is ever harder than a Medium quest of the same category (the original had a 20-Zombie Easy quest that was harder than a 10-Zombie Medium quest — audit for this pattern across all categories, not just the one instance already found).
5. **tryCharge atomic fix (Flaw 3)** — mandatory per Section 0.7, applies to Crown upgrades, Blacksmith crafting, and any future consumption code.
6. **Teleport panic-button fix (Flaw 6)** — add a 3-second stationary channel to `/home`, `/tpa` accept-side teleport, and `/rtp`. Cancel the channel if the player moves >0.5 blocks OR receives the PvP combat tag during the channel (not just any damage — falling in lava mid-channel shouldn't be exploitable to escape a fight, but it also shouldn't unfairly strand someone from an unrelated mob hit; gate specifically on combat-tag application).

### 4.5 Sanity check before shipping
Have a test pass where a brand-new player, with zero rank/crown/profession progress, can:
- Earn Silver from vanilla-tier gathering within the first 10 minutes.
- See a clear, in-game (`/trade`, `/rank`, `/king help`) explanation of all three pillars without needing this document.
- Not be flagged combat-taggable/bounty-able for their first 3 in-game days (new player protection window — carry this over from prior design discussion even though it wasn't in the original audit doc).

---

## 5. Title Composition & UI

- Build a simple concatenation system: `[Rank Title] [Player Name], [Profession Title]` for tab list and `/who`, e.g. `Seneschal Elira, Master Blacksmith`. Config-driven format string so server owner can reorder/restyle.
- Keep the existing `ItemStackMixin` "Worth: X Silver" tooltip system as-is — it's good UX, don't touch it except to make sure it still excludes shop/gamble GUI contexts as before.
- Election broadcast strings must be corrected per Flaw 9: clearly state which of the 5 Crowns is up for election and that 5 *different* thrones exist — never imply a single monarch.

---

## 6. Command Index (new/changed only — keep everything else from the original index unchanged)

```
/king help                         - unchanged
/king status                       - now shows status per-Crown (5 lines, one per Crown)
/king upgrade                      - now routes through Blacksmith crafting station, not raw inventory-hold upgrade
/rank path <military|logistics|scout|occult>
/rank info
/rank promote <player>             - leader-only, path-restricted
/trade info
/trade board
/trade recipes <trade>
/market                            - unchanged command, now applies 5% fee (3% for max-level Merchants) automatically
```

Admin commands (`/king crown`, `/king dethrone`, `/king election`, etc.) stay conceptually the same but must be updated to take a Crown-type argument, e.g. `/king crown <player> <skulls|gold|end|lava|ice>`.

---

## 7. Delivery Checklist for Gemini 3.8

- [ ] All five Crowns implemented as distinct items/abilities, stat caps enforced server-side (hard caps, not just default config values — a config edit should not be able to exceed Resistance I / Strength II / Speed II under this spec; flag this constraint clearly in code comments so a server owner editing config understands why).
- [ ] Crown degradation executes on every death, no max-tier exemption.
- [ ] Branching rank paths implemented, each with distinct passives (cumulative, no perk loss on promotion) and Crown-election eligibility gating.
- [ ] All 7 professions implemented with leveling, recipes, and max-level unlocks.
- [ ] Faction tax no longer mints Silver — deducted from real transactions only.
- [ ] 5% market fee live (3% for max-level Merchant/Trader on own listings).
- [ ] All 5 gamble crates re-tuned to 70–85% RTP with correctly-ordered rarity value (Legendary always > Common).
- [ ] Quest reward table harmonized, audited for any remaining difficulty/reward inversions.
- [ ] tryCharge-style atomic validation applied to every multi-item consumption path in the codebase, not just Crown upgrades.
- [ ] 3-second teleport channel added to `/home`, `/tpa`, `/rtp`, cancels on move or combat-tag (not generic damage).
- [ ] Title composition system live in tab list / `/who`.
- [ ] Election broadcast strings corrected to reflect 5 distinct Crowns.
- [ ] Everything ships server-side only — no required resource pack or client mod for any feature to function.
- [ ] New-player 3-day combat/bounty immunity window implemented.
- [ ] Dragon hooks left as commented TODOs only — no dragon feature work this pass.