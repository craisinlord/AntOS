package com.craisinlord.antos.content.client.screen;

import com.craisinlord.antos.AntOS;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import com.craisinlord.antos.content.client.ThemedGraphics;
import net.minecraft.resources.Identifier;

final class PixelIcons {
    static final float DESKTOP_SIZE = 24 * 1.15F;
    private static final Map<Identifier, Sprite> CACHE = new HashMap<>();
    private static final Map<String, Identifier> DESKTOP_IDS = new HashMap<>();

    private PixelIcons() {
    }

    static void clear() {
        CACHE.clear();
    }

    static void desktop(ThemedGraphics g, String type, float x, float y) {
        Identifier id = DESKTOP_IDS.computeIfAbsent(type, key -> Identifier.fromNamespaceAndPath(AntOS.MODID, "textures/gui/desktop/" + key.toLowerCase(Locale.ROOT) + ".png"));
        g.pose().pushPose();
        try {
            g.pose().translate(x, y, 0.0F);
            g.pose().scale(DESKTOP_SIZE / 24.0F, DESKTOP_SIZE / 24.0F, 1.0F);
            draw(g, id, 0, 0, 24);
        } finally {
            g.pose().popPose();
        }
    }

    static void draw(ThemedGraphics g, Identifier id, int x, int y, int size) {
        Sprite sprite = CACHE.computeIfAbsent(id, PixelIcons::load);
        if (sprite.runs().isEmpty()) {
            int inset = Math.max(1, size / 8);
            g.fill(x + inset, y + inset, x + size - inset, y + size - inset, ComputerScreen.BLACK);
            g.fill(x + inset + 2, y + inset + 2, x + size - inset - 2, y + size - inset - 2, ComputerScreen.GREEN);
            return;
        }
        boolean scaled = sprite.width() != size || sprite.height() != size;
        g.pose().pushPose();
        try {
            g.pose().translate(x, y, 0.0F);
            if (scaled) g.pose().scale(size / (float) sprite.width(), size / (float) sprite.height(), 1.0F);
            for (int[] run : sprite.runs()) g.fill(run[0], run[1], run[0] + run[2], run[1] + run[4], run[3]);
        } finally {
            g.pose().popPose();
        }
    }

    private static Sprite load(Identifier id) {
        var resource = Minecraft.getInstance().getResourceManager().getResource(id);
        if (resource.isEmpty()) return new Sprite(1, 1, List.of());
        try (InputStream input = resource.get().open(); NativeImage image = NativeImage.read(input)) {
            List<int[]> runs = new ArrayList<>();
            for (int y = 0; y < image.getHeight(); y++) {
                int start = -1;
                int color = 0;
                for (int x = 0; x <= image.getWidth(); x++) {
                    int pixel = x < image.getWidth() ? image.getPixel(x, y) : 0;
                    boolean visible = (pixel >>> 24) >= 16;
                    if (start >= 0 && (!visible || pixel != color)) {
                        runs.add(new int[]{start, y, x - start, color});
                        start = -1;
                    }
                    if (visible && start < 0) {
                        start = x;
                        color = pixel;
                    }
                }
            }
            return new Sprite(image.getWidth(), image.getHeight(), merge(runs));
        } catch (Exception exception) {
            AntOS.LOGGER.warn("Could not read icon {}", id, exception);
            return new Sprite(1, 1, List.of());
        }
    }

    private static List<int[]> merge(List<int[]> runs) {
        List<int[]> merged = new ArrayList<>();
        Map<Long, int[]> previousRow = new HashMap<>();
        Map<Long, int[]> currentRow = new HashMap<>();
        int row = Integer.MIN_VALUE;
        for (int[] run : runs) {
            if (run[1] != row) {
                Map<Long, int[]> swap = previousRow;
                previousRow = currentRow;
                currentRow = swap;
                currentRow.clear();
                if (run[1] != row + 1) previousRow.clear();
                row = run[1];
            }
            long key = ((long) run[0] << 48) ^ ((long) run[2] << 32) ^ (run[3] & 0xFFFFFFFFL);
            int[] previous = previousRow.get(key);
            if (previous != null && previous[0] == run[0] && previous[2] == run[2] && previous[3] == run[3]
                    && previous[1] + previous[4] == run[1]) {
                previous[4]++;
                currentRow.put(key, previous);
            } else {
                int[] rect = {run[0], run[1], run[2], run[3], 1};
                merged.add(rect);
                currentRow.put(key, rect);
            }
        }
        return List.copyOf(merged);
    }

    private record Sprite(int width, int height, List<int[]> runs) { }
}
