package com.craisinlord.antos.forge.client;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.content.client.AntOSClientHooks;
import com.craisinlord.antos.content.client.FloppyDiskModelProperties;
import com.craisinlord.antos.content.client.game.AntOSComputerGames;
import com.craisinlord.antos.content.client.renderer.ComputerRenderer;
import com.craisinlord.antos.content.client.renderer.RewardDropRenderer;
import com.craisinlord.antos.content.client.renderer.TaskShapeRenderer;
import com.craisinlord.antos.content.client.screen.ComputerScreen;
import com.craisinlord.antos.content.client.screen.InventoryComputerButton;
import com.craisinlord.antos.content.network.AntmailNetworking;
import com.craisinlord.antos.content.network.AnternetAccountNetworking;
import com.craisinlord.antos.content.network.ComputerNetworking;
import com.craisinlord.antos.content.network.PhoneDiskInsertPayload;
import com.craisinlord.antos.forge.AntOSForgeContent;
import com.craisinlord.antos.forge.network.AntOSForgeNetworking;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.common.MinecraftForge;

@Mod.EventBusSubscriber(modid = AntOS.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AntOSForgeClient {
    private AntOSForgeClient() { }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MinecraftForge.EVENT_BUS.addListener(AntOSForgeClient::onScreenInit);
            MinecraftForge.EVENT_BUS.addListener(AntOSForgeClient::onClientLogout);
            AntOSComputerGames.register();
            AntOSClientHooks.setComputerOpener(ComputerScreen::openComputer);
            AntOSClientHooks.setPhoneOpener(ComputerScreen::openPhone);
            FloppyDiskModelProperties.register(AntOSForgeContent.FLOPPY_DISK.get());
            ComputerNetworking.setSender(AntOSForgeNetworking::sendToServer);
            PhoneDiskInsertPayload.setSender(AntOSForgeNetworking::sendToServer);
            PhoneDiskInsertPayload.setCreativeInventoryCheck(() -> net.minecraft.client.Minecraft.getInstance().screen instanceof CreativeModeInventoryScreen);
            AnternetAccountNetworking.setSender(AntOSForgeNetworking::sendToServer);
            AntmailNetworking.setSender(AntOSForgeNetworking::sendToServer);
        });
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AntOSForgeContent.COMPUTER_BLOCK_ENTITY.get(), ComputerRenderer::new);
        event.registerEntityRenderer(AntOSForgeContent.REWARD_DROP_ENTITY.get(), RewardDropRenderer::new);
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), TaskShapeRenderer.SHADER_ID,
                DefaultVertexFormat.POSITION_TEX), TaskShapeRenderer::setShader);
    }

    private static void onScreenInit(ScreenEvent.Init.Post event) {
        if ((event.getScreen() instanceof InventoryScreen || event.getScreen() instanceof CreativeModeInventoryScreen)
                && com.craisinlord.antos.config.AntOSSettings.inventoryComputerButtonEnabled()) {
            event.addListener(new InventoryComputerButton(4, 4));
        }
    }

    private static void onClientLogout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        AntOSClientHooks.clearConnectionState();
    }
}
