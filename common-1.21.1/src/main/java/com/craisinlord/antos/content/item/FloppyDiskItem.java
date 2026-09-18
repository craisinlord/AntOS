package com.craisinlord.antos.content.item;

import com.craisinlord.antos.content.AntOSObjects;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class FloppyDiskItem extends Item {
    public FloppyDiskItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation id = stack.get(AntOSObjects.FLOPPY_DISK_COMPONENT.get());
        if (id != null) {
            var disk = com.craisinlord.antos.content.guide.ComputerGuideData.disk(id);
            if (disk != null) {
                return Component.translatable(disk.titleKey());
            }
        }
        return Component.translatable("item.antos.floppy_disk");
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.antos.floppy_disk.tooltip"));
        ResourceLocation id = stack.get(AntOSObjects.FLOPPY_DISK_COMPONENT.get());
        if (id != null) {
            var disk = com.craisinlord.antos.content.guide.ComputerGuideData.disk(id);
            if (disk != null) {
                var category = com.craisinlord.antos.content.guide.ComputerGuideData.category(disk.category());
                if (category != null) tooltip.add(Component.translatable(category.titleKey()));
            }
        }
    }
}


