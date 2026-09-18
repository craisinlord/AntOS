package com.craisinlord.antos.neoforge.client;

import com.craisinlord.antos.content.client.renderer.FloppyDiskRenderer;
import com.craisinlord.antos.content.item.FloppyDiskItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/** NeoForge hook for the floppy's data-pack-selected flat texture. */
public final class AntOSNeoForgeFloppyDiskItem extends FloppyDiskItem {
    public AntOSNeoForgeFloppyDiskItem(Properties properties) {
        super(properties);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return RendererHolder.RENDERER;
            }
        });
    }

    private static final class RendererHolder {
        private static final FloppyDiskRenderer RENDERER = new FloppyDiskRenderer(
                Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }
}
