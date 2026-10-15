package com.craisinlord.antos.content.client;

import com.craisinlord.antos.config.AntOSSettings;
import java.util.Locale;

public enum ComputerTheme {
    GREEN(0.0F, 1.0F, 0.0F),
    AMBER(1.0F, 0.69F, 0.0F),
    BLUE(0.25F, 0.72F, 1.0F),
    WHITE(0.82F, 0.82F, 0.82F),
    LAVENDER(0.58F, 0.32F, 1.0F);

    private static ComputerTheme cached;
    private static int pauseDepth;
    private final float red;
    private final float green;
    private final float blue;

    ComputerTheme(float red, float green, float blue) {
        this.red = red;
        this.green = green;
        this.blue = blue;
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

    public static void pause() {
        pauseDepth++;
    }

    public static void resume() {
        pauseDepth = Math.max(0, pauseDepth - 1);
    }

    public static int remap(int color) {
        return pauseDepth > 0 ? color : active().apply(color);
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
        int nr = clamp(base + Math.round(chroma * red));
        int ng = clamp(base + Math.round(chroma * green));
        int nb = clamp(base + Math.round(chroma * blue));
        return alpha << 24 | nr << 16 | ng << 8 | nb;
    }

    public float[] tint() {
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
