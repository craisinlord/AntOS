package com.craisinlord.antos.content.client;

import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperties;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

public final class FloppyDiskModelProperties {
    private static final Identifier FLOPPY_CATEGORY = Identifier.fromNamespaceAndPath("antos", "floppy_category");

    private FloppyDiskModelProperties() {}

    public static void register(Item floppyDisk) {
        try {
            var field = SelectItemModelProperties.class.getDeclaredField("ID_MAPPER");
            field.setAccessible(true);
            ((net.minecraft.util.ExtraCodecs.LateBoundIdMapper<Identifier, net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty.Type<?, ?>>) field.get(null))
                    .put(FLOPPY_CATEGORY, FloppyCategoryProperty.TYPE);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to register floppy model property", exception);
        }
    }
}
