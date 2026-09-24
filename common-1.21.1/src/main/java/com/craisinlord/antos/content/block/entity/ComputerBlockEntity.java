package com.craisinlord.antos.content.block.entity;

import com.craisinlord.antos.content.AntOSObjects;
import com.craisinlord.antos.content.block.ComputerBlock;
import com.craisinlord.antos.content.computer.ComputerFileSystem;
import com.craisinlord.antos.content.computer.ComputerDesktopState;
import com.craisinlord.antos.content.computer.ComputerTaskProgress;
import com.craisinlord.antos.content.computer.ComputerWorkspaceData;
import com.craisinlord.antos.content.computer.ComputerWorkspace;
import com.craisinlord.antos.content.guide.ComputerGuideData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class ComputerBlockEntity extends BlockEntity implements GeoBlockEntity, ComputerWorkspace {
    private static final ResourceLocation INTRODUCTION_DISK = ResourceLocation.fromNamespaceAndPath("antos", "introduction");
    private static final String DISKS_TAG = "Disks";
    private static final String ACTIVE_UNTIL_TAG = "ActiveUntil";
    private static final String PASSWORD_TAG = "Password";
    private static final String AUTHENTICATED_TAG = "Authenticated";
    private static final String AUTHENTICATED_UNTIL_TAG = "AuthenticatedUntil";
    private static final String DESKTOP_TAG = "Desktop";
    private static final String TASK_PROGRESS_TAG = "TaskProgress";
    private static final String WORKSPACE_OWNER_TAG = "WorkspaceOwner";
    private static final String WORKSPACE_ID_TAG = "WorkspaceId";
    private final Set<ResourceLocation> physicalDiskIds = new LinkedHashSet<>();
    private final ComputerFileSystem fileSystem = new ComputerFileSystem();
    private final ComputerDesktopState desktopState = new ComputerDesktopState();
    private final ComputerTaskProgress taskProgress = new ComputerTaskProgress();
    private final AnimatableInstanceCache animatableCache = GeckoLibUtil.createInstanceCache(this);
    private static final RawAnimation ON = RawAnimation.begin().thenLoop("on_state");
    private static final RawAnimation OFF = RawAnimation.begin().thenLoop("off_state");
    private static final RawAnimation TURN_ON = RawAnimation.begin().thenPlay("turn_on");
    private static final RawAnimation TURN_OFF = RawAnimation.begin().thenPlay("turn_off");
    private long activeUntil;
    private String password = "";
    private boolean authenticated;
    private long authenticatedUntil;
    private java.util.UUID activeUser;
    private java.util.UUID workspaceOwner;
    private java.util.UUID workspaceId;
    private boolean lastActive;

    public ComputerBlockEntity(BlockPos pos, BlockState state, Supplier<? extends BlockEntityType<ComputerBlockEntity>> type) {
        super(type.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ComputerBlockEntity computer) {
        boolean active = computer.isActive();
        if (!level.isClientSide && state.getValue(ComputerBlock.ACTIVE) != active) {
            level.setBlock(pos, state.setValue(ComputerBlock.ACTIVE, active), 3);
        }
        if (active != computer.lastActive) {
            computer.lastActive = active;
            computer.triggerAnim("transitions", active ? "turn_on" : "turn_off");
        }
        if (!level.isClientSide && computer.activeUntil > 0L && level.getGameTime() >= computer.activeUntil && !level.hasNeighborSignal(pos)) {
            computer.activeUntil = 0L;
            computer.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
        if (!level.isClientSide && computer.authenticated && (computer.activeUser == null ||
                (level instanceof net.minecraft.server.level.ServerLevel serverLevel && serverLevel.getServer().getPlayerList().getPlayer(computer.activeUser) == null))) {
            computer.clearAuthentication();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    public boolean insert(ItemStack stack, Player player) {
        if (level == null || level.isClientSide || player.level() != level || !level.hasChunkAt(worldPosition)
                || level.getBlockEntity(worldPosition) != this
                || player.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D) > 64.0D
                || !stack.is(AntOSObjects.FLOPPY_DISK.get())) {
            return false;
        }
        ResourceLocation diskId = stack.get(AntOSObjects.FLOPPY_DISK_COMPONENT.get());
        ComputerGuideData.Disk disk = diskId == null ? null : ComputerGuideData.disk(diskId);
        if (disk == null) return false;
        ComputerWorkspaceData accountData = level instanceof net.minecraft.server.level.ServerLevel serverLevel && workspaceOwner != null
                ? ComputerWorkspaceData.access(serverLevel.getServer()) : null;
        boolean profileOwned = accountData != null && accountData.account(workspaceOwner) != null;
        if (profileOwned && (!(player instanceof ServerPlayer serverPlayer)
                || com.craisinlord.antos.content.network.AnternetAccountHandler.session(serverPlayer) == null
                || !workspaceOwner.equals(com.craisinlord.antos.content.network.AnternetAccountHandler.session(serverPlayer).accountId()))) return false;
        if (profileOwned) {
            CompoundTag snapshot = accountData.workspaceSnapshot(workspaceId);
            if (snapshot != null) {
                fileSystem.load(snapshot, level.registryAccess());
                desktopState.load(snapshot.getCompound(DESKTOP_TAG));
                taskProgress.load(snapshot.getCompound(TASK_PROGRESS_TAG));
            }
        }
        boolean alreadyInstalled = profileOwned ? accountData.profileDisks(workspaceOwner).contains(diskId) : physicalDiskIds.contains(diskId);
        if (alreadyInstalled) {
            activate();
            return true;
        }
        if (profileOwned) accountData.addProfileDisk(workspaceOwner, diskId);
        else this.physicalDiskIds.add(diskId);
        for (ResourceLocation taskId : disk.tasks()) taskProgress.grant(taskId);
        for (ResourceLocation entryId : disk.entries()) taskProgress.unlockArchiveEntry(entryId);
        if (!disk.wallpaper().isBlank()) {
            try {
                ResourceLocation wallpaperId = ResourceLocation.parse(disk.wallpaper());
                if (ComputerGuideData.wallpaper(wallpaperId) != null) desktopState.unlockWallpaper(wallpaperId);
            } catch (RuntimeException ignored) {
            }
        }
        stack.shrink(1);
        activate();
        setChanged();
        if (profileOwned) saveAccountWorkspace();
        else saveWorkspaceProgress();
        if (profileOwned && player instanceof ServerPlayer serverPlayer) {
            com.craisinlord.antos.content.network.ComputerAccessHandler.sendArchive(serverPlayer, this);
        }
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        return true;
    }

    public boolean requiresAccountSession() {
        return level instanceof net.minecraft.server.level.ServerLevel serverLevel && workspaceOwner != null
                && ComputerWorkspaceData.access(serverLevel.getServer()).account(workspaceOwner) != null;
    }

    public void activate() {
        if (level != null && !level.isClientSide) {
            activeUntil = Math.max(activeUntil, level.getGameTime() + 40L);
            if (!getBlockState().getValue(ComputerBlock.ACTIVE)) {
                level.setBlock(worldPosition, getBlockState().setValue(ComputerBlock.ACTIVE, true), 3);
            }
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public boolean isActive() {
        return level != null && (level.hasNeighborSignal(worldPosition) || isAuthenticated());
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "state", state -> state.setAndContinue(isActive() ? ON : OFF)));
        controllers.add(new AnimationController<>(this, "transitions", state -> PlayState.STOP)
                .triggerableAnim("turn_on", TURN_ON)
                .triggerableAnim("turn_off", TURN_OFF));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animatableCache;
    }

    public List<ResourceLocation> diskIds() {
        Set<ResourceLocation> installed = physicalDiskIds;
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel && workspaceOwner != null) {
            ComputerWorkspaceData data = ComputerWorkspaceData.access(serverLevel.getServer());
            if (data.account(workspaceOwner) != null) installed = new LinkedHashSet<>(data.profileDisks(workspaceOwner));
        }
        List<ResourceLocation> ids = new ArrayList<>(installed.size() + 1);
        ids.add(INTRODUCTION_DISK);
        ids.addAll(installed);
        return List.copyOf(ids);
    }

    public boolean ejectOne(Player player) {
        if (physicalDiskIds.isEmpty() || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        return ejectOne(serverPlayer, physicalDiskIds.iterator().next());
    }

    public boolean ejectOne(ServerPlayer player, ResourceLocation diskId) {
        if (level == null || level.isClientSide || diskId == null || INTRODUCTION_DISK.equals(diskId)) {
            return false;
        }
        ComputerWorkspaceData accountData = null;
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel && workspaceOwner != null) {
            ComputerWorkspaceData candidate = ComputerWorkspaceData.access(serverLevel.getServer());
            if (candidate.account(workspaceOwner) != null) accountData = candidate;
        }
        if (accountData != null) {
            if (!accountData.removeProfileDisk(workspaceOwner, diskId)) return false;
        } else {
            if (!playerCanReach(player) || !physicalDiskIds.remove(diskId)) return false;
        }
        ItemStack disk = diskStack(diskId);
        net.minecraft.world.entity.item.ItemEntity dropped = player.drop(disk, false);
        if (dropped == null) {
            if (accountData != null) accountData.addProfileDisk(workspaceOwner, diskId);
            else physicalDiskIds.add(diskId);
            return false;
        }
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        return true;
    }

    public boolean hasPassword() {
        return !password.isEmpty();
    }

    public boolean hasPasswordForAccess() {
        return hasPassword();
    }

    public boolean setPassword(ServerPlayer player, String newPassword) {
        if (newPassword == null || newPassword.isBlank() || !isAuthenticated()) {
            return false;
        }
        password = newPassword;
        setChanged();
        return true;
    }

    public boolean initializePassword(ServerPlayer player, String newPassword) {
        if (hasPassword() || newPassword == null || newPassword.isBlank() || !claimUser(player)
                || !bindWorkspaceOwner(player.getUUID())) {
            return false;
        }
        password = newPassword;
        authenticated = true;
        authenticatedUntil = 0L;
        setChanged();
        return true;
    }

    public boolean initializePassword(String newPassword) {
        return false;
    }

    public boolean authenticate(ServerPlayer player, String attemptedPassword) {
        if (!hasPassword() || !password.equals(attemptedPassword) || !claimUser(player)) {
            return false;
        }
        authenticated = true;
        authenticatedUntil = 0L;
        setChanged();
        return true;
    }

    public boolean authenticate(String attemptedPassword) {
        return false;
    }

    public boolean isAuthenticated() {
        if (!authenticated) {
            return false;
        }
        return true;
    }

    public boolean isAuthenticatedBy(ServerPlayer player) {
        return isAuthenticated() && activeUser != null && activeUser.equals(player.getUUID());
    }

    public boolean claimUser(ServerPlayer player) {
        if (level == null || level.isClientSide || !playerCanReach(player)) {
            return false;
        }
        if (activeUser != null && !activeUser.equals(player.getUUID()) && isAuthenticated()) {
            return false;
        }
        activeUser = player.getUUID();
        return true;
    }

    public boolean playerCanReach(ServerPlayer player) {
        return level != null && player.level() == level && level.hasChunkAt(worldPosition)
                && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D) <= 64.0D;
    }

    public ComputerFileSystem fileSystem() {
        return fileSystem;
    }

    public ComputerDesktopState desktopState() {
        return desktopState;
    }

    public ComputerTaskProgress taskProgress() {
        return taskProgress;
    }

    public java.util.UUID workspaceOwner() {
        return workspaceOwner;
    }

    public java.util.UUID workspaceId() {
        return workspaceId;
    }

    public boolean isRemoteWorkspace() {
        return false;
    }

    public boolean bindWorkspaceOwner(java.util.UUID owner) {
        if (owner == null || level == null || level.isClientSide || workspaceOwner != null && !workspaceOwner.equals(owner)) return false;
        workspaceOwner = owner;
        setChanged();
        return true;
    }

    public boolean bindWorkspaceId(java.util.UUID id) {
        if (id == null || level == null || level.isClientSide || workspaceId != null && !workspaceId.equals(id)) return false;
        workspaceId = id;
        setChanged();
        return true;
    }

    /** Archive discoveries from inserted disks remain unlocked after ejecting the disk. */
    public void captureDiskArchiveEntries() {
        for (ResourceLocation diskId : diskIds()) {
            ComputerGuideData.Disk disk = ComputerGuideData.disk(diskId);
            if (disk != null) disk.entries().forEach(taskProgress::unlockArchiveEntry);
        }
    }

    public void saveWorkspaceProgress() {
        if (workspaceId != null && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            ComputerWorkspaceData data = ComputerWorkspaceData.access(serverLevel.getServer());
            if (workspaceOwner != null && data.account(workspaceOwner) != null) {
                CompoundTag snapshot = data.workspaceSnapshot(workspaceId);
                if (snapshot == null) snapshot = new CompoundTag();
                snapshot.put(TASK_PROGRESS_TAG, taskProgress.save());
                data.saveWorkspaceSnapshot(workspaceId, snapshot);
            } else {
                data.save(workspaceId, taskProgress);
            }
        }
    }

    public boolean authenticateAccount(ServerPlayer player, ComputerWorkspaceData.AccountInfo account) {
        if (account == null || level == null || level.isClientSide || !playerCanReach(player)
                || workspaceOwner != null && !workspaceOwner.equals(account.accountId()) && !workspaceOwner.equals(player.getUUID())
                || activeUser != null && !activeUser.equals(player.getUUID()) && isAuthenticated()) return false;
        ComputerWorkspaceData data = ComputerWorkspaceData.access(player.server);
        Set<ResourceLocation> legacyDisks = new LinkedHashSet<>(physicalDiskIds);
        CompoundTag snapshot = data.workspaceSnapshot(account.workspaceId());
        boolean hasLegacyState = workspaceId != null || workspaceOwner != null || taskProgress.hasRecoverableProgress()
                || fileSystem.list().size() > 3 || hasCustomDesktopState();
        if (snapshot == null || snapshot.isEmpty()) {
            if (hasLegacyState && workspaceOwner != null && !workspaceOwner.equals(player.getUUID())
                    && !workspaceOwner.equals(account.accountId())) return false;
        }
        if (snapshot == null) snapshot = new CompoundTag();
        if (snapshot.isEmpty() && hasLegacyState && (workspaceOwner == null || workspaceOwner.equals(player.getUUID()) || workspaceOwner.equals(account.accountId()))) {
            snapshot = saveWorkspaceSnapshot(player.serverLevel().registryAccess());
        } else {
            fileSystem.load(snapshot, player.serverLevel().registryAccess());
            desktopState.load(snapshot.getCompound(DESKTOP_TAG));
            taskProgress.load(snapshot.getCompound(TASK_PROGRESS_TAG));
        }
        workspaceOwner = account.accountId();
        workspaceId = account.workspaceId();
        for (ResourceLocation diskId : legacyDisks) data.addProfileDisk(account.accountId(), diskId);
        physicalDiskIds.clear();
        activeUser = player.getUUID();
        authenticated = true;
        authenticatedUntil = 0L;
        captureDiskArchiveEntries();
        data.saveWorkspaceSnapshot(workspaceId, saveWorkspaceSnapshot(player.serverLevel().registryAccess()));
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        return true;
    }

    private boolean hasCustomDesktopState() {
        return !ComputerDesktopState.DEFAULT_WALLPAPER.equals(desktopState.selectedWallpaper())
                || desktopState.unlockedWallpaperIds().size() > 1
                || !desktopState.installedProgramIds().isEmpty()
                || !desktopState.iconPositions().isEmpty();
    }

    public void saveAccountWorkspace() {
        if (workspaceId == null || level == null || !(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
        if (ComputerWorkspaceData.access(serverLevel.getServer()).account(workspaceOwner) != null) {
            ComputerWorkspaceData.access(serverLevel.getServer()).saveWorkspaceSnapshot(workspaceId, saveWorkspaceSnapshot(level.registryAccess()));
        }
    }

    private CompoundTag saveWorkspaceSnapshot(HolderLookup.Provider registries) {
        CompoundTag snapshot = new CompoundTag();
        fileSystem.save(snapshot, registries);
        snapshot.put(DESKTOP_TAG, desktopState.save());
        snapshot.put(TASK_PROGRESS_TAG, taskProgress.save());
        return snapshot;
    }

    public boolean unlockWallpaper(ResourceLocation id) {
        return serverDesktopMutation(() -> ComputerGuideData.wallpaper(id) != null && desktopState.unlockWallpaper(id));
    }

    public boolean installProgram(ResourceLocation id) {
        return serverDesktopMutation(() -> desktopState.installProgram(id));
    }

    public boolean uninstallProgram(ResourceLocation id) {
        return serverDesktopMutation(() -> desktopState.uninstallProgram(id));
    }

    public boolean selectWallpaper(ResourceLocation id) {
        return serverDesktopMutation(() -> ComputerGuideData.wallpaper(id) != null && desktopState.selectWallpaper(id));
    }

    public boolean setIconPosition(ResourceLocation id, int x, int y) {
        return serverDesktopMutation(() -> desktopState.setIconPosition(id, x, y));
    }

    public boolean removeIconPosition(ResourceLocation id) {
        return serverDesktopMutation(() -> desktopState.removeIconPosition(id));
    }

    private boolean serverDesktopMutation(java.util.function.BooleanSupplier mutation) {
        if (level == null || level.isClientSide) {
            return false;
        }
        boolean changed = mutation.getAsBoolean();
        if (changed) {
            setChanged();
            saveAccountWorkspace();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        return changed;
    }

    public boolean canUseFileSystem(ServerPlayer player) {
        return level != null && !level.isClientSide && isAuthenticated() && playerCanReach(player) && (activeUser == null || activeUser.equals(player.getUUID()));
    }

    public void logout() {
        saveAccountWorkspace();
        clearAuthentication();
    }

    public void releaseUser(ServerPlayer player) {
        if (activeUser != null && activeUser.equals(player.getUUID())) {
            activeUser = null;
            authenticated = false;
            authenticatedUntil = 0L;
            setChanged();
        }
    }

    public void clearAuthentication() {
        authenticated = false;
        authenticatedUntil = 0L;
        activeUser = null;
        setChanged();
    }

    public long authenticationTimeout() {
        return 0L;
    }

    public boolean hasActiveUser() {
        return activeUser != null && isAuthenticated();
    }

    private static ItemStack diskStack(ResourceLocation diskId) {
        ItemStack stack = new ItemStack(AntOSObjects.FLOPPY_DISK.get());
        stack.set(AntOSObjects.FLOPPY_DISK_COMPONENT.get(), diskId);
        return stack;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        physicalDiskIds.clear();
        ListTag disks = tag.getList(DISKS_TAG, 8);
        for (int index = 0; index < disks.size(); index++) {
            try {
                ResourceLocation diskId = ResourceLocation.parse(disks.getString(index));
                if (!INTRODUCTION_DISK.equals(diskId)) {
                    physicalDiskIds.add(diskId);
                }
            } catch (Exception ignored) {
            }
        }
        activeUntil = tag.getLong(ACTIVE_UNTIL_TAG);
        password = tag.getString(PASSWORD_TAG);
        workspaceOwner = tag.hasUUID(WORKSPACE_OWNER_TAG) ? tag.getUUID(WORKSPACE_OWNER_TAG) : null;
        workspaceId = tag.hasUUID(WORKSPACE_ID_TAG) ? tag.getUUID(WORKSPACE_ID_TAG) : null;
        fileSystem.load(tag, registries);
        desktopState.load(tag.getCompound(DESKTOP_TAG));
        taskProgress.load(tag.getCompound(TASK_PROGRESS_TAG));
        authenticated = false;
        authenticatedUntil = 0L;
        activeUser = null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag disks = new ListTag();
        for (ResourceLocation diskId : physicalDiskIds) {
            disks.add(StringTag.valueOf(diskId.toString()));
        }
        tag.put(DISKS_TAG, disks);
        tag.putLong(ACTIVE_UNTIL_TAG, activeUntil);
        boolean accountWorkspace = level instanceof net.minecraft.server.level.ServerLevel serverLevel && workspaceOwner != null
                && ComputerWorkspaceData.access(serverLevel.getServer()).account(workspaceOwner) != null;
        if (!accountWorkspace) {
            tag.putString(PASSWORD_TAG, password);
            fileSystem.save(tag, registries);
            tag.put(DESKTOP_TAG, desktopState.save());
            tag.put(TASK_PROGRESS_TAG, taskProgress.save());
        }
        if (workspaceOwner != null) tag.putUUID(WORKSPACE_OWNER_TAG, workspaceOwner);
        if (workspaceId != null) tag.putUUID(WORKSPACE_ID_TAG, workspaceId);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("HasPassword", hasPassword());
        tag.putBoolean(AUTHENTICATED_TAG, isAuthenticated());
        ListTag disks = new ListTag();
        for (ResourceLocation diskId : physicalDiskIds) disks.add(StringTag.valueOf(diskId.toString()));
        tag.put(DISKS_TAG, disks);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
}


