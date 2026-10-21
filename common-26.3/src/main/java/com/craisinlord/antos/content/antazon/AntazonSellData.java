package com.craisinlord.antos.content.antazon;

import com.craisinlord.antos.AntOS;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Data-driven item values for Antazon Sell. */
public final class AntazonSellData extends SimplePreparableReloadListener<List<AntazonSellData.Source>> {
    private static final String DIRECTORY = "antazon/sell";
    private static final AntazonSellData INSTANCE = new AntazonSellData();
    private static volatile List<Source> sources = List.of();
    private static volatile Map<Identifier, Rule> rules;

    private AntazonSellData() { }

    public static AntazonSellData instance() { return INSTANCE; }

    public static Rule rule(Identifier item) { return resolved().get(item); }

    public static Rule ruleForLimitKey(String key) {
        return resolved().values().stream().filter(rule -> rule.limitKey().equals(key)).findFirst().orElse(null);
    }

    public static Map<Identifier, Rule> snapshot() { return resolved(); }

    public static List<Rule> rules() { return resolved().values().stream().sorted(Comparator.comparing(value -> value.item().toString())).toList(); }

    private static Map<Identifier, Rule> resolved() {
        Map<Identifier, Rule> current = rules;
        if (current != null) return current;
        synchronized (AntazonSellData.class) {
            if (rules == null) rules = resolve(sources);
            return rules;
        }
    }

    private static Map<Identifier, Rule> resolve(List<Source> loaded) {
        Map<Identifier, Rule> direct = new HashMap<>();
        Map<Identifier, Rule> tagged = new HashMap<>();
        for (Source source : loaded) {
            for (Identifier item : AntazonTags.items(source.items(), List.of()))
                direct.putIfAbsent(item, new Rule(item, source.value(), source.greenTint(), source.renderMobFromSpawnEgg(), source.limit(), source.limitReset(), "item:" + item,
                        source.unlockTasks(), source.unlockMode(), source.hiddenUntilUnlocked(), source.notifyOnUnlock(), source.unlockDisks(), source.unlockCommands()));
            for (Identifier tag : source.tags()) {
                for (Identifier item : AntazonTags.items(List.of(), List.of(tag)))
                    tagged.putIfAbsent(item, new Rule(item, source.value(), source.greenTint(), source.renderMobFromSpawnEgg(), source.limit(), source.limitReset(), "tag:" + tag,
                            source.unlockTasks(), source.unlockMode(), source.hiddenUntilUnlocked(), source.notifyOnUnlock(), source.unlockDisks(), source.unlockCommands()));
            }
        }
        tagged.putAll(direct);
        return Map.copyOf(tagged);
    }

    @Override
    protected List<Source> prepare(ResourceManager manager, ProfilerFiller profiler) {
        List<Source> loaded = new ArrayList<>();
        for (var entry : manager.listResources(DIRECTORY, path -> path.getPath().endsWith(".json")).entrySet()) {
            Identifier id = Identifier.fromNamespaceAndPath(entry.getKey().getNamespace(),
                    entry.getKey().getPath().substring((DIRECTORY + "/").length(), entry.getKey().getPath().length() - 5));
            try {
                JsonElement element;
                try (var reader = entry.getValue().openAsReader()) {
                    element = JsonParser.parseReader(reader);
                }
                if (!element.isJsonObject()) continue;
                JsonObject object = element.getAsJsonObject();
                int value = object.has("value") ? object.get("value").getAsInt() : 0;
                int limit = object.has("limit") ? object.get("limit").getAsInt() : 0;
                String limitReset = object.has("limit_reset") ? object.get("limit_reset").getAsString().trim().toLowerCase(java.util.Locale.ROOT) : "none";
                List<Identifier> unlockTasks = new ArrayList<>();
                if (object.has("unlock_tasks")) for (JsonElement task : object.getAsJsonArray("unlock_tasks")) unlockTasks.add(Identifier.parse(task.getAsString().trim()));
                List<Identifier> unlockDisks = new ArrayList<>();
                if (object.has("unlock_disks")) for (JsonElement disk : object.getAsJsonArray("unlock_disks")) unlockDisks.add(Identifier.parse(disk.getAsString().trim()));
                List<Identifier> unlockCommands = new ArrayList<>();
                if (object.has("unlock_commands")) for (JsonElement command : object.getAsJsonArray("unlock_commands")) unlockCommands.add(Identifier.parse(command.getAsString().trim()));
                String unlockMode = object.has("unlock_mode") ? object.get("unlock_mode").getAsString().trim().toLowerCase(java.util.Locale.ROOT) : "all";
                boolean hiddenUntilUnlocked = !object.has("hidden_until_unlocked") || !object.get("hidden_until_unlocked").isJsonPrimitive()
                        || object.get("hidden_until_unlocked").getAsBoolean();
                boolean notifyOnUnlock = !object.has("notify_on_unlock") || !object.get("notify_on_unlock").isJsonPrimitive()
                        || object.get("notify_on_unlock").getAsBoolean();
                List<Identifier> items = new ArrayList<>();
                List<Identifier> tags = new ArrayList<>();
                if (object.has("item")) items.add(Identifier.parse(object.get("item").getAsString().trim()));
                if (object.has("items")) for (JsonElement item : object.getAsJsonArray("items")) items.add(Identifier.parse(item.getAsString().trim()));
                if (object.has("tag")) tags.add(tagId(object.get("tag").getAsString()));
                if (object.has("tags")) for (JsonElement tag : object.getAsJsonArray("tags")) tags.add(tagId(tag.getAsString()));
                if (value < 1 || limit < 0 || !List.of("none", "minecraft_day", "minecraft_week").contains(limitReset)
                        || (!unlockMode.equals("all") && !unlockMode.equals("any")) || (items.isEmpty() && tags.isEmpty())) {
                    AntOS.LOGGER.warn("Ignoring Antazon sell rule {} because its value, limit, reset period, or item/tag fields are invalid", id);
                    continue;
                }
                boolean greenTint = !object.has("green_tint") || !object.get("green_tint").isJsonPrimitive() || object.get("green_tint").getAsBoolean();
                boolean renderMobFromSpawnEgg = !object.has("render_mob_from_spawn_egg") || !object.get("render_mob_from_spawn_egg").isJsonPrimitive()
                        || object.get("render_mob_from_spawn_egg").getAsBoolean();
                loaded.add(new Source(id, List.copyOf(items), List.copyOf(tags), value, limit, limitReset, List.copyOf(unlockTasks), unlockMode,
                        hiddenUntilUnlocked, notifyOnUnlock, greenTint, renderMobFromSpawnEgg, List.copyOf(unlockDisks), List.copyOf(unlockCommands)));
            } catch (Exception exception) {
                AntOS.LOGGER.warn("Ignoring malformed Antazon sell rule {}", id, exception);
            }
        }
        loaded.sort(Comparator.comparing(source -> source.id().toString()));
        return List.copyOf(loaded);
    }

    private static Identifier tagId(String value) {
        String trimmed = value.trim();
        return Identifier.parse(trimmed.startsWith("#") ? trimmed.substring(1) : trimmed);
    }

    @Override
    protected void apply(List<Source> loaded, ResourceManager manager, ProfilerFiller profiler) {
        synchronized (AntazonSellData.class) {
            sources = loaded;
            rules = null;
        }
        AntOS.LOGGER.info("Loaded {} Antazon sell rule files", loaded.size());
    }

    public record Source(Identifier id, List<Identifier> items, List<Identifier> tags, int value, int limit, String limitReset,
                         List<Identifier> unlockTasks, String unlockMode, boolean hiddenUntilUnlocked, boolean notifyOnUnlock,
                         boolean greenTint, boolean renderMobFromSpawnEgg, List<Identifier> unlockDisks, List<Identifier> unlockCommands) {
        public Source(Identifier id, List<Identifier> items, List<Identifier> tags, int value, int limit, String limitReset,
                      List<Identifier> unlockTasks, String unlockMode, boolean hiddenUntilUnlocked, boolean notifyOnUnlock, boolean greenTint, boolean renderMobFromSpawnEgg,
                      List<Identifier> unlockDisks) {
            this(id, items, tags, value, limit, limitReset, unlockTasks, unlockMode, hiddenUntilUnlocked, notifyOnUnlock, greenTint, renderMobFromSpawnEgg, unlockDisks, List.of());
        }

        public Source(Identifier id, List<Identifier> items, List<Identifier> tags, int value, int limit, String limitReset,
                      List<Identifier> unlockTasks, String unlockMode, boolean hiddenUntilUnlocked, boolean notifyOnUnlock, boolean greenTint, boolean renderMobFromSpawnEgg) {
            this(id, items, tags, value, limit, limitReset, unlockTasks, unlockMode, hiddenUntilUnlocked, notifyOnUnlock, greenTint, renderMobFromSpawnEgg, List.of(), List.of());
        }
    }

    public record Rule(Identifier item, int value, boolean greenTint, boolean renderMobFromSpawnEgg, int limit, String limitReset, String limitKey,
                       List<Identifier> unlockTasks, String unlockMode, boolean hiddenUntilUnlocked, boolean notifyOnUnlock, List<Identifier> unlockDisks,
                       List<Identifier> unlockCommands) {
        public Rule(Identifier item, int value, boolean greenTint, boolean renderMobFromSpawnEgg, int limit, String limitReset, String limitKey,
                    List<Identifier> unlockTasks, String unlockMode, boolean hiddenUntilUnlocked, boolean notifyOnUnlock, List<Identifier> unlockDisks) {
            this(item, value, greenTint, renderMobFromSpawnEgg, limit, limitReset, limitKey, unlockTasks, unlockMode, hiddenUntilUnlocked, notifyOnUnlock, unlockDisks, List.of());
        }

        public Rule(Identifier item, int value, boolean greenTint, boolean renderMobFromSpawnEgg, int limit, String limitReset, String limitKey,
                    List<Identifier> unlockTasks, String unlockMode, boolean hiddenUntilUnlocked, boolean notifyOnUnlock) {
            this(item, value, greenTint, renderMobFromSpawnEgg, limit, limitReset, limitKey, unlockTasks, unlockMode, hiddenUntilUnlocked, notifyOnUnlock, List.of(), List.of());
        }
    }
}
