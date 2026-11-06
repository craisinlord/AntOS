package com.craisinlord.antos.content.client.screen;

public final class DesktopViewportTest {
    public static void main(String[] args) {
        for (int[] size : new int[][]{{854, 480}, {1920, 1080}, {3440, 1440}, {800, 1200}, {320, 180}}) {
            for (float scale : new float[]{0.5F, 1F, 1.5F}) {
                DesktopViewport windowed = DesktopViewport.fit(size[0], size[1], scale, false);
                check(windowed.width() == 440 && windowed.height() == 286, "Windowed mode retains the existing desktop");
                check(windowed.width() * windowed.scale() <= size[0] - 23, "Windowed desktop fits horizontally");
                check(windowed.height() * windowed.scale() <= size[1] - 23, "Windowed desktop fits vertically");
                DesktopViewport full = DesktopViewport.fit(size[0], size[1], scale, true);
                check(full.width() * full.scale() >= size[0] - 0.001, "Full-screen desktop covers the viewport width");
                check(full.height() * full.scale() >= size[1] - 0.001, "Full-screen desktop covers the viewport height");
                check(full.width() * full.scale() < size[0] + full.scale() + 0.001, "Full-screen width adds no stretched canvas");
                check(full.height() * full.scale() < size[1] + full.scale() + 0.001, "Full-screen height adds no stretched canvas");
                check(full.scale() <= scale, "Text never exceeds the selected scale");
            }
        }
        DesktopViewport full = DesktopViewport.fit(1920, 1080, 1, true);
        check(full.width() == 1920 && full.height() == 1080 && full.scale() == 1, "Extra space adds desktop area without enlarging text");
        check(DesktopViewport.fit(3440, 1440, 1, true).width() > full.width(), "Ultrawide displays gain usable horizontal space");
        for (int[] size : new int[][]{{440, 286}, {854, 480}, {1920, 1080}, {3440, 1440}, {800, 1200}}) {
            for (int i = 0; i < 12; i++) {
                DesktopIconLayout icon = DesktopIconLayout.at(size[0], size[1], i, true);
                check(icon.x() - 10 * icon.scale() >= 0 && icon.x() + 76 * icon.scale() <= size[0], "Scaled app tiles fit horizontally");
                check(icon.y() - 10 * icon.scale() >= 34 && icon.y() + 53 * icon.scale() <= size[1], "All app rows stay below the toolbar and inside the desktop");
                double centerX = icon.x() + 25 * icon.scale();
                double centerY = icon.y() + 20 * icon.scale();
                check(icon.contains(centerX, centerY), "Scaled icons retain their click targets");
                for (int other = 0; other < 12; other++) {
                    if (other != i) check(!DesktopIconLayout.at(size[0], size[1], other, true).contains(centerX, centerY), "Neighboring app targets remain separate");
                }
            }
        }
        check(DesktopIconLayout.at(854, 480, 0, true).scale() > 1.5F, "Full-screen app icons grow to use the extra area");
        check(DesktopIconLayout.at(1920, 1080, 0, false).equals(new DesktopIconLayout(28, 48, 1)), "Windowed app icons preserve their existing size and position");
        DesktopViewport framed = DesktopViewport.fit(1920, 1080, 1, false, new float[]{100, 50, 1200, 800});
        check(framed.width() == 1200 && framed.height() == 800 && framed.scale() == 1, "A larger frame adds desktop area at the chosen text size");
        check(framed.centerX() == 700 && framed.centerY() == 450, "The frame keeps its chosen position");
        DesktopViewport bigText = DesktopViewport.fit(1920, 1080, 1.5F, false, new float[]{100, 50, 1200, 800});
        check(bigText.width() == 800 && bigText.scale() == 1.5F, "Larger text uses fewer desktop units in the same frame");
        DesktopViewport cramped = DesktopViewport.fit(1920, 1080, 1.5F, false, new float[]{0, 0, 300, 200});
        check(cramped.width() >= 440 && cramped.height() >= 286, "Small frames never shrink the desktop below its base size");
        check(cramped.width() * cramped.scale() <= 300.5F, "Small frames scale down to fit");
        float[] clamped = DesktopViewport.clampFrame(new float[]{1800, -40, 50, 5000}, 1920, 1080);
        check(clamped[2] >= DesktopViewport.MIN_FRAME_WIDTH && clamped[3] <= 1080 - 2 * DesktopViewport.FRAME_MARGIN, "Frames respect minimum and screen sizes");
        check(clamped[0] + clamped[2] <= 1920 - DesktopViewport.FRAME_MARGIN && clamped[1] >= DesktopViewport.FRAME_MARGIN, "Frames stay on screen");
        DesktopViewport fullOverride = DesktopViewport.fit(1920, 1080, 1, true, new float[]{100, 50, 600, 400});
        check(fullOverride.width() == 1920 && fullOverride.centerX() == 960, "Full-screen ignores a saved frame");
        System.out.println("Desktop viewport checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
