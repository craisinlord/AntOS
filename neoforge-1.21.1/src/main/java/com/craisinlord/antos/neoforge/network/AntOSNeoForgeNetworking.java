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
        registrar.playToServer(AnternetComputerPayload.TYPE, AnternetComputerPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) ComputerAccessHandler.handleAnternet(player, payload);
                }));
        registrar.playToServer(AnternetAccountPayload.TYPE, AnternetAccountPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> withPlayer(context, player -> AnternetAccountHandler.handle(player, payload))));
        registrar.playToServer(AntmailAnternetPayload.TYPE, AntmailAnternetPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> withPlayer(context, player -> AntmailServerHandler.handle(player, payload))));
        registrar.playToClient(AnternetComputerResultPayload.TYPE, AnternetComputerResultPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.craisinlord.antos.content.client.ComputerAccessClientState.updateAnternet(payload)));
        registrar.playToClient(AnternetAccountResultPayload.TYPE, AnternetAccountResultPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.craisinlord.antos.content.client.AnternetAccountClientState.update(payload)));
        registrar.playToClient(AntmailAnternetResultPayload.TYPE, AntmailAnternetResultPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.craisinlord.antos.content.client.AntmailClientState.update(payload)));
        registrar.playToClient(FloppyTextureSyncPayload.TYPE, FloppyTextureSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.craisinlord.antos.content.client.FloppyTextureClientState.update(payload)));

        ComputerAccessHandler.setAnternetResultSender((player, payload) -> PacketDistributor.sendToPlayer(player, payload));
        AnternetAccountHandler.setResultSender((player, payload) -> PacketDistributor.sendToPlayer(player, payload));
        AntmailServerHandler.setResultSender((player, payload) -> PacketDistributor.sendToPlayer(player, payload));
        FloppyTextureSync.setSender((player, payload) -> PacketDistributor.sendToPlayer(player, payload));
    }

    private static void withPlayer(net.neoforged.neoforge.network.handling.IPayloadContext context,
                                   java.util.function.Consumer<ServerPlayer> action) {
        if (context.player() instanceof ServerPlayer player) action.accept(player);
    }
}
