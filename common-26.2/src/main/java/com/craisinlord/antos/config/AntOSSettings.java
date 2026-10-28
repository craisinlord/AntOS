package com.craisinlord.antos.config;

import com.craisinlord.antos.AntOS;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    private static final String LEGACY_MAIL_KEY = "antazonUnlockEmailsSent";
    private static final Set<String> MAP_SECTIONS = Set.of("builtInGames", "gameRewards");
    private static final String DEFAULT_PREFERENCES_PATH = "client.defaultPreferences";
    private static final List<String> LEGACY_PREFERENCES = List.of("uiSize", "screenPalette", "softText");
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
            JsonObject root = readJson(configFile);
            JsonObject migrated = migrateLegacyKeys(root);
            JsonObject defaults = GSON.toJsonTree(new PackConfig().withDefaults()).getAsJsonObject();
            List<String> problems = new ArrayList<>();
            pack = PackConfig.from(sanitize(migrated, defaults, "", problems));
            problems.forEach(problem -> AntOS.LOGGER.warn("{}: {}", configFile.getFileName(), problem));
            List<String> added = new ArrayList<>();
            JsonObject completed = complete(migrated, defaults, "", added);
            if (!completed.equals(root)) updateConfig(configFile, completed, added);
        } catch (Exception exception) {
            AntOS.LOGGER.error("Could not load AntOS configuration from {}; using defaults for this session and leaving the file untouched",
                    configFile, exception);
            pack = new PackConfig().withDefaults();
        }
    }

    private static void updateConfig(Path configFile, JsonObject completed, List<String> added) {
        try {
            write(configFile, completed);
            if (added.isEmpty()) AntOS.LOGGER.info("Moved old AntOS settings in {} to their current names", configFile);
            else AntOS.LOGGER.info("Added new AntOS settings to {}: {}", configFile, String.join(", ", added));
        } catch (Exception exception) {
            AntOS.LOGGER.warn("Could not add new settings to {}; their defaults apply", configFile, exception);
        }
    }

    private static JsonObject migrateLegacyKeys(JsonObject root) {
        JsonObject migrated = root.deepCopy();
        moveLegacyKey(migrated, "blockleDailyAntcoinReward", "gameRewards", "blockle");
        moveLegacyKey(migrated, "inventoryComputerButton", "client", "inventoryComputerButton");
        return migrated;
    }

    private static void moveLegacyKey(JsonObject root, String legacyKey, String section, String key) {
        if (!root.has(legacyKey)) return;
        JsonElement target = root.get(section);
        if (target != null && !target.isJsonObject()) return;
        JsonElement value = root.remove(legacyKey);
        JsonObject object = target == null ? new JsonObject() : target.getAsJsonObject();
        if (!object.has(key)) object.add(key, value);
        root.add(section, object);
    }

    private static JsonObject sanitize(JsonObject value, JsonObject defaults, String path, List<String> problems) {
        JsonObject clean = new JsonObject();
        boolean map = MAP_SECTIONS.contains(path);
        for (Map.Entry<String, JsonElement> entry : value.entrySet()) {
            String key = entry.getKey();
            String name = path.isEmpty() ? key : path + "." + key;
            JsonElement expected = defaults.get(key);
            if (expected == null && path.isEmpty() && key.equals(LEGACY_MAIL_KEY)) {
                if (stringArray(entry.getValue())) clean.add(key, entry.getValue());
                else problems.add("\"" + name + "\" should be a list of names; it is ignored");
                continue;
            }
            if (expected == null && path.isEmpty() && PLAYER_KEYS.contains(key)) continue;
            if (expected == null && map && key.startsWith(AntOS.MODID + ":")) {
                problems.add("\"" + name + "\" should be written as \"" + path + "." + configKey(key) + "\"; it is ignored");
                continue;
            }
            if (expected == null && map && key.contains(":") && !defaults.entrySet().isEmpty()) {
                JsonElement accepted = accept(entry.getValue(), defaults.entrySet().iterator().next().getValue(), name, "it is ignored", true, problems);
                if (accepted != null) clean.add(key, accepted);
                continue;
            }
            if (expected == null) {
                problems.add(unknownKey(name, key, defaults, map));
                continue;
            }
            JsonElement accepted = accept(entry.getValue(), expected, name, "using the default (" + expected + ")", true, problems);
            if (accepted != null) clean.add(key, accepted);
        }
        return clean;
    }

    private static String unknownKey(String name, String key, JsonObject defaults, boolean map) {
        String suggestion = defaults.keySet().stream().filter(candidate -> candidate.equalsIgnoreCase(key)).findFirst().orElse(null);
        String message = "unknown setting \"" + name + "\"";
        if (suggestion != null) message += " (did you mean \"" + suggestion + "\"?)";
        else if (map) message += " (games from other mods use their full ID, such as \"examplemod:snake\")";
        return message + "; it is ignored";
    }

    private static JsonElement accept(JsonElement value, JsonElement expected, String name, String fallback, boolean packSetting, List<String> problems) {
        if (expected.isJsonObject()) {
            if (!value.isJsonObject()) {
                problems.add("\"" + name + "\" should be a section in braces; using its defaults");
                return null;
            }
            if (name.equals(DEFAULT_PREFERENCES_PATH)) return sanitizePreferences(value.getAsJsonObject(), name, problems);
            return sanitize(value.getAsJsonObject(), expected.getAsJsonObject(), name, problems);
        }
        JsonPrimitive want = expected.getAsJsonPrimitive();
        JsonPrimitive primitive = value.isJsonPrimitive() ? value.getAsJsonPrimitive() : null;
        if (want.isBoolean()) {
            if (primitive != null && primitive.isBoolean()) return value;
            problems.add("\"" + name + "\" should be true or false, not " + value + "; " + fallback);
            return null;
        }
        if (want.isString()) {
            if (primitive != null && primitive.isString()) return value;
            problems.add("\"" + name + "\" should be text in quotes, not " + value + "; " + fallback);
            return null;
        }
        if (!wholeNumber(primitive, want.getAsNumber() instanceof Integer)) {
            problems.add("\"" + name + "\" should be a whole number, not " + value + "; " + fallback);
            return null;
        }
        if (packSetting && primitive.getAsBigDecimal().signum() < 0) {
            problems.add("\"" + name + "\" should be 0 or more, not " + value + "; " + fallback);
            return null;
        }
        return value;
    }

    private static boolean wholeNumber(JsonPrimitive value, boolean intRange) {
        if (value == null || !value.isNumber()) return false;
        BigDecimal number;
        try {
            number = value.getAsBigDecimal();
        } catch (NumberFormatException exception) {
            return false;
        }
        if (number.signum() != 0 && number.stripTrailingZeros().scale() > 0) return false;
        BigDecimal min = BigDecimal.valueOf(intRange ? Integer.MIN_VALUE : Long.MIN_VALUE);
        BigDecimal max = BigDecimal.valueOf(intRange ? Integer.MAX_VALUE : Long.MAX_VALUE);
        return number.compareTo(min) >= 0 && number.compareTo(max) <= 0;
    }

    private static boolean stringArray(JsonElement value) {
        if (!value.isJsonArray()) return false;
        for (JsonElement element : value.getAsJsonArray()) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) return false;
        }
        return true;
    }

    private static JsonObject sanitizePreferences(JsonObject value, String path, List<String> problems) {
        Preferences defaults = new Preferences();
        defaults.normalize();
        JsonObject expected = GSON.toJsonTree(defaults).getAsJsonObject();
        JsonObject clean = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : value.entrySet()) {
            String key = entry.getKey();
            String name = path + "." + key;
            if (LEGACY_PREFERENCES.contains(key)) {
                if (entry.getValue().isJsonPrimitive()) clean.add(key, entry.getValue());
                else problems.add("\"" + name + "\" should be a single value; it is ignored");
                continue;
            }
            JsonElement want = expected.get(key);
            if (want == null) {
                problems.add(unknownKey(name, key, expected, false));
                continue;
            }
            JsonElement accepted = accept(entry.getValue(), want, name, "using the default (" + want + ")", false, problems);
            if (accepted != null) clean.add(key, accepted);
        }
        Preferences parsed = GSON.fromJson(clean, Preferences.class);
        parsed.normalize();
        JsonObject normalized = GSON.toJsonTree(parsed).getAsJsonObject();
        boolean legacyBrightness = clean.has("screenPalette") || clean.has("softText");
        for (Map.Entry<String, JsonElement> entry : clean.entrySet()) {
            String key = entry.getKey();
            if (LEGACY_PREFERENCES.contains(key) || legacyBrightness && key.equals("displayBrightness")) continue;
            JsonElement result = normalized.get(key);
            if (!entry.getValue().equals(result)) problems.add("\"" + path + "." + key + "\" can't be " + entry.getValue() + "; using " + result);
        }
        return clean;
    }

    private static JsonObject complete(JsonObject value, JsonObject defaults, String path, List<String> added) {
        JsonObject result = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : defaults.entrySet()) {
            String key = entry.getKey();
            String name = path.isEmpty() ? key : path + "." + key;
            JsonElement current = value.get(key);
            if (current == null) {
                result.add(key, entry.getValue().deepCopy());
                added.add(name);
            } else if (current.isJsonObject() && entry.getValue().isJsonObject() && !name.equals(DEFAULT_PREFERENCES_PATH)) {
                result.add(key, complete(current.getAsJsonObject(), entry.getValue().getAsJsonObject(), name, added));
            } else {
                result.add(key, current);
            }
        }
        value.entrySet().forEach(entry -> {
            if (!result.has(entry.getKey())) result.add(entry.getKey(), entry.getValue());
        });
        return result;
    }

    private static String configKey(String id) {
        if (id == null) return "";
        String path = id;
        if (id.startsWith(AntOS.MODID + ":")) path = id.substring(AntOS.MODID.length() + 1);
        else if (id.contains(":")) return id;
        StringBuilder key = new StringBuilder();
        boolean upper = false;
        for (char character : path.toCharArray()) {
            if (character == '_') {
                upper = true;
                continue;
            }
            key.append(upper ? Character.toUpperCase(character) : character);
            upper = false;
        }
        return key.toString();
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
                if (element == null || element.isJsonNull()) return new JsonObject();
                if (!element.isJsonObject()) throw new IllegalStateException(file.getFileName() + " should contain one section in braces");
                return element.getAsJsonObject();
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
        Long reward = shared().gameRewards.get("blockle");
        return reward == null ? 0L : Math.max(0L, reward);
    }

    public static boolean gameIncluded(String gameId, boolean fallback) {
        Boolean included = shared().builtInGames.get(configKey(gameId));
        return included == null ? fallback : included;
    }

    public static long gameAntcoinReward(String mode) {
        String key = configKey(mode);
        if (key.equals("blockle")) return -1L;
        Long reward = shared().gameRewards.get(key);
        return reward == null ? -1L : Math.max(0L, reward);
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
        private Map<String, Boolean> builtInGames = defaultBuiltInGames();
        private Map<String, Long> gameRewards = defaultGameRewards();
        private DesktopApps desktopApps = new DesktopApps();
        private PackClient client = new PackClient();
        private JeiIntegration jei = new JeiIntegration();
        private Set<String> antazonUnlockEmailsSent;
        private transient SharedSettings shared;

        private static PackConfig from(JsonObject root) {
            PackConfig loaded = GSON.fromJson(root, PackConfig.class);
            return (loaded == null ? new PackConfig() : loaded).withDefaults();
        }

        private PackConfig withDefaults() {
            builtInGames = withDefaultEntries(builtInGames, defaultBuiltInGames());
            gameRewards = withDefaultEntries(gameRewards, defaultGameRewards());
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
        private Map<String, Boolean> builtInGames = defaultBuiltInGames();
        private Map<String, Long> gameRewards = defaultGameRewards();
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
            builtInGames = withDefaultEntries(builtInGames, defaultBuiltInGames());
            gameRewards = withDefaultEntries(gameRewards, defaultGameRewards());
            if (desktopApps == null) desktopApps = new DesktopApps();
            return this;
        }
    }

    private static <T> Map<String, T> withDefaultEntries(Map<String, T> values, Map<String, T> defaults) {
        Map<String, T> merged = new LinkedHashMap<>(defaults);
        if (values != null) values.forEach((key, value) -> {
            if (key != null && value != null) merged.put(key, value);
        });
        return merged;
    }

    private static Map<String, Boolean> defaultBuiltInGames() {
        Map<String, Boolean> games = new LinkedHashMap<>();
        for (String game : List.of("chess", "minesweeper", "cakeStack", "fliptris", "netherBreakout", "flappyGhast", "ore2048", "pistonPush")) {
            games.put(game, true);
        }
        return games;
    }

    private static Map<String, Long> defaultGameRewards() {
        Map<String, Long> rewards = new LinkedHashMap<>();
        rewards.put("blockle", 10L);
        rewards.put("chessEasy", 5L);
        rewards.put("chessNormal", 10L);
        rewards.put("chessHard", 20L);
        rewards.put("chessExpert", 40L);
        rewards.put("minesweeperSmall", 5L);
        rewards.put("minesweeperLarge", 15L);
        rewards.put("flappyGhast15", 5L);
        rewards.put("flappyGhast40", 15L);
        rewards.put("ore2048Diamond", 5L);
        rewards.put("ore2048Beacon", 25L);
        rewards.put("pistonPush", 5L);
        rewards.put("pistonPushPar", 15L);
        return rewards;
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
        private String uiSize;
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
            uiScalePercent = uiScalePercent == null ? legacyUiScale(uiSize) : normalizeUiScale(uiScalePercent);
            uiSize = null;
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
