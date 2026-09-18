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
    public void render(RewardDropEntity entity, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        pose.pushPose();
        pose.translate(0.0D, 0.38D, 0.0D);
        pose.mulPose(Axis.YP.rotationDegrees((entity.tickCount + partialTick) * 7.0F));
        pose.scale(0.9F, 0.9F, 0.9F);
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
