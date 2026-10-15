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
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(RewardDropEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        if (chest == null) chest = new ItemStack(Items.CHEST);
        itemModelResolver.updateForNonLiving(state.item, chest, ItemDisplayContext.FIXED, entity);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector,
                       net.minecraft.client.renderer.state.level.CameraRenderState cameraState) {
        super.submit(state, pose, collector, cameraState);
        pose.pushPose();
        pose.translate(0.0D, 0.38D, 0.0D);
        pose.mulPose(new org.joml.Matrix4f().rotationY((float) Math.toRadians(state.ageInTicks * 7.0F)));
        pose.scale(0.9F, 0.9F, 0.9F);
        state.item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        pose.popPose();
    }

    public static final class State extends EntityRenderState {
        public final ItemStackRenderState item = new ItemStackRenderState();
    }
}
