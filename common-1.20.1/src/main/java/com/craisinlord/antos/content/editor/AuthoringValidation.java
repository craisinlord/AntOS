package com.craisinlord.antos.content.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashSet;
import java.util.Set;

public final class AuthoringValidation {
    public static final int MAX_DOCUMENT_CHARS = 262_144;
    public static final int MAX_DRAFTS = 4096;
    private AuthoringValidation() { }

    public static JsonObject parse(String text) {
        return parse(text, MAX_DOCUMENT_CHARS);
    }

    public static JsonObject envelope(String text) { return parse(text, AuthoringWire.MAX_CHARS); }

    private static JsonObject parse(String text, int maximum) {
        if (text.length() > maximum) throw new IllegalArgumentException("Document exceeds " + maximum + " characters");
        int depth = 0;
        char[] delimiters = new char[32];
        int delimiterCount = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (quoted) {
                if (escaped) escaped = false;
                else if (character == '\\') escaped = true;
                else if (character == '"') quoted = false;
            } else if (character == '"') quoted = true;
            else if (character == '{' || character == '[') {
                if (++depth > 32) throw new IllegalArgumentException("Document nesting exceeds 32 levels");
                delimiters[delimiterCount++] = character;
            } else if (character == '}' || character == ']') {
                if (delimiterCount == 0 || delimiters[--delimiterCount] != (character == '}' ? '{' : '['))
                    throw new IllegalArgumentException("Document has mismatched JSON delimiters");
                depth--;
            }
        }
        if (quoted || delimiterCount != 0) throw new IllegalArgumentException("Document has an unfinished JSON string or structure");
        JsonElement result = JsonParser.parseString(text);
        if (!result.isJsonObject()) throw new IllegalArgumentException("A content definition must be an object");
        return result.getAsJsonObject();
    }

    public static void validate(AuthoringKind kind, JsonObject object) {
        switch (kind) {
            case TASK -> task(object);
            case MESSAGE -> {
                required(object, "sender", 128);
                required(object, "subject", 64);
                string(object, "body", 4096);
                JsonObject trigger = object(object, "trigger");
                required(trigger, "type", 64);
                for (String field : Set.of("delay", "delay_seconds", "delay_minutes", "weight"))
                    number(trigger, field, 0, 1_000_000);
                if (trigger.has("weight")) number(trigger, "weight", 1, 1_000_000);
                if (object.has("deliver_window")) {
                    JsonObject window = object(object, "deliver_window");
                    number(window, "from", 0, 24000);
                    number(window, "to", 0, 24000);
                }
                if (object.has("delivery")) {
                    JsonObject delivery = object(object, "delivery");
                    if (delivery.has("on") && !Set.of("read", "deliver").contains(string(delivery, "on", 16)))
                        throw new IllegalArgumentException("delivery.on must be read or deliver");
                    if (delivery.has("items")) array(delivery, "items", 108);
                }
                if (object.has("antcoins")) number(object, "antcoins", 0, 1_000_000_000);
            }
            case CATEGORY, GROUP -> {
                required(object, "title", 512);
                number(object, "sort_order", -100_000, 100_000);
            }
            case DISK -> {
                required(object, "title", 512);
                string(object, "category", 256);
                string(object, "wallpaper", 256);
                string(object, "description", 4096);
                for (String key : Set.of("entries", "tasks")) if (object.has(key)) array(object, key, 256);
                for (String key : Set.of("programs", "subscribe")) if (object.has(key)) array(object, key, 64);
                if (object.has("files")) array(object, "files", 32);
                if (object.has("rewards")) array(object, "rewards", 108);
                if (object.has("encryption")) {
                    JsonObject encryption = object(object, "encryption");
                    string(encryption, "password", 128);
                    string(encryption, "hint", 512);
                    if (encryption.has("password_any")) array(encryption, "password_any", 8);
                }
            }
            case DISK_CATEGORY -> {
                required(object, "title", 512);
                required(object, "texture", 256);
                string(object, "color", 16);
            }
            case PROGRAM -> {
                required(object, "type", 16);
                required(object, "title", 512);
                if (object.has("questions")) array(object, "questions", 128);
                if (object.has("rewards")) array(object, "rewards", 108);
                if (object.has("nodes") && !object.get("nodes").isJsonObject()) throw new IllegalArgumentException("nodes must be an object");
            }
            case TERMINAL_COMMAND -> {
                required(object, "command", 32);
                if (object.has("aliases")) array(object, "aliases", 64);
                if (object.has("arguments")) array(object, "arguments", 8);
                for (String key : Set.of("output", "locked_output")) if (object.has(key)) array(object, key, 64);
                for (String key : Set.of("entries", "tasks")) if (object.has(key)) array(object, key, 128);
                for (String key : Set.of("programs", "subscribe")) if (object.has(key)) array(object, key, 64);
                if (object.has("files")) array(object, "files", 32);
                if (object.has("rewards")) array(object, "rewards", 108);
                if (object.has("requires")) object(object, "requires");
            }
            case REWARD_POOL -> {
                JsonArray entries = array(object, "entries", 64);
                if (entries.isEmpty()) throw new IllegalArgumentException("A reward pool needs entries");
                for (JsonElement entry : entries) {
                    JsonObject row = entry.getAsJsonObject();
                    if (!row.has("item") && !row.has("pool")) throw new IllegalArgumentException("Pool entries need an item or pool");
                    number(row, "weight", 1, 1_000_000);
                    number(row, "count", 1, 108);
                }
            }
            case MAIL_POOL -> {
                number(object, "chance_per_day", 0, 1);
                number(object, "max_per_day", 0, 1_000_000);
            }
            case PRODUCT -> {
                required(object, "name", 512);
                string(object, "description", 32_768);
                number(object, "quantity", 1, 1_000_000);
                JsonArray payments = array(object, "payments", 16);
                if (payments.isEmpty()) throw new IllegalArgumentException("A product needs at least one payment");
                for (JsonElement element : payments) {
                    JsonObject payment = element.getAsJsonObject();
                    required(payment, "type", 64);
                    required(payment, "resource", 240);
                    number(payment, "amount", 1, 1_000_000_000);
                }
                JsonArray rewards = object.has("rewards") ? array(object, "rewards", 108) : new JsonArray();
                if (rewards.isEmpty() && !object.has("item_pool") && !object.has("variants"))
                    throw new IllegalArgumentException("A product needs rewards, an item pool or variants");
                for (JsonElement element : rewards) {
                    JsonObject reward = element.getAsJsonObject();
                    required(reward, "item", 240);
                    number(reward, "count", 1, 108);
                }
                if (object.has("availability")) {
                    JsonObject availability = object(object, "availability");
                    for (String key : Set.of("server_stock", "restock_after_minecraft_days", "restock_after_real_hours", "cooldown_minecraft_days", "player_limit"))
                        number(availability, key, 0, 1_000_000);
                }
            }
            case SELL_RULE -> {
                if (!object.has("value")) throw new IllegalArgumentException("value is required");
                number(object, "value", 1, 1_000_000_000);
                if (!object.has("items") && !object.has("item") && !object.has("tags") && !object.has("tag"))
                    throw new IllegalArgumentException("A sell rule needs at least one item or tag");
                if (object.has("limit")) number(object, "limit", 0, 1_000_000);
            }
            case DEAL_CAMPAIGN -> {
                required(object, "label", 512);
                number(object, "picks", 1, 1_000);
                JsonObject discount = object(object, "discount_percent");
                number(discount, "min", 1, 90);
                number(discount, "max", 1, 90);
            }
            case ARCHIVE_ENTRY -> {
                required(object, "title", 512);
                string(object, "subtitle", 512);
                string(object, "category", 128);
                number(object, "sort_order", -100_000, 100_000);
                number(object, "search_radius", 0, 1_000);
                number(object, "render_scale", .1, 3);
                number(object, "rotation", -360, 360);
                if (object.has("description")) array(object, "description", 128);
                if (object.has("content")) array(object, "content", 128);
                if (object.has("gallery")) array(object, "gallery", 32);
                if (object.has("lookup")) array(object, "lookup", 128);
                if (object.has("lookup_recipes")) array(object, "lookup_recipes", 128);
            }
            case ARCHIVE_FOLDER -> {
                required(object, "title", 512);
                string(object, "icon_item", 240);
                number(object, "sort_order", -100_000, 100_000);
            }
        }
    }

    private static void task(JsonObject object) {
        required(object, "title", 512);
        string(object, "description", 32_768);
        number(object, "sort_order", -100_000, 100_000);
        number(object, "size", .5, 3);
        number(object, "icon_scale", .25, 2);
        number(object, "line_bend", 0, 1);
        if (object.has("position")) {
            JsonObject position = object(object, "position");
            number(position, "x", -128, 128);
            number(position, "y", -128, 128);
        }
        if (object.has("objectives")) {
            Set<String> ids = new HashSet<>();
            for (JsonElement value : array(object, "objectives", 256)) {
                JsonObject row = value.getAsJsonObject();
                String id = required(row, "id", 128);
                if (!ids.add(id)) throw new IllegalArgumentException("Duplicate objective ID: " + id);
                required(row, "type", 128);
                if (!row.has("target_category")) required(row, "target", 512);
                number(row, "count", 1, 1_000_000);
            }
        }
        if (object.has("rewards")) rewards(array(object, "rewards", 27), 0);
        for (String key : Set.of("requires", "requires_any", "connects", "archive_entries"))
            if (object.has(key)) array(object, key, 1024);
    }

    private static void rewards(JsonArray rewards, int depth) {
        if (depth > 2) throw new IllegalArgumentException("Choice rewards cannot contain nested choices");
        for (JsonElement value : rewards) {
            JsonObject reward = value.getAsJsonObject();
            String type = required(reward, "type", 64);
            number(reward, "count", 1, 108);
            number(reward, "points", 1, 1_000_000);
            number(reward, "levels", 1, 1000);
            number(reward, "amount", 1, 1_000_000_000);
            number(reward, "duration_ticks", 1, 72_000);
            number(reward, "amplifier", 0, 255);
            if (type.equals("choice")) {
                if (depth != 0) throw new IllegalArgumentException("Choice rewards cannot contain nested choices");
                JsonArray options = array(reward, "options", 6);
                if (options.size() < 2) throw new IllegalArgumentException("A choice needs at least two options");
                Set<String> ids = new HashSet<>();
                for (JsonElement optionValue : options) {
                    JsonObject option = optionValue.getAsJsonObject();
                    if (!ids.add(required(option, "id", 64))) throw new IllegalArgumentException("Choice option IDs must be unique");
                    required(option, "title", 128);
                    JsonArray nested = array(option, "rewards", 8);
                    if (nested.isEmpty()) throw new IllegalArgumentException("Each choice option needs a reward");
                    rewards(nested, depth + 1);
                }
            }
        }
    }

    public static JsonObject object(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException(key + " must be an object");
        return value.getAsJsonObject();
    }

    public static JsonArray array(JsonObject parent, String key, int maximum) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonArray()) throw new IllegalArgumentException(key + " must be a list");
        JsonArray array = value.getAsJsonArray();
        if (array.size() > maximum) throw new IllegalArgumentException(key + " exceeds " + maximum + " entries");
        return array;
    }

    public static String required(JsonObject parent, String key, int maximum) {
        String result = string(parent, key, maximum);
        if (result.isBlank()) throw new IllegalArgumentException(key + " is required");
        return result;
    }

    public static String string(JsonObject parent, String key, int maximum) {
        JsonElement value = parent.get(key);
        if (value == null) return "";
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(key + " must be text");
        String result = value.getAsString();
        if (result.length() > maximum) throw new IllegalArgumentException(key + " exceeds " + maximum + " characters");
        return result;
    }

    public static void number(JsonObject parent, String key, double minimum, double maximum) {
        JsonElement value = parent.get(key);
        if (value == null) return;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(key + " must be a number");
        double number = value.getAsDouble();
        if (Set.of("count", "weight", "sort_order", "x", "y", "points", "levels", "antcoins", "amount", "max_per_day", "delay", "delay_seconds", "delay_minutes", "from", "to", "duration_ticks", "amplifier").contains(key)
                && value.getAsBigDecimal().stripTrailingZeros().scale() > 0) throw new IllegalArgumentException(key + " must be a whole number");
        if (!Double.isFinite(number) || number < minimum || number > maximum)
            throw new IllegalArgumentException(key + " must be between " + minimum + " and " + maximum);
    }
}
