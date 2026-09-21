package com.craisinlord.antos.content.client;

import com.craisinlord.antos.content.network.ComputerAccessPayload;
import com.craisinlord.antos.content.network.ComputerAccessResultPayload;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public final class AntazonClientState {
    private static final Map<BlockPos, List<ProductRow>> PRODUCTS = new ConcurrentHashMap<>();
    private static final Map<BlockPos, String> STATUS = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Boolean> RECEIVED = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Set<String>> WISHLISTS = new ConcurrentHashMap<>();
    private static final Map<BlockPos, List<OrderRow>> ORDERS = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Boolean> WISHLIST_RECEIVED = new ConcurrentHashMap<>();

    private AntazonClientState() { }

    public static void update(ComputerAccessResultPayload result) {
        String prefix = ComputerAccessPayload.ANTAZON_STATE + "\0\0";
        String purchasePrefix = ComputerAccessPayload.ANTAZON_PURCHASE + "\0";
        String wishlistPrefix = ComputerAccessPayload.ANTAZON_WISHLIST + "\0";
        String ordersPrefix = ComputerAccessPayload.ANTAZON_ORDERS + "\0";
        String reviewPrefix = ComputerAccessPayload.ANTAZON_REVIEW + "\0";
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
                            thumbnail, List.copyOf(gallery), row.get("quantity").getAsInt(), List.copyOf(payments), List.copyOf(reviewRows)));
                }
                PRODUCTS.put(result.pos(), List.copyOf(rows));
                RECEIVED.put(result.pos(), true);
            } else if (result.data().startsWith(wishlistPrefix)) {
                String[] envelope = result.data().split("\u0000", 3);
                Set<String> wishlist = new HashSet<>();
                if (envelope.length > 2 && !envelope[2].isBlank()) {
                    for (var item : JsonParser.parseString(envelope[2]).getAsJsonArray()) wishlist.add(item.getAsString());
                }
                WISHLISTS.put(result.pos(), Set.copyOf(wishlist));
                WISHLIST_RECEIVED.put(result.pos(), true);
                if (envelope.length > 1 && !envelope[1].isBlank()) STATUS.put(result.pos(), envelope[1].toUpperCase());
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
                ORDERS.put(result.pos(), List.copyOf(orders));
            } else if (result.data().startsWith(purchasePrefix) || result.data().startsWith(reviewPrefix)) {
                String[] envelope = result.data().split("\u0000", 3);
                STATUS.put(result.pos(), envelope.length > 1 && !envelope[1].isBlank() ? envelope[1].toUpperCase() : "REQUEST COMPLETE");
            }
        } catch (RuntimeException exception) {
            STATUS.put(result.pos(), "ANTAZON RESPONSE INVALID");
        }
        if (result.result() == ComputerAccessResultPayload.INVALID && result.data().startsWith(purchasePrefix)) STATUS.put(result.pos(), "PURCHASE FAILED");
    }

    public static List<ProductRow> products(BlockPos pos) { return PRODUCTS.getOrDefault(pos, List.of()); }
    public static boolean hasSnapshot(BlockPos pos) { return RECEIVED.getOrDefault(pos, false); }
    public static Set<String> wishlist(BlockPos pos) { return WISHLISTS.getOrDefault(pos, Set.of()); }
    public static boolean hasWishlistSnapshot(BlockPos pos) { return WISHLIST_RECEIVED.getOrDefault(pos, false); }
    public static List<OrderRow> orders(BlockPos pos) { return ORDERS.getOrDefault(pos, List.of()); }
    public static String status(BlockPos pos) { return STATUS.getOrDefault(pos, ""); }
    public static void clear(BlockPos pos) { PRODUCTS.remove(pos); STATUS.remove(pos); RECEIVED.remove(pos); WISHLISTS.remove(pos); ORDERS.remove(pos); WISHLIST_RECEIVED.remove(pos); }

    public record ProductRow(String id, String name, String description, String category, boolean enabled, int stock, long restockDays, boolean dealActive, String dealLabel, int dealDiscount, PreviewAsset thumbnail, List<PreviewAsset> gallery, int quantity, List<String> payments, List<ReviewRow> reviews) { }
    public record PreviewAsset(String item, String entity) { }
    public record ReviewRow(String author, String title, String body, int rating, String badge) { }
    public record OrderRow(String product, String option, int units, long gameTime, String status) { }
}
