package com.craisinlord.antos.api.client.game;

import net.minecraft.client.gui.Font;
import com.craisinlord.antos.content.client.ThemedGraphics;
import net.minecraft.resources.Identifier;

/** Public client API for games displayed in the AntOS Games app. */
public interface ComputerGame {
    /** Stable game ID, namespaced by the registering mod. */
    Identifier id();

    Identifier diskId();

    default boolean includedByDefault() { return false; }

    /** Display name shown in the Games app. */
    String title();

    /** Creates per-open-game state for this computer. */
    Session create();

    interface Session {
        default void tick() {}
        void render(ThemedGraphics graphics, Font font, int x, int y, int width, int height);
        default boolean keyPressed(int keyCode) { return false; }
        default boolean charTyped(char codePoint) { return false; }
        default void close() {}
    }
}
