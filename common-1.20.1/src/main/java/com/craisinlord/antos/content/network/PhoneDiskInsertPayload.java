package com.craisinlord.antos.content.network;

import com.craisinlord.antos.content.AntOSObjects;
import com.craisinlord.antos.content.item.AntroidPhoneItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public record PhoneDiskInsertPayload(int slot, String diskId, boolean redeemed) {



    private static Consumer<Object> sender = payload -> {};
    private static BooleanSupplier creativeInventoryOpen = () -> false;

    public static void setSender(Consumer<Object> value) {
        sender = value == null ? payload -> {} : value;
    }

    public static void setCreativeInventoryCheck(BooleanSupplier value) {
        creativeInventoryOpen = value == null ? () -> false : value;
    }

    public static boolean creativeInventoryOpen() {
        return creativeInventoryOpen.getAsBoolean();
    }

    public static void send(int slot, ResourceLocation diskId, boolean redeemed) {
        sender.accept(new PhoneDiskInsertPayload(slot, diskId.toString(), redeemed));
    }

    public static void handle(ServerPlayer player, PhoneDiskInsertPayload payload) {
        if (!player.isCreative() || payload.slot() < 0 || payload.slot() >= player.getInventory().getContainerSize()) return;
        ItemStack phone = player.getInventory().getItem(payload.slot());
        if (!phone.is(AntOSObjects.ANTROID_PHONE.get())) return;
        ResourceLocation diskId = ResourceLocation.tryParse(payload.diskId());
        if (diskId == null) return;
        ItemStack floppy = new ItemStack(AntOSObjects.FLOPPY_DISK.get());
        AntOSObjects.setFloppyDiskId(floppy, diskId);
        AntroidPhoneItem.insertDisk(player, phone, floppy, payload.redeemed());
        player.inventoryMenu.broadcastChanges();
    }
}
