package com.craisinlord.antos.neoforge.client;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.config.AntOSSettings;
import com.craisinlord.antos.content.client.AntOSClientHooks;
import com.craisinlord.antos.content.client.FloppyDiskModelProperties;
import com.craisinlord.antos.content.client.screen.ComputerScreen;
import com.craisinlord.antos.content.client.game.AntOSComputerGames;
import com.craisinlord.antos.content.client.renderer.ComputerRenderer;
import com.craisinlord.antos.content.client.renderer.RewardDropRenderer;
import com.craisinlord.antos.content.network.AntmailNetworking;
import com.craisinlord.antos.content.network.ComputerNetworking;
import com.craisinlord.antos.neoforge.AntOSNeoForgeContent;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import com.craisinlord.antos.content.client.screen.InventoryComputerButton;

@EventBusSubscriber(modid = AntOS.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AntOSNeoForgeClient {
    private AntOSNeoForgeClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            AntOSSettings.load(FMLPaths.CONFIGDIR.get().resolve("antos.json"));
            NeoForge.EVENT_BUS.addListener(AntOSNeoForgeClient::onClientLogout);
            NeoForge.EVENT_BUS.addListener(AntOSNeoForgeClient::onScreenInit);
            AntOSComputerGames.register();
            AntOSClientHooks.setComputerOpener(ComputerScreen::openComputer);
            AntOSClientHooks.setPhoneOpener(ComputerScreen::openPhone);
            FloppyDiskModelProperties.register(AntOSNeoForgeContent.FLOPPY_DISK.get());
            ComputerNetworking.setSender(PacketDistributor::sendToServer);
            com.craisinlord.antos.content.network.AnternetAccountNetworking.setSender(PacketDistributor::sendToServer);
            AntmailNetworking.setSender(payload -> {
                if (payload instanceof CustomPacketPayload packet) PacketDistributor.sendToServer(packet);
            });
        });
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

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AntOSNeoForgeContent.COMPUTER_BLOCK_ENTITY.get(), ComputerRenderer::new);
        event.registerEntityRenderer(AntOSNeoForgeContent.REWARD_DROP_ENTITY.get(), RewardDropRenderer::new);
    }
}
