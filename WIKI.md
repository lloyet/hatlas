# Atlas — Player Wiki

## Summary

| Section | Description |
|---|---|
| [Faction](#faction) | Create and manage a faction: levels, skill points, crystal, claims, allies |
| [Job](#job) | Choose a profession (after joining a faction), complete daily quests, earn faction XP |
| [Dungeon](#dungeon) | Raid wave-based dungeons, fight a boss, share loot, earn special rewards |
| [Combat & PvP](#combat--pvp) | 1.8-style PvP, anti-disconnect (combat log) rules |
| [Safe Zones](#safe-zones) | Protected hubs and `/spawn` behavior |
| [Basic Commands](#basic-commands) | Home, trade, teleport, and utility commands |

---

## Faction

### What is a Faction?

A faction is a persistent group of players that fight, progress, and earn rewards together. Every member's contributions (job quests, dungeon clears) flow into the faction's shared XP pool, which raises the faction's level and grants **Skill Points**. Skill Points are spent inside the Atlas Crystal GUI to grow your faction the way *you* want — claims, chests, protection, HP, or homes.

### Levels and XP

Factions start at **level 0**. XP required to reach the next level follows a compounding formula:

```
XP to reach level 1  = 100
XP to reach level N  = ceil(XP(N-1) × 1.09)
```

The maximum faction level is **99**.

XP is earned by completing job quests (via the daily quest system) or clearing dungeons. It is added to the faction pool automatically — no player action required.

Each level-up grants **Skill Points**. Levelling up no longer auto-applies claims, chests, protection, or HP — those are now individual purchases.

### Skill Points

The Skill Points shop is accessible inside the Atlas Crystal inventory (right-click on a faction crystal). Skills can be purchased independently:

| Skill | Effect | Repeatable? |
|---|---|---|
| **Claims** | Grants additional chunks you can manually claim | Yes |
| **Faction Chest** | Unlocks an additional virtual double-chest accessible by all members | Yes |
| **HP** | Increases the maximum HP of your faction's Atlas Crystals | Yes |
| **Home** | Adds a personal home slot to every member | Yes |
| **Protection** | Adds a stockpiled protection slot triggered when the Crystal is destroyed | Yes |

You decide your faction's growth path. There is no fixed upgrade tree — every faction grows skill by skill.

### Faction Chest

The faction chest is a virtual double-chest storage shared between all members. Available chests come from the **Faction Chest** skill — buy more slots with Skill Points to expand storage. Access them through the Atlas Crystal GUI.

If a level downgrade ever drops the chest count below the number you've used, the excess chests are destroyed and their contents drop on the ground at the crystal location.

### Crystal Protection

Protection is a Skill Point purchase that is **stockpiled** until your Crystal is attacked.

How it works:
1. Your Crystal is destroyed by an enemy.
2. The Crystal automatically respawns at full HP and becomes **invincible** for the duration of the longest stockpiled protection.
3. When the invincibility ends, a **regen window** of the same length starts (by default; admins can change this ratio). If the Crystal takes **no hit** during that window, the protection regenerates and is ready for the next attack. If it is hit, that protection is **lost for good**.
4. If you own multiple protections, the next defeat uses the **longest one still available**. Broken protections regenerate one at a time, **shortest first**.

**Example:** your faction owns a 1-hour and a 30-minute protection. The Crystal is destroyed: it respawns at full HP and is invincible for 1 hour. Then comes a 1-hour regen window: if nobody hits the Crystal, the 1-hour protection is back 2 hours after the defeat. If the Crystal is destroyed again in the meantime, the 30-minute protection takes over.

The level-based "protection on downgrade" mechanic is gone — all Crystal protection now comes from this stockpile.

### Claims

Claims protect chunks against enemy mining and building. They are no longer granted automatically by levelling up — you must spend Skill Points to earn claim slots, then claim chunks manually.

```
/faction claim     — claim the chunk you are standing in
/faction unclaim   — release the chunk you are standing in
/faction showclaim — render particles along the borders of your claimed chunks
```

**Adjacency rule:** new claims must touch an existing claim (chunk-side adjacency). Your first claim must be adjacent to your Atlas Crystal.

`/faction info` shows your current `[Free Claims / Total Claims]` count and remaining protection time.

### Crystal Home & Outpost Promotion

Creating an Atlas Crystal **no longer auto-sets your faction home**. Instead a chat prompt invites you to set it manually:

```
/faction sethome              — set the home of your main Crystal at your current location
/faction sethome <crystal>    — set the home of any owned Crystal at your location (Owner / Leader)
```

Once an outpost has been created, **any Crystal can be promoted** between *main* and *outpost*. This lets you migrate your headquarters to an outpost without losing it — useful when an outpost is better defended than your original main Crystal.

`/sethome` (the personal home command) is **blocked inside enemy claimed territory**.

### Ally System

Two factions can become allies. Allied factions:
- Do **not** deal damage to each other (friendly fire is disabled)
- Do **not** attack each other's dungeon entities
- Can optionally share faction messaging

To form an alliance, both faction owners or leaders must agree:

```
/faction ally <faction>       — send an ally request
/faction allyaccept <faction> — accept a pending request
/faction allydeny  <faction>  — deny a pending request
/faction unally <faction>     — remove an existing alliance
```

When a faction is **disbanded** by another faction destroying its last Crystal, a global broadcast announces the kill to every player on the server — credit goes to the attacker.

### Faction Commands

| Command | Description | Permission |
|---|---|---|
| `/faction` | Show the help menu | `atlas.faction` |
| `/faction create <name>` | Create a new faction | `atlas.faction.create` |
| `/faction disband` | Disband your faction (owner only) | `atlas.faction.disband` |
| `/faction rename <name>` | Rename your faction | `atlas.faction.rename` |
| `/faction description <text>` | Set the faction description | `atlas.faction.description` |
| `/faction color <color>` | Change the faction chat color | `atlas.faction.color` |
| `/faction invite <player>` | Invite a player to your faction | `atlas.faction.invite` |
| `/faction accept` | Accept a faction invitation | `atlas.faction.accept` |
| `/faction decline` | Decline a faction invitation | `atlas.faction.decline` |
| `/faction leave` | Leave your current faction | `atlas.faction.leave` |
| `/faction kick <player>` | Kick a member from the faction | `atlas.faction.kick` |
| `/faction promote <player>` | Promote a member to the next role | `atlas.faction.promote` |
| `/faction demote <player>` | Demote a member to the previous role | `atlas.faction.demote` |
| `/faction transfer <player>` | Transfer faction ownership | `atlas.faction.transfer` |
| `/faction info [faction]` | View faction info (claims, protection, HP) | `atlas.faction.info` |
| `/faction list` | List all factions | `atlas.faction.list` |
| `/faction members` | List your faction's members and roles | `atlas.faction.members` |
| `/faction home` | Teleport to the main Crystal's home | `atlas.faction.home` |
| `/faction sethome [crystal]` | Set the home of a Crystal at your location (Owner/Leader) | `atlas.faction.sethome` |
| `/faction claim` | Claim the chunk you stand on | `atlas.faction.claim` |
| `/faction unclaim` | Release the chunk you stand on | `atlas.faction.claim` |
| `/faction showclaim` | Render particles along your claim borders | `atlas.faction.claim` |
| `/faction ally <faction>` | Send an ally request | — |
| `/faction allyaccept <faction>` | Accept an ally request | — |
| `/faction allydeny <faction>` | Deny an ally request | — |
| `/faction unally <faction>` | Remove an alliance | — |
| `/faction msg <text>` | Send a message to online faction members only | `atlas.faction.msg` |

### Member Roles

| Role | Permissions |
|---|---|
| **Member** | Basic access, no management |
| **Moderator** | Invite, kick members |
| **Leader** | All Moderator actions + rename, color, description, sethome on any Crystal |
| **Owner** | Full control, including disband, ownership transfer, Crystal promotion |

---

## Job

### What is a Job?

Each player can hold one of four jobs. Your job determines which daily quests you receive and how you contribute to your faction's XP.

> **You must join a faction (via the faction NPC) before you can take a job.** Jobs require a faction-of-origin so that earned XP has somewhere to go.

Job progress (level + XP) is personal; faction XP earned from quest rewards is shared.

### Available Jobs

| Job | Tool | Focus |
|---|---|---|
| **Miner** | Iron Pickaxe | Mining ores and stone |
| **Lumberjack** | Iron Axe | Chopping trees |
| **Hunter** | Bow | Killing mobs |
| **Farmer** | Wheat Seeds | Harvesting crops |

Assign or change your job by interacting with the corresponding job NPC in the world.

> Changing your job resets your current job XP and level and starts a **24-hour cooldown** before you can change again.

### Daily Quests

Each day, three quests are generated for you based on your job. You may select and activate **up to 2** of the three offered quests per day. Once you have selected 2, the daily slot is locked until the next day.

Each quest contains **1 or 2 tasks** drawn randomly from your job's task pool, scaled by a random difficulty tier:

| Difficulty | Name | Description |
|---|---|---|
| 1 | Easy | Small amounts, short time |
| 2 | Normal | Moderate amounts |
| 3 | Hard | Larger amounts |
| 4 | Hardcore | High amounts, tight time |

Each task in `jobs.yml` carries a per-difficulty **`multiplier_exp`** field (`easy` / `normal` / `hard` / `hardcore`, default `1.0`) so server admins can tune the XP yield of every task individually per difficulty tier.

Progress is tracked automatically as you play. An action bar message shows your current progress per task in real time. When all tasks in a quest are complete, rewards are granted automatically.

### Faction XP Reward Formula

Completing a quest grants XP directly to your faction's pool:

```
Faction XP = baseReward × sumDifficulty × √(factionLevel + 1)
```

Where:
- `baseReward` = 10 (server default, configurable)
- `sumDifficulty` = sum of difficulty values across all tasks in the quest
- `factionLevel` = your faction's current level at the time of completion

Higher faction levels and harder quests multiply the reward significantly.

### Item Rewards

Each task can provide item rewards that scale with difficulty. Items are placed directly in your inventory; any overflow is dropped at your feet.

### Jokeyrini — The Special Quest NPC

**Jokeyrini** is a special NPC available separately from the job system. Once per day, Jokeyrini offers a unique quest with 2–3 tasks at **Hard** (difficulty 3) or **Hardcore** (difficulty 4) level. There is a **15% chance** the daily offer is a **Legendary Special Quest** — 3 tasks all at difficulty 5.

Upon completion, Jokeyrini rewards **Donjon Keys**, used to start a dungeon:

| Quest type | Keys rewarded |
|---|---|
| Hard (max difficulty 3) | 1 key |
| Hardcore (max difficulty 4) | 2 keys |
| Legendary Special | 3 keys |

Progress on Jokeyrini quests is tracked via the action bar with the `[Jokeyrini]` prefix.

### Job Commands

Interact directly with the job NPCs to access the job GUI. There are no chat commands for daily quest management — everything is done through the in-game interface.

---

## Dungeon

### What is a Dungeon?

Dungeons are pre-built structures placed in the world by an admin. Each dungeon cycles between **idle** and **active** states. When active, a player holding a **Donjon Key** can enter and start the dungeon. The dungeon then runs through a series of **waves** of enemies, ending with a **boss wave**.

### Dungeon Activation

Dungeons activate on a server-wide schedule. The **dungeon NPC at spawn** displays a live countdown to the next activation in its GUI, so you can plan ahead and farm Jokeyrini keys in time.

### Dungeon Types

| Type | Theme |
|---|---|
| **Trial** | Classic dungeon |
| **Desert** | Desert ruins |
| **Nether Castle** | Nether fortress |
| **Plains** | Open plains |
| **Sky** | Sky island |
| **Ocean** | Underwater ruins |

Each type has its own set of mobs, boss types, and loot tables.

### Level

When a dungeon becomes active, it is assigned a random level from **0 to 99**. Level determines:
- Number of waves (scales from the type's min to max wave count)
- Number of mobs per wave
- Enemy HP and attack damage multipliers
- Faction XP reward amount

Higher levels = more mobs, tougher enemies, and more XP.

### Rarity

Each dungeon activation is also assigned a **rarity**, drawn from a weighted pool:

| Rarity | Color | EXP Multiplier | Weight |
|---|---|---|---|
| Common | White | ×1.0 | 66% |
| Rare | Aqua | ×1.5 | 14% |
| Epic | Light Purple | ×2.5 | 10% |
| Legendary | Gold | ×5.0 | 5% |
| Mystic | Red | ×10.0 | 3% |
| Goddess | Yellow | ×20.0 | 2% |

Rarity multiplies the total EXP distributed at the end of the dungeon.

### Waves

A dungeon runs through a series of waves. Each wave must be fully cleared before the next one begins. After clearing a wave you have **5 seconds** before the next wave starts.

**Wave kill rules:**
- A mob killed without a player being responsible (fall damage, drowning, self-detonation) **respawns** instead of decrementing the wave counter.
- A mob killed by another mob (e.g. a skeleton's arrow taking down a zombie) **does** count toward wave progression.

The **final wave** is always a **Boss Wave**: fewer, far stronger enemies. A special sound and title announce the boss wave when it begins.

If all players leave the dungeon (no one is detected inside), the dungeon resets automatically — progress and enemy kills are lost.

### Dungeon Keys

| Key | Source | Behavior |
|---|---|---|
| **Donjon Key** | Jokeyrini quests | Starts an *active* dungeon at the dungeon's currently rolled level and rarity |
| **Sinister Donjon Key** | Special drops | Stamped at creation with a fixed **difficulty (50–99)** and **rarity (Epic+)**. Using it forces the dungeon to start at *exactly* the level and rarity printed on the key |
| **Ominous Trial Key** | Epic+ dungeon completions | Force-activates an idle dungeon at Legendary, Mystic, or Goddess rarity |

### Starting a Dungeon

1. Obtain a **Donjon Key** (Jokeyrini quests) or a **Sinister Donjon Key** (special drops).
2. Find an **active** dungeon (announced server-wide when one activates).
3. Enter the dungeon structure with the key in hand — right-clicking the trial spawner starts the dungeon.
4. Survive all waves and defeat the boss.

A Sinister Key bypasses the random level/rarity roll and starts the dungeon at the values stamped on the key.

### EXP Distribution

Total EXP is calculated as:

```
Total EXP = (baseExp + level/99 × (maxExp - baseExp)) × rarityMultiplier
```

At the end of the dungeon, EXP is distributed between all factions that dealt damage to the **boss**, proportional to how much damage each faction dealt:

```
Faction share = totalEXP × (faction's boss damage / total boss damage)
```

This means multiple factions can participate in the same dungeon and each receive a proportional share of the rewards. The faction that dealt the most boss damage is announced as the clearing faction.

### Dungeon Rewards

Beyond XP, dungeon completions can drop unique **siege rewards** that change how factions interact with each other's territory:

| Reward | Effect |
|---|---|
| **Raider's Pickaxe** | Lets a faction member break **5–20 blocks** inside an **enemy faction's claims**. Use count is printed on the item; the pickaxe is consumed when uses run out |
| **Creeper Egg** | Spawns a regular Creeper inside an enemy claim. **Three** creeper detonations within a **3-block radius** can shatter obsidian |
| **Charged Creeper Egg** | Spawns a Charged Creeper. **One** charged detonation breaks obsidian within a 3-block radius |
| **Teleportation Ward** | Quick-travel item to a specific dungeon. Persists correctly across server restarts; re-buyable from the **Smuggler NPC** at spawn |
| **Ominous Trial Key** | See *Dungeon Keys* — force-activates a high-rarity dungeon |

### Ally System in Dungeons

Allied factions fighting in the same dungeon do **not** deal damage to each other. This allows allied factions to cooperate inside a dungeon without risking friendly fire. Alliance status is checked at the time of each attack.

---

## Combat & PvP

### 1.8-Style PvP

The native 1.21.11 combat (sweep / attack-cooldown) is **disabled** in favor of a 1.8-style PvP plugin. There is no cooldown bar — every left-click registers a full hit.

### Anti-Disconnect (Combat Log)

When a player damages another player (melee or projectile), both attacker and victim are flagged as **in combat** for **20 seconds** (configurable in `combats.yml`).

- Disconnecting while flagged **kills the player** and drops their full inventory + armor at their last location.
- Each new hit refreshes the timer to the full duration.
- Surviving the timer without further damage clears the flag — you can quit safely.
- Active combat timers persist across server restarts (`combats-data.yml`); a clean restart by itself does **not** kill in-combat players.

Action-bar notifications announce when you enter combat and when the timer expires.

---

## Safe Zones

Safe Zones are admin-defined areas (e.g. spawn, the new **Rubis Ruins** zone teased for the next content update) that are protected from PvP and territorial actions. Players entering one are recorded in their visit history and can teleport back later.

```
/safezone tp <name>     — teleport to a safe zone you have already visited (op-bypassable countdown)
```

`/spawn` is a strict alias of `/safezone tp spawn`.

---

## Basic Commands

### /home and /sethome

Set a named personal home and teleport back to it at any time.

```
/sethome <name>     — save your current location as a home
/home               — teleport to your first (oldest) home
/home <name>        — teleport to a specific named home
```

- Teleportation has a **5-second countdown**. Moving cancels the teleport.
- There is a **30-second cooldown** between teleports.
- `/sethome` is **blocked inside enemy claimed territory**.
- The number of available homes is driven by the **Home** Skill Point (granted to every faction member when bought).

### /trade

Initiate a peer-to-peer item trade with another player.

```
/trade <player>     — send a trade request to a player
```

Both players are shown a GUI where they can place items in their respective slots. Both players must click **Accept** before a 3-second countdown commits the swap. Either player can cancel at any time before the countdown completes.

### /tpa

Request to teleport to another player.

```
/tpa <player>       — send a teleport request
/tpa accept         — accept an incoming request (or click [Accept] in chat)
/tpa deny           — deny an incoming request (or click [Deny] in chat)
```

- Requests expire after **30 seconds** if not answered.
- Accepted teleports have a **10-second countdown**. Taking damage cancels the teleport.
- There is a **30-second cooldown** between requests.

### /spawn

Strict alias of `/safezone tp spawn`.

```
/spawn
```

- Starts a **10-second countdown**. Moving or taking damage cancels it.
- Has a **30-second cooldown** (op-bypassed).
- A previous bug that allowed players to skip the cooldown has been fixed.

### /rtp

Randomly teleport to a safe location near the world spawn.

```
/rtp
```

- Starts a **5-second countdown**. Moving cancels it.
- Has a **30-second cooldown**.
- Maximum radius is **1024 blocks**.
- **Blocked in the Nether and the End** — use a portal instead.
- The destination is always a safe solid-ground location with air above.
