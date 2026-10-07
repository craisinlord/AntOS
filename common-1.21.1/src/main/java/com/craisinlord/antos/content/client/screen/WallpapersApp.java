package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.client.screen.ComputerScreen.Window;
import com.craisinlord.antos.content.computer.ComputerDesktopState;
import com.craisinlord.antos.content.guide.ComputerGuideData;
import com.craisinlord.antos.content.network.ComputerNetworking;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import static com.craisinlord.antos.content.client.screen.ComputerScreen.GREEN;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.PALE_GREEN;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.HOVER_FILL;

final class WallpapersApp extends ComputerApp {
    final State state = new State();

    void render(GuiGraphics g, int x, int y, int h) {
        List<ComputerGuideData.Wallpaper> available = unlockedWallpapers();
        g.drawString(font, Component.literal("INSERT A FLOPPY TO UNLOCK MORE"), x, y, GREEN, false);
        int visible = Math.max(1, (h - 34) / 38);
        int maxScroll = Math.max(0, available.size() - visible);
        state.scroll = Math.max(0, Math.min(state.scroll, maxScroll));
        for (int row = 0; row < visible && state.scroll + row < available.size(); row++) {
            ComputerGuideData.Wallpaper wallpaper = available.get(state.scroll + row);
            int rowY = y + 18 + row * 38;
            boolean selected = wallpaper.id().toString().equals(screen.session.wallpaperId);
            if (selected || screen.hovered(x, rowY - 2, 214, 36)) g.fill(x, rowY - 2, x + 214, rowY + 34, HOVER_FILL);
            screen.box(g, x, rowY - 2, x + 214, rowY + 34, selected ? PALE_GREEN : GREEN);
            drawWallpaperPreview(g, x + 5, rowY + 3, 48, 26, wallpaper.id().toString());
            String name = Component.translatable(wallpaper.titleKey()).getString();
            g.drawString(font, Component.literal(screen.trimToWidth(name, 150)), x + 60, rowY + 4, selected ? PALE_GREEN : GREEN, false);
            g.drawString(font, Component.literal(selected ? "SELECTED" : "CLICK TO APPLY"), x + 60, rowY + 18, PALE_GREEN, false);
        }
        if (available.isEmpty()) g.drawString(font, Component.literal("NO WALLPAPERS AVAILABLE"), x, y + 24, PALE_GREEN, false);
    }

    private List<ComputerGuideData.Wallpaper> unlockedWallpapers() {
        Set<String> unlocked = new HashSet<>(screen.session.wallpapers);
        unlocked.add(ComputerDesktopState.DEFAULT_WALLPAPER.toString());
        return ComputerGuideData.wallpapers().stream().filter(wallpaper -> unlocked.contains(wallpaper.id().toString())).toList();
    }

    private void drawWallpaperPreview(GuiGraphics g, int x, int y, int w, int h, String id) {
        try {
            ComputerGuideData.Wallpaper wallpaper = ComputerGuideData.wallpaper(ResourceLocation.parse(id));
            if (wallpaper != null && !wallpaper.textureId().isBlank() && screen.drawWallpaperTexture(g, wallpaper, x, y, w, h)) return;
        } catch (RuntimeException ignored) {
        }
        int hash = id.hashCode();
        int green = 24 + Math.floorMod(hash, 40);
        int blue = 12 + Math.floorMod(hash >>> 8, 24);
        int base = 0xFF000000 | (green / 2 << 16) | (green << 8) | blue;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, GREEN);
        g.fill(x, y, x + w, y + h, base);
        int accent = 0xFF000000 | (Math.min(120, green + 34) << 8) | Math.min(80, blue + 34);
        for (int py = y + 4; py < y + h; py += 9) {
            for (int px = x + 4; px < x + w; px += 12) {
                if (Math.floorMod(px + py + hash, 3) == 0) g.fill(px, py, Math.min(x + w, px + 3), Math.min(y + h, py + 2), accent);
            }
        }
    }

    boolean click(Window window, double mouseX, double mouseY, int button, int x, int y, int w, int h) {
        int contentX = x + 8;
        int contentY = y + 28;
        int contentW = w - 16;
        int contentH = h - 34;
        List<ComputerGuideData.Wallpaper> available = unlockedWallpapers();
        int visible = Math.max(1, (contentH - 34) / 38);
        int row = ((int) mouseY - (contentY + 18)) / 38;
        int index = state.scroll + row;
        if (row >= 0 && row < visible && index < available.size()) {
            ComputerGuideData.Wallpaper selected = available.get(index);
            ComputerNetworking.selectWallpaper(selected.id());
        }
        return true;
    }

    boolean scroll(Window window, double mouseX, double mouseY, double scrollY) {
        int visible = Math.max(1, (window.renderHeight - 68) / 38);
        int maximum = Math.max(0, unlockedWallpapers().size() - visible);
        state.scroll = Math.max(0, Math.min(maximum, state.scroll - (int) Math.signum(scrollY)));
        return true;
    }

    static final class State {
        int scroll;
    }
}
