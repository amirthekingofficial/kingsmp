# 👑 KingSMP Gameplay & Commands Guide

Welcome to **KingSMP**! This guide details all the server commands, core mechanics, and gameplay strategies, including how to claim the Crown, make money, run factions, and utilize custom items.

## 👑 Core Mechanics

### The Crown & Reign
The central objective of KingSMP is to claim, upgrade, and protect **The Royal Crown**.
- **Claiming the Crown**: The server regularly hosts elections. During an active election, players vote for a candidate. The winner receives the level 1 Crown and is crowned the King.
- **Usurping the Crown**: If you manage to steal a King's Crown (e.g. they die or drop it), and there is an open King slot, you instantly claim it and become the new King.
- **Crown Upgrades (`/upgrade`)**: The King can upgrade their Crown by spending specific materials. Higher tiers unlock powerful passive status effects and attribute boosts:
  - **Level 1**: Speed I, Strength I, Resistance I (+4 Hearts health bonus)
  - **Level 2**: Speed II, Strength I, Resistance I, Fire Resistance (+6 Hearts health bonus)
    - *Cost*: `32x Diamond, 16x Gold Block, 1x Ominous Bottle`
  - **Level 3**: Speed II, Strength II, Resistance I, Fire Resistance (+10 Hearts health bonus)
    - *Cost*: `4x Netherite Ingot, 64x Diamond, 1x Nether Star`
  - **Level 4**: Speed II, Strength II, Resistance II, Fire Resistance, Regeneration I (+14 Hearts health bonus)
    - *Cost*: `2x Netherite Block, 3x Nether Star, 8x Echo Shard`
  - **Level 5 (MAX)**: Speed III, Strength III, Resistance II, Fire Resistance, Regeneration II (+20 Hearts health bonus)
    - *Cost*: `1x Heavy Core, 1x Dragon Egg, 4x Netherite Block, 5x Nether Star`
- **Crown Degradation & Death**:
  - If a King dies, they are **dethroned** and the Crown drops by 1 level (unless it was already maxed at Level 5).

---

### Earning & Spending Money
The server economy runs on virtual **Silver** (`🪙`).

- **Appraising Items (`/price`)**: Hold any item in your main hand and type `/price` to see what the Bank will pay for it.
- **Selling to the Bank (`/sell`)**: Hold items and type `/sell` to convert them directly into Silver.
  - *High-value items*: Netherite blocks (900 Silver), Netherite ingots (100 Silver), Diamonds (2 Silver), Nether Stars (250 Silver).
  - *Bulk selling*: Common items (dirt, stone, wood) sell in stacks (e.g., 64 dirt = 1 Silver).
- **Barter Market (`/market sell <item> <amount>`)**: List items in your hand to trade with other players for specific items/amounts.
- **Global Shop (`/shop`)**: Spend your Silver on rare items (Elytra, Totems, Enchanted Golden Apples) or purchase spawners (Creeper, Skeleton, Blaze).
- **Auction House (`/auction`)**: Sell custom/enchanted items to other players for Silver, or browse current deals.
- **Bounties (`/bounty`)**: Put a Silver bounty on a player's head. The player who slays them claims the reward automatically.

---

### Factions & Ranks
Kings can establish factions, invite recruits, and build an army.
- **Creating Factions**: Run `/faction create <name>`. Costs **5,000 Silver**.
- **Faction Ranks & Buffs**:
  - 👑 **King**: Ruler of the faction. Grants permanent *Haste I*.
  - ⚡ **Commander**: Grants permanent *Speed I, Haste I*, and *Hero of the Village* effects.
  - 🛡️ **Knight**: Grants permanent *Resistance I* effects.
  - ⚓ **Recruit**: Base rank, no permanent buffs.

---

### Ender Chest Rules
Powerful objects like the **Dragon Egg** and the **Royal Crown** cannot be stored in your Ender Chest. If placed inside, they will violently escape back into your inventory or drop to the ground.
- **Unlock Remote Ender Chest (`/ec` or `/enderchest`)**: Access your Ender Chest remotely from anywhere in the world. Requires a one-time payment of `32 Diamonds`.
- **Ender Chest Expansion (`/ec upgrade`)**: Upgrade your Ender Chest to a double chest size (6 rows, 54 slots). Applies to remote access and physical Ender Chest blocks. Requires a one-time payment of `2x Netherite Ingots`.

---

### PvP Combat Restraints
To prevent players from fleeing fights, entering PvP combat places you **in combat** for **15 seconds**.
- **Tagged State**: Triggered when you hit another player or take damage from another player.
- **HUD Indicator**: While in combat, a real-time countdown timer (`⚔️ IN COMBAT: Xs remaining ⚔️`) will be displayed in red bold text directly above your hotbar.
- **Restrictions**: During combat, you **cannot**:
  - Teleport home (`/home`, `/home tp`)
  - Request or accept teleport requests (`/tpa`, `/tpaccept`)
  - Use economy commands (`/shop`, `/market`, `/sell`, `/auction`, `/bounty`)
- **Combat Logging Penalty**: Logging out while **in combat** results in:
  - **Dropping your entire inventory** (including armor and offhand items) on the ground at your logout location.
  - **Dethronement** if you are currently a King.
- **TPA Cooldown**: Teleporting via `/tpa` has a **60-second cooldown** to prevent teleport spam.

---

## 🎮 Player Commands Reference

### King & Election Commands
* `/vote <player>`: Vote for a player during an active election.
* `/myvote`: Check who you voted for in the current election.
* `/status`: View active Kings, reign durations, and election status.
* `/leaderboard`: See the longest-reigning Kings in server history.
* `/upgrade`: Upgrades the Royal Crown in your inventory if you meet the material costs.
* `/bal` or `/balance`: View your virtual Silver balance.

### Economy & Market Commands
* `/shop`: Opens the Global Market graphical interface (Buy Elytras, Spawners, custom drinks like Vodka, etc.).
* `/price`: Checks the appraisal price of the item held in your main hand.
* `/sell`: Sells the item in your main hand to the Bank for Silver.
* `/market sell <price_item> <amount>`: Lists your held item on the barter market for a custom exchange.
* `/auction view`: Opens the global auction screen to buy items from other players.
* `/auction sell <price>`: Lists your held item on the Global Auction House for a set amount of Silver.
* `/auction cancel <id>`: Reclaims an item you listed on the auction house.
* `/bounty add <player> <amount>`: Places a bounty on a target player using your Silver.
* `/bounty list`: Views all active bounties.

### Faction Commands
* `/faction create <name>`: Create a new faction (Costs 5000 Silver).
* `/faction invite <player>`: Invite a player to your faction.
* `/faction accept`: Accept a pending faction invite.
* `/faction promote <player> <rank>`: Promotes a member to COMMANDER, KNIGHT, or RECRUIT (King only).
* `/faction kick <player>`: Kicks a member (King and Commanders only).
* `/faction view`: Opens a visual GUI showing all members of your faction.
* `/faction leave`: Leave your current faction.
* `/faction disband`: Disbands the faction (King only).
* `/faction setoutpost <name>`: Sets a faction outpost (first one is free, subsequent ones cost 1000 Silver) (King & Commanders only).
* `/faction outpost <name>`: Teleports to a faction outpost (blocked in combat).
* `/faction deleteoutpost <name>`: Deletes a faction outpost (King & Commanders only).
* `/faction listoutposts`: Lists all active faction outposts.

### Home & Teleportation Commands
* `/home set`: Sets your home base at your current location.
* `/home` or `/home tp`: Teleports to your home location.
* `/home share <player>`: Shares home access with a player (or revokes it).
* `/home tp <player>`: Teleports to a player's home that they shared with you.
* `/home delete`: Deletes your current home location.
* `/tpa <player>`: Request to teleport to a player (60-second cooldown).
* `/tpaccept <player>`: Accept a pending teleport request.
* `/tpadeny <player>`: Reject a pending teleport request.
* `/tpauto`: Toggle automatic acceptance of teleport requests.

### Ender Chest Commands
* `/enderchest` or `/ec`: Accesses your remote Ender Chest (Costs 32 Diamonds to unlock initially).
* `/enderchest upgrade` or `/ec upgrade`: Expands your Ender Chest to a large chest size (54 slots) (Costs 2x Netherite Ingots).

---

## ⚙️ Admin Commands
* `/king crown <player>`: Instantly crown a player.
* `/king dethrone <player>`: Instantly remove a player's crown.
* `/king forceupgrade <player> <level>`: Instantly set a player's crown to a specific level (1-5).
* `/king election start [seconds]`: Manually start an election.
* `/king election end`: End the current election early.
* `/king reset`: Remove all kings and reset election states.
* `/king mint <player> <amount>`: Add Silver to a player's balance.
