package com.craisinlord.antos.config;

import com.craisinlord.antos.AntOS;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AntOSSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile Config config = new Config();

    private AntOSSettings() {}

    public static synchronized void load(Path configFile) {
        try {
            Path parent = configFile.getParent();
            if (parent != null) Files.createDirectories(parent);
            if (!Files.exists(configFile)) {
                try (Writer writer = Files.newBufferedWriter(configFile)) {
                    GSON.toJson(new Config(), writer);
                }
                config = new Config();
                AntOS.LOGGER.info("Created AntOS configuration at {}", configFile);
                return;
            }
            try (Reader reader = Files.newBufferedReader(configFile)) {
                Config loaded = GSON.fromJson(reader, Config.class);
                config = loaded == null ? new Config() : loaded.withDefaults();
            }
        } catch (Exception exception) {
            AntOS.LOGGER.error("Could not load AntOS configuration from {}; using defaults", configFile, exception);
            config = new Config();
        }
    }

    public static boolean unlockAllArchiveEntries() {
        return config.unlockAllArchiveEntries;
    }

    public static boolean unlockAllGameEntries() {
        return config.unlockAllGameEntries;
    }

    public static boolean appEnabled(String appId) {
        DesktopApps apps = config.desktopApps;
        return switch (appId) {
            case "ARCHIVE" -> apps.archive;
            case "FILES" -> apps.files;
            case "SETTINGS" -> apps.settings;
            case "TERMINAL" -> apps.terminal;
            case "TEXT" -> apps.textEditor;
            case "PAINT" -> apps.paint;
            case "ANTMAIL" -> apps.antmail;
            case "GAMES" -> apps.games;
            case "TRASH" -> apps.trash;
            case "TASKS" -> apps.tasks;
            default -> true;
        };
    }

    private static final class Config {
        private boolean unlockAllArchiveEntries;
        private boolean unlockAllGameEntries;
        private DesktopApps desktopApps = new DesktopApps();

        private Config withDefaults() {
            if (desktopApps == null) desktopApps = new DesktopApps();
            return this;
        }
    }

    private static final class DesktopApps {
        private boolean archive = true;
        private boolean files = true;
        private boolean settings = true;
        private boolean terminal = true;
        private boolean textEditor = true;
        private boolean paint = true;
        private boolean antmail = true;
        private boolean games = true;
        private boolean trash = true;
        private boolean tasks = true;
    }
}
