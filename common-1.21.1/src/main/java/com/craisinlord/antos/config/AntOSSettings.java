package com.craisinlord.antos.config;

import com.craisinlord.antos.AntOS;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class AntOSSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile Config config = new Config();
    private static Path configPath;

    private AntOSSettings() {}

    public static synchronized void load(Path configFile) {
        configPath = configFile;
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
            try (Writer writer = Files.newBufferedWriter(configFile)) {
                GSON.toJson(config, writer);
            }
        } catch (Exception exception) {
            AntOS.LOGGER.error("Could not load AntOS configuration from {}; using defaults", configFile, exception);
            config = new Config();
        }
    }

    public static synchronized String lastAnternetUsername() {
        return config.lastAnternetUsername == null ? "" : config.lastAnternetUsername;
    }

    public static synchronized void setLastAnternetUsername(String username) {
        config.lastAnternetUsername = username == null ? "" : username.trim();
        if (configPath == null) return;
        try (Writer writer = Files.newBufferedWriter(configPath)) {
            GSON.toJson(config, writer);
        } catch (Exception exception) {
            AntOS.LOGGER.error("Could not save AntOS configuration to {}", configPath, exception);
        }
    }

    public static synchronized boolean consumeInitialBoot() {
        if (config.initialBootSeen || !lastAnternetUsername().isBlank()) {
            config.initialBootSeen = true;
            save();
            return false;
        }
        config.initialBootSeen = true;
        save();
        return true;
    }

    public static synchronized boolean hasCompletedComputerTour(UUID accountId) {
        return accountId != null && config.completedComputerTours != null
                && config.completedComputerTours.contains(accountId.toString());
    }

    public static synchronized void setComputerTourCompleted(UUID accountId, boolean completed) {
        if (accountId == null) return;
        if (config.completedComputerTours == null) config.completedComputerTours = new HashSet<>();
        if (completed) config.completedComputerTours.add(accountId.toString());
        else config.completedComputerTours.remove(accountId.toString());
        save();
    }

    private static void save() {
        if (configPath == null) return;
        try (Writer writer = Files.newBufferedWriter(configPath)) {
            GSON.toJson(config, writer);
        } catch (Exception exception) {
            AntOS.LOGGER.error("Could not save AntOS configuration to {}", configPath, exception);
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
            case "ANTAZON" -> apps.antazon;
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
        private String lastAnternetUsername = "";
        private boolean initialBootSeen;
        private Set<String> completedComputerTours = new HashSet<>();

        private Config withDefaults() {
            if (desktopApps == null) desktopApps = new DesktopApps();
            if (completedComputerTours == null) completedComputerTours = new HashSet<>();
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
        private boolean antazon;
        private boolean games = true;
        private boolean trash = true;
        private boolean tasks;
    }
}
