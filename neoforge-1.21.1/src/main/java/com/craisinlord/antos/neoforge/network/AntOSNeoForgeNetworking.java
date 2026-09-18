package com.craisinlord.antos.neoforge.network;

import com.craisinlord.antos.content.network.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class AntOSNeoForgeNetworking {
    private AntOSNeoForgeNetworking() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(AntOSNeoForgeNetworking::registerPayloadHandlers);
    }

    private static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(ComputerAccessPayload.TYPE, ComputerAccessPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) ComputerAccessHandler.handle(player, payload);
                }));
        registrar.playToServer(AntmailSetupPayload.TYPE, AntmailSetupPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> withPlayer(context, player -> AntmailServerHandler.handle(player, payload))));
        registrar.playToServer(AntmailStateRequestPayload.TYPE, AntmailStateRequestPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> withPlayer(context, player -> AntmailServerHandler.handle(player, payload))));
        registrar.playToServer(AntmailMessageRequestPayload.TYPE, AntmailMessageRequestPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> withPlayer(context, player -> AntmailServerHandler.handle(player, payload))));
        registrar.playToServer(AntmailSendPayload.TYPE, AntmailSendPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> withPlayer(context, player -> AntmailServerHandler.handle(player, payload))));
        registrar.playToServer(AntmailReadPayload.TYPE, AntmailReadPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> withPlayer(context, player -> AntmailServerHandler.handle(player, payload))));
        registrar.playToServer(AntmailDeletePayload.TYPE, AntmailDeletePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> withPlayer(context, player -> AntmailServerHandler.handle(player, payload))));
        registrar.playToServer(AntmailDraftPayload.TYPE, AntmailDraftPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> withPlayer(context, player -> AntmailServerHandler.handle(player, payload))));
        registrar.playToServer(AntmailRetryPayload.TYPE, AntmailRetryPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> withPlayer(context, player -> AntmailServerHandler.handle(player, payload))));
        registrar.playToClient(ComputerAccessResultPayload.TYPE, ComputerAccessResultPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    com.craisinlord.antos.content.client.ComputerAccessClientState.update(payload);
                    com.craisinlord.antos.content.client.ComputerStructureLocatorClientState.update(payload);
                }));
        registrar.playToClient(AntmailResultPayload.TYPE, AntmailResultPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.craisinlord.antos.content.client.AntmailClientState.update(payload)));
        registrar.playToClient(FloppyTextureSyncPayload.TYPE, FloppyTextureSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.craisinlord.antos.content.client.FloppyTextureClientState.update(payload)));

        ComputerAccessHandler.setResultSender((player, payload) -> PacketDistributor.sendToPlayer(player, payload));
        AntmailServerHandler.setResultSender((player, payload) -> PacketDistributor.sendToPlayer(player, payload));
        FloppyTextureSync.setSender((player, payload) -> PacketDistributor.sendToPlayer(player, payload));
    }

    private static void withPlayer(net.neoforged.neoforge.network.handling.IPayloadContext context,
                                   java.util.function.Consumer<ServerPlayer> action) {
        if (context.player() instanceof ServerPlayer player) action.accept(player);
    }
}
