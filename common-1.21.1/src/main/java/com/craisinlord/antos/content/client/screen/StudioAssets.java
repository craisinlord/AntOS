package com.craisinlord.antos.content.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class StudioAssets {
    private ComputerScreen host;

    public void frame(int width, int height, float scale, int mouseX, int mouseY) {
        if (host == null) host = new ComputerScreen(true);
        if (host.width != width || host.height != height) host.init(Minecraft.getInstance(), width, height);
        host.scissorOrigin = new float[]{0.0F, 0.0F, scale};
        host.session.mouseX = mouseX;
        host.session.mouseY = mouseY;
        host.displays.beginFrame();
    }

    public boolean asset(GuiGraphics g, String item, String entity, String enchantment, String potion, int centerX, int centerY, int size,
                         float rotation, float scale, boolean eggs, float preview) {
        boolean tint = host.archiveGreenTint;
        host.archiveGreenTint = false;
        try { return host.renderArchiveAsset(g, item, entity, enchantment, potion, centerX, centerY, size, rotation, scale, eggs, preview); }
        finally { host.archiveGreenTint = tint; }
    }

    public boolean recipe(GuiGraphics g, String id, int x, int y, int width) {
        boolean tint = host.archiveGreenTint;
        host.archiveGreenTint = false;
        try { return host.renderArchiveRecipe(g, id, x, y, width, 16); }
        finally { host.archiveGreenTint = tint; }
    }

    public int display(GuiGraphics g, String id, int x, int y, int width, int clipTop, int clipBottom) {
        return host.displays.render(g, id, "studio/" + id, x, y, width, clipTop, clipBottom);
    }

    public void close() {
        if (host != null) host.displays.close();
    }

    public static void icon(GuiGraphics g, ResourceLocation texture, int x, int y, int size) {
        PixelIcons.draw(g, texture, x, y, size);
    }

    public static void clear() {
        PixelIcons.clear();
    }
}
