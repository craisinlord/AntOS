package com.craisinlord.antos.neoforge.client;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.config.AntOSSettings;
import com.craisinlord.antos.content.client.AntOSClientHooks;
import com.craisinlord.antos.content.client.FloppyCategoryProperty;
import com.craisinlord.antos.content.client.screen.ComputerScreen;
import com.craisinlord.antos.content.client.game.AntOSComputerGames;
import com.craisinlord.antos.content.client.renderer.ComputerRenderer;
import com.craisinlord.antos.content.client.renderer.RewardDropRenderer;
import com.craisinlord.antos.content.client.renderer.TaskShapeRenderer;
import com.craisinlord.antos.content.network.AntmailNetworking;
import com.craisinlord.antos.content.network.ComputerNetworking;
import com.craisinlord.antos.neoforge.AntOSNeoForgeContent;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import com.craisinlord.antos.content.client.screen.InventoryComputerButton;

public final class AntOSNeoForgeClient {
    private AntOSNeoForgeClient() {}

    public static void register(net.neoforged.bus.api.IEventBus modBus) {
        modBus.addListener(AntOSNeoForgeClient::onClientSetup);
        modBus.addListener(AntOSNeoForgeClient::registerRenderers);
        modBus.addListener(AntOSNeoForgeClient::registerKeys);
        modBus.addListener(AntOSNeoForgeClient::registerGuiLayers);
        modBus.addListener(AntOSNeoForgeClient::registerFloppyModelProperty);
        modBus.addListener((RegisterRenderPipelinesEvent event) -> event.registerPipeline(TaskShapeRenderer.PIPELINE));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent event) -> event.register(
                com.craisinlord.antos.content.client.renderer.DisplaySceneRenderer.State.class, com.craisinlord.antos.content.client.renderer.DisplaySceneRenderer::new));
    }

    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            AntOSSettings.loadClientState(FMLPaths.GAMEDIR.get().resolve("antos").resolve("client.json"), FMLPaths.CONFIGDIR.get().resolve("antos.json"));
            NeoForge.EVENT_BUS.addListener(AntOSNeoForgeClient::onClientLogout);
            NeoForge.EVENT_BUS.addListener(AntOSNeoForgeClient::onScreenInit);
            NeoForge.EVENT_BUS.addListener(AntOSNeoForgeClient::onTooltip);
            NeoForge.EVENT_BUS.addListener(AntOSNeoForgeClient::onClientTick);
            AntOSComputerGames.register();
            AntOSClientHooks.setComputerOpener(ComputerScreen::openComputer);
            AntOSClientHooks.setPhoneOpener(ComputerScreen::openPhone);
            ComputerNetworking.setSender(ClientPacketDistributor::sendToServer);
            com.craisinlord.antos.content.network.PhoneDiskInsertPayload.setSender(ClientPacketDistributor::sendToServer);
            com.craisinlord.antos.content.network.PhoneDiskInsertPayload.setCreativeInventoryCheck(
                    () -> net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof CreativeModeInventoryScreen);
            com.craisinlord.antos.content.network.AnternetAccountNetworking.setSender(ClientPacketDistributor::sendToServer);
            com.craisinlord.antos.content.network.ArchiveRecipeNetworking.setSender(ClientPacketDistributor::sendToServer);
            AntmailNetworking.setSender(payload -> {
                if (payload instanceof CustomPacketPayload packet) ClientPacketDistributor.sendToServer(packet);
            });
        });
    }

    private static void registerFloppyModelProperty(net.neoforged.neoforge.client.event.RegisterSelectItemModelPropertyEvent event) {
        event.register(net.minecraft.resources.Identifier.fromNamespaceAndPath(AntOS.MODID, "floppy_category"), FloppyCategoryProperty.TYPE);
    }

    private static void registerKeys(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) {
        net.minecraft.client.KeyMapping.Category antosKeys = new net.minecraft.client.KeyMapping.Category(
                net.minecraft.resources.Identifier.fromNamespaceAndPath(AntOS.MODID, "antos"));
        event.registerCategory(antosKeys);
        net.minecraft.client.KeyMapping key = com.craisinlord.antos.content.client.ArchiveLookup.create(org.lwjgl.glfw.GLFW.GLFW_KEY_W, antosKeys);
        key.setKeyConflictContext(net.neoforged.neoforge.client.settings.KeyConflictContext.GUI);
        event.register(key);
        event.register(com.craisinlord.antos.content.client.TaskTrackerHud.createKey(antosKeys));
    }

    private static void onTooltip(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent event) {
        com.craisinlord.antos.content.client.ArchiveLookup.tooltip(event.getItemStack(), event.getToolTip());
    }

    private static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        com.craisinlord.antos.content.client.ArchiveLookup.tick();
        com.craisinlord.antos.content.client.TaskTrackerHud.tick();
    }

    private static void registerGuiLayers(net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) {
        event.registerAboveAll(net.minecraft.resources.Identifier.fromNamespaceAndPath(AntOS.MODID, "task_tracker"),
                (graphics, delta) -> com.craisinlord.antos.content.client.TaskTrackerHud.render(graphics));
    }

    private static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        AntOSClientHooks.clearConnectionState();
    }

    private static void onScreenInit(ScreenEvent.Init.Post event) {
        if ((event.getScreen() instanceof InventoryScreen || event.getScreen() instanceof CreativeModeInventoryScreen)
                && AntOSSettings.inventoryComputerButtonEnabled()) {
            event.addListener(new InventoryComputerButton(4, 4));
        }
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AntOSNeoForgeContent.COMPUTER_BLOCK_ENTITY.get(), ComputerRenderer::new);
        event.registerEntityRenderer(AntOSNeoForgeContent.REWARD_DROP_ENTITY.get(), RewardDropRenderer::new);
    }

}
