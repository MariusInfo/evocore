# 📖 EvoCraft Network – Mod Ecosystem & Command Wiki

Official comprehensive documentation for the **EvoCraft** modular mod suite (Minecraft Forge 1.20.1).

---

## 📑 Table of Contents
1. [EvoCore (Core & Database Infrastructure)](#1-evocore)
2. [EvoEssentials (Commands, Moderation & Teleportation)](#2-evoessentials)
3. [EvoKits (Kit System & SQL Cooldowns)](#3-evokits)
4. [EvoChat (Moderation & Chat Channels)](#4-evochat)
5. [EvoVote (Voting & Player Rewards)](#5-evovote)
6. [EvoNPC (Leaderboards & Holographic NPCs)](#6-evonpc)
7. [EvoCombat (Anti-PvP Logging & Combat Tag)](#7-evocombat)

---

## 1. EvoCore
> **Role:** Core infrastructure backbone. Manages multi-database connections (SQLite / MySQL / MariaDB), player statistics (kills, deaths, playtime), internal economy API, transaction event logging, and client-server network packet synchronization.

### Commands:
| Command | Allowed Users | Permission / Level | Detailed Description |
| :--- | :--- | :--- | :--- |
| `/evocore migratedb` | Administrators | OP (Level 3) | **Automatic Database Migration:** Reads all local tables from the SQLite file (`evodata.db`) and migrates them directly into the remote SQL database (MySQL / MariaDB). Run this command after setting `database.type` to `MYSQL` in your config and restarting the server. |

---

## 2. EvoEssentials
> **Role:** Essential gameplay and staff management suite (economy commands, safe teleportation, moderation, vanishing, inventory inspection).

### 👥 Player Commands (Economy & Teleportation):
| Command | Permission | Detailed Description |
| :--- | :--- | :--- |
| `/money` or `/bani` | Everyone | Displays your current wallet balance formatted with currency (e.g., `1,500.00 $`). |
| `/money <player>` | Everyone | Checks the wallet balance of another player. |
| `/pay <player> <amount>` | Everyone | Transfers money directly from your balance to another online player. |
| `/rtp` | Everyone | **Random Teleport:** Safely finds a valid surface block within configured world bounds (automatically avoids lava, water, and voids) and teleports the player. |
| `/spawn` | Everyone | Teleports the player to the server's central spawn point. |
| `/tpa <player>` | Everyone | Sends a teleport request to another player. |
| `/tpahere <player>` | Everyone | Requests another player to teleport to your current location. |
| `/tpaccept` | Everyone | Accepts the latest incoming teleport request. |
| `/tpdeny` | Everyone | Rejects an incoming teleport request. |
| *Homes GUI* | `evocore:homes.3` ... `16` | Interactive GUI for setting, deleting, and teleporting to homes. Home limit tiers are controlled dynamically via LuckPerms permission nodes. |

### 🛡️ Staff & Admin Commands:
| Command | Permission | Detailed Description |
| :--- | :--- | :--- |
| `/eco give <players> <amount>` | OP (Level 2) | Adds money to one or more players' balances. |
| `/eco take <players> <amount>` | OP (Level 2) | Removes money from players' balances. |
| `/eco set <players> <amount>` | OP (Level 2) | Sets an exact balance amount for target players. |
| `/eco item <amount>` | OP (Level 2) | Spawns a physical cash banknote item with the specified value. |
| `/setspawn` | OP (Level 2) | Sets the global server spawn point to your current position and head rotation. |
| `/evowarp create <name>` | OP (Level 2) | Creates a public warp at your location. *(Requires holding an item in hand to represent the warp icon).* |
| `/evowarp delete <name>` | OP (Level 2) | Deletes an existing warp from the database. |
| `/fly` or `/fly <player>` | `evocore.fly` / OP | Toggles flight mode (supports synchronized duration countdown). |
| `/god` | `evocore.god` / OP | Toggles invulnerability (immune to damage, fall, mobs, and PvP). |
| `/invsee <player>` | `evocore.invsee` / OP | Opens and allows real-time viewing and editing of any player's inventory. |
| `/vanish` or `/v` | `evocore.vanish` / OP | Makes you completely invisible to other players and hides you from the tab list. |
| `/staffgm` | `evocore.staffgm` / OP | Quickly toggles your game mode between Survival and Spectator. |
| `/stafftp <player>` | `evocore.stafftp` / OP | Silently teleports directly to target player coordinates without alerts. |
| `/bc <message>` | `evocore.broadcast` / OP | Sends an official colored chat broadcast to everyone on the server. |
| `/tbc <title>` | `evocore.broadcast` / OP | Sends an animated on-screen title broadcast across all player screens. |
| `/warn <player> <reason>` | `evocore.warn` / OP | Logs a formal warning against a player in the SQL database. |
| `/warns <player>` | `evocore.warn` / OP | Lists all active warnings and reasons recorded for a player. |
| `/unwarn <player> <id>` | `evocore.warn` / OP | Removes a specific warning by its database ID. |
| `/tempban <player> <duration>` | `evocore.tempban` / OP | Temporarily bans a player from the server (e.g., `1d`, `12h`, `30m`). |
| `/eventlog recent [count]` | `evocore.eventlog` / OP | Displays recent administrative and transaction logs from the database. |
| `/eventlog player <player>` | `evocore.eventlog` / OP | Filters transaction and action logs for a specific player. |
| `/eventlog flush` | `evocore.eventlog` / OP | Immediately flushes any in-memory event logs to disk/database. |
| `/evo reload` | `evocore.admin` / OP | Reloads language files and server configurations. |

---

## 3. EvoKits
> **Role:** Kit management system with NBT item serialization, persistent SQL cooldowns, and automatic crate key rewards.

| Command | Permission | Detailed Description |
| :--- | :--- | :--- |
| `/kit <name>` | `kit.<name>` / OP | Claims the specified kit. Checks remaining cooldown; if available, deposits items into the player's inventory (or drops at feet if full) and grants linked crate keys. |
| `/kits` or `/kit` | Everyone | Lists all configured kits with their current status: permissions, readiness, or remaining cooldown time in hours, minutes, and seconds. |
| `/evokit create <name> <hours>` | OP (Level 2) | Instantly saves a new kit using items currently held in your inventory (slots 0–35) and sets the cooldown in hours. |
| `/evokit delete <name>` | OP (Level 2) | Deletes a kit definition from `evocraft_kits.nbt`. |
| `/evokit addkey <key> <kit>` | OP (Level 2) | Associates a virtual crate key with a kit (players receive the key upon claiming the kit). |

---

## 4. EvoChat
> **Role:** Chat moderation, anti-spam protections, and online-time-based muting.

| Command | Permission | Detailed Description |
| :--- | :--- | :--- |
| `/mutechat` | `evochat.mutechat` / OP | Temporarily mutes or unmutes the global chat for all players (useful during events or raid attacks). Staff with `evochat.mutechat.bypass` can still chat. |
| `/mute <player> <minutes> [reason]` | `evochat.mute` / OP | Mutes a player for a set number of minutes. **The mute timer only decreases while the player is actively online**, and remaining time persists in the SQL database across disconnects. |
| `/unmute <player>` | `evochat.mute` / OP | Removes an active mute from a player. |

---

## 5. EvoVote
> **Role:** Automatic integration with Minecraft server voting lists (Votifier) and player reward delivery.

| Command | Permission | Detailed Description |
| :--- | :--- | :--- |
| `/vote` | Everyone | Displays official voting links in chat with clickable buttons and reward details (`Vote Key`). |
| `/adminvote <player>` | Console / OP | Internal command executed upon receiving a vote from Votifier. Asynchronously awards 1 `Vote Key` in the database and broadcasts an animated sliding GUI notification to all players. |

---

## 6. EvoNPC
> **Role:** Visual leaderboard NPCs and 3D floating holograms reflecting top player skins and statistics from the database.

| Command | Permission | Detailed Description |
| :--- | :--- | :--- |
| `/topnpc spawn <category> <1-3>` | OP (Level 2) | Spawns a leaderboard NPC for Rank 1, 2, or 3 in the selected category (`money`, `hours`, `kills`, `deaths`, `claims`, `plots`). Automatically fetches and renders the player's skin and stat value. |
| `/topnpc remove` | OP (Level 2) | Discards the closest leaderboard NPC within a 3-block radius. |
| `/topnpc refresh` | OP (Level 2) | Forces an immediate database re-query to refresh all leaderboard names, stats, and skins in the world. |

---

## 7. EvoCombat
> **Role:** Combat tagging system to prevent combat logging during PvP encounters.

* **Automatic Combat Tag:** When a player attacks or is damaged by another player in PvP, they are placed in combat.
* **Anti-Combat Logging:** Disconnecting while tagged causes instant death and item drops.
* **On-Screen HUD:** Displays a countdown timer indicating the seconds remaining until the combat tag expires.
