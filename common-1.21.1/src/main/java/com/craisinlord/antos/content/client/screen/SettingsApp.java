package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.client.screen.ComputerScreen.Window;
import com.craisinlord.antos.content.client.AntazonClientState;
import com.craisinlord.antos.content.client.AnternetAccountClientState;
import com.craisinlord.antos.content.network.AnternetAccountNetworking;
import com.craisinlord.antos.content.network.ComputerNetworking;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import static com.craisinlord.antos.content.client.screen.ComputerScreen.GREEN;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.PALE_GREEN;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.DARK_GREEN;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.HOVER_FILL;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.WIDTH;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.HEIGHT;

final class SettingsApp extends ComputerApp {
    final State state = new State();

    Window diskScrollbarDragging;
    ResourceLocation selectedDisk;

    void drag(double mouseY) {
        if (diskScrollbarDragging != null) updateDiskScrollbar(diskScrollbarDragging, mouseY);
    }

    void release() {
        diskScrollbarDragging = null;
    }

    void render(GuiGraphics g, int x, int y, int h) {
        g.drawString(font, Component.literal("SETTINGS"), x, y, GREEN, false);
        g.drawString(font, Component.literal("SYSTEM STATUS"), x, y + 15, GREEN, false);
        drawSettingsValue(g, x, y + 27, "CPU", (System.currentTimeMillis() / 100 % 87 + 12) + "%");
        drawSettingsValue(g, x, y + 39, "MEMORY", (System.currentTimeMillis() / 250 % 42 + 31) + "%");
        g.fill(x, y + 52, x + 214, y + 53, DARK_GREEN);
        g.drawString(font, Component.literal("APPEARANCE"), x, y + 57, GREEN, false);
        drawSettingsButton(g, x, y + 69, 214, "WALLPAPER", screen.trimToWidth(screen.wallpaperName(), 110), true);
        boolean faceIdLinked = AnternetAccountClientState.faceIdLinked();
        boolean linkedToCurrentAccount = screen.faceIdLinkedToCurrentAccount();
        if (faceIdLinked && !linkedToCurrentAccount) {
            drawSettingsButton(g, x, y + 85, 214, "FACE ID", "IN USE", false);
        } else if (faceIdLinked) {
            drawSettingsButton(g, x, y + 85, 214, "FACE ID", screen.faceIdMessage.isBlank() ? "ON  [ UNLINK ]" : screen.trimToWidth(screen.faceIdMessage, 112), true);
        } else {
            drawSettingsButton(g, x, y + 85, 214, "FACE ID", screen.faceIdMessage.isBlank() ? "OFF  [ ENABLE ]" : screen.trimToWidth(screen.faceIdMessage, 112), true);
        }
        g.fill(x, y + 103, x + 214, y + 104, DARK_GREEN);
        g.drawString(font, Component.literal("HELP"), x, y + 108, GREEN, false);
        drawSettingsButton(g, x, y + 120, 104, "ANTOS", "REPLAY", true);
        drawSettingsButton(g, x + 110, y + 120, 104, "ANTAZON", "REPLAY", true);
        g.fill(x, y + 139, x + 214, y + 140, DARK_GREEN);
        g.drawString(font, Component.literal("INSTALLED DISKS"), x, y + 144, GREEN, false);
        List<ResourceLocation> disks = screen.physicalDisks();
        int maximumDiskScroll = Math.max(0, disks.size() - 3);
        state.diskScroll = Math.max(0, Math.min(state.diskScroll, maximumDiskScroll));
        if (disks.isEmpty()) {
            g.drawString(font, Component.literal("NONE INSTALLED"), x, y + 159, PALE_GREEN, false);
        } else {
            int end = Math.min(disks.size(), state.diskScroll + 3);
            for (int index = state.diskScroll; index < end; index++) {
                ResourceLocation disk = disks.get(index);
                boolean active = disk.equals(selectedDisk);
                String diskLabel = screen.trimToWidth(disk.toString(), 202);
                int rowY = y + 158 + (index - state.diskScroll) * 10;
                boolean diskHover = screen.hovered(x, rowY - 1, 206, 9);
                if (active || diskHover) g.fill(x, rowY - 1, x + 206, rowY + 8, HOVER_FILL);
                g.drawString(font, Component.literal((active ? "> " : "  ") + screen.trimToWidth(diskLabel, 194)), x, rowY, active || diskHover ? GREEN : PALE_GREEN, false);
            }
            screen.drawScrollbar(g, x + 210, y + 157, 31, 3, disks.size(), state.diskScroll, maximumDiskScroll);
        }
        drawSettingsButton(g, x + 112, y + 189, 102, "DISK", "EJECT", selectedDisk != null);
        drawSettingsButton(g, x, y + 189, 102, "ACCOUNT", "LOG OUT", true);
    }

    private void drawSettingsValue(GuiGraphics g, int x, int y, String label, String value) {
        g.drawString(font, Component.literal(label), x + 4, y, PALE_GREEN, false);
        g.drawString(font, Component.literal(value), x + 164, y, PALE_GREEN, false);
    }

    private void drawSettingsButton(GuiGraphics g, int x, int y, int width, String label, String value, boolean enabled) {
        boolean hover = enabled && screen.hovered(x, y, width, 15);
        g.fill(x, y, x + width, y + 15, hover ? HOVER_FILL : DARK_GREEN);
        screen.box(g, x, y, x + width, y + 15, enabled ? (hover ? PALE_GREEN : GREEN) : 0xFF4A754A);
        int valueWidth = font.width(value);
        g.drawString(font, Component.literal(screen.trimToWidth(label, Math.max(0, width - valueWidth - 12))), x + 4, y + 4, enabled ? PALE_GREEN : 0xFF4A754A, false);
        g.drawString(font, Component.literal(value), x + width - valueWidth - 5, y + 4, enabled ? GREEN : 0xFF4A754A, false);
    }

    private void drawSettingsHover(GuiGraphics g, String label, int x, int textY) {
        int textWidth = Math.min(font.width(label), 214);
        if (textWidth > 0 && screen.hovered(x - 2, textY - 2, textWidth + 4, 13)) {
            screen.drawHover(g, x - 2, textY - 2, textWidth + 4, 13);
        }
    }

    boolean click(Window window, double mouseX, double mouseY, int button, int x, int y, int w, int h) {
        int contentX = x + 8;
        int contentY = y + 28;
        int contentW = w - 16;
        int contentH = h - 34;
        if (screen.inside(contentX + 210, contentY + 157, 3, 31, mouseX, mouseY)) {
            diskScrollbarDragging = window;
            updateDiskScrollbar(window, mouseY);
            return true;
        }
        if (screen.inside(contentX, contentY + 157, Math.min(214, contentW), 31, mouseX, mouseY)) {
            List<ResourceLocation> disks = screen.physicalDisks();
            int index = ((int) mouseY - (contentY + 157)) / 10 + state.diskScroll;
            if (index >= state.diskScroll && index < disks.size()
                    && index < state.diskScroll + 3) selectedDisk = disks.get(index);
            return true;
        }
        if (screen.inside(contentX + 112, contentY + 189, Math.min(102, Math.max(0, contentW - 112)), 15, mouseX, mouseY)) {
            if (selectedDisk != null) ejectSelected();
            return true;
        }
        if (screen.inside(contentX, contentY + 69, Math.min(214, contentW), 15, mouseX, mouseY)) {
            screen.open("WALLPAPERS");
            return true;
        }
        if (screen.inside(contentX, contentY + 85, Math.min(214, contentW), 15, mouseX, mouseY)) {
            if (screen.faceIdWaiting) return true;
            screen.faceIdMessage = "";
            if (AnternetAccountClientState.faceIdLinked()) {
                if (!screen.faceIdLinkedToCurrentAccount()) {
                    screen.faceIdMessage = "FACE ID BELONGS TO ANOTHER ACCOUNT";
                } else {
                    screen.faceIdWaiting = true;
                    screen.faceIdExpectedLinked = false;
                    screen.faceIdRevisionAtRequest = AnternetAccountClientState.faceIdStatusRevision();
                    screen.faceIdMessage = "UNLINKING PLAYER...";
                    AnternetAccountNetworking.unlinkFaceId();
                }
            } else {
                screen.submitFaceIdLink();
            }
            return true;
        }
        if (screen.inside(contentX, contentY + 120, 104, 15, mouseX, mouseY)) {
            screen.session.windows.clear();
            screen.activeWindow = null;
            screen.session.onboardingCompleted = false;
            screen.session.onboardingStep = 0;
            return true;
        }
        if (screen.inside(contentX + 110, contentY + 120, Math.min(104, Math.max(0, contentW - 110)), 15, mouseX, mouseY)) {
            screen.session.windows.clear();
            screen.activeWindow = null;
            ComputerNetworking.resetAntazonOnboarding();
            com.craisinlord.antos.content.client.AntazonClientState.clear();
            screen.open("ANTAZON");
            return true;
        }
        if (screen.inside(contentX, contentY + 189, Math.min(102, contentW), 15, mouseX, mouseY)) {
            ComputerNetworking.logout();
            com.craisinlord.antos.content.network.AnternetAccountNetworking.logout();
            com.craisinlord.antos.content.client.AnternetAccountClientState.clear();
            AnternetAccountNetworking.checkFaceId();
            screen.loggedIn = false;
            screen.session.authenticated = false;
            screen.session.windows.clear();
            screen.session.antmail.state.pickingAttachment = false;
            screen.activeWindow = null;
            return true;
        }
        return true;
    }

    boolean scroll(Window window, double mouseX, double mouseY, double scrollY) {
        float scale = screen.uiScale();
        double localX = (mouseX - screen.width / 2.0F) / scale, localY = (mouseY - screen.height / 2.0F) / scale;
        int windowX = -WIDTH / 2 + screen.windowLocalX(window), windowY = -HEIGHT / 2 + screen.windowLocalY(window);
        int contentX = windowX + 8, contentY = windowY + 28, contentW = screen.windowWidth(window) - 16;
        if (screen.inside(contentX, contentY + 157, Math.min(214, contentW), 31, localX, localY)) {
            int maximum = Math.max(0, screen.physicalDisks().size() - 3);
            state.diskScroll = Math.max(0, Math.min(maximum,
                    state.diskScroll - (int) Math.signum(scrollY)));
            return true;
        }
        return false;
    }

    private void ejectSelected() {
        if (selectedDisk == null) return;
        ComputerNetworking.eject(selectedDisk);
        screen.loginMessage = Component.translatable("computer.antos.status.ejecting_disk").getString();
        screen.loginMessageTicks = 100;
        selectedDisk = null;
    }

    void updateDiskScrollbar(Window window, double mouseY) {
        int maximum = Math.max(0, screen.physicalDisks().size() - 3);
        if (maximum == 0) {
            state.diskScroll = 0;
            return;
        }
        int trackTop = -HEIGHT / 2 + screen.windowLocalY(window) + 28 + 157;
        double progress = Math.max(0.0, Math.min(1.0, (mouseY - trackTop) / 31.0));
        state.diskScroll = (int) Math.round(progress * maximum);
    }

    static final class State {
        int diskScroll;
    }
}
