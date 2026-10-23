package com.craisinlord.antos.content.merchant;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class MerchantStore {
    public static final int MAX_ACTIVE_LISTINGS = 50;
    public static final int MAX_REQUESTS = 2000;
    public static final long SOLD_OUT_RETENTION = 7L * 24 * 60 * 60 * 1000;
    public static final long SHIPMENT_RETENTION = 30L * 24 * 60 * 60 * 1000;
    public static final long TRADE_RETENTION = 90L * 24 * 60 * 60 * 1000;
    private final Path path;
    private JsonObject state;

    public MerchantStore(Path path) throws IOException {
        this.path = path;
        state = Files.exists(path) ? JsonParser.parseString(Files.readString(path)).getAsJsonObject() : empty();
        if (!state.has("version") || state.get("version").getAsInt() != 1) throw new IOException("Unsupported MERCHANT data version");
        for (String key : new String[]{"listings", "shipments", "trades", "requests"}) {
            if (!state.has(key) || !state.get(key).isJsonArray()) throw new IOException("Invalid MERCHANT data: " + key);
        }
    }

    private static JsonObject empty() {
        JsonObject value = new JsonObject();
        value.addProperty("version", 1);
        for (String key : new String[]{"listings", "shipments", "trades", "requests"}) value.add(key, new JsonArray());
        return value;
    }

    public synchronized JsonObject snapshot() {
        return state.deepCopy();
    }

    synchronized <T> T read(java.util.function.Function<JsonObject, T> reader) { return reader.apply(state); }

    public synchronized boolean hasPending() { return state.has("pending"); }

    public synchronized void commit(JsonObject next) throws IOException {
        prune(next, System.currentTimeMillis());
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        byte[] bytes = next.toString().getBytes(StandardCharsets.UTF_8);
        try (FileChannel output = FileChannel.open(temporary, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) output.write(buffer);
            output.force(true);
        }
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("MERCHANT requires atomic file replacement", exception);
        }
        state = next.deepCopy();
    }

    public static void prune(JsonObject root, long now) {
        root.getAsJsonArray("listings").asList().removeIf(element -> {
            JsonObject row = element.getAsJsonObject();
            return row.has("lots") && row.get("lots").getAsInt() < 1 && created(row) < now - SOLD_OUT_RETENTION;
        });
        root.getAsJsonArray("shipments").asList().removeIf(element -> {
            JsonObject row = element.getAsJsonObject();
            return row.has("status") && !row.get("status").getAsString().equals("pending") && created(row) < now - SHIPMENT_RETENTION;
        });
        root.getAsJsonArray("trades").asList().removeIf(element -> created(element.getAsJsonObject()) < now - TRADE_RETENTION);
        JsonArray requests = root.getAsJsonArray("requests");
        while (requests.size() > MAX_REQUESTS) requests.remove(0);
    }

    private static long created(JsonObject row) {
        return row.has("created") ? row.get("created").getAsLong() : Long.MAX_VALUE;
    }

    public synchronized boolean hasDeferred(String player) {
        if (!state.has("deferred")) return false;
        for (var element : state.getAsJsonArray("deferred")) if (element.getAsJsonObject().get("player").getAsString().equals(player)) return true;
        return false;
    }

    public static int activeListings(JsonObject root, String owner) {
        int count = 0;
        for (var element : root.getAsJsonArray("listings")) {
            JsonObject row = element.getAsJsonObject();
            if (row.get("owner").getAsString().equals(owner) && row.get("lots").getAsInt() > 0) count++;
        }
        return count;
    }

    public static long total(int perLot, int lots) {
        if (perLot < 1 || lots < 1) throw new IllegalArgumentException("invalid_quantity");
        return Math.multiplyExact((long) perLot, lots);
    }

    public static boolean requested(JsonObject root, String account, String request) {
        for (var element : root.getAsJsonArray("requests")) {
            JsonObject row = element.getAsJsonObject();
            if (row.get("account").getAsString().equals(account) && row.get("id").getAsString().equals(request)) return true;
        }
        return false;
    }

    public static void validateReplay(JsonObject root, String account, String request, JsonObject operation) {
        for (var element : root.getAsJsonArray("requests")) {
            JsonObject row = element.getAsJsonObject();
            if (row.get("account").getAsString().equals(account) && row.get("id").getAsString().equals(request)
                    && row.has("operation") && !row.get("operation").equals(operation)) throw new IllegalArgumentException("request_conflict");
        }
    }

    public static long paymentPrice(JsonObject listing, String currency) {
        return listing.getAsJsonArray("payments").asList().stream().map(value -> value.getAsJsonObject())
                .filter(row -> row.get("item").getAsString().equals(currency)).mapToLong(row -> row.get("quantity").getAsLong()).min().orElse(-1);
    }

    public static boolean affordable(JsonObject listing, String currency, java.util.Map<String, Long> available) {
        for (var element : listing.getAsJsonArray("payments")) {
            JsonObject payment = element.getAsJsonObject();
            String item = payment.get("item").getAsString();
            if ((currency.isEmpty() || currency.equals(item)) && available.getOrDefault(item, 0L) >= payment.get("quantity").getAsLong()) return true;
        }
        return false;
    }

    public static JsonObject find(JsonObject root, String collection, String id) {
        for (var element : root.getAsJsonArray(collection)) {
            JsonObject row = element.getAsJsonObject();
            if (row.get("id").getAsString().equals(id)) return row;
        }
        throw new IllegalArgumentException("listing_unavailable");
    }

    public record Purchase(long quantity, long price, int remaining, int revision) { }

    public record Batch(int sent, int remaining) { }

    public static Batch deliveryBatch(int quantity, int stackSize, int stackLimit) {
        if (quantity < 1 || stackSize < 1 || stackLimit < 1) throw new IllegalArgumentException("invalid_quantity");
        int sent = (int) Math.min(quantity, (long) stackSize * stackLimit);
        return new Batch(sent, quantity - sent);
    }

    public static Purchase quote(JsonObject listing, String buyer, int revision, int lots, int option) {
        if (listing.get("owner").getAsString().equals(buyer)) throw new IllegalArgumentException("own_listing");
        int available = listing.get("lots").getAsInt();
        if (available < 1) throw new IllegalArgumentException("listing_unavailable");
        if (listing.get("revision").getAsInt() != revision || lots < 1 || lots > available) throw new IllegalArgumentException("listing_changed");
        JsonArray payments = listing.getAsJsonArray("payments");
        if (option < 0 || option >= payments.size()) throw new IllegalArgumentException("invalid_payment");
        long quantity = total(listing.get("quantity").getAsInt(), lots);
        long price = total(payments.get(option).getAsJsonObject().get("quantity").getAsInt(), lots);
        if (quantity > Integer.MAX_VALUE || price > Integer.MAX_VALUE || revision == Integer.MAX_VALUE) throw new IllegalArgumentException("invalid_quantity");
        return new Purchase(quantity, price, available - lots, revision + 1);
    }
}
