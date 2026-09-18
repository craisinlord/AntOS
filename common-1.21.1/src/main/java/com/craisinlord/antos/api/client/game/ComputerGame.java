package com.craisinlord.antos.api.client.game;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** Public client API for games displayed in the AntOS Games app. */
public interface ComputerGame {
    /** Stable game ID, namespaced by the registering mod. */
    ResourceLocation id();

    /** The AntOS floppy disk ID that installs this game. */
    ResourceLocation diskId();

    /** Display name shown in the Games app. */
    String title();

    /** Creates per-open-game state for this computer. */
    Session create(BlockPos computerPosition);

    interface Session {
        default void tick() {}
        void render(GuiGraphics graphics, Font font, int x, int y, int width, int height);
        default boolean keyPressed(int keyCode) { return false; }
        default boolean charTyped(char codePoint) { return false; }
        default void close() {}
    }
}
