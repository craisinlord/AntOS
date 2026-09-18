package com.craisinlord.antos.fabric.network;

import com.craisinlord.antos.content.network.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class AntOSFabricNetworking {
    private AntOSFabricNetworking() {}

    public static void register() {
        PayloadTypeRegistry.playS2C().register(ComputerAccessResultPayload.TYPE, ComputerAccessResultPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(AntmailResultPayload.TYPE, AntmailResultPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(FloppyTextureSyncPayload.TYPE, FloppyTextureSyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ComputerAccessPayload.TYPE, ComputerAccessPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(AntmailSetupPayload.TYPE, AntmailSetupPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(AntmailStateRequestPayload.TYPE, AntmailStateRequestPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(AntmailMessageRequestPayload.TYPE, AntmailMessageRequestPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(AntmailSendPayload.TYPE, AntmailSendPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(AntmailReadPayload.TYPE, AntmailReadPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(AntmailDeletePayload.TYPE, AntmailDeletePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(AntmailDraftPayload.TYPE, AntmailDraftPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(AntmailRetryPayload.TYPE, AntmailRetryPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ComputerAccessPayload.TYPE, (payload, context) ->
                context.server().execute(() -> ComputerAccessHandler.handle(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(AntmailSetupPayload.TYPE, (payload, context) ->
                context.server().execute(() -> AntmailServerHandler.handle(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(AntmailStateRequestPayload.TYPE, (payload, context) ->
                context.server().execute(() -> AntmailServerHandler.handle(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(AntmailMessageRequestPayload.TYPE, (payload, context) ->
                context.server().execute(() -> AntmailServerHandler.handle(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(AntmailSendPayload.TYPE, (payload, context) ->
                context.server().execute(() -> AntmailServerHandler.handle(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(AntmailReadPayload.TYPE, (payload, context) ->
                context.server().execute(() -> AntmailServerHandler.handle(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(AntmailDeletePayload.TYPE, (payload, context) ->
                context.server().execute(() -> AntmailServerHandler.handle(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(AntmailDraftPayload.TYPE, (payload, context) ->
                context.server().execute(() -> AntmailServerHandler.handle(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(AntmailRetryPayload.TYPE, (payload, context) ->
                context.server().execute(() -> AntmailServerHandler.handle(context.player(), payload)));

        ComputerAccessHandler.setResultSender(ServerPlayNetworking::send);
        AntmailServerHandler.setResultSender(ServerPlayNetworking::send);
        FloppyTextureSync.setSender(ServerPlayNetworking::send);
    }
}

