package com.craisinlord.antos.content.client.renderer;

import com.craisinlord.antos.content.client.ComputerTheme;
import com.craisinlord.antos.content.guide.TaskAppearance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;

public final class TaskShapeShaderTest {
    private static final int CANVAS = 192;
    private static final int FILL = 0xFF0A170A, BORDER = 0xFF33FF33, HOVER = 0xFF99FF99;
    private static Method points, layer, pipelineAvailable;
    private static Constructor<?> outline, geometry;

    public static void main(String[] args) throws Exception {
        points = method("points", TaskAppearance.Shape.class, float.class, float.class, float.class);
        layer = method("layer", List.class, nested("Outline"), float.class, float.class, int.class, float.class, boolean.class);
        pipelineAvailable = method("pipelineAvailable");
        outline = nested("Outline").getDeclaredConstructor(List.class);
        outline.setAccessible(true);
        geometry = nested("ShapeGeometry").getDeclaredConstructors()[0];
        geometry.setAccessible(true);
        theme(ComputerTheme.GREEN);
        check(!(boolean) pipelineAvailable.invoke(null), "smooth pipeline is not used off the render thread");
        float[] sizes = {8.8F, 17.6F, 32, 96, 153.6F};
        for (boolean smooth : new boolean[]{true, false}) {
            for (float size : sizes) {
                for (var shape : TaskAppearance.Shape.values()) {
                    for (float hover : new float[]{0, 2}) render(shape, size, hover, smooth);
                }
            }
        }
        themeRemap();
        System.out.println("Task shape geometry tests passed: all seven shapes at five sizes, with and without hover, smooth and fallback, with theme remapping");
    }

    private static void render(TaskAppearance.Shape shape, float size, float hover, boolean smooth) throws Exception {
        float left = (CANVAS - size) / 2;
        float borderWidth = Math.min(2.0F, size / 4.0F);
        Object shapeOutline = outline.newInstance(points.invoke(null, shape, left, left, size));
        List<List<Captured>> layers = new ArrayList<>();
        List<Float> edges = new ArrayList<>();
        if (hover > 0) {
            layers.add(layer(shapeOutline, -1, hover, ComputerTheme.remap(HOVER), smooth));
            edges.add(hover);
        }
        layers.add(layer(shapeOutline, -borderWidth - 1, 0, ComputerTheme.remap(BORDER), smooth));
        edges.add(0F);
        layers.add(layer(shapeOutline, -inradius(shapeOutline), -borderWidth, ComputerTheme.remap(FILL), smooth));
        edges.add(-borderWidth);
        for (int index = 0; index < layers.size(); index++) {
            List<Captured> vertices = layers.get(index);
            float edge = edges.get(index);
            check(!vertices.isEmpty() && vertices.size() % 4 == 0, "geometry is emitted as quads for " + shape);
            double tolerance = shape.sides() == 0 ? size * 0.0025 + 1e-3 : 1e-3;
            for (int quad = 0; quad < vertices.size(); quad += 4) {
                if (!smooth) check(area(vertices, quad) <= 1e-4, "fallback quads share one winding for " + shape);
                for (int corner = 0; corner < 4; corner++) {
                    Captured vertex = vertices.get(quad + corner);
                    if (smooth) {
                        double actual = distance(shape, (vertex.x - left) / size, (vertex.y - left) / size) * size;
                        check(Math.abs(vertex.u + edge - actual) < tolerance, "per-vertex distance matches geometry for " + shape + " / " + size);
                        check(vertex.u <= 1 + 1e-4, "smooth bands end one pixel beyond their edge for " + shape);
                    } else {
                        check(Float.isNaN(vertex.u), "fallback geometry carries no distance coordinates for " + shape);
                    }
                }
            }
        }
        int[] alpha = composite(layers, smooth);
        int antialiased = 0;
        for (int y = 0; y < CANVAS; y++) {
            for (int x = 0; x < CANVAS; x++) {
                int value = alpha[y * CANVAS + x];
                int mirrored = alpha[y * CANVAS + CANVAS - x - 1];
                check(Math.abs(value - mirrored) <= 2, "mirror symmetry for " + shape + " / " + size + (smooth ? "" : " fallback"));
                double distance = distance(shape, (x + 0.5 - left) / size, (y + 0.5 - left) / size) * size - hover;
                if (distance < -1.5) check(value == 255, "opaque shape interior for " + shape + " / " + size + (smooth ? "" : " fallback"));
                if (distance > 1.5) check(value == 0, "shape exterior for " + shape + " / " + size + (smooth ? "" : " fallback"));
                if (value > 0 && value < 255) antialiased++;
            }
        }
        if (shape != TaskAppearance.Shape.SQUARE) check(antialiased > 0, "antialiased edges for " + shape);
    }

    private static void themeRemap() throws Exception {
        theme(ComputerTheme.GREEN);
        check(ComputerTheme.remap(BORDER) == BORDER, "green theme keeps original colors");
        theme(ComputerTheme.AMBER);
        check(ComputerTheme.remap(BORDER) == 0xFFFFC033, "amber theme remaps green border colors");
        check(ComputerTheme.remap(0x4033FF33) == 0x40FFC033, "theme remap preserves alpha");
        check(ComputerTheme.remap(0xFFFF2020) == 0xFFFF2020, "non-green colors are not remapped");
        ComputerTheme.pause();
        check(ComputerTheme.remap(BORDER) == BORDER, "paused themes leave colors unchanged");
        ComputerTheme.resume();
        check(ComputerTheme.remap(BORDER) == 0xFFFFC033, "resumed themes remap again");
        for (boolean smooth : new boolean[]{true, false}) {
            Object shapeOutline = outline.newInstance(points.invoke(null, TaskAppearance.Shape.HEXAGON, 10F, 10F, 32F));
            for (Captured vertex : layer(shapeOutline, -3, 0, ComputerTheme.remap(BORDER), smooth)) {
                check((vertex.color & 0xFFFFFF) == 0xFFC033, "shape geometry uses the remapped theme color");
                if (smooth) check(vertex.color >>> 24 == 255, "smooth geometry keeps full vertex alpha");
            }
        }
        theme(ComputerTheme.GREEN);
    }

    private static List<Captured> layer(Object shapeOutline, float inner, float edge, int color, boolean smooth) throws Exception {
        List<Object> vertices = new ArrayList<>();
        layer.invoke(null, vertices, shapeOutline, inner, edge, color, 1F, smooth);
        var state = (GuiElementRenderState) geometry.newInstance(vertices, new Matrix3x2f(), new ScreenRectangle(0, 0, CANVAS, CANVAS), null, TaskShapeRenderer.PIPELINE, smooth);
        List<Captured> captured = new ArrayList<>();
        state.buildVertices(new Capture(captured));
        return captured;
    }

    private static int[] composite(List<List<Captured>> layers, boolean smooth) {
        float[] total = new float[CANVAS * CANVAS];
        for (List<Captured> vertices : layers) {
            float[] coverage = new float[CANVAS * CANVAS];
            boolean[] hit = new boolean[CANVAS * CANVAS];
            for (int quad = 0; quad < vertices.size(); quad += 4) {
                triangle(vertices.get(quad), vertices.get(quad + 1), vertices.get(quad + 2), coverage, hit, smooth);
                triangle(vertices.get(quad), vertices.get(quad + 2), vertices.get(quad + 3), coverage, hit, smooth);
            }
            for (int i = 0; i < total.length; i++) total[i] = coverage[i] + total[i] * (1 - coverage[i]);
        }
        int[] alpha = new int[total.length];
        for (int i = 0; i < total.length; i++) alpha[i] = Math.round(Math.max(0, Math.min(1, total[i])) * 255);
        return alpha;
    }

    private static void triangle(Captured a, Captured b, Captured c, float[] coverage, boolean[] hit, boolean smooth) {
        double area = (b.x - a.x) * (c.y - a.y) - (c.x - a.x) * (b.y - a.y);
        if (Math.abs(area) < 1e-9) return;
        double dudx = 0, dudy = 0;
        if (smooth) {
            dudx = ((b.u - a.u) * (c.y - a.y) - (c.u - a.u) * (b.y - a.y)) / area;
            dudy = ((c.u - a.u) * (b.x - a.x) - (b.u - a.u) * (c.x - a.x)) / area;
        }
        double antialias = Math.max(Math.hypot(dudx, dudy), 0.0001);
        int minX = Math.max(0, (int) Math.floor(Math.min(a.x, Math.min(b.x, c.x)))), maxX = Math.min(CANVAS - 1, (int) Math.ceil(Math.max(a.x, Math.max(b.x, c.x))));
        int minY = Math.max(0, (int) Math.floor(Math.min(a.y, Math.min(b.y, c.y)))), maxY = Math.min(CANVAS - 1, (int) Math.ceil(Math.max(a.y, Math.max(b.y, c.y))));
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                int index = y * CANVAS + x;
                if (hit[index]) continue;
                double px = x + 0.5, py = y + 0.5;
                double wa = ((b.x - px) * (c.y - py) - (c.x - px) * (b.y - py)) / area;
                double wb = ((c.x - px) * (a.y - py) - (a.x - px) * (c.y - py)) / area;
                double wc = 1 - wa - wb;
                if (wa < -1e-5 || wb < -1e-5 || wc < -1e-5) continue;
                hit[index] = true;
                double alpha = (wa * (a.color >>> 24) + wb * (b.color >>> 24) + wc * (c.color >>> 24)) / 255;
                if (smooth) {
                    double u = wa * a.u + wb * b.u + wc * c.u;
                    alpha *= 1 - smoothstep(-antialias * 0.5, antialias * 0.5, u);
                }
                coverage[index] = (float) alpha;
            }
        }
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = Math.max(0, Math.min(1, (value - edge0) / (edge1 - edge0)));
        return t * t * (3 - 2 * t);
    }

    private static double area(List<Captured> vertices, int quad) {
        double area = 0;
        for (int i = 0; i < 4; i++) {
            Captured a = vertices.get(quad + i), b = vertices.get(quad + (i + 1) % 4);
            area += a.x * b.y - b.x * a.y;
        }
        return area;
    }

    private static double distance(TaskAppearance.Shape shape, double x, double y) {
        if (shape.sides() == 0) return Math.hypot(x - 0.5, y - 0.5) - 0.5;
        double minimum = Double.POSITIVE_INFINITY;
        for (int i = 0; i < shape.sides(); i++) {
            int next = (i + 1) % shape.sides();
            double dx = shape.vertexX(next) - shape.vertexX(i), dy = shape.vertexY(next) - shape.vertexY(i);
            double px = x - shape.vertexX(i), py = y - shape.vertexY(i);
            double t = Math.max(0, Math.min(1, (px * dx + py * dy) / (dx * dx + dy * dy)));
            minimum = Math.min(minimum, Math.hypot(px - dx * t, py - dy * t));
        }
        return shape.contains(x, y) ? -minimum : minimum;
    }

    private static float inradius(Object shapeOutline) throws Exception {
        var field = shapeOutline.getClass().getDeclaredField("inradius");
        field.setAccessible(true);
        return field.getFloat(shapeOutline);
    }

    private static void theme(ComputerTheme theme) throws Exception {
        var field = ComputerTheme.class.getDeclaredField("cached");
        field.setAccessible(true);
        field.set(null, theme);
    }

    private static Method method(String name, Class<?>... parameters) throws Exception {
        Method method = TaskShapeRenderer.class.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private static Class<?> nested(String name) throws Exception {
        return Class.forName(TaskShapeRenderer.class.getName() + "$" + name);
    }

    private record Captured(float x, float y, float u, int color) { }

    private static final class Capture implements VertexConsumer {
        private final List<Captured> target;
        private float x, y, u;

        private Capture(List<Captured> target) { this.target = target; }

        @Override public VertexConsumer addVertex(float x, float y, float z) {
            this.x = x; this.y = y; this.u = Float.NaN;
            return this;
        }
        @Override public VertexConsumer setColor(int red, int green, int blue, int alpha) { return setColor(alpha << 24 | red << 16 | green << 8 | blue); }
        @Override public VertexConsumer setColor(int color) {
            target.add(new Captured(x, y, u, color));
            return this;
        }
        @Override public VertexConsumer setUv(float u, float v) { this.u = u; return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
        @Override public VertexConsumer setLineWidth(float width) { return this; }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
