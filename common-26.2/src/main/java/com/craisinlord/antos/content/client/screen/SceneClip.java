package com.craisinlord.antos.content.client.screen;

final class SceneClip {
    private SceneClip() { }

    /** Converts a clip rectangle from document root space into the space of a pose at (originX, originY) with the given scale. */
    static int[] toPose(int left, int top, int right, int bottom, float originX, float originY, float scale) {
        int localLeft = (int) Math.floor((left - originX) / scale);
        int localTop = (int) Math.floor((top - originY) / scale);
        int localRight = (int) Math.ceil((right - originX) / scale);
        int localBottom = (int) Math.ceil((bottom - originY) / scale);
        return new int[] {localLeft, localTop, Math.max(localLeft, localRight), Math.max(localTop, localBottom)};
    }
}
