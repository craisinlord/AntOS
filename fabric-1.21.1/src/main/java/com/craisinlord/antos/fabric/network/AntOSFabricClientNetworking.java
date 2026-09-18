package com.craisinlord.antos.fabric.network;

import com.craisinlord.antos.content.client.AntmailClientState;
import com.craisinlord.antos.content.client.ComputerAccessClientState;
import com.craisinlord.antos.content.client.ComputerStructureLocatorClientState;
import com.craisinlord.antos.content.network.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class AntOSFabricClientNetworking {
    private AntOSFabricClientNetworking() {}
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ComputerAccessResultPayload.TYPE, (payload, context) -> context.client().execute(() -> {
            ComputerAccessClientState.update(payload);
            ComputerStructureLocatorClientState.update(payload);
        }));
        ClientPlayNetworking.registerGlobalReceiver(AntmailResultPayload.TYPE, (payload, context) -> context.client().execute(() -> AntmailClientState.update(payload)));
        ComputerNetworking.setSender(ClientPlayNetworking::send);
        AntmailNetworking.setSender(payload -> {
            if (payload instanceof net.minecraft.network.protocol.common.custom.CustomPacketPayload customPayload) {
                ClientPlayNetworking.send(customPayload);
            }
        });
    }
}

