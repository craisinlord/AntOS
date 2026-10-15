package com.craisinlord.antos.content.item;

import com.craisinlord.antos.content.AntOSObjects;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.InteractionHand;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class FloppyDiskItem extends Item {
    public static final String REDEEMED_TAG = "AntOSRedeemed";
    public FloppyDiskItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public net.minecraft.world.InteractionResult use(net.minecraft.world.level.Level level,
                                                   net.minecraft.world.entity.player.Player player,
                                                   InteractionHand hand) {
        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack phone = player.getItemInHand(otherHand);
        ItemStack floppy = player.getItemInHand(hand);
        if (phone.getItem() instanceof AntroidPhoneItem) {
            if (player instanceof ServerPlayer serverPlayer) AntroidPhoneItem.insertDisk(serverPlayer, phone, floppy);
            return sidedSuccess(level, floppy);
        }
        return super.use(level, player, hand);
    }

    @Override
    public Component getName(ItemStack stack) {
        Identifier id = stack.get(AntOSObjects.FLOPPY_DISK_COMPONENT.get());
        if (id != null) {
            var disk = com.craisinlord.antos.content.guide.ComputerGuideData.disk(id);
            if (disk != null) {
                return Component.translatable(disk.titleKey());
            }
        }
        return Component.translatable("item.antos.floppy_disk");
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.antos.floppy_disk.tooltip"));
        Identifier id = stack.get(AntOSObjects.FLOPPY_DISK_COMPONENT.get());
        if (id != null) {
            var disk = com.craisinlord.antos.content.guide.ComputerGuideData.disk(id);
            if (disk != null) {
                var category = com.craisinlord.antos.content.guide.ComputerGuideData.category(disk.category());
                if (category != null) tooltip.accept(Component.translatable(category.titleKey()));
                if (!disk.descriptionKey().isBlank()) tooltip.accept(Component.translatable(disk.descriptionKey()));
                if (disk.features().consumable() && redeemed(stack)) tooltip.accept(Component.translatable("item.antos.floppy_disk.redeemed").withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        }
    }

    @SuppressWarnings("deprecation")
    public static boolean redeemed(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBooleanOr(REDEEMED_TAG, false);
    }

    public static ItemStack ejectedCopy(Identifier diskId) {
        ItemStack stack = new ItemStack(AntOSObjects.FLOPPY_DISK.get());
        stack.set(AntOSObjects.FLOPPY_DISK_COMPONENT.get(), diskId);
        var disk = com.craisinlord.antos.content.guide.ComputerGuideData.disk(diskId);
        if (disk != null && disk.features().consumable()) CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(REDEEMED_TAG, true));
        return stack;
    }

    private static net.minecraft.world.InteractionResult sidedSuccess(net.minecraft.world.level.Level level, ItemStack stack) {
        return (level.isClientSide() ? net.minecraft.world.InteractionResult.SUCCESS : net.minecraft.world.InteractionResult.CONSUME)
                .heldItemTransformedTo(stack);
    }
}


