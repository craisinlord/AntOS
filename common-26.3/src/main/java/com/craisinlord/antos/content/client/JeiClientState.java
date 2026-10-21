package com.craisinlord.antos.content.client;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.content.network.JeiSyncPayload;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.GZIPInputStream;

public final class JeiClientState {
    public static final int TASK_LOCKED = 0;
    public static final int TASK_AVAILABLE = 1;
    public static final int TASK_COMPLETE = 2;
    public static final int TASK_CLAIMED = 3;
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Map<Integer, Pending> PENDING = new HashMap<>();
    private static volatile Definitions definitions = Definitions.EMPTY;
    private static volatile State state = State.EMPTY;

    private JeiClientState() { }

    public static void addListener(Runnable listener) {
        if (listener != null) LISTENERS.add(listener);
    }

    public static Definitions definitions() { return definitions; }

    public static State state() { return state; }

    public static void clear() {
        PENDING.clear();
        definitions = Definitions.EMPTY;
        state = State.EMPTY;
        notifyListeners();
    }

    public static void update(JeiSyncPayload payload) {
        Pending pending = PENDING.get(payload.kind());
        if (pending == null || pending.version != payload.version() || pending.parts.length != payload.parts() || payload.part() == 0) {
            if (payload.parts() < 1 || payload.parts() > 64 || payload.part() < 0 || payload.part() >= payload.parts()) return;
            pending = new Pending(payload.version(), new byte[payload.parts()][]);
            PENDING.put(payload.kind(), pending);
        }
        if (payload.part() < 0 || payload.part() >= pending.parts.length) return;
        pending.parts[payload.part()] = payload.data();
        for (byte[] part : pending.parts) if (part == null) return;
        PENDING.remove(payload.kind());
        String text;
        try {
            text = decompress(pending.parts);
        } catch (IOException exception) {
            AntOS.LOGGER.warn("Ignoring unreadable AntOS JEI data", exception);
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            if (payload.kind() == JeiSyncPayload.DEFINITIONS) {
                definitions = Definitions.parse(root);
                if (state.version() != definitions.version()) state = State.EMPTY;
            } else if (payload.kind() == JeiSyncPayload.STATE) {
                State parsed = State.parse(root);
                if (parsed.version() != definitions.version()) return;
                state = parsed;
            } else {
                return;
            }
        } catch (RuntimeException exception) {
            AntOS.LOGGER.warn("Ignoring malformed AntOS JEI data", exception);
            return;
        }
        notifyListeners();
    }

    private static void notifyListeners() {
        for (Runnable listener : LISTENERS) {
            try {
                listener.run();
            } catch (RuntimeException exception) {
                AntOS.LOGGER.error("AntOS JEI listener failed", exception);
            }
        }
    }

    private static String decompress(byte[][] parts) throws IOException {
        ByteArrayOutputStream joined = new ByteArrayOutputStream();
        for (byte[] part : parts) joined.write(part);
        try (InputStream input = new GZIPInputStream(new ByteArrayInputStream(joined.toByteArray()))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String string(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    private static int integer(JsonObject object, String key, int fallback) {
        JsonElement element = object.get(key);
        try { return element != null && element.isJsonPrimitive() ? element.getAsInt() : fallback; }
        catch (RuntimeException exception) { return fallback; }
    }

    private static long number(JsonObject object, String key, long fallback) {
        JsonElement element = object.get(key);
        try { return element != null && element.isJsonPrimitive() ? element.getAsLong() : fallback; }
        catch (RuntimeException exception) { return fallback; }
    }

    private static double decimal(JsonObject object, String key, double fallback) {
        JsonElement element = object.get(key);
        try { return element != null && element.isJsonPrimitive() ? element.getAsDouble() : fallback; }
        catch (RuntimeException exception) { return fallback; }
    }

    private static boolean bool(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsBoolean();
    }

    private static JsonArray array(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
    }

    private static List<String> strings(JsonObject object, String key) {
        List<String> values = new ArrayList<>();
        for (JsonElement element : array(object, key)) if (element.isJsonPrimitive()) values.add(element.getAsString());
        return List.copyOf(values);
    }

    private static BitSet bits(JsonObject object, String key) {
        BitSet bits = new BitSet();
        for (JsonElement element : array(object, key)) if (element.isJsonPrimitive()) bits.set(element.getAsInt());
        return bits;
    }

    private static Map<String, String> map(JsonObject object, String key) {
        Map<String, String> values = new HashMap<>();
        JsonElement element = object.get(key);
        if (element != null && element.isJsonObject())
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet())
                if (entry.getValue().isJsonPrimitive()) values.put(entry.getKey(), entry.getValue().getAsString());
        return Map.copyOf(values);
    }

    private static Unlock unlock(JsonObject object) {
        JsonElement element = object.get("unlock");
        if (element == null || !element.isJsonObject()) return Unlock.NONE;
        JsonObject row = element.getAsJsonObject();
        return new Unlock(string(row, "mode", "all"), strings(row, "tasks"), strings(row, "disks"), strings(row, "commands"));
    }

    public record Definitions(long version, List<Listing> listings, List<Sell> sell, List<Task> tasks,
                              Map<String, String> taskTitles, Map<String, String> diskTitles) {
        static final Definitions EMPTY = new Definitions(0L, List.of(), List.of(), List.of(), Map.of(), Map.of());

        static Definitions parse(JsonObject root) {
            List<Listing> listings = new ArrayList<>();
            for (JsonElement element : array(root, "listings")) {
                JsonObject row = element.getAsJsonObject();
                List<Stack> rewards = new ArrayList<>();
                for (JsonElement reward : array(row, "rewards")) {
                    JsonObject rewardRow = reward.getAsJsonObject();
                    rewards.add(new Stack(string(rewardRow, "item", ""), integer(rewardRow, "count", 1), 1.0, ""));
                }
                List<Payment> payments = new ArrayList<>();
                for (JsonElement payment : array(row, "payments")) {
                    JsonObject paymentRow = payment.getAsJsonObject();
                    payments.add(new Payment(string(paymentRow, "type", ""), string(paymentRow, "resource", ""), integer(paymentRow, "amount", 0)));
                }
                listings.add(new Listing(listings.size(), string(row, "id", ""), string(row, "name", ""), string(row, "pool_item", ""),
                        string(row, "variant_mode", ""), strings(row, "variants"), List.copyOf(rewards), List.copyOf(payments),
                        bool(row, "hidden"), bool(row, "rotating"), integer(row, "stock", 0), integer(row, "limit", 0),
                        string(row, "limit_reset", "none"), unlock(row)));
            }
            List<Sell> sell = new ArrayList<>();
            for (JsonElement element : array(root, "sell")) {
                JsonObject row = element.getAsJsonObject();
                sell.add(new Sell(sell.size(), string(row, "item", ""), integer(row, "value", 0), integer(row, "limit", 0),
                        string(row, "limit_reset", "none"), bool(row, "hidden"), unlock(row)));
            }
            List<Task> tasks = new ArrayList<>();
            for (JsonElement element : array(root, "tasks")) {
                JsonObject row = element.getAsJsonObject();
                List<Stack> items = new ArrayList<>();
                for (JsonElement item : array(row, "items")) {
                    JsonObject itemRow = item.getAsJsonObject();
                    items.add(new Stack(string(itemRow, "item", ""), integer(itemRow, "count", 1), decimal(itemRow, "chance", 1.0), string(itemRow, "group", "")));
                }
                tasks.add(new Task(tasks.size(), string(row, "id", ""), string(row, "title", ""), string(row, "category", ""), List.copyOf(items),
                        number(row, "antcoins", 0L), integer(row, "experience", 0), integer(row, "levels", 0),
                        strings(row, "requires"), strings(row, "requires_any")));
            }
            return new Definitions(number(root, "version", 0L), List.copyOf(listings), List.copyOf(sell), List.copyOf(tasks),
                    map(root, "titles"), map(root, "disks"));
        }
    }

    public record State(long version, boolean signedIn, boolean account, BitSet locked, BitSet unavailable, Map<Integer, Deal> deals,
                        BitSet sellLocked, Map<Integer, Integer> tasks) {
        static final State EMPTY = new State(0L, false, false, new BitSet(), new BitSet(), Map.of(), new BitSet(), Map.of());

        static State parse(JsonObject root) {
            Map<Integer, Deal> deals = new HashMap<>();
            long received = System.currentTimeMillis();
            for (JsonElement element : array(root, "deals")) {
                JsonObject row = element.getAsJsonObject();
                List<Integer> prices = new ArrayList<>();
                for (JsonElement price : array(row, "prices")) prices.add(price.getAsInt());
                long endsIn = number(row, "ends_in_ms", 0L);
                deals.put(integer(row, "i", -1), new Deal(string(row, "label", ""), integer(row, "discount", 0), integer(row, "remaining", -1),
                        endsIn > 0L ? received + endsIn : 0L, List.copyOf(prices)));
            }
            Map<Integer, Integer> tasks = new HashMap<>();
            for (JsonElement element : array(root, "tasks")) {
                int value = element.getAsInt();
                tasks.put(value / 4, value % 4);
            }
            return new State(number(root, "version", 0L), bool(root, "signed_in"), bool(root, "account"), bits(root, "locked"),
                    bits(root, "unavailable"), Map.copyOf(deals), bits(root, "sell_locked"), Map.copyOf(tasks));
        }

        public boolean listingLocked(Listing listing) { return locked.get(listing.index()); }

        public boolean listingUnavailable(Listing listing) { return unavailable.get(listing.index()); }

        public Deal deal(Listing listing) { return deals.get(listing.index()); }

        public boolean sellLocked(Sell rule) { return sellLocked.get(rule.index()); }

        public int taskStatus(Task task) { return tasks.getOrDefault(task.index(), -1); }
    }

    public record Listing(int index, String id, String name, String poolItem, String variantMode, List<String> variants, List<Stack> rewards,
                          List<Payment> payments, boolean hidden, boolean rotating, int stock, int limit, String limitReset, Unlock unlock) { }

    public record Sell(int index, String item, int value, int limit, String limitReset, boolean hidden, Unlock unlock) { }

    public record Task(int index, String id, String title, String category, List<Stack> items, long antcoins, int experience, int levels,
                       List<String> requires, List<String> requiresAny) { }

    public record Stack(String item, int count, double chance, String group) { }

    public record Payment(String type, String resource, int amount) { }

    public record Deal(String label, int discount, int remaining, long endsAt, List<Integer> prices) { }

    public record Unlock(String mode, List<String> tasks, List<String> disks, List<String> commands) {
        static final Unlock NONE = new Unlock("all", List.of(), List.of(), List.of());

        public boolean empty() { return tasks.isEmpty() && disks.isEmpty() && commands.isEmpty(); }
    }

    private static final class Pending {
        private final long version;
        private final byte[][] parts;

        private Pending(long version, byte[][] parts) {
            this.version = version;
            this.parts = parts;
        }
    }
}
