package com.craisinlord.antos.content.client.studio;

import com.craisinlord.antos.content.editor.AuthoringKind;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

final class StudioRefs {
    record Choice(String value, String label, StudioIcon icon) { }

    private final StudioStore store;
    private final Map<String, List<String>> lists = new HashMap<>();
    private final Set<String> requested = new HashSet<>();
    private final Map<String, List<Choice>> cached = new HashMap<>();
    private long cachedRevision = -1;
    private int version;

    StudioRefs(StudioStore store) { this.store = store; }

    void forget() {
        lists.clear();
        requested.clear();
        cached.clear();
        version++;
    }

    boolean waiting(String type) { return requested.contains(type) && !lists.containsKey(type); }
    int version() { return version; }

    List<String> list(String type) {
        List<String> known = lists.get(type);
        if (known != null) return known;
        if (requested.add(type)) {
            JsonObject request = new JsonObject();
            request.addProperty("type", type);
            int asked = version;
            store.send("references", request, response -> {
                if (asked != version && !requested.contains(type)) return;
                List<String> values = new ArrayList<>();
                if (StudioStore.ok(response)) for (JsonElement value : StudioJson.list(response, "ids")) values.add(value.getAsString());
                else requested.remove(type);
                lists.put(type, List.copyOf(values));
                cached.clear();
                version++;
            });
        }
        return List.of();
    }

    List<Choice> choices(String type) {
        if (cachedRevision != store.revision()) {
            cached.keySet().removeIf(key -> key.startsWith("kind:") || key.equals("sender") || key.equals("shop_category") || key.equals("shop_tag") || key.equals("article_category"));
            cachedRevision = store.revision();
        }
        List<Choice> known = cached.get(type);
        if (known != null) return known;
        List<Choice> result = build(type);
        cached.put(type, result);
        return result;
    }

    private List<Choice> build(String type) {
        List<Choice> result = new ArrayList<>();
        if (type.startsWith("kind:")) {
            AuthoringKind kind = AuthoringKind.parse(type.substring(5));
            for (StudioKey key : store.keys(kind)) result.add(new Choice(key.id(), store.label(key), StudioIcons.of(store, key)));
            return List.copyOf(result);
        }
        switch (type) {
            case "effect" -> BuiltInRegistries.MOB_EFFECT.keySet().stream().sorted().forEach(id -> result.add(new Choice(id.toString(), StudioText.effectName(id.toString()), null)));
            case "potion" -> BuiltInRegistries.POTION.keySet().stream().sorted().forEach(id -> result.add(new Choice(id.toString(), StudioText.prettyId(id.toString()), null)));
            case "game" -> list(type).forEach(id -> result.add(new Choice(id, StudioText.gameName(id), StudioIcon.pixel("desktop/games"))));
            case "sender" -> {
                Set<String> names = new java.util.LinkedHashSet<>();
                for (StudioKey key : store.keys(AuthoringKind.MESSAGE)) {
                    JsonObject body = store.view(key);
                    String sender = StudioJson.string(body, "sender");
                    if (!sender.isBlank() && names.add(sender)) {
                        String shown = StudioJson.string(body, "sender_display");
                        result.add(new Choice(sender, shown.isBlank() ? sender : shown, null));
                    }
                }
            }
            case "shop_category", "article_category", "shop_tag" -> {
                Set<String> names = new java.util.TreeSet<>();
                for (StudioKey key : store.keys(type.equals("article_category") ? AuthoringKind.ARCHIVE_ENTRY : AuthoringKind.PRODUCT)) {
                    JsonObject body = store.view(key);
                    if (type.equals("shop_tag")) names.addAll(StudioJson.strings(body, "tags"));
                    else if (!StudioJson.string(body, "category").isBlank()) names.add(StudioJson.string(body, "category"));
                }
                names.forEach(name -> result.add(new Choice(name, StudioText.pretty(name), null)));
            }
            case "item_tag" -> BuiltInRegistries.ITEM.getTags().map(tag -> tag.key().location().toString()).sorted()
                    .forEach(id -> result.add(new Choice(id, StudioText.prettyId(id), null)));
            case "texture" -> {
                Set<String> textures = new java.util.LinkedHashSet<>();
                textures.add("antos:item/floppy_disk/floppy_disk");
                for (StudioKey key : store.keys(AuthoringKind.DISK_CATEGORY)) textures.add(StudioJson.string(store.view(key), "texture", "antos:item/floppy_disk/floppy_disk"));
                net.minecraft.client.Minecraft.getInstance().getResourceManager().listResources("textures/item/floppy_disk", path -> path.getPath().endsWith(".png")).keySet().stream().sorted()
                        .forEach(id -> textures.add(id.getNamespace() + ":" + id.getPath().substring("textures/".length(), id.getPath().length() - 4)));
                textures.forEach(id -> result.add(new Choice(id, StudioText.prettyId(id), StudioIcon.texture(id))));
            }
            default -> list(type).forEach(id -> result.add(new Choice(id, StudioText.prettyId(id), type.equals("recipe") ? recipeIcon(id) : null)));
        }
        return List.copyOf(result);
    }

    private static StudioIcon recipeIcon(String id) {
        if (Identifier.tryParse(id) == null) return null;
        return (kit, x, y, size) -> {
            var stack = kit.assets.recipeResult(id);
            if (!stack.isEmpty()) StudioIcon.drawStack(kit, stack, x, y, size);
        };
    }
}
