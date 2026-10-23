package com.craisinlord.antos.content.client.palette;

import com.craisinlord.antos.config.AntOSSettings;
import com.craisinlord.antos.content.client.ComputerTheme;

import java.nio.file.Files;
import java.nio.file.Path;

public final class PaletteTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("antos-palette");
        Path config = directory.resolve("antos.json");
        try {
            Files.writeString(config, "{\"preferences\":{\"screenPalette\":\"light\",\"softText\":true}}");
            AntOSSettings.loadClientState(directory.resolve("antos").resolve("client.json"), config);
            var migrated = AntOSSettings.preferences();
            check(migrated.displayBrightness.equals("dim"), "old Light and Dim palettes become Dim brightness");
            check(migrated.displayLook.equals("terminal") && migrated.iconColor.equals("accent") && migrated.wallpaperColors.equals("match"),
                    "new looks default to Terminal");
            check(migrated.resumeSession, "resume is on by default");
            check(!migrated.lookChosen, "the look picker shows until a look is chosen");

            AntOSSettings.updatePreferences(p -> p.displayBrightness = "standard");
            ComputerTheme.select(ComputerTheme.GREEN);
            Ui.refresh();
            check(Ui.ACCENT == 0xFF65FF65, "Terminal keeps the neon green for accents");
            check(Ui.BORDER_STRONG == 0xFF3E9E3E && Ui.TEXT_STRONG == 0xFFB0F0B0 && Ui.TEXT == 0xFFA0D8A0, "Terminal frames, headings, and text are toned down");
            check(PaletteColors.contrast(Ui.TEXT_STRONG, Ui.BACKGROUND) > PaletteColors.contrast(Ui.TEXT, Ui.BACKGROUND), "Terminal headings stay brighter than body text");
            check(Ui.BACKGROUND == 0xFF030603 && Ui.SURFACE == 0xFF102010, "Terminal keeps its surfaces");
            check(Ui.fromTerminal(0xFFB8FFB8) == Ui.TEXT, "pale task markup follows body text");
            Ui.Preview graphitePreview = Ui.preview("graphite");
            check(graphitePreview.window() == 0xFF202124 && Ui.BACKGROUND == 0xFF030603, "previewing Graphite leaves the active look alone");
            check(Ui.preview("terminal").border() == 0xFF3E9E3E, "Terminal preview matches the active Terminal look");
            check(Ui.HOVER == 0xFF173817 && Ui.LINE == 0xFF315531 && Ui.TEXT_MUTED == 0xFF6E926E && Ui.ERROR == 0xFFFF7777, "Terminal keeps the original details");
            check(Ui.icon(0xFF315531) == 0xFF315531 && Ui.proceduralWallpaper(0xFF0C2010) == 0xFF0C2010, "Terminal icons and wallpaper are unchanged");
            check(Ui.fromTerminal(0xFF87B787) == 0xFF87B787, "Terminal task markup colors are unchanged");

            ComputerTheme.select(ComputerTheme.AMBER);
            Ui.refresh();
            check(Ui.ACCENT == ComputerTheme.AMBER.apply(0xFF65FF65), "Terminal themes still tint the screen");

            AntOSSettings.updatePreferences(p -> p.displayLook = "graphite");
            Ui.refresh();
            check(Ui.terminal() && Ui.BORDER_STRONG == ComputerTheme.AMBER.apply(0xFF3E9E3E), "AntOS stays in Terminal until a look is confirmed");
            AntOSSettings.updatePreferences(p -> p.lookChosen = true);
            Ui.refresh();
            check(Ui.BACKGROUND == 0xFF202124 && Ui.SURFACE == 0xFF191A1D && Ui.TEXT == 0xFFDBDEE1, "Graphite uses the dark gray palette");
            check(Ui.ACCENT == ComputerTheme.AMBER.apply(0xFF65FF65), "Graphite takes its accent from the theme");
            for (int fill : new int[]{Ui.SELECTION, Ui.SELECTED_FILL, Ui.TOGGLE_ON, Ui.HOVER, Ui.PANEL, Ui.LINE, Ui.BORDER_STRONG, Ui.FIND_MATCH, Ui.BRAND_MARK}) {
                check(neutral(fill), "Graphite fills have no green tint: " + Integer.toHexString(fill));
            }
            check(PaletteColors.contrast(Ui.TEXT, Ui.BACKGROUND) >= 4.5 && PaletteColors.contrast(Ui.TEXT_MUTED, Ui.BACKGROUND) >= 4.5,
                    "Graphite text is readable");
            check(neutral(Ui.proceduralWallpaper(0xFF0C2010)), "Graphite built-in wallpaper is gray");
            check(Ui.icon(0xFF65FF65) == Ui.ACCENT, "accent icons use the accent color");
            AntOSSettings.updatePreferences(p -> p.iconColor = "neutral");
            Ui.refresh();
            check(neutral(Ui.icon(0xFF65FF65)), "neutral icons are gray");

            int[] colors = PaletteColors.graphite();
            colors[PaletteColors.WINDOW] = 0xFF10141C;
            colors[PaletteColors.ACCENT] = 0xFF3388FF;
            colors[PaletteColors.ICONS] = 0xFFFFAA00;
            String code = PaletteColors.shareCode(colors);
            int[] parsed = PaletteColors.parseShareCode(code);
            check(parsed != null && java.util.Arrays.equals(parsed, colors), "share codes round-trip");
            check(PaletteColors.parseShareCode("antos:nope") == null, "bad share codes are rejected");
            check(PaletteColors.parseHex("#abc") == 0xFFAABBCC && PaletteColors.parseHex("12345") == null, "hex input accepts short and full forms only");
            AntOSSettings.updatePreferences(p -> {
                p.displayLook = "custom";
                p.customColors = PaletteColors.format(colors);
            });
            Ui.refresh();
            check(Ui.BACKGROUND == 0xFF10141C && Ui.ACCENT == 0xFF3388FF, "Custom colors drive the palette");
            check(Ui.icon(0xFF65FF65) == 0xFFFFAA00, "Custom icons use their own color");
            AntOSSettings.updatePreferences(p -> p.customColors = "zzzzzz,,");
            Ui.refresh();
            check(Ui.BACKGROUND == 0xFF202124, "invalid custom colors fall back to Graphite");
            System.out.println("PaletteTest passed");
        } finally {
            Files.walk(directory).sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    private static boolean neutral(int color) {
        int r = (color >> 16) & 255;
        int g = (color >> 8) & 255;
        int b = color & 255;
        return Math.abs(r - g) <= 10 && Math.abs(g - b) <= 10 && Math.abs(r - b) <= 12;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
