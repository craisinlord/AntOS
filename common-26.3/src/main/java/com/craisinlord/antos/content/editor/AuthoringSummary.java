package com.craisinlord.antos.content.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;

public final class AuthoringSummary {
    private static final List<String> TEXT = List.of("title", "name", "label", "subject", "command", "subtitle");
    private static final List<String> IDENTITY = List.of("type", "icon", "icon_entity", "icon_item", "cover_item", "cover_entity", "item", "entity", "texture", "color",
            "category", "folder", "parent", "group", "sort_order", "sender", "sender_display", "sender_avatar_item", "sender_avatar_entity", "once", "availability",
            "unlocked_by_default", "hidden", "enabled", "value", "subscription_only", "chance_per_day", "green_tint", "render_mob_from_spawn_egg", "wallpaper");
    private static final List<String> LINKS = List.of("requires", "requires_any", "connects", "entries", "tasks", "programs", "subscribe", "unlock_tasks", "unlock_disks",
            "unlock_commands", "archive_entries", "items", "tags", "trigger", "conditions", "position", "shape", "size", "icon_scale", "line_axis", "line_bend",
            "link_category", "link_group", "thumbnail", "consumable", "hide_until_dependencies_complete", "invisible_until_completed");
    private static final List<String> PARTIAL = List.of("objectives", "rewards", "payments");

    private AuthoringSummary() { }

    public static JsonObject of(AuthoringKind kind, JsonObject body) {
        JsonObject result = new JsonObject();
        for (String key : TEXT) if (body.has(key) && body.get(key).isJsonPrimitive()) result.addProperty(key, cut(body.get(key).getAsString(), 256));
        for (String key : IDENTITY) if (body.has(key) && body.get(key).isJsonPrimitive()) result.add(key, body.get(key));
        for (String key : LINKS) {
            if (kind == AuthoringKind.REWARD_POOL && key.equals("entries")) continue;
            JsonElement value = body.get(key);
            if (value == null) continue;
            if (value.toString().length() <= 24_000) result.add(key, value.deepCopy());
            else result.addProperty("shortened", true);
        }
        for (String key : PARTIAL) if (body.has(key) && body.get(key).isJsonArray()) {
            JsonArray rows = new JsonArray();
            for (JsonElement row : body.getAsJsonArray(key)) {
                if (rows.size() >= 3 || row.toString().length() > 600) break;
                rows.add(row.deepCopy());
            }
            result.add(key, rows);
            if (rows.size() != body.getAsJsonArray(key).size()) result.addProperty(key + "_count", body.getAsJsonArray(key).size());
        }
        return result;
    }

    private static String cut(String value, int length) { return value.length() <= length ? value : value.substring(0, length); }
}
