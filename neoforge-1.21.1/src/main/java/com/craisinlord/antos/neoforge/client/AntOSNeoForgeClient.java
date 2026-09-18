package com.craisinlord.antos.neoforge.client;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.content.client.AntOSClientHooks;
import com.craisinlord.antos.content.client.screen.ComputerScreen;
import com.craisinlord.antos.content.client.renderer.ComputerRenderer;
import com.craisinlord.antos.content.client.renderer.RewardDropRenderer;
import com.craisinlord.antos.content.network.AntmailNetworking;
import com.craisinlord.antos.content.network.ComputerNetworking;
import com.craisinlord.antos.neoforge.AntOSNeoForgeContent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = AntOS.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AntOSNeoForgeClient {
    private AntOSNeoForgeClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            AntOSClientHooks.setComputerOpener(pos -> Minecraft.getInstance().setScreen(new ComputerScreen(pos)));
            ComputerNetworking.setSender(PacketDistributor::sendToServer);
            AntmailNetworking.setSender(payload -> {
                if (payload instanceof CustomPacketPayload packet) PacketDistributor.sendToServer(packet);
            });
        });
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AntOSNeoForgeContent.COMPUTER_BLOCK_ENTITY.get(), ComputerRenderer::new);
        event.registerEntityRenderer(AntOSNeoForgeContent.REWARD_DROP_ENTITY.get(), RewardDropRenderer::new);
    }
}
