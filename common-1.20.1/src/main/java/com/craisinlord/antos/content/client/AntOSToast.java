package com.craisinlord.antos.content.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class AntOSToast implements Toast {
    private static final int WIDTH = 170;
    private static final long DURATION = 5000L;
    private final Component title;
    private final List<FormattedCharSequence> lines;

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
    public Visibility render(GuiGraphics graphics, ToastComponent component, long time) {
        Font font = Minecraft.getInstance().font;
        int green = ComputerTheme.remap(0xFF65FF65);
        int pale = ComputerTheme.remap(0xFFB8FFB8);
        int height = height();
        graphics.fill(0, 0, WIDTH, height, ComputerTheme.remap(0xF0030603));
        graphics.fill(0, 0, WIDTH, 1, green);
        graphics.fill(0, height - 1, WIDTH, height, green);
        graphics.fill(0, 0, 1, height, green);
        graphics.fill(WIDTH - 1, 0, WIDTH, height, green);
        graphics.fill(1, 1, 4, height - 1, ComputerTheme.remap(0xFF173817));
        graphics.drawString(font, title, 9, 6, green, false);
        int y = 18;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, 9, y, pale, false);
            y += 10;
        }
        long elapsed = Math.min(DURATION, time);
        int progress = (int) ((WIDTH - 2) * (1.0 - elapsed / (double) DURATION));
        graphics.fill(1, height - 2, 1 + progress, height - 1, ComputerTheme.remap(0xFF315531));
        return time >= DURATION ? Visibility.HIDE : Visibility.SHOW;
    }
}
