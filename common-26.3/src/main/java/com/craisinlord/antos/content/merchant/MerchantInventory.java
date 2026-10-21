package com.craisinlord.antos.content.merchant;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;

public final class MerchantInventory {
    private MerchantInventory() { }

    public static List<ItemStack> take(List<ItemStack> inventory, ItemStack prototype, int amount) {
        if (prototype.isEmpty() || amount < 1) throw new IllegalArgumentException("invalid_quantity");
        List<ItemStack> result = new ArrayList<>();
        int remaining = amount;
        for (ItemStack source : inventory) {
            ItemStack item = source.copy();
            if (remaining > 0 && ItemStack.isSameItemSameComponents(item, prototype)) {
                int taken = Math.min(remaining, item.getCount());
                item.shrink(taken);
                remaining -= taken;
            }
            result.add(item);
        }
        if (remaining > 0) throw new IllegalArgumentException("insufficient_payment");
        return result;
    }
}
