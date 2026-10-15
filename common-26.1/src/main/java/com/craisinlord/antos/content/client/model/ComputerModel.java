package com.craisinlord.antos.content.client.model;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.content.block.entity.ComputerBlockEntity;
import net.minecraft.resources.Identifier;
import com.geckolib.model.GeoModel;

public final class ComputerModel extends GeoModel<ComputerBlockEntity> {
    private static final Identifier MODEL = Identifier.fromNamespaceAndPath(AntOS.MODID, "geo/computer");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(AntOS.MODID, "textures/block/computer/computer.png");
    private static final Identifier ANIMATION = Identifier.fromNamespaceAndPath(AntOS.MODID, "computer");

    @Override
    public Identifier getModelResource(com.geckolib.renderer.base.GeoRenderState renderState) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(com.geckolib.renderer.base.GeoRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(ComputerBlockEntity animatable) {
        return ANIMATION;
    }
}


