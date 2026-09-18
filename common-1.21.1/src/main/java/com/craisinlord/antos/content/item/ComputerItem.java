package com.craisinlord.antos.content.item;

import com.craisinlord.antos.content.block.ComputerBlock;
import com.craisinlord.antos.content.AntOSObjects;
import com.craisinlord.antos.content.block.entity.ComputerBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

public final class ComputerItem extends BlockItem {
    public ComputerItem(ComputerBlock block, Properties properties) {
        super(block, properties);
    }

    public static void storeComputerState(ItemStack stack, ComputerBlockEntity computer) {
        if (computer.getLevel() == null) return;
        storeComputerState(stack, computer, computer.getLevel().registryAccess());
    }

    public static void storeComputerState(ItemStack stack, ComputerBlockEntity computer, HolderLookup.Provider registries) {
        BlockItem.setBlockEntityData(stack, AntOSObjects.COMPUTER_BLOCK_ENTITY.get(),
                computer.saveWithoutMetadata(registries));
    }
}


