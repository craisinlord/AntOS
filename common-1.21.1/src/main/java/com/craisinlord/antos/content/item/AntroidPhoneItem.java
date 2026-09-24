package com.craisinlord.antos.content.item;

import com.craisinlord.antos.content.client.AntOSClientHooks;
import com.craisinlord.antos.content.AntOSObjects;
import com.craisinlord.antos.content.computer.ComputerDesktopState;
import com.craisinlord.antos.content.computer.ComputerTaskProgress;
import com.craisinlord.antos.content.computer.ComputerWorkspaceData;
import com.craisinlord.antos.content.guide.ComputerGuideData;
import com.craisinlord.antos.content.network.AnternetAccountHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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

import java.util.function.Consumer;

/** A portable client for a computer that has been paired to the item. */
public final class AntroidPhoneItem extends Item implements GeoItem {
    private static final RawAnimation ON = RawAnimation.begin().thenLoop("on");
    private final AnimatableInstanceCache animatableCache = GeckoLibUtil.createInstanceCache(this);

    public AntroidPhoneItem(Properties properties) {
        super(properties);
        GeoItem.registerSyncedAnimatable(this);
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level,
                                                                        net.minecraft.world.entity.player.Player player,
                                                                        net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) AntOSClientHooks.openPhone();
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack phone, ItemStack carried, Slot slot, ClickAction action, Player player, SlotAccess access) {
        if (action != ClickAction.SECONDARY || !carried.is(AntOSObjects.FLOPPY_DISK.get())) return false;
        if (player instanceof ServerPlayer serverPlayer) installDisk(serverPlayer, carried);
        return true;
    }

    private static void installDisk(ServerPlayer player, ItemStack floppy) {
        ComputerWorkspaceData.AccountInfo account = AnternetAccountHandler.session(player);
        if (account == null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("Sign in to Anternet before installing a disk."), true);
            return;
        }
        ResourceLocation diskId = floppy.get(AntOSObjects.FLOPPY_DISK_COMPONENT.get());
        if (diskId == null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("This floppy has no disk data."), true);
            return;
        }
        ComputerWorkspaceData data = ComputerWorkspaceData.access(player.serverLevel().getServer());
        if (data.account(account.accountId()) == null) return;
        if (data.profileDisks(account.accountId()).contains(diskId)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("That disk is already installed."), true);
            return;
        }
        if (!data.addProfileDisk(account.accountId(), diskId)) return;
        ComputerGuideData.Disk disk = ComputerGuideData.disk(diskId);
        var snapshot = data.workspaceSnapshot(account.workspaceId());
        if (snapshot == null) snapshot = new net.minecraft.nbt.CompoundTag();
        ComputerTaskProgress progress = new ComputerTaskProgress();
        progress.load(snapshot.getCompound("TaskProgress"));
        ComputerDesktopState desktop = new ComputerDesktopState();
        desktop.load(snapshot.getCompound("Desktop"));
        if (disk != null) {
            disk.tasks().forEach(progress::grant);
            disk.entries().forEach(progress::unlockArchiveEntry);
            if (!disk.wallpaper().isBlank()) {
                try {
                    ResourceLocation wallpaperId = ResourceLocation.parse(disk.wallpaper());
                    if (ComputerGuideData.wallpaper(wallpaperId) != null) desktop.unlockWallpaper(wallpaperId);
                } catch (RuntimeException ignored) { }
            }
        }
        snapshot.put("TaskProgress", progress.save());
        snapshot.put("Desktop", desktop.save());
        data.saveWorkspaceSnapshot(account.workspaceId(), snapshot);
        floppy.shrink(1);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("Disk installed to your Anternet profile."), true);
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
