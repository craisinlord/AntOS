package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.client.screen.ComputerScreen.Window;
import com.craisinlord.antos.api.client.game.ComputerGame;
import com.craisinlord.antos.api.client.game.ComputerGameRegistry;
import com.craisinlord.antos.content.guide.ComputerGuideData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import static com.craisinlord.antos.content.client.screen.ComputerScreen.GREEN;
import static com.craisinlord.antos.content.client.screen.ComputerScreen.PALE_GREEN;

final class GamesApp extends ComputerApp {

    private final Map<ResourceLocation, ComputerGame.Session> gameSessions = new HashMap<>();

    void render(GuiGraphics g, Window window, int x, int y, int mouseX, int mouseY) {
        g.drawString(font, Component.literal("INSTALLED GAMES"), x, y, GREEN, false);
        if (window.gameOpen) {
            ComputerGame.Session game = gameSession(window).orElse(null);
            if (game != null) game.render(g, font, x, y + 16, 214, 155);
            return;
        }
        List<ComputerGame> games = installedGames();
        for (int index = 0; index < games.size(); index++) {
            int rowTop = y + 18 + index * 16;
            if (screen.inside(x - 4, rowTop, 218, 16, mouseX, mouseY)) screen.drawHover(g, x - 4, rowTop, 218, 16);
            g.drawString(font, Component.literal(games.get(index).title() + "  [ ENTER ]"), x, y + 22 + index * 16, PALE_GREEN, false);
        }
        if (games.isEmpty()) {
            g.drawString(font, Component.literal("NO GAMES INSTALLED"), x, y + 22, PALE_GREEN, false);
            g.drawString(font, Component.literal(screen.trimToWidth("FIND GAME DISKS TO INSTALL PROGRAMS", 214)), x, y + 40, PALE_GREEN, false);
        }
    }

    private List<ComputerGame> installedGames() {
        List<ResourceLocation> disks = screen.physicalDisks();
        if (ComputerGuideData.unlockAllGameEntries()) return ComputerGameRegistry.all().stream().toList();
        return ComputerGameRegistry.all().stream().filter(game -> game.includedByDefault() || game.diskId() != null && disks.contains(game.diskId())).toList();
    }

    void tick(Window window) {
        if (window.gameOpen) gameSession(window).ifPresent(ComputerGame.Session::tick);
    }

    java.util.Optional<ComputerGame.Session> gameSession(Window window) {
        if (window == null || window.gameId == null) return java.util.Optional.empty();
        ResourceLocation id = ResourceLocation.tryParse(window.gameId);
        ComputerGame game = id == null ? null : ComputerGameRegistry.get(id);
        if (game == null) return java.util.Optional.empty();
        return java.util.Optional.of(gameSessions.computeIfAbsent(id, ignored -> game.create()));
    }

    boolean click(Window window, double mouseX, double mouseY, int button, int x, int y, int w, int h) {
        int contentX = x + 8;
        int contentY = y + 28;
        int contentW = w - 16;
        int contentH = h - 34;
        if (window.gameOpen) return true;
        List<ComputerGame> games = installedGames();
        int index = ((int) mouseY - (contentY + 18)) / 16;
        if (index >= 0 && index < games.size() && mouseY < contentY + 18 + games.size() * 16) {
            ComputerGame game = games.get(index);
            window.gameId = game.id().toString();
            window.gameOpen = true;
            gameSessions.computeIfAbsent(game.id(), ignored -> game.create());
            screen.activeWindow = window;
            return true;
        }
        return true;
    }

    boolean type(Window window, char codePoint, int modifiers) {
        if (window.gameOpen && gameSession(window).map(game -> game.charTyped(codePoint)).orElse(false)) return true;
        return false;
    }

    boolean key(Window window, int keyCode, int scanCode, int modifiers) {
        if (window.gameOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                gameSession(window).ifPresent(ComputerGame.Session::close);
                gameSessions.remove(ResourceLocation.tryParse(window.gameId));
                window.gameOpen = false;
                return true;
            }
            if (gameSession(window).map(game -> game.keyPressed(keyCode)).orElse(false)) return true;
        }
        return false;
    }
}
