package com.craisinlord.antos.content.network;

import com.craisinlord.antos.AntOS;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AntmailAnternetPayload(int action, String first, String second, String third, String fourth, String fifth,
                                    long number, long version) implements CustomPacketPayload {
    public static final int STATE = 1;
    public static final int MESSAGE = 2;
    public static final int SEND = 3;
    public static final int READ = 4;
    public static final int DELETE = 5;
    public static final int DRAFT = 6;
    public static final int RETRY = 7;
    public static final Type<AntmailAnternetPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AntOS.MODID, "antmail_anternet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AntmailAnternetPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                ByteBufCodecs.VAR_INT.encode(buf, payload.action());
                ByteBufCodecs.stringUtf8(65536).encode(buf, payload.first());
                ByteBufCodecs.stringUtf8(65536).encode(buf, payload.second());
                ByteBufCodecs.stringUtf8(65536).encode(buf, payload.third());
                ByteBufCodecs.stringUtf8(65536).encode(buf, payload.fourth());
                ByteBufCodecs.stringUtf8(65536).encode(buf, payload.fifth());
                ByteBufCodecs.VAR_LONG.encode(buf, payload.number());
                ByteBufCodecs.VAR_LONG.encode(buf, payload.version());
            },
            buf -> new AntmailAnternetPayload(
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.stringUtf8(65536).decode(buf),
                    ByteBufCodecs.stringUtf8(65536).decode(buf),
                    ByteBufCodecs.stringUtf8(65536).decode(buf),
                    ByteBufCodecs.stringUtf8(65536).decode(buf),
                    ByteBufCodecs.stringUtf8(65536).decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf)));
    @Override public Type<AntmailAnternetPayload> type() { return TYPE; }
}
