package com.craisinlord.antos.content.item;

import com.craisinlord.antos.AntOS;
import net.minecraft.resources.Identifier;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;

public final class AntroidPhoneModel extends GeoModel<AntroidPhoneItem> {
    private static final Identifier MODEL = Identifier.fromNamespaceAndPath(AntOS.MODID, "geo/antroid_phone");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(AntOS.MODID, "textures/item/antroid_phone.png");
    private static final Identifier ANIMATION = Identifier.fromNamespaceAndPath(AntOS.MODID, "antroid_phone");

    @Override public Identifier getModelResource(GeoRenderState state) {
        return MODEL;
    }
    @Override public Identifier getTextureResource(GeoRenderState state) {
        return TEXTURE;
    }
    @Override public Identifier getAnimationResource(AntroidPhoneItem item) {
        return ANIMATION;
    }
}
