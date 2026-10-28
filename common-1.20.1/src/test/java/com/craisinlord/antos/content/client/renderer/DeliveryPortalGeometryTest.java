package com.craisinlord.antos.content.client.renderer;

import java.util.ArrayList;

public final class DeliveryPortalGeometryTest {
    private record Point(float x, float y, float z) { }

    public static void main(String[] args) {
        var points = new ArrayList<Point>();
        float radius = 0.9F;
        DeliveryPortalGeometry.emit(radius, 2.5F, (x, y, z) -> points.add(new Point(x, y, z)));
        check(points.size() == 256, "portal emits complete quads for both faces");
        for (var point : points) {
            check(point.y() == 2.5F, "portal stays in its fixed ceiling plane");
            check(Math.hypot(point.x(), point.z()) <= radius + 0.00001D, "mesh stays inside checked clearance");
        }
        int front = 0;
        int back = 0;
        double area = 0;
        for (int i = 0; i < points.size(); i += 4) {
            var center = points.get(i);
            var first = points.get(i + 1);
            var second = points.get(i + 2);
            check(center.equals(points.get(i + 3)), "triangle fan has a degenerate fourth vertex");
            double cross = first.x() * second.z() - second.x() * first.z();
            check(Math.abs(cross) > 0.00001D, "each sector has visible area");
            if (cross > 0) front++;
            else back++;
            area += Math.abs(cross) / 2.0D;
        }
        check(front == 32 && back == 32, "portal is visible from above and below with face culling");
        check(Math.abs(area / 2.0D - Math.PI * radius * radius) < 0.02D, "sectors form a complete circular surface");
        System.out.println("Delivery portal geometry tests passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
