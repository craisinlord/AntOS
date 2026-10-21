package com.craisinlord.antos.content.client.renderer;

import com.craisinlord.antos.content.client.ComputerTheme;
import com.craisinlord.antos.content.client.ThemedGraphics;
import com.craisinlord.antos.content.guide.TaskAppearance;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

public final class TaskShapeRenderer {
    public static final Identifier SHADER_ID = Identifier.fromNamespaceAndPath("antos", "core/task_shape");
    public static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("antos", "pipeline/task_shape"))
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withVertexShader(SHADER_ID)
            .withFragmentShader(SHADER_ID)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withCull(false)
            .build();
    private static final int CIRCLE_SEGMENTS = 64;
    private static final double WEDGE_STEP = Math.PI / 12.0;
    private static boolean pipelineUnavailable;

    private TaskShapeRenderer() {}

    public static void draw(ThemedGraphics graphics, TaskAppearance.Shape shape, float left, float top, float size,
                            int fill, int border, int hover, boolean hovered) {
        if (size <= 0.0F) return;
        var source = graphics.source();
        Matrix3x2fc pose = new Matrix3x2f(graphics.pose().matrix());
        ScreenRectangle scissor = currentScissor(source);
        float pixel = pixelSize(pose);
        float borderWidth = Math.min(2.0F, size / 4.0F);
        float hoverWidth = hovered ? 2.0F : 0.0F;
        boolean smooth = pipelineAvailable();
        Outline outline = new Outline(points(shape, left, top, size));
        List<Vertex> vertices = new ArrayList<>();
        if (hovered) layer(vertices, outline, -pixel, hoverWidth, hover, pixel, smooth);
        layer(vertices, outline, -borderWidth - pixel, 0.0F, border, pixel, smooth);
        layer(vertices, outline, -outline.inradius, -borderWidth, fill, pixel, smooth);
        if (vertices.isEmpty()) return;
        float padding = hoverWidth + pixel * 2.0F + 1.0F;
        int x = (int) Math.floor(left - padding);
        int y = (int) Math.floor(top - padding);
        int extent = (int) Math.ceil(size + padding * 2.0F) + 1;
        ScreenRectangle bounds = new ScreenRectangle(x, y, extent, extent).transformAxisAligned(pose);
        submit(source, new ShapeGeometry(vertices, pose, bounds, scissor, smooth ? PIPELINE : RenderPipelines.GUI, smooth));
    }

    private static boolean pipelineAvailable() {
        if (pipelineUnavailable || !RenderSystem.isOnRenderThread()) return false;
        try {
            if (RenderSystem.getCompiledPipelineNullable(PIPELINE) != null) return true;
        } catch (RuntimeException ignored) {
        }
        pipelineUnavailable = true;
        return false;
    }

    private static float pixelSize(Matrix3x2fc pose) {
        float scale = (float) Math.sqrt(Math.abs(pose.m00() * pose.m11() - pose.m01() * pose.m10()));
        int guiScale = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        return 1.0F / Math.max(0.01F, scale * guiScale);
    }

    private static void layer(List<Vertex> vertices, Outline outline, float inner, float edge, int color,
                              float pixel, boolean smooth) {
        float half = pixel * 0.5F;
        List<Sample[]> quads = new ArrayList<>();
        if (smooth) {
            outline.band(quads, inner, edge + pixel);
        } else {
            outline.band(quads, inner, edge - half);
            outline.band(quads, Math.max(inner, edge - half), edge + half);
        }
        int alpha = color >>> 24;
        int rgb = color & 0xFFFFFF;
        for (Sample[] quad : quads) {
            if (!smooth && area(quad) > 0.0F) quad = new Sample[] {quad[0], quad[3], quad[2], quad[1]};
            for (Sample sample : quad) {
                if (smooth) {
                    vertices.add(new Vertex(sample.x, sample.y, color, sample.distance - edge));
                } else {
                    float coverage = Math.max(0.0F, Math.min(1.0F, (edge + half - sample.distance) / pixel));
                    vertices.add(new Vertex(sample.x, sample.y, Math.round(alpha * coverage) << 24 | rgb, 0.0F));
                }
            }
        }
    }

    private static float area(Sample[] quad) {
        float area = 0.0F;
        for (int i = 0; i < quad.length; i++) {
            Sample a = quad[i];
            Sample b = quad[(i + 1) % quad.length];
            area += a.x * b.y - b.x * a.y;
        }
        return area;
    }

    private static List<Point> points(TaskAppearance.Shape shape, float left, float top, float size) {
        int count = shape == TaskAppearance.Shape.CIRCLE ? CIRCLE_SEGMENTS : shape.sides();
        List<Point> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double x;
            double y;
            if (shape == TaskAppearance.Shape.CIRCLE) {
                double angle = Math.PI * 2.0 * i / count;
                x = 0.5 + Math.cos(angle) * 0.5;
                y = 0.5 + Math.sin(angle) * 0.5;
            } else {
                x = shape.vertexX(i);
                y = shape.vertexY(i);
            }
            points.add(new Point(left + (float) (x * size), top + (float) (y * size)));
        }
        return points;
    }

    private static ScreenRectangle currentScissor(net.minecraft.client.gui.GuiGraphicsExtractor source) {
        try {
            try {
                return (ScreenRectangle) source.getClass().getMethod("peekScissorStack").invoke(source);
            } catch (NoSuchMethodException ignored) {
                var field = source.getClass().getDeclaredField("scissorStack");
                field.setAccessible(true);
                Object stack = field.get(source);
                var peek = stack.getClass().getMethod("peek");
                peek.setAccessible(true);
                return (ScreenRectangle) peek.invoke(stack);
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to read GUI scissor state", exception);
        }
    }

    private static void submit(net.minecraft.client.gui.GuiGraphicsExtractor source, GuiElementRenderState state) {
        try {
            try {
                source.getClass().getMethod("submitGuiElementRenderState", GuiElementRenderState.class).invoke(source, state);
            } catch (NoSuchMethodException ignored) {
                var field = source.getClass().getDeclaredField("guiRenderState");
                field.setAccessible(true);
                Object renderState = field.get(source);
                var add = renderState.getClass().getMethod("addGuiElement", GuiElementRenderState.class);
                add.setAccessible(true);
                add.invoke(renderState, state);
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to submit GUI element state", exception);
        }
    }

    private static final class Outline {
        private final List<Point> points;
        private final List<Point> normals;
        private final List<Point> miters;
        private final float inradius;

        private Outline(List<Point> points) {
            this.points = points;
            int count = points.size();
            float centerX = 0.0F;
            float centerY = 0.0F;
            for (Point point : points) {
                centerX += point.x;
                centerY += point.y;
            }
            centerX /= count;
            centerY /= count;
            normals = new ArrayList<>(count);
            float nearest = Float.MAX_VALUE;
            for (int i = 0; i < count; i++) {
                Point a = points.get(i);
                Point b = points.get((i + 1) % count);
                float dx = b.x - a.x;
                float dy = b.y - a.y;
                float length = (float) Math.sqrt(dx * dx + dy * dy);
                Point normal = length == 0.0F ? new Point(0.0F, 0.0F) : new Point(dy / length, -dx / length);
                float reach = normal.x * (a.x - centerX) + normal.y * (a.y - centerY);
                if (reach < 0.0F) {
                    normal = new Point(-normal.x, -normal.y);
                    reach = -reach;
                }
                normals.add(normal);
                if (length > 0.0F) nearest = Math.min(nearest, reach);
            }
            inradius = nearest == Float.MAX_VALUE ? 0.0F : nearest;
            miters = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                Point first = normals.get((i + count - 1) % count);
                Point second = normals.get(i);
                float denominator = 1.0F + first.x * second.x + first.y * second.y;
                float scale = denominator < 0.001F ? 1.0F : 1.0F / denominator;
                miters.add(new Point((first.x + second.x) * scale, (first.y + second.y) * scale));
            }
        }

        private void band(List<Sample[]> quads, float from, float to) {
            float insideFrom = Math.max(from, -inradius);
            float insideTo = Math.min(to, 0.0F);
            int count = points.size();
            if (insideTo > insideFrom) {
                for (int i = 0; i < count; i++) {
                    int j = (i + 1) % count;
                    quads.add(new Sample[] {mitered(i, insideFrom), mitered(j, insideFrom), mitered(j, insideTo), mitered(i, insideTo)});
                }
            }
            float outsideFrom = Math.max(from, 0.0F);
            if (to <= outsideFrom) return;
            for (int i = 0; i < count; i++) {
                int j = (i + 1) % count;
                Point normal = normals.get(i);
                quads.add(new Sample[] {along(i, normal, outsideFrom), along(j, normal, outsideFrom), along(j, normal, to), along(i, normal, to)});
                Point next = normals.get(j);
                double sweep = Math.atan2(normal.x * next.y - normal.y * next.x, normal.x * next.x + normal.y * next.y);
                int steps = Math.max(1, (int) Math.ceil(Math.abs(sweep) / WEDGE_STEP - 1.0E-4));
                double start = Math.atan2(normal.y, normal.x);
                for (int step = 0; step < steps; step++) {
                    Point a = direction(start + sweep * step / steps);
                    Point b = direction(start + sweep * (step + 1) / steps);
                    quads.add(new Sample[] {along(j, a, outsideFrom), along(j, b, outsideFrom), along(j, b, to), along(j, a, to)});
                }
            }
        }

        private Sample mitered(int index, float distance) {
            Point point = points.get(index);
            Point miter = miters.get(index);
            return new Sample(point.x + miter.x * distance, point.y + miter.y * distance, distance);
        }

        private Sample along(int index, Point direction, float distance) {
            Point point = points.get(index);
            return new Sample(point.x + direction.x * distance, point.y + direction.y * distance, distance);
        }

        private static Point direction(double angle) {
            return new Point((float) Math.cos(angle), (float) Math.sin(angle));
        }
    }

    private record Point(float x, float y) {}

    private record Sample(float x, float y, float distance) {}

    private record Vertex(float x, float y, int color, float distance) {}

    private record ShapeGeometry(List<Vertex> vertices, Matrix3x2fc pose, ScreenRectangle bounds,
                                 ScreenRectangle scissorArea, RenderPipeline pipeline,
                                 boolean smooth) implements GuiElementRenderState {
        @Override
        public void buildVertices(VertexConsumer consumer) {
            for (Vertex vertex : vertices) {
                VertexConsumer target = consumer.addVertexWith2DPose(pose, vertex.x, vertex.y);
                if (smooth) target.setUv(vertex.distance, 0.0F);
                target.setColor(vertex.color);
            }
        }

        @Override
        public TextureSetup textureSetup() {
            return TextureSetup.noTexture();
        }
    }
}
