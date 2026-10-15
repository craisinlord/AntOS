package com.craisinlord.antos.content.guide;

import com.google.gson.JsonObject;
import java.util.Locale;

public record TaskAppearance(Shape shape, float size, float iconScale, LineAxis lineAxis, float lineBend) {
    public static final TaskAppearance DEFAULT = new TaskAppearance(Shape.SQUARE, 1.0F, 1.0F, LineAxis.AUTO, 0.5F);

    public static TaskAppearance parse(JsonObject object) {
        Shape shape = Shape.SQUARE;
        try {
            if (object.has("shape")) shape = Shape.valueOf(object.get("shape").getAsString().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ignored) { }
        LineAxis lineAxis = LineAxis.AUTO;
        try {
            if (object.has("line_axis")) lineAxis = LineAxis.valueOf(object.get("line_axis").getAsString().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ignored) { }
        return new TaskAppearance(shape, scale(object, "size", 0.5F, 3.0F), scale(object, "icon_scale", 0.25F, 2.0F), lineAxis,
                bounded(object, "line_bend", 0.0F, 1.0F, 0.5F));
    }

    public enum LineAxis { AUTO, VERTICAL, HORIZONTAL }

    private static float scale(JsonObject object, String key, float minimum, float maximum) {
        return bounded(object, key, minimum, maximum, 1.0F);
    }

    private static float bounded(JsonObject object, String key, float minimum, float maximum, float fallback) {
        try {
            if (!object.has(key) || !object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isNumber()) return fallback;
            float value = object.get(key).getAsFloat();
            return Float.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public enum Shape {
        SQUARE(4, Math.PI / 4), DIAMOND(4, 0), HEXAGON(6, 0), CIRCLE(0, 0),
        PENTAGON(5, -Math.PI / 2), OCTAGON(8, Math.PI / 8), TRIANGLE(3, -Math.PI / 2);

        private final double[][] vertices;
        private final double iconFit;

        Shape(int sides, double rotation) {
            vertices = new double[sides][2];
            double extent = 0;
            for (int i = 0; i < sides; i++) {
                double angle = rotation + i * Math.PI * 2 / sides;
                vertices[i][0] = Math.cos(angle);
                vertices[i][1] = Math.sin(angle);
                extent = Math.max(extent, Math.max(Math.abs(vertices[i][0]), Math.abs(vertices[i][1])));
            }
            for (double[] vertex : vertices) {
                vertex[0] = 0.5 + vertex[0] * 0.5 / extent;
                vertex[1] = 0.5 + vertex[1] * 0.5 / extent;
            }
            double radius = 0.5;
            for (int i = 0; i < sides; i++) {
                double[] a = vertices[i], b = vertices[(i + 1) % sides];
                double dx = b[0] - a[0], dy = b[1] - a[1];
                radius = Math.min(radius, (dx * (0.5 - a[1]) - dy * (0.5 - a[0])) / (Math.abs(dx) + Math.abs(dy)));
            }
            iconFit = sides == 0 ? Math.sqrt(0.5) : radius * 2;
        }

        public double edge(double y, boolean right) {
            if (y < 0 || y > 1) return 0.5;
            if (this == SQUARE) return right ? 1 : 0;
            if (this == CIRCLE) {
                double extent = Math.sqrt(Math.max(0, 0.25 - (y - 0.5) * (y - 0.5)));
                return 0.5 + (right ? extent : -extent);
            }
            double edge = right ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
            for (int i = 0; i < vertices.length; i++) {
                double[] a = vertices[i], b = vertices[(i + 1) % vertices.length];
                if (Math.abs(a[1] - b[1]) < 1.0E-9) continue;
                if (y < Math.min(a[1], b[1]) || y > Math.max(a[1], b[1])) continue;
                double x = a[0] + (y - a[1]) * (b[0] - a[0]) / (b[1] - a[1]);
                edge = right ? Math.max(edge, x) : Math.min(edge, x);
            }
            return Double.isFinite(edge) ? edge : 0.5;
        }

        public double edgeVertical(double x, boolean bottom) {
            if (x < 0 || x > 1) return 0.5;
            if (this == SQUARE) return bottom ? 1 : 0;
            if (this == CIRCLE) {
                double extent = Math.sqrt(Math.max(0, 0.25 - (x - 0.5) * (x - 0.5)));
                return 0.5 + (bottom ? extent : -extent);
            }
            double edge = bottom ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
            for (int i = 0; i < vertices.length; i++) {
                double[] a = vertices[i], b = vertices[(i + 1) % vertices.length];
                if (Math.abs(a[0] - b[0]) < 1.0E-9) continue;
                if (x < Math.min(a[0], b[0]) || x > Math.max(a[0], b[0])) continue;
                double y = a[1] + (x - a[0]) * (b[1] - a[1]) / (b[0] - a[0]);
                edge = bottom ? Math.max(edge, y) : Math.min(edge, y);
            }
            return Double.isFinite(edge) ? edge : 0.5;
        }

        public boolean contains(double x, double y) {
            if (this == CIRCLE) return (x - 0.5) * (x - 0.5) + (y - 0.5) * (y - 0.5) <= 0.25 + 1e-9;
            for (int i = 0; i < vertices.length; i++) {
                double[] a = vertices[i], b = vertices[(i + 1) % vertices.length];
                if ((b[0] - a[0]) * (y - a[1]) - (b[1] - a[1]) * (x - a[0]) < -1e-9) return false;
            }
            return true;
        }

        public double iconFit() {
            return iconFit;
        }

        public int sides() { return vertices.length; }

        public double vertexX(int index) { return vertices[index][0]; }

        public double vertexY(int index) { return vertices[index][1]; }
    }
}
