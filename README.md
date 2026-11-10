# HyFactions

HyFactions is a Hytale plugin for player factions. A faction has members, power, chunk claims, relations, and a bank. Claimed land is tinted on the normal world map.

Author: Axeex

## Install

Build `HyFactions.jar`, put it in the server `mods` folder, and restart the server. The plugin id is `jaredscarito:HyFactions`.

Open `/f` in game. `/faction` and `/factions` open the same page.

Saves are written under the server data directory in `mods/jaredscarito_HyFactions/`. `factions.json` holds factions and claims. `config.json` holds the tuning values and is created on first start.

If you are upgrading a server that already ran this plugin under the name Factions, stop the server first and rename the old data folder:

```text
mods/jaredscarito_Factions  →  mods/jaredscarito_HyFactions
```

Remove the old `Factions.jar` so the server does not load both copies. The staff permission stays `factions.admin`.

## Build

Java 25 is required. The Gradle wrapper is included.

Point the build at your server jar, then build:

```shell
./gradlew build -PhytaleServerJar=/path/to/HytaleServer.jar
```

`HYTALE_SERVER_JAR` is accepted as well. The jar is written to `build/libs/HyFactions.jar`.

## Playing

A claim is one 32×32 chunk, the full height of the world. Power is the limit on how much land a faction can hold. Land past that power can be taken by enemies and neutrals. Allies, truces, peaceful factions, and permanent land cannot be overclaimed. Unclaiming refunds the claim cost. Dying costs power. A power boost stays until someone changes it.

Entering land announces only when the owner changes: wilderness to a claim, a claim back to wilderness, or one faction to another.

In chat, your faction is green, allies are dark green, truces are yellow, and enemies are red. A public line starts with `!`.

`/f` opens a page of commands. Click a row to place that command in chat. Fill in anything in angle brackets, then send it.

### Start

| Command | What it does |
| --- | --- |
| `/f create <name>` | Found a faction. You become the leader. Names are 3–16 letters, numbers, or underscores. |
| `/f disband --confirm` | Delete your faction and release its land. Leader only. |
| `/f open` | Toggle open joining. |
| `/f info [name]` | Show a faction. Alone, it shows yours. |
| `/f list` | List every faction. |
| `/f desc <text>` | Set the description shown by `/f info`. |
| `/f peaceful` | Toggle peaceful. Peaceful land cannot be taken, and the faction does not fight. |

### People

Ranks run from recruit to member, officer, co-leader, and leader.

| Command | What it does |
| --- | --- |
| `/f invite <player>` | Invite someone who is online. Invites last 5 minutes. |
| `/f join [name]` | Accept an invite. Add the name when you have more than one. |
| `/f deny` | Decline your invite. |
| `/f leave` | Leave your faction. |
| `/f kick <player>` | Remove a member. |
| `/f promote <player>` | Promote a member toward officer. |
| `/f demote <player>` | Demote an officer to member. |
| `/f leader <player>` | Hand leadership to another member. |
| `/f ban <player>` | Ban a player from the faction. |
| `/f unban <player>` | Remove a faction ban. |

### Land

| Command | What it does |
| --- | --- |
| `/f claim` | Claim the chunk you are standing in. |
| `/f unclaim` | Release this chunk and refund the claim cost. |
| `/f unclaimall --confirm` | Release every chunk your faction owns. |
| `/f autoclaim` | Claim chunks as you walk. |
| `/f claimline <count>` | Claim a line in the direction you face, up to 32. |
| `/f claimsquare <radius>` | Claim a square around you. Radius up to 7. |
| `/f claimcircle <radius>` | Claim a circle around you. Radius up to 7. |
| `/f claimfill` | Fill the enclosed wilderness around you. |
| `/f map` | Show nearby chunks in chat. The world map shows the same claims. |
| `/f owner <member>` | Set which member owns this chunk. |
| `/f access <player>` | Grant or remove build access on this chunk. |
| `/f entry <allow\|deny>` | Allow or deny enemies walking onto your land. |
| `/f tag <tag>` | Set the short tag shown in chat. |
| `/f title <text>` | Set the faction title. |
| `/f announce <text>` | Set the message players see when they enter your land. |

### Travel

Home is one shared place. Warps are extra places, and a warp can ask for a password. Home has a short warmup, and moving cancels it.

| Command | What it does |
| --- | --- |
| `/f sethome` | Set the faction home where you stand. |
| `/f home` | Teleport to the faction home. |
| `/f setwarp <name> [password]` | Set a warp where you stand. |
| `/f delwarp <name>` | Delete a warp. |
| `/f warp <name> [password]` | Teleport to a warp. |
| `/f warps` | List the faction warps. |
| `/f stuck` | Escape another faction's land. |

### Relations

| Command | What it does |
| --- | --- |
| `/f ally <faction>` | Request or accept an alliance. |
| `/f truce <faction>` | Request or accept a truce. |
| `/f enemy <faction>` | Mark a faction as an enemy. |
| `/f neutral <faction>` | Clear a relation. |

### Chat and bank

| Command | What it does |
| --- | --- |
| `/f chat [mode]` | Set chat to public, faction, ally, or truce. Alone, it cycles. |
| `/f say <message>` | Send one message to your faction. |
| `/fc <message>` | Send a message to your faction. `/fchat` is the same command. |
| `/f power` | Show your power and how much land the faction holds. |
| `/f money` | Show your balance and the faction bank. |
| `/f deposit <amount>` | Move money from your balance into the faction bank. |
| `/f withdraw <amount>` | Move money from the faction bank to your balance. |

## Staff

Grant `factions.admin`. Do not grant it to every player. `/f admin` opens a management page: pick a faction, then lock it, change its rules, adjust its power boost, disband it, or claim the chunk you are standing in. The bottom of that page works on the chunk you are in: unclaim, safezone, warzone, and bypass. Bypass lets you build and break in claimed land. Disband asks you to click again before it deletes the faction.

These commands still work when you need to type an amount:

| Command | What it does |
| --- | --- |
| `/f admin bypass` | Toggle building in claimed land. |
| `/f admin unclaim` | Release the claim you are standing in. |
| `/f admin disband <name> --confirm` | Delete a faction and its claims. |
| `/f admin safezone` | Mark this chunk as server safe land. |
| `/f admin warzone` | Mark this chunk as server war land. |
| `/f admin claim <faction>` | Claim this chunk for a faction. |
| `/f admin give <player> <amount>` | Add money to a player's balance. |
| `/f admin power <player> <amount>` | Set a player's power. |
| `/f admin powerboost <player> <amount>` | Set a player's power boost. |
| `/f admin factionboost <faction> <amount>` | Set a faction power boost. |
| `/f admin permanent <name>` | Toggle a permanent faction. Members cannot `/f disband` it. |
| `/f admin lock <name>` | Toggle a faction lock. Members cannot manage a locked faction. |
| `/f admin reload` | Reload `config.json`. |

## Config

`config.json` lives beside `factions.json`. Restart is not required after `/f admin reload`.

| Key | Default | Meaning |
| --- | --- | --- |
| `maxPower` | 10 | Power cap for a player. |
| `minPower` | 0 | Power floor. |
| `powerLossOnDeath` | 2 | Power lost on death. |
| `powerRegenPerMinute` | 0.5 | Power regained over time. |
| `offlineDecayGraceHours` | 24 | Hours offline before power starts to decay. |
| `offlineDecayPerHour` | 1 | Power lost per hour after that grace. |
| `claimCost` | 10 | Money paid by the player who claims. |
| `startingBalance` | 250 | Balance for a new profile. |
| `unclaimRefund` | true | Refund `claimCost` on unclaim. |
| `homeWarmupSeconds` | 3 | Wait before `/f home`. |
| `inviteMinutes` | 5 | How long an invite lasts. |
| `stuckCooldownSeconds` | 30 | Cooldown for `/f stuck`. |
| `raidStartHour`, `raidEndHour` | -1 | Server-clock raid window. `-1` means raids are always open. While raids are closed, player explosion and fire damage in claims is blocked. |
| `protectOfflineFactions` | false | While every member is offline, block overclaim and explosion damage. |
| `friendlyFire` | false | Members damaging each other. |
| `allyPvp`, `trucePvp`, `enemyPvp` | false, false, true | PvP by relation. |
| `neutralPvpInClaims` | false | Neutrals fighting inside claims. |
| `neutralPvpInWilderness` | true | Neutrals fighting in wilderness. |

Build, container, door, and button access have a true/false value for ally, truce, neutral, and enemy. Allies and truces can use doors and buttons. They cannot build or open containers. Neutrals can use buttons. Enemies cannot build, open containers, use doors, or use buttons. Entry follows the same four relations, and a faction can still deny enemy entry with `/f entry deny`.
