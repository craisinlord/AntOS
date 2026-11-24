package com.craisinlord.antos.content.client.studio;

import com.craisinlord.antos.content.client.palette.PaletteColors;
import com.craisinlord.antos.content.client.palette.Ui;

final class StudioTheme {
    static int window;
    static int desk;
    static int panel;
    static int button;
    static int section;
    static int hover;
    static int selected;
    static int line;
    static int strongLine;
    static int text;
    static int strong;
    static int muted;
    static int soft;
    static int accent;
    static int ink;
    static int error;
    static int warning;

    private StudioTheme() { }

    static int hex(String value, int fallback) {
        return value.matches("[0-9a-fA-F]{6}") ? Ui.alpha(Integer.parseInt(value, 16), 255) : fallback;
    }

    static void apply(boolean matchAntOS) {
        if (matchAntOS) Ui.refresh();
        else Ui.useGraphite();
        if (matchAntOS) {
            window = Ui.BACKGROUND;
            desk = Ui.terminal() ? Ui.DEEP : Ui.DESKTOP;
            panel = Ui.PANEL;
            text = Ui.TEXT;
            muted = Ui.TEXT_SOFT;
            accent = Ui.ACCENT;
            error = Ui.ERROR;
        } else {
            int[] bases = PaletteColors.graphite();
            window = bases[PaletteColors.WINDOW];
            desk = bases[PaletteColors.DESKTOP];
            panel = bases[PaletteColors.PANEL];
            text = bases[PaletteColors.TEXT];
            muted = bases[PaletteColors.MUTED];
            accent = bases[PaletteColors.ACCENT];
            error = bases[PaletteColors.ERROR];
        }
        boolean light = Ui.luminance(window) > 0.5F;
        button = Ui.mix(window, text, 0.05F);
        section = Ui.mix(window, text, 0.03F);
        hover = Ui.mix(window, text, 0.10F);
        selected = Ui.mix(window, text, 0.12F);
        line = Ui.mix(window, text, 0.16F);
        strongLine = Ui.mix(window, text, 0.30F);
        strong = Ui.mix(text, light ? Ui.SHADOW : Ui.WORLD_NEUTRAL_TINT, 0.6F);
        soft = Ui.mix(muted, window, 0.12F);
        ink = Ui.TASK_DONE_INK;
        warning = Ui.TASK_READY;
    }
}
