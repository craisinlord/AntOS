package com.craisinlord.antos.content.client.studio;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class StudioNode {
    final StudioStore store;
    final StudioKey key;
    private final List<Object> path;

    StudioNode(StudioStore store, StudioKey key) { this(store, key, List.of()); }

    private StudioNode(StudioStore store, StudioKey key, List<Object> path) {
        this.store = store;
        this.key = key;
        this.path = path;
    }

    StudioNode at(Object segment) {
        List<Object> next = new ArrayList<>(path);
        next.add(segment);
        return new StudioNode(store, key, List.copyOf(next));
    }

    String path() {
        StringBuilder result = new StringBuilder();
        for (Object segment : path) result.append(result.isEmpty() ? "" : "/").append(segment);
        return result.toString();
    }

    String field(String name) { return path.isEmpty() ? name : path() + "/" + name; }
    String id(String name) { return key + "#" + field(name); }

    private JsonElement resolve(JsonElement root, boolean create) {
        JsonElement current = root;
        for (int index = 0; index < path.size(); index++) {
            Object segment = path.get(index);
            if (segment instanceof Integer position) {
                if (current == null || !current.isJsonArray() || position >= current.getAsJsonArray().size()) return null;
                current = current.getAsJsonArray().get(position);
            } else {
                if (current == null || !current.isJsonObject()) return null;
                JsonObject object = current.getAsJsonObject();
                String name = (String) segment;
                JsonElement child = object.get(name);
                if ((child == null || child.isJsonPrimitive() || child.isJsonNull()) && create) {
                    child = index + 1 < path.size() && path.get(index + 1) instanceof Integer ? new JsonArray() : new JsonObject();
                    object.add(name, child);
                }
                current = child;
            }
        }
        return current;
    }

    JsonElement get() { return resolve(store.view(key), false); }

    JsonObject object() {
        JsonElement value = get();
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    JsonArray array() {
        JsonElement value = get();
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    boolean has(String name) { return object().has(name); }
    String string(String name) { return StudioJson.string(object(), name); }
    String string(String name, String fallback) { return StudioJson.string(object(), name, fallback); }
    double number(String name, double fallback) { return StudioJson.number(object(), name, fallback); }
    int integer(String name, int fallback) { return StudioJson.integer(object(), name, fallback); }
    boolean bool(String name, boolean fallback) { return StudioJson.bool(object(), name, fallback); }
    List<String> strings(String name) { return StudioJson.strings(object(), name); }

    void change(String tag, Consumer<JsonElement> action) {
        store.edit(key, tag.isEmpty() ? "" : path() + "/" + tag, body -> {
            JsonElement target = resolve(body, true);
            if (target != null) action.accept(target);
        });
    }

    void put(String name, JsonElement value) {
        change(name, target -> {
            if (target.isJsonObject()) target.getAsJsonObject().add(name, value);
        });
    }

    void put(String name, String value) { put(name, new JsonPrimitive(value)); }
    void put(String name, Number value) { put(name, new JsonPrimitive(value)); }
    void put(String name, boolean value) { put(name, new JsonPrimitive(value)); }

    void putOrDrop(String name, String value) {
        if (value.isEmpty()) drop(name);
        else put(name, value);
    }

    void drop(String name) {
        change("", target -> {
            if (target.isJsonObject()) target.getAsJsonObject().remove(name);
        });
    }

    void add(JsonElement value) {
        if (path.isEmpty() || !(path.get(path.size() - 1) instanceof String name)) return;
        new StudioNode(store, key, path.subList(0, path.size() - 1)).change("", target -> {
            if (!target.isJsonObject()) return;
            JsonArray list = StudioJson.list(target.getAsJsonObject(), name);
            list.add(value);
            target.getAsJsonObject().add(name, list);
        });
    }

    void set(int index, JsonElement value) {
        change("item" + index, target -> {
            if (target.isJsonArray() && index < target.getAsJsonArray().size()) target.getAsJsonArray().set(index, value);
        });
    }

    void removeAt(int index) {
        change("", target -> {
            if (target.isJsonArray() && index < target.getAsJsonArray().size()) target.getAsJsonArray().remove(index);
        });
    }

    void move(int from, int to) {
        change("", target -> {
            if (!target.isJsonArray()) return;
            JsonArray list = target.getAsJsonArray();
            if (from < 0 || to < 0 || from >= list.size() || to >= list.size()) return;
            JsonElement value = list.remove(from);
            JsonArray copy = new JsonArray();
            for (int index = 0; index <= list.size(); index++) {
                if (index == to) copy.add(value);
                if (index < list.size()) copy.add(list.get(index));
            }
            while (!list.isEmpty()) list.remove(list.size() - 1);
            copy.forEach(list::add);
        });
    }
}
