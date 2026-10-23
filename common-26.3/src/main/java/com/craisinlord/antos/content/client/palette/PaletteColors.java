package com.craisinlord.antos.content.client.palette;

import java.util.Locale;

public final class PaletteColors {
    public static final int DESKTOP = 0;
    public static final int WINDOW = 1;
    public static final int PANEL = 2;
    public static final int TEXT = 3;
    public static final int MUTED = 4;
    public static final int ACCENT = 5;
    public static final int ICONS = 6;
    public static final int ERROR = 7;
    public static final String[] KEYS = {"desktop", "window", "panel", "text", "muted", "accent", "icons", "error"};
    private static final int[] GRAPHITE = {0xFF141518, 0xFF202124, 0xFF191A1D, 0xFFDBDEE1, 0xFFB5BAC1, 0xFF65FF65, 0xFF65FF65, 0xFFFA777C};
    private static final int[] TERMINAL = {0xFF102010, 0xFF030603, 0xFF071007, 0xFFA0D8A0, 0xFF6E926E, 0xFF65FF65, 0xFF65FF65, 0xFFFF7777};
    private static final String SHARE_PREFIX = "antos:";

    private PaletteColors() { }

    public static int[] graphite() {
        return GRAPHITE.clone();
    }

    public static int[] terminal() {
        return TERMINAL.clone();
    }

    public static int[] parse(String stored) {
        int[] colors = graphite();
        if (stored == null || stored.isBlank()) return colors;
        String[] parts = stored.split(",");
        for (int index = 0; index < colors.length && index < parts.length; index++) {
            Integer parsed = parseHex(parts[index]);
            if (parsed != null) colors[index] = parsed;
        }
        return colors;
    }

    public static String format(int[] colors) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < colors.length; index++) {
            if (index > 0) builder.append(',');
            builder.append(hex(colors[index]));
        }
        return builder.toString();
    }

    public static String hex(int color) {
        return String.format(Locale.ROOT, "%06X", color & 0x00FFFFFF);
    }

    public static Integer parseHex(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        if (cleaned.startsWith("#")) cleaned = cleaned.substring(1);
        if (cleaned.length() == 3) {
            cleaned = "" + cleaned.charAt(0) + cleaned.charAt(0) + cleaned.charAt(1) + cleaned.charAt(1) + cleaned.charAt(2) + cleaned.charAt(2);
        }
        if (!cleaned.matches("[0-9a-fA-F]{6}")) return null;
        return 0xFF000000 | Integer.parseInt(cleaned, 16);
    }

    public static String shareCode(int[] colors) {
        return SHARE_PREFIX + format(colors);
    }

    public static int[] parseShareCode(String code) {
        if (code == null) return null;
        String cleaned = code.trim();
        if (cleaned.regionMatches(true, 0, SHARE_PREFIX, 0, SHARE_PREFIX.length())) cleaned = cleaned.substring(SHARE_PREFIX.length());
        String[] parts = cleaned.split(",");
        if (parts.length != KEYS.length) return null;
        int[] colors = new int[KEYS.length];
        for (int index = 0; index < parts.length; index++) {
            Integer parsed = parseHex(parts[index]);
            if (parsed == null) return null;
            colors[index] = parsed;
        }
        return colors;
    }

    public static double contrast(int first, int second) {
        double a = luminance(first);
        double b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(int color) {
        return 0.2126 * channel((color >> 16) & 255) + 0.7152 * channel((color >> 8) & 255) + 0.0722 * channel(color & 255);
    }

    private static double channel(int value) {
        double normalized = value / 255.0;
        return normalized <= 0.03928 ? normalized / 12.92 : Math.pow((normalized + 0.055) / 1.055, 2.4);
    }
}
