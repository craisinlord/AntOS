package com.craisinlord.antos.content.item;

import com.craisinlord.antos.content.client.AntOSClientHooks;
import com.craisinlord.antos.content.AntOSObjects;
import com.craisinlord.antos.content.computer.ComputerDesktopState;
import com.craisinlord.antos.content.computer.ComputerTaskProgress;
import com.craisinlord.antos.content.computer.ComputerWorkspaceData;
import com.craisinlord.antos.content.guide.ComputerGuideData;
import com.craisinlord.antos.content.network.AnternetAccountHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class AntroidPhoneItem extends Item implements GeoItem {
    private static final RawAnimation ON = RawAnimation.begin().thenLoop("on");
    private static final String PENDING_DISKS_TAG = "PendingFloppyDisks";
    private final AnimatableInstanceCache animatableCache = GeckoLibUtil.createInstanceCache(this);

    public AntroidPhoneItem(Properties properties) {
        super(properties);
        GeoItem.registerSyncedAnimatable(this);
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level,
                                                                        net.minecraft.world.entity.player.Player player,
                                                                        InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ItemStack otherHand = player.getItemInHand(hand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (otherHand.is(AntOSObjects.FLOPPY_DISK.get())) {
            if (player instanceof ServerPlayer serverPlayer) insertDisk(serverPlayer, stack, otherHand);
            return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (level.isClientSide) AntOSClientHooks.openPhone();
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack phone, ItemStack carried, Slot slot, ClickAction action, Player player, SlotAccess access) {
        if (action != ClickAction.SECONDARY || !carried.is(AntOSObjects.FLOPPY_DISK.get())) return false;
        if (player instanceof ServerPlayer serverPlayer) insertDisk(serverPlayer, phone, carried);
        return true;
    }

    public static boolean insertDisk(ServerPlayer player, ItemStack phone, ItemStack floppy) {
        ResourceLocation diskId = floppy.get(AntOSObjects.FLOPPY_DISK_COMPONENT.get());
        if (diskId == null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("This floppy has no disk data."), true);
            return false;
        }
        if (ComputerGuideData.disk(diskId) == null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("This floppy contains unknown disk data."), true);
            return false;
        }
        List<ResourceLocation> pending = pendingDisks(phone);
        if (pending.contains(diskId)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("That disk is already queued on this phone."), true);
            return false;
        }
        ComputerWorkspaceData.AccountInfo account = AnternetAccountHandler.session(player);
        if (account == null) {
            if (!pending.contains(diskId)) pending.add(diskId);
            writePendingDisks(phone, pending);
            floppy.shrink(1);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("Disk queued on this phone. Sign in to Anternet to sync it."), true);
            return true;
        }
        ComputerWorkspaceData data = ComputerWorkspaceData.access(player.serverLevel().getServer());
        if (data.profileDisks(account.accountId()).contains(diskId)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("That disk is already installed on your Anternet profile."), true);
            return false;
        }
        if (!installProfileDisk(player, account, diskId)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("The disk could not be installed. It was not consumed."), true);
            return false;
        }
        floppy.shrink(1);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("Disk installed to your Anternet profile."), true);
        return true;
    }

    public static int syncPendingDisks(ServerPlayer player, ComputerWorkspaceData.AccountInfo account) {
        int installed = 0;
        for (ItemStack phone : player.getInventory().items) installed += syncPhone(player, phone, account);
        for (ItemStack phone : player.getInventory().offhand) installed += syncPhone(player, phone, account);
        if (installed > 0) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    installed == 1 ? "1 queued phone disk synced to your Anternet profile." : installed + " queued phone disks synced to your Anternet profile."), false);
        }
        return installed;
    }

    private static int syncPhone(ServerPlayer player, ItemStack phone, ComputerWorkspaceData.AccountInfo account) {
        if (!(phone.getItem() instanceof AntroidPhoneItem)) return 0;
        List<ResourceLocation> pending = pendingDisks(phone);
        if (pending.isEmpty()) return 0;
        ComputerWorkspaceData data = ComputerWorkspaceData.access(player.serverLevel().getServer());
        List<ResourceLocation> remaining = new ArrayList<>();
        int installed = 0;
        for (ResourceLocation diskId : pending) {
            if (data.profileDisks(account.accountId()).contains(diskId)) continue;
            if (ComputerGuideData.disk(diskId) != null && installProfileDisk(player, account, diskId)) installed++;
            else remaining.add(diskId);
        }
        writePendingDisks(phone, remaining);
        return installed;
    }

    private static boolean installProfileDisk(ServerPlayer player, ComputerWorkspaceData.AccountInfo account, ResourceLocation diskId) {
        ComputerWorkspaceData data = ComputerWorkspaceData.access(player.serverLevel().getServer());
        if (data.account(account.accountId()) == null || ComputerGuideData.disk(diskId) == null) return false;
        if (data.profileDisks(account.accountId()).contains(diskId)) return true;
        var snapshot = data.workspaceSnapshot(account.workspaceId());
        if (snapshot == null) snapshot = new CompoundTag();
        ComputerTaskProgress progress = new ComputerTaskProgress();
        progress.load(snapshot.getCompound("TaskProgress"));
        ComputerDesktopState desktop = new ComputerDesktopState();
        desktop.load(snapshot.getCompound("Desktop"));
        ComputerGuideData.Disk disk = ComputerGuideData.disk(diskId);
        disk.tasks().forEach(progress::grant);
        disk.entries().forEach(progress::unlockArchiveEntry);
        if (!disk.wallpaper().isBlank()) {
            try {
                ResourceLocation wallpaperId = ResourceLocation.parse(disk.wallpaper());
                if (ComputerGuideData.wallpaper(wallpaperId) != null) desktop.unlockWallpaper(wallpaperId);
            } catch (RuntimeException ignored) { }
        }
        snapshot.put("TaskProgress", progress.save());
        snapshot.put("Desktop", desktop.save());
        if (!data.saveWorkspaceSnapshot(account.workspaceId(), snapshot)) return false;
        return data.addProfileDisk(account.accountId(), diskId) || data.profileDisks(account.accountId()).contains(diskId);
    }

    private static List<ResourceLocation> pendingDisks(ItemStack phone) {
        CustomData customData = phone.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return new ArrayList<>();
        ListTag tag = customData.copyTag().getList(PENDING_DISKS_TAG, Tag.TAG_STRING);
        List<ResourceLocation> pending = new ArrayList<>();
        for (int index = 0; index < tag.size(); index++) {
            try {
                ResourceLocation id = ResourceLocation.parse(tag.getString(index));
                if (!pending.contains(id)) pending.add(id);
            } catch (RuntimeException ignored) { }
        }
        return pending;
    }

    private static void writePendingDisks(ItemStack phone, List<ResourceLocation> pending) {
        CompoundTag tag = phone.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (pending.isEmpty()) tag.remove(PENDING_DISKS_TAG);
        else {
            ListTag disks = new ListTag();
            pending.forEach(id -> disks.add(StringTag.valueOf(id.toString())));
            tag.put(PENDING_DISKS_TAG, disks);
        }
        if (tag.isEmpty()) phone.remove(DataComponents.CUSTOM_DATA);
        else phone.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "screen", state -> state.setAndContinue(ON)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animatableCache;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoItemRenderer<AntroidPhoneItem> renderer;

            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getGeoItemRenderer() {
                if (renderer == null) renderer = new GeoItemRenderer<>(new AntroidPhoneModel());
                return renderer;
            }
        });
    }
}
