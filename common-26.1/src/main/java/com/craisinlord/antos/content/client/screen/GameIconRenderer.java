package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.api.client.game.GameIcon;
import java.util.HashSet;
import java.util.Set;
import com.craisinlord.antos.content.client.ThemedGraphics;

final class GameIconRenderer {
    private static final Set<Object> FAILED = new HashSet<>();

    private GameIconRenderer() {
    }

    static void render(ComputerScreen screen, ThemedGraphics g, GameIcon icon, int x, int y, int size, int ticks, boolean hovered) {
        if (icon == null || !draw(screen, g, icon, x, y, size, ticks, hovered)) draw(screen, g, GameIcon.DEFAULT, x, y, size, ticks, hovered);
    }

    private static boolean draw(ComputerScreen screen, ThemedGraphics g, GameIcon icon, int x, int y, int size, int ticks, boolean hovered) {
        if (icon instanceof GameIcon.Item item) return asset(screen, g, item.itemId().toString(), "", item.greenTint(), x, y, size);
        if (icon instanceof GameIcon.Entity entity) return asset(screen, g, "", entity.entityId().toString(), entity.greenTint(), x, y, size);
        if (icon instanceof GameIcon.Sprite sprite) {
            PixelIcons.draw(g, sprite.texture(), x, y, size);
            return true;
        }
        if (icon instanceof GameIcon.Texture texture) {
            int source = Math.max(texture.width(), texture.height());
            int drawn = size >= source ? size / source * source : size;
            float scale = drawn / (float) source;
            g.pose().pushPose();
            try {
                g.pose().translate(x + (size - texture.width() * scale) / 2.0F, y + (size - texture.height() * scale) / 2.0F, 0.0F);
                g.pose().scale(scale, scale, 1.0F);
                g.blit(texture.texture(), 0, 0, texture.width(), texture.height(), 0, 0, texture.width(), texture.height(), texture.width(), texture.height());
            } finally {
                g.pose().popPose();
            }
            return true;
        }
        if (icon instanceof GameIcon.Custom custom) {
            if (FAILED.contains(custom)) return false;
            g.pose().pushPose();
            try {
                custom.painter().paint(g, x, y, size, ticks, hovered);
                return true;
            } catch (RuntimeException exception) {
                FAILED.add(custom);
                AntOS.LOGGER.warn("Game icon painter failed", exception);
                return false;
            } finally {
                g.pose().popPose();
            }
        }
        if (icon instanceof GameIcon.Layered layered) {
            render(screen, g, layered.base(), x, y, size, ticks, hovered);
            int badge = Math.max(8, size * 5 / 9);
            int overhang = Math.max(1, size / 12);
            g.pose().pushPose();
            try {
                g.pose().translate(0.0F, 0.0F, 200.0F);
                render(screen, g, layered.badge(), x + size - badge + overhang, y + size - badge + overhang, badge, ticks, hovered);
            } finally {
                g.pose().popPose();
            }
            return true;
        }
        return false;
    }

    private static boolean asset(ComputerScreen screen, ThemedGraphics g, String itemId, String entityId, boolean greenTint, int x, int y, int size) {
        boolean previousTint = screen.archiveGreenTint;
        screen.archiveGreenTint = greenTint;
        try {
            int base = Math.min(32, Math.max(16, size - 4));
            int target = size < 20 ? size : size - 4;
            return screen.renderArchiveAsset(g, itemId, entityId, "", "", x + size / 2, y + size / 2, size, 0.0F, 1.0F, false, target / (float) base);
        } finally {
            screen.archiveGreenTint = previousTint;
        }
    }
}
