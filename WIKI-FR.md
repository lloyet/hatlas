# Atlas — Wiki Joueur

> **Mise à jour actuelle : Ruby Ruins** — les rubis (la monnaie des factions), l'équipement en améthyste, les zones de minerai, l'hôtel des ventes et les boutiques de métier.

## Sommaire

| Section | Description |
|---|---|
| [Faction](#faction) | Créer et gérer une faction : niveaux, points de compétence, cristal, claims, alliés |
| [Métier](#métier) | Choisir une profession (après avoir rejoint une faction), faire des quêtes quotidiennes |
| [Donjon](#donjon) | Raider des donjons par vagues, vaincre un boss, partager le butin, gagner des récompenses spéciales |
| [Combat & PvP](#combat--pvp) | PvP 1.8, règles d'anti-déconnexion (combat log) |
| [Zones Sûres](#zones-sûres) | Hubs protégés et comportement de `/spawn` |
| [Commandes de base](#commandes-de-base) | Maison, échange, téléportation et commandes utilitaires |

---

## Faction

### Qu'est-ce qu'une Faction ?

<img width="1919" height="1007" alt="{D4B06E27-339A-45A8-8628-242463FF7C36}" src="https://gist.github.com/user-attachments/assets/077a2bdb-233a-4adc-9ab7-8ace5940fd9c" />

Une faction est un groupe persistant de joueurs qui combattent, progressent et gagnent des récompenses ensemble. Les contributions de chaque membre (quêtes de métier, donjons complétés) alimentent le pool d'XP partagé de la faction, ce qui fait monter son niveau et octroie des **Points de Compétence**. Les Points de Compétence se dépensent dans l'inventaire du Cristal d'Atlas pour faire grandir votre faction comme *vous* le voulez — claims, coffres, protection, PV ou homes.

### Niveaux et XP


<img width="506" height="441" alt="{051CFD19-C936-4719-8015-9855DC8D04F0}" src="https://gist.github.com/user-attachments/assets/53ef1bc8-7d0d-48d6-a9ae-1a997c4d1698" />

Les factions démarrent au **niveau 0**. L'XP requise pour atteindre le niveau suivant suit une formule à progression géométrique :

```
XP pour atteindre le niveau 1  = 100
XP pour atteindre le niveau N  = ceil(XP(N-1) × 1.09)
```

Le niveau maximum d'une faction est **99**.

L'XP est gagnée en complétant des quêtes de métier (système de quêtes quotidiennes) ou en terminant des donjons. Elle est ajoutée automatiquement au pool de la faction — aucune action manuelle n'est requise.

Chaque montée de niveau octroie des **Points de Compétence**. Monter de niveau n'attribue plus automatiquement des claims, coffres, protections ou PV — ce sont désormais des achats individuels.

### Points de Compétence

La boutique de Points de Compétence est accessible dans l'inventaire du Cristal d'Atlas (clic droit sur un cristal de faction). Chaque compétence s'achète indépendamment :

| Compétence | Effet | Achetable plusieurs fois ? |
|---|---|---|
| **Claims** | Octroie des chunks supplémentaires à claim manuellement | Oui |
| **Coffre de Faction** | Débloque un coffre double virtuel supplémentaire partagé entre tous les membres | Oui |
| **PV** | Augmente les PV maximum des Cristaux d'Atlas de votre faction | Oui |
| **Home** | Ajoute un emplacement de home personnel à chaque membre | Oui |
| **Protection** | Ajoute une protection en réserve, déclenchée à la destruction du Cristal | Oui |

Vous décidez de la trajectoire de votre faction. Il n'y a pas d'arbre d'amélioration imposé — chaque faction grandit compétence par compétence.

### Coffre de Faction

<img width="500" height="440" alt="{23DC93FF-E356-480D-816C-59D1B51E3005}" src="https://gist.github.com/user-attachments/assets/f06b2f82-e491-49ae-b105-8c6d802f1392" />

Le coffre de faction est un espace de stockage virtuel (coffre double) partagé entre tous les membres. Le nombre de coffres disponibles dépend de la compétence **Coffre de Faction** — achetez-en plus avec des Points de Compétence pour étendre l'espace. L'accès se fait via l'interface du Cristal d'Atlas.

Si une rétrogradation de niveau réduit le nombre de coffres en dessous de ce que vous utilisez, les coffres en trop sont détruits et leur contenu est jeté au sol à l'emplacement du cristal.

### Protection du Cristal

La protection est un achat de Point de Compétence **mis en réserve** jusqu'à l'attaque de votre Cristal.

Comment ça marche :
1. Votre Cristal est détruit par un ennemi.
2. Le Cristal réapparaît automatiquement à pleine vie et devient **invincible** pour la durée de la plus longue protection en réserve.
3. Si aucun dégât n'est subi pendant cette fenêtre, la protection se régénère et reste disponible pour la prochaine attaque.
4. Si vous possédez plusieurs protections, elles s'activent les unes après les autres, **la plus longue en premier**.

**Exemple :** votre faction possède une protection d'1 h et une protection de 30 min. Quand le Cristal est détruit, il revient à pleine vie et devient invincible pendant 1 h. Si la protection d'1 h tient sans subir de dégâts, elle se régénère. Si elle est percée, la protection de 30 min prend automatiquement le relais.

L'ancienne mécanique de « protection à la rétrogradation de niveau » a été supprimée — toute la protection du Cristal vient désormais de cette réserve.

### Claims

Les claims protègent les chunks contre le minage et la construction ennemis. Ils ne sont plus accordés automatiquement aux montées de niveau — vous devez dépenser des Points de Compétence pour gagner des emplacements de claim, puis claim chaque chunk manuellement.

```
/faction claim     — claim le chunk sur lequel vous vous trouvez
/faction unclaim   — libère le chunk sur lequel vous vous trouvez
/faction showclaim — affiche des particules sur les bordures de vos chunks claim
```

**Règle d'adjacence :** un nouveau claim doit toucher un claim existant (par côté de chunk). Votre tout premier claim doit être adjacent à votre Cristal d'Atlas.

`/faction info` affiche votre compteur `[Claims Libres / Claims Totaux]` ainsi que la protection restante.

### Home du Cristal & Promotion d'Avant-poste

Créer un Cristal d'Atlas **ne définit plus automatiquement le home de votre faction**. Un message dans le chat vous invite à le définir manuellement :

```
/faction sethome              — place le home du Cristal principal à votre position actuelle
/faction sethome <cristal>    — place le home d'un Cristal possédé à votre position (Propriétaire / Chef)
```

Une fois qu'un avant-poste est créé, **n'importe quel Cristal peut être promu** entre *principal* et *avant-poste*. Cela vous permet de déplacer votre QG vers un avant-poste sans le perdre — utile lorsqu'un avant-poste est mieux défendu que votre Cristal principal d'origine.

`/sethome` (le home personnel) est **bloqué à l'intérieur d'un territoire ennemi**.

### Système d'Alliance

Deux factions peuvent devenir alliées. Les factions alliées :
- Ne **se font pas** de dégâts entre elles (les tirs amis sont désactivés)
- N'**attaquent pas** les entités de donjon de l'autre
- Peuvent optionnellement partager la messagerie de faction

Pour former une alliance, les propriétaires ou chefs des deux factions doivent être d'accord :

```
/faction ally <faction>       — envoyer une demande d'alliance
/faction allyaccept <faction> — accepter une demande en attente
/faction allydeny  <faction>  — refuser une demande en attente
/faction unally <faction>     — dissoudre une alliance existante
```

Lorsqu'une faction est **dissoute** parce qu'une autre faction a détruit son dernier Cristal, un message global est diffusé à tous les joueurs du serveur — la victoire est attribuée à l'attaquant.

### Commandes de Faction

| Commande | Description | Permission |
|---|---|---|
| `/faction` | Afficher le menu d'aide | `atlas.faction` |
| `/faction create <nom>` | Créer une nouvelle faction | `atlas.faction.create` |
| `/faction disband` | Dissoudre votre faction (propriétaire uniquement) | `atlas.faction.disband` |
| `/faction rename <nom>` | Renommer votre faction | `atlas.faction.rename` |
| `/faction description <texte>` | Définir la description de la faction | `atlas.faction.description` |
| `/faction color <couleur>` | Changer la couleur du chat de faction | `atlas.faction.color` |
| `/faction invite <joueur>` | Inviter un joueur dans votre faction | `atlas.faction.invite` |
| `/faction accept` | Accepter une invitation de faction | `atlas.faction.accept` |
| `/faction decline` | Refuser une invitation de faction | `atlas.faction.decline` |
| `/faction leave` | Quitter votre faction actuelle | `atlas.faction.leave` |
| `/faction kick <joueur>` | Expulser un membre de la faction | `atlas.faction.kick` |
| `/faction promote <joueur>` | Promouvoir un membre au rang supérieur | `atlas.faction.promote` |
| `/faction demote <joueur>` | Rétrograder un membre au rang inférieur | `atlas.faction.demote` |
| `/faction transfer <joueur>` | Transférer la propriété de la faction | `atlas.faction.transfer` |
| `/faction info [faction]` | Afficher les infos d'une faction (claims, protection, PV) | `atlas.faction.info` |
| `/faction list` | Lister toutes les factions | `atlas.faction.list` |
| `/faction members` | Lister les membres et rôles de votre faction | `atlas.faction.members` |
| `/faction home` | Se téléporter au home du Cristal principal | `atlas.faction.home` |
| `/faction sethome [cristal]` | Définit le home d'un Cristal à votre position (Propriétaire/Chef) | `atlas.faction.sethome` |
| `/faction claim` | Claim le chunk sur lequel vous vous trouvez | `atlas.faction.claim` |
| `/faction unclaim` | Libère le chunk sur lequel vous vous trouvez | `atlas.faction.claim` |
| `/faction showclaim` | Affiche des particules sur les bordures de vos claims | `atlas.faction.claim` |
| `/faction ally <faction>` | Envoyer une demande d'alliance | — |
| `/faction allyaccept <faction>` | Accepter une demande d'alliance | — |
| `/faction allydeny <faction>` | Refuser une demande d'alliance | — |
| `/faction unally <faction>` | Dissoudre une alliance | — |
| `/faction msg <texte>` | Envoyer un message aux membres en ligne uniquement | `atlas.faction.msg` |

### Rôles des Membres

| Rôle | Permissions |
|---|---|
| **Membre** | Accès de base, aucune gestion |
| **Modérateur** | Inviter, expulser des membres |
| **Chef** | Toutes les actions de Modérateur + renommer, couleur, description, sethome sur n'importe quel Cristal |
| **Propriétaire** | Contrôle total, y compris dissoudre, transférer la propriété et promouvoir un Cristal |

---

## Métier

### Qu'est-ce qu'un Métier ?

Chaque joueur peut exercer l'un des quatre métiers disponibles. Votre métier détermine les quêtes quotidiennes que vous recevez et la façon dont vous contribuez à l'XP de votre faction.

> **Vous devez rejoindre une faction (via le PNJ de faction) avant de pouvoir prendre un métier.** Les métiers requièrent une faction d'origine pour que l'XP gagnée ait une destination.

La progression du métier (niveau + XP) est personnelle ; l'XP de faction gagnée grâce aux récompenses de quêtes est partagée.

### Métiers Disponibles

| Métier | Outil | Spécialité |
|---|---|---|
| **Mineur** | Pioche en Fer | Miner des minerais et de la pierre |
| **Bûcheron** | Hache en Fer | Couper des arbres |
| **Chasseur** | Arc | Tuer des mobs |
| **Fermier** | Graines de Blé | Récolter des cultures |

Attribuez ou changez votre métier en interagissant avec le PNJ correspondant dans le monde.

> Changer de métier réinitialise votre XP et votre niveau de métier actuels et déclenche un **temps de recharge de 24 heures** avant de pouvoir changer à nouveau.

### Quêtes Quotidiennes

Chaque jour, trois quêtes sont générées pour vous en fonction de votre métier. Vous pouvez sélectionner et activer **jusqu'à 2** des trois quêtes proposées par jour. Une fois 2 quêtes sélectionnées, le quota quotidien est verrouillé jusqu'au lendemain.

Chaque quête contient **1 ou 2 tâches** tirées aléatoirement du pool de tâches de votre métier, calibrées selon un niveau de difficulté aléatoire :

| Difficulté | Nom | Description |
|---|---|---|
| 1 | Facile | Petites quantités, peu de temps |
| 2 | Normal | Quantités modérées |
| 3 | Difficile | Grandes quantités |
| 4 | Hardcore | Quantités élevées, peu de temps |

Chaque tâche dans `jobs.yml` possède un champ **`multiplier_exp`** par difficulté (`easy` / `normal` / `hard` / `hardcore`, valeur par défaut `1.0`). Les administrateurs peuvent ainsi ajuster individuellement le rendement d'XP de chaque tâche pour chaque difficulté.

La progression est suivie automatiquement pendant le jeu. Un message en barre d'action affiche votre avancement par tâche en temps réel. Lorsque toutes les tâches d'une quête sont complétées, les récompenses sont accordées automatiquement.

### Formule de Récompense en XP de Faction

Compléter une quête accorde de l'XP directement au pool de votre faction :

```
XP Faction = récompenseBase × sommeDifficulté × √(niveauFaction + 1)
```

Où :
- `récompenseBase` = 10 (valeur par défaut du serveur, configurable)
- `sommeDifficulté` = somme des valeurs de difficulté de toutes les tâches de la quête
- `niveauFaction` = niveau actuel de votre faction au moment de la complétion

Des niveaux de faction élevés et des quêtes plus difficiles multiplient considérablement la récompense.

### Récompenses en Objets

Chaque tâche peut fournir des récompenses en objets qui évoluent avec la difficulté. Les objets sont placés directement dans votre inventaire ; tout surplus est jeté à vos pieds.

### Jokeyrini — Le PNJ de Quête Spéciale

**Jokeyrini** est un PNJ spécial disponible indépendamment du système de métiers. Une fois par jour, Jokeyrini propose une quête unique avec 2 à 3 tâches en difficulté **Difficile** (niveau 3) ou **Hardcore** (niveau 4). Il y a **15 % de chance** que l'offre quotidienne soit une **Quête Spéciale Légendaire** — 3 tâches toutes en difficulté 5.

À la complétion, Jokeyrini récompense des **Clés de Donjon** :

| Type de quête | Clés reçues |
|---|---|
| Difficile (difficulté max 3) | 1 clé |
| Hardcore (difficulté max 4) | 2 clés |
| Spéciale Légendaire | 3 clés |

La progression des quêtes Jokeyrini est affichée dans la barre d'action avec le préfixe `[Jokeyrini]`.

### Commandes de Métier

Interagissez directement avec les PNJ de métier pour accéder à l'interface du métier. Il n'y a pas de commandes de chat pour la gestion des quêtes quotidiennes — tout se fait via l'interface en jeu.

---

## Donjon

### Qu'est-ce qu'un Donjon ?

Les donjons sont des structures pré-construites placées dans le monde par un administrateur. Chaque donjon alterne entre les états **inactif** et **actif**. Lorsqu'il est actif, un joueur possédant une **Clé de Donjon** peut entrer et démarrer le donjon. Celui-ci enchaîne alors une série de **vagues** d'ennemis, se terminant par une **vague de boss**.

### Activation des Donjons

Les donjons s'activent selon un planning serveur. Le **PNJ des donjons au spawn** affiche un compte à rebours en direct jusqu'à la prochaine activation dans son interface, ce qui vous permet de planifier vos farms de clés Jokeyrini.

### Types de Donjons

| Type | Thème |
|---|---|
| **Trial** | Donjon classique |
| **Desert** | Ruines du désert |
| **Nether Castle** | Forteresse du Nether |
| **Plains** | Plaines ouvertes |
| **Sky** | Île céleste |
| **Ocean** | Ruines sous-marines |

Chaque type possède son propre ensemble de mobs, types de boss et tables de butin.

### Niveau

Lorsqu'un donjon devient actif, un niveau aléatoire compris entre **0 et 99** lui est attribué. Le niveau détermine :
- Le nombre de vagues (évolue entre le min et le max définis pour le type)
- Le nombre de mobs par vague
- Les multiplicateurs de PV et de dégâts des ennemis
- Le montant d'XP de faction accordé

Plus le niveau est élevé = plus de mobs, des ennemis plus résistants et plus d'XP.

### Rareté

Chaque activation de donjon se voit également attribuer une **rareté**, tirée d'un pool pondéré :

| Rareté | Couleur | Multiplicateur d'XP | Probabilité |
|---|---|---|---|
| Commun | Blanc | ×1.0 | 66 % |
| Rare | Cyan | ×1.5 | 14 % |
| Épique | Violet clair | ×2.5 | 10 % |
| Légendaire | Or | ×5.0 | 5 % |
| Mystique | Rouge | ×10.0 | 3 % |
| Déesse | Jaune | ×20.0 | 2 % |

La rareté multiplie l'XP totale distribuée à la fin du donjon.

### Vagues

Un donjon se déroule en plusieurs vagues successives. Chaque vague doit être entièrement éliminée avant que la suivante ne commence. Après avoir nettoyé une vague, vous disposez de **5 secondes** avant que la vague suivante ne démarre.

**Règles de comptage des kills :**
- Un mob qui meurt sans qu'un joueur en soit responsable (chute, noyade, auto-explosion) **réapparaît** au lieu de faire baisser le compteur de la vague.
- Un mob tué par un autre mob (par exemple, une flèche de squelette qui tue un zombie) **compte** désormais pour la progression de la vague.

La **dernière vague** est toujours une **Vague de Boss** : moins d'ennemis, mais bien plus résistants. Un son et un titre spéciaux annoncent la vague de boss à son début.

Si tous les joueurs quittent le donjon (aucun joueur détecté à l'intérieur), le donjon se réinitialise automatiquement — la progression et les kills sont perdus.

### Clés de Donjon

| Clé | Source | Comportement |
|---|---|---|
| **Clé de Donjon** | Quêtes Jokeyrini | Démarre un donjon *actif* à son niveau et sa rareté actuellement tirés au sort |
| **Clé Sinistre de Donjon** | Drops spéciaux | Estampillée à sa création avec une **difficulté (50–99)** et une **rareté (Épique+)** fixes. L'utiliser force le donjon à démarrer *exactement* avec le niveau et la rareté inscrits sur la clé |
| **Clé d'Épreuve Omineuse** | Donjons complétés en Épique+ | Force l'activation d'un donjon inactif en rareté Légendaire, Mystique ou Déesse |

### Démarrer un Donjon

1. Obtenir une **Clé de Donjon** (quêtes Jokeyrini) ou une **Clé Sinistre de Donjon** (drops spéciaux).
2. Trouver un donjon **actif** (annoncé à l'ensemble du serveur lors de son activation).
3. Entrer dans la structure du donjon avec la clé en main — un clic droit sur le générateur d'épreuves démarre le donjon.
4. Survivre à toutes les vagues et vaincre le boss.

Une Clé Sinistre court-circuite le tirage aléatoire de niveau et de rareté pour démarrer le donjon avec les valeurs inscrites sur la clé.

### Distribution de l'XP

L'XP totale est calculée ainsi :

```
XP totale = (expBase + niveau/99 × (expMax - expBase)) × multiplicateurRareté
```

À la fin du donjon, l'XP est distribuée entre toutes les factions ayant infligé des dégâts au **boss**, proportionnellement aux dégâts infligés par chaque faction :

```
Part de la faction = XP totale × (dégâts du boss de la faction / dégâts totaux du boss)
```

Cela signifie que plusieurs factions peuvent participer au même donjon et chacune reçoit une part proportionnelle des récompenses. La faction ayant infligé le plus de dégâts au boss est annoncée comme la faction victorieuse.

### Récompenses de Donjon

En plus de l'XP, les donjons peuvent faire tomber des **récompenses de siège** uniques qui changent la façon dont les factions interagissent avec les territoires ennemis :

| Récompense | Effet |
|---|---|
| **Pioche du Pillard** | Permet à un membre de faction de casser **5 à 20 blocs** à l'intérieur des **claims d'une faction ennemie**. Le nombre d'utilisations est inscrit sur l'objet ; la pioche est consommée quand les utilisations sont épuisées |
| **Œuf de Creeper** | Fait apparaître un Creeper standard dans un claim ennemi. **Trois** détonations de creepers normaux dans un rayon de **3 blocs** peuvent briser de l'obsidienne |
| **Œuf de Creeper Chargé** | Fait apparaître un Creeper Chargé. **Une seule** détonation chargée brise l'obsidienne dans un rayon de 3 blocs |
| **Téléportation de Donjon (Teleportation Ward)** | Objet de téléportation rapide vers un donjon spécifique. Correctement sauvegardé après un redémarrage du serveur ; rachetable chez le **PNJ Contrebandier** au spawn |
| **Clé d'Épreuve Omineuse** | Voir *Clés de Donjon* — force l'activation d'un donjon de haute rareté |

### Système d'Alliance dans les Donjons

Les factions alliées combattant dans le même donjon ne **se font pas** de dégâts entre elles. Cela permet aux factions alliées de coopérer à l'intérieur d'un donjon sans risquer les tirs amis. Le statut d'alliance est vérifié au moment de chaque attaque.

---

## Combat & PvP

### PvP Style 1.8

Le combat natif de la 1.21.11 (sweep / cooldown d'attaque) est **désactivé** au profit d'un plugin de PvP en style 1.8. Il n'y a plus de barre de cooldown — chaque clic gauche enregistre une frappe complète.

### Anti-Déconnexion (Combat Log)

Lorsqu'un joueur inflige des dégâts à un autre joueur (mêlée ou projectile), l'attaquant et la victime sont marqués **en combat** pendant **20 secondes** (configurable dans `combats.yml`).

- Se déconnecter pendant ce délai **tue le joueur** et fait tomber l'inventaire complet + l'armure à sa dernière position.
- Chaque nouveau coup réinitialise le timer à la durée complète.
- Tenir le timer en entier sans subir de dégâts efface la marque — vous pouvez quitter en sécurité.
- Les timers de combat actifs persistent à travers les redémarrages serveur (`combats-data.yml`) ; un redémarrage propre ne **tue pas** les joueurs en combat.

Des notifications en barre d'action signalent l'entrée en combat et l'expiration du timer.

---

## Zones Sûres

Les Zones Sûres sont des aires définies par les administrateurs (par exemple le spawn) protégées contre le PvP et les actions territoriales. Les joueurs entrant dans une zone sûre voient leur visite enregistrée et peuvent s'y téléporter plus tard.

```
/safezone tp <nom>     — se téléporter vers une zone sûre déjà visitée (compte à rebours, contournable par op)
```

`/spawn` est un alias strict de `/safezone tp spawn`.

---

## Commandes de Base

### /home et /sethome

Définissez un foyer personnel nommé et téléportez-vous-y à tout moment.

```
/sethome <nom>   — enregistre votre position actuelle comme foyer
/home            — se téléporte vers votre premier foyer (le plus ancien)
/home <nom>      — se téléporte vers un foyer nommé spécifique
```

- La téléportation lance un **compte à rebours de 5 secondes**. Se déplacer annule la téléportation.
- Il y a un **temps de recharge de 30 secondes** entre les téléportations.
- `/sethome` est **bloqué à l'intérieur d'un territoire ennemi**.
- Le nombre de homes disponibles est piloté par la compétence **Home** (accordée à chaque membre de la faction lorsqu'elle est achetée).

### /trade

Initiez un échange d'objets pair à pair avec un autre joueur.

```
/trade <joueur>  — envoyer une demande d'échange à un joueur
```

Les deux joueurs voient une interface dans laquelle ils peuvent déposer des objets dans leurs emplacements respectifs. Les deux joueurs doivent cliquer sur **Accepter** avant qu'un compte à rebours de 3 secondes valide l'échange. Chaque joueur peut annuler à tout moment avant la fin du compte à rebours.

### /tpa

Demandez à vous téléporter chez un autre joueur.

```
/tpa <joueur>    — envoyer une demande de téléportation
/tpa accept      — accepter une demande reçue (ou cliquer sur [Accepter] dans le chat)
/tpa deny        — refuser une demande reçue (ou cliquer sur [Refuser] dans le chat)
```

- Les demandes expirent après **30 secondes** si elles ne reçoivent pas de réponse.
- Les téléportations acceptées lancent un **compte à rebours de 10 secondes**. Recevoir des dégâts annule la téléportation.
- Il y a un **temps de recharge de 30 secondes** entre les demandes.

### /spawn

Alias strict de `/safezone tp spawn`.

```
/spawn
```

- Lance un **compte à rebours de 10 secondes**. Se déplacer ou recevoir des dégâts l'annule.
- Possède un **temps de recharge de 30 secondes** (contournable par op).
- Un bug qui permettait à certains joueurs d'éviter le cooldown a été corrigé.

### /rtp

Se téléporter aléatoirement vers un endroit sûr près du spawn du monde.

```
/rtp
```

- Lance un **compte à rebours de 5 secondes**. Se déplacer annule la téléportation.
- Possède un **temps de recharge de 30 secondes**.
- Le rayon maximum est de **1024 blocs**.
- **Bloqué dans le Nether et l'End** — utilisez un portail à la place.
- La destination est toujours un emplacement sûr avec un sol solide et de l'air au-dessus.
