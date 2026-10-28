package com.craisinlord.antos.api.client.game;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public interface ComputerGame {
    ResourceLocation id();

    ResourceLocation diskId();

    default boolean includedByDefault() { return false; }

    String title();

    default GameIcon icon() { return GameIcon.DEFAULT; }

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
