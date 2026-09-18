package com.craisinlord.antos.fabric.client;

import com.craisinlord.antos.content.client.AntOSClientHooks;
import com.craisinlord.antos.content.client.FloppyDiskModelProperties;
import com.craisinlord.antos.content.client.FloppyTextureClientState;
import com.craisinlord.antos.content.client.screen.ComputerScreen;
import com.craisinlord.antos.content.client.renderer.ComputerRenderer;
import com.craisinlord.antos.content.client.renderer.RewardDropRenderer;
import com.craisinlord.antos.content.network.FloppyTextureSyncPayload;
import com.craisinlord.antos.fabric.AntOSFabricContent;
import com.craisinlord.antos.fabric.network.AntOSFabricClientNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public final class AntOSFabricClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        AntOSFabricClientNetworking.register();
        ClientPlayNetworking.registerGlobalReceiver(FloppyTextureSyncPayload.TYPE,
                (payload, context) -> context.client().execute(() -> FloppyTextureClientState.update(payload)));
        FloppyDiskModelProperties.register(AntOSFabricContent.FLOPPY_DISK);
        AntOSClientHooks.setComputerOpener(pos -> Minecraft.getInstance().setScreen(new ComputerScreen(pos)));
        BlockEntityRendererRegistry.register(AntOSFabricContent.COMPUTER_BLOCK_ENTITY, ComputerRenderer::new);
        EntityRendererRegistry.register(AntOSFabricContent.REWARD_DROP_ENTITY, RewardDropRenderer::new);
    }
}

