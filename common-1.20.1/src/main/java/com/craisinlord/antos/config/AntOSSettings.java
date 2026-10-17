package com.craisinlord.antos.config;

import com.craisinlord.antos.AntOS;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    public static synchronized Boolean taskGroupCollapsed(String groupId) {
        return config.taskGroupCollapsed == null ? null : config.taskGroupCollapsed.get(groupId);
    }

    public static synchronized void setTaskGroupCollapsed(String groupId, boolean collapsed) {
        if (groupId == null || groupId.isBlank()) return;
        if (config.taskGroupCollapsed == null) config.taskGroupCollapsed = new HashMap<>();
        config.taskGroupCollapsed.put(groupId, collapsed);
        save();
    }

    public static synchronized Preferences preferences() {
        if (config.preferences == null) config.preferences = new Preferences();
        return config.preferences.copy();
    }

    public static synchronized void updatePreferences(java.util.function.Consumer<Preferences> change) {
        if (config.preferences == null) config.preferences = new Preferences();
        change.accept(config.preferences);
        config.preferences.normalize();
        save();
    }

    public static synchronized List<String> terminalHistory(String workspace) {
        if (config.terminalHistory == null || workspace == null) return List.of();
        return List.copyOf(config.terminalHistory.getOrDefault(workspace, List.of()));
    }

    public static synchronized void setTerminalHistory(String workspace, List<String> history) {
        if (workspace == null || workspace.isBlank()) return;
        if (config.terminalHistory == null) config.terminalHistory = new HashMap<>();
        if (history == null || history.isEmpty()) config.terminalHistory.remove(workspace);
        else config.terminalHistory.put(workspace, List.copyOf(history.subList(Math.max(0, history.size() - 50), history.size())));
        save();
    }

    public static synchronized void clearTerminalHistory() {
        if (config.terminalHistory != null) config.terminalHistory.clear();
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

    public static boolean showIntroductionEntry() {
        return config.showIntroductionEntry;
    }

    public static synchronized long blockleDailyAntcoinReward() {
        return Math.max(0L, config.blockleDailyAntcoinReward);
    }

    public static synchronized boolean gameBuiltIn(String gameId) {
        BuiltInGames games = config.builtInGames;
        return switch (gameId) {
            case "chess" -> games.chess;
            case "minesweeper" -> games.minesweeper;
            default -> false;
        };
    }

    public static synchronized long gameAntcoinReward(String mode) {
        GameRewards rewards = config.gameRewards;
        return switch (mode) {
            case "chess_easy" -> Math.max(0L, rewards.chessEasy);
            case "chess_normal" -> Math.max(0L, rewards.chessNormal);
            case "chess_hard" -> Math.max(0L, rewards.chessHard);
            case "chess_expert" -> Math.max(0L, rewards.chessExpert);
            case "minesweeper_small" -> Math.max(0L, rewards.minesweeperSmall);
            case "minesweeper_large" -> Math.max(0L, rewards.minesweeperLarge);
            default -> -1L;
        };
    }

    public static synchronized boolean sendAntazonUnlockEmail() {
        return config.sendAntazonUnlockEmail;
    }

    public static synchronized Set<String> legacyAntazonUnlockEmails() {
        return config.antazonUnlockEmailsSent == null ? Set.of() : Set.copyOf(config.antazonUnlockEmailsSent);
    }

    public static boolean inventoryComputerButtonEnabled() {
        return config.inventoryComputerButton;
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
            case "DISKS" -> apps.disks;
            case "TASKS" -> apps.tasks;
            default -> true;
        };
    }

    private static final class Config {
        private boolean unlockAllArchiveEntries;
        private boolean unlockAllGameEntries;
        private boolean showIntroductionEntry = true;
        private long blockleDailyAntcoinReward = 10L;
        private BuiltInGames builtInGames = new BuiltInGames();
        private GameRewards gameRewards = new GameRewards();
        private boolean sendAntazonUnlockEmail = true;
        private Set<String> antazonUnlockEmailsSent = new HashSet<>();
        private boolean inventoryComputerButton;
        private DesktopApps desktopApps = new DesktopApps();
        private String lastAnternetUsername = "";
        private boolean initialBootSeen;
        private Set<String> completedComputerTours = new HashSet<>();
        private Map<String, Boolean> taskGroupCollapsed = new HashMap<>();
        private Preferences preferences = new Preferences();
        private Map<String, List<String>> terminalHistory = new HashMap<>();

        private Config withDefaults() {
            if (preferences == null) preferences = new Preferences();
            preferences.normalize();
            if (terminalHistory == null) terminalHistory = new HashMap<>();
            if (desktopApps == null) desktopApps = new DesktopApps();
            if (builtInGames == null) builtInGames = new BuiltInGames();
            if (gameRewards == null) gameRewards = new GameRewards();
            if (completedComputerTours == null) completedComputerTours = new HashSet<>();
            if (taskGroupCollapsed == null) taskGroupCollapsed = new HashMap<>();
            if (antazonUnlockEmailsSent == null) antazonUnlockEmailsSent = new HashSet<>();
            return this;
        }
    }

    public static final class Preferences {
        public static final List<String> TASK_MAP_ZOOMS = List.of("55", "65", "75", "85", "100");

        public String theme = "green";
        public String mailAlerts = "toast";
        public boolean mailSound = true;
        public String taskAlerts = "actionbar";
        public boolean taskSound = true;
        public String taskMapZoom = "75";
        public String editorLineNumbers = "auto";
        public boolean paintGrid = true;
        public int paintBrush = 1;
        public boolean terminalKeepHistory = true;
        public boolean explorerPlaces = true;
        public boolean explorerSortBySize;

        private Preferences copy() {
            Preferences copy = new Preferences();
            copy.theme = theme;
            copy.mailAlerts = mailAlerts;
            copy.mailSound = mailSound;
            copy.taskAlerts = taskAlerts;
            copy.taskSound = taskSound;
            copy.taskMapZoom = taskMapZoom;
            copy.editorLineNumbers = editorLineNumbers;
            copy.paintGrid = paintGrid;
            copy.paintBrush = paintBrush;
            copy.terminalKeepHistory = terminalKeepHistory;
            copy.explorerPlaces = explorerPlaces;
            copy.explorerSortBySize = explorerSortBySize;
            return copy;
        }

        private void normalize() {
            if (theme == null || !List.of("green", "amber", "blue", "white").contains(theme)) theme = "green";
            if (mailAlerts == null || !List.of("toast", "actionbar", "both", "off").contains(mailAlerts)) mailAlerts = "toast";
            if (taskAlerts == null || !List.of("toast", "actionbar", "both", "off").contains(taskAlerts)) taskAlerts = "actionbar";
            if (taskMapZoom == null || !TASK_MAP_ZOOMS.contains(taskMapZoom)) taskMapZoom = "75";
            if (editorLineNumbers == null || !List.of("auto", "on", "off").contains(editorLineNumbers)) editorLineNumbers = "auto";
            paintBrush = Math.max(1, Math.min(3, paintBrush));
        }
    }

    private static final class BuiltInGames {
        private boolean chess = true;
        private boolean minesweeper = true;
    }

    private static final class GameRewards {
        private long chessEasy = 5L;
        private long chessNormal = 10L;
        private long chessHard = 20L;
        private long chessExpert = 40L;
        private long minesweeperSmall = 5L;
        private long minesweeperLarge = 15L;
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
        private boolean disks = true;
        private boolean tasks;
    }
}
