package com.craisinlord.antos.content.network;

import com.craisinlord.antos.AntOS;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ComputerAccessPayload(int action, String value) implements CustomPacketPayload {
    public static final int OPEN = 0;
    public static final int SETUP = 1;
    public static final int LOGIN = 2;
    public static final int LOGOUT = 3;
    public static final int CHANGE_PASSWORD = 4;
    public static final int EJECT = 5;
    public static final int CLOSE = 6;
    public static final int FILE_LIST = 7;
    public static final int FILE_OPEN = 8;
    public static final int FILE_CREATE = 9;
    public static final int FILE_SAVE = 10;
    public static final int DESKTOP_STATE = 11;
    public static final int DESKTOP_WALLPAPER = 12;
    public static final int FILE_DELETE = 13;
    public static final int FILE_MOVE = 14;
    public static final int TERMINAL_COMMAND = 15;
    public static final int ARCHIVE_STATE = 20;
    public static final int LOCATE_STRUCTURE = 21;
    public static final int TASK_STATE = 22;
    public static final int ARCHIVE_VIEWED = 23;
    public static final int BLOCKLE_STATE = 24;
    public static final int BLOCKLE_GUESS = 25;
    public static final int ANTAZON_STATE = 26;
    public static final int ANTAZON_PURCHASE = 27;
    public static final int ANTAZON_WISHLIST = 28;
    public static final int ANTAZON_ORDERS = 29;
    public static final int ANTAZON_REVIEW = 30;
    public static final int ANTAZON_WALLET = 31;
    public static final int ANTAZON_SELL = 32;
    public static final int ANTAZON_SELL_STATE = 33;
    public static final int ANTAZON_PRICES = 34;
    public static final int ANTAZON_PREPARE = 35;
    public static final int ANTAZON_ONBOARDING = 36;
    public static final int ANTAZON_ONBOARDING_COMPLETE = 37;
    public static final int ANTAZON_ONBOARDING_RESET = 38;
    public static final int ANTAZON_CRATE_LINK = 39;
    public static final Type<ComputerAccessPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AntOS.MODID, "computer_access"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ComputerAccessPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ComputerAccessPayload::action,
            ByteBufCodecs.stringUtf8(65536), ComputerAccessPayload::value,
            ComputerAccessPayload::new
    );

    public ComputerAccessPayload(int action) {
        this(action, "");
    }

    @Override
    public Type<ComputerAccessPayload> type() {
        return TYPE;
    }
}


