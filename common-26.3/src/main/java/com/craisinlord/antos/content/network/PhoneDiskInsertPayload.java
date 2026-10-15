package com.craisinlord.antos.content.network;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.content.AntOSObjects;
import com.craisinlord.antos.content.item.AntroidPhoneItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public record PhoneDiskInsertPayload(int slot, String diskId, boolean redeemed) implements CustomPacketPayload {
    public static final Type<PhoneDiskInsertPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(AntOS.MODID, "phone_disk_insert"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PhoneDiskInsertPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PhoneDiskInsertPayload::slot,
            ByteBufCodecs.stringUtf8(256), PhoneDiskInsertPayload::diskId,
            ByteBufCodecs.BOOL, PhoneDiskInsertPayload::redeemed,
            PhoneDiskInsertPayload::new
    );

    private static Consumer<CustomPacketPayload> sender = payload -> {};
    private static BooleanSupplier creativeInventoryOpen = () -> false;

    public static void setSender(Consumer<CustomPacketPayload> value) {
        sender = value == null ? payload -> {} : value;
    }

    public static void setCreativeInventoryCheck(BooleanSupplier value) {
        creativeInventoryOpen = value == null ? () -> false : value;
    }

    public static boolean creativeInventoryOpen() {
        return creativeInventoryOpen.getAsBoolean();
    }

    public static void send(int slot, Identifier diskId, boolean redeemed) {
        sender.accept(new PhoneDiskInsertPayload(slot, diskId.toString(), redeemed));
    }

    public static void handle(ServerPlayer player, PhoneDiskInsertPayload payload) {
        if (!player.isCreative() || payload.slot() < 0 || payload.slot() >= player.getInventory().getContainerSize()) return;
        ItemStack phone = player.getInventory().getItem(payload.slot());
        if (!phone.is(AntOSObjects.ANTROID_PHONE.get())) return;
        Identifier diskId = Identifier.tryParse(payload.diskId());
        if (diskId == null) return;
        ItemStack floppy = new ItemStack(AntOSObjects.FLOPPY_DISK.get());
        floppy.set(AntOSObjects.FLOPPY_DISK_COMPONENT.get(), diskId);
        AntroidPhoneItem.insertDisk(player, phone, floppy, payload.redeemed());
        player.inventoryMenu.broadcastChanges();
    }

    @Override
    public Type<PhoneDiskInsertPayload> type() {
        return TYPE;
    }
}
