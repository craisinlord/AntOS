package com.craisinlord.antos.content.item;

import com.craisinlord.antos.content.AntOSObjects;
import com.craisinlord.antos.content.client.AntOSClientHooks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;

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
        ResourceLocation id = stack.get(AntOSObjects.FLOPPY_DISK_COMPONENT.get());
        if (id != null) {
            var disk = com.craisinlord.antos.content.guide.ComputerGuideData.disk(id);
            if (disk != null) {
                return displayText(disk.titleKey());
            }
        }
        return Component.translatable("item.antos.floppy_disk");
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ResourceLocation id = stack.get(AntOSObjects.FLOPPY_DISK_COMPONENT.get());
        var disk = id == null ? null : com.craisinlord.antos.content.guide.ComputerGuideData.disk(id);
        if (disk != null) {
            var category = com.craisinlord.antos.content.guide.ComputerGuideData.category(disk.category());
            if (category != null) tooltip.add(displayText(category.titleKey()).copy().withStyle(Style.EMPTY.withColor(TextColor.fromRgb(category.color()))));
            if (disk.features().consumable() && redeemed(stack)) tooltip.add(Component.translatable("item.antos.floppy_disk.redeemed").withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("item.antos.floppy_disk.tooltip").withStyle(ChatFormatting.DARK_GRAY));
    }

    @SuppressWarnings("deprecation")
    public static boolean redeemed(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.getUnsafe().getBoolean(REDEEMED_TAG);
    }

    public static ItemStack ejectedCopy(ResourceLocation diskId) {
        ItemStack stack = new ItemStack(AntOSObjects.FLOPPY_DISK.get());
        stack.set(AntOSObjects.FLOPPY_DISK_COMPONENT.get(), diskId);
        var disk = com.craisinlord.antos.content.guide.ComputerGuideData.disk(diskId);
        if (disk != null && disk.features().consumable()) CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(REDEEMED_TAG, true));
        return stack;
    }

    private static Component displayText(String key) {
        Component text = Component.translatable(key);
        return AntOSClientHooks.isClient() ? Component.literal(titleCase(text.getString())) : text;
    }

    private static String titleCase(String text) {
        if (!text.equals(text.toUpperCase(Locale.ROOT))) return text;
        StringBuilder result = new StringBuilder(text.length());
        boolean wordStart = true;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            result.append(wordStart ? character : Character.toLowerCase(character));
            wordStart = Character.isWhitespace(character);
        }
        return result.toString();
    }
}
