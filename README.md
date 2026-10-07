# AntOS

AntOS adds an ant-themed computer to Minecraft 1.21.1. Its desktop includes an Archive, file tools, a terminal, Antmail, Antazon, games, paint, and a Tasks app. Floppy disks unlock Archive entries, wallpapers, tasks, and games. Modpack authors can add their own content with data packs or resource packs.

## For players

AntOS supports **Fabric** and **NeoForge**. It requires **GeckoLib** on either loader. Fabric also requires Fabric API. Install the AntOS file for your loader alongside those dependencies.

Use a computer block or an Antroid Phone to access the Anternet desktop. Both devices sign in with an Anternet username and password. The login screen remembers the last username on that client to reduce repeat typing.

Insert a floppy disk into a computer to add it to the signed-in profile. Archive entries, wallpapers, and tasks unlocked by the disk are saved with that profile; disk-installed games are available while the disk remains installed. In **Settings**, choose **EJECT SELECTED** to drop a disk at the player's current location. Disks stored on older computers migrate into the profile the first time that profile is opened on that computer; the migration keeps the installed disk IDs.

### Progress and backups

The server saves workspace files, desktop preferences, task/archive progress, and installed disk IDs in a profile-owned workspace. The Minecraft server world stores these profiles; accounts do not transfer between unrelated servers. Computers and phones are access devices rather than workspace storage, so the same signed-in profile is available from either device.

You can give another player your Anternet username and password so they can access the same profile at the same time from their own device. Anyone with the password has full access; AntOS does not currently provide read-only or per-app sharing permissions. Only share credentials with players you trust. The client remembers the last username, not the password. In multiplayer, task objectives based on inventory, advancements, and statistics use the player viewing the Tasks app; profile task progress is shared.

Antmail addresses and mailboxes belong to Anternet profiles. Each profile automatically receives `<username>@antmail.com`; no separate Antmail setup is required. Antazon account data is also profile-based. Antazon shipping crates remain physical: link a nearby crate once from a computer, then use the profile's Antazon functions from a computer or phone.

### Configuration

On first launch AntOS creates `config/antos.json`. Restart the game or server after editing it. The unlock options are controlled by the server; desktop app toggles are read by the client and are client-side.

```json
{
  "unlockAllArchiveEntries": false,
  "unlockAllGameEntries": false,
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
    "trash": true,
    "tasks": false
  }
}
```

Set either `unlockAllArchiveEntries` or `unlockAllGameEntries` to `true` to unlock all loaded entries or registered games without their disks. Antazon and Tasks are disabled by default; set `desktopApps.antazon` and/or `desktopApps.tasks` to `true` to enable them. Disable other individual desktop apps by setting their values to `false`.

## For modpack developers

The working examples are in [`examples/datapack-template`](examples/datapack-template). Add data files under `data/<namespace>/...`, using your mod ID or pack namespace. For content shipped in a mod or resource pack, put translations and textures under `assets/<namespace>/...`. AntOS reads content from all namespaces.

### Archive entries

Add `data/<namespace>/computer/entry/<path>.json`; its ID is `<namespace>:<path>`. Entries can contain translated titles, subtitles, and descriptions, plus links to items, entities, recipes, enchantments, or structures for Archive previews and locating. Set `unlocked_by_default` to `true` to make an entry available on every computer without requiring a disk. It defaults to `false`. Structure entries can use `structure_tag`, `dimension`, and `search_radius`. `green_tint` defaults to `true`; set it to `false` to show preview colors normally. Cover fields include `cover_item`, `cover_entity`, `cover_potion`, `rotation`, and `render_scale`.

For entity-specific archive poses, register a client-side configurator with ArchiveEntityPreviewRegistry.register(ResourceLocation, Consumer<LivingEntity>). AntOS creates the preview entity, calls the registered configurator once, and then caches that entity for rendering. Match the entity type ID and check or cast the entity to your mod's class before applying its visual state. Configurators should only set client-safe preview state and must not perform gameplay actions. Register configurators during client initialization before the Archive is opened.
```java
ArchiveEntityPreviewRegistry.register(
        ResourceLocation.fromNamespaceAndPath("examplemod", "field_beetle"),
        entity -> {
            if (entity instanceof FieldBeetleEntity beetle) beetle.setArchivePose();
        });
```

For targets that are not vanilla structures (grid-placed features, boss spawn sites, and so on), give the entry a `locator` ID and a `dimension`, then register a server-side lookup with ArchiveLocatorRegistry.register(ResourceLocation, Locator) during common initialization. When the entry is opened, AntOS calls the locator on the server thread with the target dimension's level and its shared spawn as the origin, and shows the returned position as `LOCATION // x, y, z`. Return `null` when nothing is in range. Locators should find existing positions only and must not spawn entities or otherwise change the world.
```java
ArchiveLocatorRegistry.register(
        ResourceLocation.fromNamespaceAndPath("examplemod", "beetle_nest"),
        (level, origin) -> BeetleNestGrid.nearestNest(level, origin));
```

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

Add translations such as `guide.examplemod.entry.field_notes.title` to `assets/<namespace>/lang/en_us.json`.

### Floppy disks

Add `data/<namespace>/computer/disk/<path>.json`. Inserting a disk unlocks its listed entries, wallpaper, and tasks. A disk's ID is its namespace and path. Create the item with the `antos:floppy_disk` component set to that ID; the template includes a crafting recipe.

```json
{
  "category": "general",
  "title": "guide.examplemod.disk.field_notes.title",
  "entries": ["examplemod:field_notes"],
  "wallpaper": "examplemod:meadow",
  "tasks": ["examplemod:field_basics"]
}
```

### Tasks

Add tasks at `data/<namespace>/computer/task/<path>.json`. A disk can grant starting tasks; tasks can also start automatically or require earlier tasks. Progress belongs to the signed-in Anternet profile. Objectives can check a player's current inventory, advancements, vanilla statistics, reading a data-driven Antmail message, or viewing an Archive entry. Tasks can grant items, experience, effects, advancements, Archive unlocks, or Antmail messages.

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
      "count": 4
    }
  ]
}
```

Supported objective types are `item`, `item_tag`, `advancement`, `stat`, `mail_read`, `archive_viewed`, and registered mod objective IDs. With FTB Quests installed, AntOS also supports `ftbquests:quest_completed` and `ftbquests:task_completed`; their `target` is the FTB hexadecimal object ID. Objectives require `id`, `type`, `description`, and usually `target`; `count` defaults to `1`, and `optional: true` prevents an objective from blocking completion.

`item` checks for one exact item ID. `item_tag` checks an item tag ID without a leading `#`. Its optional `tag_mode` accepts `any` or `all` and defaults to `any` when omitted. For `any`, `count` is the total number of matching items required across all members. For `all`, `count` is required for each item currently in the tag, and progress shows how many tag members have met that amount. Item tags are limited to 128 members. For example, this objective requires any combination of 16 planks; omitting `tag_mode` selects `any`:

```json
{
  "id": "collect_planks",
  "type": "item_tag",
  "target": "minecraft:planks",
  "description": "task.examplemod.collect_planks",
  "count": 16
}
```

Objective progress is sticky by default: AntOS saves the highest observed progress, so separate objectives—or members of an `all` tag—can be satisfied at different times. For an exact-item or `any` tag objective, the `count` threshold must still be observed at once; sticky progress is not cumulative pickup counting. Set `sticky: false` to track live inventory progress until the objective is satisfied.

Set `consume: true` on an `item` or `item_tag` objective to remove the required items when satisfied; it defaults to `false`. Exact-item and `any` objectives consume their full requirement together. An `all` tag consumes each member's requirement as that member is satisfied, so the full tag does not need to be held at once. `advancement` checks an advancement ID; `mail_read` targets a data-driven Antmail message ID; and `archive_viewed` targets an Archive entry ID. For `stat`, add `stat_type` such as `item_crafted`, `block_mined`, or `entity_killed`; statistics are lifetime player totals.

For an objective chain, add `requires: ["namespace:task_id"]`. Optional `position: {"x": 0, "y": 0}` places a node on the map; `hide_until_dependencies_complete` and `invisible_until_completed` control task visibility. Set optional `green_tint` to `false` to show the task icon and its detail-panel item icons in their normal colors; it defaults to `true`. Set optional `render_mob_from_spawn_egg` to `false` to show spawn eggs as their item icons in the task map and detail panel; it defaults to `true`. Set optional `icon` to an item ID, or `icon_entity` to an entity ID, to choose the task's map icon. Without one, AntOS uses the first `item` objective, then the first kill statistic's creature, then the first item reward, then paper. Category names use `task.category.<namespace>.<path>` translations.

Optional category files at `data/<namespace>/computer/task/category/<path>.json` set a category's title, sidebar icon, order, group, and icon tint. Category icons are green tinted by default; set `green_tint` to `false` to show the icon in its normal colors. Set `render_mob_from_spawn_egg` to `false` to keep a spawn egg as the category icon; it defaults to `true`. The file's namespace and path must match the `category` value on its tasks:

```json
{
  "title": "task.category.examplemod.meadow_research",
  "icon": "minecraft:compass",
  "green_tint": true,
  "sort_order": 10,
  "group": "examplemod:fieldwork"
}
```

Use `icon_entity` instead of `icon` for a creature. Categories are ordered by `sort_order`, lowest first; categories without a file appear after them in alphabetical order. Optional group files at `data/<namespace>/computer/task/group/<path>.json` add a collapsible sidebar header with the same `title`, `icon`, `icon_entity`, and `sort_order` fields, plus `collapsed` for its default state. Group icons are green tinted by default; set `green_tint` to `false` to show the icon in its normal colors. Set `render_mob_from_spawn_egg` to `false` to keep a spawn egg as the group icon; it defaults to `true`. Groups and ungrouped categories share one ordering, and a group's categories are listed under it by their own `sort_order`. Each player's expanded or collapsed choice is saved in their client `antos.json`. Groups without visible categories are hidden, and a category naming a missing group is shown ungrouped. Category and group titles can include `{player}`. Because the `category` and `group` folders hold these files, tasks can't be placed in folders with those names.

For structure visits, use a vanilla advancement with the `minecraft:location` trigger and a `structures` location predicate, then reference its ID in an `advancement` objective. The template includes a stronghold example. A locator result alone does not count as visiting the structure.

Completing a task makes its rewards claimable in the Tasks app; rewards are not delivered automatically. Each Anternet profile can claim a task's rewards once. Supported reward types are `item`, `experience`, `experience_levels`, `advancement`, `effect`, `archive`, and `mail`. Item rewards appear as dropped items near the player, and mail rewards are delivered to the claiming profile's Antmail address. Archive rewards unlock when the task completes.

The Tasks app shows each category as a dependency map. Drag empty map space to pan; use the mouse wheel to scroll vertically, Shift+wheel to scroll sideways, and Ctrl+wheel or the `-` and `+` buttons to zoom. **CENTER** frames the category, and **?** opens the control guide. Use **FIND** or Ctrl+F to search task titles and descriptions; selecting a result opens and centers that task. Hover over a task node to see its title. Locked tasks use a lock icon, while a `!` badge marks rewards ready to claim.

The category sidebar opens when you hover over its edge or click its chevron. Choose **KEEP OPEN** to pin the sidebar open; otherwise it closes when you move away. Category groups can be expanded or collapsed, and the selected category shows its completed and reward-ready counts. Opening a task shows its description, progress bar, prerequisites, objectives, and every configured reward. Click an objective or reward item icon to expand recipe details when a matching recipe is available. Claimable rewards have a **CLAIM REWARD** button.

Players can optionally create or join one AntOS team from the **TEAM** button at the lower-right of the Tasks app. Team task progress and Archive unlocks are shared immediately. Joining merges existing progress; leaving or disbanding keeps the progress each member gained with the team. Every member can claim completed task rewards separately. Team invitations are sent as individual Antmail messages and accepted in the Tasks app. Files, mailboxes, disks, devices, and account credentials remain private.

### FTB Quests compatibility

FTB Quests compatibility is optional on Fabric and NeoForge. When installed, FTB task and quest completions can advance the matching AntOS task objective for each online FTB team member with an active AntOS session or Face ID-linked profile. Offline members and members without a resolvable AntOS profile are skipped for that event.

```json
{
  "id": "ftb_research",
  "type": "ftbquests:quest_completed",
  "target": "0A1B2C3D4E5F6789",
  "description": "task.examplemod.research.ftb_quest"
}
```

FTB Quests adds three AntOS choices to its quest editor:

- **Unlock AntOS Archive Entry** unlocks a selected Archive entry when the FTB reward is claimed.
- **Grant AntOS Task** grants a selected AntOS task when the FTB reward is claimed. If the player has no active or Face ID-linked AntOS account, AntOS stores the grant in world data and delivers it to the account used at that player's next successful AntOS sign-in.
- **AntOS Task** completes an FTB task when the selected AntOS task completes. The completing player's online AntOS teammates also have their own FTB team progress updated; each FTB team retains its normal shared-progress and dependency rules. Offline AntOS teammates are reconciled when they next join an FTB team or sign in to AntOS.

FTB reward actions follow FTB's reward claim and auto-claim settings. An AntOS completion objective follows quest/task completion directly. Authors may configure either or both; when both are configured, both actions run.

### Antmail messages

Each Anternet profile has an Antmail address and its own inbox, sent mail, drafts, archive, and trash. The app supports searching, unread-only and attachment-only filters, replying, restoring or moving messages, and editing the profile's display name and avatar. Compose messages to other AntOS addresses and attach workspace text files, Paint images, or AntCoins when Antazon is enabled. Received text and Paint attachments can be saved into the recipient's workspace; AntCoin transfers are credited to the recipient's wallet.

Add data-driven messages at `data/<namespace>/antmail/message/<path>.json`; the message ID is `<namespace>:<path>`. Supported triggers are `random`, `advancement`, `mail_read`, and `task_complete`. A random message with a `value` chance is checked once per in-game day per profile. Advancement, message-read, and task-completion triggers can target an advancement ID, message definition ID, or task ID. Use `once: false` to allow a triggered message to be sent more than once; it defaults to `true` except for random messages. Messages may be delayed in Minecraft days with `delay`, gated by `conditions`, and limited to a Minecraft-time `deliver_window`. A message is queued until its conditions and delivery window allow it to arrive.

Random messages can use weighted pools. Define a pool at `data/<namespace>/antmail/pool/<path>.json` with `chance_per_day` and `max_per_day`; reference it from a random message's `trigger.pool`, and optionally set `trigger.weight`. A pool draws eligible unseen messages by weight for each profile, then cycles through them after all eligible entries have been seen.

Data-driven messages may set an optional `sender_display` name and `sender_avatar_item` or `sender_avatar_entity` to a registered item or living entity ID. The inbox and message view render the configured portrait; messages without a usable portrait show initials. Reuse the same avatar ID across messages from the same sender to keep their portrait consistent. For example, `"sender_display": "Field Office"` with `"sender_avatar_item": "minecraft:compass"` shows a named sender with a rendered compass portrait. A registered block item can also be used as an item avatar.

Optional presentation fields include `style`, `notification`, and `delete_after_read`. Set `style` to `corrupted` to give a message the animated corrupted presentation; body text can use `[glitch]...[/glitch]` and `[redact]...[/redact]` spans. Notifications play a chime by default; set `notification` to `silent` to suppress it or to a sound event ID to choose another sound. A `delivery` object can grant item stacks either when the message arrives (`"on": "deliver"`) or when it is read (the default). These delivered items are separate from message attachments.

AntMail automatically turns explicit Archive render markers in message bodies into render attachments. Use `@item:<namespace>:<path>`, `@entity:<namespace>:<path>`, `@enchantment:<namespace>:<path>`, `@potion:<namespace>:<path>`, or `@recipe:<namespace>:<path>` in a body. The marker is removed from the displayed text and the matching preview is shown below the message. This works for data-driven story mail, task mail rewards, and regular AntMail messages; ordinary IDs without a marker remain plain text.

```json
{
  "sender": "fieldoffice",
  "subject": "A note from the field",
  "body": "Welcome to the colony network.",
  "trigger": { "type": "random", "value": "0.05" }
}
```

Use `trigger.type` of `random` with a chance from `0` to `1`, or `advancement` with an advancement ID as `trigger.value`.

For `mail_read`, set `trigger.value` to another data-driven message ID. For `task_complete`, set it to a task ID. `conditions` accepts `advancements`, `not_advancements`, `mail_read`, `tasks_complete`, and `dimension`; each ID field accepts one ID or an array. A pool file can look like this:

```json
{
  "chance_per_day": 0.35,
  "max_per_day": 1
}
```

Then add `"pool": "examplemod:field_rumors"` and optionally `"weight": 2` to a message's random `trigger` instead of setting `value` to a direct chance.

### Antazon

Antazon is the in-game supply shop and AntCoin exchange. It supports catalog browsing, AntCoin purchases, selling configured items from a nearby chest for AntCoins, task-based unlocks, shared server stock, Minecraft-time restocking, player limits, cooldowns, seeded reviews, verified player reviews, daily deals, a shopping cart, persistent server wishlists, order history, and Archive-style item or mob previews. Successful purchases arrive through the falling chest delivery system. Purchases are final and are not refundable.

Add products at `data/<namespace>/antazon/product/<path>.json`; the product ID is `<namespace>:<path>`. Product data is loaded during server data reloads. The product must define `quantity`, at least one payment, and one reward. Optional `thumbnail` and `gallery` objects use `{ "item": "namespace:item" }` or `{ "entity": "namespace:entity" }` and control the catalog thumbnail and product-page previews. Set optional `green_tint` to `false` to show those previews in their normal colors; it defaults to `true`. Set optional `render_mob_from_spawn_egg` to `false` to show spawn eggs as item icons throughout the product previews; it defaults to `true`.

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
  ],
  "deal": {
    "enabled": true,
    "label": "NEST DEAL",
    "discount_percent": 20,
    "cycle_minecraft_days": 1
  }
}
```

Product payments use `type: "antcoins"`; the resource is conventionally `antcoins` and the amount is the AntCoin price. Sell rules live at `data/<namespace>/antazon/sell/<path>.json` with an `item` ID and AntCoin `value`. Optional `green_tint` controls the item preview in Sell and Prices; it defaults to `true`, and `false` shows the normal colors. Set `render_mob_from_spawn_egg` to `false` to show a spawn egg as the item in Sell and Prices; it defaults to `true`. In the Antazon Sell tab, place items in a single chest next to the computer, select every occupied sellable slot, and ship the chest. The chest is consumed and each purchase delivery includes a replacement chest.

Payment choices are tried in data-file order, and the first affordable option is used. AntOS's built-in products use AntCoins. Each product has one purchase definition: `quantity` controls how many reward sets arrive, and `payments` lists the accepted payment choices. `rewards` defines the contents of one reward set. `server_stock` is shared by the server; set it to `0` for unlimited stock. `player_limit_reset` accepts `none`, `minecraft_day`, or `minecraft_week`.

Products can reference any loaded task IDs with `unlock_tasks`. Use `unlock_mode` of `all` or `any`. Daily deals use Minecraft days and apply their discount on the server during active cycle days. Reviews in product data are configured shop reviews; players can add one verified review after a delivered purchase. Wishlists and order history are saved on the shared server. AntMail can compose product links and wishlist messages from Antazon, and recipients can open shared product links directly in the shop.

### Wallpapers and disk categories

Define a wallpaper at `data/<namespace>/computer/wallpaper/<path>.json`, then reference it from a disk. Provide the image at `assets/<namespace>/textures/computer/wallpaper/<name>.png`. Texture IDs omit the `textures/` prefix and `.png` suffix. `fit` can be `cover` (default) or `stretch`.

```json
{
  "title": "wallpaper.examplemod.meadow",
  "texture": "examplemod:computer/wallpaper/meadow",
  "fit": "cover"
}
```

Disk categories are defined at `data/<namespace>/computer/category/<path>.json` with a translated `title` and a `texture` resource location. See [`docs/floppy-disk-categories.md`](docs/floppy-disk-categories.md) for the category format. Wallpaper images must be available to clients through the mod or a resource pack; missing images use the default desktop background.

### Native games and custom objectives

Mods can register code-based games in the Games app using `ComputerGameRegistry`. Each game has a namespaced ID and an installing disk ID; implement its session's `tick`, `render`, `keyPressed`, and `charTyped` methods. See `ComputerGame` and `ComputerGameRegistry` in the AntOS API.

Mods can register task objective types with `TaskObjectiveRegistry` and report server-side progress with `TaskObjectiveApi.record(player, computer, type, target, amount)`. This lets custom objectives update without polling gameplay state.

## Server operators

Task commands require operator permission and target a loaded computer. Use a task ID loaded from a data pack.

- `/antos tasks inspect <namespace:task>` shows completion and objective counts for your signed-in Anternet workspace.
- `/antos tasks grant <namespace:task>` makes a task available.
- `/antos tasks complete <namespace:task>` completes it, makes its rewards claimable, and checks dependent tasks.
- `/antos tasks setprogress <namespace:task> <objective> <count>` updates event-driven objective progress.
- `/antos tasks reset` clears task progress while preserving Archive unlocks.

On startup and data pack reload, AntOS checks content references and reports problems in the server log. Invalid definitions may be skipped; check the log if content is missing in game.
