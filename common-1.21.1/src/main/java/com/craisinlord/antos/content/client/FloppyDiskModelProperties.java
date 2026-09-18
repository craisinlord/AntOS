package com.craisinlord.antos.content.client;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/** Registers the standard model predicate used by floppy_disk.json overrides. */
public final class FloppyDiskModelProperties {
    private static final ResourceLocation FLOPPY_CATEGORY =
            ResourceLocation.fromNamespaceAndPath("antos", "floppy_category");

    private FloppyDiskModelProperties() {}

    public static void register(Item floppyDisk) {
        ItemProperties.register(floppyDisk, FLOPPY_CATEGORY,
                (stack, level, entity, seed) -> FloppyTextureClientState.modelOverride(stack));
    }
}
