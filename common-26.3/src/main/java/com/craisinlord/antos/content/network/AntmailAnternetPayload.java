package com.craisinlord.antos.content.network;

import com.craisinlord.antos.AntOS;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record AntmailAnternetPayload(int action, String first, String second, String third, String fourth, String fifth,
                                    long number, long version) implements CustomPacketPayload {
    public static final int STATE = 1;
    public static final int MESSAGE = 2;
    public static final int SEND = 3;
    public static final int READ = 4;
    public static final int DELETE = 5;
    public static final int DRAFT = 6;
    public static final int RETRY = 7;
    public static final int ARCHIVE = 8;
    public static final int TRASH = 9;
    public static final int RESTORE = 10;
    public static final int PERMANENT_DELETE = 11;
    public static final int UNARCHIVE = 12;
    public static final int UPDATE_PROFILE = 13;
    public static final int MAX_FIELD_CHARS = 65536;
    public static final Type<AntmailAnternetPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(AntOS.MODID, "antmail_anternet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AntmailAnternetPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                ByteBufCodecs.VAR_INT.encode(buf, payload.action());
                ByteBufCodecs.stringUtf8(MAX_FIELD_CHARS).encode(buf, payload.first());
                ByteBufCodecs.stringUtf8(MAX_FIELD_CHARS).encode(buf, payload.second());
                ByteBufCodecs.stringUtf8(MAX_FIELD_CHARS).encode(buf, payload.third());
                ByteBufCodecs.stringUtf8(MAX_FIELD_CHARS).encode(buf, payload.fourth());
                ByteBufCodecs.stringUtf8(MAX_FIELD_CHARS).encode(buf, payload.fifth());
                ByteBufCodecs.VAR_LONG.encode(buf, payload.number());
                ByteBufCodecs.VAR_LONG.encode(buf, payload.version());
            },
            buf -> new AntmailAnternetPayload(
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.stringUtf8(MAX_FIELD_CHARS).decode(buf),
                    ByteBufCodecs.stringUtf8(MAX_FIELD_CHARS).decode(buf),
                    ByteBufCodecs.stringUtf8(MAX_FIELD_CHARS).decode(buf),
                    ByteBufCodecs.stringUtf8(MAX_FIELD_CHARS).decode(buf),
                    ByteBufCodecs.stringUtf8(MAX_FIELD_CHARS).decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf)));
    public boolean fits() {
        return fits(first) && fits(second) && fits(third) && fits(fourth) && fits(fifth);
    }

    private static boolean fits(String value) {
        return value != null && value.length() <= MAX_FIELD_CHARS;
    }

    @Override public Type<AntmailAnternetPayload> type() { return TYPE; }
}
