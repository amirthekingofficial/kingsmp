# 👑 KingSMP: Complete Master Specification, Mechanics & Balance Audit

> **Target Version**: Minecraft `26.2` ("Chaos Cubed") | Fabric Loader `>=0.19.5` | Fabric API `0.160.0+26.2` | Java SE `25`  
> **Mod ID**: `kingsmp`  
> **Document Purpose**: Complete technical reference, data dictionary, economy audit, mathematical model, and balance analysis designed for system refinement, game balancing, and tuning.

---

## 📑 Table of Contents
1. [Core Design Philosophy & Server Loop](#1-core-design-philosophy--server-loop)
2. [Technical Architecture & State Management](#2-technical-architecture--state-management)
3. [The Monarchy & Crown System (Combat & Progression)](#3-the-monarchy--crown-system-combat--progression)
4. [Complete Economy & Valuation Matrix](#4-complete-economy--valuation-matrix)
5. [Spawners, Loot Caches & Gamble Mechanics](#5-spawners-loot-caches--gamble-mechanics)
6. [Factions & Territory Hierarchy](#6-factions--territory-hierarchy)
7. [Daily Contracts & Quest System](#7-daily-contracts--quest-system)
8. [Virtual Storage & Anti-Hoarding Rules](#8-virtual-storage--anti-hoarding-rules)
9. [PvP, Combat Tagging & Anti-Combat Log](#9-pvp-combat-tagging--anti-combat-log)
10. [Teleportation & Travel Systems](#10-teleportation--travel-systems)
11. [Complete Command & Permission Index](#11-complete-command--permission-index)
12. [In-Depth Balance Audit & Critical Vulnerabilities](#12-in-depth-balance-audit--critical-vulnerabilities)
13. [Refinement Guidelines for Claude (Tuning Blueprint)](#13-refinement-guidelines-for-claude-tuning-blueprint)

---

## 1. Core Design Philosophy & Server Loop

KingSMP is a competitive, monarchy-driven survival multiplayer mod. The gameplay loop centers on:
1. **Democratic Power Acquisition**: Regular democratic elections occur every 250 in-game days (~83 real-world hours) where players campaign and vote. Up to **5 Kings** are elected simultaneously.
2. **Asymmetric Combat Advantages**: Kings wield **The Royal Crown**, an un-droppable, upgradable helmet granting massive health, attribute, and potion status buffs.
3. **Regicide & Usurpation**: If a King is slain, their Crown drops, and any non-King can physically steal it to usurp the throne.
4. **Economy & Mercenaries**: Players earn **Silver** (`🪙`) by selling raw resources, completing daily contracts, claiming bounties, or operating player-to-player markets.
5. **Tribal Faction Warfare**: Kings and warlords form factions, establish outposts, recruit knights, and pool wealth into collective treasuries.

```
       ┌────────────────────────────────────────────────────────┐
       │                Democratic Election Cycle               │
       │                   (Every 250 Days)                     │
       └───────────────────────────┬────────────────────────────┘
                                   │ Elects up to 5 Kings
                                   ▼
┌──────────────────┐      ┌──────────────────┐      ┌──────────────────┐
│  Faction Empire  │◄────►│  The Royal Crown │◄────►│ Global Economy   │
│ Outposts/Knights │      │ Upgrades (1 → 5) │      │ Silver/Sell/Shop │
└──────────────────┘      └────────┬─────────┘      └──────────────────┘
                                   │
                         Killed in Battle / Dropped
                                   │
                                   ▼
                      ┌──────────────────────────┐
                      │    Crown Degradation     │
                      │   Regicide & Usurpation  │
                      └──────────────────────────┘
```

---

## 2. Technical Architecture & State Management

### 2.1 Engine & Toolchain
* **Minecraft**: `26.2` (Mojang official mappings).
* **Fabric API**: `0.160.0+26.2`.
* **Fabric Loader**: `0.19.5`.
* **Java Target**: Java SE `25`.
* **Component-Driven NBT**: Utilizes Minecraft 26.x `DataComponents` (`CUSTOM_DATA`, `ATTRIBUTE_MODIFIERS`, `LORE`, `CUSTOM_NAME`, `UNBREAKABLE`, etc.).
* **Color Collections**: Integrates Mojang's `ColorCollection<Item>` API records for stained glass, wool, beds, banners, and dyed shulker boxes.

### 2.2 Persistence & Auto-Save
* **Storage Location**: `world/kingsmp/` JSON and NBT structures.
* **Auto-Save Frequency**: Every `6000` ticks (5 minutes) via `ServerTickEvents.END_SERVER_TICK`.
* **Deferred Mutation Save**: `KingSMPMod.saveNow()` executes immediately if `>15s` have elapsed since the last disk flush; otherwise schedules an asynchronous `CompletableFuture` throttle at 15s to prevent I/O micro-stutters.

---

## 3. The Monarchy & Crown System (Combat & Progression)

### 3.1 Crown Item Properties
* **Base Item**: `minecraft:carved_pumpkin` (re-textured via custom models: `crown.json`, `end_crown.json`, `ice_crown.json`, `lava_crown.json`, `skull_crown.json`).
* **Tags**: `Unbreakable: true`, `EnchantmentGlint: true`.
* **Equip Slot**: Helmet (`EquipmentSlotGroup.HEAD`).
* **Anti-Drop Protection**: Custom Mixin (`ServerPlayerEntityMixin.java`) cancels the drop key (`Q`) and forcefully re-syncs the player inventory slot to prevent ghost items.

### 3.2 Crown Upgrade Progression & Costs

Upgrades are executed through `/king upgrade` while holding the Crown in inventory.

```
Tier 1 (Base Crown)
  │
  ├─► Cost to Tier 2: 32x Diamond, 16x Gold Block, 1x Ominous Bottle
  │
Tier 2
  │
  ├─► Cost to Tier 3: 4x Netherite Ingot, 64x Diamond, 1x Nether Star
  │
Tier 3
  │
  ├─► Cost to Tier 4: 2x Netherite Block, 3x Nether Star, 8x Echo Shard
  │
Tier 4
  │
  └─► Cost to Tier 5: 1x Heavy Core, 4x Netherite Block, 5x Nether Star
```

### 3.3 Complete Stat & Buff Matrix by Tier

| Crown Tier | Extra Health | Total Health | Potion Status Effects (Ambient/Passive) | Armor Attribute | Armor Toughness | Knockback Resistance |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Tier 1** | +4 Hearts (+8 HP) | 14 Hearts (28 HP) | Speed I, Strength I, Resistance I, Hero of the Village | +0 | +0 | +0.0 |
| **Tier 2** | +6 Hearts (+12 HP) | 16 Hearts (32 HP) | Speed II, Strength I, Resistance I, Fire Resistance, Hero of the Village | +3.0 | +2.0 | +0.0 |
| **Tier 3** | +10 Hearts (+20 HP) | 20 Hearts (40 HP) | Speed II, Strength II, Resistance I, Fire Resistance, Hero of the Village | +5.0 | +3.0 | +0.10 |
| **Tier 4** | +14 Hearts (+28 HP) | 24 Hearts (48 HP) | Speed II, Strength II, Resistance II, Fire Resistance, Regeneration I, Hero of the Village | +7.0 | +4.0 | +0.20 |
| **Tier 5 (MAX)**| +20 Hearts (+40 HP) | 30 Hearts (60 HP) | Speed III, Strength III, Resistance II, Fire Resistance, Regeneration II, Hero of the Village | +10.0 | +6.0 | +0.30 |

*Effect Refresh Rate*: Refreshed every `40` server ticks (2 seconds) with a duration of 100 ticks (5 seconds).

### 3.4 Degradation & Usurpation Mechanics
* **Usurpation**: If a player holds a Crown in inventory and is not currently a King, and the total active King count is `< 5`, they are immediately crowned, healed for +4 Hearts, and a server-wide broadcast announcement fires.
* **Current Degradation Rule**: On death, `ServerPlayerEntityMixin` searches the inventory:
  ```java
  if (level < 5 && level > 1) {
      KingSMPMod.setCrownLevel(stack, level - 1);
  }
  ```
  *(Note: See Section 12 for the critical flaw where Tier 5 never degrades).*

---

## 4. Complete Economy & Valuation Matrix

The server economy is built on a virtual integer currency: **Silver** (`🪙`).

### 4.1 Bank Sell Values (`/sell` and `/price`)
Players can sell inventory items directly to the virtual Bank for guaranteed Silver.

#### A. Ores & Raw Minerals
| Item | Bank Buy Price (Silver) | Notes |
| :--- | :--- | :--- |
| **Netherite Block** | `900.0` 🪙 | Highest base block value |
| **Netherite Ingot** | `100.0` 🪙 | 4 Debris + 4 Gold = 104 cost ratio |
| **Ancient Debris** | `25.0` 🪙 | |
| **Netherite Scrap** | `25.0` 🪙 | |
| **Diamond Block** | `18.0` 🪙 | 9x Diamond ratio |
| **Diamond** | `2.0` 🪙 | Benchmark mid-tier commodity |
| **Emerald Block** | `9.0` 🪙 | |
| **Emerald** | `1.0` 🪙 | |
| **Gold Block** | `9.0` 🪙 | |
| **Gold Ingot** | `1.0` 🪙 | |
| **Raw Gold** | `1.0` 🪙 | |
| **Iron Block** | `4.5` 🪙 | 9x Iron Ingot |
| **Iron Ingot** | `0.5` 🪙 | 2 ingots = 1 Silver |
| **Raw Iron** | `0.5` 🪙 | |
| **Copper Block** | `2.25` 🪙 | 9x Copper Ingot |
| **Copper Ingot / Raw Copper**| `0.25` 🪙 | 4 ingots = 1 Silver |
| **Coal Block** | `1.8` 🪙 | |
| **Coal** | `0.2` 🪙 | 5 coal = 1 Silver |
| **Redstone Block** | `1.8` 🪙 | |
| **Redstone Dust** | `0.2` 🪙 | |
| **Lapis Block** | `1.8` 🪙 | |
| **Lapis Lazuli** | `0.2` 🪙 | |
| **Quartz Block** | `1.0` 🪙 | |
| **Nether Quartz** | `0.25` 🪙 | |
| **Budding Amethyst** | `15.0` 🪙 | Silk-touchable / rare |
| **Amethyst Block** | `2.0` 🪙 | |
| **Amethyst Shard** | `0.5` 🪙 | |
| **Prismarine Shard / Crystals**| `0.5` 🪙 | |

#### B. Artifacts & Boss Drops
| Item | Bank Buy Price (Silver) | Notes |
| :--- | :--- | :--- |
| **Mace** | `800.0` 🪙 | End-game weapon |
| **Elytra** | `600.0` 🪙 | Flight privilege |
| **Heavy Core** | `500.0` 🪙 | Used for Tier 5 upgrade |
| **Beacon** | `350.0` 🪙 | |
| **Nether Star** | `250.0` 🪙 | Wither boss drop |
| **Conduit** | `200.0` 🪙 | |
| **Heart of the Sea** | `150.0` 🪙 | |
| **Netherite Upgrade Template** | `150.0` 🪙 | |
| **Totem of Undying** | `100.0` 🪙 | Raid/Evoker reward |
| **Trident** | `100.0` 🪙 | Drowned drop |
| **Dragon Head** | `100.0` 🪙 | End ship trophy |
| **Enchanted Golden Apple** | `60.0` 🪙 | God Apple |
| **Shulker Box (All 17 Variants)**| `25.0` 🪙 | Base + 16 Dyed Colors |
| **Wither Skeleton Skull** | `20.0` 🪙 | |
| **Ominous Bottle** | `15.0` 🪙 | Raid trigger |
| **Ominous Trial Key** | `15.0` 🪙 | |
| **Trial Key** | `5.0` 🪙 | |
| **Breeze Rod** | `3.0` 🪙 | |
| **Shulker Shell** | `10.0` 🪙 | 2 shells = 1 box |
| **Golden Apple** | `4.0` 🪙 | |
| **Ghast Tear** | `5.0` 🪙 | |
| **Phantom Membrane** | `2.0` 🪙 | |
| **Slime Ball** | `1.0` 🪙 | |
| **Dragon Egg** | `0.0` 🪙 | Unsellable (trophy item) |

#### C. Bulk Agricultural & Building Blocks
* **Dirt, Stone, Cobblestone, Deepslate, Netherrack, Sand, Gravel, Basalt, Blackstone, Tuff**: `64 items = 1.0 Silver` (`0.015625 Silver/each`).
* **Logs & Stems (All Wood Types)**: `16 items = 1.0 Silver` (`0.0625 Silver/each`).
* **Planks**: `64 items = 1.0 Silver`.
* **Crops (Wheat, Potato, Carrot, Beetroot)**: `16 items = 1.0 Silver`.
* **Pumpkins & Melons**: `8 items = 1.0 Silver`.
* **Sugar Cane, Bamboo, Cactus, Kelp**: `32 items = 1.0 Silver`.
* **Nether Wart**: `8 items = 1.0 Silver`.
* **Mob Drops (Rotten Flesh, Bones, Gunpowder, String, Spider Eye)**: `16 items = 1.0 Silver`.
* **Arrows**: `32 items = 1.0 Silver`.
* **Cooked Meats**: `4 items = 1.0 Silver`.
* **Raw Meats**: `8 items = 1.0 Silver`.

### 4.2 Server Shop Purchase Prices (`/shop`)
| Item Purchased | Cost in Silver (`🪙`) | Batch Size |
| :--- | :--- | :--- |
| **Totem of Undying** | `250` 🪙 | 1x |
| **Enchanted Golden Apple** | `120` 🪙 | 1x |
| **Golden Apple** | `64` 🪙 | 8x |
| **Echo Shard** | `60` 🪙 | 4x |
| **Ominous Bottle** | `40` 🪙 | 1x |
| **Wind Charge** | `16` 🪙 | 16x |
| **Ender Pearl** | `16` 🪙 | 16x |
| **Firework Rocket (Flight 1)**| `12` 🪙 | 64x |
| **Cobweb** | `10` 🪙 | 16x |
| **Experience Bottle** | `40` 🪙 | 64x |
| **Shulker Box (Undyed)** | `60` 🪙 | 1x |

---

## 5. Spawners, Loot Caches & Gamble Mechanics

### 5.1 Tiered Mob Loot Spawners
Players can purchase portable Spawner Caches from `/shop`. Right-clicking opens an interactive GUI allowing players to select their mob loot type.

* **Purchase Costs**:
  * Tier 1: `100` 🪙
  * Tier 2: `250` 🪙
  * Tier 3: `500` 🪙
  * Tier 4: `750` 🪙
  * Tier 5: `1,000` 🪙
* **Drop Formula**:
  * Base Quantity: `(BaseRange) * Tier`
  * Rare Drop Chance: `Tier * 10%` (Tier 1 = 10%, Tier 2 = 20%, Tier 3 = 30%, Tier 4 = 40%, Tier 5 = 50%)
* **All 8 Supported Mobs & Exact Drop Tables** (`SpawnerLootTable.java`):
  * **Zombie**:
    * Base: Rotten Flesh `(4 + rand(5)) * Tier`
    * Rare: Iron Ingot (`1 * Tier`), Carrot (`1 * Tier`), Potato (`1 * Tier`), Zombie Head (`1x`).
  * **Skeleton**:
    * Base: Bones `(4 + rand(5)) * Tier`, Arrows `(4 + rand(5)) * Tier`
    * Rare: Bow (`1x`), Skeleton Skull (`1x`).
  * **Creeper**:
    * Base: Gunpowder `(4 + rand(5)) * Tier`
    * Rare: Creeper Head (`1x`), Random Music Disc (`1x` from 14 discs: 13, Cat, Blocks, Chirp, Far, Mall, Mellohi, Stal, Strad, Ward, 11, Wait, Otherside, Pigstep).
  * **Spider**:
    * Base: String `(4 + rand(5)) * Tier`, Spider Eye `(2 + rand(3)) * Tier`
    * Rare: Cobwebs (`1 * Tier`).
  * **Blaze**:
    * Base: Blaze Rods `(2 + rand(3)) * Tier`
    * Rare: Fire Charge (`1 * Tier`).
  * **Enderman**:
    * Base: Ender Pearl `(1 + rand(2)) * Tier`
    * Rare: Eye of Ender (`1 * Tier`).
  * **Slime**:
    * Base: Slimeball `(4 + rand(5)) * Tier`
    * Rare: Slime Block (`1 * Tier`).
  * **Witch**:
    * Base: Glowstone Dust `(2 + rand(3)) * Tier`, Redstone Dust `(2 + rand(3)) * Tier`, Gunpowder `(2 + rand(3)) * Tier`
    * Rare: Potion of Healing (`1x`).

### 5.2 Progressive Gamble Spawners
Gamble spawners simulate an animated roulette roll in an interactive chest GUI (`GambleScreenHandler.java`).

#### Pricing & Probability Distribution
* **Ticket Prices**:
  * **Overworld Crate**: `600` 🪙
  * **Nether Crate**: `800` 🪙
  * **Trial Crate**: `1,000` 🪙
  * **Ore Crate**: `1,200` 🪙
  * **End Crate**: `1,500` 🪙
* **Rarity Brackets** (Uniform roll out of `1000`):
  * **Common (65.0%)**: `roll >= 350`
  * **Rare (25.0%)**: `100 <= roll < 350`
  * **Epic (9.5%)**: `5 <= roll < 100`
  * **Legendary (0.5%)**: `roll < 5`

#### Complete Drop Table Breakdown
```
[Overworld Crate (600 Silver)]
  ├─► Common (65%): 10x Gold Block, 40x Diamond
  ├─► Rare (25%): 60x Diamond, 16x Gold Block, Random Overworld Armor Trim
  ├─► Epic (9.5%): 80x Diamond, 10x Golden Apple, 8x Ominous Bottle
  └─► Legendary (0.5%): 1x Enchanted Golden Apple

[Nether Crate (800 Silver)]
  ├─► Common (65%): 12x Netherite Scrap, 8x Gold Block
  ├─► Rare (25%): 20x Netherite Scrap, 16x Gold Block
  ├─► Epic (9.5%): 4x Netherite Ingot, Nether Armor Trims (Snout/Rib/Spire)
  └─► Legendary (0.5%): 9x Wither Skeleton Skull

[End Crate (1,500 Silver)]
  ├─► Common (65%): 12x Shulker Shell, 3x Echo Shard
  ├─► Rare (25%): 18x Shulker Shell, 5x Echo Shard
  ├─► Epic (9.5%): 2x Nether Star, 1x Random Shulker Box
  └─► Legendary (0.5%): 1x Elytra

[Ore Crate (1,200 Silver)]
  ├─► Common (65%): 40x Diamond, 10x Gold Block, 10x Emerald Block
  ├─► Rare (25%): 60x Diamond, 15x Gold Block, 15x Emerald Block
  ├─► Epic (9.5%): 10x Diamond Block, 8x Ancient Debris
  └─► Legendary (0.5%): 4x Netherite Ingot

[Trial Crate (1,000 Silver)]
  ├─► Common (65%): 16x Breeze Rod, 20x Iron Block, 4x Trial Key
  ├─► Rare (25%): 32x Breeze Rod, 30x Iron Block, 8x Trial Key, 2x Ominous Key
  ├─► Epic (9.5%): 1x Heavy Core
  └─► Legendary (0.5%): 1x Mace
```

---

## 6. Factions & Territory Hierarchy

### 6.1 Faction Configuration
* **Creation Fee**: `5,000` 🪙.
* **Maximum Members**: 20 members per faction.
* **Maximum Outposts**: 3 persistent teleport locations per faction (`/f outpost set <name>`, `/f outpost tp <name>`, `/f outpost del <name>`).
* **Treasury (`/f bank`)**: Shared faction account for pooling Silver.

### 6.2 The Faction Tax Mechanism
* **Tax Rate**: Configurable from `0%` to `5%` by the Faction King.
* **Kickback System**: When any faction member spends Silver (`removeSilver(uuid, amount, true)`), the system calculates `amount * (taxRate / 100.0)`.
* **Daily Faction Cap**: Up to `5,000` 🪙 Silver per faction per day is minted directly into the Faction King's personal account.
* **Inflationary Note**: This Silver is created from the server itself without being deducted from the member's wallet.

### 6.3 Rank Hierarchy & Status Buffs
Faction buffs are calculated every 40 ticks in `PlayerEventHandler.java`:
* 👑 **King (Faction Leader)**:
  * Permanent *Haste I*, *Fire Resistance*, *Hero of the Village*.
  * Inherits Crown buffs if wearing a Royal Crown.
* ⚡ **Commander**:
  * Permanent *Speed I*, *Haste I*, *Fire Resistance*, *Hero of the Village*.
  * Permissions: Invite, Kick, Promote to Knight, Set Outposts, Withdraw from Bank.
* 🛡️ **Knight**:
  * Permanent *Resistance I*.
  * Permissions: Access Outposts, Deposit to Bank.
* ⚓ **Recruit**:
  * Base rank (no passive buffs). Access to faction chat and outposts.

---

## 7. Daily Contracts & Quest System

Players access quests via `/quest` or GUI (`QuestScreenHandler.java`). Quests are assigned daily per player UUID using a calendar seed offset (`System.currentTimeMillis() / (1000 * 60 * 60 * 24) + offset`).

### 7.1 Complete Quest Templates & Reward Table (`QuestManager.java`)

#### A. Easy Contracts (Reward: `200` 🪙 Silver, 0 Items)
| Category | Quest Objective | Target Count | Target Entity/Block ID |
| :--- | :--- | :--- | :--- |
| **Mining** | Mine 32 Iron Ores | 32 | `iron_ore` |
| **Mining** | Mine 64 Coal Ores | 64 | `coal_ore` |
| **Mining** | Mine 32 Copper Ores | 32 | `copper_ore` |
| **Mining** | Mine 16 Gold Ores | 16 | `gold_ore` |
| **Mining** | Mine 64 Redstone Ores | 64 | `redstone_ore` |
| **Hunting** | Hunt 12 Skeletons | 12 | `skeleton` |
| **Hunting** | Hunt 10 Spiders | 10 | `spider` |
| **Hunting** | Hunt 15 Creepers | 15 | `creeper` |
| **Hunting** | Hunt 20 Zombies | 20 | `zombie` |

#### B. Medium Contracts (Reward: `500` 🪙 Silver + `1x Diamond`)
| Category | Quest Objective | Target Count | Target Entity/Block ID |
| :--- | :--- | :--- | :--- |
| **Hunting** | Hunt 10 Zombies | 10 | `zombie` *(Note: Easier than Easy quest!)* |
| **Hunting** | Hunt 8 Endermen | 8 | `enderman` |
| **Hunting** | Hunt 15 Blazes | 15 | `blaze` |
| **Hunting** | Hunt 12 Piglins | 12 | `piglin` |
| **Mining** | Mine 45 Gold Ores | 45 | `gold_ore` |
| **Mining** | Mine 24 Lapis Ores | 24 | `lapis_ore` |
| **Mining** | Mine 16 Nether Quartz Ores | 16 | `nether_quartz_ore` |

#### C. Hard Contracts (Reward: `1,000` 🪙 Silver + `4x Diamonds`)
| Category | Quest Objective | Target Count | Target Entity/Block ID |
| :--- | :--- | :--- | :--- |
| **Mining** | Mine 128 Diamond Ores | 128 | `diamond_ore` *(Extreme outlier!)* |
| **Mining** | Mine 20 Ancient Debris | 20 | `ancient_debris` |
| **Mining** | Mine 15 Emerald Ores | 15 | `emerald_ore` |
| **Hunting** | Hunt 4 Wither Skeletons | 4 | `wither_skeleton` |
| **Hunting** | Hunt 6 Ghasts | 6 | `ghast` |
| **Hunting** | Hunt 25 Drowned | 25 | `drowned` |

### 7.2 Event Interceptors
* **Mining Tracking**: Intercepted in `BlockMixin.java` -> `PlayerBlockBreakHook.handleBlockBreak(serverPlayer, state)`. Matches block type against active quest target.
* **Hunting Tracking**: Intercepted in `KingSMPMod.java` -> `ServerLivingEntityEvents.AFTER_DEATH`. Validates entity type against active quest target.

---

## 8. Virtual Storage, Market UX & Anti-Hoarding Rules

### 8.1 Remote Ender Chest (`/ec` / `/enderchest`)
* **Base Unlock**: Requires one-time payment of `32x Diamond`.
* **Double Chest Upgrade**: Requires one-time payment of `2x Netherite Ingot`. Expands capacity from 27 slots (3 rows) to 54 slots (6 rows).
* **Physical Block Hook**: Intercepting `UseBlockCallback` on `minecraft:ender_chest` automatically opens the 54-slot container if unlocked.

### 8.2 Global Market Sign Search System
* `ShopScreenHandler.java` supports search by creating an ephemeral virtual sign block.
* `ServerGamePacketListenerImplMixin.java` intercepts `handleSignUpdate` (`ServerboundSignUpdatePacket`).
* The typed text from the client is gathered into a query string, the original block state is immediately restored, and the shop is re-opened displaying filtered search results.

### 8.3 Dynamic Item Worth Tooltips
* `ItemStackMixin.java` intercepts `ItemStack.getTooltipLines()`.
* Automatically appends the item's individual and bulk Silver appraisal (`Worth: X Silver (Y each)` or `Worth: 1 Silver per Z items`) directly to item tooltips in real-time, except when viewing shop/gamble screen handlers.

### 8.4 Prohibited Relic Storage Rules
To prevent players from permanently hiding high-value game-changing relics in un-raidable private inventories, **The Royal Crown** and the **Dragon Egg** are strictly banned from storage:
* **Chests & Containers**: `SlotMixin.java` blocks `mayPlace()` if the container is not a player inventory.
* **Shulker Boxes**: `ShulkerBoxSlotMixin.java` blocks insertion.
* **Bundles**: `BundleItemMixin.java` intercepts `overrideStackedOnOther()` and `overrideOtherStackedOnMe()` to reject insertions in both directions.
* **Ender Chests**: `PlayerEventHandler.enforceEnderChestRules()` sweeps Ender Chest contents every 40 ticks, forcibly ejecting any Crown or Dragon Egg back into the player's main inventory or dropping it onto the ground with an ominous dragon wing sound.

---

## 9. PvP, Combat Tagging & Anti-Combat Log

### 9.1 Combat Tag Duration
* **Duration**: `15 seconds` (15,000 ms).
* **Trigger**: Any player-vs-player damage event (`ServerLivingEntityEvents.ALLOW_DAMAGE`). Tags both the attacker and the victim.
* **Restrictions While Tagged**:
  * Cannot use `/home` or `/home tp`.
  * Cannot use `/tpa` or `/tpaccept`.
  * Cannot use `/rtp`.
  * Status message displayed continuously on Action Bar: `⚔️ IN COMBAT: Xs remaining ⚔️`.

### 9.2 Combat Logging Penalty
If a tagged player disconnects (`ServerPlayConnectionEvents.DISCONNECT`):
1. **Inventory Dropped**: All items in inventory, armor, and offhand are violently dropped at their coordinates (`player.getInventory().dropAll()`).
2. **Dethroned**: If the player was a King, they are stripped of their title, and the server broadcasts:  
   `☠ <Player> was dethroned for combat logging!`.

---

## 10. Teleportation & Travel Systems

| Command | Warmup | Cooldown | Combat Blocked? | Cost / Restrictions |
| :--- | :--- | :--- | :--- | :--- |
| **`/home`** | 0s | 0s | Yes (15s tag) | 1 set home per player. Can share with specific UUIDs. |
| **`/tpa <player>`** | 0s | 60s | Yes (15s tag) | Target has 60s to accept. Sender must wait 60s between requests. |
| **`/tpauto`** | 0s | 0s | No | Toggles automatic acceptance of incoming TPA requests. |
| **`/rtp`** | 0s | 300s (5m) | Yes (15s tag) | Teleports within 5,000 blocks radius. Checks for water/lava/hazards. |
| **`/f outpost tp`**| 0s | 0s | Yes (15s tag) | Teleports to faction outpost. Must have Knight+ rank. |

---

## 11. Complete Command & Permission Index

### Player Commands
* `/king help` - Opens the interactive GUI guide.
* `/king status` - Shows active election phase, countdown, and active Kings.
* `/king leaderboard` - Displays total cumulative reign time of top rulers.
* `/king vote <player>` - Casts vote for active election candidate.
* `/king myvote` - Displays player's cast vote.
* `/king upgrade` - Upgrades Crown held in inventory to next tier.
* `/bal` or `/balance` - Shows virtual Silver balance.
* `/pay <player> <amount>` - Transfers Silver to another player.
* `/price` - Appraises the bank value of the item held in main hand.
* `/sell` - Opens the chest GUI to sell bulk items for Silver.
* `/shop` - Opens the Global Market GUI.
* `/shop sell <price>` - Lists held item on the peer-to-peer market.
* `/market cancel <id>` - Cancels an active market listing.
* `/bounty add <player> <amount>` - Places a Silver bounty on a player.
* `/bounty list` - Shows top active bounties.
* `/ec` or `/enderchest` - Opens remote Ender Chest (once unlocked).
* `/ec upgrade` - Upgrades Ender Chest to 54 slots (2 Netherite Ingots).
* `/home [set|tp|delete|share]` - Manages personal home coordinates.
* `/tpa <player>` | `/tpaccept` | `/tpadeny` - Teleport requests.
* `/rtp` - Random wilderness teleportation.
* `/faction [create|disband|invite|join|leave|kick|promote|demote|info|list|bank|outpost]` - Faction management.
* `/quest` - Opens the daily quest contract board.

### Operator / Admin Commands (Permission Level 2+)
* `/king crown <player>` - Instantly crowns a player.
* `/king dethrone <player>` - Strips a player of their crown.
* `/king election [start <ticks>|end]` - Manages election cycles.
* `/king upgrade <player> <level>` - Forces a crown to a specific level.
* `/king set <player> <amount>` - Sets a player's Silver balance.
* `/king add <player> <amount>` - Adds Silver to a player's balance.
* `/king remove <player> <amount>` - Deducts Silver from a player's balance.
* `/king bounty [set|remove] <player> [amount]` - Overrides player bounties.
* `/king unlockender <player> [large]` - Grants Ender Chest permissions.
* `/king quest [view|reset|complete] <player>` - Manages quest states.
* `/king market remove <id>` - Force-cancels an illegal market listing.
* `/king reign [set|clear] <player> [ticks]` - Edits reign leaderboard ticks.
* `/king combat clear <player>` - Clears a player's active combat tag.

---

## 12. In-Depth Balance Audit & Critical Vulnerabilities

### 🔴 Flaw 1: The "God-King" Problem (Combat Stagnation)
* **Diagnosis**: A Tier 5 King has 30 hearts (60 HP), Resistance II (40% damage reduction), and Regeneration II.
* **Mathematical Reality**:
  * A critical hit with a Sharpness V Netherite Axe deals `13.5` base damage. Against Prot IV Netherite armor + Resistance II, actual damage taken is **~1.5 to 2.0 Hearts**.
  * Regeneration II restores **1 Heart every 1.25 seconds**.
  * A Tier 5 King can literally stand completely still and survive a 3v1 ambush without swinging back.
* **Impact**: Normal players feel helpless. Factions stop attempting regicide because the math makes it impossible.

### 🔴 Flaw 2: The Level 5 Degradation Immunity Loophole
* **Diagnosis**: In `ServerPlayerEntityMixin.java`:
  ```java
  if (level < 5 && level > 1) {
      KingSMPMod.setCrownLevel(stack, level - 1);
  }
  ```
* **Mathematical Reality**:
  * Levels 2, 3, and 4 degrade by 1 on death.
  * **Level 5 does NOT degrade**. If a Level 5 King dies, their crown drops as Level 5!
* **Impact**: Once a team reaches Tier 5, the server enters an eternal monopoly. They risk nothing because even if they die, their teammate picks up a maxed Tier 5 Crown.

### 🔴 Flaw 3: The `tryCharge()` Inventory Eraser Bug
* **Diagnosis**: In `KingCommands.java`:
  ```java
  if (tryCharge(player, Items.DIAMOND, 32) &&
      tryCharge(player, Items.GOLD_BLOCK, 16) &&
      tryCharge(player, Items.OMINOUS_BOTTLE, 1)) {
      upgradeCrown(src, player, crownSlot, crown, 2);
  }
  ```
* **Mathematical Reality**: `tryCharge` removes items *as it checks them*. If a player has 32 Diamonds and 16 Gold Blocks, but no Ominous Bottle, Java evaluates `tryCharge(DIAMOND)` (takes 32 diamonds), evaluates `tryCharge(GOLD_BLOCK)` (takes 16 gold blocks), and then fails on `OMINOUS_BOTTLE`.
* **Impact**: The player loses all their diamonds and gold blocks with **zero compensation and no upgrade**.

### 🔴 Flaw 4: Inverted Gamble EV (Expected Value Paradox)
* **Diagnosis**:
  * End Gamble Ticket: `1,500` 🪙.
    * Common drop (65%): 12 Shulker Shells + 3 Echo Shards = `120 + 45 = 165 Silver` (89% loss).
    * Legendary drop (0.5%): 1x Elytra = `600 Silver` value (still a 60% net loss on the ticket price!).
  * Nether Gamble Ticket: `800` 🪙.
    * Common drop (65%): 12 Scrap + 8 Gold Blocks = `300 + 72 = 372 Silver`.
    * Legendary drop (0.5%): 9 Wither Skulls = `180 Silver`.
* **Impact**: The legendary prize in the Nether crate is worth **less than half the common prize**. Players quickly realize the gamble system is mathematically broken and abandon it.

### 🔴 Flaw 5: 0% Tax Hyper-Inflation
* **Diagnosis**: The bank `/sell` system injects millions of Silver into player balances from mob grinding and strip mining, but `/market` player trades have a 0% fee.
* **Impact**: After 2–3 weeks of an SMP season, base prices inflate to hundreds of thousands of Silver, locking out new and casual players.

### 🔴 Flaw 6: Panic Instant-Teleportation
* **Diagnosis**: `/home`, `/tpa`, and `/rtp` have zero warmup delay if the player is not currently combat-tagged.
* **Impact**: If a player spots an enemy stalking them from 20 blocks away, they type `/home` and vanish instantly before an arrow or hit can register the combat tag.

### 🔴 Flaw 7: "Money Out of Thin Air" Faction Tax Arbitrage
* **Diagnosis**: In `KingDataManager.java:272-297`, `removeSilver(uuid, amount, true)` triggers whenever a member spends Silver (e.g. buying crate tickets, market items, or paying players). The faction tax is calculated as:
  ```java
  int taxAmount = (int) Math.round(amount * (taxRate / 100.0));
  int allowedTax = Math.min(taxAmount, 5000 - currentDaily);
  addSilver(kingUuid, allowedTax);
  ```
* **Mathematical Reality**: This Silver is **not** deducted from the spender. The server generates brand-new Silver and credits it directly to the Faction King's balance (up to 5,000 Silver daily).
* **Exploitation Vector**: Two faction members can trade Silver back and forth or buy and sell dummy market items to instantly print 5,000 free Silver per faction every single day at zero net cost.

### 🔴 Flaw 8: Quest Contract Asymmetry & Difficulty Inversion
* **Diagnosis**: In `QuestManager.java`:
  * Easy Quests require hunting **20 Zombies** for 200 Silver.
  * Medium Quests require hunting only **10 Zombies** for 500 Silver + 1 Diamond.
  * Hard Quests require mining **128 Diamond Ores** for 1,000 Silver + 4 Diamonds, compared to hunting only **4 Wither Skeletons** in the same difficulty tier.
* **Impact**: 128 Diamond Ores requires hours of deep mining even with Fortune III, whereas 4 Wither Skeletons takes 3 minutes in a Nether Fortress. Players re-roll or ignore mining contracts entirely.

### 🔴 Flaw 9: Election Crowning Inconsistency
* **Diagnosis**: In `ElectionManager.java:68`, the election start message announces:
  `"A new election has begun! Only the person who is #1 gets the Crown."`
  However, in `concludeElection()` (line 128) and `KingSMPMod.MAX_KINGS`:
  `if (crownedCount >= KingSMPMod.MAX_KINGS) break;` (crownedCount up to 5).
* **Impact**: Players are misled to believe only a single monarch exists, causing vote concentration rather than electing a multi-member council.

---

## 13. Refinement Guidelines for Claude (Tuning Blueprint)

When refining the balance of this mod, Claude should follow these specific target parameters:

### 1. Combat & Crown Target Curves
* **Cap Resistance at I**: Never grant permanent Resistance II to any player under any circumstance.
* **Tone Down Strength & Speed**: Cap Crown buffs at **Strength II** and **Speed II** (Speed III breaks PvP combat networking and hit registration).
* **Linear Health Scaling**:
  * Tier 1: +2 Hearts (+4 HP)
  * Tier 2: +4 Hearts (+8 HP)
  * Tier 3: +6 Hearts (+12 HP)
  * Tier 4: +8 Hearts (+16 HP)
  * Tier 5: +10 Hearts (+20 HP) -> *Total Max HP: 20 Hearts (40 HP)*.
* **Conditional Climax Ability (The Boss Surge)**:
  * Instead of permanent Regen II / Res II, give Tier 5 an "Adrenaline Surge" triggered only when HP drops below 30% for 8 seconds (120s internal cooldown).
* **Tier 5 Degradation**: Force Level 5 to degrade to Level 4 on death (`if (level > 1) { setCrownLevel(stack, level - 1); }`).

### 2. Economy & Currency Sink Guidelines
* **Implement a 5% Market Fee**: Deduct 5% from all `/market` peer-to-peer sales.
* **Fix Faction Tax Source**: Faction taxes must be deducted from the member's transaction or from market proceeds, not minted from the void.
* **Fix Gamble EV**:
  * Aim for an average **Return-to-Player (RTP) of 70–80%**.
  * Make Legendary drops (0.5%) feel like monumental, 4x–8x ticket value events (e.g. End Crate Legendary = Elytra + Nether Star + 2x Shulker Boxes).
* **Harmonize Quest Contracts**:
  * Easy: 10 Zombies / 32 Coal / 16 Iron -> 150 Silver.
  * Medium: 25 Zombies / 12 Endermen / 24 Lapis -> 350 Silver + 1 Diamond.
  * Hard: 8 Wither Skeletons / 16 Diamond Ore / 6 Ancient Debris -> 750 Silver + 3 Diamonds.

### 3. Safety & QoL Implementations
* **Atomic Transaction Validator**: Write a pre-check loop that counts all required items in the inventory before shrinking any of them.
* **3-Second Teleport Warmup**: Implement a stationary channel for `/home`, `/tpa`, and `/rtp` that cancels on movement `>0.5` blocks or taking any damage.
* **Faction Buff Hierarchy**: Ensure Commanders inherit the Knight's Resistance I perk so higher ranks are strictly superior to lower ranks.
* **Synchronize Monarchy Strings**: Update election broadcast strings to clearly state that the top 5 candidates will form the High Council / Monarchy.
