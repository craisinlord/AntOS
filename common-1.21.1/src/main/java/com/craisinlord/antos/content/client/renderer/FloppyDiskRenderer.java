package com.craisinlord.antos.content.client.renderer;

import com.craisinlord.antos.content.AntOSObjects;
import com.craisinlord.antos.content.guide.ComputerGuideData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Renders the flat, data-pack textured floppy item. */
public final class FloppyDiskRenderer extends BlockEntityWithoutLevelRenderer {
    public FloppyDiskRenderer(net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher dispatcher,
            net.minecraft.client.model.geom.EntityModelSet entityModels) {
        super(dispatcher, entityModels);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        renderTexture(textureFor(stack), poseStack, buffers, packedLight, packedOverlay);
    }

    public static ResourceLocation textureFor(ItemStack stack) {
        ResourceLocation diskId = stack.get(AntOSObjects.FLOPPY_DISK_COMPONENT.get());
        return diskId == null ? ResourceLocation.fromNamespaceAndPath("antos", "item/floppy_disk/floppy_disk")
                : ComputerGuideData.diskTexture(diskId);
    }

    public static void renderTexture(ResourceLocation texture, PoseStack poseStack, MultiBufferSource buffers,
            int packedLight, int packedOverlay) {
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        // A centered, unit-sized face. The item model's display transforms scale it into the item bounds.
        quad(consumer, pose, -0.5F, 0.5F, 0.006F, false, packedLight, packedOverlay);
        quad(consumer, pose, -0.5F, 0.5F, -0.006F, true, packedLight, packedOverlay);
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, float left, float right,
            float z, boolean reverse, int light, int overlay) {
        float normalZ = reverse ? -1.0F : 1.0F;
        if (!reverse) {
            vertex(consumer, pose, left, -0.5F, z, 0.0F, 1.0F, normalZ, light, overlay);
            vertex(consumer, pose, right, -0.5F, z, 1.0F, 1.0F, normalZ, light, overlay);
            vertex(consumer, pose, right, 0.5F, z, 1.0F, 0.0F, normalZ, light, overlay);
            vertex(consumer, pose, left, 0.5F, z, 0.0F, 0.0F, normalZ, light, overlay);
        } else {
            vertex(consumer, pose, right, -0.5F, z, 0.0F, 1.0F, normalZ, light, overlay);
            vertex(consumer, pose, left, -0.5F, z, 1.0F, 1.0F, normalZ, light, overlay);
            vertex(consumer, pose, left, 0.5F, z, 1.0F, 0.0F, normalZ, light, overlay);
            vertex(consumer, pose, right, 0.5F, z, 0.0F, 0.0F, normalZ, light, overlay);
        }
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z,
            float u, float v, float normalZ, int light, int overlay) {
        consumer.addVertex(pose, x, y, z).setColor(255, 255, 255, 255).setUv(u, v)
                .setOverlay(overlay).setLight(light).setNormal(pose, 0.0F, 0.0F, normalZ);
    }
}
