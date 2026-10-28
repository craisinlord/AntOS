package com.craisinlord.antos.content.client.renderer;

import com.craisinlord.antos.content.entity.RewardDropEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;

public final class RewardDropRenderer extends EntityRenderer<RewardDropEntity, RewardDropRenderer.State> {
    private ItemStack chest;
    private final net.minecraft.client.renderer.item.ItemModelResolver itemModelResolver;

    public RewardDropRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
        this.shadowRadius = 0.45F;
    }

    @Override
    public boolean shouldRender(RewardDropEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double cameraX, double cameraY, double cameraZ) {
        return super.shouldRender(entity, frustum, cameraX, cameraY, cameraZ)
                || entity.portalRadius(0.0F) > 0.0F && entity.distanceToSqr(cameraX, cameraY, cameraZ) < 65536.0D
                && frustum.isVisible(entity.portalBounds());
    }

    private static void portalVertices(PoseStack.Pose pose, com.mojang.blaze3d.vertex.VertexConsumer vertices,
                                       float radius, float y) {
        DeliveryPortalGeometry.emit(radius, y, (x, vertexY, z) -> vertices.addVertex(pose.pose(), x, vertexY, z));
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(RewardDropEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.deliveryHidden = entity.deliveryHidden();
        state.deliveryScale = entity.deliveryScale(partialTick);
        state.portalRadius = entity.portalRadius(partialTick);
        state.portalOffset = entity.portalOffset(partialTick);
        if (chest == null) chest = new ItemStack(Items.CHEST);
        itemModelResolver.updateForNonLiving(state.item, chest, ItemDisplayContext.FIXED, entity);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector,
                       net.minecraft.client.renderer.state.level.CameraRenderState cameraState) {
        if (state.portalRadius > 0.0F) {
            float radius = state.portalRadius;
            float y = state.portalOffset;
            collector.submitCustomGeometry(pose, net.minecraft.client.renderer.rendertype.RenderTypes.endPortal(),
                    (portalPose, vertices) -> portalVertices(portalPose, vertices, radius, y));
        }
        if (state.deliveryHidden) return;
        super.submit(state, pose, collector, cameraState);
        pose.pushPose();
        pose.translate(0.0D, 0.38D, 0.0D);
        pose.mulPose(new Quaternionf().rotationY((float) Math.toRadians(state.ageInTicks * 7.0F)));
        float scale = 0.9F * state.deliveryScale;
        pose.scale(scale, scale, scale);
        state.item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        pose.popPose();
    }

    public static final class State extends EntityRenderState {
        public boolean deliveryHidden;
        public float deliveryScale;
        public float portalRadius;
        public float portalOffset;
        public final ItemStackRenderState item = new ItemStackRenderState();
    }
}
