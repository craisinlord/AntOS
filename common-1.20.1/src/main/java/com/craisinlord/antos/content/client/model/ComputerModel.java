package com.craisinlord.antos.content.client.model;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.content.block.entity.ComputerBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class ComputerModel extends GeoModel<ComputerBlockEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(AntOS.MODID, "geo/computer.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(AntOS.MODID, "textures/block/computer/computer.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(AntOS.MODID, "animations/computer.animation.json");

    @Override
    public ResourceLocation getModelResource(ComputerBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(ComputerBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(ComputerBlockEntity animatable) {
        return ANIMATION;
    }
}


