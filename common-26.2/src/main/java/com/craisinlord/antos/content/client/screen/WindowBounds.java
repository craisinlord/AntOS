package com.craisinlord.antos.content.client.screen;

record WindowBounds(int x, int y, int width, int height) {
    static final int FRAME = 2;

    static <T> T topmost(java.util.List<T> windows, java.util.function.Function<T, WindowBounds> visibleBounds, double mouseX, double mouseY) {
        for (int index = windows.size() - 1; index >= 0; index--) {
            T window = windows.get(index);
            WindowBounds bounds = visibleBounds.apply(window);
            if (bounds != null && bounds.contains(mouseX, mouseY)) return window;
        }
        return null;
    }

    boolean contains(double mouseX, double mouseY) {
        return inside(x - FRAME, y - FRAME, width + FRAME * 2, height + FRAME * 2, mouseX, mouseY);
    }

    boolean title(double mouseX, double mouseY, int titleHeight) {
        return inside(x, y, width, titleHeight, mouseX, mouseY);
    }

    boolean content(double mouseX, double mouseY, int titleHeight) {
        return inside(x, y + titleHeight, width, height - titleHeight, mouseX, mouseY);
    }

    private static boolean inside(int x, int y, int width, int height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
