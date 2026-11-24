package com.craisinlord.antos.content.client.studio;

abstract class StudioPopup {
    int x;
    int y;
    int w;
    int h;

    abstract void layout(StudioKit kit);
    abstract void render(StudioKit kit);
    boolean shade() { return false; }
    boolean key(int key, int modifiers) { return false; }
    void closed() { }

    void near(StudioKit kit, int anchorX, int anchorY, int anchorH, int width, int height) {
        w = Math.min(width, kit.width - 8);
        h = Math.min(height, kit.height - 8);
        x = Math.max(4, Math.min(kit.width - w - 4, anchorX));
        int below = kit.height - 4 - (anchorY + anchorH + 2);
        int above = anchorY - 6;
        if (h <= below || below >= above) {
            y = anchorY + anchorH + 2;
            if (h > below) y = Math.max(4, kit.height - 4 - h);
        } else y = Math.max(4, anchorY - 2 - h);
    }

    void center(StudioKit kit, int width, int height) {
        w = Math.min(width, kit.width - 16);
        h = Math.min(height, kit.height - 16);
        x = (kit.width - w) / 2;
        y = (kit.height - h) / 2;
    }
}
