package com.craisinlord.antos.content.merchant;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.UUID;

public final class MerchantProtocol {
    private MerchantProtocol() { }

    public static JsonObject parse(String encoded) {
        try {
            JsonObject request = JsonParser.parseString(encoded).getAsJsonObject();
            String action = string(request, "action", 16);
            if (!List.of("state", "publish", "buy", "cancel", "deliver").contains(action)) invalid();
            if (request.has("query")) UUID.fromString(string(request, "query", 36));
            if (request.has("view") && !List.of("browse", "mine", "history", "deliveries", "summary").contains(string(request, "view", 16))) invalid();
            if (request.has("sort") && !List.of("newest", "name", "price").contains(string(request, "sort", 16))) invalid();
            for (String key : List.of("search", "currency", "selected")) if (request.has(key)) string(request, key, 256);
            if (request.has("page") && integer(request, "page") < 0) invalid();
            for (String key : List.of("affordable", "showDispatched")) {
                if (request.has(key) && (!request.get(key).isJsonPrimitive() || !request.getAsJsonPrimitive(key).isBoolean())) invalid();
            }
            if (!action.equals("state")) UUID.fromString(string(request, "request", 36));
            if (action.equals("publish")) {
                int slot = integer(request, "slot");
                if (slot < 0 || slot >= 36 || !request.get("expected").isJsonObject()) invalid();
                positive(request, "quantity");
                positive(request, "lots");
                if (!request.get("payments").isJsonArray() || request.getAsJsonArray("payments").isEmpty()) invalid();
                for (JsonElement element : request.getAsJsonArray("payments")) {
                    JsonObject payment = element.getAsJsonObject();
                    string(payment, "item", 256);
                    positive(payment, "quantity");
                }
            } else if (!action.equals("state")) {
                UUID.fromString(string(request, "id", 36));
                if (!action.equals("deliver") && integer(request, "revision") < 0) invalid();
                if (action.equals("buy")) {
                    positive(request, "lots");
                    if (integer(request, "option") < 0) invalid();
                }
            }
            return request;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("invalid_request");
        }
    }

    private static void positive(JsonObject object, String key) {
        if (integer(object, key) < 1) invalid();
    }

    private static String string(JsonObject object, String key, int maximum) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) invalid();
        String text = value.getAsString();
        if (text.length() > maximum || text.indexOf('\0') >= 0) invalid();
        return text;
    }

    public static int integer(JsonObject object, String key) {
        try {
            JsonElement value = object.get(key);
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) invalid();
            return value.getAsBigDecimal().intValueExact();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("invalid_quantity");
        }
    }

    public static JsonObject envelope(JsonObject request) {
        JsonObject result = new JsonObject();
        for (String key : List.of("query", "request")) {
            JsonElement value = request.get(key);
            if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() && value.getAsString().length() <= 36) result.add(key, value.deepCopy());
        }
        return result;
    }

    public static JsonObject failure(String encoded, String error) {
        JsonObject result;
        try { result = envelope(JsonParser.parseString(encoded).getAsJsonObject()); }
        catch (RuntimeException exception) { result = new JsonObject(); }
        result.addProperty("ok", false);
        result.addProperty("error", error);
        return result;
    }

    public static JsonObject operation(JsonObject request) {
        JsonObject result = new JsonObject();
        for (String key : List.of("action", "id", "revision", "option", "lots", "slot", "quantity", "expected", "payments")) {
            if (request.has(key)) result.add(key, request.get(key).deepCopy());
        }
        return result;
    }

    public static JsonObject compactResponse(String encoded) {
        try {
            JsonObject response = JsonParser.parseString(encoded).getAsJsonObject();
            if (response.has("ok") && response.get("ok").getAsBoolean() && response.has("notice") && response.has("request")) {
                JsonObject result = envelope(response);
                result.addProperty("ok", true);
                result.add("notice", response.get("notice").deepCopy());
                return result;
            }
        } catch (RuntimeException exception) { }
        return failure(encoded, "result_too_large");
    }

    public static boolean matches(JsonObject result, String key, String identity) {
        return identity != null && !identity.isEmpty() && result.has(key) && result.get(key).isJsonPrimitive() && identity.equals(result.get(key).getAsString());
    }

    public static final class Inbox {
        private final java.util.concurrent.ConcurrentLinkedQueue<JsonObject> results = new java.util.concurrent.ConcurrentLinkedQueue<>();

        public void accept(JsonObject result) { results.add(result.deepCopy()); }

        public List<JsonObject> drain() {
            List<JsonObject> drained = new java.util.ArrayList<>();
            JsonObject result;
            while ((result = results.poll()) != null) drained.add(result);
            return drained;
        }
    }

    private static void invalid() { throw new IllegalArgumentException("invalid_request"); }
}
