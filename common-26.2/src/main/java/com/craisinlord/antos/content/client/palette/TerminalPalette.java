package com.craisinlord.antos.content.client.palette;

import com.craisinlord.antos.config.AntOSSettings;
import com.craisinlord.antos.content.client.ComputerTheme;

public final class TerminalPalette {
    private static final int DIM_LIFT = 0xFF1A221A;
    private static final int DIM_BACKGROUND = 0xFF0D130D;
    private static final int SOFT_TARGET = 0xFFC9D0CB;
    private static final int[] RAINBOW_SWATCH = {0xFFFF3030, 0xFFFFA020, 0xFFFFFF30, 0xFF30E050, 0xFF3080FF, 0xFFA050FF};
    private static final int[] THEME_SWATCH = {0xFF030603, 0xFF65FF65, 0xFFA0D8A0, 0xFF3E9E3E};

    private TerminalPalette() { }

    private enum Role { SURFACE, MUTED_TEXT, DISABLED_TEXT, PRIMARY_TEXT, STATUS, CONTENT }

    private static Role roleOf(int color) {
        return switch (color & 0x00FFFFFF) {
            case 0x000000, 0x000603, 0x030603, 0x080D08, 0x071007, 0x0B180B, 0x102010, 0x102A10,
                    0x0A2118, 0x143014, 0x172717, 0x173C2C, 0x183318, 0x183B2D, 0x1A2A1A,
                    0x1D3D1D, 0x1F4A1F, 0x203B18, 0x26351D, 0x2A6A2A,
                    0x173817, 0x204820, 0x1E481E, 0x2A5E2A, 0x315E43, 0x356035 -> Role.SURFACE;
            case 0x315531, 0x638063, 0x476047, 0x587458 -> Role.DISABLED_TEXT;
            case 0x6E926E, 0x7EA77E, 0x87B787, 0x3E9E3E -> Role.MUTED_TEXT;
            case 0x65FF65, 0xB8FFB8, 0xA0D8A0, 0xB0F0B0, 0xFFFFFF -> Role.PRIMARY_TEXT;
            case 0xFF7777, 0xFF5555 -> Role.STATUS;
            default -> Role.CONTENT;
        };
    }

    public static int remap(int source, ComputerTheme theme) {
        String brightness = AntOSSettings.displayBrightness();
        Role role = roleOf(source);
        if (brightness.equals("dim")) return theme.apply(dim(source, role));
        int themed = theme.apply(source);
        if (brightness.equals("soft") && role == Role.PRIMARY_TEXT) return Ui.mix(themed, SOFT_TARGET, 0.42F);
        return themed;
    }

    public static int themeSwatch(ComputerTheme theme, int slot) {
        return theme.apply(THEME_SWATCH[Math.floorMod(slot, THEME_SWATCH.length)]);
    }

    public static int rainbowSwatch(int index) {
        return RAINBOW_SWATCH[Math.floorMod(index, RAINBOW_SWATCH.length)];
    }

    public static int rainbowSwatchCount() {
        return RAINBOW_SWATCH.length;
    }

    private static int dim(int source, Role role) {
        return switch (role) {
            case SURFACE -> Ui.mix(source, DIM_LIFT, 0.45F);
            case PRIMARY_TEXT -> Ui.mix(source, DIM_BACKGROUND, 0.30F);
            case MUTED_TEXT -> Ui.mix(source, DIM_BACKGROUND, 0.08F);
            case STATUS -> Ui.mix(source, DIM_BACKGROUND, 0.20F);
            default -> source;
        };
    }
}
