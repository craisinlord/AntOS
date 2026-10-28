package com.craisinlord.antos.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class ConfigMigrationTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("antos-config-migration");
        try {
            migratesPlayerData(directory.resolve("migrate"));
            leavesConfigAloneOnceMigrated(directory.resolve("existing"));
            createsCleanConfig(directory.resolve("fresh"));
            merchantTutorialState(directory.resolve("merchant"));
            seedsPackDefaults(directory.resolve("defaults"));
            keepsUnreadableConfig(directory.resolve("broken"));
            usesServerSettings(directory.resolve("synced"));
            addsMissingSettings(directory.resolve("upgrade"));
            keepsGoodValuesBesideBadOnes(directory.resolve("invalid"));
            acceptsOtherModGames(directory.resolve("modded"));
            movesLegacyKeys(directory.resolve("legacy"));
            System.out.println("Config migration checks passed");
        } finally {
            Files.walk(directory).sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    private static void migratesPlayerData(Path directory) throws Exception {
        Files.createDirectories(directory);
        Path config = directory.resolve("antos.json");
        Path state = directory.resolve("antos").resolve("client.json");
        UUID account = UUID.randomUUID();
        Files.writeString(config, """
                {
                  "unlockAllArchiveEntries": true,
                  "passwordHintAfterFailures": 7,
                  "inventoryComputerButton": true,
                  "desktopApps": { "antazon": true },
                  "antazonUnlockEmailsSent": ["someone"],
                  "lastAnternetUsername": "ant",
                  "initialBootSeen": true,
                  "completedComputerTours": ["%s"],
                  "taskGroupCollapsed": { "main": true },
                  "resumeSnapshots": { "key": "snapshot" },
                  "terminalHistory": { "home": ["ls"] },
                  "preferences": { "theme": "blue", "screenPalette": "dim" }
                }
                """.formatted(account));
        AntOSSettings.loadConfig(config);
        check(AntOSSettings.unlockAllArchiveEntries() && AntOSSettings.passwordHintAfterFailures() == 7, "v1 gameplay settings load");
        check(AntOSSettings.inventoryComputerButtonEnabled(), "v1 inventory button setting is honored");
        check(AntOSSettings.appEnabled("ANTAZON"), "v1 desktop apps load");
        AntOSSettings.loadClientState(state, config);
        check(Files.exists(state), "client state file is created");
        check(AntOSSettings.lastAnternetUsername().equals("ant"), "username moves to client state");
        check(AntOSSettings.hasCompletedComputerTour(account), "completed tours move to client state");
        check(Boolean.TRUE.equals(AntOSSettings.taskGroupCollapsed("main")), "collapsed groups move to client state");
        check("snapshot".equals(AntOSSettings.resumeSnapshot("key")), "resume snapshots move to client state");
        check(AntOSSettings.terminalHistory("home").equals(java.util.List.of("ls")), "terminal history moves to client state");
        check(AntOSSettings.preferences().theme.equals("blue") && AntOSSettings.displayBrightness().equals("dim"), "preferences move and normalize");
        check(!AntOSSettings.consumeInitialBoot(), "boot flag moves to client state");

        JsonObject stripped = JsonParser.parseString(Files.readString(config)).getAsJsonObject();
        check(stripped.get("configVersion").getAsInt() == 2, "config is marked as version 2");
        for (String key : new String[] {"preferences", "lastAnternetUsername", "initialBootSeen", "completedComputerTours",
                "taskGroupCollapsed", "resumeSnapshots", "terminalHistory", "inventoryComputerButton"}) {
            check(!stripped.has(key), key + " is removed from the pack config");
        }
        check(stripped.get("unlockAllArchiveEntries").getAsBoolean() && stripped.get("passwordHintAfterFailures").getAsInt() == 7, "gameplay settings stay in the pack config");
        check(stripped.has("antazonUnlockEmailsSent"), "legacy Antazon mail list stays for the server import");
        check(stripped.getAsJsonObject("client").get("inventoryComputerButton").getAsBoolean(), "inventory button moves into the client section");
        check(stripped.getAsJsonObject("client").has("defaultPreferences"), "client section lists default preferences");

        AntOSSettings.loadConfig(config);
        check(AntOSSettings.inventoryComputerButtonEnabled() && AntOSSettings.legacyAntazonUnlockEmails().contains("someone"), "stripped config still loads");
    }

    private static void leavesConfigAloneOnceMigrated(Path directory) throws Exception {
        Files.createDirectories(directory.resolve("antos"));
        Path config = directory.resolve("antos.json");
        Path state = directory.resolve("antos").resolve("client.json");
        String packFile = "{\"lastAnternetUsername\":\"author\",\"preferences\":{\"theme\":\"amber\"}}";
        Files.writeString(config, packFile);
        Files.writeString(state, "{\"lastAnternetUsername\":\"player\",\"preferences\":{\"theme\":\"lavender\"}}");
        AntOSSettings.loadConfig(config);
        String loaded = Files.readString(config);
        AntOSSettings.loadClientState(state, config);
        check(AntOSSettings.lastAnternetUsername().equals("player"), "existing client state wins over the pack config");
        AntOSSettings.updatePreferences(p -> p.theme = "white");
        AntOSSettings.setLastAnternetUsername("player2");
        check(Files.readString(config).equals(loaded), "pack config is never rewritten once client state exists");
        check(JsonParser.parseString(loaded).getAsJsonObject().has("lastAnternetUsername"), "unknown keys stay in the pack config");
        check(AntOSSettings.preferences().theme.equals("white"), "preference changes go to client state");
    }

    private static void createsCleanConfig(Path directory) throws Exception {
        Path config = directory.resolve("antos.json");
        Path state = directory.resolve("antos").resolve("client.json");
        AntOSSettings.loadConfig(config);
        AntOSSettings.loadClientState(state, config);
        JsonObject created = JsonParser.parseString(Files.readString(config)).getAsJsonObject();
        check(created.get("configVersion").getAsInt() == 2 && created.has("client"), "new pack config uses version 2");
        check(!created.has("preferences") && !created.has("antazonUnlockEmailsSent") && !created.has("inventoryComputerButton"), "new pack config has no player data");
        String before = Files.readString(config);
        AntOSSettings.updatePreferences(p -> p.theme = "blue");
        AntOSSettings.loadConfig(config);
        check(Files.readString(config).equals(before), "loading a complete pack config never writes it");
    }

    private static void merchantTutorialState(Path directory) throws Exception {
        Path config = directory.resolve("antos.json");
        Path state = directory.resolve("antos").resolve("client.json");
        AntOSSettings.loadConfig(config);
        AntOSSettings.loadClientState(state, config);
        String originalConfig = Files.readString(config);
        String first = "workspace:" + UUID.randomUUID();
        String second = "workspace:" + UUID.randomUUID();
        check(!AntOSSettings.appEnabled("MERCHANT"), "MERCHANT is disabled in fresh configs");
        check(!AntOSSettings.hasCompletedMerchantTour(first), "New workspaces receive MERCHANT onboarding");
        AntOSSettings.setMerchantTourCompleted(first, true);
        AntOSSettings.loadClientState(state, config);
        check(AntOSSettings.hasCompletedMerchantTour(first), "MERCHANT tutorial completion survives a reload");
        check(!AntOSSettings.hasCompletedMerchantTour(second), "MERCHANT completion is isolated by workspace");
        check(Files.readString(config).equals(originalConfig), "Tutorial completion leaves pack settings untouched");
        AntOSSettings.setMerchantTourCompleted(first, false);
        AntOSSettings.loadClientState(state, config);
        check(!AntOSSettings.hasCompletedMerchantTour(first), "MERCHANT onboarding can be reset");
        AntOSSettings.setMerchantTourCompleted("workspace:unbound", true);
        AntOSSettings.setMerchantTourCompleted(null, true);
        AntOSSettings.setMerchantTourCompleted("", true);
        check(!AntOSSettings.hasCompletedMerchantTour("workspace:unbound"), "Unbound workspaces never suppress onboarding");
        Files.writeString(state, "{\"completedMerchantTours\":null}");
        AntOSSettings.loadClientState(state, config);
        check(!AntOSSettings.hasCompletedMerchantTour(first), "Older or null tutorial state receives safe defaults");
        AntOSSettings.setMerchantTourCompleted(first, true);
        check(AntOSSettings.hasCompletedMerchantTour(first), "Normalized tutorial state remains writable");
    }

    private static void seedsPackDefaults(Path directory) throws Exception {
        Files.createDirectories(directory);
        Path config = directory.resolve("antos.json");
        Path state = directory.resolve("antos").resolve("client.json");
        Files.writeString(config, "{\"client\":{\"defaultPreferences\":{\"theme\":\"amber\",\"uiSize\":\"large\",\"taskMapZoom\":\"nonsense\"}}}");
        AntOSSettings.loadConfig(config);
        AntOSSettings.loadClientState(state, config);
        var preferences = AntOSSettings.preferences();
        check(preferences.theme.equals("amber") && preferences.uiScalePercent == 115, "pack defaults seed a new player");
        check(preferences.taskMapZoom.equals("75") && preferences.interfaceSounds, "invalid or missing defaults fall back");
        AntOSSettings.updatePreferences(p -> p.theme = "green");
        Files.writeString(config, "{\"client\":{\"defaultPreferences\":{\"theme\":\"blue\"}}}");
        AntOSSettings.loadConfig(config);
        AntOSSettings.loadClientState(state, config);
        check(AntOSSettings.preferences().theme.equals("green"), "changed pack defaults leave existing players alone");
    }

    private static void keepsUnreadableConfig(Path directory) throws Exception {
        Files.createDirectories(directory);
        Path config = directory.resolve("antos.json");
        Path state = directory.resolve("antos").resolve("client.json");
        Files.writeString(config, "{ not json");
        AntOSSettings.loadConfig(config);
        AntOSSettings.loadClientState(state, config);
        check(Files.readString(config).equals("{ not json"), "unreadable pack config is left untouched");
        check(Files.exists(state) && AntOSSettings.preferences().theme.equals("green"), "client state starts from defaults");
    }

    private static void usesServerSettings(Path directory) throws Exception {
        Files.createDirectories(directory);
        Path config = directory.resolve("antos.json");
        JsonObject server = new JsonObject();
        Files.writeString(config, "{\"desktopApps\":{\"antazon\":true},\"gameRewards\":{\"chessEasy\":99},\"builtInGames\":{\"chess\":false}}");
        AntOSSettings.loadConfig(config);
        AntOSSettings.encodeNetworkSettings(server);
        Files.writeString(config, "{\"desktopApps\":{\"antazon\":false}}");
        AntOSSettings.loadConfig(config);
        check(!AntOSSettings.appEnabled("ANTAZON"), "local config applies before joining");
        AntOSSettings.applyNetworkSettings(server);
        check(AntOSSettings.appEnabled("ANTAZON") && AntOSSettings.gameAntcoinReward("chess_easy") == 99 && !AntOSSettings.gameIncluded("antos:chess", true),
                "server settings override the local config");
        AntOSSettings.clearNetworkSettings();
        check(!AntOSSettings.appEnabled("ANTAZON") && AntOSSettings.gameIncluded("antos:chess", true), "local config returns after disconnecting");
    }

    private static void addsMissingSettings(Path directory) throws Exception {
        Files.createDirectories(directory);
        Path config = directory.resolve("antos.json");
        Files.writeString(config, """
                {
                  "configVersion": 2,
                  "passwordHintAfterFailures": 5,
                  "desktopApps": { "antazon": true },
                  "builtInGames": { "chess": false },
                  "client": { "defaultPreferences": { "theme": "amber" } }
                }
                """);
        AntOSSettings.loadConfig(config);
        JsonObject updated = JsonParser.parseString(Files.readString(config)).getAsJsonObject();
        check(updated.get("passwordHintAfterFailures").getAsInt() == 5, "existing values survive the upgrade");
        check(updated.getAsJsonObject("desktopApps").get("antazon").getAsBoolean(), "existing section values survive the upgrade");
        check(updated.getAsJsonObject("desktopApps").has("merchant") && updated.getAsJsonObject("desktopApps").has("tasks"), "missing apps are added");
        check(!updated.getAsJsonObject("builtInGames").get("chess").getAsBoolean() && updated.getAsJsonObject("builtInGames").has("pistonPush"), "missing games are added");
        check(updated.has("jei") && updated.has("gameRewards") && updated.has("showIntroductionEntry"), "missing sections are added");
        check(updated.getAsJsonObject("client").getAsJsonObject("defaultPreferences").entrySet().size() == 1, "default preferences are left as written");
        String completed = Files.readString(config);
        AntOSSettings.loadConfig(config);
        check(Files.readString(config).equals(completed), "a completed config is not written again");
    }

    private static void keepsGoodValuesBesideBadOnes(Path directory) throws Exception {
        Files.createDirectories(directory);
        Path config = directory.resolve("antos.json");
        Path state = directory.resolve("antos").resolve("client.json");
        Files.writeString(config, """
                {
                  "passwordHintAfterFailures": "three",
                  "unlockAllArchiveEntries": true,
                  "desktopApps": { "antazon": "yes", "tasks": true },
                  "desktopapps": { "merchant": true },
                  "gameRewards": { "chessEasy": -4, "chessHard": 2.5, "chessNormal": 12 },
                  "builtInGames": { "chesss": false, "antos:chess": false },
                  "jei": [],
                  "client": { "inventoryComputerButton": 1, "defaultPreferences": { "theme": 3, "interfaceVolume": 140, "mailAlerts": "toast", "favoriteColor": "red" } }
                }
                """);
        AntOSSettings.loadConfig(config);
        check(AntOSSettings.passwordHintAfterFailures() == 3 && AntOSSettings.unlockAllArchiveEntries(), "a bad value only resets itself");
        check(!AntOSSettings.appEnabled("ANTAZON") && AntOSSettings.appEnabled("TASKS"), "a bad app switch keeps the other apps");
        check(!AntOSSettings.appEnabled("MERCHANT"), "misspelled sections are ignored");
        check(AntOSSettings.gameAntcoinReward("chess_easy") == 5 && AntOSSettings.gameAntcoinReward("chess_hard") == 20
                && AntOSSettings.gameAntcoinReward("chess_normal") == 12, "negative and fractional rewards use their defaults");
        check(AntOSSettings.gameIncluded("antos:chess", true), "misspelled and namespaced built-in game keys are ignored");
        check(AntOSSettings.jeiAntazonPrices() && !AntOSSettings.inventoryComputerButtonEnabled(), "bad sections and switches use their defaults");
        AntOSSettings.loadClientState(state, config);
        var preferences = AntOSSettings.preferences();
        check(preferences.theme.equals("green") && preferences.interfaceVolume == 100, "bad default preferences are corrected");
        JsonObject updated = JsonParser.parseString(Files.readString(config)).getAsJsonObject();
        check(updated.get("passwordHintAfterFailures").getAsString().equals("three") && updated.has("desktopapps"), "bad values stay in the file for the author to fix");
    }

    private static void acceptsOtherModGames(Path directory) throws Exception {
        Files.createDirectories(directory);
        Path config = directory.resolve("antos.json");
        Files.writeString(config, """
                {
                  "builtInGames": { "examplemod:snake": true, "examplemod:maze": false },
                  "gameRewards": { "examplemod:snake_win": 30, "blockle": 4 }
                }
                """);
        AntOSSettings.loadConfig(config);
        check(AntOSSettings.gameIncluded("examplemod:snake", false) && !AntOSSettings.gameIncluded("examplemod:maze", true), "other mods' games can be installed or removed");
        check(AntOSSettings.gameIncluded("examplemod:other", true) && !AntOSSettings.gameIncluded("examplemod:other", false), "unlisted games keep their own default");
        check(AntOSSettings.gameAntcoinReward("examplemod:snake_win") == 30 && AntOSSettings.gameAntcoinReward("examplemod:unknown") == -1, "other mods' reward modes are configurable");
        check(AntOSSettings.gameAntcoinReward("antos:chess_expert") == 40 && AntOSSettings.gameAntcoinReward("chess_expert") == 40, "built-in modes accept both spellings");
        check(AntOSSettings.gameAntcoinReward("blockle") == -1 && AntOSSettings.blockleDailyAntcoinReward() == 4, "Blockle is only paid through its own puzzle");
        JsonObject server = new JsonObject();
        AntOSSettings.encodeNetworkSettings(server);
        Files.writeString(config, "{}");
        AntOSSettings.loadConfig(config);
        AntOSSettings.applyNetworkSettings(server);
        check(AntOSSettings.gameAntcoinReward("examplemod:snake_win") == 30 && AntOSSettings.gameIncluded("antos:chess", false), "modded entries and defaults reach clients");
        AntOSSettings.clearNetworkSettings();
    }

    private static void movesLegacyKeys(Path directory) throws Exception {
        Files.createDirectories(directory);
        Path config = directory.resolve("antos.json");
        Path state = directory.resolve("antos").resolve("client.json");
        Files.writeString(config, "{\"configVersion\":2,\"blockleDailyAntcoinReward\":25,\"inventoryComputerButton\":true,\"antazonUnlockEmailsSent\":[\"ant\"]}");
        AntOSSettings.loadConfig(config);
        JsonObject updated = JsonParser.parseString(Files.readString(config)).getAsJsonObject();
        check(AntOSSettings.blockleDailyAntcoinReward() == 25 && AntOSSettings.inventoryComputerButtonEnabled(), "old key values still apply");
        check(!updated.has("blockleDailyAntcoinReward") && updated.getAsJsonObject("gameRewards").get("blockle").getAsLong() == 25, "the old Blockle reward moves into gameRewards");
        check(!updated.has("inventoryComputerButton") && updated.getAsJsonObject("client").get("inventoryComputerButton").getAsBoolean(), "the old inventory button moves into client");
        check(AntOSSettings.legacyAntazonUnlockEmails().contains("ant") && updated.has("antazonUnlockEmailsSent"), "the old Antazon mail list stays for worlds that have not imported it");
        Files.createDirectories(state.getParent());
        Files.writeString(state, "{\"preferences\":{\"uiSize\":\"largest\"}}");
        AntOSSettings.loadClientState(state, config);
        check(AntOSSettings.uiScalePercent() == 130, "the old interface size still applies");
        AntOSSettings.updatePreferences(p -> p.theme = "blue");
        JsonObject saved = JsonParser.parseString(Files.readString(state)).getAsJsonObject().getAsJsonObject("preferences");
        check(!saved.has("uiSize") && saved.get("uiScalePercent").getAsInt() == 130, "the old interface size is replaced by uiScalePercent");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
