package com.craisinlord.antos.content.client.studio;

import com.craisinlord.antos.config.AntOSSettings;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;

final class StudioPrefs {
    boolean advanced;
    boolean matchAntOS;
    boolean preview = true;
    boolean groupDisks;
    int zoom;
    int listWidth = 126;
    int previewWidth = 165;
    String category = "FLOPPIES";
    String tasksView = "map";
    private final List<String> items = new ArrayList<>();

    static StudioPrefs load() {
        StudioPrefs prefs = new StudioPrefs();
        try {
            String stored = AntOSSettings.editorDraft("studio", "preferences");
            if (stored == null || stored.isBlank()) return prefs;
            JsonObject object = JsonParser.parseString(stored).getAsJsonObject();
            prefs.advanced = StudioJson.bool(object, "advanced", false);
            prefs.matchAntOS = StudioJson.bool(object, "match_antos", false);
            prefs.preview = StudioJson.bool(object, "preview", true);
            prefs.groupDisks = StudioJson.bool(object, "group_disks", false);
            prefs.zoom = Math.max(-3, Math.min(3, StudioJson.integer(object, "zoom", 0)));
            prefs.listWidth = Math.max(80, Math.min(260, StudioJson.integer(object, "list_width", 126)));
            prefs.previewWidth = Math.max(120, Math.min(320, StudioJson.integer(object, "preview_width", 165)));
            prefs.category = StudioJson.string(object, "category", "FLOPPIES");
            prefs.tasksView = StudioJson.string(object, "tasks_view", "map");
            for (JsonElement value : StudioJson.list(object, "recent_items")) prefs.items.add(value.getAsString());
        } catch (RuntimeException exception) {
            com.craisinlord.antos.AntOS.LOGGER.warn("Could not read AntStudio preferences", exception);
        }
        return prefs;
    }

    void save() {
        JsonObject object = new JsonObject();
        object.addProperty("advanced", advanced);
        object.addProperty("match_antos", matchAntOS);
        object.addProperty("preview", preview);
        object.addProperty("group_disks", groupDisks);
        object.addProperty("zoom", zoom);
        object.addProperty("list_width", listWidth);
        object.addProperty("preview_width", previewWidth);
        object.addProperty("category", category);
        object.addProperty("tasks_view", tasksView);
        JsonArray recent = new JsonArray();
        items.forEach(recent::add);
        object.add("recent_items", recent);
        AntOSSettings.setEditorDraft("studio", "preferences", object.toString());
    }

    List<String> recentItems() { return items; }

    void recentItem(String id) {
        items.remove(id);
        items.add(0, id);
        while (items.size() > 40) items.remove(items.size() - 1);
    }
}
