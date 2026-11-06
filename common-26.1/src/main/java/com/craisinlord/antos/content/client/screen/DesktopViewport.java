package com.craisinlord.antos.content.client.screen;

record DesktopViewport(int width, int height, float scale, float centerX, float centerY) {
    static final int BASE_WIDTH = 440;
    static final int BASE_HEIGHT = 286;
    static final float MIN_FRAME_WIDTH = 220;
    static final float MIN_FRAME_HEIGHT = 143;
    static final float FRAME_MARGIN = 8;

    static DesktopViewport fit(int screenWidth, int screenHeight, float requestedScale, boolean fullscreen) {
        return fit(screenWidth, screenHeight, requestedScale, fullscreen, null);
    }

    static DesktopViewport fit(int screenWidth, int screenHeight, float requestedScale, boolean fullscreen, float[] frame) {
        if (fullscreen || frame == null) {
            float margin = fullscreen ? 0 : 24;
            float available = Math.max(0.1F, Math.min((screenWidth - margin) / BASE_WIDTH, (screenHeight - margin) / BASE_HEIGHT));
            float scale = Math.min(requestedScale, available);
            int width = fullscreen ? Math.max(BASE_WIDTH, (int) Math.ceil(screenWidth / scale)) : BASE_WIDTH;
            int height = fullscreen ? Math.max(BASE_HEIGHT, (int) Math.ceil(screenHeight / scale)) : BASE_HEIGHT;
            return new DesktopViewport(width, height, scale, screenWidth / 2.0F, screenHeight / 2.0F);
        }
        float[] rect = clampFrame(frame, screenWidth, screenHeight);
        float scale = Math.min(requestedScale, Math.max(0.1F, Math.min(rect[2] / BASE_WIDTH, rect[3] / BASE_HEIGHT)));
        int width = Math.max(BASE_WIDTH, (int) Math.floor(rect[2] / scale));
        int height = Math.max(BASE_HEIGHT, (int) Math.floor(rect[3] / scale));
        return new DesktopViewport(width, height, scale, rect[0] + rect[2] / 2.0F, rect[1] + rect[3] / 2.0F);
    }

    static float[] clampFrame(float[] frame, int screenWidth, int screenHeight) {
        float maxWidth = Math.max(1, screenWidth - FRAME_MARGIN * 2);
        float maxHeight = Math.max(1, screenHeight - FRAME_MARGIN * 2);
        float width = Math.min(maxWidth, Math.max(Math.min(MIN_FRAME_WIDTH, maxWidth), frame[2]));
        float height = Math.min(maxHeight, Math.max(Math.min(MIN_FRAME_HEIGHT, maxHeight), frame[3]));
        float left = Math.max(FRAME_MARGIN, Math.min(screenWidth - FRAME_MARGIN - width, frame[0]));
        float top = Math.max(FRAME_MARGIN, Math.min(screenHeight - FRAME_MARGIN - height, frame[1]));
        return new float[] {left, top, width, height};
    }
}
