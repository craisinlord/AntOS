package com.craisinlord.antos.fabric.network;

import com.craisinlord.antos.content.network.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class AntOSFabricNetworking {
    private AntOSFabricNetworking() {}

    public static void register() {
        PayloadTypeRegistry.playS2C().register(AnternetComputerResultPayload.TYPE, AnternetComputerResultPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(AnternetAccountResultPayload.TYPE, AnternetAccountResultPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(AntmailAnternetResultPayload.TYPE, AntmailAnternetResultPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(FloppyTextureSyncPayload.TYPE, FloppyTextureSyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(AnternetComputerPayload.TYPE, AnternetComputerPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(AnternetAccountPayload.TYPE, AnternetAccountPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(AntmailAnternetPayload.TYPE, AntmailAnternetPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(AnternetComputerPayload.TYPE, (payload, context) ->
                context.server().execute(() -> ComputerAccessHandler.handleAnternet(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(AnternetAccountPayload.TYPE, (payload, context) ->
                context.server().execute(() -> AnternetAccountHandler.handle(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(AntmailAnternetPayload.TYPE, (payload, context) ->
                context.server().execute(() -> AntmailServerHandler.handle(context.player(), payload)));

        ComputerAccessHandler.setAnternetResultSender(ServerPlayNetworking::send);
        AnternetAccountHandler.setResultSender(ServerPlayNetworking::send);
        AntmailServerHandler.setResultSender(ServerPlayNetworking::send);
        FloppyTextureSync.setSender(ServerPlayNetworking::send);
    }
}

