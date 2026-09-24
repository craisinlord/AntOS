package com.craisinlord.antos.content.item;

import com.craisinlord.antos.AntOS;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class AntroidPhoneModel extends GeoModel<AntroidPhoneItem> {
    @Override public ResourceLocation getModelResource(AntroidPhoneItem item) {
        return ResourceLocation.fromNamespaceAndPath(AntOS.MODID, "geo/antroid_phone.geo.json");
    }
    @Override public ResourceLocation getTextureResource(AntroidPhoneItem item) {
        return ResourceLocation.fromNamespaceAndPath(AntOS.MODID, "textures/item/antroid_phone.png");
    }
    @Override public ResourceLocation getAnimationResource(AntroidPhoneItem item) {
        return ResourceLocation.fromNamespaceAndPath(AntOS.MODID, "animations/antroid_phone.animation.json");
    }
}
