package com.craisinlord.antos.content.network;

import com.craisinlord.antos.AntOS;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AnternetAccountPayload(int action, String username, String password, String extra) implements CustomPacketPayload {
    public static final int CREATE = 0;
    public static final int LOGIN = 1;
    public static final int LOGOUT = 2;
    public static final int CHANGE_PASSWORD = 3;
    public static final int FACE_ID_LOGIN = 4;
    public static final int FACE_ID_LINK = 5;
    public static final int FACE_ID_UNLINK = 6;
    public static final int FACE_ID_STATUS = 7;
    public static final int SET_HINT = 8;
    public static final Type<AnternetAccountPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AntOS.MODID, "anternet_account"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AnternetAccountPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AnternetAccountPayload::action,
            ByteBufCodecs.stringUtf8(128), AnternetAccountPayload::username,
            ByteBufCodecs.stringUtf8(256), AnternetAccountPayload::password,
            ByteBufCodecs.stringUtf8(128), AnternetAccountPayload::extra,
            AnternetAccountPayload::new
    );

    public AnternetAccountPayload(int action, String username, String password) {
        this(action, username, password, "");
    }

    @Override
    public Type<AnternetAccountPayload> type() {
        return TYPE;
    }
}
