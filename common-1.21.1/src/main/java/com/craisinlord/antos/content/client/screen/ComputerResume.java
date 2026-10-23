package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.config.AntOSSettings;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Set;

final class ComputerResume {
    private static final Set<String> TYPES = Set.of("TERMINAL", "FILES", "TEXT", "PAINT", "SETTINGS", "ANTMAIL", "ANTAZON", "MERCHANT",
            "ARCHIVE", "DISKS", "TASKS", "GAMES", "WALLPAPERS");

    private ComputerResume() { }

    static String capture(ComputerScreen.Session session) {
        JsonObject root = new JsonObject();
        JsonArray windows = new JsonArray();
        for (ComputerScreen.Window window : session.windows) {
            if (!TYPES.contains(window.type)) continue;
            JsonObject row = new JsonObject();
            row.addProperty("type", window.type);
            row.addProperty("x", window.x);
            row.addProperty("y", window.y);
            row.addProperty("restore_x", window.restoreX);
            row.addProperty("restore_y", window.restoreY);
            row.addProperty("minimized", window.minimized);
            row.addProperty("maximized", window.maximized);
            windows.add(row);
        }
        root.add("windows", windows);
        JsonObject apps = new JsonObject();
        apps.add("files", session.files.saveResume());
        apps.add("text", session.text.saveResume());
        apps.add("paint", session.paint.saveResume());
        apps.add("archive", session.archive.saveResume());
        apps.add("merchant", session.merchant.saveResume());
        apps.add("antazon", session.antazon.saveResume());
        apps.add("antmail", session.antmail.saveResume());
        apps.add("settings", session.settings.saveResume());
        apps.add("tasks", TasksApp.rememberedResume());
        JsonObject games = new JsonObject();
        games.addProperty("gallery", session.gamesGallery);
        apps.add("games", games);
        root.add("apps", apps);
        return root.toString();
    }

    static void restore(ComputerScreen screen, ComputerScreen.Session session, String json) {
        if (json == null || json.isBlank()) return;
        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException exception) {
            return;
        }
        JsonObject apps = object(root, "apps");
        try {
            session.files.loadResume(object(apps, "files"));
            session.text.loadResume(object(apps, "text"));
            session.paint.loadResume(object(apps, "paint"));
            session.archive.loadResume(object(apps, "archive"));
            session.merchant.loadResume(object(apps, "merchant"));
            session.antazon.loadResume(object(apps, "antazon"));
            session.antmail.loadResume(object(apps, "antmail"));
            session.settings.loadResume(object(apps, "settings"));
            TasksApp.restoreResume(object(apps, "tasks"));
            JsonObject games = object(apps, "games");
            if (games.has("gallery")) session.gamesGallery = games.get("gallery").getAsBoolean();
        } catch (RuntimeException ignored) {
        }
        if (!root.has("windows") || !root.get("windows").isJsonArray()) return;
        session.windows.clear();
        for (JsonElement element : root.getAsJsonArray("windows")) {
            if (!element.isJsonObject()) continue;
            JsonObject row = element.getAsJsonObject();
            String type = string(row, "type");
            if (!TYPES.contains(type) || !AntOSSettings.appEnabled(type)
                    || session.windows.stream().anyMatch(window -> window.type.equals(type))) continue;
            ComputerScreen.Window window = new ComputerScreen.Window(type,
                    Component.translatable("computer.antos.app." + type.toLowerCase(Locale.ROOT) + ".window_title").getString(),
                    number(row, "x"), number(row, "y"));
            window.restoreX = number(row, "restore_x");
            window.restoreY = number(row, "restore_y");
            window.minimized = flag(row, "minimized");
            window.maximized = flag(row, "maximized");
            session.windows.add(window);
            screen.windowOpened(type);
        }
    }

    static JsonObject object(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : new JsonObject();
    }

    static String string(JsonObject parent, String key) {
        try {
            return parent.has(key) ? parent.get(key).getAsString() : "";
        } catch (RuntimeException exception) {
            return "";
        }
    }

    static int number(JsonObject parent, String key) {
        try {
            return parent.has(key) ? parent.get(key).getAsInt() : 0;
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    static boolean flag(JsonObject parent, String key) {
        try {
            return parent.has(key) && parent.get(key).getAsBoolean();
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
