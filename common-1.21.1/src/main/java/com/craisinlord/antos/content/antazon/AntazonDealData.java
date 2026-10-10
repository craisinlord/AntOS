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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class AntazonDealData extends SimplePreparableReloadListener<List<AntazonDealData.Campaign>> {
    private static final String DIRECTORY = "antazon/deal";
    private static final AntazonDealData INSTANCE = new AntazonDealData();
    private static volatile List<Campaign> campaigns = List.of();
    private static volatile long version = 1L;

    private AntazonDealData() { }

    public static AntazonDealData instance() { return INSTANCE; }

    public static List<Campaign> campaigns() { return campaigns; }

    public static long version() { return version; }

    public static Campaign campaign(ResourceLocation id) {
        for (Campaign campaign : campaigns) if (campaign.id().equals(id)) return campaign;
        return null;
    }

    @Override
    protected List<Campaign> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, Campaign> loaded = new HashMap<>();
        for (Map.Entry<ResourceLocation, net.minecraft.server.packs.resources.Resource> entry :
                manager.listResources(DIRECTORY, path -> path.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation id = resourceId(entry.getKey());
            try {
                JsonElement element;
                try (var reader = entry.getValue().openAsReader()) {
                    element = JsonParser.parseReader(reader);
                }
                Campaign campaign = parse(id, element);
                if (campaign != null && loaded.putIfAbsent(id, campaign) != null) AntOS.LOGGER.warn("Ignoring duplicate Antazon deal campaign {}", id);
            } catch (Exception exception) {
                AntOS.LOGGER.error("Ignoring malformed Antazon deal campaign {}", id, exception);
            }
        }
        return loaded.values().stream()
                .sorted(Comparator.comparingInt(Campaign::priority).reversed().thenComparing(campaign -> campaign.id().toString()))
                .toList();
    }

    @Override
    protected void apply(List<Campaign> prepared, ResourceManager manager, ProfilerFiller profiler) {
        long next = new java.util.Random().nextLong();
        campaigns = prepared;
        version = next == 0L ? 1L : next;
        AntazonDeals.clear();
        AntOS.LOGGER.info("Loaded {} Antazon deal campaigns", prepared.size());
    }

    private static Campaign parse(ResourceLocation id, JsonElement element) {
        if (!element.isJsonObject()) {
            AntOS.LOGGER.warn("Ignoring Antazon deal campaign {} because the root is not an object", id);
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        String label = string(object, "label", "Deal").trim();
        if (label.isBlank()) label = "Deal";
        boolean enabled = !object.has("enabled") || !object.get("enabled").isJsonPrimitive() || object.get("enabled").getAsBoolean();
        if (object.has("every_minecraft_days") && object.has("every_real_hours")) {
            AntOS.LOGGER.warn("Ignoring Antazon deal campaign {} because it sets both every_minecraft_days and every_real_hours", id);
            return null;
        }
        long everyMinecraftDays = boundedLong(object, "every_minecraft_days", 1L, 1L, 1_000_000L);
        long everyRealHours = boundedLong(object, "every_real_hours", 0L, 0L, 1_000_000L);
        String order = string(object, "order", "shuffled").toLowerCase(Locale.ROOT);
        if (!order.equals("shuffled") && !order.equals("sequential")) {
            AntOS.LOGGER.warn("Ignoring Antazon deal campaign {} because order must be shuffled or sequential", id);
            return null;
        }
        int discountMin;
        int discountMax;
        JsonElement discount = object.get("discount_percent");
        try {
            if (discount != null && discount.isJsonObject()) {
                discountMin = discount.getAsJsonObject().get("min").getAsInt();
                discountMax = discount.getAsJsonObject().get("max").getAsInt();
            } else if (discount != null && discount.isJsonPrimitive()) {
                discountMin = discount.getAsInt();
                discountMax = discountMin;
            } else throw new IllegalArgumentException();
        } catch (RuntimeException exception) {
            AntOS.LOGGER.warn("Ignoring Antazon deal campaign {} because discount_percent must be a number or an object with min and max", id);
            return null;
        }
        if (discountMin < 1 || discountMax > 90 || discountMin > discountMax) {
            AntOS.LOGGER.warn("Ignoring Antazon deal campaign {} because discount_percent must be between 1 and 90 with min no greater than max", id);
            return null;
        }
        JsonObject rules = object.has("candidates") && object.get("candidates").isJsonObject() ? object.getAsJsonObject("candidates") : new JsonObject();
        Candidates candidates;
        try {
            candidates = new Candidates(ids(rules.get("products")), lowered(rules.get("categories")), lowered(rules.get("tags")),
                    ids(rules.get("exclude_products")), lowered(rules.get("exclude_categories")), lowered(rules.get("exclude_tags")),
                    boundedInt(rules, "min_price", 0, 0, Integer.MAX_VALUE), boundedInt(rules, "max_price", 0, 0, Integer.MAX_VALUE));
        } catch (RuntimeException exception) {
            AntOS.LOGGER.warn("Ignoring Antazon deal campaign {} because candidates has an invalid product ID", id);
            return null;
        }
        return new Campaign(id, label, enabled, boundedInt(object, "picks", 1, 1, 1_000), everyMinecraftDays, everyRealHours,
                order.equals("shuffled"), discountMin, discountMax, candidates, boundedInt(object, "max_per_category", 0, 0, 1_000),
                boundedInt(object, "deal_limit", 0, 0, 1_000_000), boundedInt(object, "priority", 0, -1_000_000, 1_000_000));
    }

    private static Set<ResourceLocation> ids(JsonElement element) {
        Set<ResourceLocation> result = new LinkedHashSet<>();
        for (String value : strings(element)) result.add(ResourceLocation.parse(value.trim()));
        return Set.copyOf(result);
    }

    private static Set<String> lowered(JsonElement element) {
        Set<String> result = new LinkedHashSet<>();
        for (String value : strings(element)) result.add(value.trim().toLowerCase(Locale.ROOT));
        return Set.copyOf(result);
    }

    private static List<String> strings(JsonElement element) {
        if (element == null || !element.isJsonArray()) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonElement value : element.getAsJsonArray()) if (value.isJsonPrimitive() && !value.getAsString().isBlank()) result.add(value.getAsString());
        return result;
    }

    private static ResourceLocation resourceId(ResourceLocation path) {
        String prefix = DIRECTORY + "/";
        String value = path.getPath().startsWith(prefix) ? path.getPath().substring(prefix.length()) : path.getPath();
        return ResourceLocation.fromNamespaceAndPath(path.getNamespace(), value.substring(0, value.length() - 5));
    }

    private static String string(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    private static int boundedInt(JsonObject object, String key, int fallback, int min, int max) {
        try {
            return object.has(key) ? Math.max(min, Math.min(max, object.get(key).getAsInt())) : fallback;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static long boundedLong(JsonObject object, String key, long fallback, long min, long max) {
        try {
            return object.has(key) ? Math.max(min, Math.min(max, object.get(key).getAsLong())) : fallback;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    public record Campaign(ResourceLocation id, String label, boolean enabled, int picks, long everyMinecraftDays, long everyRealHours,
                           boolean shuffled, int discountMin, int discountMax, Candidates candidates, int maxPerCategory, int dealLimit, int priority) {
        public long period(long day, long nowMillis) {
            return AntazonRotation.period(everyMinecraftDays, everyRealHours, day, nowMillis);
        }

        public long endsInMillis(long gameTime, long nowMillis) {
            return AntazonRotation.endsInMillis(everyMinecraftDays, everyRealHours, gameTime, nowMillis);
        }

        public boolean realTime() {
            return everyRealHours > 0L;
        }
    }

    public record Candidates(Set<ResourceLocation> products, Set<String> categories, Set<String> tags,
                             Set<ResourceLocation> excludeProducts, Set<String> excludeCategories, Set<String> excludeTags,
                             int minPrice, int maxPrice) {
        public boolean includesEverything() {
            return products.isEmpty() && categories.isEmpty() && tags.isEmpty();
        }
    }
}
