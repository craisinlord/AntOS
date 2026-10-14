package com.craisinlord.antos.content.client.renderer;

import com.craisinlord.antos.content.guide.TaskAppearance;
import com.google.gson.JsonParser;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL33;

public final class TaskShapeShaderTest {
    private static final int CANVAS = 192;

    public static void main(String[] args) throws Exception {
        check(GLFW.glfwInit(), "GLFW initialization");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window = GLFW.glfwCreateWindow(CANVAS, CANVAS, "Task shape validation", 0, 0);
        check(window != 0, "hidden OpenGL context");
        try {
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            int program = GL33.glCreateProgram();
            int vertex = compile(GL33.GL_VERTEX_SHADER, resource("task_shape.vsh"));
            int fragment = compile(GL33.GL_FRAGMENT_SHADER, resource("task_shape.fsh"));
            GL33.glAttachShader(program, vertex);
            GL33.glAttachShader(program, fragment);
            GL33.glBindAttribLocation(program, 0, "Position");
            GL33.glBindAttribLocation(program, 1, "UV0");
            GL33.glLinkProgram(program);
            check(GL33.glGetProgrami(program, GL33.GL_LINK_STATUS) != 0, GL33.glGetProgramInfoLog(program));
            GL33.glUseProgram(program);
            var definition = JsonParser.parseString(resource("task_shape.json")).getAsJsonObject();
            for (var element : definition.getAsJsonArray("uniforms")) {
                String name = element.getAsJsonObject().get("name").getAsString();
                check(GL33.glGetUniformLocation(program, name) >= 0, "shader uniform " + name);
            }
            float[] matrix = new float[16];
            GL33.glUniformMatrix4fv(uniform(program, "ModelViewMat"), false, new Matrix4f().get(matrix));
            GL33.glUniformMatrix4fv(uniform(program, "ProjMat"), false, new Matrix4f().setOrtho2D(0, CANVAS, CANVAS, 0).get(matrix));
            GL33.glUniform4f(uniform(program, "ColorModulator"), 1, 1, 1, 1);
            GL33.glUniform4f(uniform(program, "FillColor"), 0.04F, 0.09F, 0.04F, 1);
            GL33.glUniform4f(uniform(program, "BorderColor"), 0.2F, 1, 0.2F, 1);
            GL33.glUniform4f(uniform(program, "HoverColor"), 0.6F, 1, 0.6F, 1);
            GL33.glBindVertexArray(GL33.glGenVertexArrays());
            GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, GL33.glGenBuffers());
            GL33.glEnableVertexAttribArray(0);
            GL33.glEnableVertexAttribArray(1);
            GL33.glVertexAttribPointer(0, 3, GL33.GL_FLOAT, false, 20, 0);
            GL33.glVertexAttribPointer(1, 2, GL33.GL_FLOAT, false, 20, 12);
            int texture = GL33.glGenTextures();
            GL33.glBindTexture(GL33.GL_TEXTURE_2D, texture);
            GL33.glTexImage2D(GL33.GL_TEXTURE_2D, 0, GL33.GL_RGBA8, CANVAS, CANVAS, 0, GL33.GL_RGBA, GL33.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL33.glBindFramebuffer(GL33.GL_FRAMEBUFFER, GL33.glGenFramebuffers());
            GL33.glFramebufferTexture2D(GL33.GL_FRAMEBUFFER, GL33.GL_COLOR_ATTACHMENT0, GL33.GL_TEXTURE_2D, texture, 0);
            check(GL33.glCheckFramebufferStatus(GL33.GL_FRAMEBUFFER) == GL33.GL_FRAMEBUFFER_COMPLETE, "validation framebuffer");
            GL33.glViewport(0, 0, CANVAS, CANVAS);
            float[] sizes = {8.8F, 17.6F, 32, 96, 153.6F};
            var shapes = TaskAppearance.Shape.values();
            BufferedImage preview = new BufferedImage(CANVAS * shapes.length, (CANVAS + 24) * sizes.length, BufferedImage.TYPE_INT_ARGB);
            var graphics = preview.createGraphics();
            graphics.setColor(new Color(12, 18, 12));
            graphics.fillRect(0, 0, preview.getWidth(), preview.getHeight());
            for (int row = 0; row < sizes.length; row++) {
                for (int column = 0; column < shapes.length; column++) {
                    BufferedImage image = null;
                    for (float hover : new float[]{0, 2}) image = render(program, shapes[column], sizes[row], hover);
                    int x = column * CANVAS, y = row * (CANVAS + 24);
                    graphics.drawImage(image, x, y + 24, null);
                    graphics.setColor(Color.WHITE);
                    graphics.drawString(shapes[column].name() + " / " + sizes[row], x + 6, y + 17);
                }
            }
            graphics.dispose();
            Path output = Path.of(args.length > 0 ? args[0] : "build/task-shapes-preview.png");
            Files.createDirectories(output.toAbsolutePath().getParent());
            ImageIO.write(preview, "png", output.toFile());
            check(GL33.glGetError() == GL33.GL_NO_ERROR, "OpenGL state");
            GL33.glDeleteProgram(program);
            GL33.glDeleteShader(vertex);
            GL33.glDeleteShader(fragment);
            System.out.println("Task shape GPU tests passed: all seven shapes at five sizes, with and without hover");
            System.out.println(output.toAbsolutePath());
        } finally {
            GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }

    private static BufferedImage render(int program, TaskAppearance.Shape shape, float size, float hover) {
        GL33.glUniform1i(uniform(program, "SideCount"), shape.sides());
        GL33.glUniform1f(uniform(program, "ShapeSize"), size);
        GL33.glUniform1f(uniform(program, "BorderWidth"), 2);
        GL33.glUniform1f(uniform(program, "HoverWidth"), hover);
        for (int i = 0; i < 8; i++) GL33.glUniform2f(uniform(program, "Vertex" + i),
                i < shape.sides() ? (float) shape.vertexX(i) : 0.5F, i < shape.sides() ? (float) shape.vertexY(i) : 0.5F);
        float left = (CANVAS - size) / 2;
        float minimum = -4 / size, maximum = 1 + 4 / size;
        float a = left - 4, b = left + size + 4;
        GL33.glBufferData(GL33.GL_ARRAY_BUFFER, new float[]{
                a,a,0,minimum,minimum, a,b,0,minimum,maximum, b,b,0,maximum,maximum,
                a,a,0,minimum,minimum, b,b,0,maximum,maximum, b,a,0,maximum,minimum
        }, GL33.GL_STATIC_DRAW);
        GL33.glClearColor(0, 0, 0, 0);
        GL33.glClear(GL33.GL_COLOR_BUFFER_BIT);
        GL33.glDrawArrays(GL33.GL_TRIANGLES, 0, 6);
        ByteBuffer pixels = BufferUtils.createByteBuffer(CANVAS * CANVAS * 4);
        GL33.glReadPixels(0, 0, CANVAS, CANVAS, GL33.GL_RGBA, GL33.GL_UNSIGNED_BYTE, pixels);
        BufferedImage image = new BufferedImage(CANVAS, CANVAS, BufferedImage.TYPE_INT_ARGB);
        int antialiased = 0;
        for (int y = 0; y < CANVAS; y++) {
            for (int x = 0; x < CANVAS; x++) {
                int offset = ((CANVAS - y - 1) * CANVAS + x) * 4;
                int alpha = pixels.get(offset + 3) & 255;
                int mirrored = pixels.get(((CANVAS - y - 1) * CANVAS + CANVAS - x - 1) * 4 + 3) & 255;
                check(Math.abs(alpha - mirrored) <= 2, "GPU mirror symmetry for " + shape);
                double distance = distance(shape, (x + 0.5 - left) / size, (y + 0.5 - left) / size) * size - hover;
                if (distance < -1.5) check(alpha == 255, "opaque shape interior for " + shape);
                if (distance > 1.5) check(alpha == 0, "shape exterior for " + shape);
                if (alpha > 0 && alpha < 255) antialiased++;
                int color = alpha << 24 | (pixels.get(offset) & 255) << 16 | (pixels.get(offset + 1) & 255) << 8 | pixels.get(offset + 2) & 255;
                image.setRGB(x, y, color);
            }
        }
        if (shape != TaskAppearance.Shape.SQUARE) check(antialiased > 0, "antialiased edges for " + shape);
        return image;
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

    private static int compile(int type, String source) {
        int shader = GL33.glCreateShader(type);
        GL33.glShaderSource(shader, source);
        GL33.glCompileShader(shader);
        check(GL33.glGetShaderi(shader, GL33.GL_COMPILE_STATUS) != 0, GL33.glGetShaderInfoLog(shader));
        return shader;
    }

    private static int uniform(int program, String name) { return GL33.glGetUniformLocation(program, name); }

    private static String resource(String name) throws IOException {
        try (var stream = TaskShapeShaderTest.class.getResourceAsStream("/assets/antos/shaders/core/" + name)) {
            if (stream == null) throw new IOException("Missing shader resource " + name);
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
