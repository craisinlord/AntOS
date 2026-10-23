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
        System.out.println("Desktop viewport checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
