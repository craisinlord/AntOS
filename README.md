# AntOS

AntOS adds an ant-themed computer to Minecraft 1.21.1. Its desktop includes an Archive, file tools, a Terminal, Antmail, the Antazon shop, games, Paint, Tasks, and a Disks Library. Almost everything on it is data-driven, so a modpack can add its own Archive pages, floppy disks, quests, mail, shop listings, quizzes, adventures, and secret terminal commands with a data pack and a resource pack.

This README is the reference for modpack developers. The fastest way to start is to copy [`examples/datapack-template`](examples/datapack-template), which has a working example of everything below.

The development workspace also contains forward-port targets for Minecraft 26.1, 26.2, and 26.3 on Fabric and NeoForge. Those targets are under active porting and are not release-ready until their build and runtime parity checks pass. See [`docs/PORTING_26.md`](docs/PORTING_26.md) for the project layout and Gradle tasks.

## Contents

- [Requirements and integrations](#requirements-and-integrations)
- [Configuration](#configuration)
- [Quick start](#quick-start)
- [Shared concepts](#shared-concepts): IDs, translations, previews, rewards, unlock requirements, time
- Content types: [Archive entries](#archive-entries) · [Floppy disks](#floppy-disks) · [Programs](#programs-quizzes-and-adventures) · [Tasks](#tasks) · [Antmail](#antmail) · [Antazon](#antazon) · [Terminal commands](#terminal-commands)
- [Appearance](#appearance): wallpapers, disk categories, icons, renaming apps and currency
- [Integrations](#integrations): FTB Quests, Field Guide, shipping containers
- [Java API](#java-api)
- [Commands](#commands)
- [How AntOS behaves in play](#how-antos-behaves-in-play)
- [Troubleshooting](#troubleshooting)

## Requirements and integrations

| Mod | Status |
| --- | --- |
| Fabric or NeoForge (1.21.1) | Required; use the AntOS file for your loader |
| GeckoLib | Required on both loaders |
| Fabric API | Required on Fabric |
| FTB Quests | Optional; see [FTB Quests](#ftb-quests) |
| Field Guide | Optional; see [Field Guide](#field-guide) |
| Refurbished Furniture, Create | Optional; their mailboxes and Frogports work as Antazon [shipping containers](#shipping-containers) |

## Configuration

AntOS creates `config/antos.json` on first launch. Restart the game or server after editing it. Unlock and Antazon options are read from the server; `desktopApps` is read by each client.

```json
{
  "unlockAllArchiveEntries": false,
  "unlockAllGameEntries": false,
  "showIntroductionEntry": true,
  "blockleDailyAntcoinReward": 10,
  "builtInGames": {
    "chess": true,
    "minesweeper": true
  },
  "gameRewards": {
    "chessEasy": 5,
    "chessNormal": 10,
    "chessHard": 20,
    "chessExpert": 40,
    "minesweeperSmall": 5,
    "minesweeperLarge": 15
  },
  "sendAntazonUnlockEmail": true,
  "desktopApps": {
    "archive": true,
    "files": true,
    "settings": true,
    "terminal": true,
    "textEditor": true,
    "paint": true,
    "antmail": true,
    "antazon": false,
    "games": true,
    "disks": true,
    "tasks": false
  }
}
```

| Key | Side | Default | Effect |
| --- | --- | --- | --- |
| `unlockAllArchiveEntries` | Server | `false` | Unlocks every loaded Archive entry without its disk. |
| `unlockAllGameEntries` | Server | `false` | Unlocks every registered game without its disk. |
| `showIntroductionEntry` | Server (synced) | `true` | Shows the built-in "SYSTEM INITIALIZED" Introduction page in every Archive. |
| `blockleDailyAntcoinReward` | Server | `10` | AntCoins for solving Blockle, once per profile per Minecraft day. A popup shows the award. `0` disables it. No AntCoins are awarded or shown while Antazon is disabled. |
| `builtInGames.chess`, `builtInGames.minesweeper` | Client | `true` | Installs Chess or Minesweeper on every computer. Set to `false` to require the matching disk instead (`antos:chess`, `antos:minesweeper`). AntOS doesn't define those disks; a pack that turns this off supplies the disk JSON and its drops. |
| `gameRewards.<mode>` | Server | see above | AntCoins for winning a Chess level or a Minesweeper size, once per mode per profile per Minecraft day. `0` disables that reward. Nothing is paid while Antazon is disabled. |
| `sendAntazonUnlockEmail` | Server | `true` | Sends the Antazon Dispatch email when listings unlock. |
| `desktopApps.<app>` | Client | `true` | Set to `false` to hide an app. **Antazon and Tasks default to `false`**; set `desktopApps.antazon` and `desktopApps.tasks` to `true` to enable them. A config that omits `disks` keeps the Disks Library enabled. |

The file also stores client state (last username, tour progress, collapsed task groups, terminal history). Leave those keys alone.

### Pause and interface sounds

In AntOS **Settings → Accessibility**, **Pause world while AntOS is open** pauses a private single-player world while using the computer or Antroid Phone, including sign-in and the introduction tour. It defaults to off. Multiplayer and worlds opened to LAN keep running. The preference takes effect immediately and is saved locally for that Minecraft installation.

AntOS apps and GUI animations continue while paused. World-time features, such as scheduled deliveries and daily rewards, wait for the world to resume. Encrypted-disk password retry cooldowns use elapsed time so they can expire while paused.

**Settings → Notifications → Interface sounds** controls the retro clicks and tones for buttons, windows, login, saves, terminal commands, disk activation, and Antazon transactions. Window open and close sounds use quiet, lower-register beeps with softened high frequencies. Antazon plays a rising confirmation after an accepted order or checkout and a separate lower confirmation after a successful sale; rejected transactions play the error cue. A checkout plays one cue for the whole response, including partially accepted carts, and an empty cart is silent. Interface sounds default to enabled at 60% volume. The volume choices run from 0% to 100%; **Preview** plays the sound set at the selected volume. Minecraft's Master volume also applies. Mail and task notification sounds keep their separate controls. There are no typing, hover, or continuous background sounds.

These client preferences are stored under `preferences` in `config/antos.json`:

```json
{
  "pauseWhileOpen": false,
  "interfaceSounds": true,
  "interfaceVolume": 60
}
```

Resource packs can replace `assets/antos/sounds/ui/<name>.ogg` or override `assets/antos/sounds.json`. The sound event IDs are `antos:ui.click`, `antos:ui.open`, `antos:ui.close`, `antos:ui.minimize`, `antos:ui.maximize`, `antos:ui.login`, `antos:ui.save`, `antos:ui.command`, `antos:ui.disk`, `antos:ui.order`, `antos:ui.sale`, and `antos:ui.error`. The bundled sounds are adapted from [Shiru's ZX Spectrum beeper sound effects](https://opengameart.org/content/zx-spectrum-beeper-sound-effects), using the author's CC0 license option; source mappings and the license are included in the mod's `licenses/` directory.

Window borders focus and raise the clicked window and block clicks to apps or desktop icons behind it. Borders do not resize windows. Scrolling over a window's frame or title bar cannot scroll another window underneath it.

## Quick start

1. Copy [`examples/datapack-template`](examples/datapack-template) into your pack's data pack (or your mod's resources) and rename `examplemod` to your namespace. AntOS reads content from every namespace.
2. Put data files under `data/<namespace>/...` and translations and textures under `assets/<namespace>/...` (in a mod or resource pack).
3. Run `/reload` or restart, then check the server log. AntOS validates every reference on startup and reload, logs problems, and may skip invalid definitions.
4. Get a disk to test with:

```
/give @p antos:floppy_disk[antos:floppy_disk="examplemod:field_notes"]
```

The template's disks also have crafting recipes in `data/examplemod/recipe/`; the result is `antos:floppy_disk` with the `antos:floppy_disk` component set to the disk ID.

### File locations

Every file's ID is `<namespace>:<path>`, where `<path>` is the file path under the folder below, without `.json`.

| Content | Path |
| --- | --- |
| Archive entry | `data/<ns>/computer/entry/<path>.json` |
| Archive folder | `data/<ns>/computer/archive_folder/<path>.json` |
| Floppy disk | `data/<ns>/computer/disk/<path>.json` |
| Disk category | `data/<ns>/computer/category/<path>.json` |
| Disk/command file source | `data/<ns>/computer/disk_file/<path>.<ext>` (ID keeps the extension) |
| Program (quiz/adventure) | `data/<ns>/computer/program/<path>.json` |
| Wallpaper | `data/<ns>/computer/wallpaper/<path>.json` |
| Terminal command | `data/<ns>/computer/terminal_command/<path>.json` |
| Task or task link | `data/<ns>/computer/task/<path>.json` |
| Task category | `data/<ns>/computer/task/category/<path>.json` |
| Task group | `data/<ns>/computer/task/group/<path>.json` |
| Task reward pool | `data/<ns>/computer/task/reward_pool/<path>.json` |
| Antmail message | `data/<ns>/antmail/message/<path>.json` |
| Antmail pool | `data/<ns>/antmail/pool/<path>.json` |
| Antazon product | `data/<ns>/antazon/product/<path>.json` |
| Antazon sell rule | `data/<ns>/antazon/sell/<path>.json` |
| Antazon deal campaign | `data/<ns>/antazon/deal/<path>.json` |
| Translations | `assets/<ns>/lang/en_us.json` |
| Wallpaper image | `assets/<ns>/textures/computer/wallpaper/<name>.png` |

Because `category`, `group` and `reward_pool` are reserved folders under `task/`, tasks can't be placed in folders with those names.

## Shared concepts

### Text and translations

Titles, subtitles, and descriptions are translation keys; add them to `assets/<namespace>/lang/en_us.json`. Programs, terminal output, Antazon product names and descriptions, and Antmail bodies accept either a translation key or literal text.

Task titles, descriptions, and objective descriptions also support [markup](#text-markup).

### Previews

Many content types show an item or creature using the Archive renderer. Two options appear throughout:

- `green_tint` (default `true`) draws the preview in the green computer palette. Set `false` to show normal colors.
- `render_mob_from_spawn_egg` (default `true`) draws a spawn egg as its creature. Set `false` to show the egg item.

### Rewards

Tasks, disks, programs, terminal commands, and Antmail all share one reward format. Each reward is an object with a `type`:

| Type | Fields | Tasks | Disks, programs, commands |
| --- | --- | --- | --- |
| `item` | `item`, `count` (default `1`) | ✓ | ✓ |
| `item_pool` | `pool`, `mode`: `random` (default) or `choice` | ✓ | `random` only |
| `choice` | `options`; see [choice rewards](#choice-rewards) | ✓ | ✗ |
| `antcoins` | `amount` | ✓ | ✓ |
| `experience` | `points` (up to 1,000,000) | ✓ | ✓ |
| `experience_levels` | `levels` (up to 1,000) | ✓ | ✓ |
| `advancement` | `advancement` | ✓ | ✓ |
| `effect` | `effect`, `duration_ticks` (default `1200`, max `72000`), `amplifier` (default `0`) | ✓ | ✓ |
| `archive` | `entry` | ✓ | ✗ (use the disk's `entries`) |
| `mail` | `message` (an [Antmail message](#antmail) ID), or inline `sender`, `subject`, `body` | ✓ | ✓ |

- Rejected types are skipped with a log warning.
- A reward list allows up to 27 rows and 108 directly configured items.
- `antcoins` go to the profile's Antazon wallet. `mail` goes to the profile's Antmail address.
- A `mail` reward that names a `message` delivers it immediately, skipping its trigger, conditions, and schedule, but still respecting its `once` setting. Give such messages a `manual` trigger if they should only arrive as rewards.

```json
[
  { "type": "antcoins", "amount": 100 },
  { "type": "item", "item": "minecraft:emerald", "count": 1 },
  { "type": "effect", "effect": "minecraft:speed", "duration_ticks": 600, "amplifier": 1 },
  { "type": "mail", "message": "examplemod:gift_card_thanks" }
]
```

### Unlock requirements

Antazon products and sell rules share one set of unlock fields:

| Field | Meaning |
| --- | --- |
| `unlock_tasks` | Task IDs that must be completed. |
| `unlock_disks` | Disk IDs that must have been activated. This uses permanent history, so ejecting the disk keeps the unlock. Encrypted disks count after decryption. |
| `unlock_commands` | [Terminal command](#terminal-commands) IDs the profile must have run. A locked listing shows the command's `hint`, never the command itself. |
| `unlock_mode` | `all` (default): every listed requirement. `any`: one requirement from any list. All lists empty means unlocked. |
| `hidden_until_unlocked` | Hide the listing until it unlocks. Defaults to `false` for products. For sell rules, locked rules are hidden from Prices unless this is `false`. |
| `notify_on_unlock` | Defaults to `true`; set `false` to skip the Antazon Dispatch email for this listing. |

Locked listings show the required task, disk, and command names with their state. Terminal commands use a similar `requires` block; see [terminal commands](#terminal-commands).

### Minecraft time and real time

Several timers take either `..._minecraft_days` or `..._real_hours`; set one, not both. One Minecraft day is 24,000 game ticks (about 20 real minutes), and sleeping doesn't skip it. Real hours are aligned to UTC, so `24` changes at midnight UTC. Countdowns in Antazon show the unit the timer is set in, for example "2.4 MINECRAFT DAYS" or "3H 12M".

## Archive entries

`data/<namespace>/computer/entry/<path>.json`

```json
{
  "type": "article",
  "category": "general",
  "title": "guide.examplemod.entry.field_notes.title",
  "subtitle": "guide.examplemod.entry.field_notes.subtitle",
  "description": ["guide.examplemod.entry.field_notes.body"],
  "item": "minecraft:iron_ingot",
  "cover_item": "minecraft:book"
}
```

| Field | Notes |
| --- | --- |
| `title`, `subtitle`, `description` | Translation keys. `description` is a list of paragraphs. |
| `item`, `entity`, `recipe`, `enchantment`, `structure` | What the entry is about. Drives the large preview and locating. |
| `cover_item`, `cover_entity`, `cover_potion`, `rotation`, `render_scale` | Cover art. An entry with no `entity`, `item`, or `recipe` shows `cover_entity` as its large preview, so a page about a place can show the creature it's known for. |
| `structure_tag`, `dimension`, `search_radius` | Structure locating. |
| `locator` | A Java [locator](#archive-locators) for targets that aren't vanilla structures. Needs `dimension`. |
| `unlocked_by_default` | `true` makes the entry available on every computer without a disk. Default `false`. |
| `folder` | Optional namespaced Archive folder ID, such as `examplemod:field_research`. Omit to show the entry at the Archive root. |
| `sort_order` | Optional integer ordering inside the folder or among loose root entries. Default `0`; lower values appear first. Ties use the displayed title, then entry ID. |
| `green_tint` | Default `true`. See [previews](#previews). |
| `gallery`, `gallery_title` | A row of small previews under the text. See below. |

Entries are normally unlocked by a disk's `entries`, a task's `archive` reward, a terminal command, or FTB Quests.

### Archive folders

Folders organize Archive entries using any structure the pack author chooses: chapters, locations, research topics, mods, or something else. AntOS never assigns folders automatically based on a mod or namespace. Folders are separate from an entry's `category` and from floppy disk categories.

Define a folder at `data/<namespace>/computer/archive_folder/<path>.json`. For example, `data/examplemod/computer/archive_folder/field_research.json`:

```json
{
  "title": "guide.examplemod.archive_folder.field_research",
  "icon_item": "minecraft:spyglass",
  "sort_order": 10
}
```

| Field | Notes |
| --- | --- |
| `title` | Translation key for the folder name. Use `literal:Field Research` for a name that does not need a resource-pack translation. Defaults to the folder ID's path. |
| `icon_item` | Item ID displayed on the folder. Default `minecraft:book`. |
| `green_tint` | Default `true`. Set `false` to show the folder's icon in its normal colors. Each folder and subfolder controls its own icon tint; this does not change the tint of entries inside it. |
| `parent` | Optional namespaced folder ID. Omit for a top-level folder; set it to a top-level folder's ID to create a subfolder. One level of subfolders is supported. |
| `sort_order` | Integer ordering among folders, from `-100000` to `100000`. Default `0`; lower values appear first. Ties use the displayed folder title, then folder ID. Entry `sort_order` uses the same range. |

Assign an entry to the folder with its full ID:

```json
{
  "type": "article",
  "folder": "examplemod:field_research",
  "sort_order": 20,
  "category": "creatures",
  "title": "guide.examplemod.entry.field_notes.title",
  "description": ["guide.examplemod.entry.field_notes.body"],
  "cover_item": "minecraft:book"
}
```

To add a subfolder, define another folder such as `data/examplemod/computer/archive_folder/creatures.json`:

```json
{
  "title": "literal:Creatures",
  "parent": "examplemod:field_research",
  "icon_item": "minecraft:spider_eye",
  "sort_order": 10
}
```

An entry with `"folder": "examplemod:creatures"` then appears under `Archive > Field Research > Creatures`. Folder IDs and parent relationships are explicit; a slash in a folder's JSON path does not create a parent relationship automatically.

The Archive root shows top-level folders first, followed by loose entries. Opening a folder shows its visible subfolders first, followed by entries assigned directly to it. Each group uses `sort_order`, displayed title, and ID ordering. A parent may contain both subfolders and its own entries. Folder counts include all unlocked entries beneath that folder, including entries in its subfolders. Folder artwork keeps a recognizable folder silhouette and renders its configured `icon_item` centered inside the folder without a background box, including modded items and resource-pack item textures. Entry covers retain their item, entity, potion, enchantment, and recipe previews, with a document icon when no preview is available.

Archive opens in list view in a wider default window. The toolbar switches between list and gallery views; maximizing preserves the selected view. Gallery columns adapt to the available width. The chosen view remains available throughout the current computer session, and switching views retains the focused entry where possible.

Each breadcrumb segment is clickable, so `Archive > Field Research > Creatures` can return directly to either ancestor. Long paths collapse earlier segments into a menu while retaining the current location. Hovering truncated titles or paths reveals their full text. Back returns to the previous view, including searches, opened entries, and breadcrumb jumps, restoring its search, focus, view mode, and scroll position. Up follows the folder hierarchy; from an entry it opens that entry's assigned folder, or the root for a loose entry. Escape leaves an entry, unfocuses search, or moves up one folder level, in that order.

Use `Ctrl+F` to focus search, `Tab`/`Shift+Tab` to move between controls and content, arrow keys to focus entries, and `Enter` to open them. `Home` and `End` select the first or last entry. Gallery navigation follows the visual rows and columns. While reading, arrow keys, `Page Up`/`Page Down`, `Home`, and `End` scroll the article. The article view has its own scrollbar, wrapping titles and subtitles, and separated text and preview sections.

Searching at the root searches all unlocked entries. Searching inside a parent searches that folder and its subfolders; searching inside a subfolder searches only that subfolder. Results show their full folder path. Empty folders stay hidden until an entry anywhere beneath them becomes available, so a parent with no direct entries can still appear when a subfolder has an unlocked entry. Folders do not unlock entries: disks, tasks, and other existing unlock sources continue to control availability.

Each entry can belong to one folder. The maximum hierarchy is `Archive > Folder > Subfolder`. Existing folders without `parent` stay at the root, and existing entries without `folder` stay loose at the root. Entries referencing missing folders also appear at the root, with a warning in the server log. Missing or invalid parents, self-parenting, cycles, and excessive nesting promote the affected folders to the root with warnings; their assigned entries are retained. Validation uses the original parent graph, so descendants of a broken parent chain are also promoted, independently of file load order. Folder definitions, parent relationships, and entry assignments sync from the server with the rest of the Archive data.

### Galleries

`gallery` shows a row of small previews under the text, such as every creature in a biome:

```json
{
  "type": "article",
  "title": "guide.examplemod.entry.glow_caves.title",
  "description": ["guide.examplemod.entry.glow_caves.body"],
  "cover_entity": "examplemod:glow_moth",
  "gallery": ["examplemod:glow_moth", {"entity": "examplemod:cave_crawler"}, {"item": "examplemod:glow_shard", "name": "guide.examplemod.entry.glow_caves.shard"}],
  "gallery_title": "guide.examplemod.entry.glow_caves.gallery"
}
```

- Each element is an ID string, or an object with one of `entity`, `item`, or `id`, plus an optional `name` translation key.
- A plain string or `id` is an entity if that entity type exists, otherwise an item.
- Without `name`, the preview is labeled with the entity or item name.
- Up to 32 elements are kept.
- A preview that matches another entry the player can open (by its `entity`, `item`, `cover_entity`, or `cover_item`) gets a border and opens that entry when clicked.
- `gallery_title` is the label above the row; it defaults to `GALLERY`.

To make a structure entry count as "visited" for a task, use a vanilla advancement with the `minecraft:location` trigger and a `structures` predicate, and reference it in an `advancement` objective. The template has a stronghold example. Locating a structure in the Archive doesn't count as visiting it.

## Floppy disks

`data/<namespace>/computer/disk/<path>.json`

Inserting a disk into a computer or phone activates it for the signed-in profile and grants its contents.

```json
{
  "category": "general",
  "title": "guide.examplemod.disk.field_notes.title",
  "description": "guide.examplemod.disk.field_notes.description",
  "hidden": false,
  "entries": ["examplemod:field_notes"],
  "wallpaper": "examplemod:meadow",
  "tasks": ["examplemod:field_basics"]
}
```

| Field | Notes |
| --- | --- |
| `category` | A [disk category](#disk-categories) ID. Sets the item texture and Library grouping. |
| `title` | Translation key. |
| `description` | Optional translation key shown in the Library and the item tooltip. |
| `hidden` | `true` for secret disks that only appear in the Library after collection. Hidden disks never count toward collection totals, even after discovery. Default `false`. |
| `entries` | Archive entries to unlock. |
| `wallpaper` | A [wallpaper](#wallpapers) to unlock. |
| `tasks` | Tasks to grant. |
| `programs` | [Programs](#programs-quizzes-and-adventures) to install. |
| `files` | [Workspace files](#workspace-files) to deliver. |
| `subscribe` | [Newsletters](#newsletters) to subscribe to. |
| `rewards` | One-time [rewards](#disk-rewards). |
| `consumable` | Make the disk redeemable repeatedly, like a gift card. See [consumable disks](#consumable-disks). |
| `encryption` | Require a password first. See [encrypted disks](#encrypted-disks). |

Disks can also unlock Antazon listings (via `unlock_disks`) and complete [`disk_installed` objectives](#objectives).

What's permanent and what isn't:

- Archive entries, tasks, wallpapers, files, rewards, and Antazon unlocks are permanent once the disk is activated. Ejecting a disk never removes them.
- Programs and native games need the disk to stay installed.
- If a disk definition is removed, collected copies show as unknown disks and can still be ejected. They don't count toward collection totals.

### Disk rewards

`rewards` uses the shared [reward format](#rewards), without the interactive types. Item and pool rewards arrive in a reward crate near the player. A desktop popup summarizes the payout, and the Library keeps a receipt of what was delivered.

- Each profile claims a disk's rewards once, whether or not the disk stays installed. Profiles on the same team each claim their own.
- Already-installed disks with an unclaimed reward list pay it once on the next sign-in.
- A disk without rewards doesn't reserve a claim, so rewards added later are still paid once. After a payout, editing the list doesn't pay again.
- An ejected disk with newly added rewards must be reinstalled to claim them.

### Consumable disks

Set `"consumable": true` for a disk that pays out every time, such as a gift card:

```json
{
  "category": "examplemod:shop",
  "title": "guide.examplemod.disk.gift_card.title",
  "consumable": true,
  "rewards": [
    { "type": "antcoins", "amount": 100 },
    { "type": "item", "item": "minecraft:emerald", "count": 1 },
    { "type": "mail", "message": "examplemod:gift_card_thanks" }
  ]
}
```

- Every copy inserted is used up and pays `rewards` again, instead of being refused as already installed. A phone that isn't signed in queues each copy and pays for each when it syncs.
- It's collected once in the Library. Its other contents (entries, tasks, wallpaper, files, programs, newsletters, Antazon unlocks) are granted only the first time.
- An encrypted consumable disk pays again only after decryption.
- A `mail` reward follows that message's own `once` rule.
- Ejecting a consumable disk returns a copy marked redeemed (custom data `AntOSRedeemed`, tooltip **Redeemed**). A redeemed copy never pays. Inserted into a profile that already has the disk, it stays in hand with **Already redeemed**; into a profile without the disk, it's collected without a payout. A phone that isn't signed in won't queue it.

### Workspace files

`files` copies text or Paint files into the profile's workspace:

```json
"files": [
  { "path": "Disks/Lab/readme.txt", "source": "examplemod:lab/readme.txt" },
  { "path": "Disks/Lab/map.paint", "source": "examplemod:lab/map.json" },
  { "path": "secret.txt", "source": "examplemod:lab/secret.txt" }
]
```

- Sources are UTF-8 files under `data/<namespace>/computer/disk_file/`, and their IDs keep the extension. `examplemod:lab/readme.txt`, `examplemod:disk_file/lab/readme.txt`, `examplemod:disk_files/lab/readme.txt`, and `examplemod:computer/disk_file/lab/readme.txt` all point at the same file.
- A filename without a folder goes under `Disks/<disk title>/` (unsafe characters replaced). Titles use the server's language, so use an explicit folder for consistent names on dedicated servers. Parent folders are created automatically.
- `{player}` in text is replaced with the installing or decrypting player's name.
- Each disk/path is delivered once per profile. Existing files are left alone and count as delivered. Edited, moved, or deleted files aren't copied again, even if the source changes. Missing sources or failed copies retry at sign-in or reinsertion.
- Limits: 32 file entries per disk; 16 KiB per file, 64 KiB total, 128 nodes, and 64 characters per path per workspace. System paths and `..` are rejected.
- The Library links delivered files to the Files app.

**Paint files.** Paint's extension is `.antpaint`; a `.paint` destination is converted automatically. The source may be the Base64 bytes Paint itself stores (`AntPaintFileCodec`), or JSON with `pixels`: exactly 24 strings of exactly 32 `0`/`1` characters, where `1` is painted. See `lab/map.json` in the template.

### Encrypted disks

```json
"encryption": {
  "password": "redstone",
  "password_any": ["red stone"],
  "hint": "guide.examplemod.disk.lab_secret.hint",
  "case_sensitive": false
}
```

- An encrypted disk is collected and installed right away, but grants nothing (entries, tasks, wallpaper, files, rewards, programs, subscriptions, Antazon unlocks) until decrypted.
- Players decrypt it from the Library (which shows the lock, `hint`, and a masked field) or the Terminal: `decrypt examplemod:lab_secret` prompts for a masked password, and `decrypt <disk> <password>` works in one line without echoing the password. `cancel` or Escape cancels.
- `password` and `password_any` can be combined: up to eight non-empty passwords of at most 128 characters. `case_sensitive` defaults to `false`.
- Passwords, quiz answers, and adventure graphs never reach the client, even in singleplayer.
- Wrong guesses share a per-profile throttle that grows from one to ten seconds.
- Decryption is permanent and survives ejecting and reinserting.
- Profiles saved before encryption was added keep their existing access to those disks.

### Newsletters

A disk can permanently subscribe its profile to Antmail pools or individual random messages:

```json
"subscribe": ["examplemod:research_news", "examplemod:lab_tip"]
```

See [subscription-only mail](#subscription-only-mail). Ejecting never unsubscribes.

## Programs: quizzes and adventures

`data/<namespace>/computer/program/<path>.json`

List program IDs in a disk's `programs` array (or a terminal command's). Programs appear in Games next to native games and in the disk's Library page. Disk programs need the disk to stay installed. Players use numbered choices or the mouse, scroll with the wheel or arrow keys, and press Escape to leave. Text, titles, questions, and choices accept translation keys or literal text.

### Quiz

```json
{
  "type": "quiz",
  "title": "program.examplemod.lab_quiz.title",
  "questions_per_run": 1,
  "questions": [
    { "question": "Which tool mines stone?", "answers": ["Shovel", "Pickaxe", "Axe"], "correct": 1, "item": "minecraft:iron_pickaxe" }
  ],
  "daily_reward": [{ "type": "antcoins", "amount": 10 }],
  "reward_on": "perfect"
}
```

| Field | Notes |
| --- | --- |
| `questions` | Up to 128 questions with 2–8 `answers` each. `correct` is the zero-based index before shuffling. Questions and answers are shuffled on the server each run. |
| `item` / `entity` (per question) | Optional preview. |
| `green_tint` | On the quiz or a single question. Default `true`. |
| `questions_per_run` | Defaults to the whole pool, capped to its size. |
| `reward_on` | `perfect` (default) or `complete`. |
| `daily_reward` (or `rewards`) | [Rewards](#rewards), non-interactive types only. |
| `reward_limit` | `daily` (default): at most once per profile per overworld Minecraft day; rolling time back can't repay a day. `once`: only the first time, for quizzes whose questions never change. |

### Adventure

```json
{
  "type": "adventure",
  "title": "Lab Escape",
  "start": "intro",
  "nodes": {
    "intro": { "text": "A terminal guards the exit.", "choices": [{ "text": "Read the terminal", "goto": "win" }] },
    "win": { "text": "The door opens.", "ending": "escaped", "password": "redstone", "rewards": [{ "type": "antcoins", "amount": 50 }] }
  }
}
```

- Nodes support `set_flags` and `clear_flags`; choices support `requires_flag` and `requires_no_flag`. Flags reset every run.
- An ending node has an `ending` ID and no choices. Its `rewards` pay once per profile per program and ending.
- An ending `password` is shown only when the player reaches it, which is useful for unlocking an [encrypted disk](#encrypted-disks).
- Limits: 128 nodes, eight choices per node, 4,096 steps per run.
- The template's `lab_escape` shows finding, keeping, and dropping a key, two endings, and a password for `lab_secret`.

Runs are temporary and reset on server restart or reload; ending history and reward claims are saved. The server checks run tokens and sequence numbers, so stale clicks can't advance a run. Use [`program_ending` objectives](#objectives) to make tasks out of endings.

## Tasks

`data/<namespace>/computer/task/<path>.json`

Tasks are quests shown as a dependency map in the Tasks app (disabled by default; see [configuration](#configuration)). Progress belongs to the signed-in profile, or to the profile's [team](#teams).

```json
{
  "title": "task.examplemod.field_basics.title",
  "description": "task.examplemod.field_basics.description",
  "category": "examplemod:field_research",
  "availability": "disk",
  "objectives": [
    {
      "id": "iron_samples",
      "type": "item",
      "target": "minecraft:iron_ingot",
      "description": "task.examplemod.field_basics.iron_samples",
      "count": 4,
      "notify_player": true
    }
  ],
  "rewards": [{ "type": "antcoins", "amount": 100 }]
}
```

### Task fields

| Field | Notes |
| --- | --- |
| `title`, `description` | Translation keys; support [markup](#text-markup). |
| `category` | The map tab. Its title uses `task.category.<namespace>.<path>`, or a [category file](#categories-and-groups). |
| `availability` | `disk` (default): the task starts when a disk, terminal command, reward, or command grants it. `automatic`: it starts on its own once its prerequisites are complete. |
| `objectives` | See [objectives](#objectives). |
| `rewards` | Claimed in the Tasks app. See [task rewards](#task-rewards). |
| `requires` | Every listed task must be complete. |
| `requires_any` | At least one listed task must be complete (on top of `requires`). Shown under ANY ONE OF. |
| `hide_until_dependencies_complete`, `invisible_until_completed` | Visibility. |
| `icon` / `icon_entity` | Map icon. Default: the first `item` objective, then the first kill statistic's creature, then the first item reward, then paper. Disk objectives fall back to the category's floppy texture. |
| `green_tint`, `render_mob_from_spawn_egg` | For the icon and detail-panel icons. See [previews](#previews). |
| `position`, `shape`, `size`, `icon_scale`, `connects`, `line_axis`, `line_bend`, `sort_order` | See [map layout](#map-layout). |

### Objectives

Every objective needs `id`, `type`, and `description`, and usually `target`.

| Common field | Default | Notes |
| --- | --- | --- |
| `count` | `1` | |
| `optional` | `false` | `true` lets the task complete without it. |
| `notify_player` | `true` | `false` suppresses the action-bar message and chime. |
| `sticky` | `true` | Item objectives only; see below. |
| `consume` | `false` | Item objectives only; see below. |

| Type | `target` | Notes |
| --- | --- | --- |
| `item` | Item ID | Checks the viewing player's inventory. |
| `item_tag` | Item tag, no `#` | `tag_mode`: `any` (default) needs `count` items total across the tag; `all` needs `count` of each tag member. Tags are limited to 128 members. |
| `advancement` | Advancement ID | |
| `stat` | Stat target | Add `stat_type` such as `item_crafted`, `block_mined`, or `entity_killed`. Lifetime player totals. |
| `mail_read` | Antmail message ID | |
| `archive_viewed` | Archive entry ID | |
| `disk_installed` | Disk ID, or use `target_category` + `count` | Uses permanent history, so it completes retroactively and ejecting doesn't reduce it. Encrypted disks count after decryption. A single disk counts once. |
| `program_ending` | Program ID | With `ending`, that ending. Without it, `count` distinct endings. Uses permanent history. |
| `terminal_command` | Terminal command ID | Completes once the profile has run it, even before the task was available. |
| `ftbquests:quest_completed`, `ftbquests:task_completed` | FTB hex object ID | With [FTB Quests](#ftb-quests). |
| Your own | | Registered from Java; see [custom objectives](#custom-objectives). |

```json
[
  { "id": "collect_planks", "type": "item_tag", "target": "minecraft:planks", "description": "task.examplemod.collect_planks", "count": 16 },
  { "id": "lab", "type": "disk_installed", "target": "examplemod:lab_programs", "description": "Install the lab disk" },
  { "id": "set", "type": "disk_installed", "target_category": "examplemod:research", "count": 2, "description": "Activate two research disks" },
  { "id": "escape", "type": "program_ending", "target": "examplemod:lab_escape", "ending": "escaped", "description": "Escape the lab" }
]
```

**Sticky progress.** By default AntOS keeps the highest progress seen, so separate objectives (or members of an `all` tag) can be met at different times. For an exact item or an `any` tag, the full `count` must still be held at once; it isn't cumulative pickup counting. `sticky: false` tracks live inventory until the objective is met.

**Consuming items.** `consume: true` removes the required items when the objective is met. Exact-item and `any` objectives take the whole requirement together. An `all` tag takes each member's share as that member is met, so the full tag never has to be held at once.

The template's `disk_collector` and `lab_escape` tasks show disk and program objectives.

In multiplayer, inventory, advancement, and statistic objectives check the player viewing the Tasks app, while task progress itself is shared by the profile.

### Task rewards

Completing a task makes its [rewards](#rewards) claimable in the Tasks app; nothing is delivered automatically except `archive` rewards, which unlock on completion. Each profile claims a task's rewards once, including team members. Item rewards drop near the player.

#### Reward pools

`data/<namespace>/computer/task/reward_pool/<path>.json`

```json
{
  "entries": [
    { "item": "minecraft:iron_ingot", "weight": 5, "count": 3 },
    { "pool": "examplemod:rare_supplies", "weight": 1, "count": 1 }
  ]
}
```

- Each entry names an `item` or another `pool`. `weight` defaults to `1` and must be positive. `count` defaults to `1`: the item quantity, or the number of draws from a nested pool.
- Up to 64 entries, counts up to 108, nesting up to eight levels. Missing references and cycles are rejected.
- Use from a reward with `{ "type": "item_pool", "pool": "examplemod:field_supplies", "mode": "random" }`. Random pools roll on the server at claim time, and the preview cycles through possible items.
- `"mode": "choice"` lets the player pick one reachable outcome before claiming; weights don't limit the choice. For nested entries, the outcome follows one path, and the nested entry's count multiplies the item's quantity.

#### Choice rewards

`type: "choice"` lets the player pick one reward package:

```json
{
  "type": "choice",
  "options": [
    { "id": "coins", "title": "task.examplemod.field_basics.reward.coins", "rewards": [{ "type": "antcoins", "amount": 100 }] },
    { "id": "supplies", "title": "task.examplemod.field_basics.reward.supplies", "rewards": [{ "type": "item_pool", "pool": "examplemod:field_supplies", "mode": "random" }] },
    { "id": "tools", "title": "task.examplemod.field_basics.reward.tools", "rewards": [
      { "type": "item", "item": "minecraft:iron_pickaxe", "count": 1 },
      { "type": "item", "item": "minecraft:bread", "count": 8 }
    ] }
  ]
}
```

- 2–6 options with unique `id`s; each has a translatable `title` and 1–8 `rewards`.
- Options can't contain another `choice`, an `archive` reward, or an `item_pool` with `mode: "choice"`.
- The server validates the selected option at claim time.

### Map layout

| Field | Default | Notes |
| --- | --- | --- |
| `position` | automatic | `{"x": 0, "y": 0}`; both coordinates needed, each clamped to −128…128. Automatic placement leaves room for large nodes; explicit positions are kept as-is, so space them yourself. |
| `shape` | `square` | `square`, `diamond`, `hexagon`, `circle`, `pentagon`, `octagon`, `triangle` (case-insensitive). |
| `size` | `1.0` | Scales the whole node, clamped 0.5–3.0. Resizing keeps the center fixed. |
| `icon_scale` | `1.0` | Scales the icon inside the node, clamped 0.25–2.0. Above 1.0 the icon can overflow the border. |
| `connects` | | Task or link IDs in the same category to draw a line to, without a dependency. Lights up when either end is complete. |
| `line_axis` | `auto` | `vertical` or `horizontal`: which sides this task's lines (its `connects` and its prerequisite lines) leave from. `auto` picks vertical when nodes are farther apart vertically. Nodes in the same row or column always get a straight line. |
| `line_bend` | `0.5` | Where the single bend sits, 0.0–1.0. For prerequisite lines `0.0` is at the prerequisite and `1.0` at this task; for `connects`, `0.0` is this task. Give farther children a smaller value so a fan-out shares one trunk. |

Unknown shapes and invalid or non-finite numbers fall back to the defaults. Shapes, sizes, and scales affect map nodes only; sidebar and detail icons don't change.

### Link nodes

A task file with `"type": "link"` and `link_category` (or `link_group`) becomes a link node that opens another tab, like an FTB Quests quest link:

```json
{
  "type": "link",
  "category": "examplemod:start_here",
  "link_category": "examplemod:meadow_research",
  "position": {"x": 2, "y": 0},
  "shape": "circle"
}
```

- A link to a group expands it and opens its first visible category.
- Links have no objectives or rewards, never complete, and aren't counted in progress or search.
- They support `category`, `position`, `sort_order`, `shape`, `size`, `icon_scale`, `connects`, and `requires`. `title`, `icon`, `icon_entity`, `green_tint`, and `render_mob_from_spawn_egg` default to the target tab's values.
- A link only shows when its target has a visible task and everything in `requires` is complete. On a link, `requires` gates visibility only and draws no lines.

### Categories and groups

Category files (`task/category/<path>.json`) are optional and set a tab's title, icon, order, and group. The file's ID must match the tasks' `category` value.

```json
{
  "title": "task.category.examplemod.meadow_research",
  "icon": "minecraft:compass",
  "green_tint": true,
  "sort_order": 10,
  "group": "examplemod:fieldwork"
}
```

Group files (`task/group/<path>.json`) add a collapsible sidebar header with the same `title`, `icon`, `icon_entity`, `green_tint`, `render_mob_from_spawn_egg`, and `sort_order` fields, plus `collapsed` for its default state.

- Use `icon_entity` instead of `icon` for a creature.
- Categories and groups share one ordering by `sort_order`, lowest first; categories without a file come after, alphabetically. A group's categories are sorted by their own `sort_order`.
- Groups without visible categories are hidden. A category naming a missing group shows ungrouped.
- Titles can include `{player}`.
- Each player's expanded/collapsed choice is saved in their client config.

### Text markup

Task titles, descriptions, and objective descriptions support markup after translation. Markup is stripped from search, hover titles, notifications, and other plain-text places.

- **Colors:** `<green>`, `<pale>`, `<dim>`, `<dark_green>`, `<lime>`, `<red>`, `<dark_red>`, `<orange>`, `<gold>`, `<yellow>`, `<aqua>`, `<dark_aqua>`, `<blue>`, `<dark_blue>`, `<purple>`, `<dark_purple>`, `<pink>`, `<light_purple>`, `<white>`, `<gray>`, `<dark_gray>`, `<black>`, or `<#RRGGBB>`.
- **Formatting:** `<b>`, `<i>`, `<u>`, `<s>`.
- **Closing:** `</>` closes the latest tag, `</b>` or `</red>` closes that tag, `<reset>` clears everything. Styles carry across wrapped lines.
- **Inline icons:** `{item:minecraft:diamond}`, `{entity:minecraft:zombie}`, `{potion:minecraft:swiftness}`, `{enchantment:minecraft:sharpness}`. Options: `green:true|false` (defaults to the task's `green_tint`) and `size:N` (6–48, default 10 = text height; larger icons make the line taller). Example: `{entity:minecraft:creeper, green:false, size:16}`.
- **Escapes:** `<<` and `{{` print a literal `<` or `{`. Unknown tags and icons are shown as plain text.

### Teams

Players can create or join one AntOS team from the **TEAM** button in the Tasks app. Team task progress and Archive unlocks are shared immediately. Joining merges progress; leaving or disbanding keeps what each member earned. Every member claims rewards separately. Invitations arrive as Antmail and are accepted in the Tasks app. Files, mailboxes, disks, devices, and credentials stay private.

## Antmail

`data/<namespace>/antmail/message/<path>.json`

Every profile automatically gets `<username>@antmail.com`. Data-driven messages are sent to profiles by triggers, rewards, or commands.

```json
{
  "sender": "fieldoffice",
  "sender_display": "Field Office",
  "sender_avatar_item": "minecraft:compass",
  "subject": "A note from the field",
  "body": "Welcome to the colony network.",
  "trigger": { "type": "random", "value": "0.05" }
}
```

### Triggers

| `trigger.type` | `trigger.value` |
| --- | --- |
| `random` | Daily chance from `0` to `1`, checked once per in-game day per profile. Or use `trigger.pool` (and optional `trigger.weight`) instead; see [pools](#pools). |
| `advancement` | Advancement ID. |
| `mail_read` | Another data-driven message ID. |
| `task_complete` | Task ID. |
| `manual` | Never fires on its own; sent only as a reward or by command. |

### Delivery options

| Field | Notes |
| --- | --- |
| `once` | Defaults to `true` (`false` for `random`). `false` allows sending again. |
| `delay` | Delay in Minecraft days. |
| `delay_minutes` | Additional delay in whole in-game minutes. |
| `delay_seconds` | Additional delay in whole in-game seconds; one second is 20 ticks. The delay fields add together and are stored against world time, so they survive restarts and pause while the world is closed. Conditions and delivery windows are still checked when the message becomes due. |
| `conditions` | `advancements`, `not_advancements`, `mail_read`, `tasks_complete`, `dimension`. Each takes one ID or a list. |
| `deliver_window` | Minecraft-time window for arrival. Messages wait in a queue until conditions and window allow. |
| `antcoins` | Positive whole number credited on delivery, shown as a transfer line. Repeating mail pays each time. Nothing is paid or shown while Antazon is disabled. |
| `delivery` | Grants item stacks when the message is read (default) or arrives (`"on": "deliver"`). Separate from attachments. |

### Presentation

| Field | Notes |
| --- | --- |
| `sender_display` | Display name. |
| `sender_avatar_item` / `sender_avatar_entity` | Item (block items work) or living entity portrait. Reuse the same one for a sender's messages. Without one, initials are shown. |
| `style` | `corrupted` for an animated corrupted look. The body can use `[glitch]...[/glitch]` and `[redact]...[/redact]`. |
| `notification` | Chime by default; `silent`, or a sound event ID. |
| `delete_after_read` | Removes the message after it's read. |

Bodies can embed Archive-style previews with `@item:<id>`, `@entity:<id>`, `@enchantment:<id>`, `@potion:<id>`, or `@recipe:<id>`. The marker is removed from the text and the preview is attached below. This works in data-driven mail, `mail` rewards, and player-written mail. IDs without `@` stay plain text.

### Pools

`data/<namespace>/antmail/pool/<path>.json`

```json
{
  "chance_per_day": 0.35,
  "max_per_day": 1
}
```

Point random messages at it with `"trigger": { "type": "random", "pool": "examplemod:field_rumors", "weight": 2 }`. A pool draws unseen eligible messages by weight for each profile, then cycles once all have been seen.

### Subscription-only mail

Add `"subscription_only": true` to a pool file, or to a single message's random `trigger`. Those messages are only eligible for profiles subscribed to the message or its pool through a disk's or terminal command's `subscribe`. Chances, weights, schedules, conditions, and `once` still apply. Without the flag, random mail is open to everyone. Rewards and commands bypass trigger eligibility. The template includes a restricted pool and a standalone subscribed message.

## Antazon

Antazon is the AntCoin shop and is **disabled by default** (see [configuration](#configuration)). It supports products, selling items for AntCoins, unlocks, shared stock, restocking, player limits, cooldowns, seeded and verified player reviews, deal campaigns, a cart, wishlists, order history, and item/mob previews. Purchases are final. Antmail can share product links and wishlists.

### Products

`data/<namespace>/antazon/product/<path>.json`

```json
{
  "name": "Reinforced Nest Bundle",
  "description": "A supply bundle for expanding a busy nest.",
  "category": "building_supplies",
  "tags": ["building", "starter"],
  "quantity": 1,
  "payments": [
    { "type": "antcoins", "resource": "antcoins", "amount": 56 }
  ],
  "unlock_tasks": ["examplemod:field_basics"],
  "unlock_mode": "all",
  "hidden_until_unlocked": false,
  "availability": {
    "server_stock": 20,
    "restock_after_minecraft_days": 3,
    "cooldown_minecraft_days": 1,
    "player_limit": 2,
    "player_limit_reset": "minecraft_week"
  },
  "delivery": "falling_chest",
  "rewards": [
    { "item": "minecraft:iron_ingot", "count": 16 },
    { "item": "minecraft:oak_planks", "count": 32 }
  ],
  "reviews": [
    {
      "id": "queen_ant_001",
      "author": "Queen Ant",
      "title": "Reliable starter supplies",
      "body": "Arrived quickly and held up through the first expansion.",
      "rating": 5,
      "badge": "NEST VERIFIED"
    }
  ]
}
```

| Field | Notes |
| --- | --- |
| `name`, `description` | Literal text or translation keys. `{item}` is replaced for [variants](#variants) and [item pools](#item-pools). |
| `category`, `tags` | Catalog filters; also used by [deals](#deals). |
| `quantity` | Required. How many reward sets one purchase delivers. |
| `payments` | Required. Tried in order; the first affordable one is used. `{ "type": "antcoins", "resource": "antcoins", "amount": N }`, or `{ "type": "item", "resource": "#namespace:tag", ... }` to take any items in a tag. |
| `rewards` | One reward set: `{ "item": ..., "count": ... }` rows. Required unless `variants` or `item_pool` supplies the item. |
| `availability.server_stock` | Shared server stock; `0` = unlimited. |
| `availability.restock_after_minecraft_days` / `restock_after_real_hours` | Restock timer. "RESTOCKS IN" shows only while stock is low. |
| `availability.cooldown_minecraft_days` | Per-player cooldown. |
| `availability.player_limit`, `player_limit_reset` | Per-player cap; reset `none`, `minecraft_day`, or `minecraft_week`. |
| `delivery`, `delivery_location` | See below. |
| `unlock_*`, `hidden_until_unlocked`, `notify_on_unlock` | See [unlock requirements](#unlock-requirements). |
| `thumbnail`, `gallery` | `{ "item": ... }` or `{ "entity": ... }` for the catalog thumbnail and product page. |
| `green_tint`, `render_mob_from_spawn_egg` | See [previews](#previews). |
| `reviews` | Seeded reviews. Players can add one verified review per delivered purchase. |

**Delivery.**

- `falling_chest` (default): a chest falls near the buyer.
- `direct`: items go straight to the inventory, and overflow drops beside the player.
- `falling_chest_location`: a chest falls at `delivery_location` (`{ "x": 120, "y": 64, "z": -240 }`) in the buyer's current dimension. The chunk must be loaded and inside the world border.

Purchases never need a linked container.

Product files no longer accept a `deal` block; one that has it logs a warning and the block is ignored. Use [deal campaigns](#deals).

### Variants

`variants` sells one listing whose item comes from an item tag:

```json
"variants": { "tag": "examplemod:shop_variants", "mode": "choose" }
```

- `all`: every tagged item, `quantity` of each.
- `random`: one item picked on the server at checkout.
- `choose`: the player picks on the product page. The preview and `{item}` in the name update, and each choice is its own cart line.
- Variants share the product's stock, limits, payments, reviews, and deals. They can't be combined with `item_pool`.

### Item pools

`item_pool` turns one product into one listing per item, optionally rotating:

```json
{
  "name": "Hat of the Day: {item}",
  "description": "Today's hat. Tomorrow's will be different.",
  "category": "cosmetics",
  "quantity": 1,
  "payments": [{ "type": "antcoins", "resource": "antcoins", "amount": 100 }],
  "item_pool": {
    "tag": "examplemod:shop_hats",
    "count": 1,
    "mode": "rotate",
    "rotation": { "picks": 1, "every_minecraft_days": 1, "order": "shuffled" }
  }
}
```

- `items` lists item IDs; `tag` or `tags` adds tagged items. Each listing's ID is `<product id>/<item namespace>/<item path>` and delivers item × `count` plus any fixed `rewards`.
- `server_stock`, `player_limit`, and `cooldown_minecraft_days` apply to the whole pool, so a limit of 1 per day means one item from the pool per day. Wishlists, orders, and reviews are per listing.
- `mode: "all"` lists every item. `mode: "rotate"` lists `rotation.picks` items and changes them every `rotation.every_minecraft_days` (default `1`) or `rotation.every_real_hours` (see [time](#minecraft-time-and-real-time)). The page shows a "CHANGES IN" countdown.
- `rotation.order`: `shuffled` (default; a seeded order that shows every item once before repeating, the same for all players) or `sequential` (item-ID order).
- Unselected listings are hidden and can't be bought; they're removed from carts when the rotation changes. The same happens to cart lines for removed or disabled products.
- Thumbnail and gallery default to the listing's item.

### Selling

`data/<namespace>/antazon/sell/<path>.json`

```json
{
  "tag": "c:crops",
  "value": 1,
  "limit": 256,
  "limit_reset": "minecraft_day",
  "unlock_tasks": ["examplemod:field_basics"],
  "unlock_mode": "all",
  "hidden_until_unlocked": false
}
```

| Field | Notes |
| --- | --- |
| `item`, `items`, `tag`, `tags` | What the rule buys. A rule naming an item directly overrides a tag rule for that item. |
| `value` | AntCoins per item. |
| `limit` | Per-profile cap per reset period; omit or `0` for none. Tag rules share one limit across the tag; exact-item rules have one per item. |
| `limit_reset` | `none` (default), `minecraft_day`, `minecraft_week`. |
| `unlock_*`, `hidden_until_unlocked`, `notify_on_unlock` | See [unlock requirements](#unlock-requirements). Locked items stay in the container. |
| `green_tint`, `render_mob_from_spawn_egg` | For Sell and Prices previews. |

Players put items in a [shipping container](#shipping-containers) near a computer, link it once, and ship it from the Sell tab. The container is consumed. Over-limit items are returned beside the container and only accepted items are paid for. Sell and Prices show remaining allowance, and the manifest shows accepted and returned counts.

**Selling floppy disks.** A rule for `antos:floppy_disk` matches every labeled disk, since normal item rules match the item ID across all component variants. This lets duplicate disks be sold. Per-disk pricing isn't supported. See the template's `floppy_disks` rule, and its `gift_card`, `broker_license`, and `research_catalog` disks and products for disk-gated listings.

Item tags are accepted anywhere Antazon takes items, and are resolved the first time the shop is used after a reload.

### Deals

`data/<namespace>/antazon/deal/<path>.json`

A deal campaign picks products each period and discounts them. Products don't opt in, so new products matching a campaign join automatically.

```json
{
  "label": "examplemod.deal.daily",
  "picks": 4,
  "every_real_hours": 24,
  "order": "shuffled",
  "discount_percent": { "min": 25, "max": 35 },
  "candidates": {
    "categories": ["materials", "gear", "food"],
    "exclude_tags": ["no_deals"],
    "max_price": 499
  },
  "max_per_category": 1,
  "deal_limit": 8,
  "priority": 10
}
```

| Field | Default | Notes |
| --- | --- | --- |
| `label` | `Deal` | Shown on the product page; accepts a translation key. |
| `enabled` | `true` | `false` turns the campaign off. |
| `picks` | `1` | Products on sale at once. |
| `every_minecraft_days` / `every_real_hours` | `1` Minecraft day | Period length; set one. |
| `order` | `shuffled` | Seeded order through every candidate before repeats; or `sequential` (product-ID order). |
| `discount_percent` | required | A number, or `{ "min", "max" }` rolled per pick per period. 1–90%; prices never go below 1. |
| `candidates` | all products | `products`, `categories`, `tags` (any match includes). `exclude_products`, `exclude_categories`, `exclude_tags` remove. `min_price` / `max_price` filter on the first payment. Categories and tags are case-insensitive. |
| `max_per_category` | | Caps picks from one category per period. |
| `deal_limit` | `0` (none) | Deal-price purchases per profile per period; beyond that, regular price. |
| `priority` | `0` | Higher numbers pick first. A product is in at most one deal at a time. |

- Each profile only gets deals on products it has unlocked. Locked products are skipped and the next candidate is used, so everyone gets up to `picks` buyable deals. The order is the same for everyone.
- Picks are fixed for the whole period. An item pool or variant product counts as one candidate, and every listing in a picked pool is discounted. Disabled products and pools with nothing on sale are never picked.
- The catalog's DEALS filter shows the deal count and next end time. Product pages show the label, discount, "ENDS IN" countdown, and remaining deal-price purchases. Wishlisted products that go on sale get a desktop badge.
- After each reload, AntOS warns about any deal product whose lowest deal price is at or below its sell-back value, since players could profit by buying and selling.
- Orders record the price paid, discount, campaign, and deal-price units. Use `/antos antazon deals` to [inspect](#commands) them.

### Unlock emails

When tasks, disks, or terminal commands unlock listings, Antazon Dispatch sends one combined email of new products and sell items. Each listing is announced once per account. Listings already unlocked through another `any` requirement aren't announced again. Turn this off per listing with `notify_on_unlock: false`, or server-wide with `sendAntazonUnlockEmail`.

## Terminal commands

`data/<namespace>/computer/terminal_command/<path>.json`

Custom Terminal commands are matched on the server, and their definitions never reach the client, so players can't find secret commands by reading game files. Built-in names such as `ls` or `decrypt` can't be replaced; such definitions are skipped with a warning.

```json
{
  "command": "sudo",
  "aliases": ["doas"],
  "arguments": ["open", "vault"],
  "hidden": true,
  "hint": "terminal.examplemod.vault.hint",
  "requires": { "disks": ["examplemod:lab_secret"], "tasks": [], "commands": [], "mode": "all", "directory": "/" },
  "typing": true,
  "typing_speed": 45,
  "locked_output": [{ "text": "Permission denied.", "style": "error", "typing": false }],
  "output": [
    { "text": "Authenticating {username}...", "style": "system" },
    { "text": "VAULT OPEN", "style": "highlight", "delay": 900, "typing_speed": 20 }
  ],
  "once": true,
  "entries": ["examplemod:field_notes"],
  "tasks": ["examplemod:vault_hunt"],
  "wallpaper": "examplemod:meadow",
  "files": [{ "path": "Vault/manifest.txt", "source": "examplemod:vault/manifest.txt" }],
  "programs": ["examplemod:lab_quiz"],
  "subscribe": ["examplemod:research_news"],
  "rewards": [{ "type": "antcoins", "amount": 50 }],
  "open": "/Vault/manifest.txt"
}
```

**Matching.**

- `command` and up to eight `aliases` use 1–32 lowercase letters, digits, `.`, `_`, or `-`.
- Typed arguments must match `arguments` exactly, in order and count, with no wildcards. Quoting works as usual (`"open vault"` is one argument).
- Matching ignores case unless `case_sensitive: true`.
- Several definitions can share a name with different arguments. If two share both name and arguments, the first by ID wins.

**Visibility.**

- `hidden` defaults to `true`: the command is never in `help` or Tab completion, and non-matching input gets the normal invalid-command error.
- `"hidden": false` shows it in `help` once requirements are met, using `usage` (default: command and arguments) and `description`.
- `hint` is what locked Antazon listings show; it defaults to "Run a secret terminal command".

**Requirements.**

- `requires` takes `disks` (activated; encrypted disks after decryption), `tasks` (completed), and `commands` (other command IDs the profile has run).
- `mode` is `all` (default) or `any`. `directory` also requires the Terminal to be in that folder.
- While locked, the command prints `locked_output`, or the invalid-command error if there isn't one.

**Output.**

- `output` and `locked_output` hold up to 64 lines of 512 characters. A line is a string or `{ text, style, typing, typing_speed, delay }`, and text can be a translation key.
- `style`: `output` (default), `highlight`, `system`, `error`.
- `typing: true` reveals the line at `typing_speed` characters per second (5–400, default 40). `delay` waits up to 10,000 ms first. Command-level `style`, `typing`, and `typing_speed` set defaults for every line.
- The prompt pauses while output plays; Enter, Escape, or Ctrl+C skips to the end. `"clear": true` clears the screen first.
- Placeholders: `{player}` (Minecraft name), `{username}` (Anternet name), `{directory}`, `{time}` (in-game `HH:MM`), `{day}` (in-game day). These work in translated text too.

**Effects.**

- Once requirements are met, a command can grant `entries`, `tasks`, `wallpaper`, `files`, `programs`, `subscribe`, and `rewards`, with the same formats and limits as [disks](#floppy-disks).
- Files without a folder go under `Terminal/<command>/`. Programs from a command stay in Games permanently, with no disk needed. Rewards use the disk reward types.
- `once` (default `true`) grants effects on each profile's first run; with `"once": false`, rewards pay on every run.
- `open` opens a file in Text or Paint after the output finishes.

Every successful run is saved on the profile and counts for `requires.commands`, Antazon `unlock_commands`, and `terminal_command` objectives. The first run sends the Antazon unlock email if it unlocks listings. Commands with requirements or effects need a signed-in account; output-only commands work for anyone.

In the template, the hidden `vault` command is behind the decrypted `lab_secret` disk, whose secret file reveals the command. Running it unlocks the `vault_blueprint` product and completes the `vault_breaker` task. `weather` is a visible example.

## Appearance

### Wallpapers

`data/<namespace>/computer/wallpaper/<path>.json`, unlocked by a disk or terminal command:

```json
{
  "title": "wallpaper.examplemod.meadow",
  "texture": "examplemod:computer/wallpaper/meadow",
  "fit": "cover"
}
```

The image goes in `assets/<namespace>/textures/computer/wallpaper/meadow.png`; texture IDs leave out `textures/` and `.png`. `fit` is `cover` (default) or `stretch`. Images must be in the mod or a resource pack on the client; missing images use the default background.

### Disk categories

`data/<namespace>/computer/category/<path>.json` sets a disk's item texture, tooltip category name, and Library group:

```json
{
  "title": "item.examplemod.floppy_category.arcade",
  "texture": "examplemod:item/floppy_disk/arcade"
}
```

Provide `assets/examplemod/textures/item/floppy_disk/arcade.png`, or point at an existing texture such as `antos:item/floppy_disk/floppy_disk`. It's drawn on the item in inventories, hands, and item frames, and in the Library. Disks reference the category ID with `category`. No Java registration or item model overrides are needed, and clients use the server's category assignments.

### Icons

Every computer icon is a PNG a resource pack can replace. Icons are unsmoothed pixel art; other sizes are scaled to fit.

| Icons | Path under `assets/antos/textures/gui/` | Size | Files |
| --- | --- | --- | --- |
| Desktop | `desktop/` | 24×24 | `antazon`, `antmail`, `archive`, `disks`, `files`, `games`, `paint`, `settings`, `tasks`, `terminal`, `text` |
| AntPaint toolbar | `antpaint/` | 16×16 | `pencil`, `eraser`, `bucket`, `line`, `rectangle`, `ellipse`, `undo`, `redo`, `trash` |
| File Explorer toolbar | `files/` | 16×16 | `up`, `new_folder`, `new_text`, `new_image`, `open`, `move`, `refresh` (rename and delete use `antpaint/pencil` and `antpaint/trash`) |
| File list | `files/` | 12×12 | `folder`, `lock`, `text`, `image` |

Green shades follow the player's color theme; other colors are drawn as-is. The defaults use `#65FF65` (main shape), `#289628` (secondary), `#102010` (dark fills), and `#030603` (outlines). Pixels under about 6% opacity are skipped. Icons reload whenever the computer screen opens.

### Renaming apps and currency

App names and the currency come from AntOS's lang file, so a resource pack can rename them by overriding keys in `assets/antos/lang/en_us.json`:

| Key | Default |
| --- | --- |
| `computer.antos.app.<app>.name` (`archive`, `files`, `settings`, `terminal`, `text`, `paint`, `antmail`, `antazon`, `games`, `disks`, `tasks`) | `ARCHIVE`, `FILES`, … |
| `computer.antos.currency.name` | `AntCoins` |
| `computer.antos.currency.symbol` | `AC` |

Any other `computer.antos.*` text can be overridden the same way.

## Integrations

### FTB Quests

Optional on both loaders.

**Objectives.** `ftbquests:quest_completed` and `ftbquests:task_completed` objectives take the FTB hexadecimal ID as `target`. When the FTB object completes, every online FTB team member with an active AntOS session or Face ID-linked profile progresses. Offline members, and members without a resolvable profile, are skipped for that event.

```json
{
  "id": "ftb_research",
  "type": "ftbquests:quest_completed",
  "target": "0A1B2C3D4E5F6789",
  "description": "task.examplemod.research.ftb_quest"
}
```

**Quest editor options.**

- **Unlock AntOS Archive Entry** (reward): unlocks an Archive entry when claimed.
- **Grant AntOS Task** (reward): grants an AntOS task when claimed. If the player has no active or Face ID-linked profile, the grant is saved and delivered at their next AntOS sign-in.
- **AntOS Task** (FTB task): completes when the chosen AntOS task completes. The player's online AntOS teammates also update their own FTB team progress, following FTB's normal rules. Offline teammates catch up when they next join an FTB team or sign in.

FTB rewards follow FTB's claim and auto-claim settings, while AntOS objectives follow completion directly. You can use either or both.

### Field Guide

When Field Guide is installed, entries a player unlocks in it are mirrored into their Archive under a **FIELD GUIDE** category, with the creature or item as the preview. No configuration is needed. Entries that can't be converted are logged and skipped.

### Shipping containers

Selling on Antazon needs a container near the computer, linked once from that computer. Vanilla single chests always work. Refurbished Furniture mailboxes and Create Frogports work when those mods are installed; mailbox delivery queues are left to Refurbished Furniture.

## Java API

### Archive entity poses

Register a client-side configurator during client init to pose an entity in Archive previews. AntOS creates the entity, calls the configurator once, and caches it. Only set client-safe visual state.

```java
ArchiveEntityPreviewRegistry.register(
        ResourceLocation.fromNamespaceAndPath("examplemod", "field_beetle"),
        entity -> {
            if (entity instanceof FieldBeetleEntity beetle) beetle.setArchivePose();
        });
```

### Archive locators

For targets that aren't vanilla structures (grid features, boss sites, and so on), give the entry a `locator` ID and `dimension`, and register a server-side lookup during common init. AntOS calls it on the server thread with that dimension's level and its shared spawn as the origin, and shows the result as `LOCATION // x, y, z`. Return `null` when nothing is in range. Locators must only find existing positions, never change the world.

```java
ArchiveLocatorRegistry.register(
        ResourceLocation.fromNamespaceAndPath("examplemod", "beetle_nest"),
        (level, origin) -> BeetleNestGrid.nearestNest(level, origin));
```

### Native games

Register code-based games with `ComputerGameRegistry`. Each game has a namespaced ID and an installing disk ID, and its session implements `tick`, `render`, `keyPressed`, and `charTyped`. Sessions can also override `mouseClicked(mouseX, mouseY, button)` and the `render` overload that takes `mouseX, mouseY`; both use coordinates relative to the game area's top-left corner. The game area is the Games window below its BACK header: 374×190 by default and 396×178 maximized. Lay out from the `width` and `height` passed to `render`; drawing is clipped to that area. See `ComputerGame` and `ComputerGameRegistry`.

AntOS ships Blockle, Chess (four AI levels) and Minesweeper (two board sizes). Chess and Minesweeper are adapted from Create: Cybernetics by Perigrine3 under the MIT License; see `licenses/createcybernetics-MIT.txt`.

### Custom objectives

Register objective types with `TaskObjectiveRegistry` and report progress from server code with `TaskObjectiveApi.record(player, computer, type, target, amount)`, so objectives update without polling.

## Commands

All commands need operator permission (level 2).

| Command | Effect |
| --- | --- |
| `/antos tasks inspect <task>` | Shows completion and objective counts for your signed-in workspace. |
| `/antos tasks grant <task>` | Makes a task available. |
| `/antos tasks complete <task>` | Completes it, makes rewards claimable, and checks dependents. |
| `/antos tasks setprogress <task> <objective> <count>` | Sets event-driven objective progress. |
| `/antos tasks reset` | Clears task progress; Archive unlocks are kept. |
| `/antos tasks rewardpool <player> <pool>` | Gives one roll of a reward pool. |
| `/antos mail send <player> <message>` | Sends a data-driven message now, bypassing its requirements. |
| `/antos mail trigger <player> <type> <value>` | Fires a trigger event (such as `advancement <id>`) and reports how many messages matched. |
| `/antos antazon deals` | Orders, units, revenue, and average discount per campaign and product, split into deal and regular sales. |
| `/antos antazon deals preview [periods]` | Each campaign's picks for this period and the next ones (default 3), before unlock filtering. |
| `/antcoin give <player> <amount>` | Adds AntCoins to the player's signed-in profile. |
| `/antcoin get <player>` | Shows a balance. |
| `/antcoin set <player> <amount>` | Sets a balance. |

The `/antos tasks` commands without a player argument act on your own signed-in Anternet profile, so sign in on a computer or phone first.

## How AntOS behaves in play

What players experience, so you can design around it:

- **Profiles, not devices.** Computers and Antroid Phones are access devices. Players sign in with an Anternet username and password, and everything (files, preferences, task and Archive progress, disks, rewards, mailbox, wallet) is saved to that profile in the server world. Profiles don't transfer between servers. Players can share credentials to share a profile; there are no read-only permissions.
- **Disks.** Inserting a disk adds it to the profile. **EJECT DISK** in the Disks Library drops it at the player; permanent unlocks are kept and one-time rewards are never repeated. Inserting an already-installed disk leaves it in hand with **Already installed**. Phones can queue disks before sign-in and return duplicates. Disks on older computers migrate into a profile the first time it's opened there.
- **Disks Library.** Disks are grouped by category with collection counts, search, and filters. Undiscovered disks show as silhouettes with `???`, and search never reveals them. Disk pages link to their entries, tasks, wallpapers, games, files, and Antazon listings. Links to disabled apps are inactive.
- **Tasks app.** Drag to pan, scroll or Shift+scroll to move, and Ctrl+wheel or `-`/`+` to zoom. **CENTER** frames the tab and **FIND**/Ctrl+F searches. Locked tasks show a lock, and a `!` marks claimable rewards. Clicking a prerequisite opens it; one in another tab asks first. Clicking an item icon shows its recipe.
- **Antmail.** Players can write to each other and attach text files, Paint images, or AntCoins (when Antazon is on).

## Troubleshooting

- **Content doesn't show up.** Check the server log after `/reload`. AntOS reports bad references and skips invalid definitions.
- **A task in a `category` or `group` folder is missing.** Those folder names are reserved under `task/`.
- **A disk shows its ID instead of a name.** Add the `title` translation to your lang file.
- **Antazon or Tasks isn't on the desktop.** Both are off by default; enable them in `desktopApps`.
- **A disk reward didn't pay again after editing.** Rewards are claimed once per profile per disk; see [disk rewards](#disk-rewards).
- **A wallpaper shows the default background.** The image is missing on the client. Ship it in a mod or resource pack.
- **Folder names from disks differ between servers.** Untitled file paths use the server's language; give files an explicit folder.
