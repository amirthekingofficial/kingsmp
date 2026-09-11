# KingSMP Feature Wishlist & Ideas

This document compiles potential feature additions, gameplay enhancements, and systems to expand the KingSMP experience across Factions, Economy, Elections, Politics, Utility, and Combat.

---

## 📑 Table of Contents
1. [👑 Politics & Monarchy (The King's Reign)](#-politics--monarchy-the-kings-reign)
2. [🛡️ Faction War & Outpost Raiding](#%EF%B8%8F-faction-war--outpost-raiding)
3. [💰 Economy & Trading Systems](#-economy--trading-systems)
4. [⚔️ Combat, PvP & Anti-Combat Log](#%EF%B8%8F-combat-pvp--anti-combat-log)
5. [🗳️ Election & Campaign Events](#%EF%B8%8F-election--campaign-events)
6. [🛠️ Utility, Remote Systems & QoL](#%EF%B8%8F-utility-remote-systems--qol)
7. [🎯 Bounty Board & Contracts](#-bounty-board--contracts)
8. [🗺️ Faction Quests & Biome Territory](#%EF%B8%8F-faction-quests--biome-territory)
9. [🤝 Diplomacy & Alliances](#-diplomacy--alliances)
10. [🏛️ The King's Vault (Heist Event)](#%EF%B8%8F-the-kings-vault-heist-event)

---

## 👑 Politics & Monarchy (The King's Reign)

### 1.1 Faction Treasury Taxes
* **Concept**: The King can set a tax rate (1% to 5%) on all player-to-player trades, market sales, and shop purchases.
* **Mechanic**: Taxed silver is funneled directly into the King's Faction Treasury.
* **Command**: `/king tax <percentage>`

### 1.2 Royal Decrees (Server-wide Buffs)
* **Concept**: The King can activate server-wide buffs once per real-life day using Faction Treasury funds.
* **Buff Examples**:
  * *Haste Decree*: Grants Haste I to all online players.
  * *Harvest Decree*: Double crop drops.
  * *Bounty Decree*: Mobs drop 1.5x Silver.

### 1.3 Regicide Rewards (King Bounties)
* **Concept**: Overthrowing or slaying the King rewards the slayer.
* **Mechanic**: The player who kills the King steals a percentage of the King's personal silver balance and gains a unique slayer tag.

---

## 🛡️ Faction War & Outpost Raiding

### 2.1 Outpost Control Points
* **Concept**: Faction outposts can be captured by rivals.
* **Mechanic**: Standing near a rival's outpost block for 5 contiguous minutes with no defenders nearby initiates a capture. Once captured, the teleport link `/faction outpost <name>` is severed or redirected to the capturing faction.

### 2.2 Faction Guards & Defenses
* **Concept**: Factions can fortify their outposts.
* **Mechanic**: Spend Faction Silver to purchase AI guards (custom Iron Golems or Skeleton Archers) that defend the outpost from rivals.

### 2.3 Territory Claims & Upkeep
* **Concept**: Factions can claim chunks of land.
* **Mechanic**: Claimed land blocks non-members from editing blocks. To maintain claims, the faction must pay a daily silver upkeep fee from the Faction Treasury.

---

## 💰 Economy & Trading Systems

### 3.1 Global Auction House (Bidding System)
* **Concept**: Upgrade the `/market` listing to support bids.
* **Mechanic**: Players set a starting price and an auction duration. Other players can bid, and the highest bidder wins the item when the timer expires.

### 3.2 Dynamic Commodity Shop
* **Concept**: A server shop for base resources (Gold, Iron, Wood, Coal) where prices fluctuate based on supply.
* **Mechanic**: If players sell massive amounts of Iron, its sell value drops. If players buy lots of Gold, its purchase cost increases.

### 3.3 Trading Caravans (World Event)
* **Concept**: A periodic server event spawning a wandering merchant at a random coordinate.
* **Mechanic**: The merchant buys rare goods for high prices or sells exclusive custom items. Factions must escort the caravan or raid rival players heading to it.

---

## ⚔️ Combat, PvP & Anti-Combat Log

### 4.1 Anti-Combat Log (Combat Tagging)
* **Concept**: Prevent players from logging out during battles.
* **Mechanic**: If a player leaves the server within 15 seconds of taking or dealing damage, an NPC clone remains in the world for 15 seconds. If the clone is killed, the player drops their inventory upon logging back in.

### 4.2 Duel System
* **Concept**: Safe, wager-based 1v1 duels.
* **Mechanic**: Players can challenge each other to a duel with `/duel challenge <player> [wager_silver]`. Winners take the wagered silver.

---

## 🗳️ Election & Campaign Events

### 5.1 Campaign Broadcast System
* **Concept**: Candidates can buy broadcast packages.
* **Mechanic**: Spend silver to announce campaign slogans automatically to the server every hour.
* **Command**: `/campaign broadcast "<message>"`

### 5.2 Gladiator Vote
* **Concept**: Candidates can fight for a vote multiplier.
* **Mechanic**: Candidates enter an arena event. The victor receives a +5% vote count multiplier in the upcoming election.

---

## 🛠️ Utility, Remote Systems & QoL

### 6.1 Portable Workstations
* **Concept**: Purchase remote access to crafting tables, anvils, or grindstones using Netherite.
* **Commands**: `/craft`, `/anvil`, `/grindstone`.

### 6.2 Faction Custom Outfits / Banners
* **Concept**: Cosmetics linked to factions.
* **Mechanic**: The King can design a custom banner pattern that automatically applies to shields worn by faction members.

---

## 🎯 Bounty Board & Contracts

### 7.1 Player-Placed Bounties
* **Concept**: Put hits on rival players using Silver.
* **Mechanic**: Anyone can list a player on the public board with `/bounty add <player> <amount>`. The first player to kill them collects the reward.
* **Command**: `/bounty list`

---

## 🗺️ Faction Quests & Biome Territory

### 8.1 Faction Challenges
* **Concept**: Faction-wide cooperative goals.
* **Mechanic**: Factions receive a random objective weekly (e.g., "Collect 5,000 Oak Logs" or "Slay 300 Creepers"). Success awards faction silver and a temporary XP multiplier.

### 8.2 Biome-Based Resource Harvesting
* **Concept**: Territories claimed in specific biomes yield unique bonuses.
* **Mechanic**: Desert claims passively generate sand/glass to the faction vault, while mountain claims increase ore yields for faction members.

---

## 🤝 Diplomacy & Alliances

### 9.1 Faction Standings
* **Concept**: Formal diplomatic relationships.
* **Mechanic**: Kings/Commanders can declare statuses towards other factions: *Ally* (cannot PvP each other, shared outposts), *Neutral* (standard gameplay), or *At War* (allows claim raiding).
* **Command**: `/faction ally <faction>` / `/faction war <faction>`

---

## 🏛️ The King's Vault (Heist Event)

### 10.1 Treasury Raiding
* **Concept**: Factions can raid the King's personal/royal vault.
* **Mechanic**: The King's faction gets a physical Vault block that holds a portion of the tax income. During a scheduled weekend event window, rival factions can attempt to crack it using specialized vault keys, instigating a massive defense event.
