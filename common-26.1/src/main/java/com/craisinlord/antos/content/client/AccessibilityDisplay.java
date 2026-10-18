package com.craisinlord.antos.content.client;

import com.craisinlord.antos.config.AntOSSettings;

public final class AccessibilityDisplay {
    public enum ColorRole {
        BACKGROUND,
        SURFACE,
        PANEL,
        HOVER,
        SELECTION,
        SUBTLE_TEXT,
        MUTED_TEXT,
        DISABLED_TEXT,
        PRIMARY_TEXT,
        STATUS,
        CONTENT
    }

    private static final int DIM_LIFT = 0xFF1A221A;
    private static final int DIM_BACKGROUND = 0xFF0D130D;

    private AccessibilityDisplay() { }

    public static ColorRole roleOf(int color) {
        return switch (color & 0x00FFFFFF) {
            case 0x000000, 0x000603, 0x030603, 0x080D08 -> ColorRole.BACKGROUND;
            case 0x071007, 0x0B180B, 0x102010, 0x102A10 -> ColorRole.SURFACE;
            case 0x0A2118, 0x143014, 0x172717, 0x173C2C, 0x183318, 0x183B2D, 0x1A2A1A,
                    0x1D3D1D, 0x1F4A1F, 0x203B18, 0x26351D, 0x2A6A2A -> ColorRole.PANEL;
            case 0x173817, 0x204820, 0x1E481E -> ColorRole.HOVER;
            case 0x2A5E2A, 0x315E43, 0x356035 -> ColorRole.SELECTION;
            case 0x315531, 0x638063, 0x476047, 0x587458 -> ColorRole.DISABLED_TEXT;
            case 0x6E926E, 0x7EA77E, 0x87B787 -> ColorRole.MUTED_TEXT;
            case 0x65FF65, 0xB8FFB8, 0xFFFFFF -> ColorRole.PRIMARY_TEXT;
            case 0xFF7777, 0xFF5555 -> ColorRole.STATUS;
            default -> ColorRole.CONTENT;
        };
    }

    /** Dim works on the original green colors before theming, so the theme tint and text hierarchy both survive. */
    public static int remap(int source, ComputerTheme theme) {
        String brightness = AntOSSettings.displayBrightness();
        ColorRole role = roleOf(source);
        if (brightness.equals("dim")) return theme.apply(dim(source, role));
        int themed = theme.apply(source);
        if (brightness.equals("soft") && role == ColorRole.PRIMARY_TEXT) return soften(themed);
        return themed;
    }

    private static int dim(int source, ColorRole role) {
        return switch (role) {
            case BACKGROUND, SURFACE, PANEL, HOVER, SELECTION -> blend(source, DIM_LIFT, 0.45F);
            case PRIMARY_TEXT -> blend(source, DIM_BACKGROUND, 0.30F);
            case MUTED_TEXT, SUBTLE_TEXT -> blend(source, DIM_BACKGROUND, 0.08F);
            case STATUS -> blend(source, DIM_BACKGROUND, 0.20F);
            default -> source;
        };
    }

    private static int soften(int color) {
        return blend(color, 0xFFC9D0CB, 0.42F);
    }

    private static int blend(int first, int second, float amount) {
        int r = Math.round(((first >> 16) & 255) * (1 - amount) + ((second >> 16) & 255) * amount);
        int g = Math.round(((first >> 8) & 255) * (1 - amount) + ((second >> 8) & 255) * amount);
        int b = Math.round((first & 255) * (1 - amount) + (second & 255) * amount);
        return first & 0xFF000000 | r << 16 | g << 8 | b;
    }
}
