package com.craisinlord.antos.config;

import com.craisinlord.antos.AntOS;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
    private static final int READ_ATTEMPTS = 5;
    private static final int CONFIG_VERSION = 2;
    private static final int STATE_VERSION = 1;
    private static final String NETWORK_KEY = "antosSettings";
    private static final List<String> PLAYER_KEYS = List.of("preferences", "lastAnternetUsername", "initialBootSeen",
            "completedComputerTours", "taskGroupCollapsed", "resumeSnapshots", "terminalHistory");
    private static volatile PackConfig pack = new PackConfig().withDefaults();
    private static volatile SharedSettings synced;
    private static ClientState state = new ClientState().withDefaults();
    private static Path statePath;
    private static boolean stateWritable;

    private AntOSSettings() {}

    public static synchronized void loadConfig(Path configFile) {
        try {
            Path parent = configFile.getParent();
            if (parent != null) Files.createDirectories(parent);
            if (!Files.exists(configFile)) {
                pack = new PackConfig().withDefaults();
                write(configFile, GSON.toJsonTree(pack));
                AntOS.LOGGER.info("Created AntOS configuration at {}", configFile);
                return;
            }
            pack = PackConfig.from(readJson(configFile));
        } catch (Exception exception) {
            AntOS.LOGGER.error("Could not load AntOS configuration from {}; using defaults for this session and leaving the file untouched",
                    configFile, exception);
            pack = new PackConfig().withDefaults();
        }
    }

    public static synchronized void loadClientState(Path stateFile, Path legacyConfigFile) {
        statePath = stateFile;
        stateWritable = false;
        try {
            Path parent = stateFile.getParent();
            if (parent != null) Files.createDirectories(parent);
            if (Files.exists(stateFile)) {
                ClientState loaded = GSON.fromJson(readJson(stateFile), ClientState.class);
                state = (loaded == null ? new ClientState() : loaded).withDefaults();
                stateWritable = true;
                return;
            }
        } catch (Exception exception) {
            AntOS.LOGGER.error("Could not load AntOS player settings from {}; using defaults for this session and leaving the file untouched",
                    stateFile, exception);
            state = new ClientState().withDefaults();
            return;
        }
        JsonObject legacy = readLegacy(legacyConfigFile);
        boolean migrating = legacy != null && PLAYER_KEYS.stream().anyMatch(legacy::has);
        state = migrating ? legacyState(legacy) : freshState();
        if (state == null) {
            migrating = false;
            state = freshState();
        }
        stateWritable = true;
        if (!saveState()) return;
        if (migrating) {
            stripLegacyConfig(legacyConfigFile, legacy);
            AntOS.LOGGER.info("Moved AntOS player settings from {} to {}", legacyConfigFile, stateFile);
        } else {
            AntOS.LOGGER.info("Created AntOS player settings at {}", stateFile);
        }
    }

    private static JsonObject readJson(Path file) throws Exception {
        for (int attempt = 1; ; attempt++) {
            try (Reader reader = Files.newBufferedReader(file)) {
                JsonElement element = JsonParser.parseReader(reader);
                return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
            } catch (java.io.IOException exception) {
                if (attempt >= READ_ATTEMPTS) throw exception;
                Thread.sleep(100L * attempt);
            }
        }
    }

    private static JsonObject readLegacy(Path legacyConfigFile) {
        if (legacyConfigFile == null || !Files.exists(legacyConfigFile)) return null;
        try {
            return readJson(legacyConfigFile);
        } catch (Exception exception) {
            AntOS.LOGGER.warn("Could not read {} to move AntOS player settings; starting with defaults", legacyConfigFile, exception);
            return null;
        }
    }

    private static ClientState legacyState(JsonObject legacy) {
        try {
            ClientState migrated = GSON.fromJson(legacy, ClientState.class);
            return migrated == null ? null : migrated.withDefaults();
        } catch (RuntimeException exception) {
            AntOS.LOGGER.warn("Could not read the AntOS player settings in the old configuration; starting with defaults", exception);
            return null;
        }
    }

    private static ClientState freshState() {
        ClientState fresh = new ClientState().withDefaults();
        fresh.preferences = pack.client.defaultPreferences();
        return fresh;
    }

    private static void stripLegacyConfig(Path configFile, JsonObject legacy) {
        JsonObject updated = new JsonObject();
        updated.addProperty("configVersion", CONFIG_VERSION);
        JsonObject client = legacy.get("client") instanceof JsonObject existing ? existing.deepCopy() : new JsonObject();
        legacy.entrySet().forEach(entry -> {
            String key = entry.getKey();
            if (key.equals("configVersion") || key.equals("client") || PLAYER_KEYS.contains(key)) return;
            if (key.equals("inventoryComputerButton")) {
                if (!client.has(key)) client.add(key, entry.getValue());
                return;
            }
            updated.add(key, entry.getValue());
        });
        if (!client.has("inventoryComputerButton")) client.addProperty("inventoryComputerButton", false);
        if (!client.has("defaultPreferences")) client.add("defaultPreferences", new JsonObject());
        updated.add("client", client);
        try {
            write(configFile, updated);
        } catch (Exception exception) {
            AntOS.LOGGER.warn("Could not remove player settings from {}; they will be ignored", configFile, exception);
        }
    }

    private static boolean saveState() {
        if (statePath == null || !stateWritable) return false;
        try {
            write(statePath, GSON.toJsonTree(state));
            return true;
        } catch (Exception exception) {
            AntOS.LOGGER.error("Could not save AntOS player settings to {}", statePath, exception);
            return false;
        }
    }

    private static void write(Path file, JsonElement json) throws java.io.IOException {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(json, writer);
            }
            try {
                Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static synchronized void encodeNetworkSettings(JsonObject root) {
        root.add(NETWORK_KEY, GSON.toJsonTree(pack.shared()));
    }

    public static void applyNetworkSettings(JsonObject root) {
        if (!(root.get(NETWORK_KEY) instanceof JsonObject json)) return;
        try {
            SharedSettings received = GSON.fromJson(json, SharedSettings.class);
            if (received != null) synced = received.withDefaults();
        } catch (RuntimeException exception) {
            AntOS.LOGGER.warn("Ignoring invalid AntOS settings from the server", exception);
        }
    }

    public static void clearNetworkSettings() {
        synced = null;
    }

    private static SharedSettings shared() {
        SharedSettings current = synced;
        return current != null ? current : pack.shared();
    }

    public static synchronized String lastAnternetUsername() {
        return state.lastAnternetUsername == null ? "" : state.lastAnternetUsername;
    }

    public static synchronized void setLastAnternetUsername(String username) {
        state.lastAnternetUsername = username == null ? "" : username.trim();
        saveState();
    }

    public static synchronized boolean consumeInitialBoot() {
        if (state.initialBootSeen || !lastAnternetUsername().isBlank()) {
            state.initialBootSeen = true;
            saveState();
            return false;
        }
        state.initialBootSeen = true;
        saveState();
        return true;
    }

    public static synchronized boolean hasCompletedComputerTour(UUID accountId) {
        return accountId != null && state.completedComputerTours.contains(accountId.toString());
    }

    public static synchronized void setComputerTourCompleted(UUID accountId, boolean completed) {
        if (accountId == null) return;
        if (completed) state.completedComputerTours.add(accountId.toString());
        else state.completedComputerTours.remove(accountId.toString());
        saveState();
    }

    public static synchronized boolean hasCompletedMerchantTour(String workspace) {
        return workspace != null && state.completedMerchantTours.contains(workspace);
    }

    public static synchronized void setMerchantTourCompleted(String workspace, boolean completed) {
        if (workspace == null || workspace.isBlank() || workspace.equals("workspace:unbound")) return;
        if (completed) state.completedMerchantTours.add(workspace);
        else state.completedMerchantTours.remove(workspace);
        saveState();
    }

    public static synchronized String resumeSnapshot(String key) {
        return key == null ? null : state.resumeSnapshots.get(key);
    }

    public static synchronized void setResumeSnapshot(String key, String snapshot) {
        if (key == null || key.isBlank()) return;
        if (snapshot == null) state.resumeSnapshots.remove(key);
        else state.resumeSnapshots.put(key, snapshot);
        saveState();
    }

    public static synchronized String editorDraft(String workspace, String app) {
        return state.editorDrafts.get(workspace + "|" + app);
    }

    public static synchronized void setEditorDraft(String workspace, String app, String draft) {
        if (workspace == null || workspace.equals("workspace:unbound")) return;
        String key = workspace + "|" + app;
        if (draft == null) state.editorDrafts.remove(key);
        else state.editorDrafts.put(key, draft);
        saveState();
    }

    public static synchronized String notificationHistory(String workspace) {
        return state.notificationHistories.get(workspace);
    }

    public static synchronized void setNotificationHistory(String workspace, String history) {
        if (workspace == null || workspace.isBlank() || workspace.equals("workspace:unbound")) return;
        state.notificationHistories.put(workspace, history);
        saveState();
    }

    public static synchronized Set<String> archiveBookmarks(String workspace) {
        Set<String> bookmarks = state.archiveBookmarks.get(workspace);
        return bookmarks == null ? Set.of() : bookmarks.stream().filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public static synchronized void toggleArchiveBookmark(String workspace, String id) {
        if (workspace == null || workspace.equals("workspace:unbound") || id == null) return;
        Set<String> bookmarks = state.archiveBookmarks.computeIfAbsent(workspace, ignored -> new HashSet<>());
        if (!bookmarks.remove(id)) bookmarks.add(id);
        saveState();
    }

    public static synchronized List<String> recentArchiveEntries(String workspace) {
        List<String> recent = state.recentArchiveEntries.get(workspace);
        return recent == null ? List.of() : recent.stream().filter(java.util.Objects::nonNull).distinct().limit(20).toList();
    }

    public static synchronized void recordArchiveEntry(String workspace, String id) {
        if (workspace == null || workspace.equals("workspace:unbound") || id == null) return;
        List<String> recent = new java.util.ArrayList<>(recentArchiveEntries(workspace));
        recent.remove(id);
        recent.addFirst(id);
        while (recent.size() > 20) recent.removeLast();
        state.recentArchiveEntries.put(workspace, recent);
        saveState();
    }

    public static synchronized void clearRecentArchiveEntries(String workspace) {
        state.recentArchiveEntries.remove(workspace);
        saveState();
    }

    public static synchronized Boolean taskGroupCollapsed(String groupId) {
        return state.taskGroupCollapsed.get(groupId);
    }

    public static synchronized void setTaskGroupCollapsed(String groupId, boolean collapsed) {
        if (groupId == null || groupId.isBlank()) return;
        state.taskGroupCollapsed.put(groupId, collapsed);
        saveState();
    }

    public static synchronized Preferences preferences() {
        return state.preferences.copy();
    }

    public static synchronized String displayBrightness() {
        return state.preferences.displayBrightness;
    }

    public static synchronized boolean reduceEffects() {
        return state.preferences.reduceEffects;
    }

    public static final int MIN_UI_SCALE = 50;
    public static final int MAX_UI_SCALE = 150;
    public static final int UI_SCALE_STEP = 5;

    public static int normalizeUiScale(int percentage) {
        int clamped = Math.max(MIN_UI_SCALE, Math.min(MAX_UI_SCALE, percentage));
        return Math.round(clamped / (float) UI_SCALE_STEP) * UI_SCALE_STEP;
    }

    private static int legacyUiScale(String size) {
        if ("large".equals(size)) return 115;
        if ("largest".equals(size)) return 130;
        return 100;
    }

    public static synchronized int uiScalePercent() {
        Integer percentage = state.preferences.uiScalePercent;
        return percentage == null ? legacyUiScale(state.preferences.uiSize) : normalizeUiScale(percentage);
    }

    public static synchronized float antosUiScale() {
        return uiScalePercent() / 100.0F;
    }

    public static synchronized void updatePreferences(java.util.function.Consumer<Preferences> change) {
        change.accept(state.preferences);
        state.preferences.normalize();
        saveState();
    }

    public static synchronized List<String> terminalHistory(String workspace) {
        if (workspace == null) return List.of();
        return List.copyOf(state.terminalHistory.getOrDefault(workspace, List.of()));
    }

    public static synchronized void setTerminalHistory(String workspace, List<String> history) {
        if (workspace == null || workspace.isBlank()) return;
        if (history == null || history.isEmpty()) state.terminalHistory.remove(workspace);
        else state.terminalHistory.put(workspace, List.copyOf(history.subList(Math.max(0, history.size() - 50), history.size())));
        saveState();
    }

    public static synchronized void clearTerminalHistory() {
        state.terminalHistory.clear();
        saveState();
    }

    public static boolean unlockAllArchiveEntries() {
        return pack.unlockAllArchiveEntries;
    }

    public static boolean unlockAllGameEntries() {
        return pack.unlockAllGameEntries;
    }

    public static boolean archiveLookupRequiresPhone() {
        return shared().archiveLookupRequiresPhone;
    }

    public static boolean showIntroductionEntry() {
        return pack.showIntroductionEntry;
    }

    public static long blockleDailyAntcoinReward() {
        return Math.max(0L, shared().gameRewards.blockle);
    }

    public static boolean gameBuiltIn(String gameId) {
        BuiltInGames games = shared().builtInGames;
        return switch (gameId) {
            case "chess" -> games.chess;
            case "minesweeper" -> games.minesweeper;
            case "cake_stack" -> games.cakeStack;
            case "fliptris" -> games.fliptris;
            case "nether_breakout" -> games.netherBreakout;
            case "flappy_ghast" -> games.flappyGhast;
            case "ore_2048" -> games.ore2048;
            case "redstone_rush" -> games.redstoneRush;
            case "piston_push" -> games.pistonPush;
            default -> false;
        };
    }

    public static long gameAntcoinReward(String mode) {
        GameRewards rewards = shared().gameRewards;
        return switch (mode) {
            case "chess_easy" -> Math.max(0L, rewards.chessEasy);
            case "chess_normal" -> Math.max(0L, rewards.chessNormal);
            case "chess_hard" -> Math.max(0L, rewards.chessHard);
            case "chess_expert" -> Math.max(0L, rewards.chessExpert);
            case "minesweeper_small" -> Math.max(0L, rewards.minesweeperSmall);
            case "minesweeper_large" -> Math.max(0L, rewards.minesweeperLarge);
            case "flappy_ghast_15" -> Math.max(0L, rewards.flappyGhast15);
            case "flappy_ghast_40" -> Math.max(0L, rewards.flappyGhast40);
            case "ore_2048_diamond" -> Math.max(0L, rewards.ore2048Diamond);
            case "ore_2048_beacon" -> Math.max(0L, rewards.ore2048Beacon);
            case "redstone_rush" -> Math.max(0L, rewards.redstoneRush);
            case "piston_push" -> Math.max(0L, rewards.pistonPush);
            case "piston_push_par" -> Math.max(0L, rewards.pistonPushPar);
            default -> -1L;
        };
    }

    public static int passwordHintAfterFailures() {
        return Math.max(0, pack.passwordHintAfterFailures);
    }

    public static boolean sendAntazonUnlockEmail() {
        return pack.sendAntazonUnlockEmail;
    }

    public static Set<String> legacyAntazonUnlockEmails() {
        Set<String> sent = pack.antazonUnlockEmailsSent;
        return sent == null ? Set.of() : Set.copyOf(sent);
    }

    public static boolean inventoryComputerButtonEnabled() {
        return pack.client.inventoryComputerButton;
    }

    public static boolean jeiAntazonPrices() {
        return pack.jei.antazonPrices;
    }

    public static boolean jeiSellValues() {
        return pack.jei.sellValues;
    }

    public static boolean jeiTaskRewards() {
        return pack.jei.taskRewards;
    }

    public static boolean appEnabled(String appId) {
        DesktopApps apps = shared().desktopApps;
        return switch (appId) {
            case "ARCHIVE" -> apps.archive;
            case "FILES" -> apps.files;
            case "SETTINGS" -> apps.settings;
            case "TERMINAL" -> apps.terminal;
            case "TEXT" -> apps.textEditor;
            case "PAINT" -> apps.paint;
            case "ANTMAIL" -> apps.antmail;
            case "ANTAZON" -> apps.antazon;
            case "MERCHANT" -> apps.merchant;
            case "GAMES" -> apps.games;
            case "DISKS" -> apps.disks;
            case "TASKS" -> apps.tasks;
            default -> true;
        };
    }

    private static final class PackConfig {
        private int configVersion = CONFIG_VERSION;
        private boolean unlockAllArchiveEntries;
        private boolean unlockAllGameEntries;
        private boolean showIntroductionEntry = true;
        private boolean archiveLookupRequiresPhone = true;
        private boolean sendAntazonUnlockEmail = true;
        private int passwordHintAfterFailures = 3;
        private BuiltInGames builtInGames = new BuiltInGames();
        private GameRewards gameRewards = new GameRewards();
        private DesktopApps desktopApps = new DesktopApps();
        private PackClient client = new PackClient();
        private JeiIntegration jei = new JeiIntegration();
        private Set<String> antazonUnlockEmailsSent;
        private transient SharedSettings shared;

        private static PackConfig from(JsonObject root) {
            PackConfig loaded = GSON.fromJson(root, PackConfig.class);
            PackConfig config = (loaded == null ? new PackConfig() : loaded).withDefaults();
            boolean rewardsHaveBlockle = root.get("gameRewards") instanceof JsonObject rewards && rewards.has("blockle");
            if (!rewardsHaveBlockle && root.has("blockleDailyAntcoinReward")) {
                config.gameRewards.blockle = root.get("blockleDailyAntcoinReward").getAsLong();
            }
            boolean clientHasButton = root.get("client") instanceof JsonObject client && client.has("inventoryComputerButton");
            if (!clientHasButton && root.has("inventoryComputerButton")) {
                config.client.inventoryComputerButton = root.get("inventoryComputerButton").getAsBoolean();
            }
            return config;
        }

        private PackConfig withDefaults() {
            if (builtInGames == null) builtInGames = new BuiltInGames();
            if (gameRewards == null) gameRewards = new GameRewards();
            if (desktopApps == null) desktopApps = new DesktopApps();
            if (client == null) client = new PackClient();
            if (jei == null) jei = new JeiIntegration();
            if (client.defaultPreferences == null) client.defaultPreferences = new JsonObject();
            shared = SharedSettings.of(this);
            return this;
        }

        private SharedSettings shared() {
            return shared;
        }
    }

    private static final class JeiIntegration {
        private boolean antazonPrices = true;
        private boolean sellValues = true;
        private boolean taskRewards = true;
    }

    private static final class PackClient {
        private boolean inventoryComputerButton;
        private JsonObject defaultPreferences = new JsonObject();

        private Preferences defaultPreferences() {
            Preferences preferences;
            try {
                preferences = defaultPreferences == null ? null : GSON.fromJson(defaultPreferences, Preferences.class);
            } catch (RuntimeException exception) {
                AntOS.LOGGER.warn("Ignoring invalid client.defaultPreferences in the AntOS configuration", exception);
                preferences = null;
            }
            if (preferences == null) preferences = new Preferences();
            preferences.normalize();
            return preferences;
        }
    }

    private static final class SharedSettings {
        private boolean archiveLookupRequiresPhone = true;
        private BuiltInGames builtInGames = new BuiltInGames();
        private GameRewards gameRewards = new GameRewards();
        private DesktopApps desktopApps = new DesktopApps();

        private static SharedSettings of(PackConfig config) {
            SharedSettings settings = new SharedSettings();
            settings.archiveLookupRequiresPhone = config.archiveLookupRequiresPhone;
            settings.builtInGames = config.builtInGames;
            settings.gameRewards = config.gameRewards;
            settings.desktopApps = config.desktopApps;
            return settings;
        }

        private SharedSettings withDefaults() {
            if (builtInGames == null) builtInGames = new BuiltInGames();
            if (gameRewards == null) gameRewards = new GameRewards();
            if (desktopApps == null) desktopApps = new DesktopApps();
            return this;
        }
    }

    private static final class ClientState {
        private int stateVersion = STATE_VERSION;
        private Preferences preferences = new Preferences();
        private String lastAnternetUsername = "";
        private boolean initialBootSeen;
        private Set<String> completedComputerTours = new HashSet<>();
        private Set<String> completedMerchantTours = new HashSet<>();
        private Map<String, Boolean> taskGroupCollapsed = new HashMap<>();
        private Map<String, String> resumeSnapshots = new HashMap<>();
        private Map<String, String> notificationHistories = new HashMap<>();
        private Map<String, String> editorDrafts = new HashMap<>();
        private Map<String, Set<String>> archiveBookmarks = new HashMap<>();
        private Map<String, List<String>> recentArchiveEntries = new HashMap<>();
        private Map<String, List<String>> terminalHistory = new HashMap<>();

        private ClientState withDefaults() {
            stateVersion = STATE_VERSION;
            if (preferences == null) preferences = new Preferences();
            preferences.normalize();
            if (lastAnternetUsername == null) lastAnternetUsername = "";
            if (completedComputerTours == null) completedComputerTours = new HashSet<>();
            if (completedMerchantTours == null) completedMerchantTours = new HashSet<>();
            if (taskGroupCollapsed == null) taskGroupCollapsed = new HashMap<>();
            if (resumeSnapshots == null) resumeSnapshots = new HashMap<>();
            if (notificationHistories == null) notificationHistories = new HashMap<>();
            if (editorDrafts == null) editorDrafts = new HashMap<>();
            if (archiveBookmarks == null) archiveBookmarks = new HashMap<>();
            if (recentArchiveEntries == null) recentArchiveEntries = new HashMap<>();
            if (terminalHistory == null) terminalHistory = new HashMap<>();
            return this;
        }
    }

    public static final class Preferences {
        public static final List<String> TASK_MAP_ZOOMS = List.of("55", "65", "75", "85", "100");
        public static final List<String> TASK_TRACKER_POSITIONS = List.of("right", "left", "top_right", "top_left");
        public static final List<String> TASK_TRACKER_OPACITIES = List.of("0", "25", "50", "75");

        public String theme = "green";
        public String displayBrightness = "standard";
        public boolean reduceEffects;
        public boolean resumeSession = true;
        public String displayLook = "terminal";
        public String iconColor = "accent";
        public String wallpaperColors = "match";
        public String customColors = "";
        public boolean lookChosen;
        public boolean pauseWhileOpen;
        public boolean fullscreenDesktop;
        public boolean quietNotifications;
        public boolean interfaceSounds = true;
        public int interfaceVolume = 60;
        private String screenPalette;
        private Boolean softText;
        public String uiSize = "standard";
        public Integer uiScalePercent;
        public String mailAlerts = "toast";
        public boolean mailSound = true;
        public String taskAlerts = "actionbar";
        public boolean taskSound = true;
        public String taskMapZoom = "75";
        public String taskSidebarBehavior = "click";
        public String taskSidebarWidth = "narrow";
        public String taskSidebarText = "small";
        public String taskSidebarSpacing = "compact";
        public String archiveText = "small";
        public boolean taskSidebarCounts = true;
        public boolean taskTracker = true;
        public String taskTrackerPosition = "right";
        public String taskTrackerStyle = "detailed";
        public String taskTrackerOpacity = "50";
        public boolean taskAutoUnpin = true;
        public String editorLineNumbers = "auto";
        public boolean paintGrid = true;
        public int paintBrush = 1;
        public boolean terminalKeepHistory = true;
        public boolean explorerPlaces = true;
        public boolean explorerSortBySize;

        private Preferences copy() {
            Preferences copy = new Preferences();
            copy.theme = theme;
            copy.displayBrightness = displayBrightness;
            copy.reduceEffects = reduceEffects;
            copy.resumeSession = resumeSession;
            copy.displayLook = displayLook;
            copy.iconColor = iconColor;
            copy.wallpaperColors = wallpaperColors;
            copy.customColors = customColors;
            copy.lookChosen = lookChosen;
            copy.pauseWhileOpen = pauseWhileOpen;
            copy.fullscreenDesktop = fullscreenDesktop;
            copy.quietNotifications = quietNotifications;
            copy.interfaceSounds = interfaceSounds;
            copy.interfaceVolume = interfaceVolume;
            copy.uiSize = uiSize;
            copy.uiScalePercent = uiScalePercent;
            copy.mailAlerts = mailAlerts;
            copy.mailSound = mailSound;
            copy.taskAlerts = taskAlerts;
            copy.taskSound = taskSound;
            copy.taskMapZoom = taskMapZoom;
            copy.taskSidebarBehavior = taskSidebarBehavior;
            copy.taskSidebarWidth = taskSidebarWidth;
            copy.taskSidebarText = taskSidebarText;
            copy.taskSidebarSpacing = taskSidebarSpacing;
            copy.archiveText = archiveText;
            copy.taskSidebarCounts = taskSidebarCounts;
            copy.taskTracker = taskTracker;
            copy.taskTrackerPosition = taskTrackerPosition;
            copy.taskTrackerStyle = taskTrackerStyle;
            copy.taskTrackerOpacity = taskTrackerOpacity;
            copy.taskAutoUnpin = taskAutoUnpin;
            copy.editorLineNumbers = editorLineNumbers;
            copy.paintGrid = paintGrid;
            copy.paintBrush = paintBrush;
            copy.terminalKeepHistory = terminalKeepHistory;
            copy.explorerPlaces = explorerPlaces;
            copy.explorerSortBySize = explorerSortBySize;
            return copy;
        }

        private void normalize() {
            if (theme == null || !List.of("green", "amber", "blue", "white", "lavender", "rainbow").contains(theme)) theme = "green";
            if (screenPalette != null || softText != null) {
                displayBrightness = "dim".equals(screenPalette) || "light".equals(screenPalette) ? "dim" : Boolean.TRUE.equals(softText) ? "soft" : "standard";
                screenPalette = null;
                softText = null;
            }
            if (displayBrightness == null || !List.of("standard", "soft", "dim").contains(displayBrightness)) displayBrightness = "standard";
            if (displayLook == null || !List.of("terminal", "graphite", "custom").contains(displayLook)) displayLook = "terminal";
            if (iconColor == null || !List.of("accent", "neutral").contains(iconColor)) iconColor = "accent";
            if (wallpaperColors == null || !List.of("match", "original").contains(wallpaperColors)) wallpaperColors = "match";
            if (customColors == null) customColors = "";
            if (uiSize == null || !List.of("standard", "large", "largest").contains(uiSize)) uiSize = "standard";
            uiScalePercent = uiScalePercent == null ? legacyUiScale(uiSize) : normalizeUiScale(uiScalePercent);
            if (mailAlerts == null || !List.of("toast", "actionbar", "both", "off").contains(mailAlerts)) mailAlerts = "toast";
            if (taskAlerts == null || !List.of("toast", "actionbar", "both", "off").contains(taskAlerts)) taskAlerts = "actionbar";
            if (taskMapZoom == null || !TASK_MAP_ZOOMS.contains(taskMapZoom)) taskMapZoom = "75";
            if (taskSidebarBehavior == null || !List.of("click", "hover", "always").contains(taskSidebarBehavior)) taskSidebarBehavior = "click";
            if (taskSidebarWidth == null || !List.of("narrow", "standard", "wide").contains(taskSidebarWidth)) taskSidebarWidth = "narrow";
            if (taskSidebarText == null || !List.of("small", "normal", "large").contains(taskSidebarText)) taskSidebarText = "small";
            if (taskSidebarSpacing == null || !List.of("compact", "comfortable").contains(taskSidebarSpacing)) taskSidebarSpacing = "compact";
            if (taskTrackerPosition == null || !TASK_TRACKER_POSITIONS.contains(taskTrackerPosition)) taskTrackerPosition = "right";
            if (taskTrackerStyle == null || !List.of("detailed", "compact").contains(taskTrackerStyle)) taskTrackerStyle = "detailed";
            if (taskTrackerOpacity == null || !TASK_TRACKER_OPACITIES.contains(taskTrackerOpacity)) taskTrackerOpacity = "50";
            if (archiveText == null || !List.of("small", "normal", "large").contains(archiveText)) archiveText = "small";
            if (editorLineNumbers == null || !List.of("auto", "on", "off").contains(editorLineNumbers)) editorLineNumbers = "auto";
            paintBrush = Math.max(1, Math.min(3, paintBrush));
            interfaceVolume = Math.max(0, Math.min(100, interfaceVolume));
        }
    }

    private static final class BuiltInGames {
        private boolean chess = true;
        private boolean minesweeper = true;
        private boolean cakeStack = true;
        private boolean fliptris = true;
        private boolean netherBreakout = true;
        private boolean flappyGhast = true;
        private boolean ore2048 = true;
        private boolean redstoneRush = true;
        private boolean pistonPush = true;
    }

    private static final class GameRewards {
        private long chessEasy = 5L;
        private long chessNormal = 10L;
        private long chessHard = 20L;
        private long chessExpert = 40L;
        private long minesweeperSmall = 5L;
        private long minesweeperLarge = 15L;
        private long blockle = 10L;
        private long flappyGhast15 = 5L;
        private long flappyGhast40 = 15L;
        private long ore2048Diamond = 5L;
        private long ore2048Beacon = 25L;
        private long redstoneRush = 10L;
        private long pistonPush = 5L;
        private long pistonPushPar = 15L;
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
        private boolean merchant = false;
        private boolean games = true;
        private boolean disks = true;
        private boolean tasks;
    }
}
