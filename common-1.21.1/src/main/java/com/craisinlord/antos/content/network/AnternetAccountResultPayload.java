package com.craisinlord.antos.content.network;

import com.craisinlord.antos.AntOS;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record AnternetAccountResultPayload(int result, UUID accountId, UUID workspaceId, String username) implements CustomPacketPayload {
    public static final int SUCCESS = 0;
    public static final int INVALID_REQUEST = 1;
    public static final int USERNAME_TAKEN = 2;
    public static final int INVALID_CREDENTIALS = 3;
    public static final int LOGGED_OUT = 5;
    public static final int RATE_LIMITED = 6;
    public static final int FACE_ID_STATUS = 7;
    public static final int FACE_ID_CONFLICT = 8;
    public static final int FACE_ID_PASSWORD_INVALID = 9;
    public static final int FACE_ID_RATE_LIMITED = 10;
    public static final int LOGIN_REQUIRED = 11;
    public static final UUID EMPTY_ID = new UUID(0L, 0L);
    public static final Type<AnternetAccountResultPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AntOS.MODID, "anternet_account_result"));
    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC = new StreamCodec<>() {
        @Override public UUID decode(RegistryFriendlyByteBuf buffer) { return buffer.readUUID(); }
        @Override public void encode(RegistryFriendlyByteBuf buffer, UUID value) { buffer.writeUUID(value); }
    };
    public static final StreamCodec<RegistryFriendlyByteBuf, AnternetAccountResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AnternetAccountResultPayload::result,
            UUID_CODEC, AnternetAccountResultPayload::accountId,
            UUID_CODEC, AnternetAccountResultPayload::workspaceId,
            ByteBufCodecs.stringUtf8(64), AnternetAccountResultPayload::username,
            AnternetAccountResultPayload::new
    );

    @Override
    public Type<AnternetAccountResultPayload> type() {
        return TYPE;
    }
}
