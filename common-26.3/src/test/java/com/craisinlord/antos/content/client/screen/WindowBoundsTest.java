package com.craisinlord.antos.content.client.screen;

public final class WindowBoundsTest {
    public static void main(String[] args) {
        for (WindowBounds bounds : new WindowBounds[]{new WindowBounds(-108, -91, 230, 210), new WindowBounds(-206, -109, 412, 238)}) {
            double left = bounds.x() - 2;
            double top = bounds.y() - 2;
            double right = bounds.x() + bounds.width() + 2;
            double bottom = bounds.y() + bounds.height() + 2;
            for (double x : new double[]{left, left + 1, right - 1, right - 0.001}) {
                for (double y : new double[]{top, top + 1, bottom - 1, bottom - 0.001}) {
                    check(bounds.contains(x, y), "Visible frame corners own input");
                    check(!bounds.content(x, y, 22), "Frame corners cannot dispatch to app content");
                    check(!bounds.title(x, y, 22), "Frame corners cannot trigger title controls");
                }
            }
            double centerX = bounds.x() + bounds.width() / 2.0;
            double centerY = bounds.y() + bounds.height() / 2.0;
            check(bounds.contains(left, centerY), "Left edge is included");
            check(bounds.contains(right - 0.001, centerY), "Right edge is included");
            check(bounds.contains(centerX, top), "Top edge is included");
            check(bounds.contains(centerX, bottom - 0.001), "Bottom edge is included");
            check(!bounds.contains(left - 0.001, centerY), "Outside left is excluded");
            check(!bounds.contains(right, centerY), "Outside right is excluded");
            check(!bounds.contains(centerX, top - 0.001), "Outside top is excluded");
            check(!bounds.contains(centerX, bottom), "Outside bottom is excluded");
            check(bounds.title(centerX, bounds.y(), 22), "Title bar owns its top edge");
            check(!bounds.content(centerX, bounds.y() + 21.999, 22), "Title bar blocks content dispatch");
            check(bounds.content(centerX, bounds.y() + 22, 22), "Content starts below title bar");
            check(!bounds.title(centerX, bounds.y() + 22, 22), "Content cannot trigger title controls");
            for (double scale : new double[]{0.5, 0.85, 1.0, 1.15, 1.3}) {
                double screenX = 640 + (right - 0.25) * scale;
                double screenY = 360 + centerY * scale;
                check(bounds.contains((screenX - 640) / scale, (screenY - 360) / scale), "Scaled frame owns input");
            }
        }
        WindowBounds lower = new WindowBounds(0, 0, 100, 100);
        WindowBounds upper = new WindowBounds(20, 20, 100, 100);
        var windows = java.util.Arrays.asList(lower, null, upper);
        check(WindowBounds.topmost(windows, bounds -> bounds, 19, 60) == upper, "Upper left frame blocks lower content");
        check(WindowBounds.topmost(windows, bounds -> bounds, 60, 19) == upper, "Upper top frame blocks lower content");
        check(WindowBounds.topmost(windows, bounds -> bounds, 10, 10) == lower, "Exposed lower content remains reachable");
        check(WindowBounds.topmost(windows, bounds -> bounds, 60, 25) == upper, "Upper title bar blocks lower content");
        check(WindowBounds.topmost(windows, bounds -> bounds == upper ? null : bounds, 60, 60) == lower, "Minimized windows do not intercept input");
        check(WindowBounds.topmost(windows, bounds -> bounds, 200, 200) == null, "Desktop remains reachable outside windows");
        check(WindowBounds.topmost(java.util.List.of(upper, lower), bounds -> bounds, 60, 60) == lower, "Raised window takes ownership");
        System.out.println("Window bounds and overlapping-window checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
