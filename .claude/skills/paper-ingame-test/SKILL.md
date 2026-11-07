---
name: "paper-ingame-test"
description: "Teste en jeu une fonctionnalité d'un plugin Paper 1.21.x avec des bots offline via MCP (VitaminMCP, ou Mineflayer + RCON) : build, déploiement, scénario, assertions, rapport."
---

# Test en jeu de plugins Paper via MCP

Utilise ce skill quand l'utilisateur demande de tester, vérifier, reproduire un bug ou valider une fonctionnalité de son plugin Paper (Minecraft 1.21.x) en jeu : commande, GUI, listener, permission, PNJ, scoreboard, persistance, configuration, etc.

Objectif : produire un verdict vérifiable (PASS / FAIL / BLOQUÉ) pour chaque cas de test, appuyé sur des preuves collectées côté client (ce que le bot a reçu) et côté serveur (événements, état, logs, exceptions), puis sauvegarder le scénario pour la non-régression.

## 0. Vérifications préalables (toujours)

1. **Outils MCP disponibles** :
   - Mode A (recommandé) : outils `mcp__vitaminmcp__*` (`session_start`, `bot_spawn`, `bot_run_scenario`, `bot_inspect`, `state_query`, `events_query`, `logs_query`, `exceptions_recent`, `command_exec`, `wait_for`…).
   - Mode B (repli) : un MCP bot Mineflayer (`move-to-position`, `dig-block`, `send-chat`, `read-chat`, `list-inventory`…) + un MCP RCON (`run_command`, `run_command_with_log`, `read_test_log`, `read_server_log`).
   - Aucun des deux : arrête-toi et explique quoi installer (`claude mcp add --scope project vitaminmcp -- npx -y vitaminmcp` + `VitaminMCP.jar` dans `plugins/`).
2. **Contexte projet** : lis `CLAUDE.md` pour `TEST_SERVER_DIR`, `PLUGIN_NAME` et le script de contrôle (par défaut `scripts/test-server.sh start|stop|restart|deploy|status`). Lis `src/main/resources/plugin.yml` ou `paper-plugin.yml` (nom, commandes, permissions, dépendances) et la config par défaut.
3. **Garde-fou serveur de test** : n'agis que sur un serveur de test (localhost, tunnel SSH ou hôte explicitement désigné comme test dans `CLAUDE.md`). Si l'hôte ressemble à de la production ou si tu as un doute, demande confirmation avant toute commande qui modifie le monde, les permissions ou les données.
4. **Prérequis offline** : le serveur doit avoir `online-mode=false` et `enforce-secure-profile=false`. Si un bot est expulsé (« Failed to verify username ») ou si son chat est ignoré, signale la ligne de `server.properties` à corriger.

## 1. Comprendre et planifier

- Localise le code de la fonctionnalité : `CommandExecutor` / Brigadier, `@EventHandler`, `InventoryHolder` ou menus, tâches planifiées, accès aux données (PDC, YAML, SQL).
- Écris un plan de test court, sous forme de tableau : `ID | Cas | Préconditions | Actions | Résultat attendu | Preuve`. Couvre au minimum :
  - le cas nominal ;
  - un cas sans permission (bot non-op, sans le nœud) ;
  - un cas limite (argument invalide, inventaire plein, solde insuffisant, cooldown…) ;
  - la persistance si la fonctionnalité stocke des données (déconnexion/reconnexion du bot, puis redémarrage serveur si pertinent).
- Si l'utilisateur a déjà dit « vas-y » ou si le test est simple, enchaîne sans attendre de validation. Sinon, montre le plan et demande s'il manque un cas.

## 2. Construire et déployer (si le code a changé depuis le dernier déploiement)

1. `scripts/test-server.sh deploy` (build Gradle, copie du jar, redémarrage, attente de « Done ( » dans `logs/latest.log`). Sans script : `./gradlew build`, copie du jar dans `$TEST_SERVER_DIR/plugins/` en supprimant l'ancien, redémarrage.
2. N'utilise jamais `/reload` ni PlugMan : redémarre toujours le serveur.
3. Si le build échoue, corrige ou rapporte l'erreur de compilation avant d'aller plus loin.

## 3. Ouvrir la session (mode A)

1. `session_start` avec `{}` en local (sinon `host`, `mcpPort`, `token` fournis par l'utilisateur), puis `server_info` : vérifie que la version est 1.21.x et que le plugin testé est chargé et activé.
2. Ligne de base : `exceptions_recent` et `events_summary` (note l'horodatage de départ), pour ne compter ensuite que ce que ton test provoque.
3. Si `command_exec` est absent, le plugin VitaminMCP est en `read-only: true` : demande à l'utilisateur de passer `read-only: false` dans `plugins/VitaminMCP/config.yml` puis de redémarrer. Continue en lecture seule si c'est possible, sinon marque le test BLOQUÉ.

## 4. Préparer le joueur et le monde

- Noms des bots : `Tester1`, `Tester2`… (UUID offline stable dérivé du nom, utile pour la persistance).
- `bot_spawn {"name":"Tester1"}`.
- Prépare l'état via la console (`command_exec` sans `as`) : `op Tester1` uniquement si le cas l'exige, sinon des permissions précises (`lp user Tester1 permission set <noeud> true` si LuckPerms est installé), `gamemode survival Tester1`, `clear Tester1`, `give Tester1 <item> <n>`, `tp Tester1 <x> <y> <z>`.
- Travaille dans une zone dédiée (par exemple autour de x=1000, z=1000) et nettoie-la avec `fill` avant chaque cas, pour que les tests soient indépendants.

## 5. Exécuter

Préfère `bot_run_scenario` (rejouable, s'arrête au premier échec) aux appels isolés. **Lis la description de l'outil `bot_run_scenario` pour le schéma exact des champs de chaque action** : les exemples ci-dessous sont indicatifs.

Actions disponibles : `spawn`, `despawn`, `move_to`, `break_block`, `place_block`, `use_block`, `use_entity`, `attack_entity`, `hold_item`, `drop_item`, `jump`, `sneak`, `sprint`, `look_at`, `assert_reachable`, `chat`, `command`, `console`, `click_slot`, `close_menu`, `wait_for`, `assert_block`, `assert_player`, `assert_event`, `assert_inventory`, `assert_message`.

Règles :
- **Jamais d'attente fixe** : utilise `wait_for` sur une condition (`block_is`, `event`, `inventory_open`, `inventory_contains`, `player_state`, `player_near`, `log_matches`, ou `ticks` en dernier recours). Le timeout par défaut est de 10 s, le maximum de 60 s.
- `command_exec` avec `as` renvoie souvent un `output` vide : la réponse est partie au client, lis-la avec `bot_inspect` (`messages`). `dispatched: true` signifie seulement que la commande a été acceptée, pas qu'elle a réussi.
- Pour une GUI dessinée par paquets, `state_query` (kind inventory) renvoie `occupiedSlots: 0` : lis le menu avec `bot_inspect` (`menu`, `items`).

Recettes par type de fonctionnalité :

| Fonctionnalité | Recette |
| --- | --- |
| Commande | `command` (bot) → `assert_message` ou `bot_inspect.messages` ; vérifier aussi l'effet (inventaire, bloc, données) |
| GUI / menu | `command` qui ouvre → `wait_for inventory_open` (titre) → `assert_inventory` (slots, matériaux, noms, lore) → `click_slot` (left, right, shift_left, shift_right) → vérifier l'effet → `close_menu` |
| Listener de bloc | `place_block` / `break_block` → `wait_for block_is` → `assert_event` (BlockBreakEvent…) + vérifier l'annulation éventuelle |
| PNJ / entité | `console summon …` → `use_entity` ou `attack_entity` → `assert_message` / `assert_inventory` |
| Permissions | `state_query {"kind":"player","target":"Tester1","permissions":["<noeud>"]}` puis le même scénario avec et sans le nœud |
| Scoreboard, bossbar, title, action bar | `bot_inspect` : `scoreboard`, `bossBars`, messages préfixés `[title]`, `[subtitle]`, `[action bar]` |
| Effets, vie, XP | `bot_inspect` : `effects`, `health`, `food`, `experienceLevel` |
| Persistance | modifier → `despawn` → `spawn` (même nom donc même UUID) → vérifier ; puis `scripts/test-server.sh restart` → nouvelle session → vérifier |
| Tâche asynchrone ou planifiée | `wait_for log_matches` sur un log du plugin ou `wait_for event` |
| Config | `state_query {"kind":"plugin","target":"<Plugin>"}` pour la config en direct ; modifier le fichier, redémarrer, re-tester |
| Multi-joueurs | deux bots `Tester1` / `Tester2`, puis vérifier ce que chacun reçoit via `bot_inspect` |

Exemple de scénario GUI (indicatif) :

```json
[
  {"action": "spawn", "bot": "Tester1"},
  {"action": "console", "command": "give Tester1 emerald 10"},
  {"action": "command", "bot": "Tester1", "command": "shop"},
  {"action": "wait_for", "condition": "inventory_open", "name": "Tester1", "title": "Shop"},
  {"action": "assert_inventory", "bot": "Tester1", "slots": [{"slot": 11, "material": "EMERALD", "name": "Buy"}]},
  {"action": "click_slot", "bot": "Tester1", "slot": 11, "click": "left"},
  {"action": "wait_for", "condition": "inventory_contains", "name": "Tester1", "material": "DIAMOND", "which": "player"},
  {"action": "close_menu", "bot": "Tester1"}
]
```

## 6. Collecter les preuves et juger

Après chaque cas :
1. `bot_inspect` (messages depuis le dernier `cursor`, menu, items, scoreboard…).
2. `events_query` sur les types pertinents et le joueur concerné.
3. `logs_query {"level":"WARN"}` et `exceptions_recent` : **toute nouvelle exception dont la stack trace contient le package du plugin fait échouer le cas**, même si le comportement visible semble correct.
4. Verdict : PASS si toutes les assertions passent et qu'aucune nouvelle exception n'apparaît ; FAIL sinon ; BLOQUÉ si l'environnement empêche le test (outil absent, serveur arrêté, dépendance manquante).
5. Sur FAIL : retrouve la cause probable dans le code (fichier:ligne), propose un correctif, et ne le modifie que si l'utilisateur l'a demandé. Après une correction : redéploie et rejoue **tous** les cas de la fonctionnalité.

## 7. Nettoyer

`session_reset` (déconnecte les bots ; l'état du monde n'est pas restauré), `deop Tester1` si tu l'avais op, retrait des permissions ajoutées, `fill` de la zone de test à l'air.

## 8. Rapport et non-régression

- Enregistre chaque scénario validé dans `src/test/ingame/<fonctionnalite>/<cas>.json` pour pouvoir le rejouer plus tard (« rejoue les tests en jeu de /shop »).
- Rapport final dans la réponse :
  - tableau `ID | Cas | Verdict | Observé | Preuve (outil + extrait court)` ;
  - exceptions nouvelles : message + 5 premières lignes de la stack trace ;
  - causes probables et correctifs proposés (fichier:ligne) ;
  - ce qui n'a pas pu être testé, et pourquoi.

## Mode B (Mineflayer + RCON)

Même démarche, avec ces équivalences :
- Préparation et commandes console : `run_command` ou `run_command_with_log` (RCON).
- Actions joueur : outils du bot (`move-to-position`, `dig-block`, `place-block`, `equip-item`, `send-chat` pour les commandes avec `/`).
- Preuves : `read-chat` (messages reçus par le bot), `list-inventory`, `get-block-info`, `read_test_log` (lignes `[TEST]` loggées par le plugin en debug), `read_server_log` pour les exceptions.
- Limites à signaler dans le rapport : pas de clic dans les GUI, pas d'événements Bukkit, et un seul bot par instance MCP.