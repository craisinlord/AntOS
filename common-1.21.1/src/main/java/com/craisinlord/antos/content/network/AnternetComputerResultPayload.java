package com.craisinlord.antos.content.network;

import com.craisinlord.antos.AntOS;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AnternetComputerResultPayload(int result, boolean hasPassword, boolean authenticated, String data) implements CustomPacketPayload {
    public static final Type<AnternetComputerResultPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AntOS.MODID, "anternet_computer_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AnternetComputerResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AnternetComputerResultPayload::result,
            ByteBufCodecs.BOOL, AnternetComputerResultPayload::hasPassword,
            ByteBufCodecs.BOOL, AnternetComputerResultPayload::authenticated,
            ByteBufCodecs.stringUtf8(ComputerAccessResultPayload.MAX_DATA_CHARS), AnternetComputerResultPayload::data,
            AnternetComputerResultPayload::new
    );

    @Override
    public Type<AnternetComputerResultPayload> type() {
        return TYPE;
    }
}
