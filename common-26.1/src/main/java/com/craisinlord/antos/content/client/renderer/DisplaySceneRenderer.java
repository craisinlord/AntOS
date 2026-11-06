package com.craisinlord.antos.content.client.renderer;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class DisplaySceneRenderer extends PictureInPictureRenderer<DisplaySceneRenderer.State> {
    private static final int IDLE_FRAMES = 200;
    private static int nextSlot;
    private static boolean slotsClaimed;
    private final Map<Integer, Slot> slots = new HashMap<>();
    private int frame;

    @FunctionalInterface
    public interface Draw { void draw(PoseStack pose, MultiBufferSource buffers, SubmitNodeCollector collector, CameraRenderState camera); }

    public record State(List<Draw> draws, Matrix4f view, Quaternionf camera, int x0, int y0, int x1, int y1, int slot,
                        ScreenRectangle scissorArea, ScreenRectangle bounds) implements PictureInPictureRenderState {
        public State(List<Draw> draws, Matrix4f view, Quaternionf camera, int x0, int y0, int x1, int y1, ScreenRectangle scissorArea) {
            this(draws, view, camera, x0, y0, x1, y1, claimSlot(), scissorArea, PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
        }

        @Override
        public float scale() { return 1; }
    }

    private static int claimSlot() {
        slotsClaimed = true;
        return nextSlot++;
    }

    public DisplaySceneRenderer(MultiBufferSource.BufferSource buffers) {
        super(buffers);
    }

    @Override
    public Class<State> getRenderStateClass() { return State.class; }

    @Override
    public void prepare(State state, GuiRenderState guiRenderState, int guiScale) {
        scene(state).prepare(state, guiRenderState, guiScale);
    }

    private Scene scene(State state) {
        if (slotsClaimed) {
            slotsClaimed = false;
            nextSlot = 0;
            frame++;
            slots.values().removeIf(slot -> {
                if (frame - slot.frame <= IDLE_FRAMES) return false;
                slot.scene.close();
                return true;
            });
        }
        Slot slot = slots.computeIfAbsent(state.slot(), ignored -> new Slot(new Scene(bufferSource)));
        slot.frame = frame;
        return slot.scene;
    }

    @Override
    public void close() {
        for (Slot slot : slots.values()) slot.scene.close();
        slots.clear();
        super.close();
    }

    @Override
    protected void renderToTexture(State state, PoseStack pose) { }

    @Override
    protected String getTextureLabel() { return "antos display scenes"; }

    private static final class Slot {
        private final Scene scene;
        private int frame;

        private Slot(Scene scene) {
            this.scene = scene;
        }
    }

    private static final class Scene extends PictureInPictureRenderer<State> {
        private Scene(MultiBufferSource.BufferSource buffers) {
            super(buffers);
        }

        @Override
        public Class<State> getRenderStateClass() { return State.class; }

        @Override
        protected void renderToTexture(State state, PoseStack pose) {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);
            pose.mulPose(state.view());
            var features = minecraft.gameRenderer.getFeatureRenderDispatcher();
            CameraRenderState camera = new CameraRenderState();
            camera.orientation = new Quaternionf(state.camera());
            for (Draw draw : state.draws()) {
                pose.pushPose();
                try { draw.draw(pose, bufferSource, features.getSubmitNodeStorage(), camera); }
                catch (RuntimeException ignored) { }
                finally { pose.popPose(); }
            }
            features.renderAllFeatures();
        }

        @Override
        protected float getTranslateY(int height, int guiScale) { return height / 2.0F; }

        @Override
        protected String getTextureLabel() { return "antos display scene"; }
    }
}
