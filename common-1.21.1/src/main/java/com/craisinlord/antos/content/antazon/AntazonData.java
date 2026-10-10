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
    private static volatile Map<ResourceLocation, Product> loaded = Map.of();
    private static volatile long loadedVersion = 0L;
    private static volatile Catalog catalog;
    private static final Map<ResourceLocation, ActiveSet> ACTIVE = new java.util.concurrent.ConcurrentHashMap<>();

    private AntazonData() {
    }

    public static AntazonData instance() {
        return INSTANCE;
    }

    public static List<Product> products() {
        return catalog().sorted();
    }

    public static long version() {
        return catalog().version();
    }

    public static String catalogVersion(long day) {
        Catalog current = catalog();
        long stamp = current.version();
        for (Product template : current.rotatingTemplates()) stamp = stamp * 31L + rotationPeriod(template, day);
        return Long.toString(stamp);
    }

    public static JsonObject catalogRow(ResourceLocation id) {
        return catalog().rows().get(id);
    }

    public static Product product(ResourceLocation id) {
        return catalog().products().get(id);
    }

    public static boolean availableOn(Product product, long day) {
        PoolEntry entry = product.poolEntry();
        if (entry == null || product.itemPool().rotation() == null) return true;
        Product template = loaded.get(entry.template());
        if (template == null) return false;
        long period = rotationPeriod(template, day);
        ActiveSet active = ACTIVE.get(template.id());
        if (active == null || active.period() != period) {
            active = new ActiveSet(period, activeIndices(template.id(), template.itemPool().rotation(), entry.poolSize(), period));
            ACTIVE.put(template.id(), active);
        }
        return active.indices().contains(entry.index());
    }

    public static long rotatesInMillis(Product product, long gameTime) {
        if (product.poolEntry() == null || product.itemPool().rotation() == null) return 0L;
        Rotation rotation = product.itemPool().rotation();
        return AntazonRotation.endsInMillis(rotation.everyMinecraftDays(), rotation.everyRealHours(), gameTime, System.currentTimeMillis());
    }

    public static ResourceLocation limitKey(Product product) {
        return product.poolEntry() == null ? product.id() : product.poolEntry().template();
    }

    private static long rotationPeriod(Product template, long day) {
        Rotation rotation = template.itemPool().rotation();
        return AntazonRotation.period(rotation.everyMinecraftDays(), rotation.everyRealHours(), day, System.currentTimeMillis());
    }

    private static Set<Integer> activeIndices(ResourceLocation templateId, Rotation rotation, int size, long period) {
        int picks = Math.min(rotation.picks(), size);
        Set<Integer> chosen = new HashSet<>();
        if (picks >= size) {
            for (int index = 0; index < size; index++) chosen.add(index);
            return Set.copyOf(chosen);
        }
        long position = period * picks;
        AntazonRotation.OrderCache cache = new AntazonRotation.OrderCache();
        for (int guard = 0; chosen.size() < picks && guard < picks * 2 + size; guard++, position++) {
            chosen.add(AntazonRotation.slot(templateId, rotation.shuffled(), position, size, cache));
        }
        return Set.copyOf(chosen);
    }

    private static Catalog catalog() {
        Catalog current = catalog;
        if (current != null) return current;
        synchronized (AntazonData.class) {
            if (catalog == null) catalog = buildCatalog(loaded, loadedVersion);
            return catalog;
        }
    }

    private static Catalog buildCatalog(Map<ResourceLocation, Product> source, long version) {
        Map<ResourceLocation, Product> all = new HashMap<>();
        List<Product> rotating = new ArrayList<>();
        for (Product product : source.values()) {
            if (product.variants() != null) {
                List<ResourceLocation> variants = AntazonTags.items(List.of(), List.of(product.variants().tag()));
                if (variants.isEmpty()) {
                    AntOS.LOGGER.warn("Antazon product {} has an empty variant tag", product.id());
                    continue;
                }
                Product configured = new Product(product.id(), product.name(), product.description(), product.category(), product.tags(), product.thumbnail(), product.gallery(), product.quantity(), product.payments(), product.unlockMode(), product.unlockTasks(), product.availability(), product.rewards(), product.reviews(), product.delivery(), product.deliveryLocation(), product.enabled(), product.greenTint(), product.renderMobFromSpawnEgg(), product.hiddenUntilUnlocked(), product.notifyOnUnlock(), product.itemPool(), product.poolEntry(), product.variants(), variants);
                all.put(configured.id(), configured);
                continue;
            }
            if (product.itemPool() == null) {
                all.put(product.id(), product);
                continue;
            }
            List<ResourceLocation> items = AntazonTags.items(product.itemPool().items(), product.itemPool().tags());
            if (items.isEmpty()) AntOS.LOGGER.warn("Antazon product {} has an item pool with no items", product.id());
            if (product.itemPool().rotation() != null && !items.isEmpty()) rotating.add(product);
            for (int index = 0; index < items.size(); index++) {
                Product entry = poolProduct(product, items.get(index), index, items.size());
                if (entry != null) all.put(entry.id(), entry);
            }
        }
        List<Product> sorted = all.values().stream().sorted(java.util.Comparator.comparing(product -> product.id().toString())).toList();
        Map<ResourceLocation, JsonObject> rows = new HashMap<>();
        for (Product product : sorted) rows.put(product.id(), catalogRow(product));
        rotating.sort(java.util.Comparator.comparing(product -> product.id().toString()));
        AntOS.LOGGER.info("Built Antazon catalog: {} listings from {} product files", sorted.size(), source.size());
        return new Catalog(version, sorted, Map.copyOf(all), Map.copyOf(rows), List.copyOf(rotating));
    }

    private static Product poolProduct(Product template, ResourceLocation item, int index, int size) {
        int fixed = template.rewards().stream().mapToInt(Reward::count).sum();
        int count = Math.min(template.itemPool().count(), 108 - fixed);
        if (count < 1) {
            AntOS.LOGGER.warn("Antazon product {} has no room for its pool item within the 108-item reward limit", template.id());
            return null;
        }
        List<Reward> rewards = new ArrayList<>(template.rewards());
        rewards.add(new Reward(item, count));
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(template.id().getNamespace(),
                template.id().getPath() + "/" + item.getNamespace() + "/" + item.getPath());
        PreviewAsset thumbnail = template.thumbnail().item().isBlank() && template.thumbnail().entity().isBlank()
                ? new PreviewAsset(item.toString(), "") : template.thumbnail();
        List<PreviewAsset> gallery = template.gallery().isEmpty() ? List.of(new PreviewAsset(item.toString(), "")) : template.gallery();
        return new Product(id, template.name(), template.description(), template.category(), template.tags(), thumbnail, gallery,
                template.quantity(), template.payments(), template.unlockMode(), template.unlockTasks(), template.availability(),
                List.copyOf(rewards), template.reviews(), template.delivery(), template.deliveryLocation(), template.enabled(), template.greenTint(),
                template.renderMobFromSpawnEgg(), template.hiddenUntilUnlocked(), template.notifyOnUnlock(), template.itemPool(), new PoolEntry(template.id(), item, index, size), null, List.of());
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
    protected void apply(Map<ResourceLocation, Product> prepared, ResourceManager manager, ProfilerFiller profiler) {
        long version = new java.util.Random().nextLong();
        synchronized (AntazonData.class) {
            loaded = prepared;
            loadedVersion = version == 0L ? 1L : version;
            catalog = null;
            ACTIVE.clear();
        }
        AntOS.LOGGER.info("Loaded {} Antazon product files", prepared.size());
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
        row.addProperty("restock_real_hours", availability.restockRealHours());
        row.addProperty("limit", availability.playerLimit());
        row.addProperty("limit_reset", availability.playerLimitReset());
        row.addProperty("hidden_until_unlocked", product.hiddenUntilUnlocked());
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
        if (product.poolEntry() != null) row.addProperty("pool_item", product.poolEntry().item().toString());
        if (product.variants() != null) {
            row.addProperty("variant_mode", product.variants().mode());
            JsonArray variants = new JsonArray();
            for (ResourceLocation variant : product.variantItems()) variants.add(variant.toString());
            row.add("variants", variants);
        }
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
        boolean hiddenUntilUnlocked = !object.has("hidden_until_unlocked") || !object.get("hidden_until_unlocked").isJsonPrimitive()
                || object.get("hidden_until_unlocked").getAsBoolean();
        boolean notifyOnUnlock = !object.has("notify_on_unlock") || !object.get("notify_on_unlock").isJsonPrimitive()
                || object.get("notify_on_unlock").getAsBoolean();
        String unlockMode = string(object, "unlock_mode", "all").toLowerCase(Locale.ROOT);
        if (!unlockMode.equals("all") && !unlockMode.equals("any")) {
            AntOS.LOGGER.warn("Ignoring Antazon product {} because unlock_mode must be all or any", id);
            return null;
        }
        Availability availability = parseAvailability(id, object.getAsJsonObject("availability"));
        if (availability == null) return null;
        List<Reward> rewards = parseRewards(id, object.get("rewards"));
        ItemPool itemPool = object.has("item_pool") ? parseItemPool(id, object.get("item_pool")) : null;
        if (object.has("item_pool") && itemPool == null) return null;
        VariantPool variants = object.has("variants") ? parseVariants(id, object.get("variants")) : null;
        if (object.has("variants") && variants == null) return null;
        if (itemPool != null && variants != null) return null;
        if (rewards.isEmpty() && itemPool == null && variants == null) {
            AntOS.LOGGER.warn("Ignoring Antazon product {} because it has no valid rewards", id);
            return null;
        }
        String delivery = string(object, "delivery", "falling_chest").toLowerCase(Locale.ROOT);
        if (!delivery.equals("falling_chest") && !delivery.equals("direct") && !delivery.equals("falling_chest_location")) {
            AntOS.LOGGER.warn("Ignoring Antazon product {} because delivery '{}' is unsupported", id, delivery);
            return null;
        }
        DeliveryLocation deliveryLocation = null;
        if (delivery.equals("falling_chest_location")) {
            JsonObject coordinates = object.has("delivery_location") && object.get("delivery_location").isJsonObject()
                    ? object.getAsJsonObject("delivery_location") : null;
            if (coordinates == null || !coordinates.has("x") || !coordinates.has("y") || !coordinates.has("z")) {
                AntOS.LOGGER.warn("Ignoring Antazon product {} because location delivery requires delivery_location x, y, and z", id);
                return null;
            }
            try {
                deliveryLocation = new DeliveryLocation(coordinates.get("x").getAsInt(), coordinates.get("y").getAsInt(), coordinates.get("z").getAsInt());
            } catch (RuntimeException exception) {
                AntOS.LOGGER.warn("Ignoring Antazon product {} because delivery_location coordinates are invalid", id);
                return null;
            }
        }
        boolean enabled = !object.has("enabled") || object.get("enabled").getAsBoolean();
        boolean greenTint = !object.has("green_tint") || !object.get("green_tint").isJsonPrimitive() || object.get("green_tint").getAsBoolean();
        List<Review> reviews = parseReviews(id, object.get("reviews"));
        if (object.has("deal")) AntOS.LOGGER.warn("Antazon product {} has a deal block, which is no longer supported; use a deal campaign in data/<namespace>/antazon/deal instead", id);
        boolean renderMobFromSpawnEgg = !object.has("render_mob_from_spawn_egg") || !object.get("render_mob_from_spawn_egg").isJsonPrimitive() || object.get("render_mob_from_spawn_egg").getAsBoolean();
        return new Product(id, name, description, category, tags, thumbnail, gallery, quantity, payments, unlockMode, taskIds, availability, rewards, reviews, delivery, deliveryLocation, enabled, greenTint, renderMobFromSpawnEgg, hiddenUntilUnlocked, notifyOnUnlock, itemPool, null, variants, List.of());
    }

    private static VariantPool parseVariants(ResourceLocation id, JsonElement element) {
        if (element == null || !element.isJsonObject()) return null;
        JsonObject object = element.getAsJsonObject();
        String tagValue = string(object, "tag", "").trim();
        String mode = string(object, "mode", "choose").trim().toLowerCase(Locale.ROOT);
        if (tagValue.startsWith("#")) tagValue = tagValue.substring(1);
        try {
            ResourceLocation tag = ResourceLocation.parse(tagValue);
            if (!mode.equals("all") && !mode.equals("random") && !mode.equals("choose")) throw new IllegalArgumentException();
            return new VariantPool(tag, mode);
        } catch (RuntimeException exception) {
            AntOS.LOGGER.warn("Ignoring Antazon product {} because variants requires a valid tag and mode all, random, or choose", id);
            return null;
        }
    }

    private static ItemPool parseItemPool(ResourceLocation id, JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            AntOS.LOGGER.warn("Ignoring Antazon product {} because item_pool is not an object", id);
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        List<ResourceLocation> items = new ArrayList<>();
        List<ResourceLocation> tags = new ArrayList<>();
        try {
            for (String value : strings(object.get("items"))) items.add(ResourceLocation.parse(value.trim()));
            String tag = string(object, "tag", "").trim();
            if (!tag.isBlank()) tags.add(ResourceLocation.parse(tag.startsWith("#") ? tag.substring(1) : tag));
            for (String value : strings(object.get("tags"))) tags.add(ResourceLocation.parse(value.trim().startsWith("#") ? value.trim().substring(1) : value.trim()));
        } catch (RuntimeException exception) {
            AntOS.LOGGER.warn("Ignoring Antazon product {} because item_pool has an invalid item or tag ID", id);
            return null;
        }
        if (items.isEmpty() && tags.isEmpty()) {
            AntOS.LOGGER.warn("Ignoring Antazon product {} because item_pool has no items or tags", id);
            return null;
        }
        int count = boundedInt(object, "count", 1, 1, 108);
        String mode = string(object, "mode", "all").toLowerCase(Locale.ROOT);
        Rotation rotation = null;
        if (mode.equals("rotate")) {
            JsonObject rotationJson = object.has("rotation") && object.get("rotation").isJsonObject() ? object.getAsJsonObject("rotation") : new JsonObject();
            String order = string(rotationJson, "order", "shuffled").toLowerCase(Locale.ROOT);
            if (!order.equals("shuffled") && !order.equals("sequential")) {
                AntOS.LOGGER.warn("Ignoring Antazon product {} because item_pool.rotation.order must be shuffled or sequential", id);
                return null;
            }
            if (rotationJson.has("every_minecraft_days") && rotationJson.has("every_real_hours")) {
                AntOS.LOGGER.warn("Ignoring Antazon product {} because item_pool.rotation sets both every_minecraft_days and every_real_hours", id);
                return null;
            }
            rotation = new Rotation(boundedInt(rotationJson, "picks", 1, 1, 1_000),
                    boundedLong(rotationJson, "every_minecraft_days", 1L, 1L, 1_000_000L),
                    boundedLong(rotationJson, "every_real_hours", 0L, 0L, 1_000_000L), order.equals("shuffled"));
        } else if (!mode.equals("all")) {
            AntOS.LOGGER.warn("Ignoring Antazon product {} because item_pool.mode must be all or rotate", id);
            return null;
        }
        return new ItemPool(List.copyOf(items), List.copyOf(tags), count, rotation);
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
        long restockRealHours = boundedLong(object, "restock_after_real_hours", 0L, 0L, 1_000_000L);
        if (restockDays > 0L && restockRealHours > 0L) {
            AntOS.LOGGER.warn("Ignoring Antazon product {} because it sets both restock_after_minecraft_days and restock_after_real_hours", id);
            return null;
        }
        long cooldownDays = boundedLong(object, "cooldown_minecraft_days", 0L, 0L, 1_000_000L);
        int playerLimit = boundedInt(object, "player_limit", 0, 0, 1_000_000);
        String reset = string(object, "player_limit_reset", "none").toLowerCase(Locale.ROOT);
        if (!reset.equals("none") && !reset.equals("minecraft_day") && !reset.equals("minecraft_week")) {
            AntOS.LOGGER.warn("Ignoring Antazon product {} because player_limit_reset is invalid", id);
            return null;
        }
        return new Availability(stock, restockDays, restockRealHours, cooldownDays, playerLimit, reset);
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
                          Availability availability, List<Reward> rewards, List<Review> reviews, String delivery, DeliveryLocation deliveryLocation, boolean enabled, boolean greenTint, boolean renderMobFromSpawnEgg, boolean hiddenUntilUnlocked, boolean notifyOnUnlock,
                          ItemPool itemPool, PoolEntry poolEntry, VariantPool variants, List<ResourceLocation> variantItems) {
    }

    public record DeliveryLocation(int x, int y, int z) { }

    public record ItemPool(List<ResourceLocation> items, List<ResourceLocation> tags, int count, Rotation rotation) {
    }

    public record Rotation(int picks, long everyMinecraftDays, long everyRealHours, boolean shuffled) {
    }

    public record PoolEntry(ResourceLocation template, ResourceLocation item, int index, int poolSize) {
    }

    public record VariantPool(ResourceLocation tag, String mode) { }

    private record ActiveSet(long period, Set<Integer> indices) {
    }

    private record Catalog(long version, List<Product> sorted, Map<ResourceLocation, Product> products,
                           Map<ResourceLocation, JsonObject> rows, List<Product> rotatingTemplates) {
    }

    public record PreviewAsset(String item, String entity) {
    }

    public record Payment(String type, String resource, int amount) {
    }

    public record Reward(ResourceLocation item, int count) {
    }

    public record Review(String id, String author, String title, String body, int rating, String badge) {
    }

    public record Availability(int serverStock, long restockMinecraftDays, long restockRealHours, long cooldownMinecraftDays,
                               int playerLimit, String playerLimitReset) {
        public boolean restocks() {
            return restockMinecraftDays > 0L || restockRealHours > 0L;
        }
    }
}
