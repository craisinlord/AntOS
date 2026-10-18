package com.craisinlord.antos.config;

import java.nio.file.Files;
import java.nio.file.Path;

public final class DesktopPreferencesTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("antos-desktop-preferences");
        Path config = directory.resolve("antos.json");
        try {
            Files.writeString(config, "{\"preferences\":{\"theme\":\"amber\",\"mailSound\":false,\"taskSound\":false}}");
            AntOSSettings.load(config);
            var defaults = AntOSSettings.preferences();
            check(!defaults.pauseWhileOpen, "Old configs preserve running worlds");
            check(defaults.interfaceSounds && defaults.interfaceVolume == 60, "Old configs receive sound defaults");
            check(defaults.theme.equals("amber") && !defaults.mailSound && !defaults.taskSound, "Existing preferences survive migration");
            defaults.pauseWhileOpen = true;
            check(!AntOSSettings.preferences().pauseWhileOpen, "Reading preferences returns an independent copy");
            AntOSSettings.updatePreferences(p -> {
                p.pauseWhileOpen = true;
                p.interfaceSounds = false;
                p.interfaceVolume = 37;
            });
            AntOSSettings.load(config);
            var saved = AntOSSettings.preferences();
            check(saved.pauseWhileOpen && !saved.interfaceSounds && saved.interfaceVolume == 37, "Preferences persist across reloads");
            check(!saved.mailSound && !saved.taskSound, "Interface controls leave notification preferences alone");
            AntOSSettings.updatePreferences(p -> p.interfaceVolume = 200);
            check(AntOSSettings.preferences().interfaceVolume == 100, "Volume is clamped above 100");
            AntOSSettings.updatePreferences(p -> p.interfaceVolume = -1);
            check(AntOSSettings.preferences().interfaceVolume == 0, "Volume is clamped below zero");
            Files.writeString(config, "{\"preferences\":null}");
            AntOSSettings.load(config);
            check(!AntOSSettings.preferences().pauseWhileOpen && AntOSSettings.preferences().interfaceVolume == 60, "Null preferences receive defaults");
            System.out.println("Desktop preference checks passed");
        } finally {
            Files.deleteIfExists(config.resolveSibling("antos.json.tmp"));
            Files.deleteIfExists(config);
            Files.deleteIfExists(directory);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
