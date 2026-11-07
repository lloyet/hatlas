# CLAUDE.md

This file guides Claude Code (claude.ai/code) when working in this repository.

> **Working agreement for Claude**
> 1. **Read this file fully at the start of every session.** It is the source of truth for project goals, features and known issues.
> 2. **Every time you modify the project, append an entry to [`docs/claude-changelog.md`](docs/claude-changelog.md)** (date, branch, what changed, files touched) and update any section that is now out of date.
> 3. **When a request is vague or a design choice isn't settled, add it to [Open Questions](#open-questions)** and ask the user, instead of guessing. Move answered questions into the relevant feature section, marked `✔ answered <date>`.
> 4. For Paper API questions, use the `papermc-docs` MCP server (`.mcp.json`) or the 1.21.11 Javadocs at `https://jd.papermc.io/paper/1.21.11/`.
> 5. **When a feature is finished (its PR is opened), the player wiki is updated by the `wiki-writer` agent**, started in the background by the `wiki-on-feature` hook (§2). Review its edits, commit them as `docs(wiki): …` on the feature branch and push. Never skip it for a PR that changes `src/main/`.
> 6. **A feature is only finished when its in-game suites pass.** Before `gh pr create`, the `ingame-gate` hook (§2) denies the PR until the `ingame-tester` agent has written a PASS verdict for the change. When denied, run that agent in the foreground with the features it names, fix any FAIL, commit (incl. new scenarios), then retry. `WAIVED` only when the user explicitly says to skip.

---

## 1. Project Overview

`play.mc.atlas` ("Atlas") is a **PaperMC 1.21.11 plugin (Java 21)** that turns a survival server into a **faction-war progression game**. Group `org.minecraft`, artifact `atlas`, version `1.0-SNAPSHOT`, root package `org.minecraft.atlas`.

### Gameplay goals
- **Factions are the core.** Every progression system feeds into a faction's shared XP pool. The faction levels up (0 → 99), earns **Skill Points**, and spends them in the **Atlas Crystal** GUI (HP, claims, chests, protections, homes, vault, outpost).
- **Territory is defended by Atlas Crystals** (ender crystals with custom HP). Destroying an enemy's last crystal disbands that faction.
- **Rubies are the economy.** Ruby gems are mined in Ore Zones, kept in a ruby-only faction vault, used to pay at Shops and on the Auction House, and dropped when a crystal's protection breaks.
- **PvE progression** goes through Jobs (daily quests), Jokeyrini (hard quests that give Donjon Keys) and Donjons (wave dungeons with a boss that give big faction XP and siege items).
- **PvP/raiding** goes through siege items from donjons (Raider's Pickaxe, creeper eggs, TNT), the combat-log penalty, faction golems and a locator bar that shows faction members only.

Player-facing docs: `WIKI.md` (EN) and `WIKI-FR.md` (FR). Some values there are out of date; see [Open Questions](#open-questions).

---

## 2. Build, Run, Tooling

```bash
./gradlew build        # compile + package the plugin JAR (build/libs)
./gradlew assemble     # build without tests
./gradlew compileJava  # fast compile check (use after every change)
./gradlew runServer    # local Paper 1.21.11 test server in ./run (2 GB heap)
```

- **No automated tests.** Check changes with `compileJava`, then manually on `runServer` (or the `paper-ingame-test` skill with offline bots).
- Gradle plugins: `java-library`, `xyz.jpenilla.run-paper 3.0.2`. Repos: Maven Central, PaperMC, CodeMC.
- Dependencies (both `compileOnly`): `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT` and `com.github.retrooper:packetevents-spigot:2.14.0`. **packetevents must be installed on the server as its own plugin.** It is declared as a required `server` dependency (`load: BEFORE`, `join-classpath: true`) in `paper-plugin.yml`.
- Descriptor: **`src/main/resources/paper-plugin.yml`** (Paper plugin, not `plugin.yml`), `load: POSTWORLD`, `api-version: '1.21'`, `loader: org.minecraft.atlas.AtlasLoader`. All permission nodes are declared there.
- Database drivers: **SQLite** uses the `sqlite-jdbc` that Paper ships on the server classpath (no dependency). **PostgreSQL**'s driver (`org.postgresql:postgresql:42.7.7`) is downloaded at startup by `AtlasLoader` (Paper `MavenLibraryResolver`) **only when `storage.type: postgresql`**.
- **Local PostgreSQL:** `docker compose -f docker/postgres/docker-compose.yml up -d --wait` (password in the git-ignored `docker/postgres/.env`, see `.env.example`). Usage and test recipe: `docs/storage.md` §4.
- `run/` (test server and world) is git-ignored. **No world or map save is committed.**
- MCP: `.mcp.json` declares `papermc-docs` → `https://gitmcp.io/PaperMC/docs` (GitMCP view of the PaperMC docs repo) and `vitaminmcp` (`cmd /c npx -y vitaminmcp@3.2.2`, pinned; bots + server observation for in-game tests).

### In-game test server (used by the `paper-ingame-test` skill)
- `TEST_SERVER_DIR=C:\Users\lloyet\Documents\Projects\servers\minecraft-paper1.21.11` (outside the repo, not in git, not Docker). `PLUGIN_NAME=atlas`.
- Control script (Windows, PowerShell 5.1): `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-server.ps1 start|stop|restart|deploy|status|console "<cmd>"`. `deploy` = gradle build → replace `plugins/atlas-*.jar` → restart. Stop goes through RCON so `onDisable` saves data; the script never kills the process. Requires env `MC_RCON_PASSWORD`.
- **Never `/reload`.** Always restart.
- Server plugins: atlas, packetevents 2.14.0, VitaminMCP (`read-only: false`), OldCombatMechanics, spark, BlueMap.
- Bots: offline names `Tester1`, `Tester2`… Atlas gotchas for scenarios: most features need a faction first (`/f create` or `/f join <bot> <faction>` from console); the AFK kick (`afk.timeout_seconds`) is raised on the test server; disconnecting within `combat_log.timeout_seconds` of PvP kills the bot; BlueMap re-renders world edits (CPU).
- This server also holds a real world and plugin data: only act on it when the user has asked for an in-game test.

### Wiki automation (Open Question 12)
- **Skill** `.claude/skills/wiki-writer/SKILL.md`: how to write `WIKI.md` (EN) + `WIKI-FR.md` (FR) for players. Sources: code, shipped `src/main/resources/*.yml`, `paper-plugin.yml`. Both files kept in sync, no internals, admin commands left out.
- **Agent** `.claude/agents/wiki-writer.md`: preloads the skill, may only edit the two WIKI files, read-only Bash (`git diff/log/show`, `gh pr view`), never commits.
- **Hook** `.claude/settings.json` → PostToolUse on `Bash(gh pr create*)` runs `.claude/hooks/wiki-on-feature.mjs` (Node). If a segment of the command really is `gh pr create`, its stdout has the new PR URL, and the diff against the PR's `--base` (default `main`) touches `src/main/`, it injects context asking the main session to launch `wiki-writer` in the background. Otherwise it does nothing.
- `.gitignore` now versions `.claude/settings.json`, `agents/`, `skills/` and `hooks/` (so `paper-ingame-test` is versioned too); `settings.local.json` stays personal.

### In-game test gate (Open Question 20)
- **Suites:** `src/test/ingame/<feature>/<case>.json` (step arrays for VitaminMCP `bot_run_scenario`), `*-before-restart.json` / `*-after-restart.json` pairs for persistence. Format, conventions and pitfalls: `paper-ingame-test` skill, section "Suites de scénarios du projet Atlas".
- **Map:** `src/test/ingame/features.yml` (feature → globs; `shared` globs run all suites). Selection is per feature (✔ Open Question 17). `node .claude/hooks/affected-features.mjs --base <branch>` prints the affected features, `uncovered` src/main files and `missingSuites`.
- **Agent** `.claude/agents/ingame-tester.md`: writes missing scenarios, deploys to the test server (§2), protects real data (backup/restore, or only undoes bot changes when a real player is online), runs the suites, writes the verdict `.claude/state/ingame/<sha>.json` (git-ignored).
- **Hook** `.claude/settings.json` → PreToolUse on `Bash(gh pr create*)` runs `.claude/hooks/ingame-gate.mjs`: no feature touched → allowed; otherwise needs a PASS/WAIVED verdict covering every affected feature, for HEAD or for an ancestor of HEAD with identical `src/main/`, and no uncommitted `src/main/` change. Else the call is denied with the exact agent prompt to run.

---

## 3. Architecture

### 3.1 Entry point: `Atlas.java`
`Atlas extends JavaPlugin`, singleton `Atlas.instance`. Its `onEnable()` runs these steps in order:
1. Registers `LocatorBarListener` with the packetevents API.
2. Loads the tuning YAML files (see §5) into `public static YamlConfiguration xxxConfig` fields (`loadTuning`), copies new `config.yml` defaults (e.g. `storage`), and calls the managers' `loadConfig(...)` and `init()` methods (keys, item registries, recipes).
3. **Storage** (`storage/`, see [`docs/storage.md`](docs/storage.md)): `Storage.start()` opens the backend chosen in `config.yml > storage` (sqlite | postgresql), imports the `*-data.yml` files once into a fresh database, and loads every domain. If this fails, the plugin disables itself (nothing is written).
4. Registers one snapshot supplier per domain, then calls each manager's `restore(...)` in dependency order (factions → crystals → … → shops), then `Storage.markReady()` (writes allowed from here on, autosave started).
5. Registers 18 listeners (incl. `AfkManager`, `CombatLogListener`, `ResourcePackManager`, which are listeners themselves) and calls `ResourcePackManager.sendToAll()`.
6. Starts the schedulers: Golem, Crystal, Donjon, Quest expiry, ItemClear, Afk, CombatLog, ClaimBorder, NpcLook, OreZone, Shop.
7. Registers the Brigadier commands through `LifecycleEvents.COMMANDS`.

`onDisable()` sets `CombatLogManager.setShuttingDown(true)` first, then `Storage.shutdown()` (waits for pending async writes, then saves every domain synchronously).

### 3.2 Conventions
- **Static managers** hold in-memory state with static methods (`FactionManager.getFaction(name)`). There is no DI and no instances.
- **Config vs data split:** `xxx.yml` holds tuning and is shipped in resources (`saveResource` if missing). Runtime state goes through the **storage adapter**: managers keep their in-memory state and expose `snapshot()` (immutable records from `storage/model`) and `restore(data)`. To persist after a mutation, call `Storage.save(DataDomain.X, …)` (async, one transaction). **Never write `*-data.yml` files directly**, and always save `FACTIONS` and `CRYSTALS` together. Autosave runs every `storage.autosave_minutes`.
- **Items in storage** are serialized with `ItemBytes` (Paper `serializeAsBytes`, version-safe), on the main thread only.
- **Brigadier:** each command class has a static `build()` returning `LiteralCommandNode<CommandSourceStack>`. Helpers `error()/success()/info()` return red/green/gold `Component`s.
- **Adventure API only** (`Component`, `NamedTextColor`). Never use legacy `ChatColor`.
- **PDC** (`NamespacedKey`) tags custom entities and items. Keys are lazily created static fields on the owning manager.
- **Custom items** use data components (`ITEM_MODEL` = `hatlas:<id>`, `ITEM_NAME`, `LORE`, `EQUIPPABLE`) on a vanilla base material. Identity is checked through the `ITEM_MODEL` key (e.g. `RubyItem.isRubyGem`).
- **NPCs** are vanilla entities with `setAI(false)`, invulnerable and persistent, tagged in PDC. They persist with the world and are not saved to YAML (except shop NPCs). `NpcLookHelper` turns Smugglers and Explorers toward the nearest player.
- **GUIs** implement `AtlasGui extends InventoryHolder`. `GuiListener` routes click, close and drag to the holder. `GuiNavigator` keeps a per-player back stack (`push`/`back`/`pop`). The last slot is always the "Back" barrier (`finishGui()`). `GuiUtil` has the layout, format and pane helpers. Paper `Dialog` is used for text input (crystal rename).
- **Titles:** use `TitleUtil` (word-wraps to title and subtitle, never embeds `\n`).
- Use `ConcurrentHashMap` for state touched from async events (chat, AFK, combat).

---

## 4. Features

### 4.1 Factions (`faction/`, `FactionCommand`, `FactionListener`, `ChatListener`)
- **Model `Faction`:** name, `owner` (UUID, stored separately from `members`), members, `roles` (`FactionRole` = MEMBER < BANKER < MODERATOR < LEADER; there is **no OWNER enum value**), color (`NamedTextColor`), description, level, exp, skillPoints, allies, `outpostUnlocked`, purchased home/vault tier indices, vault size + contents, downgrade fields (legacy, unused).
- **Levels:** `FactionLevelManager.MAX_LEVEL = 99`. XP to level 1 = 100, then each level costs `ceil(prev × 1.09)`. Overflow carries over. Each level gives `skill_points_per_level` SP (default 1). XP sources are quest completion and donjon boss-damage share.
- **Permissions by role:** spend the vault (take gems out of `CrystalVaultGui`, buy in a job shop or on the auction house) = owner or BANKER+ (`FactionManager.canSpendVault`; any member may deposit; ✔ answered 2026-11-07, Open Question 6); invite = MODERATOR+; kick = MODERATOR may kick BANKER/MEMBER, LEADER may kick MODERATOR/BANKER/MEMBER, owner may kick anyone; rename/description/color = LEADER+; promote/demote/transfer/disband = owner only (transfer turns the old owner into LEADER); claim, sethome, outpost, ally, sell on auction = owner or LEADER. A BANKER has no right other than spending the vault.
- **Creating a faction** (`/f create <name>`) spawns the **main crystal** at the player's position, claims that chunk, names it `main` and sets its home there. It is refused inside an existing claim, a donjon chunk or a safe zone.
- **Claims** (`FactionClaimManager`, runtime key `world:cx:cz`): a new claim must be side-adjacent to the faction's territory and needs a crystal with free `claimCapacity` (`findCrystalForClaim` picks the nearest one). Claims belong to a crystal and are persisted per crystal. `/f showclaim true|false` draws GLOW_SQUID_INK borders within 3 chunks (`FactionClaimBorderRenderer`).
- **Territory protection** (`FactionListener`): outsiders cannot break, place or interact in a claim. Exceptions: TNT placement, creeper spawn eggs, flint & steel or fire charge on TNT, and the **Raider's Pickaxe** (break only in enemy claims, consumes uses; anvils and smithing are blocked and it takes no durability damage). Entering or leaving a territory shows a title.
- **Allies:** requests are in-memory only (`pendingAllyRequests`). `areAllied()` requires both sides to list each other and treats the same faction as allied. Allies cannot hurt each other in donjons.
- **Faction chat formatting** (`ChatListener`): ops show in dark red, faction tag in the faction color. `/f msg` sends to online members only.
- **Leaving or losing faction membership** removes the player's job, removes secondary homes (main home is kept) and refreshes the locator bar.
- **Vault** (`FactionVault`, `CrystalVaultGui`): a **ruby-gem-only** shared inventory per faction. Default size from `vault.default_size`, can be expanded with the Vault skill. When a crystal protection breaks, `max(ceil(total × protection_break_drop_percent), protection_break_drop_min)` gems drop. All gems drop when the faction is disbanded through crystal loss. `/f balance` shows the gem count.
- **Admin/debug:** `/f level set`, `/f exp set|add`, `/f skillpoints set` (`atlas.faction.debug`), `/f disband <faction>` (`atlas.faction.admin.disband`), `/f join <player> <faction>` (`atlas.faction.admin.join`).

### 4.2 Atlas Crystal: core, skills, chests, outpost (`crystal/`, `Crystal*Gui`, `FactionListener`, `CrystalListener`)
- `AtlasCrystal` wraps an `EnderCrystal` with its own HP (`BASE_MAX_HP = 50`). State is split between entity PDC (hp, max_hp, immunity, bonuses, protections) and `factions-data.yml` → `crystals.<entityUUID>` (faction, name, hp, home, outpost flag, claimed chunks per world as chunk-key longs, chest sizes and contents, purchased and broken protections). Crystals in unloaded chunks stay as **stubs** (`isLoaded()==false`) and get reattached on `EntitiesLoadEvent`.
- **Nametag:** a TextDisplay showing `name [Faction]` / `LvL.x` / `hp/max ♥` / `IMMUNITY - [ready/total]` (aqua while immune, yellow while reloading).
- **Damage:** only enemy-faction players can damage a crystal, at most 1 hit/s per attacker. Non-entity damage is cancelled. Members are alerted. Regen is `regen_per_second + regenBonus` per second after `regen_timeout_seconds` without being hit.
- **When HP reaches 0:**
  - If a protection is available, the **longest** one is consumed: HP is refilled, the crystal becomes immune for that duration, a small explosion plays and vault rubies drop. Broken protections regenerate **one at a time, shortest first**. Each one's watch window is `crystal.protection_watch_multiplier × duration` after it broke (default 2: immunity + a regen window as long as the protection). If the crystal was hit during that window, the protection is **lost forever**.
  - If no protection is left, a big explosion plays and `destroyCrystal()` runs: the crystal's claims are removed, its chests drop, the faction loses `spentSkillPoints / skill_points_per_level` levels (an outpost also refunds the outpost cost) and its exp resets to 0. If the main crystal died, the outpost becomes main. If it was the last crystal, all vault gems drop and the faction is disbanded with a global broadcast.
- **Skills** (`CrystalSkillListGui` → `CrystalSkillConfirmGui`, tiers from `factions.yml > skills`):
  | Skill | Scope | Effect |
  |---|---|---|
  | `hp` | per crystal | +max HP, +regen/s |
  | `claims` | per crystal | +claim capacity |
  | `chest` | per crystal | new virtual chest (size 9/18/27/54) |
  | `protection` | per crystal | stockpiled immunity (minutes). Each duration can be bought once |
  | `homes` | faction | +1 personal home slot for every member (one-shot per tier) |
  | `vault` | faction | vault size upgrade (one-shot per tier) |
  | `outpost` | faction | unlocks a 2nd crystal through `/f outpost` (max 2 crystals) |
  A skill with no tiers in the YAML is hidden from the GUI.
- **CrystalMainGui** (right-click your own crystal) has: crystal list, faction info (click = promote this outpost to main, shift-click = rename dialog), color, chests, upgrades, vault, quests.
- `/crystal list`, `/crystal rename <old> <new>`. `/f home [crystal]` uses a countdown (`HomeTeleportManager`, `factions.yml > faction_home`). `/f sethome <crystal>` works only inside your own claim.

### 4.3 Jobs & daily quests (`job/`, `quest/`, `JobListener`, `Job*Gui`, `NpcJobSwitchGui`)
- `Job` enum: **MINER, LUMBERJACK, HUNTER, FARMER, ALCHEMIST** (playable) plus **JOKEYRINI** (pseudo-job, used only for Jokeyrini task templates and its NPC).
- You must be in a faction to get a job. You get it by right-clicking a job NPC (villager tagged `job_npc_type`, spawned with `/job npc spawn <job>`). Switching jobs resets your job data, and after a reset there is a **24 h cooldown** before you can pick a new one.
- **Job XP:** +1 per relevant action. Level-up needs `(level+1) × 64`.
  - MINER: ores, stone variants and glowstone. LUMBERJACK: logs and stems. FARMER: crops (`harvest_crop`), sugar cane and kelp, sweet berries, glow berries. HUNTER: kills (`kill_entity`). ALCHEMIST: only `brew_potion` (brewing-stand pickup) and `craft_item` (✔ decided 2026-11-07, Open Question 7; its old kill tasks and 3 never-progressing `break_block` gather tasks were replaced by brew/craft tasks).
- **Daily quests** (`QuestManager`): every `daily_reset_hours` you are offered 3 quests, each with 1–2 tasks at random difficulty 1–4. You can select **2**. Selecting a new day's offer clears your active quests.
  - Task amount = `BASE[d] + jobLevel × PER_LEVEL[d]` (Hunter ×0.9). Time limit is 15/30/60/120/180 min for difficulty 1–5, summed across the quest's tasks. Item rewards are scaled (Hunter ×1.1).
  - Faction XP = `exp_base_reward × Σ(difficulty × expMultiplier) × √(factionLevel+1)`.
  - `jobs.yml` has 85 job tasks (16 per job) with action types `break_block`, `kill_entity`, `harvest_crop`, `craft_item`, `brew_potion`, plus the `jokeyrini.tasks` pool.
- `/quest` opens `JobMainGui`. `/job level` shows progress. Admin: `/job set|remove|level add/set/remove|npc spawn`.

### 4.4 Quests with NPC: Jokeyrini (`JokeyriniManager`, `JokeyriniQuestGui`)
- A Wandering Trader NPC (`/job npc spawn jokeyrini`, PDC `jokeyrini_npc`). Requires a faction.
- One offer per `jokeyrini.daily_reset_hours`: 2–3 tasks at difficulty 3–4, or with `special_quest_chance` (0.15) a **Legendary** quest of 3 tasks at difficulty 5. Progress shows in the action bar with a `[Jokeyrini]` prefix and works whatever your job is.
- Reward: **Donjon Trial Keys**. Max difficulty 3 gives 1, 4 gives 2, 5 gives 3.

### 4.5 Donjons (`donjon/`, `DonjonCommand`, `DonjonListener`, `DonjonListGui`)
- **Instance `Donjon`:** id, name, type (key in `donjons.yml > types`), world, level 0–99, `DonjonRarity`, `DonjonStatus` (**IDLE / ACTIVE only**), protected chunks (chunk-key longs), wave spawn points (id → loc), vault block (the trigger), teleport spawn, nametag TextDisplay, waves, boss damage per faction.
- **Admin setup:** `/donjon create <type> <name>` claims the current chunk. Then use `claim/unclaim/unclaimall`, `set spawn`, `set vault` (look at the block), `wavespawn add/list/delete/clear`, `set difficulty|rarity`, `doctor <id>` (checks vault, teleport spawn and wave spawns), `activate|deactivate|reset|tp|delete|list|info`, `give <player> key|ominouskey [lvl] [rarity] [n]|creeper_egg|powered_creeper_egg|raider_diamond_pickaxe`, `npc summon smuggler`.
- **Schedule:** every `activation_interval_hours` a random idle donjon is activated (up to `max_active_donjons`) with a weighted level (`difficulty` ranges) and rarity (`rarity` weights). An active donjon times out after `idle_timeout_hours`, with a warning 1 h before.
- **Starting a run:** right-click the **vault block**.
  - On an ACTIVE donjon, a Donjon Key (Trial or Ominous, PDC `donjon_key_marker`) starts it. An Ominous key overrides the level and rarity.
  - On an IDLE donjon, only an **Ominous Trial Key** works. It force-activates the donjon with the level and rarity stored on the key.
- **Waves:** the number of waves and mobs per wave scale with level. HP and attack multipliers scale with level, and the boss wave gets extra boss multipliers. Mobs spawn round-robin across spawn points. Special mob tokens are `BABY_ZOMBIE` and `CHICKEN_JOCKEY`. After a wave there is a 5 s pause. When 5 or fewer mobs are left they glow.
  - Only kills by a player, or by a different mob, count. Environmental deaths respawn the mob.
  - Slime and magma splits and zombie→drowned conversions keep being tracked.
  - If no player is inside, the run resets (checked every 2 s).
- **Completion:** total XP = `(min_exp + level/99 × (max_exp−min_exp)) × rarity multiplier`, shared by **boss damage per faction**. The top damager gets the broadcast. Drops at the anchor:
  - creeper eggs and TNT (`1 + level/99 × (rarity+1)`)
  - rare chance of a powered creeper egg (0.5%–3%)
  - wither skulls for EPIC+
  - a chance of an Ominous key for EPIC+
  - boss loot from `boss_drops.<rarity>` (`BossDropConfig`, which can include a powered egg or a Raider pickaxe level)
- **Rarities:** COMMON ×1, RARE ×1.5, EPIC ×2.5, LEGENDARY ×5, MYSTIC ×10, GODDESS ×20.
- **Types in `donjons.yml`:** trial, desert, nether_castle, plain, sky, ocean.
- **Protection in donjon chunks:** non-admins cannot break, place, empty buckets or use interactive blocks. Explosions are filtered. Allied factions don't damage each other. Vanilla drops and XP from donjon mobs are cleared.
- **Locator / navigation:**
  - The **Smuggler** NPC (Illusioner, PDC `smuggler_npc`) opens `DonjonListGui` ("Smuggler's Chart"), which shows a countdown to the next activation and teleports you to donjons you have visited, with a cooldown of `smuggler_teleport_cooldown_seconds`.
  - Entering a donjon chunk records a visit and shows a title.
- **Siege items:**
  - `RaiderPickaxe`: Raider I/II/III gives 5/10/20 breaks in enemy claims.
  - `ElectricalCreeperManager`: the Powered Creeper Egg spawns a charged creeper. 3 charged explosions break 1 obsidian block (hit map is in memory only).
- **The 7 `.nbt` files in `resources/structures/` are storage only**: saved arena/donjon builds kept so they can be imported onto a server (e.g. with `/place template` or a structure block). No Java code loads them, and none should be added unless requested. (✔ answered 2026-11-05)

### 4.6 Ore Zones (`orezone/`, `OreZoneCommand`)
- Cuboid regions refilled every `interval_reset_hours` with a weighted roll per block over `OreType` (diamond, gold, emerald, iron, copper, coal, **stone**, **ruby**). Ruby is placed through `RubyBlockManager`.
- Admin: `/orezone pos1|pos2` (the block you look at, up to 100 blocks), `add <name>`, `remove`, `reset`, `list`. New zones copy `default_ore_weights`. Weights per zone are saved in `orezones-data.yml`.
- Status: compiles, but has **not been verified in game**. The current uncommitted change adds `STONE` and rebalances the weights (stone 75%).

### 4.7 Safe Zones (`safezone/`, `SafeZoneCommand`, `SpawnCommand`, `SafeZoneListener`, `SafeZoneGui`)
- Named sets of chunks with an optional spawn point and description.
- **Inside a safe zone:** players take no damage, players inside cannot hit players outside, blocks cannot be broken or interacted with, and explosions skip safe-zone blocks. Factions cannot be created, claimed or outposted there.
- Entering a zone records a visit. The **Explorer** NPC (Illusioner on a Camel, `/safezone npc summon`) opens `SafeZoneGui`, which teleports you to zones you have visited.
- `/safezone tp <name>` and `/spawn` (= `/safezone tp spawn`) use a countdown that is cancelled if you move, followed by a cooldown. Ops bypass both. `/spawn <zone>` works if you have visited the zone (or are admin). `/spawn set` writes the legacy `SpawnManager` spawn to config.yml.

### 4.8 Auction House (`auction/`, `AuctionCommand`, `Auction*Gui`)
- **Selling:** `/auction sell <price> [description]` lists the whole main-hand stack. You must be the owner or a LEADER, and you can have at most `max_listings_per_player` listings. **Ruby items cannot be listed.**
- **Buying:** payment goes from the **buyer's faction vault to the seller's faction vault** (the faction recorded on the listing). Overflow drops as gems. Only the owner or a BANKER+ can buy.
- Listings from disbanded factions are hidden from the market but can still be taken back through `/auction sales`.
- The Auctioneer NPC (librarian villager, `/auction npc spawn`) opens `AuctionMainGui`, which leads to market and sales lists (paged, 45 per page) and `AuctionConfirmGui` (BUY/REMOVE).

### 4.9 Job Shops (`shop/`, `ShopCommand`, `Shop*Gui`), newest feature on branch `feat/add-shop-manager`
- Each shop is a villager NPC tied to a job and has two weighted pools: **common** (any faction member with a job) and **rare** (members whose job matches the shop). Prices are **ruby gems taken from the buyer's faction vault**, like the auction house (✔ decided 2026-11-07, Open Question 6); the buyer needs access to the tier **and** must be the owner or a BANKER+.
- Every `reset_interval_hours` (168 h) up to `items_per_shop.<tier>` (max 28) items are drawn. Shops (job, NPC, pools), the rotation and the last reset time are stored in the **SHOPS** storage domain (✔ decided 2026-11-06).
- `shops.yml` keeps only tuning and `job_defaults.<JOB>` (templates copied into a new shop by `/shop create`; entries are `material/amount/potion/enchantments` or a serialized `item:`). A shop still hand-written under `shops.yml > shops` is **imported once** at startup, then that section is emptied.
- Admin: `/shop create <name> <job>`, `delete`, `list`, `additem <name> <common|rare> <weight> <price>`, `reset [name]`. Shop NPCs are immune to all damage and transformations.

### 4.10 Ruby (new item) & custom blocks (`Item/RubyItem`, `block/RubyBlockManager`, `BlockListener`, `RubyRestrictionListener`)
- Items (namespace `hatlas`):
  - `ruby` (EMERALD base)
  - `ruby_block` (EMERALD_BLOCK base)
  - `ruby_ore` and `deepslate_ruby_ore` (emerald-ore bases)
  - Recipes: 9 rubies ↔ 1 block.
- **Placed blocks** use the note-block trick: a NOTE_BLOCK with `instrument=pling`, note 0/1/2 → ruby_block / ruby_ore / deepslate_ruby_ore, overridden in `resourcepack/assets/minecraft/blockstates/note_block.json`. Locations are tracked in `blocks-data.yml`. `BlockListener` blocks note play, redstone, physics and pistons, re-applies the state on chunk load and on neighbor updates, and handles pick-block and step sounds.
  - Ores need an iron+ pickaxe and drop a gem.
  - Ruby blocks and ores can only be placed **in your own faction's claim**.
- `RubyRestrictionListener` (allowlist):
  - Any ruby item is allowed in the player inventory, 2×2 crafting grid and creative menu.
  - Gems and blocks are allowed in a crafting table.
  - Only gems are allowed in the faction vault.
  - Everything else (chests, shulkers, hoppers, crystal chests…) rejects all ruby items.
- `AmethystItem`: a netherite-based gear set re-skinned as amethyst (sword, tools, spear/trident, armor via equipment asset `hatlas:amethyst`, rod, horse armor), crafted from amethyst shards. Stats are the base item's **+`config.yml > amethyst.stat_bonus_percent`** (default 15 %): attack damage (total, fist included), armor, toughness, knockback resistance, correct-tool mining speed (huge speeds such as the sword's instant-mine rule are kept, else the item cannot be saved) and durability; attack speed and default mining speed unchanged. No ruby-style restrictions (✔ answered 2026-11-07, Open Question 8).
- `/item give <player> <id> [amount]` (`atlas.item.admin`).

### 4.11 Trade (`trade/`, `TradeCommand`, `TradeListener`, `TradeGui`)
- Right-clicking a player sends a trade request (clickable accept/decline, expires after 60 s). `/trade accept|decline [player]`.
- The GUI has a left half for your offer and a right half for theirs. Changing items resets both accepts. When both accept, a 3 s countdown (shown on the XP bar) runs and then the items swap. Quitting, dying or closing cancels the trade and returns the items.

### 4.12 Teleports
- **`/rtp`** (`RandomTeleportManager`): countdown, cooldown, radius around world spawn, safe solid ground. Blocked in the Nether and End.
- **`/tpa <player>`, `/tpa accept|deny`** (`TeleportAtManager`): requests expire after 30 s, then countdown, then cooldown.
- **`/sethome [name]`, `/home [name]`, `/delhome <name>`** (`HomeManager`): reserved `main` home plus secondary homes up to `1 + faction homes-skill bonus`. Cannot be set in another faction's claim.
- **`DeathTeleportCooldownManager`:** an escalating teleport ban after death (base × multiplier while inside the escalation window) for /home, /f home and /tpa. Ops bypass it.

### 4.13 Combat (`combat/`, `CombatLogListener`)
- Any PvP hit (melee or projectile) marks both players as in combat for `combat_log.timeout_seconds`. Quitting while marked drops your inventory and armor, kills you and broadcasts it. Timers survive a restart (`combats-data.yml`) and are not applied during shutdown.
- 1.8-style PvP comes from an **external plugin** (per WIKI), not from Atlas.

### 4.14 Golems (`golem/`, `GolemListener`)
- Building the T-pattern with a carved pumpkin on top of a body block creates a faction-tagged Iron Golem with stats from `GolemType`:
  | Body block | Stats / special effect |
  |---|---|
  | Iron | base stats |
  | Soul Sand | Slowness |
  | Amethyst | 0 damage, Glowing |
  | Gold | high damage, low HP |
  | Emerald | heals nearby faction members (Regen II) |
  | Crying Obsidian | 200 HP, slow, 5% chance to send the target to the Nether |
- Golems attack non-faction players within range and alert the faction (throttled). Right-click with the body material heals 10%. A golem killed by a player drops 0–5 of its material.

### 4.15 Locator bar (`LocatorBarListener`, packetevents)
- Intercepts outgoing WAYPOINT packets so a player only sees locator-bar markers of **their own faction**. `refresh(players)` must be called whenever faction membership changes (already done in `FactionManager`).

### 4.16 Utilities
- `TagManager` / `/tag create|delete|add|edit|list`: multi-line TextDisplay holograms (`tags-data.yml`).
- `AfkManager`: kicks after `afk.timeout_seconds` without activity.
- `ItemClearManager`: clears dropped items every 20 min with warnings.
- `TabListManager`: tab names colored by faction, admins in dark red.
- `ResourcePackManager`: pushes the `hatlas` pack on join (url, sha1, required, prompt in config.yml).

---

## 5. Configuration & data files (`plugins/atlas/`)

| Config (shipped) | Content |
|---|---|
| `config.yml` | `storage` (backend), `amethyst`, `tpa`, `random_teleport`, `afk`, `death_teleport_cooldown`, `resourcepack` (legacy `spawn.*` is only read once, for the import) |
| `factions.yml` | `faction_home`, `skill_points_per_level`, `vault`, `skills.{homes,hp,claims,chest,protection,outpost,vault}`, `crystal.*` |
| `jobs.yml` | `quests.{daily_reset_hours,exp_base_reward,tasks[]}`, `jokeyrini.{daily_reset_hours,special_quest_chance,tasks[]}` |
| `donjons.yml` | activation/timeout/max active, smuggler cooldown, `difficulty[]`, `rarity`, `ominous_key`, `types.<type>` (mobs, bosses, waves, multipliers, exp, `boss_drops.<rarity>`) |
| `homes.yml` | `home_teleport` |
| `safezones.yml` | `safezone` teleport delay/cooldown |
| `combats.yml` | `combat_log.timeout_seconds` |
| `auctions.yml` | `max_listings_per_player` |
| `orezones.yml` | `interval_reset_hours`, `default_ore_weights` |
| `shops.yml` | `reset_interval_hours`, `items_per_shop`, `job_defaults` (+ `shops`: import-only, emptied after import) |

**Runtime data** goes through the storage adapter (`config.yml > storage.type`, default **sqlite** → `plugins/atlas/atlas.db`). Full audit, schema and operating notes: [`docs/storage.md`](docs/storage.md). 13 domains: FACTIONS, CRYSTALS, JOBS (incl. Jokeyrini), DONJONS, HOMES, SAFEZONES, COMBAT, BLOCKS, AUCTIONS, OREZONES, SHOPS, TAGS, SPAWN. Schema files: `src/main/resources/db/V<n>__*.sql`, applied by `SchemaMigrator`.
- YAML is no longer a backend: `storage.type: yaml` is read as `sqlite` with a warning. The backend is chosen once (no SQLite ↔ PostgreSQL migration). `/atlas storage export` (`atlas.storage.admin`) writes a full backup to a portable SQLite file in `plugins/atlas/backups/` (✔ answered 2026-11-07, Open Question 19).
- On the first start with a database, the `*-data.yml` files are imported and renamed `*.yml.migrated` (backup).
- Saves happen right after important mutations, then every `autosave_minutes` (5) for all domains, and at shutdown. **A crash loses at most the last autosave interval** for data without an immediate save (job XP, quest progress, custom blocks, combat timers).

---

## 6. Resource pack (`resourcepack/`, namespace `hatlas`)
- `pack.mcmeta`: `min_format = max_format = 75` (1.21.11).
- `assets/hatlas/items/*.json` → `models/item|block/*.json` → `textures/item|block/*.png`. Armor uses `equipment/amethyst.json` plus `textures/entity/equipment/{humanoid,humanoid_leggings,horse_body}/amethyst.png`.
- `assets/minecraft/blockstates/note_block.json` overrides pling notes 0–2.
- **Gotcha:** block textures must be under `textures/block/` (the vanilla block atlas does not include `item/`).
- The pack is zipped and hosted outside the repo. `config.yml > resourcepack.url/sha1` are placeholders.

---

## 7. Known issues / technical debt (found during analysis, 2026-11-05)
1. ~~`jobs.yml` uses `multiplier_exp:` but the code read `exp_multiplier`~~ — **fixed 2026-11-05**: `QuestManager.expMultiplierSection()` reads `multiplier_exp` (legacy `exp_multiplier` still accepted) for job and Jokeyrini tasks.
2. ~~`TagCommand` `delete`/`edit` saved tags to `config.yml`~~ — **fixed 2026-11-05**: all `/tag` subcommands now save to `tags-data.yml`.
3. `ElectricalCreeperManager.OBSIDIAN_HITS_REQUIRED = 3`, but a listener comment says "4 hits". The WIKI also contradicts itself on creeper eggs.
4. ~~Faction max level 99 in code, 100 in the WIKI and `/f level set` help~~ — **fixed 2026-11-07**: 99 is the max (Open Question 4); help text and both WIKIs say 99.
5. ~~`factions.yml` `crystal.immunity_base_seconds` / `immunity_multiplier` loaded but unused~~ — **fixed 2026-11-07** (Open Question 5): removed; the watch-window factor is now `crystal.protection_watch_multiplier` (default 2, min 1). The `Faction` downgrade fields are still unused.
6. The `config.yml` comment says "/air tp" for random teleport. The WIKI values (tpa cooldown 30 s, 5 s countdowns) differ from the configs (tpa 60 s, 10 s countdowns).
7. The `structures/*.nbt` files are unused by the code **by design** (see §4.5).
8. Typo `maxActiveDonjonons` in `DonjonManager`.
9. ~~`FactionClaimManager.saveClaims/loadClaims` are legacy~~ — **removed 2026-11-06**: claims are persisted per crystal (CRYSTALS domain, `crystal_claims`).
10. ~~**Orphan crystals keep claims**~~ — **fixed 2026-11-06**: `FactionManager.persist()` saves factions + crystals right after create/delete/disband; `loadCrystalData` drops crystals of unknown factions (WARN log + file rewrite); `restore()` removes orphan crystal entities when their chunk loads.
11. ~~**Crystals in chunks already loaded at enable are never restored**~~ — **fixed 2026-11-07**: `FactionListener.onEntitiesLoad` only saw chunks whose entities loaded after the listener was registered, so a force-loaded chunk's crystal stayed a stub (and an orphan entity stayed in the world). `Atlas.onEnable` now calls `AtlasCrystalManager.restoreLoadedEntities()` after registering listeners; the listener and the scan share `restoreIfNeeded()`.

---

## 8. Milestones

### M1 — Regression tests: local in-game gate + CI unit tests (status: **in progress**, redesigned 2026-11-07)

**Decisions (Open Questions 13–16, 20):** no MCP server and no Minecraft server in CI (token and CI-time cost).
1. **Local, in game, before every feature PR** — done on `feat/ingame-test-agent`: JSON suites per feature, `features.yml` selection, `ingame-tester` agent, `ingame-gate` hook (§2). Runs on the Windows test server until the dedicated test world lands (Open Questions 10/15), then on that world.
2. **CI on every PR to `main`** — planned: a GitHub Actions workflow running `./gradlew build` + **unit tests** (JUnit 5, MockBukkit where Bukkit types are needed) for pure logic: faction XP curve and level-ups, quest amounts / time limits / rewards, donjon XP and rarity rolls, ore-zone weighted roll, storage round-trip (YAML ↔ SQLite on a temp folder). Required status check before merge.

**Coverage target:** per feature of §4, one nominal + one refusal/edge + one persistence scenario, plus E2E flows ("create faction → job quest → faction XP → crystal skill", "mine ruby in an ore zone → buy in a shop", "run donjon → raid with the Raider's Pickaxe"). Current suites: faction, storage, orezone (+ shop and job on their PR branches).

---

## Open Questions

> Claude: add a question here whenever a request is unclear. Ask the user, and record the answer.

1. ~~Exp multiplier key~~ — ✔ answered 2026-11-05: user asked to fix; code now reads `multiplier_exp`.
2. ~~Structures~~ — ✔ answered 2026-11-05: archives of arena/donjon builds for manual import; not loaded by code.
3. ~~**Donjon types**~~ — ✔ answered 2026-11-07: all 6 types in `donjons.yml` are live. There is no `COOLDOWN` status: it was only written in the old CLAUDE.md (commit c9d9685, `DonjonStatus — IDLE, ACTIVE, COOLDOWN`) and never existed in code. `DonjonStatus` stays IDLE / ACTIVE.
4. ~~**Faction max level**~~ — ✔ answered 2026-11-07: **99** (known issue §7.4 fixed).
5. ~~**Crystal immunity escalation**~~ — ✔ answered 2026-11-07: the dead `immunity_base_seconds` / `immunity_multiplier` keys are removed; the protection watch window (hard-coded `2 × duration` before) is configurable as `crystal.protection_watch_multiplier`, default 2 = previous behaviour.
6. ~~**Shop payment**~~ — ✔ answered 2026-11-07: shops are paid from the **faction vault** like the auction house (no difference intended). New role **BANKER** (MEMBER < BANKER < MODERATOR < LEADER) that may only spend the vault's rubies; spending the vault (withdraw, shop, auction buy) = owner or BANKER+ (see §4.1).
7. ~~**ALCHEMIST job**~~ — ✔ answered 2026-11-07: tracks **only brewing and crafting**. Kills no longer give Alchemist XP; the 7 kill/gather tasks in `jobs.yml` were replaced by 7 brew/craft tasks (still 16 per job).
8. ~~**Amethyst gear**~~ — ✔ answered 2026-11-07: custom stats, by default **15 % above netherite** (configurable `amethyst.stat_bonus_percent`); **no** ruby-style restrictions. See §4.10.
9. ~~**"Rubis Ruins" safe zone**~~ — ✔ answered 2026-11-07: **Ruby Ruins** is the name of the current update (ruby, amethyst, ore zones, auction house, shops), not a safe zone. WIKI EN/FR fixed (banner at the top, wrong safe-zone mention removed).
10. ~~**Map/world**~~ — ✔ answered 2026-11-07: the user will add an **empty world save** plus **schematics of the spawn city and the donjons** to the repo (for saves/tests); see also Open Question 15.
11. ~~**Persistence:** autosave?~~ — ✔ answered 2026-11-06: storage adapter with async saves after mutations + autosave every `storage.autosave_minutes` (5) + sync save at shutdown.
12. ~~**WIKI maintenance**~~ — ✔ answered 2026-11-07: a dedicated **wiki-writer agent + skill**, started by a **hook** each time a feature is finished, writes player-facing docs (features, commands, what players can do) in `WIKI.md` / `WIKI-FR.md`. Built on its own branch (`feat/wiki-agent`).
13. ~~**M1 — who drives the tests in CI?**~~ — ✔ answered 2026-11-07: nobody; no MCP in CI (see M1).
14. ~~**M1 — VitaminMCP in CI**~~ — ✔ answered 2026-11-07: not needed, no MCP in CI.
15. ~~**M1 — test world**~~ — ✔ answered 2026-11-07: a dedicated test world, copied into the repo by the user in the next PR.
16. ~~**M1 — CI host**~~ — ✔ answered 2026-11-07: no Minecraft server in CI, so no runner choice needed.
17. ~~**M1 — "part of a feature" granularity**~~ — ✔ answered 2026-11-07: test selection **by feature**, feature-level (every scenario of a touched feature runs), as implemented in `src/test/ingame/features.yml`.
18. ~~**Storage backend**~~ — ✔ answered 2026-11-06: adapter `DataStore` with YAML, SQLite (default) and PostgreSQL (later) backends; full relational schema; automatic one-time import of `*-data.yml`; shops moved to storage; async writes + autosave. See [`docs/storage.md`](docs/storage.md).
19. ~~**Storage — PostgreSQL / export**~~ — ✔ answered 2026-11-07: PostgreSQL is covered by a **local docker-compose** (`docker/postgres/`, see `docs/storage.md` §4), tested end to end. YAML storage is removed (legacy `*-data.yml` are only imported once); once an admin picks sqlite or postgresql there is **no migration** between them; `/atlas storage export` writes a **whole-database backup to a portable SQLite file** (see §5).
20. ~~**M1 — what should CI check instead?**~~ — ✔ answered 2026-11-07: **`./gradlew build` + unit tests** (JUnit + MockBukkit) for pure logic on each PR. In-game scenarios stay local. See M1.

---

## Changelog

The changelog lives in [`docs/claude-changelog.md`](docs/claude-changelog.md). Read it at session start and append an entry (newest first) after every change.
