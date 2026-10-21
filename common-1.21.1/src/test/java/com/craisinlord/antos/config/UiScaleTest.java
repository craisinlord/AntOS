package com.craisinlord.antos.config;

import java.nio.file.Files;
import java.nio.file.Path;

public final class UiScaleTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("antos-ui-scale");
        try {
            for (String size : new String[]{"standard", "large", "largest"}) {
                int expected = size.equals("large") ? 115 : size.equals("largest") ? 130 : 100;
                Path state = directory.resolve(size + ".json");
                Files.writeString(state, "{\"preferences\":{\"uiSize\":\"" + size + "\"}}");
                AntOSSettings.loadClientState(state, null);
                check(AntOSSettings.uiScalePercent() == expected, "Existing " + size + " setting keeps its size");
                AntOSSettings.updatePreferences(p -> p.uiScalePercent = 75);
                AntOSSettings.loadClientState(state, null);
                check(AntOSSettings.uiScalePercent() == 75 && AntOSSettings.antosUiScale() == 0.75F, "Smaller scale persists after reload");
                var copy = AntOSSettings.preferences();
                copy.uiScalePercent = 140;
                check(AntOSSettings.uiScalePercent() == 75, "Reading preferences does not expose mutable state");
                AntOSSettings.updatePreferences(p -> p.uiScalePercent = 100);
                check(AntOSSettings.antosUiScale() == 1.0F, "Reset restores standard scale");
            }
            Path state = directory.resolve("limits.json");
            Files.writeString(state, "{\"preferences\":{\"uiSize\":\"largest\",\"uiScalePercent\":60}}");
            AntOSSettings.loadClientState(state, null);
            check(AntOSSettings.uiScalePercent() == 60, "Explicit percentage overrides legacy preset");
            AntOSSettings.updatePreferences(p -> p.uiScalePercent = Integer.MIN_VALUE);
            check(AntOSSettings.uiScalePercent() == 50, "Very small settings clamp safely");
            AntOSSettings.updatePreferences(p -> p.uiScalePercent = Integer.MAX_VALUE);
            check(AntOSSettings.uiScalePercent() == 150, "Very large settings clamp safely");
            AntOSSettings.updatePreferences(p -> p.uiScalePercent = 83);
            check(AntOSSettings.uiScalePercent() == 85, "Percentages snap to a slider step");
            Path pack = directory.resolve("pack.json");
            Files.writeString(pack, "{\"client\":{\"defaultPreferences\":{\"uiScalePercent\":70}}}");
            AntOSSettings.loadConfig(pack);
            AntOSSettings.loadClientState(directory.resolve("new-player.json"), pack);
            check(AntOSSettings.uiScalePercent() == 70, "Pack defaults can set a smaller interface");
            System.out.println("UI scale migration, persistence, limits, and pack-default checks passed");
        } finally {
            try (var paths = Files.walk(directory)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
