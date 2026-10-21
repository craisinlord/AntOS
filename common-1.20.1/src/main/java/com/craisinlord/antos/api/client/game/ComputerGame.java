package com.craisinlord.antos.api.client.game;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Public client API for games displayed in the AntOS Games app. */
public interface ComputerGame {
    /** Stable game ID, namespaced by the registering mod. */
    ResourceLocation id();

    ResourceLocation diskId();

    default boolean includedByDefault() { return false; }

    /** Display name shown in the Games app. */
    String title();

    default GameIcon icon() { return GameIcon.DEFAULT; }

    /** Creates per-open-game state for this computer. */
    Session create();

    interface Session {
        default void tick() {}
        void render(GuiGraphics graphics, Font font, int x, int y, int width, int height);
        default void render(GuiGraphics graphics, Font font, int x, int y, int width, int height, int mouseX, int mouseY) { render(graphics, font, x, y, width, height); }
        default boolean keyPressed(int keyCode) { return false; }
        default boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
        default boolean charTyped(char codePoint) { return false; }
        default void close() {}
    }
}
