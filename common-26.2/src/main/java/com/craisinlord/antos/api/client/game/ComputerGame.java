package com.craisinlord.antos.api.client.game;

import net.minecraft.client.gui.Font;
import com.craisinlord.antos.content.client.ThemedGraphics;
import net.minecraft.resources.Identifier;

public interface ComputerGame {
    Identifier id();

    Identifier diskId();

    default boolean includedByDefault() { return false; }

    String title();

    default GameIcon icon() { return GameIcon.DEFAULT; }

    Session create();

    interface Session {
        default void tick() {}
        void render(ThemedGraphics graphics, Font font, int x, int y, int width, int height);
        default void render(ThemedGraphics graphics, Font font, int x, int y, int width, int height, int mouseX, int mouseY) { render(graphics, font, x, y, width, height); }
        default boolean keyPressed(int keyCode) { return false; }
        default boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
        default boolean charTyped(char codePoint) { return false; }
        default void close() {}
    }
}
