package com.craisinlord.antos.content.network;

import com.craisinlord.antos.AntOS;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AnternetComputerPayload(int action, String value) implements CustomPacketPayload {
    public static final Type<AnternetComputerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AntOS.MODID, "anternet_computer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AnternetComputerPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AnternetComputerPayload::action,
            ByteBufCodecs.stringUtf8(65536), AnternetComputerPayload::value,
            AnternetComputerPayload::new
    );

    @Override
    public Type<AnternetComputerPayload> type() {
        return TYPE;
    }
}
