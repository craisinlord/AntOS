package com.craisinlord.antos.content.client.screen;

record DesktopIconLayout(int x, int y, float scale) {
    static DesktopIconLayout at(int width, int height, int index, boolean fullscreen) {
        if (!fullscreen) return new DesktopIconLayout(28 + index % 4 * 104, 48 + index / 4 * 78, 1);
        float cellWidth = (width - 24) / 4.0F;
        float cellHeight = (height - 44) / 3.0F;
        float scale = Math.max(1, Math.min(cellWidth / 104, cellHeight / 78));
        int x = Math.round(12 + (index % 4 + 0.5F) * cellWidth - 34 * scale);
        int y = Math.round(36 + (index / 4 + 0.5F) * cellHeight - 22.5F * scale);
        return new DesktopIconLayout(x, y, scale);
    }

    boolean contains(double mouseX, double mouseY) {
        double localX = (mouseX - x) / scale;
        double localY = (mouseY - y) / scale;
        return localX >= -8 && localX < 76 && localY >= -8 && localY < 53;
    }
}
