package com.craisinlord.antos.content.merchant;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MerchantStoreTest {
    public static void main(String[] args) throws Exception {
        MerchantAuditTest.run();
        MerchantIdentityTest.run();
        if (args.length == 0 || !args[0].equals("--rules-only")) MerchantInventoryTest.run();
        Path directory = Files.createTempDirectory("merchant-test-");
        Path file = directory.resolve("market.json");
        MerchantStore store = new MerchantStore(file);
        JsonObject root = store.snapshot();
        JsonObject listing = listing();
        root.getAsJsonArray("listings").add(listing);
        JsonObject request = new JsonObject();
        request.addProperty("account", "buyer");
        request.addProperty("id", "request-1");
        root.getAsJsonArray("requests").add(request);
        store.commit(root);
        root.getAsJsonArray("listings").get(0).getAsJsonObject().addProperty("lots", 0);
        check(MerchantStore.find(store.snapshot(), "listings", "listing-1").get("lots").getAsInt() == 8, "Snapshots must not mutate stored escrow");
        MerchantStore reopened = new MerchantStore(file);
        check(MerchantStore.requested(reopened.snapshot(), "buyer", "request-1"), "Request identity survives restart");
        check(!MerchantStore.requested(reopened.snapshot(), "another-account", "request-1"), "Request identity is account-scoped");
        JsonObject restored = MerchantStore.find(reopened.snapshot(), "listings", "listing-1");
        MerchantStore.Purchase quote = MerchantStore.quote(restored, "buyer", 0, 3, 5);
        check(quote.quantity() == 192 && quote.price() == 18 && quote.remaining() == 5, "Repeatable lots and fifth-plus alternatives must settle correctly");
        restored.addProperty("lots", quote.remaining());
        restored.addProperty("revision", quote.revision());
        fails("listing_changed", () -> MerchantStore.quote(restored, "another-buyer", 0, 3, 0));
        fails("own_listing", () -> MerchantStore.quote(restored, "seller", 1, 1, 0));
        fails("listing_changed", () -> MerchantStore.quote(restored, "buyer", 1, 6, 0));
        fails("listing_changed", () -> MerchantStore.quote(restored, "buyer", 1, 0, 0));
        fails("invalid_payment", () -> MerchantStore.quote(restored, "buyer", 1, 1, 12));
        MerchantStore.Purchase last = MerchantStore.quote(restored, "buyer", 1, 5, 0);
        restored.addProperty("lots", last.remaining());
        restored.addProperty("revision", last.revision());
        fails("listing_unavailable", () -> MerchantStore.quote(restored, "buyer", 2, 1, 0));
        fails("invalid_quantity", () -> MerchantStore.total(-1, 5));
        check(MerchantStore.total(Integer.MAX_VALUE, Integer.MAX_VALUE) == 4_611_686_014_132_420_609L, "Quantity multiplication must not wrap int");
        JsonObject huge = listing();
        huge.addProperty("quantity", Integer.MAX_VALUE);
        fails("invalid_quantity", () -> MerchantStore.quote(huge, "buyer", 0, 2, 0));
        Files.writeString(file.resolveSibling("market.json.tmp"), "incomplete replacement");
        check(new MerchantStore(file).snapshot().equals(reopened.snapshot()), "Interrupted temporary writes must leave the committed market intact");
        Files.writeString(file, "{\"version\":2}");
        boolean rejected = false;
        try { new MerchantStore(file); } catch (Exception exception) { rejected = true; }
        check(rejected, "Unsupported data must not be replaced with an empty market");
        Files.delete(file.resolveSibling("market.json.tmp"));
        Files.delete(file);
        Files.delete(directory);
        System.out.println("MerchAnt persistence and trading rules passed");
    }

    private static JsonObject listing() {
        JsonObject listing = new JsonObject();
        listing.addProperty("id", "listing-1");
        listing.addProperty("owner", "seller");
        listing.addProperty("quantity", 64);
        listing.addProperty("lots", 8);
        listing.addProperty("revision", 0);
        JsonArray payments = new JsonArray();
        for (int index = 0; index < 12; index++) {
            JsonObject payment = new JsonObject();
            payment.addProperty("item", "currency-" + index);
            payment.addProperty("quantity", index + 1);
            payments.add(payment);
        }
        listing.add("payments", payments);
        return listing;
    }

    private static void fails(String expected, Runnable action) {
        try { action.run(); } catch (IllegalArgumentException exception) {
            check(expected.equals(exception.getMessage()), "Unexpected failure: " + exception.getMessage());
            return;
        }
        throw new AssertionError("Expected " + expected);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
