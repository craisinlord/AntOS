package com.craisinlord.antos.content.antazon;

import com.craisinlord.antos.AntOS;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class AntazonData extends SimplePreparableReloadListener<Map<ResourceLocation, AntazonData.Product>> {
    private static final String DIRECTORY = "antazon/product";
    private static final AntazonData INSTANCE = new AntazonData();
    private static volatile Map<ResourceLocation, Product> products = Map.of();
    private static volatile Catalog catalog = new Catalog(0L, List.of(), Map.of());

    private AntazonData() {
    }

    public static AntazonData instance() {
        return INSTANCE;
    }

    /** Every product sorted by id; sorted once per reload rather than per storefront request. */
    public static List<Product> products() {
        return catalog.sorted();
    }

    /** Changes on every reload, so clients can keep the static catalogue until the datapacks change. */
    public static long catalogVersion() {
        return catalog.version();
    }

    /**
     * The player-independent half of a storefront row, built once per reload. Shared: callers must not modify it.
     */
    public static JsonObject catalogRow(ResourceLocation id) {
        return catalog.rows().get(id);
    }

    public static Product product(ResourceLocation id) {
        return products.get(id);
    }

    @Override
    protected Map<ResourceLocation, Product> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, Product> loaded = new HashMap<>();
        for (Map.Entry<ResourceLocation, net.minecraft.server.packs.resources.Resource> entry :
                manager.listResources(DIRECTORY, path -> path.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation id = resourceId(entry.getKey());
            try {
                JsonElement element;
                try (var reader = entry.getValue().openAsReader()) {
                    element = JsonParser.parseReader(reader);
                }
                Product product = parse(id, element);
                if (product != null) {
                    if (loaded.putIfAbsent(id, product) != null) AntOS.LOGGER.warn("Ignoring duplicate Antazon product {}", id);
                }
            } catch (Exception exception) {
                AntOS.LOGGER.error("Ignoring malformed Antazon product {}", id, exception);
            }
        }
        return Map.copyOf(loaded);
    }

    @Override
    protected void apply(Map<ResourceLocation, Product> loaded, ResourceManager manager, ProfilerFiller profiler) {
        List<Product> sorted = loaded.values().stream().sorted(java.util.Comparator.comparing(product -> product.id().toString())).toList();
        Map<ResourceLocation, JsonObject> rows = new HashMap<>();
        for (Product product : sorted) rows.put(product.id(), catalogRow(product));
        long version = new java.util.Random().nextLong();
        products = loaded;
        catalog = new Catalog(version == 0L ? 1L : version, sorted, Map.copyOf(rows));
        AntOS.LOGGER.info("Loaded {} Antazon products", loaded.size());
    }

    private static JsonObject catalogRow(Product product) {
        JsonObject row = new JsonObject();
        row.addProperty("id", product.id().toString());
        row.addProperty("name", product.name());
        row.addProperty("description", product.description());
        row.addProperty("category", product.category());
        row.addProperty("enabled", product.enabled());
        row.addProperty("green_tint", product.greenTint());
        row.addProperty("render_mob_from_spawn_egg", product.renderMobFromSpawnEgg());
        Availability availability = product.availability();
        row.addProperty("stock", availability.serverStock());
        row.addProperty("restock_days", availability.restockMinecraftDays());
        row.addProperty("limit", availability.playerLimit());
        row.addProperty("limit_reset", availability.playerLimitReset());
        row.addProperty("deal_label", product.deal().label());
        row.addProperty("deal_discount", product.deal().discountPercent());
        row.addProperty("unlock_mode", product.unlockMode());
        JsonArray unlockTasks = new JsonArray();
        for (ResourceLocation task : product.unlockTasks()) unlockTasks.add(task.toString());
        row.add("unlock_tasks", unlockTasks);
        JsonArray tags = new JsonArray();
        for (String tag : product.tags()) tags.add(tag);
        row.add("tags", tags);
        row.add("thumbnail", assetJson(product.thumbnail()));
        JsonArray gallery = new JsonArray();
        for (PreviewAsset asset : product.gallery()) gallery.add(assetJson(asset));
        row.add("gallery", gallery);
        row.addProperty("quantity", product.quantity());
        JsonArray rewards = new JsonArray();
        for (Reward reward : product.rewards()) {
            JsonObject rewardRow = new JsonObject();
            rewardRow.addProperty("item", reward.item().toString());
            rewardRow.addProperty("count", reward.count());
            rewards.add(rewardRow);
        }
        row.add("rewards", rewards);
        JsonArray payments = new JsonArray();
        for (Payment payment : product.payments()) {
            JsonObject paymentRow = new JsonObject();
            paymentRow.addProperty("type", payment.type());
            paymentRow.addProperty("resource", payment.resource());
            paymentRow.addProperty("amount", payment.amount());
            payments.add(paymentRow);
        }
        row.add("payments", payments);
        JsonArray reviews = new JsonArray();
        for (Review review : product.reviews()) {
            JsonObject reviewRow = new JsonObject();
            reviewRow.addProperty("author", review.author());
            reviewRow.addProperty("title", review.title());
            reviewRow.addProperty("body", review.body());
            reviewRow.addProperty("rating", review.rating());
            reviewRow.addProperty("badge", review.badge());
            reviews.add(reviewRow);
        }
        row.add("reviews", reviews);
        return row;
    }

    private static JsonObject assetJson(PreviewAsset asset) {
        JsonObject object = new JsonObject();
        object.addProperty("item", asset.item());
        object.addProperty("entity", asset.entity());
        return object;
    }

    private static Product parse(ResourceLocation id, JsonElement element) {
        if (!element.isJsonObject()) {
            AntOS.LOGGER.warn("Ignoring Antazon product {} because the root is not an object", id);
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        String name = string(object, "name", "").trim();
        if (name.isBlank()) {
            AntOS.LOGGER.warn("Ignoring Antazon product {} because name is missing", id);
            return null;
        }
        String description = string(object, "description", "");
        String category = string(object, "category", "general").trim();
        if (category.isBlank()) category = "general";
        List<String> tags = strings(object.get("tags"));
        PreviewAsset thumbnail = parseAsset(object.getAsJsonObject("thumbnail"));
        List<PreviewAsset> gallery = parseAssets(object.get("gallery"));
        int quantity = boundedInt(object, "quantity", 1, 1, 1_000_000);
        List<Payment> payments = parsePayments(id, object.get("payments"));
        if (payments.isEmpty()) {
            AntOS.LOGGER.warn("Ignoring Antazon product {} because it has no valid payments", id);
            return null;
        }
        List<ResourceLocation> taskIds = resourceIds(object.get("unlock_tasks"), id);
        String unlockMode = string(object, "unlock_mode", "all").toLowerCase(Locale.ROOT);
        if (!unlockMode.equals("all") && !unlockMode.equals("any")) {
            AntOS.LOGGER.warn("Ignoring Antazon product {} because unlock_mode must be all or any", id);
            return null;
        }
        Availability availability = parseAvailability(id, object.getAsJsonObject("availability"));
        if (availability == null) return null;
        List<Reward> rewards = parseRewards(id, object.get("rewards"));
        if (rewards.isEmpty()) {
            AntOS.LOGGER.warn("Ignoring Antazon product {} because it has no valid rewards", id);
            return null;
        }
        String delivery = string(object, "delivery", "falling_chest").toLowerCase(Locale.ROOT);
        if (!delivery.equals("falling_chest")) {
            AntOS.LOGGER.warn("Ignoring Antazon product {} because delivery '{}' is unsupported", id, delivery);
            return null;
        }
        boolean enabled = !object.has("enabled") || object.get("enabled").getAsBoolean();
        boolean greenTint = !object.has("green_tint") || !object.get("green_tint").isJsonPrimitive() || object.get("green_tint").getAsBoolean();
        List<Review> reviews = parseReviews(id, object.get("reviews"));
        Deal deal = parseDeal(id, object.getAsJsonObject("deal"));
        boolean renderMobFromSpawnEgg = !object.has("render_mob_from_spawn_egg") || !object.get("render_mob_from_spawn_egg").isJsonPrimitive() || object.get("render_mob_from_spawn_egg").getAsBoolean();
        return new Product(id, name, description, category, tags, thumbnail, gallery, quantity, payments, unlockMode, taskIds, availability, rewards, reviews, deal, delivery, enabled, greenTint, renderMobFromSpawnEgg);
    }

    private static PreviewAsset parseAsset(JsonObject object) {
        if (object == null) return new PreviewAsset("", "");
        return new PreviewAsset(string(object, "item", "").trim(), string(object, "entity", "").trim());
    }

    private static List<PreviewAsset> parseAssets(JsonElement element) {
        if (element == null || !element.isJsonArray()) return List.of();
        List<PreviewAsset> assets = new ArrayList<>();
        for (JsonElement value : element.getAsJsonArray()) if (value.isJsonObject() && assets.size() < 12) {
            PreviewAsset asset = parseAsset(value.getAsJsonObject());
            if (!asset.item().isBlank() || !asset.entity().isBlank()) assets.add(asset);
        }
        return List.copyOf(assets);
    }

    private static List<Payment> parsePayments(ResourceLocation productId, JsonElement element) {
        if (element == null || !element.isJsonArray()) return List.of();
        List<Payment> payments = new ArrayList<>();
        for (JsonElement paymentElement : element.getAsJsonArray()) {
            if (!paymentElement.isJsonObject()) continue;
            JsonObject payment = paymentElement.getAsJsonObject();
            String type = string(payment, "type", "").trim();
            String resource = string(payment, "resource", "").trim();
            int amount = boundedInt(payment, "amount", 0, 1, 1_000_000_000);
            if (!type.isBlank() && !resource.isBlank() && amount > 0) payments.add(new Payment(type, resource, amount));
        }
        return List.copyOf(payments);
    }

    private static Availability parseAvailability(ResourceLocation id, JsonObject object) {
        if (object == null) object = new JsonObject();
        int stock = boundedInt(object, "server_stock", 0, 0, 1_000_000);
        long restockDays = boundedLong(object, "restock_after_minecraft_days", 0L, 0L, 1_000_000L);
        long cooldownDays = boundedLong(object, "cooldown_minecraft_days", 0L, 0L, 1_000_000L);
        int playerLimit = boundedInt(object, "player_limit", 0, 0, 1_000_000);
        String reset = string(object, "player_limit_reset", "none").toLowerCase(Locale.ROOT);
        if (!reset.equals("none") && !reset.equals("minecraft_day") && !reset.equals("minecraft_week")) {
            AntOS.LOGGER.warn("Ignoring Antazon product {} because player_limit_reset is invalid", id);
            return null;
        }
        return new Availability(stock, restockDays, cooldownDays, playerLimit, reset);
    }

    private static List<Reward> parseRewards(ResourceLocation productId, JsonElement element) {
        if (element == null || !element.isJsonArray()) return List.of();
        List<Reward> rewards = new ArrayList<>();
        int total = 0;
        for (JsonElement value : element.getAsJsonArray()) {
            if (!value.isJsonObject() || rewards.size() >= 108) continue;
            JsonObject object = value.getAsJsonObject();
            try {
                ResourceLocation itemId = ResourceLocation.parse(string(object, "item", ""));
                int count = boundedInt(object, "count", 1, 1, 108);
                if (BuiltInRegistries.ITEM.get(itemId) == net.minecraft.world.item.Items.AIR || total + count > 108) {
                    AntOS.LOGGER.warn("Ignoring invalid reward on Antazon product {}", productId);
                    continue;
                }
                rewards.add(new Reward(itemId, count));
                total += count;
            } catch (RuntimeException exception) {
                AntOS.LOGGER.warn("Ignoring malformed reward on Antazon product {}", productId);
            }
        }
        return List.copyOf(rewards);
    }

    private static List<Review> parseReviews(ResourceLocation productId, JsonElement element) {
        if (element == null || !element.isJsonArray()) return List.of();
        List<Review> reviews = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonElement value : element.getAsJsonArray()) {
            if (!value.isJsonObject() || reviews.size() >= 32) continue;
            JsonObject object = value.getAsJsonObject();
            String id = string(object, "id", "").trim();
            String author = string(object, "author", "Nest member").trim();
            String title = string(object, "title", "").trim();
            String body = string(object, "body", "").trim();
            int rating = boundedInt(object, "rating", 5, 1, 5);
            if (id.isBlank() || !ids.add(id) || author.isBlank() || body.isBlank()) {
                AntOS.LOGGER.warn("Ignoring invalid review '{}' on Antazon product {}", id, productId);
                continue;
            }
            reviews.add(new Review(id, author, title, body, rating, string(object, "badge", "")));
        }
        return List.copyOf(reviews);
    }

    private static Deal parseDeal(ResourceLocation productId, JsonObject object) {
        if (object == null) return new Deal(false, "", 0, 0);
        String label = string(object, "label", "Nest Deal").trim();
        int discount = boundedInt(object, "discount_percent", 0, 0, 90);
        long cycle = boundedLong(object, "cycle_minecraft_days", 1L, 1L, 1_000_000L);
        boolean enabled = object.has("enabled") && object.get("enabled").getAsBoolean();
        if (enabled && (label.isBlank() || discount <= 0)) {
            AntOS.LOGGER.warn("Disabling invalid deal on Antazon product {}", productId);
            return new Deal(false, "", 0, 0);
        }
        return new Deal(enabled, label, discount, cycle);
    }

    private static ResourceLocation resourceId(ResourceLocation path) {
        String prefix = DIRECTORY + "/";
        String value = path.getPath().startsWith(prefix) ? path.getPath().substring(prefix.length()) : path.getPath();
        return ResourceLocation.fromNamespaceAndPath(path.getNamespace(), value.substring(0, value.length() - 5));
    }

    private static List<ResourceLocation> resourceIds(JsonElement element, ResourceLocation productId) {
        if (element == null || !element.isJsonArray()) return List.of();
        List<ResourceLocation> result = new ArrayList<>();
        for (JsonElement value : element.getAsJsonArray()) {
            if (!value.isJsonPrimitive()) continue;
            try {
                result.add(ResourceLocation.parse(value.getAsString()));
            } catch (RuntimeException exception) {
                AntOS.LOGGER.warn("Ignoring invalid unlock task on Antazon product {}", productId);
            }
        }
        return List.copyOf(result);
    }

    private static List<String> strings(JsonElement element) {
        if (element == null || !element.isJsonArray()) return List.of();
        List<String> result = new ArrayList<>();
        JsonArray array = element.getAsJsonArray();
        for (JsonElement value : array) if (value.isJsonPrimitive() && !value.getAsString().isBlank()) result.add(value.getAsString());
        return List.copyOf(result);
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

    public record Product(ResourceLocation id, String name, String description, String category, List<String> tags,
                          PreviewAsset thumbnail, List<PreviewAsset> gallery, int quantity, List<Payment> payments, String unlockMode, List<ResourceLocation> unlockTasks,
                          Availability availability, List<Reward> rewards, List<Review> reviews, Deal deal, String delivery, boolean enabled, boolean greenTint, boolean renderMobFromSpawnEgg) {
    }

    private record Catalog(long version, List<Product> sorted, Map<ResourceLocation, JsonObject> rows) {
    }

    public record PreviewAsset(String item, String entity) {
    }

    public record Payment(String type, String resource, int amount) {
    }

    public record Reward(ResourceLocation item, int count) {
    }

    public record Review(String id, String author, String title, String body, int rating, String badge) {
    }

    public record Deal(boolean enabled, String label, int discountPercent, long cycleMinecraftDays) {
        public boolean active(long day) {
            return enabled && cycleMinecraftDays > 0 && Math.floorMod(day, cycleMinecraftDays) == 0;
        }
    }

    public record Availability(int serverStock, long restockMinecraftDays, long cooldownMinecraftDays,
                               int playerLimit, String playerLimitReset) {
    }
}
