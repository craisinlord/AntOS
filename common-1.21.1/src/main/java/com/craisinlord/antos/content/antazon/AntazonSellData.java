package com.craisinlord.antos.content.antazon;

import com.craisinlord.antos.AntOS;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.Map;

/** Data-driven item values for Antazon Sell. */
public final class AntazonSellData extends SimplePreparableReloadListener<Map<ResourceLocation, AntazonSellData.Rule>> {
    private static final String DIRECTORY = "antazon/sell";
    private static final AntazonSellData INSTANCE = new AntazonSellData();
    private static volatile Map<ResourceLocation, Rule> rules = Map.of();

    private AntazonSellData() { }

    public static AntazonSellData instance() { return INSTANCE; }

    public static Rule rule(ResourceLocation item) { return rules.get(item); }

    public static java.util.List<Rule> rules() { return rules.values().stream().sorted(java.util.Comparator.comparing(value -> value.item().toString())).toList(); }

    @Override
    protected Map<ResourceLocation, Rule> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, Rule> loaded = new HashMap<>();
        for (var entry : manager.listResources(DIRECTORY, path -> path.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(entry.getKey().getNamespace(),
                    entry.getKey().getPath().substring((DIRECTORY + "/").length(), entry.getKey().getPath().length() - 5));
            try {
                JsonElement element;
                try (var reader = entry.getValue().openAsReader()) {
                    element = JsonParser.parseReader(reader);
                }
                if (!element.isJsonObject()) continue;
                String itemValue = element.getAsJsonObject().has("item") ? element.getAsJsonObject().get("item").getAsString() : "";
                int value = element.getAsJsonObject().has("value") ? element.getAsJsonObject().get("value").getAsInt() : 0;
                boolean greenTint = !element.getAsJsonObject().has("green_tint") || !element.getAsJsonObject().get("green_tint").isJsonPrimitive()
                        || element.getAsJsonObject().get("green_tint").getAsBoolean();
                boolean renderMobFromSpawnEgg = !element.getAsJsonObject().has("render_mob_from_spawn_egg") || !element.getAsJsonObject().get("render_mob_from_spawn_egg").isJsonPrimitive()
                        || element.getAsJsonObject().get("render_mob_from_spawn_egg").getAsBoolean();
                ResourceLocation item = ResourceLocation.parse(itemValue);
                if (BuiltInRegistries.ITEM.get(item) == net.minecraft.world.item.Items.AIR || value < 1) continue;
                loaded.put(item, new Rule(item, value, greenTint, renderMobFromSpawnEgg));
            } catch (Exception exception) {
                AntOS.LOGGER.warn("Ignoring malformed Antazon sell rule {}", id, exception);
            }
        }
        return Map.copyOf(loaded);
    }

    @Override
    protected void apply(Map<ResourceLocation, Rule> loaded, ResourceManager manager, ProfilerFiller profiler) {
        rules = loaded;
        AntOS.LOGGER.info("Loaded {} Antazon sell rules", loaded.size());
    }

    public record Rule(ResourceLocation item, int value, boolean greenTint, boolean renderMobFromSpawnEgg) { }
}
