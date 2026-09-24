package com.craisinlord.antos.content.client;

import com.craisinlord.antos.content.network.ComputerAccessPayload;
import com.craisinlord.antos.content.network.ComputerAccessResultPayload;
import com.google.gson.JsonParser;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public final class AntazonClientState {
    private static final Map<String, List<ProductRow>> PRODUCTS = new ConcurrentHashMap<>();
    private static final Map<String, String> STATUS = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> RECEIVED = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> WISHLISTS = new ConcurrentHashMap<>();
    private static final Map<String, List<OrderRow>> ORDERS = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> WISHLIST_RECEIVED = new ConcurrentHashMap<>();
    private static final Map<String, Long> WALLETS = new ConcurrentHashMap<>();
    private static final Map<String, List<SellRow>> SELL_ROWS = new ConcurrentHashMap<>();
    private static final Map<String, String> SELL_FEEDBACK = new ConcurrentHashMap<>();
    private static final Map<String, Long> SELL_AMOUNT = new ConcurrentHashMap<>();
    private static final Map<String, List<PriceRow>> PRICES = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> ONBOARDING = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> ONBOARDING_RECEIVED = new ConcurrentHashMap<>();

    private AntazonClientState() { }

    public static void clearAll() {
        PRODUCTS.clear(); STATUS.clear(); RECEIVED.clear(); WISHLISTS.clear(); ORDERS.clear(); WISHLIST_RECEIVED.clear();
        WALLETS.clear(); SELL_ROWS.clear(); SELL_FEEDBACK.clear(); SELL_AMOUNT.clear(); PRICES.clear(); ONBOARDING.clear(); ONBOARDING_RECEIVED.clear();
    }

    public static void update(ComputerAccessResultPayload result) {
        String key = ComputerWorkspaceClientKey.of();
        String prefix = ComputerAccessPayload.ANTAZON_STATE + "\0\0";
        String purchasePrefix = ComputerAccessPayload.ANTAZON_PURCHASE + "\0";
        String wishlistPrefix = ComputerAccessPayload.ANTAZON_WISHLIST + "\0";
        String ordersPrefix = ComputerAccessPayload.ANTAZON_ORDERS + "\0";
        String reviewPrefix = ComputerAccessPayload.ANTAZON_REVIEW + "\0";
        String walletPrefix = ComputerAccessPayload.ANTAZON_WALLET + "\0";
        String sellPrefix = ComputerAccessPayload.ANTAZON_SELL + "\0";
        String sellStatePrefix = ComputerAccessPayload.ANTAZON_SELL_STATE + "\0";
        String pricesPrefix = ComputerAccessPayload.ANTAZON_PRICES + "\0";
        String onboardingPrefix = ComputerAccessPayload.ANTAZON_ONBOARDING + "\0";
        String onboardingCompletePrefix = ComputerAccessPayload.ANTAZON_ONBOARDING_COMPLETE + "\0";
        String onboardingResetPrefix = ComputerAccessPayload.ANTAZON_ONBOARDING_RESET + "\0";
        try {
            if (result.data().startsWith(prefix)) {
                var array = JsonParser.parseString(result.data().substring(prefix.length())).getAsJsonArray();
                java.util.ArrayList<ProductRow> rows = new java.util.ArrayList<>();
                for (var element : array) {
                    var row = element.getAsJsonObject();
                    var thumbnailJson = row.getAsJsonObject("thumbnail");
                    PreviewAsset thumbnail = new PreviewAsset(thumbnailJson.get("item").getAsString(), thumbnailJson.get("entity").getAsString());
                    java.util.ArrayList<PreviewAsset> gallery = new java.util.ArrayList<>();
                    for (var galleryElement : row.getAsJsonArray("gallery")) {
                        var asset = galleryElement.getAsJsonObject();
                        gallery.add(new PreviewAsset(asset.get("item").getAsString(), asset.get("entity").getAsString()));
                    }
                    java.util.ArrayList<String> payments = new java.util.ArrayList<>();
                    for (var paymentElement : row.getAsJsonArray("payments")) {
                        var payment = paymentElement.getAsJsonObject();
                        payments.add(payment.get("amount").getAsInt() + " " + payment.get("resource").getAsString());
                    }
                    java.util.ArrayList<ReviewRow> reviewRows = new java.util.ArrayList<>();
                    for (var reviewElement : row.getAsJsonArray("reviews")) {
                        var review = reviewElement.getAsJsonObject();
                        reviewRows.add(new ReviewRow(review.get("author").getAsString(), review.get("title").getAsString(),
                                review.get("body").getAsString(), review.get("rating").getAsInt(), review.get("badge").getAsString()));
                    }
                    rows.add(new ProductRow(row.get("id").getAsString(), row.get("name").getAsString(),
                            row.get("description").getAsString(), row.get("category").getAsString(),
                            row.get("enabled").getAsBoolean(), row.get("stock").getAsInt(),
                            row.get("restock_days").getAsLong(), row.get("deal_active").getAsBoolean(),
                            row.get("deal_label").getAsString(), row.get("deal_discount").getAsInt(),
                            thumbnail, List.copyOf(gallery), row.get("quantity").getAsInt(), row.has("cooldown_ends") ? row.get("cooldown_ends").getAsLong() : 0L,
                            List.copyOf(payments), List.copyOf(reviewRows)));
                }
                PRODUCTS.put(key, List.copyOf(rows));
                RECEIVED.put(key, true);
            } else if (result.data().startsWith(wishlistPrefix)) {
                String[] envelope = result.data().split("\u0000", 3);
                Set<String> wishlist = new HashSet<>();
                if (envelope.length > 2 && !envelope[2].isBlank()) {
                    for (var item : JsonParser.parseString(envelope[2]).getAsJsonArray()) wishlist.add(item.getAsString());
                }
                WISHLISTS.put(key, Set.copyOf(wishlist));
                WISHLIST_RECEIVED.put(key, true);
                if (envelope.length > 1 && !envelope[1].isBlank()) STATUS.put(key, envelope[1].toUpperCase());
            } else if (result.data().startsWith(ordersPrefix)) {
                String[] envelope = result.data().split("\u0000", 3);
                List<OrderRow> orders = new ArrayList<>();
                if (envelope.length > 2 && !envelope[2].isBlank()) {
                    for (var element : JsonParser.parseString(envelope[2]).getAsJsonArray()) {
                        var row = element.getAsJsonObject();
                        orders.add(new OrderRow(row.get("product").getAsString(), row.get("option").getAsString(),
                                row.get("units").getAsInt(), row.get("game_time").getAsLong(), row.get("status").getAsString()));
                    }
                }
                ORDERS.put(key, List.copyOf(orders));
            } else if (result.data().startsWith(walletPrefix)) {
                String[] envelope = result.data().split("\u0000", 3);
                WALLETS.put(key, envelope.length > 2 ? Long.parseLong(envelope[2]) : 0L);
                if (envelope.length > 1 && !envelope[1].isBlank()) STATUS.put(key, envelope[1].toUpperCase());
            } else if (result.data().startsWith(sellStatePrefix)) {
                String[] envelope = result.data().split("\u0000", 3);
                List<SellRow> rows = new ArrayList<>();
                if (envelope.length > 2 && !envelope[2].isBlank()) {
                    for (var element : JsonParser.parseString(envelope[2]).getAsJsonArray()) {
                        var row = element.getAsJsonObject();
                        rows.add(new SellRow(row.get("item").getAsString(), row.get("count").getAsInt(), row.get("value").getAsLong(), row.get("sellable").getAsBoolean()));
                    }
                }
                SELL_ROWS.put(key, List.copyOf(rows));
                if (envelope.length > 1 && !envelope[1].isBlank()) STATUS.put(key, envelope[1].toUpperCase());
            } else if (result.data().startsWith(pricesPrefix)) {
                String[] envelope = result.data().split("\u0000", 3);
                List<PriceRow> prices = new ArrayList<>();
                if (envelope.length > 2 && !envelope[2].isBlank()) for (var element : JsonParser.parseString(envelope[2]).getAsJsonArray()) {
                    var row = element.getAsJsonObject();
                    prices.add(new PriceRow(row.get("item").getAsString(), row.get("value").getAsInt()));
                }
                PRICES.put(key, List.copyOf(prices));
            } else if (result.data().startsWith(onboardingPrefix)) {
                String[] envelope = result.data().split("\u0000", 3);
                ONBOARDING.put(key, envelope.length > 2 && Boolean.parseBoolean(envelope[2]));
                ONBOARDING_RECEIVED.put(key, true);
            } else if (result.data().startsWith(onboardingCompletePrefix) || result.data().startsWith(onboardingResetPrefix)) {
                String[] envelope = result.data().split("\u0000", 3);
                ONBOARDING.put(key, envelope.length > 2 && Boolean.parseBoolean(envelope[2]));
                ONBOARDING_RECEIVED.put(key, true);
            } else if (result.data().startsWith(ComputerAccessPayload.ANTAZON_PREPARE + "\0")) {
                String[] envelope = result.data().split("\u0000", 4);
                STATUS.put(key, envelope.length > 1 ? envelope[1].toUpperCase() : "REQUEST COMPLETE");
                if (result.result() == ComputerAccessResultPayload.SUCCESS) com.craisinlord.antos.content.network.ComputerNetworking.requestAntazonSellState();
            } else if (result.data().startsWith(purchasePrefix) || result.data().startsWith(reviewPrefix) || result.data().startsWith(sellPrefix)) {
                String[] envelope = result.data().split("\u0000", 3);
                String status = envelope.length > 1 && !envelope[1].isBlank() ? envelope[1].toUpperCase() : "REQUEST COMPLETE";
                STATUS.put(key, status);
                if (result.data().startsWith(sellPrefix)) {
                    SELL_FEEDBACK.put(key, status);
                    if (envelope.length > 2 && !envelope[2].isBlank()) SELL_AMOUNT.put(key, Long.parseLong(envelope[2]));
                }
            }
        } catch (RuntimeException exception) {
            STATUS.put(key, "ANTAZON RESPONSE INVALID");
        }
        if (result.result() == ComputerAccessResultPayload.INVALID && result.data().startsWith(purchasePrefix)) STATUS.put(key, "PURCHASE FAILED");
    }

    public static List<ProductRow> products() { return PRODUCTS.getOrDefault(ComputerWorkspaceClientKey.of(), List.of()); }
    public static boolean hasSnapshot() { return RECEIVED.getOrDefault(ComputerWorkspaceClientKey.of(), false); }
    public static Set<String> wishlist() { return WISHLISTS.getOrDefault(ComputerWorkspaceClientKey.of(), Set.of()); }
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
    public static void clearSellFeedback() { String key = ComputerWorkspaceClientKey.of(); SELL_FEEDBACK.remove(key); SELL_AMOUNT.remove(key); }
    public static void clear() { String key = ComputerWorkspaceClientKey.of(); PRODUCTS.remove(key); STATUS.remove(key); RECEIVED.remove(key); WISHLISTS.remove(key); ORDERS.remove(key); WISHLIST_RECEIVED.remove(key); WALLETS.remove(key); SELL_ROWS.remove(key); SELL_FEEDBACK.remove(key); SELL_AMOUNT.remove(key); PRICES.remove(key); ONBOARDING.remove(key); ONBOARDING_RECEIVED.remove(key); }

    public record ProductRow(String id, String name, String description, String category, boolean enabled, int stock, long restockDays, boolean dealActive, String dealLabel, int dealDiscount, PreviewAsset thumbnail, List<PreviewAsset> gallery, int quantity, long cooldownEnds, List<String> payments, List<ReviewRow> reviews) { }
    public record PreviewAsset(String item, String entity) { }
    public record ReviewRow(String author, String title, String body, int rating, String badge) { }
    public record OrderRow(String product, String option, int units, long gameTime, String status) { }
    public record SellRow(String item, int count, long value, boolean sellable) { }
    public record PriceRow(String item, int value) { }
}
