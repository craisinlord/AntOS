package com.craisinlord.antos.fabric.network;

import com.craisinlord.antos.content.client.AntmailClientState;
import com.craisinlord.antos.content.client.ComputerAccessClientState;
import com.craisinlord.antos.content.network.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class AntOSFabricClientNetworking {
    private AntOSFabricClientNetworking() {}
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(AnternetComputerResultPayload.TYPE, (payload, context) -> context.client().execute(() ->
                ComputerAccessClientState.updateAnternet(payload)));
        ClientPlayNetworking.registerGlobalReceiver(AnternetAccountResultPayload.TYPE, (payload, context) -> context.client().execute(() ->
                com.craisinlord.antos.content.client.AnternetAccountClientState.update(payload)));
        ClientPlayNetworking.registerGlobalReceiver(AntmailAnternetResultPayload.TYPE, (payload, context) -> context.client().execute(() -> AntmailClientState.update(payload)));
        ComputerNetworking.setSender(payload -> ClientPlayNetworking.send(payload));
        AnternetAccountNetworking.setSender(ClientPlayNetworking::send);
        AntmailNetworking.setSender(payload -> {
            if (payload instanceof net.minecraft.network.protocol.common.custom.CustomPacketPayload customPayload) {
                ClientPlayNetworking.send(customPayload);
            }
        });
    }
}

