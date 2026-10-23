package com.craisinlord.antos.fabric.client;

import com.craisinlord.antos.content.client.AntOSClientHooks;
import com.craisinlord.antos.content.client.FloppyDiskModelProperties;
import com.craisinlord.antos.content.client.FloppyTextureClientState;
import com.craisinlord.antos.content.client.screen.ComputerScreen;
import com.craisinlord.antos.content.client.game.AntOSComputerGames;
import com.craisinlord.antos.content.client.renderer.ComputerRenderer;
import com.craisinlord.antos.content.client.renderer.RewardDropRenderer;
import com.craisinlord.antos.content.client.renderer.TaskShapeRenderer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.craisinlord.antos.content.network.FloppyTextureSyncPayload;
import com.craisinlord.antos.config.AntOSSettings;
import com.craisinlord.antos.fabric.AntOSFabricContent;
import com.craisinlord.antos.fabric.network.AntOSFabricClientNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import com.craisinlord.antos.content.client.screen.InventoryComputerButton;

public final class AntOSFabricClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        CoreShaderRegistrationCallback.EVENT.register(context -> context.register(TaskShapeRenderer.SHADER_ID,
                DefaultVertexFormat.POSITION_TEX, TaskShapeRenderer::setShader));
        AntOSSettings.loadClientState(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("antos").resolve("client.json"),
                net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("antos.json"));
        AntOSFabricClientNetworking.register();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(AntOSClientHooks::clearConnectionState));
        AntOSComputerGames.register();
        ClientPlayNetworking.registerGlobalReceiver(FloppyTextureSyncPayload.TYPE,
                (payload, context) -> context.client().execute(() -> FloppyTextureClientState.update(payload)));
        FloppyDiskModelProperties.register(AntOSFabricContent.FLOPPY_DISK);
        AntOSClientHooks.setComputerOpener(ComputerScreen::openComputer);
        AntOSClientHooks.setPhoneOpener(ComputerScreen::openPhone);
        net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(com.craisinlord.antos.content.client.ArchiveLookup.create(org.lwjgl.glfw.GLFW.GLFW_KEY_Z));
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> com.craisinlord.antos.content.client.ArchiveLookup.tooltip(stack, lines));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> com.craisinlord.antos.content.client.ArchiveLookup.tick());
        net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(com.craisinlord.antos.content.client.TaskTrackerHud.createKey());
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> com.craisinlord.antos.content.client.TaskTrackerHud.tick());
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((graphics, delta) -> com.craisinlord.antos.content.client.TaskTrackerHud.render(graphics));
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if ((screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen)
                    && AntOSSettings.inventoryComputerButtonEnabled()) {
                Screens.getButtons(screen).add(new InventoryComputerButton(4, 4));
            }
        });
        BlockEntityRendererRegistry.register(AntOSFabricContent.COMPUTER_BLOCK_ENTITY, ComputerRenderer::new);
        EntityRendererRegistry.register(AntOSFabricContent.REWARD_DROP_ENTITY, RewardDropRenderer::new);
    }
}

