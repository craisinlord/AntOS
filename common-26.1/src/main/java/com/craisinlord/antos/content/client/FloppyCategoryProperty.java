package com.craisinlord.antos.content.client;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty.Type;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public record FloppyCategoryProperty() implements SelectItemModelProperty<String> {
    public static final Type<FloppyCategoryProperty, String> TYPE =
            Type.create(MapCodec.unit(new FloppyCategoryProperty()), Codec.STRING);

    @Override
    public String get(ItemStack stack, ClientLevel level, LivingEntity entity, int seed, ItemDisplayContext context) {
        return FloppyTextureClientState.modelCategory(stack);
    }

    @Override
    public Codec<String> valueCodec() {
        return Codec.STRING;
    }

    @Override
    public Type<FloppyCategoryProperty, String> type() {
        return TYPE;
    }
}