# AntOS

AntOS adds an ant-themed computer to Minecraft 1.21.1. Its desktop includes an Archive, file tools, a terminal, Antmail, Antazon, games, paint, and a Tasks app. Floppy disks unlock Archive entries, wallpapers, tasks, and games. Modpack authors can add their own content with data packs or resource packs.

## For players

AntOS supports **Fabric** and **NeoForge**. It requires **GeckoLib** on either loader. Fabric also requires Fabric API. Install the AntOS file for your loader alongside those dependencies.

Place and use the computer block to open its desktop. A computer can be picked up: mining it turns it into a portable computer item that keeps installed disks and local computer data. Place the item to restore the computer. In **Settings**, select an installed disk and choose **EJECT SELECTED** to remove it; if your inventory is full, it drops nearby.

Insert a floppy disk to unlock its Archive entries, wallpaper, and tasks. Disk-installed games appear while their disk is installed. Archive unlocks and task progress remain with that computer's progression workspace when disks are removed.

### Progress and backups

The server saves Archive unlocks and task progress in a workspace associated with the Minecraft UUID of the player who initializes it. When that player opens a new computer, they can restore the existing workspace or start a separate one. The server backup remains available if the computer item is lost.

The portable computer item carries local files, wallpapers, passwords, and installed disks. These local settings are not part of the server progression backup. In multiplayer, task objectives based on inventory, advancements, and statistics use the player opening the Tasks app; task progress is shared by the computer.

### Configuration

On first launch AntOS creates `config/antos.json`. Restart the game or server after editing it. The unlock options are controlled by the server; desktop app toggles are client-side.

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
    "antazon": true,
    "games": true,
    "trash": true,
    "tasks": true
  }
}
```

Set either `unlockAllArchiveEntries` or `unlockAllGameEntries` to `true` to unlock all loaded entries or registered games without their disks. Disable individual desktop apps by setting their values to `false`.

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

Task rewards are issued once when the task completes. Supported rewards are `item`, `experience`, `experience_levels`, `advancement`, `effect`, `archive`, and `mail`; item rewards appear as dropped items near the player. A mail reward requires the player to have registered an Antmail address. The template includes a task chain and reward examples.

### Antmail messages

Add `data/<namespace>/antmail/message/<path>.json`. Random messages are checked once per in-game day per registered address. Advancement messages are sent when a player with a registered address earns the specified advancement.

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

Antazon is the in-game online supply shop. It supports catalog browsing, product pages, purchases, multiple item payment options, task-based unlocks, shared server stock, Minecraft-time restocking, player limits, cooldowns, seeded reviews, verified player reviews, daily deals, a shopping cart, persistent server wishlists, order history, and Archive-style item or mob previews. Successful purchases arrive through the falling chest delivery system. Purchases are final and are not refundable.

Add products at `data/<namespace>/antazon/product/<path>.json`; the product ID is `<namespace>:<path>`. Product data is loaded during server data reloads. The product must define `quantity`, at least one payment, and one reward. Optional `thumbnail` and `gallery` objects use `{ "item": "namespace:item" }` or `{ "entity": "namespace:entity" }` and control the catalog thumbnail and product-page previews.

```json
{
  "name": "Reinforced Nest Bundle",
  "description": "A supply bundle for expanding a busy nest.",
  "category": "building_supplies",
  "tags": ["building", "starter"],
  "quantity": 1,
  "payments": [
    { "type": "item", "resource": "minecraft:emerald", "amount": 8 },
    { "type": "item", "resource": "minecraft:diamond", "amount": 1 }
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

Payment choices are tried in data-file order, and the first affordable option is used. Current payment support is item-based. Each product has one purchase definition: `quantity` controls how many reward sets arrive, and `payments` lists the accepted payment choices. `rewards` defines the contents of one reward set. `server_stock` is shared by the server; set it to `0` for unlimited stock. `player_limit_reset` accepts `none`, `minecraft_day`, or `minecraft_week`.

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

- `/antos tasks inspect <x y z> <namespace:task>` shows completion and objective counts.
- `/antos tasks grant <x y z> <namespace:task>` makes a task available.
- `/antos tasks complete <x y z> <namespace:task>` completes it, grants rewards, and checks dependent tasks.
- `/antos tasks setprogress <x y z> <namespace:task> <objective> <count>` updates event-driven objective progress.
- `/antos tasks reset <x y z>` clears task progress while preserving Archive unlocks.

On startup and data pack reload, AntOS checks content references and reports problems in the server log. Invalid definitions may be skipped; check the log if content is missing in game.
