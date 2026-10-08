package com.craisinlord.antos.content.antazon;

import com.craisinlord.antos.AntOS;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
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
    private static volatile Map<ResourceLocation, Rule> rules;

    private AntazonSellData() { }

    public static AntazonSellData instance() { return INSTANCE; }

    public static Rule rule(ResourceLocation item) { return resolved().get(item); }

    public static List<Rule> rules() { return resolved().values().stream().sorted(Comparator.comparing(value -> value.item().toString())).toList(); }

    private static Map<ResourceLocation, Rule> resolved() {
        Map<ResourceLocation, Rule> current = rules;
        if (current != null) return current;
        synchronized (AntazonSellData.class) {
            if (rules == null) rules = resolve(sources);
            return rules;
        }
    }

    private static Map<ResourceLocation, Rule> resolve(List<Source> loaded) {
        Map<ResourceLocation, Rule> direct = new HashMap<>();
        Map<ResourceLocation, Rule> tagged = new HashMap<>();
        for (Source source : loaded) {
            for (ResourceLocation item : AntazonTags.items(source.items(), List.of()))
                direct.putIfAbsent(item, new Rule(item, source.value(), source.greenTint(), source.renderMobFromSpawnEgg()));
            for (ResourceLocation item : AntazonTags.items(List.of(), source.tags()))
                tagged.putIfAbsent(item, new Rule(item, source.value(), source.greenTint(), source.renderMobFromSpawnEgg()));
        }
        tagged.putAll(direct);
        return Map.copyOf(tagged);
    }

    @Override
    protected List<Source> prepare(ResourceManager manager, ProfilerFiller profiler) {
        List<Source> loaded = new ArrayList<>();
        for (var entry : manager.listResources(DIRECTORY, path -> path.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(entry.getKey().getNamespace(),
                    entry.getKey().getPath().substring((DIRECTORY + "/").length(), entry.getKey().getPath().length() - 5));
            try {
                JsonElement element;
                try (var reader = entry.getValue().openAsReader()) {
                    element = JsonParser.parseReader(reader);
                }
                if (!element.isJsonObject()) continue;
                JsonObject object = element.getAsJsonObject();
                int value = object.has("value") ? object.get("value").getAsInt() : 0;
                List<ResourceLocation> items = new ArrayList<>();
                List<ResourceLocation> tags = new ArrayList<>();
                if (object.has("item")) items.add(ResourceLocation.parse(object.get("item").getAsString().trim()));
                if (object.has("items")) for (JsonElement item : object.getAsJsonArray("items")) items.add(ResourceLocation.parse(item.getAsString().trim()));
                if (object.has("tag")) tags.add(tagId(object.get("tag").getAsString()));
                if (object.has("tags")) for (JsonElement tag : object.getAsJsonArray("tags")) tags.add(tagId(tag.getAsString()));
                if (value < 1 || (items.isEmpty() && tags.isEmpty())) {
                    AntOS.LOGGER.warn("Ignoring Antazon sell rule {} because it needs a positive value and an item or tag", id);
                    continue;
                }
                boolean greenTint = !object.has("green_tint") || !object.get("green_tint").isJsonPrimitive() || object.get("green_tint").getAsBoolean();
                boolean renderMobFromSpawnEgg = !object.has("render_mob_from_spawn_egg") || !object.get("render_mob_from_spawn_egg").isJsonPrimitive()
                        || object.get("render_mob_from_spawn_egg").getAsBoolean();
                loaded.add(new Source(id, List.copyOf(items), List.copyOf(tags), value, greenTint, renderMobFromSpawnEgg));
            } catch (Exception exception) {
                AntOS.LOGGER.warn("Ignoring malformed Antazon sell rule {}", id, exception);
            }
        }
        loaded.sort(Comparator.comparing(source -> source.id().toString()));
        return List.copyOf(loaded);
    }

    private static ResourceLocation tagId(String value) {
        String trimmed = value.trim();
        return ResourceLocation.parse(trimmed.startsWith("#") ? trimmed.substring(1) : trimmed);
    }

    @Override
    protected void apply(List<Source> loaded, ResourceManager manager, ProfilerFiller profiler) {
        synchronized (AntazonSellData.class) {
            sources = loaded;
            rules = null;
        }
        AntOS.LOGGER.info("Loaded {} Antazon sell rule files", loaded.size());
    }

    public record Source(ResourceLocation id, List<ResourceLocation> items, List<ResourceLocation> tags, int value,
                         boolean greenTint, boolean renderMobFromSpawnEgg) { }

    public record Rule(ResourceLocation item, int value, boolean greenTint, boolean renderMobFromSpawnEgg) { }
}
