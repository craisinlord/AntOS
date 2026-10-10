package com.craisinlord.antos.content.client;

import com.craisinlord.antos.content.network.ComputerAccessPayload;
import com.craisinlord.antos.content.network.ComputerAccessResultPayload;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AntazonClientState {
    private static final Map<String, List<ProductRow>> PRODUCTS = new ConcurrentHashMap<>();
    private static final Map<String, String> STATUS = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> RECEIVED = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> WISHLISTS = new ConcurrentHashMap<>();
    private static final Map<String, List<OrderRow>> ORDERS = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> WISHLIST_RECEIVED = new ConcurrentHashMap<>();
    private static final Map<String, Long> WALLETS = new ConcurrentHashMap<>();
    private static final Map<String, List<SellRow>> SELL_ROWS = new ConcurrentHashMap<>();
    private static final Map<String, String> SELL_FEEDBACK = new ConcurrentHashMap<>();
    private static final Map<String, Long> SELL_AMOUNT = new ConcurrentHashMap<>();
    private static final Map<String, List<PriceRow>> PRICES = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> ONBOARDING = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> ONBOARDING_RECEIVED = new ConcurrentHashMap<>();
    private static final Map<String, List<CartLine>> CARTS = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> CART_RECEIVED = new ConcurrentHashMap<>();
    private static final Map<String, List<CheckoutLine>> CHECKOUTS = new ConcurrentHashMap<>();
    private static final Map<String, Notice> NOTICES = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, ProductRow>> PRODUCT_INDEX = new ConcurrentHashMap<>();
    private static volatile Map<String, JsonObject> catalog = Map.of();
    private static volatile String catalogVersion = "";

    private AntazonClientState() { }

    public static void clearAll() {
        PRODUCTS.clear(); STATUS.clear(); RECEIVED.clear(); WISHLISTS.clear(); ORDERS.clear(); WISHLIST_RECEIVED.clear();
        WALLETS.clear(); SELL_ROWS.clear(); SELL_FEEDBACK.clear(); SELL_AMOUNT.clear(); PRICES.clear(); ONBOARDING.clear(); ONBOARDING_RECEIVED.clear();
        CARTS.clear(); CART_RECEIVED.clear(); CHECKOUTS.clear(); NOTICES.clear(); PRODUCT_INDEX.clear();
        catalog = Map.of();
        catalogVersion = "";
    }

    public static String catalogVersion() {
        return catalogVersion;
    }

    public static void update(ComputerAccessResultPayload result) {
        String key = ComputerWorkspaceClientKey.of();
        String data = result.data();
        String prefix = ComputerAccessPayload.ANTAZON_STATE + "\0\0";
        try {
            if (data.startsWith(prefix)) {
                JsonObject response = JsonParser.parseString(data.substring(prefix.length())).getAsJsonObject();
                String version = response.get("catalog_version").getAsString();
                if (response.has("catalog")) {
                    Map<String, JsonObject> rows = new java.util.HashMap<>();
                    for (JsonElement element : response.getAsJsonArray("catalog")) {
                        JsonObject row = element.getAsJsonObject();
                        rows.put(row.get("id").getAsString(), row);
                    }
                    catalog = Map.copyOf(rows);
                    catalogVersion = version;
                } else if (!version.equals(catalogVersion)) {
                    catalogVersion = "";
                    com.craisinlord.antos.content.network.ComputerNetworking.requestAntazon();
                    return;
                }
                List<ProductRow> rows = new ArrayList<>();
                Map<String, ProductRow> index = new java.util.HashMap<>();
                for (JsonElement element : response.getAsJsonArray("state")) {
                    JsonObject state = element.getAsJsonObject();
                    JsonObject base = catalog.get(state.get("id").getAsString());
                    if (base == null) continue;
                    ProductRow row = product(merge(base, state));
                    rows.add(row);
                    index.put(row.id(), row);
                }
                PRODUCTS.put(key, List.copyOf(rows));
                PRODUCT_INDEX.put(key, Map.copyOf(index));
                RECEIVED.put(key, true);
            } else if (data.startsWith(ComputerAccessPayload.ANTAZON_WISHLIST + "\0")) {
                String[] envelope = data.split("\u0000", 3);
                List<String> wishlist = new ArrayList<>();
                if (envelope.length > 2 && !envelope[2].isBlank()) {
                    for (JsonElement item : JsonParser.parseString(envelope[2]).getAsJsonArray()) wishlist.add(item.getAsString());
                }
                WISHLISTS.put(key, List.copyOf(wishlist));
                WISHLIST_RECEIVED.put(key, true);
            } else if (data.startsWith(ComputerAccessPayload.ANTAZON_ORDERS + "\0")) {
                String[] envelope = data.split("\u0000", 3);
                List<OrderRow> orders = new ArrayList<>();
                if (envelope.length > 2 && !envelope[2].isBlank()) {
                    for (JsonElement element : JsonParser.parseString(envelope[2]).getAsJsonArray()) {
                        JsonObject row = element.getAsJsonObject();
                        orders.add(new OrderRow(row.get("product").getAsString(), row.get("option").getAsString(),
                                row.get("units").getAsInt(), row.get("game_time").getAsLong(), row.get("status").getAsString()));
                    }
                }
                ORDERS.put(key, List.copyOf(orders.reversed()));
            } else if (data.startsWith(ComputerAccessPayload.ANTAZON_WALLET + "\0")) {
                String[] envelope = data.split("\u0000", 3);
                if (envelope.length > 2 && !envelope[2].isBlank()) WALLETS.put(key, Long.parseLong(envelope[2]));
            } else if (data.startsWith(ComputerAccessPayload.ANTAZON_SELL_STATE + "\0")) {
                String[] envelope = data.split("\u0000", 3);
                List<SellRow> rows = new ArrayList<>();
                if (envelope.length > 2 && !envelope[2].isBlank()) {
                    for (JsonElement element : JsonParser.parseString(envelope[2]).getAsJsonArray()) {
                        JsonObject row = element.getAsJsonObject();
                        rows.add(new SellRow(row.get("item").getAsString(), row.get("count").getAsInt(), integer(row, "accepted_count", row.get("count").getAsInt()),
                                integer(row, "returned_count", 0), row.get("value").getAsLong(),
                                row.get("sellable").getAsBoolean(), bool(row, "green_tint", true), bool(row, "render_mob_from_spawn_egg", true),
                                integer(row, "limit", 0), string(row, "limit_reset", "none"), integer(row, "remaining", Integer.MAX_VALUE), bool(row, "over_limit", false),
                                bool(row, "locked", false), string(row, "unlock_mode", "all"), strings(row.getAsJsonArray("unlock_tasks"))));
                    }
                }
                SELL_ROWS.put(key, List.copyOf(rows));
                STATUS.put(key, envelope.length > 1 ? envelope[1].toUpperCase() : "");
            } else if (data.startsWith(ComputerAccessPayload.ANTAZON_PRICES + "\0")) {
                String[] envelope = data.split("\u0000", 3);
                List<PriceRow> prices = new ArrayList<>();
                if (envelope.length > 2 && !envelope[2].isBlank()) for (JsonElement element : JsonParser.parseString(envelope[2]).getAsJsonArray()) {
                    JsonObject row = element.getAsJsonObject();
                    prices.add(new PriceRow(row.get("item").getAsString(), row.get("value").getAsInt(), bool(row, "green_tint", true), bool(row, "render_mob_from_spawn_egg", true),
                            integer(row, "limit", 0), integer(row, "used", 0), integer(row, "remaining", Integer.MAX_VALUE), string(row, "limit_reset", "none"),
                            bool(row, "locked", false), string(row, "unlock_mode", "all"), strings(row.getAsJsonArray("unlock_tasks"))));
                }
                PRICES.put(key, List.copyOf(prices));
            } else if (data.startsWith(ComputerAccessPayload.ANTAZON_ONBOARDING + "\0")
                    || data.startsWith(ComputerAccessPayload.ANTAZON_ONBOARDING_COMPLETE + "\0")
                    || data.startsWith(ComputerAccessPayload.ANTAZON_ONBOARDING_RESET + "\0")) {
                String[] envelope = data.split("\u0000", 3);
                ONBOARDING.put(key, envelope.length > 2 && Boolean.parseBoolean(envelope[2]));
                ONBOARDING_RECEIVED.put(key, true);
            } else if (data.startsWith(ComputerAccessPayload.ANTAZON_PREPARE + "\0")) {
                String[] envelope = data.split("\u0000", 3);
                boolean success = result.result() == ComputerAccessResultPayload.SUCCESS;
                NOTICES.put(key, new Notice("prepare", envelope.length > 1 ? envelope[1] : "", "", 0, "", success, System.currentTimeMillis()));
                if (success) com.craisinlord.antos.content.network.ComputerNetworking.requestAntazonSellState();
            } else if (data.startsWith(ComputerAccessPayload.ANTAZON_PURCHASE + "\0")) {
                String[] envelope = data.split("\u0000", 5);
                boolean success = result.result() == ComputerAccessResultPayload.SUCCESS;
                String product = envelope.length > 2 ? envelope[2] : "";
                int units = envelope.length > 3 ? parseInt(envelope[3]) : 0;
                String receipt = envelope.length > 4 ? envelope[4] : "";
                NOTICES.put(key, new Notice("purchase", envelope.length > 1 ? envelope[1] : "", product, units, receipt, success, System.currentTimeMillis()));
                refreshAfterPurchase();
            } else if (data.startsWith(ComputerAccessPayload.ANTAZON_CART + "\0")) {
                String[] envelope = data.split("\u0000", 3);
                if (envelope.length > 2 && !envelope[2].isBlank()) {
                    CARTS.put(key, cart(JsonParser.parseString(envelope[2]).getAsJsonArray()));
                    CART_RECEIVED.put(key, true);
                }
            } else if (data.startsWith(ComputerAccessPayload.ANTAZON_CHECKOUT + "\0")) {
                String[] envelope = data.split("\u0000", 3);
                if (envelope.length > 2 && !envelope[2].isBlank()) {
                    JsonObject response = JsonParser.parseString(envelope[2]).getAsJsonObject();
                    List<CheckoutLine> lines = new ArrayList<>();
                    for (JsonElement element : response.getAsJsonArray("results")) {
                        JsonObject row = element.getAsJsonObject();
                        lines.add(new CheckoutLine(row.get("product").getAsString(), row.get("option").getAsInt(), row.get("units").getAsInt(), string(row, "variant", ""),
                                row.get("status").getAsString(), row.get("receipt").getAsString()));
                    }
                    CHECKOUTS.put(key, List.copyOf(lines));
                    CARTS.put(key, cart(response.getAsJsonArray("cart")));
                    CART_RECEIVED.put(key, true);
                } else {
                    NOTICES.put(key, new Notice("purchase", envelope.length > 1 ? envelope[1] : "invalid_request", "", 0, "", false, System.currentTimeMillis()));
                }
                refreshAfterPurchase();
            } else if (data.startsWith(ComputerAccessPayload.ANTAZON_REVIEW + "\0")) {
                String[] envelope = data.split("\u0000", 3);
                boolean success = result.result() == ComputerAccessResultPayload.SUCCESS;
                NOTICES.put(key, new Notice("review", envelope.length > 1 ? envelope[1] : "", "", 0, "", success, System.currentTimeMillis()));
                if (success) com.craisinlord.antos.content.network.ComputerNetworking.requestAntazon();
            } else if (data.startsWith(ComputerAccessPayload.ANTAZON_SELL + "\0")) {
                String[] envelope = data.split("\u0000", 3);
                String status = envelope.length > 1 && !envelope[1].isBlank() ? envelope[1].toUpperCase() : "REQUEST COMPLETE";
                STATUS.put(key, status);
                SELL_FEEDBACK.put(key, status);
                if (envelope.length > 2 && !envelope[2].isBlank()) SELL_AMOUNT.put(key, Long.parseLong(envelope[2]));
                if (result.result() == ComputerAccessResultPayload.SUCCESS) {
                    com.craisinlord.antos.content.network.ComputerNetworking.requestAntazonSellState();
                    com.craisinlord.antos.content.network.ComputerNetworking.requestAntazonPrices();
                }
            }
        } catch (RuntimeException exception) {
            NOTICES.put(key, new Notice("error", "invalid_response", "", 0, "", false, System.currentTimeMillis()));
        }
    }

    private static void refreshAfterPurchase() {
        com.craisinlord.antos.content.network.ComputerNetworking.requestAntazon();
        com.craisinlord.antos.content.network.ComputerNetworking.requestAntazonWallet();
        com.craisinlord.antos.content.network.ComputerNetworking.requestAntazonOrders();
    }

    private static JsonObject merge(JsonObject base, JsonObject state) {
        JsonObject row = base.deepCopy();
        for (Map.Entry<String, JsonElement> entry : state.entrySet()) {
            if (!entry.getKey().equals("payments") && !entry.getKey().equals("player_reviews")) row.add(entry.getKey(), entry.getValue());
        }
        JsonArray payments = row.getAsJsonArray("payments");
        JsonArray paymentStates = state.has("payments") ? state.getAsJsonArray("payments") : new JsonArray();
        for (int index = 0; index < payments.size() && index < paymentStates.size(); index++) {
            JsonObject payment = payments.get(index).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : paymentStates.get(index).getAsJsonObject().entrySet()) payment.add(entry.getKey(), entry.getValue());
        }
        if (state.has("player_reviews")) row.getAsJsonArray("reviews").addAll(state.getAsJsonArray("player_reviews"));
        return row;
    }

    private static ProductRow product(JsonObject row) {
        JsonObject thumbnailJson = row.getAsJsonObject("thumbnail");
        PreviewAsset thumbnail = new PreviewAsset(thumbnailJson.get("item").getAsString(), thumbnailJson.get("entity").getAsString());
        List<PreviewAsset> gallery = new ArrayList<>();
        for (JsonElement galleryElement : row.getAsJsonArray("gallery")) {
            JsonObject asset = galleryElement.getAsJsonObject();
            gallery.add(new PreviewAsset(asset.get("item").getAsString(), asset.get("entity").getAsString()));
        }
        List<PaymentRow> payments = new ArrayList<>();
        for (JsonElement paymentElement : row.getAsJsonArray("payments")) {
            JsonObject payment = paymentElement.getAsJsonObject();
            int amount = payment.get("amount").getAsInt();
            payments.add(new PaymentRow(string(payment, "type", "antcoins"), payment.get("resource").getAsString(), amount,
                    payment.has("price") ? payment.get("price").getAsInt() : amount, payment.has("owned") ? payment.get("owned").getAsLong() : 0L));
        }
        List<ReviewRow> reviews = new ArrayList<>();
        for (JsonElement reviewElement : row.getAsJsonArray("reviews")) {
            JsonObject review = reviewElement.getAsJsonObject();
            reviews.add(new ReviewRow(review.get("author").getAsString(), review.get("title").getAsString(),
                    review.get("body").getAsString(), review.get("rating").getAsInt(), review.get("badge").getAsString()));
        }
        List<RewardRow> rewards = new ArrayList<>();
        if (row.has("rewards")) for (JsonElement rewardElement : row.getAsJsonArray("rewards")) {
            JsonObject reward = rewardElement.getAsJsonObject();
            rewards.add(new RewardRow(reward.get("item").getAsString(), reward.get("count").getAsInt()));
        }
        String poolItem = poolItemName(string(row, "pool_item", ""));
        List<String> variants = row.has("variants") ? strings(row.getAsJsonArray("variants")) : List.of();
        return new ProductRow(row.get("id").getAsString(), withPoolItem(row.get("name").getAsString(), poolItem),
                withPoolItem(row.get("description").getAsString(), poolItem),
                row.get("category").getAsString(), bool(row, "green_tint", true), bool(row, "render_mob_from_spawn_egg", true),
                row.has("remaining") ? row.get("remaining").getAsInt() : -1, deadline(row, "restock_in_ms"), bool(row, "restock_real", false),
                row.has("limit") ? row.get("limit").getAsInt() : 0, row.has("limit_used") ? row.get("limit_used").getAsInt() : 0,
                string(row, "limit_reset", "none"), bool(row, "deal_active", false), string(row, "deal_label", ""),
                integer(row, "deal_discount", 0), string(row, "deal_key", ""), deadline(row, "deal_ends_in_ms"), bool(row, "deal_real", false),
                integer(row, "deal_limit", 0), integer(row, "deal_remaining", -1), bool(row, "locked", false), strings(row.getAsJsonArray("unlock_tasks")),
                string(row, "unlock_mode", "all"), bool(row, "hidden_until_unlocked", false), strings(row.getAsJsonArray("tags")), thumbnail, List.copyOf(gallery),
                row.get("quantity").getAsInt(), row.has("cooldown_ends") ? row.get("cooldown_ends").getAsLong() : 0L,
                List.copyOf(payments), List.copyOf(rewards), List.copyOf(reviews), bool(row, "purchased", false), bool(row, "reviewed", false),
                deadline(row, "rotates_in_ms"), bool(row, "rotates_real", false), string(row, "variant_mode", ""), variants);
    }

    private static long deadline(JsonObject row, String key) {
        long remaining = row.has(key) ? row.get(key).getAsLong() : 0L;
        return remaining > 0L ? System.currentTimeMillis() + remaining : 0L;
    }

    private static List<CartLine> cart(JsonArray array) {
        List<CartLine> lines = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject row = element.getAsJsonObject();
            lines.add(new CartLine(row.get("product").getAsString(), row.get("option").getAsInt(), row.get("units").getAsInt(), string(row, "variant", "")));
        }
        return List.copyOf(lines);
    }

    public static String encodeCart(List<CartLine> lines) {
        JsonArray array = new JsonArray();
        for (CartLine line : lines) {
            JsonObject row = new JsonObject();
            row.addProperty("product", line.product());
            row.addProperty("option", line.option());
            row.addProperty("units", line.units());
            row.addProperty("variant", line.variant());
            array.add(row);
        }
        return array.toString();
    }

    private static List<String> strings(JsonArray array) {
        if (array == null) return List.of();
        List<String> values = new ArrayList<>();
        for (JsonElement element : array) values.add(element.getAsString());
        return List.copyOf(values);
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsBoolean() : fallback;
    }

    private static int integer(JsonObject object, String key, int fallback) {
        try { return object.has(key) ? object.get(key).getAsInt() : fallback; }
        catch (RuntimeException exception) { return fallback; }
    }

    private static String withPoolItem(String text, String poolItem) {
        if (poolItem.isBlank()) return text;
        return net.minecraft.network.chat.Component.translatable(text).getString().replace("{item}", poolItem);
    }

    private static String poolItemName(String itemId) {
        if (itemId.isBlank()) return "";
        try {
            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(itemId));
            return new net.minecraft.world.item.ItemStack(item).getHoverName().getString();
        } catch (RuntimeException exception) {
            return itemId;
        }
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }

    private static int parseInt(String value) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException exception) { return 0; }
    }

    public static List<ProductRow> products() { return PRODUCTS.getOrDefault(ComputerWorkspaceClientKey.of(), List.of()); }
    public static ProductRow product(String id) { return id == null ? null : PRODUCT_INDEX.getOrDefault(ComputerWorkspaceClientKey.of(), Map.of()).get(id); }
    public static boolean hasSnapshot() { return RECEIVED.getOrDefault(ComputerWorkspaceClientKey.of(), false); }
    public static List<String> wishlist() { return WISHLISTS.getOrDefault(ComputerWorkspaceClientKey.of(), List.of()); }
    public static boolean hasWishlistSnapshot() { return WISHLIST_RECEIVED.getOrDefault(ComputerWorkspaceClientKey.of(), false); }
    public static List<OrderRow> orders() { return ORDERS.getOrDefault(ComputerWorkspaceClientKey.of(), List.of()); }
    public static String status() { return STATUS.getOrDefault(ComputerWorkspaceClientKey.of(), ""); }
    public static long wallet() { return WALLETS.getOrDefault(ComputerWorkspaceClientKey.of(), 0L); }
    public static List<SellRow> sellRows() { return SELL_ROWS.getOrDefault(ComputerWorkspaceClientKey.of(), List.of()); }
    public static String sellFeedback() { return SELL_FEEDBACK.getOrDefault(ComputerWorkspaceClientKey.of(), ""); }
    public static long sellAmount() { return SELL_AMOUNT.getOrDefault(ComputerWorkspaceClientKey.of(), 0L); }
    public static List<PriceRow> prices() { return PRICES.getOrDefault(ComputerWorkspaceClientKey.of(), List.of()); }
    public static boolean onboardingComplete() { return ONBOARDING.getOrDefault(ComputerWorkspaceClientKey.of(), false); }
    public static boolean onboardingReceived() { return ONBOARDING_RECEIVED.getOrDefault(ComputerWorkspaceClientKey.of(), false); }
    public static List<CartLine> cart() { return CARTS.getOrDefault(ComputerWorkspaceClientKey.of(), List.of()); }
    public static boolean hasCartSnapshot() { return CART_RECEIVED.getOrDefault(ComputerWorkspaceClientKey.of(), false); }
    public static List<CheckoutLine> checkout() { return CHECKOUTS.getOrDefault(ComputerWorkspaceClientKey.of(), List.of()); }
    public static Notice notice() { return NOTICES.get(ComputerWorkspaceClientKey.of()); }

    public static void setCart(List<CartLine> lines) {
        String key = ComputerWorkspaceClientKey.of();
        CARTS.put(key, List.copyOf(lines));
        com.craisinlord.antos.content.network.ComputerNetworking.saveAntazonCart(encodeCart(lines));
    }

    public static void pushNotice(String kind, String code, String product, int units, boolean success) {
        NOTICES.put(ComputerWorkspaceClientKey.of(), new Notice(kind, code, product, units, "", success, System.currentTimeMillis()));
    }

    public static void clearCheckout() { CHECKOUTS.remove(ComputerWorkspaceClientKey.of()); }
    public static void clearNotice() { NOTICES.remove(ComputerWorkspaceClientKey.of()); }
    public static void clearSellFeedback() { String key = ComputerWorkspaceClientKey.of(); SELL_FEEDBACK.remove(key); SELL_AMOUNT.remove(key); }

    public static void clear() {
        String key = ComputerWorkspaceClientKey.of();
        PRODUCTS.remove(key); PRODUCT_INDEX.remove(key); STATUS.remove(key); RECEIVED.remove(key); WISHLISTS.remove(key); ORDERS.remove(key); WISHLIST_RECEIVED.remove(key);
        WALLETS.remove(key); SELL_ROWS.remove(key); SELL_FEEDBACK.remove(key); SELL_AMOUNT.remove(key); PRICES.remove(key); ONBOARDING.remove(key);
        ONBOARDING_RECEIVED.remove(key); CARTS.remove(key); CART_RECEIVED.remove(key); CHECKOUTS.remove(key); NOTICES.remove(key);
    }

    public record ProductRow(String id, String name, String description, String category, boolean greenTint, boolean renderMobFromSpawnEgg, int remaining, long restockAt, boolean restockReal,
                             int limit, int limitUsed, String limitReset, boolean dealActive, String dealLabel, int dealDiscount,
                             String dealKey, long dealEndsAt, boolean dealReal, int dealLimit, int dealRemaining,
                             boolean locked, List<String> unlockTasks, String unlockMode, boolean hiddenUntilUnlocked, List<String> tags, PreviewAsset thumbnail,
                             List<PreviewAsset> gallery, int quantity, long cooldownEnds, List<PaymentRow> payments, List<RewardRow> rewards,
                             List<ReviewRow> reviews, boolean purchased, boolean reviewed, long rotatesAt, boolean rotatesReal, String variantMode, List<String> variants) {
        public boolean onSale() {
            return dealActive && dealRemaining != 0;
        }

        public double rating() {
            return reviews.isEmpty() ? 0.0D : reviews.stream().mapToInt(ReviewRow::rating).average().orElse(0.0D);
        }
    }

    public record PreviewAsset(String item, String entity) { }
    public record PaymentRow(String type, String resource, int amount, int price, long owned) { }
    public record RewardRow(String item, int count) { }
    public record ReviewRow(String author, String title, String body, int rating, String badge) { }
    public record OrderRow(String product, String option, int units, long gameTime, String status) { }
    public record SellRow(String item, int count, int acceptedCount, int returnedCount, long value, boolean sellable, boolean greenTint, boolean renderMobFromSpawnEgg,
                          int limit, String limitReset, int remaining, boolean overLimit, boolean locked, String unlockMode, List<String> unlockTasks) { }
    public record PriceRow(String item, int value, boolean greenTint, boolean renderMobFromSpawnEgg,
                           int limit, int used, int remaining, String limitReset, boolean locked, String unlockMode, List<String> unlockTasks) { }
    public record CartLine(String product, int option, int units, String variant) {
        public CartLine(String product, int option, int units) { this(product, option, units, ""); }
    }
    public record CheckoutLine(String product, int option, int units, String variant, String status, String receipt) { }
    public record Notice(String kind, String code, String product, int units, String receipt, boolean success, long createdAt) { }
}
