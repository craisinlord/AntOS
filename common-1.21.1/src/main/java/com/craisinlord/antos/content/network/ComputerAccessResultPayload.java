package com.craisinlord.antos.content.network;

import com.craisinlord.antos.AntOS;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ComputerAccessResultPayload(int result, boolean hasPassword, boolean authenticated, String data) implements CustomPacketPayload {
    public static final int MAX_DATA_CHARS = 900_000;
    public static final int MAX_DATA_BYTES = 1_000_000;
    public static final int SETUP_REQUIRED = 0;
    public static final int READY = 1;
    public static final int SUCCESS = 2;
    public static final int INVALID_PASSWORD = 3;
    public static final int BUSY = 4;
    public static final int INVALID = 5;
    public static final int RECOVERY_AVAILABLE = 6;
    public static final Type<ComputerAccessResultPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AntOS.MODID, "computer_access_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ComputerAccessResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ComputerAccessResultPayload::result,
            ByteBufCodecs.BOOL, ComputerAccessResultPayload::hasPassword,
            ByteBufCodecs.BOOL, ComputerAccessResultPayload::authenticated,
            ByteBufCodecs.stringUtf8(MAX_DATA_CHARS), ComputerAccessResultPayload::data,
            ComputerAccessResultPayload::new
    );

    public ComputerAccessResultPayload(int result, boolean hasPassword, boolean authenticated) {
        this(result, hasPassword, authenticated, "");
    }

    public static boolean fits(String data) {
        if (data == null) return true;
        int length = data.length();
        if (length > MAX_DATA_CHARS) return false;
        if ((long) length * 3L <= MAX_DATA_BYTES) return true;
        long bytes = 0L;
        for (int index = 0; index < length; index++) {
            char character = data.charAt(index);
            if (character < 0x80) bytes++;
            else if (character < 0x800) bytes += 2;
            else if (Character.isHighSurrogate(character) && index + 1 < length && Character.isLowSurrogate(data.charAt(index + 1))) {
                bytes += 4;
                index++;
            } else bytes += 3;
            if (bytes > MAX_DATA_BYTES) return false;
        }
        return true;
    }

    @Override
    public Type<ComputerAccessResultPayload> type() {
        return TYPE;
    }
}


