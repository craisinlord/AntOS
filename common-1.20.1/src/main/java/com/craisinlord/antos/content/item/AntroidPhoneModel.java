package com.craisinlord.antos.content.item;

import com.craisinlord.antos.AntOS;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class AntroidPhoneModel extends GeoModel<AntroidPhoneItem> {
    private static final ResourceLocation MODEL = new ResourceLocation(AntOS.MODID, "geo/antroid_phone.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(AntOS.MODID, "textures/item/antroid_phone.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(AntOS.MODID, "animations/antroid_phone.animation.json");

    @Override public ResourceLocation getModelResource(AntroidPhoneItem item) {
        return MODEL;
    }
    @Override public ResourceLocation getTextureResource(AntroidPhoneItem item) {
        return TEXTURE;
    }
    @Override public ResourceLocation getAnimationResource(AntroidPhoneItem item) {
        return ANIMATION;
    }
}
