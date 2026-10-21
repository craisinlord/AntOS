package com.craisinlord.antos.content.merchant;

import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class MerchantInventoryTest {
    public static void run() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ItemStack named = new ItemStack(Items.DIAMOND, 7);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Keep these"));
        List<ItemStack> original = List.of(new ItemStack(Items.DIAMOND, 2), named, new ItemStack(Items.DIAMOND, 5), ItemStack.EMPTY);
        List<ItemStack> paid = MerchantInventory.take(original, new ItemStack(Items.DIAMOND), 6);
        check(paid.get(0).isEmpty() && paid.get(2).getCount() == 1, "Payment combines matching main inventory stacks");
        check(paid.get(1).getCount() == 7 && paid.get(1).getHoverName().getString().equals("Keep these"), "Plain payments preserve named variants");
        check(original.get(0).getCount() == 2 && original.get(2).getCount() == 5, "Planning a payment does not mutate inventory");
        boolean failed = false;
        try { MerchantInventory.take(original, new ItemStack(Items.DIAMOND), 8); } catch (IllegalArgumentException exception) { failed = true; }
        check(failed && original.get(0).getCount() == 2, "Insufficient plain currency must not consume special items or mutate stock");
        List<ItemStack> deposited = MerchantInventory.take(original, named, 3);
        check(deposited.get(1).getCount() == 4 && deposited.get(0).getCount() == 2 && deposited.get(2).getCount() == 5, "Listing a special variant deposits only that exact variant");
        ItemStack damaged = new ItemStack(Items.IRON_PICKAXE);
        damaged.setDamageValue(12);
        failed = false;
        try { MerchantInventory.take(List.of(damaged), new ItemStack(Items.IRON_PICKAXE), 1); } catch (IllegalArgumentException exception) { failed = true; }
        check(failed && damaged.getDamageValue() == 12, "Damaged tools must not count as plain payment");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
