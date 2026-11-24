package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.content.client.ArchiveRecipeClientState;
import com.craisinlord.antos.content.client.AuthoringClientState;
import com.craisinlord.antos.content.computer.ArchiveRecipeData;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import com.craisinlord.antos.content.client.ThemedGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class StudioAssets {
    private final Set<String> asked = new HashSet<>();
    private ComputerScreen host;
    private long nextLookup;
    private long recipeRevision = Long.MIN_VALUE;

    public void frame(int width, int height, float scale, int mouseX, int mouseY) {
        if (host == null) host = new ComputerScreen(true);
        if (host.width != width || host.height != height) host.init(width, height);
        long revision = AuthoringClientState.reloadRevision();
        if (recipeRevision != revision) {
            recipeRevision = revision;
            asked.clear();
            ArchiveRecipeClientState.clear();
        }
        host.scissorOrigin = new float[]{0.0F, 0.0F, scale};
        host.session.mouseX = mouseX;
        host.session.mouseY = mouseY;
        host.displays.beginFrame();
    }

    public boolean asset(ThemedGraphics g, String item, String entity, String enchantment, String potion, int centerX, int centerY, int size,
                         float rotation, float scale, boolean eggs, float preview) {
        boolean tint = host.archiveGreenTint;
        host.archiveGreenTint = false;
        try { return host.renderArchiveAsset(g, item, entity, enchantment, potion, centerX, centerY, size, rotation, scale, eggs, preview); }
        finally { host.archiveGreenTint = tint; }
    }

    public boolean recipe(ThemedGraphics g, String id, int x, int y, int width) {
        boolean tint = host.archiveGreenTint;
        host.archiveGreenTint = false;
        try { return host.renderArchiveRecipe(g, id, x, y, width, 16); }
        finally { host.archiveGreenTint = tint; }
    }

    public ItemStack recipeResult(String id) {
        Minecraft minecraft = Minecraft.getInstance();
        if (host == null || minecraft.level == null) return ItemStack.EMPTY;
        if (minecraft.getSingleplayerServer() == null && !asked.contains(id)) {
            long now = System.currentTimeMillis();
            if (now < nextLookup) return ItemStack.EMPTY;
            nextLookup = now + 50L;
            asked.add(id);
        }
        try {
            ArchiveRecipeData recipe = host.resolveArchiveRecipe(id);
            return recipe == null || recipe.results().isEmpty() ? ItemStack.EMPTY : recipe.results().get(0);
        } catch (RuntimeException exception) { return ItemStack.EMPTY; }
    }

    public int display(ThemedGraphics g, String id, int x, int y, int width, int clipTop, int clipBottom) {
        return host.displays.render(g, id, "studio/" + id, x, y, width, clipTop, clipBottom);
    }

    public void close() {
        if (host != null) host.displays.close();
    }

    public static void icon(ThemedGraphics g, Identifier texture, int x, int y, int size) {
        PixelIcons.draw(g, texture, x, y, size);
    }

    public static void clear() {
        PixelIcons.clear();
    }
}
