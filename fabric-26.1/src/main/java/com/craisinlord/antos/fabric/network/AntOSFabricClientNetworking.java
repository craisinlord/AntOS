package com.craisinlord.antos.fabric.network;

import com.craisinlord.antos.content.client.AntmailClientState;
import com.craisinlord.antos.content.client.ComputerAccessClientState;
import com.craisinlord.antos.content.network.*;
import com.craisinlord.antos.AntOS;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class AntOSFabricClientNetworking {
    private static volatile boolean serverProtocolReceived;

    private AntOSFabricClientNetworking() {}
    public static void register() {
        ClientConfigurationConnectionEvents.START.register((handler, client) -> serverProtocolReceived = false);
        ClientConfigurationNetworking.registerGlobalReceiver(AntOSProtocolPayload.TYPE, (payload, context) -> {
            serverProtocolReceived = true;
            context.responseSender().sendPacket(new AntOSProtocolPayload(AntOS.NETWORK_PROTOCOL));
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (!serverProtocolReceived && !client.hasSingleplayerServer() && ClientPlayNetworking.canSend(AnternetComputerPayload.TYPE))
                handler.getConnection().disconnect(AntOSProtocolPayload.outdatedServer());
        });
        ClientPlayNetworking.registerGlobalReceiver(AnternetComputerResultPayload.TYPE, (payload, context) -> context.client().execute(() ->
                ComputerAccessClientState.updateAnternet(payload)));
        ClientPlayNetworking.registerGlobalReceiver(AnternetAccountResultPayload.TYPE, (payload, context) -> context.client().execute(() ->
                com.craisinlord.antos.content.client.AnternetAccountClientState.update(payload)));
        ClientPlayNetworking.registerGlobalReceiver(JeiSyncPayload.TYPE, (payload, context) -> context.client().execute(() ->
                com.craisinlord.antos.content.client.JeiClientState.update(payload)));
        ClientPlayNetworking.registerGlobalReceiver(AntmailAnternetResultPayload.TYPE, (payload, context) -> context.client().execute(() -> AntmailClientState.update(payload)));
        ClientPlayNetworking.registerGlobalReceiver(ArchiveRecipeResultPayload.TYPE, (payload, context) -> context.client().execute(() ->
                com.craisinlord.antos.content.client.ArchiveRecipeClientState.update(payload)));
        ComputerNetworking.setSender(payload -> ClientPlayNetworking.send(payload));
        PhoneDiskInsertPayload.setSender(ClientPlayNetworking::send);
        PhoneDiskInsertPayload.setCreativeInventoryCheck(() -> net.minecraft.client.Minecraft.getInstance().screen
                instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen);
        AnternetAccountNetworking.setSender(ClientPlayNetworking::send);
        ArchiveRecipeNetworking.setSender(ClientPlayNetworking::send);
        AntmailNetworking.setSender(payload -> {
            if (payload instanceof net.minecraft.network.protocol.common.custom.CustomPacketPayload customPayload) {
                ClientPlayNetworking.send(customPayload);
            }
        });
    }
}

