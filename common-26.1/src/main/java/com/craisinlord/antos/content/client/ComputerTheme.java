package com.craisinlord.antos.content.client;

import com.craisinlord.antos.config.AntOSSettings;
import java.util.Locale;

public enum ComputerTheme {
    GREEN(0.0F, 1.0F, 0.0F),
    AMBER(1.0F, 0.69F, 0.0F),
    BLUE(0.25F, 0.72F, 1.0F),
    WHITE(0.82F, 0.82F, 0.82F),
    LAVENDER(0.58F, 0.32F, 1.0F),
    RAINBOW(0.0F, 1.0F, 0.0F, true);

    private static ComputerTheme cached;
    private final float red;
    private final float green;
    private final float blue;
    private final boolean rainbow;

    ComputerTheme(float red, float green, float blue) {
        this(red, green, blue, false);
    }

    ComputerTheme(float red, float green, float blue, boolean rainbow) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.rainbow = rainbow;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ComputerTheme active() {
        if (cached == null) cached = byId(AntOSSettings.preferences().theme);
        return cached;
    }

    public static void select(ComputerTheme theme) {
        cached = theme;
        AntOSSettings.updatePreferences(preferences -> preferences.theme = theme.id());
    }

    public static ComputerTheme byId(String id) {
        for (ComputerTheme theme : values()) if (theme.id().equals(id)) return theme;
        return GREEN;
    }




    public int apply(int color) {
        if (this == GREEN) return color;
        int alpha = color >>> 24;
        int r = (color >> 16) & 255;
        int g = (color >> 8) & 255;
        int b = color & 255;
        if (Math.abs(r - b) > 12 || g < Math.max(r, b)) return color;
        int base = (r + b) / 2;
        int chroma = g - base;
        float[] tint = tint();
        int nr = clamp(base + Math.round(chroma * tint[0]));
        int ng = clamp(base + Math.round(chroma * tint[1]));
        int nb = clamp(base + Math.round(chroma * tint[2]));
        return alpha << 24 | nr << 16 | ng << 8 | nb;
    }

    public float[] tint() {
        if (rainbow) {
            float hue = Math.floorMod(System.nanoTime(), 6_000_000_000L) / 1_000_000_000.0F;
            int segment = (int) hue;
            float fraction = hue - segment;
            float[][] colors = {{1, 0, 0}, {1, 0.45F, 0}, {1, 1, 0}, {0, 1, 0}, {0, 0.45F, 1}, {0.55F, 0, 1}, {1, 0, 0}};
            return new float[]{colors[segment][0] + (colors[segment + 1][0] - colors[segment][0]) * fraction,
                    colors[segment][1] + (colors[segment + 1][1] - colors[segment][1]) * fraction,
                    colors[segment][2] + (colors[segment + 1][2] - colors[segment][2]) * fraction};
        }
        return new float[]{red, green, blue};
    }

    public float[] accent() {
        int color = apply(0xFF65FF65);
        return new float[]{((color >> 16) & 255) / 255.0F, ((color >> 8) & 255) / 255.0F, (color & 255) / 255.0F};
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
