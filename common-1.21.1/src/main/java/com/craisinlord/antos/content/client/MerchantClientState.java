package com.craisinlord.antos.content.client;

import com.craisinlord.antos.content.network.ComputerAccessPayload;
import com.craisinlord.antos.content.network.ComputerAccessResultPayload;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MerchantClientState {
    private static final Map<String, com.craisinlord.antos.content.merchant.MerchantProtocol.Inbox> RESULTS = new ConcurrentHashMap<>();

    private MerchantClientState() { }

    public static void update(ComputerAccessResultPayload result) {
        String prefix = ComputerAccessPayload.MERCHANT + "\0";
        if (!result.data().startsWith(prefix)) return;
        try {
            String[] fields = result.data().split("\u0000", 3);
            JsonObject value = fields.length == 3 && !fields[2].isBlank() ? JsonParser.parseString(fields[2]).getAsJsonObject() : new JsonObject();
            if (!value.has("ok")) {
                value.addProperty("ok", false);
                value.addProperty("error", fields.length > 1 && !fields[1].isBlank() ? fields[1] : "unavailable");
            }
            accept(value);
        } catch (RuntimeException exception) {
            JsonObject value = new JsonObject();
            value.addProperty("ok", false);
            value.addProperty("error", "invalid_response");
            accept(value);
        }
    }

    private static void accept(JsonObject result) {
        RESULTS.computeIfAbsent(ComputerWorkspaceClientKey.of(), key -> new com.craisinlord.antos.content.merchant.MerchantProtocol.Inbox()).accept(result);
    }

    public static java.util.List<JsonObject> drain() {
        var inbox = RESULTS.get(ComputerWorkspaceClientKey.of());
        return inbox == null ? java.util.List.of() : inbox.drain();
    }

    public static void clear() {
        RESULTS.remove(ComputerWorkspaceClientKey.of());
    }

    public static void clearAll() {
        RESULTS.clear();
    }
}
