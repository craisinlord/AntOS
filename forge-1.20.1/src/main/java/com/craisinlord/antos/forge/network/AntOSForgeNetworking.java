package com.craisinlord.antos.forge.network;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.content.network.*;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.fml.DistExecutor;

public final class AntOSForgeNetworking {
    private static final String PROTOCOL = Integer.toString(AntOS.NETWORK_PROTOCOL);
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(AntOS.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private AntOSForgeNetworking() { }

    public static void register() {
        int id = 0;
        registerServer(id++, AnternetComputerPayload.class, (message, buffer) -> { buffer.writeVarInt(message.action()); buffer.writeUtf(message.value(), 65536); },
                buffer -> new AnternetComputerPayload(buffer.readVarInt(), buffer.readUtf(65536)),
                (message, context) -> withPlayer(context, player -> ComputerAccessHandler.handleAnternet(player, message)));
        registerServer(id++, AnternetAccountPayload.class, (message, buffer) -> { buffer.writeVarInt(message.action()); buffer.writeUtf(message.username(), 256); buffer.writeUtf(message.password(), 256); buffer.writeUtf(message.extra(), 256); },
                buffer -> new AnternetAccountPayload(buffer.readVarInt(), buffer.readUtf(256), buffer.readUtf(256), buffer.readUtf(256)),
                (message, context) -> withPlayer(context, player -> AnternetAccountHandler.handle(player, message)));
        registerServer(id++, AntmailAnternetPayload.class, (message, buffer) -> {
            buffer.writeVarInt(message.action()); buffer.writeUtf(message.first(), 65536); buffer.writeUtf(message.second(), 65536);
            buffer.writeUtf(message.third(), 65536); buffer.writeUtf(message.fourth(), 65536); buffer.writeUtf(message.fifth(), 65536);
            buffer.writeLong(message.number()); buffer.writeLong(message.version());
        }, buffer -> new AntmailAnternetPayload(buffer.readVarInt(), buffer.readUtf(65536), buffer.readUtf(65536), buffer.readUtf(65536),
                buffer.readUtf(65536), buffer.readUtf(65536), buffer.readLong(), buffer.readLong()),
                (message, context) -> withPlayer(context, player -> AntmailServerHandler.handle(player, message)));
        registerServer(id++, PhoneDiskInsertPayload.class, (message, buffer) -> {
            buffer.writeVarInt(message.slot()); buffer.writeUtf(message.diskId(), 256); buffer.writeBoolean(message.redeemed());
        }, buffer -> new PhoneDiskInsertPayload(buffer.readVarInt(), buffer.readUtf(256), buffer.readBoolean()),
                (message, context) -> withPlayer(context, player -> PhoneDiskInsertPayload.handle(player, message)));
        registerClient(id++, AnternetComputerResultPayload.class, (message, buffer) -> {
            buffer.writeVarInt(message.result()); buffer.writeBoolean(message.hasPassword()); buffer.writeBoolean(message.authenticated()); buffer.writeUtf(message.data(), 65536);
        }, buffer -> new AnternetComputerResultPayload(buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean(), buffer.readUtf(65536)));
        registerClient(id++, AnternetAccountResultPayload.class, (message, buffer) -> {
            buffer.writeVarInt(message.result()); buffer.writeUUID(message.accountId()); buffer.writeUUID(message.workspaceId()); buffer.writeUtf(message.username(), 256);
        }, buffer -> new AnternetAccountResultPayload(buffer.readVarInt(), buffer.readUUID(), buffer.readUUID(), buffer.readUtf(256)));
        registerClient(id++, AntmailAnternetResultPayload.class, (message, buffer) -> {
            buffer.writeVarInt(message.status()); buffer.writeUtf(message.address(), 256); buffer.writeUtf(message.messageId(), 256);
            buffer.writeUtf(message.detail(), 4096); buffer.writeUtf(message.data(), 65536); buffer.writeLong(message.version());
        }, buffer -> new AntmailAnternetResultPayload(buffer.readVarInt(), buffer.readUtf(256), buffer.readUtf(256),
                buffer.readUtf(4096), buffer.readUtf(65536), buffer.readLong()));
        registerClient(id++, FloppyTextureSyncPayload.class, (message, buffer) -> buffer.writeUtf(message.mappings(), 65536),
                buffer -> new FloppyTextureSyncPayload(buffer.readUtf(65536)));
        registerClient(id++, JeiSyncPayload.class, (message, buffer) -> {
            buffer.writeVarInt(message.kind()); buffer.writeVarLong(message.version()); buffer.writeVarInt(message.part());
            buffer.writeVarInt(message.parts()); buffer.writeByteArray(message.data());
        }, buffer -> new JeiSyncPayload(buffer.readVarInt(), buffer.readVarLong(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readByteArray(JeiSyncPayload.MAX_CHUNK)));
        registerClient(id++, DisplaySyncPayload.class, (message, buffer) -> {
            buffer.writeVarLong(message.version()); buffer.writeVarInt(message.part()); buffer.writeVarInt(message.parts()); buffer.writeByteArray(message.data());
        }, buffer -> new DisplaySyncPayload(buffer.readVarLong(), buffer.readVarInt(), buffer.readVarInt(), buffer.readByteArray(DisplaySyncPayload.MAX_CHUNK)));
        ComputerAccessHandler.setAnternetResultSender((player, payload) -> send(player, payload));
        AnternetAccountHandler.setResultSender((player, payload) -> send(player, payload));
        AntmailServerHandler.setResultSender((player, payload) -> send(player, payload));
        FloppyTextureSync.setSender((player, payload) -> send(player, payload));
        JeiSync.setSender((player, payload) -> send(player, payload));
        DisplaySync.setSender((player, payload) -> send(player, payload));
    }

    private static <T> void registerServer(int id, Class<T> type, java.util.function.BiConsumer<T, net.minecraft.network.FriendlyByteBuf> encoder,
                                           java.util.function.Function<net.minecraft.network.FriendlyByteBuf, T> decoder,
                                           java.util.function.BiConsumer<T, java.util.function.Supplier<NetworkEvent.Context>> handler) {
        CHANNEL.registerMessage(id, type, encoder, decoder, (message, context) -> {
            context.get().enqueueWork(() -> handler.accept(message, context));
            context.get().setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    private static <T> void registerClient(int id, Class<T> type, java.util.function.BiConsumer<T, net.minecraft.network.FriendlyByteBuf> encoder,
                                           java.util.function.Function<net.minecraft.network.FriendlyByteBuf, T> decoder) {
        CHANNEL.registerMessage(id, type, encoder, decoder, (message, context) -> {
            context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> AntOSForgeClientMessages.handle(message)));
            context.get().setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    private static void withPlayer(java.util.function.Supplier<NetworkEvent.Context> context, java.util.function.Consumer<ServerPlayer> action) {
        ServerPlayer player = context.get().getSender();
        if (player != null) action.accept(player);
    }

    private static void send(ServerPlayer player, Object payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    public static void sendToServer(Object payload) { CHANNEL.sendToServer(payload); }
}
