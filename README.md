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

Add `data/<namespace>/computer/entry/<path>.json`; its ID is `<namespace>:<path>`. Entries can contain translated titles, subtitles, and descriptions, plus links to items, entities, recipes, enchantments, or structures for Archive previews and locating. Structure entries can use `structure_tag`, `dimension`, and `search_radius`. `green_tint` defaults to `true`; set it to `false` to show preview colors normally. Cover fields include `cover_item`, `cover_entity`, `cover_potion`, `rotation`, and `render_scale`.

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

Add tasks at `data/<namespace>/computer/task/<path>.json`. A disk can grant starting tasks; tasks can also start automatically or require earlier tasks. Progress belongs to the computer. Objectives can check a player's current inventory, advancements, vanilla statistics, reading a data-driven Antmail message, or viewing an Archive entry. Tasks can grant items, experience, effects, advancements, Archive unlocks, or Antmail messages when completed.

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

Supported objective types are `item`, `advancement`, `stat`, `mail_read`, `archive_viewed`, and registered mod objective IDs. Objectives require `id`, `type`, `description`, and usually `target`; `count` defaults to `1`, and `optional: true` prevents an objective from blocking completion. `item` checks the player's current inventory; `advancement` checks an advancement ID; `mail_read` targets a data-driven Antmail message ID; and `archive_viewed` targets an Archive entry ID. For `stat`, add `stat_type` such as `item_crafted`, `block_mined`, or `entity_killed`; statistics are lifetime player totals. For an objective chain, add `requires: ["namespace:task_id"]`. Optional `position: {"x": 0, "y": 0}` places a node on the map; `hide_until_dependencies_complete` and `invisible_until_completed` control task visibility. Category names use `task.category.<namespace>.<path>` translations.

For structure visits, use a vanilla advancement with the `minecraft:location` trigger and a `structures` location predicate, then reference its ID in an `advancement` objective. The template includes a stronghold example. A locator result alone does not count as visiting the structure.

Task rewards are issued once when the task completes. Supported rewards are `item`, `experience`, `experience_levels`, `advancement`, `effect`, `archive`, and `mail`; item rewards appear as dropped items near the player. Mail rewards are delivered to the player's automatically assigned Antmail address. The template includes a task chain and reward examples.

### Antmail messages

Add `data/<namespace>/antmail/message/<path>.json`. Random messages are checked once per in-game day per Anternet profile. Advancement messages are sent to the profile address when a player earns the specified advancement.

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

### Antazon

Antazon is the in-game supply shop and AntCoin exchange. It supports catalog browsing, AntCoin purchases, selling configured items from a nearby chest for AntCoins, task-based unlocks, shared server stock, Minecraft-time restocking, player limits, cooldowns, seeded reviews, verified player reviews, daily deals, a shopping cart, persistent server wishlists, order history, and Archive-style item or mob previews. Successful purchases arrive through the falling chest delivery system. Purchases are final and are not refundable.

Add products at `data/<namespace>/antazon/product/<path>.json`; the product ID is `<namespace>:<path>`. Product data is loaded during server data reloads. The product must define `quantity`, at least one payment, and one reward. Optional `thumbnail` and `gallery` objects use `{ "item": "namespace:item" }` or `{ "entity": "namespace:entity" }` and control the catalog thumbnail and product-page previews.

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

Product payments use `type: "antcoins"`; the resource is conventionally `antcoins` and the amount is the AntCoin price. Sell rules live at `data/<namespace>/antazon/sell/<path>.json` with an `item` ID and AntCoin `value`. In the Antazon Sell tab, place items in a single chest next to the computer, select every occupied sellable slot, and ship the chest. The chest is consumed and each purchase delivery includes a replacement chest.

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
- `/antos tasks complete <namespace:task>` completes it, grants rewards, and checks dependent tasks.
- `/antos tasks setprogress <namespace:task> <objective> <count>` updates event-driven objective progress.
- `/antos tasks reset` clears task progress while preserving Archive unlocks.

On startup and data pack reload, AntOS checks content references and reports problems in the server log. Invalid definitions may be skipped; check the log if content is missing in game.
