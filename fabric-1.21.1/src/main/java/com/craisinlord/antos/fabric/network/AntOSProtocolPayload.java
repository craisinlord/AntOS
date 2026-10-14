package com.craisinlord.antos.fabric.network;

import com.craisinlord.antos.AntOS;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AntOSProtocolPayload(int version) implements CustomPacketPayload {
    public static final Type<AntOSProtocolPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AntOS.MODID, "protocol"));
    public static final StreamCodec<ByteBuf, AntOSProtocolPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, AntOSProtocolPayload::version, AntOSProtocolPayload::new);

    static Component mismatch(int server, int client) {
        return Component.translatableWithFallback("antos.network.protocol_mismatch",
                "AntOS version mismatch: the server uses network protocol %s but your client uses %s. Install the AntOS version that matches the server.",
                server, client);
    }

    static Component outdatedClient() {
        return Component.translatableWithFallback("antos.network.protocol_mismatch.outdated_client",
                "AntOS version mismatch: the server uses network protocol %s but your AntOS version is too old. Install the AntOS version that matches the server.",
                AntOS.NETWORK_PROTOCOL);
    }

    static Component outdatedServer() {
        return Component.translatableWithFallback("antos.network.protocol_mismatch.outdated_server",
                "AntOS version mismatch: this server runs an older AntOS version than your client (network protocol %s). Install the AntOS version that matches the server.",
                AntOS.NETWORK_PROTOCOL);
    }

    @Override
    public Type<AntOSProtocolPayload> type() {
        return TYPE;
    }
}
