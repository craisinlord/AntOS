package com.craisinlord.antos.content.item;

import com.craisinlord.antos.content.AntOSObjects;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class FloppyDiskItem extends Item {
    public static final String REDEEMED_TAG = "AntOSRedeemed";
    public FloppyDiskItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level,
                                                   net.minecraft.world.entity.player.Player player,
                                                   InteractionHand hand) {
        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack phone = player.getItemInHand(otherHand);
        ItemStack floppy = player.getItemInHand(hand);
        if (phone.getItem() instanceof AntroidPhoneItem) {
            if (player instanceof ServerPlayer serverPlayer) AntroidPhoneItem.insertDisk(serverPlayer, phone, floppy);
            return InteractionResultHolder.sidedSuccess(floppy, level.isClientSide);
        }
        return super.use(level, player, hand);
    }

    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation id = AntOSObjects.getFloppyDiskId(stack);
        if (id != null) {
            var disk = com.craisinlord.antos.content.guide.ComputerGuideData.disk(id);
            if (disk != null) {
                return Component.translatable(disk.titleKey());
            }
        }
        return Component.translatable("item.antos.floppy_disk");
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.antos.floppy_disk.tooltip"));
        ResourceLocation id = AntOSObjects.getFloppyDiskId(stack);
        if (id != null) {
            var disk = com.craisinlord.antos.content.guide.ComputerGuideData.disk(id);
            if (disk != null) {
                var category = com.craisinlord.antos.content.guide.ComputerGuideData.category(disk.category());
                if (category != null) tooltip.add(Component.translatable(category.titleKey()));
                if (!disk.descriptionKey().isBlank()) tooltip.add(Component.translatable(disk.descriptionKey()));
                if (disk.features().consumable() && redeemed(stack)) tooltip.add(Component.translatable("item.antos.floppy_disk.redeemed").withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        }
    }

    public static boolean redeemed(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(REDEEMED_TAG);
    }

    public static ItemStack ejectedCopy(ResourceLocation diskId) {
        ItemStack stack = new ItemStack(AntOSObjects.FLOPPY_DISK.get());
        AntOSObjects.setFloppyDiskId(stack, diskId);
        var disk = com.craisinlord.antos.content.guide.ComputerGuideData.disk(diskId);
        if (disk != null && disk.features().consumable()) stack.getOrCreateTag().putBoolean(REDEEMED_TAG, true);
        return stack;
    }
}


