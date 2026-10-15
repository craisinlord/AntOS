package com.craisinlord.antos.content.client.renderer;

import com.craisinlord.antos.content.guide.TaskAppearance;
import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public final class TaskShapeRenderer {
    public static final ResourceLocation SHADER_ID = new ResourceLocation("antos", "task_shape");
    private static final String[] VERTEX_UNIFORMS = {"Vertex0", "Vertex1", "Vertex2", "Vertex3", "Vertex4", "Vertex5", "Vertex6", "Vertex7"};
    private static ShaderInstance shader;
    private static ShaderInstance uniformShader;
    private static AbstractUniform sideCount;
    private static AbstractUniform shapeSize;
    private static AbstractUniform borderWidth;
    private static AbstractUniform hoverWidth;
    private static AbstractUniform fillColor;
    private static AbstractUniform borderColor;
    private static AbstractUniform hoverColor;
    private static final AbstractUniform[] vertices = new AbstractUniform[VERTEX_UNIFORMS.length];
    private static final java.util.function.Supplier<ShaderInstance> SHADER_SUPPLIER = () -> shader;

    private TaskShapeRenderer() { }

    public static void setShader(ShaderInstance loaded) {
        shader = loaded;
    }

    public static void draw(GuiGraphics g, TaskAppearance.Shape shape, float left, float top, float size,
                            int fill, int border, int hover, boolean hovered) {
        if (shader == null) return;
        g.flush();
        ShaderInstance previousShader = RenderSystem.getShader();
        try {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(SHADER_SUPPLIER);
            bindUniforms();
            sideCount.set(shape.sides());
            shapeSize.set(size);
            borderWidth.set(Math.min(2.0F, size / 4));
            hoverWidth.set(hovered ? 2.0F : 0.0F);
            setColor(fillColor, fill);
            setColor(borderColor, border);
            setColor(hoverColor, hovered ? hover : border);
            for (int i = 0; i < 8; i++) {
                vertices[i].set(i < shape.sides() ? (float) shape.vertexX(i) : 0.5F,
                        i < shape.sides() ? (float) shape.vertexY(i) : 0.5F);
            }
            Matrix4f matrix = g.pose().last().pose();
            float screenScale = Math.max(0.01F, matrix.m00());
            double guiScale = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
            float padding = 2.0F + 2.0F / (screenScale * (float) guiScale);
            float minimumUv = -padding / size, maximumUv = 1 + padding / size;
            BufferBuilder buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            buffer.vertex(matrix, left - padding, top - padding, 0).uv(minimumUv, minimumUv).endVertex();
            buffer.vertex(matrix, left - padding, top + size + padding, 0).uv(minimumUv, maximumUv).endVertex();
            buffer.vertex(matrix, left + size + padding, top + size + padding, 0).uv(maximumUv, maximumUv).endVertex();
            buffer.vertex(matrix, left + size + padding, top - padding, 0).uv(maximumUv, minimumUv).endVertex();
            BufferUploader.drawWithShader(buffer.end());
        } finally {
            RenderSystem.setShader(() -> previousShader);
            RenderSystem.disableBlend();
        }
    }

    private static void bindUniforms() {
        if (uniformShader == shader) return;
        uniformShader = shader;
        sideCount = shader.safeGetUniform("SideCount");
        shapeSize = shader.safeGetUniform("ShapeSize");
        borderWidth = shader.safeGetUniform("BorderWidth");
        hoverWidth = shader.safeGetUniform("HoverWidth");
        fillColor = shader.getUniform("FillColor");
        borderColor = shader.getUniform("BorderColor");
        hoverColor = shader.getUniform("HoverColor");
        for (int i = 0; i < VERTEX_UNIFORMS.length; i++) vertices[i] = shader.safeGetUniform(VERTEX_UNIFORMS[i]);
    }

    private static void setColor(AbstractUniform uniform, int themed) {
        int color = com.craisinlord.antos.content.client.ComputerTheme.remap(themed);
        if (uniform != null) uniform.set((color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F,
                (color & 255) / 255.0F, (color >>> 24) / 255.0F);
    }
}
