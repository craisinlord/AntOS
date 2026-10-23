package com.craisinlord.antos.content.client.screen;

record DesktopViewport(int width, int height, float scale) {
    static DesktopViewport fit(int screenWidth, int screenHeight, float requestedScale, boolean fullscreen) {
        float margin = fullscreen ? 0 : 24;
        float available = Math.max(0.1F, Math.min((screenWidth - margin) / 440, (screenHeight - margin) / 286));
        float scale = Math.min(requestedScale, available);
        int width = fullscreen ? Math.max(440, (int) Math.ceil(screenWidth / scale)) : 440;
        int height = fullscreen ? Math.max(286, (int) Math.ceil(screenHeight / scale)) : 286;
        return new DesktopViewport(width, height, scale);
    }
}
