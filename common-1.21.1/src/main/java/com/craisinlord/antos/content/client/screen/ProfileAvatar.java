package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.client.palette.Ui;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class ProfileAvatar {
    private ProfileAvatar() { }

    static void draw(ComputerApp app, GuiGraphics g, String identity, String itemId, String entityId, int x, int y, int size) {
        g.fill(x, y, x + size, y + size, Ui.SURFACE);
        boolean previousTint = app.screen.archiveGreenTint;
        app.screen.archiveGreenTint = false;
        boolean rendered = false;
        try {
            rendered = app.screen.renderArchiveAsset(g, itemId, entityId, "", "", x + size / 2, y + size / 2, size, 0.0F, 0.9F);
        } finally {
            app.screen.archiveGreenTint = previousTint;
        }
        if (rendered) {
            app.screen.box(g, x, y, x + size, y + size, Ui.LINE);
            return;
        }
        int background = switch (Math.floorMod(identity.hashCode(), 4)) {
            case 0 -> Ui.HOVER;
            case 1 -> Ui.MAIL_TINT_1;
            case 2 -> Ui.MAIL_TINT_2;
            default -> Ui.MAIL_TINT_3;
        };
        g.fill(x, y, x + size, y + size, background);
        String[] parts = identity.replaceAll("[^A-Za-z0-9]+", " ").trim().split("\\s+");
        String initials = parts.length == 0 || parts[0].isBlank() ? "?" : parts[0].substring(0, Math.min(2, parts[0].length()));
        if (parts.length > 1 && !parts[1].isBlank()) initials = parts[0].substring(0, 1) + parts[1].substring(0, 1);
        g.drawCenteredString(app.font, Component.literal(initials.toUpperCase(Locale.ROOT)), x + size / 2, y + size / 2 - 4, Ui.TEXT);
        app.screen.box(g, x, y, x + size, y + size, Ui.LINE);
    }

}
