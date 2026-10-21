package com.craisinlord.antos.content.client;

import com.craisinlord.antos.content.client.palette.Ui;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class AntOSToast implements Toast {
    private static final int WIDTH = 170;
    private static final long DURATION = 5000L;
    private final Component title;
    private final List<FormattedCharSequence> lines;
    private Visibility visibility = Visibility.SHOW;

    public AntOSToast(Component title, Component message) {
        this.title = title;
        Font font = Minecraft.getInstance().font;
        List<FormattedCharSequence> split = font.split(message, WIDTH - 16);
        this.lines = split.subList(0, Math.min(2, split.size()));
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        return 22 + lines.size() * 10;
    }

    @Override
    public Visibility getWantedVisibility() {
        return visibility;
    }

    @Override
    public void update(ToastManager manager, long time) {
        visibility = time >= DURATION ? Visibility.HIDE : Visibility.SHOW;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long time) {
        Ui.refresh();
        int border = Ui.BORDER_STRONG;
        int green = Ui.ACCENT;
        int pale = Ui.TEXT;
        int height = height();
        graphics.fill(0, 0, WIDTH, height, Ui.alpha(Ui.BACKGROUND, 0xF0));
        graphics.fill(0, 0, WIDTH, 1, border);
        graphics.fill(0, height - 1, WIDTH, height, border);
        graphics.fill(0, 0, 1, height, border);
        graphics.fill(WIDTH - 1, 0, WIDTH, height, border);
        graphics.fill(1, 1, 4, height - 1, Ui.HOVER);
        graphics.text(font, title, 9, 6, green, false);
        int y = 18;
        for (FormattedCharSequence line : lines) {
            graphics.text(font, line, 9, y, pale, false);
            y += 10;
        }
        long elapsed = Math.min(DURATION, time);
        int progress = (int) ((WIDTH - 2) * (1.0 - elapsed / (double) DURATION));
        graphics.fill(1, height - 2, 1 + progress, height - 1, Ui.LINE);
    }
}
