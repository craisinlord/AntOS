package com.craisinlord.antos.content.client.game;

import com.craisinlord.antos.config.AntOSSettings;
import com.craisinlord.antos.content.client.AntOSPlayerText;
import com.craisinlord.antos.content.network.ComputerNetworking;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class GameRewardTracker {
    private long seenRevision;
    private Component popup;
    private long popupUntil;

    GameRewardTracker() {
        GameRewardClientState.Update update = GameRewardClientState.get();
        seenRevision = update == null ? 0L : update.revision();
    }

    void claim(String mode) {
        ComputerNetworking.claimGameReward(mode);
    }

    void tick() {
        GameRewardClientState.Update update = GameRewardClientState.get();
        if (update == null || update.revision() == seenRevision) return;
        seenRevision = update.revision();
        if (update.error().equals("claimed")) {
            show(Component.translatable("computer.antos.games.reward_claimed"));
            return;
        }
        if (!update.error().isBlank()) return;
        String[] parts = update.data().split("[|]", 2);
        long amount = 0L;
        try { amount = parts.length > 1 ? Long.parseLong(parts[1]) : 0L; } catch (NumberFormatException ignored) { }
        if (amount > 0L) show(Component.translatable("computer.antos.games.reward", amount, AntOSPlayerText.currencyName()));
    }

    private void show(Component message) {
        popup = message;
        popupUntil = System.currentTimeMillis() + 3500L;
    }

    void render(GuiGraphics graphics, Font font, int x, int y, int width, int height) {
        if (popup == null) return;
        if (System.currentTimeMillis() >= popupUntil) {
            popup = null;
            return;
        }
        int popupWidth = Math.min(width - 8, font.width(popup) + 20);
        int popupX = x + (width - popupWidth) / 2;
        int popupY = y + (height - 24) / 2;
        graphics.fill(popupX, popupY, popupX + popupWidth, popupY + 24, 0xEE17351C);
        graphics.renderOutline(popupX, popupY, popupWidth, 24, 0xFF65FF65);
        graphics.drawCenteredString(font, popup, x + width / 2, popupY + 8, 0xFFFFFFFF);
    }

    static Component dailyBonus(String mode) {
        long reward = AntOSSettings.gameAntcoinReward(mode);
        if (reward <= 0L || !AntOSSettings.appEnabled("ANTAZON")) return Component.empty();
        return Component.translatable("computer.antos.games.daily_bonus", reward, AntOSPlayerText.currencySymbol());
    }
}
