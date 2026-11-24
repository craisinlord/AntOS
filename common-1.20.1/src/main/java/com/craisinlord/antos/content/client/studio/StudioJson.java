package com.craisinlord.antos.content.client.studio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

final class StudioJson {
    static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private StudioJson() { }

    static String string(JsonObject object, String key) { return string(object, key, ""); }

    static String string(JsonObject object, String key, String fallback) {
        JsonElement value = object == null ? null : object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    static double number(JsonObject object, String key, double fallback) {
        try {
            JsonElement value = object == null ? null : object.get(key);
            return value == null || !value.isJsonPrimitive() ? fallback : value.getAsDouble();
        } catch (NumberFormatException exception) { return fallback; }
    }

    static int integer(JsonObject object, String key, int fallback) { return (int) number(object, key, fallback); }

    static boolean bool(JsonObject object, String key, boolean fallback) {
        JsonElement value = object == null ? null : object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() ? value.getAsBoolean() : fallback;
    }

    static JsonArray list(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    static JsonObject object(JsonObject object, String key) {
        JsonElement value = object == null ? null : object.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    static List<String> strings(JsonObject object, String key) {
        List<String> result = new ArrayList<>();
        for (JsonElement value : list(object, key)) if (value.isJsonPrimitive()) result.add(value.getAsString());
        return result;
    }

    static boolean contains(JsonObject object, String key, String value) { return strings(object, key).contains(value); }

    static void add(JsonObject object, String key, String value) {
        JsonArray array = list(object, key);
        for (JsonElement element : array) if (element.isJsonPrimitive() && element.getAsString().equals(value)) return;
        array.add(value);
        object.add(key, array);
    }

    static boolean remove(JsonObject object, String key, String value) {
        JsonArray array = list(object, key);
        boolean changed = false;
        for (int index = array.size() - 1; index >= 0; index--) if (array.get(index).isJsonPrimitive() && array.get(index).getAsString().equals(value)) {
            array.remove(index);
            changed = true;
        }
        return changed;
    }

    static JsonElement replace(JsonElement element, String old, String value) {
        if (element.isJsonPrimitive()) return element.getAsJsonPrimitive().isString() && element.getAsString().equals(old) ? new com.google.gson.JsonPrimitive(value) : element;
        if (element.isJsonArray()) {
            JsonArray result = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) result.add(replace(child, old, value));
            return result;
        }
        if (element.isJsonObject()) {
            JsonObject result = new JsonObject();
            element.getAsJsonObject().entrySet().forEach(entry -> result.add(entry.getKey(), replace(entry.getValue(), old, value)));
            return result;
        }
        return element;
    }

    static boolean mentions(JsonElement element, String value) {
        if (element.isJsonPrimitive()) return element.getAsJsonPrimitive().isString() && element.getAsString().equals(value);
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) if (mentions(child, value)) return true;
            return false;
        }
        if (element.isJsonObject()) {
            for (var entry : element.getAsJsonObject().entrySet()) if (mentions(entry.getValue(), value)) return true;
        }
        return false;
    }
}
