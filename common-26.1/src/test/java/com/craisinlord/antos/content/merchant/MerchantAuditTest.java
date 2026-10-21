package com.craisinlord.antos.content.merchant;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.UUID;

public final class MerchantAuditTest {
    public static void run() {
        JsonObject buy = new JsonObject();
        buy.addProperty("action", "buy");
        buy.addProperty("query", UUID.randomUUID().toString());
        buy.addProperty("request", UUID.randomUUID().toString());
        buy.addProperty("id", UUID.randomUUID().toString());
        buy.addProperty("revision", 0);
        buy.addProperty("lots", 2);
        buy.addProperty("option", 1);
        check(MerchantProtocol.parse(buy.toString()).equals(buy), "Valid trades must preserve their exact intent");
        for (String field : new String[]{"lots", "revision", "option"}) {
            JsonObject invalid = buy.deepCopy();
            invalid.addProperty(field, 1.5);
            rejects(() -> MerchantProtocol.parse(invalid.toString()));
            invalid.addProperty(field, "2");
            rejects(() -> MerchantProtocol.parse(invalid.toString()));
            invalid.addProperty(field, Long.MAX_VALUE);
            rejects(() -> MerchantProtocol.parse(invalid.toString()));
        }
        JsonObject badView = buy.deepCopy();
        badView.addProperty("view", "missing");
        rejects(() -> MerchantProtocol.parse(badView.toString()));
        JsonObject failure = MerchantProtocol.failure(buy.toString(), "busy");
        check(MerchantProtocol.matches(failure, "request", buy.get("request").getAsString()), "Busy/error replies must identify the mutation they rejected");
        check(!MerchantProtocol.matches(failure, "request", UUID.randomUUID().toString()), "An unrelated failure must not dismiss a pending trade");
        JsonObject oldPoll = new JsonObject();
        oldPoll.addProperty("query", UUID.randomUUID().toString());
        oldPoll.addProperty("ok", true);
        check(!MerchantProtocol.matches(oldPoll, "query", buy.get("query").getAsString()), "Stale poll replies must not replace the current view");
        MerchantProtocol.Inbox inbox = new MerchantProtocol.Inbox();
        inbox.accept(failure);
        for (int index = 0; index < 40; index++) inbox.accept(oldPoll);
        var replies = inbox.drain();
        check(replies.size() == 41 && replies.get(0).equals(failure), "Refreshes must not overwrite a trade acknowledgement before the next frame");
        check(inbox.drain().isEmpty(), "Processed replies must not replay on later frames");
        JsonObject committed = MerchantProtocol.envelope(buy);
        committed.addProperty("ok", true);
        committed.addProperty("notice", "buy");
        committed.add("state", new JsonObject());
        JsonObject compact = MerchantProtocol.compactResponse(committed.toString());
        check(compact.get("ok").getAsBoolean() && compact.get("notice").getAsString().equals("buy") && !compact.has("state"), "An oversized refresh must preserve the successful trade acknowledgement");
        check(MerchantProtocol.matches(compact, "request", buy.get("request").getAsString()), "Compacted acknowledgements must preserve request identity");
        MerchantStore.Batch batch = MerchantStore.deliveryBatch(Integer.MAX_VALUE, 64, 108);
        check(batch.sent() == 6912 && (long) batch.sent() + batch.remaining() == Integer.MAX_VALUE, "Oversized shipments must split without losing or overflowing item quantities");
        check(MerchantStore.deliveryBatch(128, 64, 108).remaining() == 0, "Ordinary trades must remain one shipment");
        rejects(() -> MerchantStore.deliveryBatch(1, 0, 108));
        JsonObject root = new JsonObject();
        JsonArray requests = new JsonArray();
        root.add("requests", requests);
        JsonObject recorded = new JsonObject();
        recorded.addProperty("account", "buyer");
        recorded.addProperty("id", buy.get("request").getAsString());
        recorded.add("operation", MerchantProtocol.operation(buy));
        requests.add(recorded);
        JsonObject retry = buy.deepCopy();
        retry.addProperty("view", "history");
        retry.addProperty("query", UUID.randomUUID().toString());
        MerchantStore.validateReplay(root, "buyer", buy.get("request").getAsString(), MerchantProtocol.operation(retry));
        retry.addProperty("lots", 3);
        rejects(() -> MerchantStore.validateReplay(root, "buyer", buy.get("request").getAsString(), MerchantProtocol.operation(retry)));
        JsonObject listing = new JsonObject();
        JsonArray payments = new JsonArray();
        listing.add("payments", payments);
        payments.add(payment("minecraft:diamond", 4));
        payments.add(payment("minecraft:emerald", 2));
        payments.add(payment("minecraft:diamond", 8));
        check(MerchantStore.affordable(listing, "", Map.of("minecraft:emerald", 2L)), "Any affordable alternative should match an unrestricted search");
        check(!MerchantStore.affordable(listing, "minecraft:diamond", Map.of("minecraft:emerald", 2L)), "Diamond filtering must not match a trade affordable only in emeralds");
        check(MerchantStore.affordable(listing, "minecraft:diamond", Map.of("minecraft:diamond", 4L)), "Exact currency matching must include sufficient matching stock");
        check(MerchantStore.paymentPrice(listing, "minecraft:diamond") == 4, "Sorting must use the cheapest accepted alternative in the selected currency");
    }

    private static JsonObject payment(String item, int quantity) {
        JsonObject row = new JsonObject();
        row.addProperty("item", item);
        row.addProperty("quantity", quantity);
        return row;
    }

    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException exception) { return; }
        throw new AssertionError("Expected invalid or conflicting request to be rejected");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
