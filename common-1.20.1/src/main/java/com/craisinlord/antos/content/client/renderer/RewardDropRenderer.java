package com.craisinlord.antos.content.client.renderer;

import com.craisinlord.antos.content.entity.RewardDropEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class RewardDropRenderer extends EntityRenderer<RewardDropEntity> {
    private final ItemStack chest = new ItemStack(Items.CHEST);
    private final net.minecraft.client.renderer.entity.ItemRenderer itemRenderer;

    public RewardDropRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
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
        DeliveryPortalGeometry.emit(radius, y, (x, vertexY, z) -> vertices.vertex(pose.pose(), x, vertexY, z).endVertex());
    }

    @Override
    public void render(RewardDropEntity entity, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        float radius = entity.portalRadius(partialTick);
        if (radius > 0.0F) portalVertices(pose.last(), buffers.getBuffer(net.minecraft.client.renderer.RenderType.endPortal()),
                radius, entity.portalOffset(partialTick));
        if (entity.deliveryHidden()) return;
        pose.pushPose();
        pose.translate(0.0D, 0.38D, 0.0D);
        pose.mulPose(Axis.YP.rotationDegrees((entity.tickCount + partialTick) * 7.0F));
        float scale = 0.9F * entity.deliveryScale(partialTick);
        pose.scale(scale, scale, scale);
        this.itemRenderer.renderStatic(chest, ItemDisplayContext.FIXED, packedLight,
                OverlayTexture.NO_OVERLAY, pose, buffers, entity.level(), entity.getId());
        pose.popPose();
        super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(RewardDropEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
