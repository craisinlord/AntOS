package com.craisinlord.antos.fabric.network;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.content.network.*;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ConfigurationTask;

public final class AntOSFabricNetworking {
    private static final Set<UUID> VERIFIED = ConcurrentHashMap.newKeySet();

    private AntOSFabricNetworking() {}

    public static void register() {
        PayloadTypeRegistry.clientboundConfiguration().register(AntOSProtocolPayload.TYPE, AntOSProtocolPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundConfiguration().register(AntOSProtocolPayload.TYPE, AntOSProtocolPayload.STREAM_CODEC);
        ServerConfigurationNetworking.registerGlobalReceiver(AntOSProtocolPayload.TYPE, (payload, context) -> {
            if (payload.version() != AntOS.NETWORK_PROTOCOL) {
                context.packetListener().disconnect(AntOSProtocolPayload.mismatch(AntOS.NETWORK_PROTOCOL, payload.version()));
                return;
            }
            VERIFIED.add(context.packetListener().getOwner().id());
        });
        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
            VERIFIED.remove(handler.getOwner().id());
            if (ServerConfigurationNetworking.canSend(handler, AntOSProtocolPayload.TYPE)) handler.addTask(new ProtocolTask(handler.getOwner().id()));
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            boolean verified = VERIFIED.remove(handler.player.getUUID());
            if (!verified && !server.isSingleplayerOwner(handler.player.nameAndId())
                    && ServerPlayNetworking.canSend(handler, AnternetComputerResultPayload.TYPE)) handler.disconnect(AntOSProtocolPayload.outdatedClient());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> VERIFIED.remove(handler.player.getUUID()));
        PayloadTypeRegistry.clientboundPlay().register(AnternetComputerResultPayload.TYPE, AnternetComputerResultPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AnternetAccountResultPayload.TYPE, AnternetAccountResultPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AntmailAnternetResultPayload.TYPE, AntmailAnternetResultPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FloppyTextureSyncPayload.TYPE, FloppyTextureSyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(JeiSyncPayload.TYPE, JeiSyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ArchiveRecipeResultPayload.TYPE, ArchiveRecipeResultPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AnternetComputerPayload.TYPE, AnternetComputerPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AnternetAccountPayload.TYPE, AnternetAccountPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AntmailAnternetPayload.TYPE, AntmailAnternetPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PhoneDiskInsertPayload.TYPE, PhoneDiskInsertPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ArchiveRecipeRequestPayload.TYPE, ArchiveRecipeRequestPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(PhoneDiskInsertPayload.TYPE, (payload, context) ->
                context.server().execute(() -> PhoneDiskInsertPayload.handle(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(AnternetComputerPayload.TYPE, (payload, context) ->
                context.server().execute(() -> ComputerAccessHandler.handleAnternet(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(AnternetAccountPayload.TYPE, (payload, context) ->
                context.server().execute(() -> AnternetAccountHandler.handle(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(AntmailAnternetPayload.TYPE, (payload, context) ->
                context.server().execute(() -> AntmailServerHandler.handle(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(ArchiveRecipeRequestPayload.TYPE, (payload, context) ->
                context.server().execute(() -> ArchiveRecipeNetworking.handle(context.player(), payload)));

        ComputerAccessHandler.setAnternetResultSender(ServerPlayNetworking::send);
        AnternetAccountHandler.setResultSender(ServerPlayNetworking::send);
        AntmailServerHandler.setResultSender(ServerPlayNetworking::send);
        FloppyTextureSync.setSender(ServerPlayNetworking::send);
        JeiSync.setSender(ServerPlayNetworking::send);
        ArchiveRecipeNetworking.setResultSender(ServerPlayNetworking::send);
    }

    private record ProtocolTask(UUID player) implements ConfigurationTask {
        private static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type(AntOSProtocolPayload.TYPE.id().toString());

        @Override
        public void start(Consumer<Packet<?>> sender) {
            sender.accept(ServerConfigurationNetworking.createClientboundPacket(new AntOSProtocolPayload(AntOS.NETWORK_PROTOCOL)));
        }

        @Override
        public boolean tick() {
            return VERIFIED.contains(player);
        }

        @Override
        public ConfigurationTask.Type type() {
            return TYPE;
        }
    }
}

