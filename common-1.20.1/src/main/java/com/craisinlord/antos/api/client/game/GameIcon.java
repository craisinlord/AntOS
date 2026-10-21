package com.craisinlord.antos.api.client.game;

import java.util.Objects;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public sealed interface GameIcon permits GameIcon.Item, GameIcon.Entity, GameIcon.Texture, GameIcon.Sprite, GameIcon.Custom, GameIcon.Layered {
    GameIcon DEFAULT = sprite(new ResourceLocation("antos", "textures/gui/desktop/games.png"));

    static GameIcon item(ResourceLocation itemId) { return new Item(itemId, false); }

    static GameIcon item(ResourceLocation itemId, boolean greenTint) { return new Item(itemId, greenTint); }

    static GameIcon entity(ResourceLocation entityId) { return new Entity(entityId, false); }

    static GameIcon entity(ResourceLocation entityId, boolean greenTint) { return new Entity(entityId, greenTint); }

    static GameIcon texture(ResourceLocation texture, int width, int height) { return new Texture(texture, width, height); }

    static GameIcon sprite(ResourceLocation texture) { return new Sprite(texture); }

    static GameIcon custom(Painter painter) { return new Custom(painter); }

    static GameIcon layered(GameIcon base, GameIcon badge) { return new Layered(base, badge); }

    @FunctionalInterface
    interface Painter {
        void paint(GuiGraphics graphics, int x, int y, int size, int ticks, boolean hovered);
    }

    record Item(ResourceLocation itemId, boolean greenTint) implements GameIcon {
        public Item { Objects.requireNonNull(itemId, "itemId"); }
    }

    record Entity(ResourceLocation entityId, boolean greenTint) implements GameIcon {
        public Entity { Objects.requireNonNull(entityId, "entityId"); }
    }

    record Texture(ResourceLocation texture, int width, int height) implements GameIcon {
        public Texture {
            Objects.requireNonNull(texture, "texture");
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("Texture size must be positive");
        }
    }

    record Sprite(ResourceLocation texture) implements GameIcon {
        public Sprite { Objects.requireNonNull(texture, "texture"); }
    }

    record Custom(Painter painter) implements GameIcon {
        public Custom { Objects.requireNonNull(painter, "painter"); }
    }

    record Layered(GameIcon base, GameIcon badge) implements GameIcon {
        public Layered {
            Objects.requireNonNull(base, "base");
            Objects.requireNonNull(badge, "badge");
        }
    }
}
