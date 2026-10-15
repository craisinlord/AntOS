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
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.core.UUIDUtil;

import java.util.List;
import java.util.function.Supplier;
import com.geckolib.animatable.GeoBlockEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;

public final class ComputerBlockEntity extends BlockEntity implements GeoBlockEntity, ComputerWorkspace {
    private static final Identifier INTRODUCTION_DISK = Identifier.fromNamespaceAndPath("antos", "introduction");
    private static final String ACTIVE_UNTIL_TAG = "ActiveUntil";
    private static final String PASSWORD_TAG = "Password";
    private static final String AUTHENTICATED_TAG = "Authenticated";
    private static final String AUTHENTICATED_UNTIL_TAG = "AuthenticatedUntil";
    private static final String DESKTOP_TAG = "Desktop";
    private static final String TASK_PROGRESS_TAG = "TaskProgress";
    private static final String WORKSPACE_OWNER_TAG = "WorkspaceOwner";
    private static final String WORKSPACE_ID_TAG = "WorkspaceId";
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
    private boolean neighborSignal;
    private boolean neighborSignalKnown;
    private long neighborSignalCheckedAt;

    public ComputerBlockEntity(BlockPos pos, BlockState state, Supplier<? extends BlockEntityType<ComputerBlockEntity>> type) {
        super(type.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ComputerBlockEntity computer) {
        if (level.isClientSide()) {
            boolean active = state.getValue(ComputerBlock.ACTIVE);
            if (active != computer.lastActive) {
                computer.lastActive = active;
                computer.triggerAnim("transitions", active ? "turn_on" : "turn_off");
            }
            return;
        }
        boolean powered = computer.neighborSignal(level, pos);
        boolean active = powered || computer.isAuthenticated();
        if (state.getValue(ComputerBlock.ACTIVE) != active) {
            level.setBlock(pos, state.setValue(ComputerBlock.ACTIVE, active), 3);
        }
        if (active != computer.lastActive) {
            computer.lastActive = active;
            computer.triggerAnim("transitions", active ? "turn_on" : "turn_off");
        }
        if (computer.activeUntil > 0L && level.getGameTime() >= computer.activeUntil && !powered) {
            computer.activeUntil = 0L;
            computer.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
        if (computer.authenticated && (computer.activeUser == null || level.getGameTime() % 20L == 0L
                && level instanceof net.minecraft.server.level.ServerLevel serverLevel && serverLevel.getServer().getPlayerList().getPlayer(computer.activeUser) == null)) {
            computer.clearAuthentication();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    private boolean neighborSignal(Level level, BlockPos pos) {
        long now = level.getGameTime();
        if (!neighborSignalKnown || now - neighborSignalCheckedAt >= 20L || now < neighborSignalCheckedAt) {
            neighborSignal = level.hasNeighborSignal(pos);
            neighborSignalKnown = true;
            neighborSignalCheckedAt = now;
        }
        return neighborSignal;
    }

    public void invalidateNeighborSignal() {
        neighborSignalKnown = false;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide()) com.craisinlord.antos.content.network.AnternetAccountHandler.forgetDevice(this);
    }

    public boolean insert(ItemStack stack, Player player) {
        if (level == null || level.isClientSide() || player.level() != level || !level.hasChunkAt(worldPosition)
                || level.getBlockEntity(worldPosition) != this
                || player.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D) > 64.0D
                || !stack.is(AntOSObjects.FLOPPY_DISK.get())) {
            return false;
        }
        Identifier diskId = stack.get(AntOSObjects.FLOPPY_DISK_COMPONENT.get());
        ComputerGuideData.Disk disk = diskId == null ? null : ComputerGuideData.disk(diskId);
        if (disk == null) return false;
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        ComputerWorkspaceData.AccountInfo account = com.craisinlord.antos.content.network.AnternetAccountHandler.session(serverPlayer);
        if (account == null) return false;
        if (workspaceOwner == null && !authenticateAccount(serverPlayer, account)) return false;
        if (!account.accountId().equals(workspaceOwner) || workspaceId == null) return false;
        ComputerWorkspaceData accountData = ComputerWorkspaceData.access(serverPlayer.level().getServer());
        CompoundTag snapshot = accountData.workspaceSnapshot(workspaceId);
        if (snapshot != null) {
            fileSystem.load(snapshot, level.registryAccess());
            desktopState.load(snapshot.getCompoundOrEmpty(DESKTOP_TAG));
            taskProgress.load(snapshot.getCompoundOrEmpty(TASK_PROGRESS_TAG));
        }
        var result = com.craisinlord.antos.content.computer.ComputerDisks.install(serverPlayer, this, diskId,
                com.craisinlord.antos.content.item.FloppyDiskItem.redeemed(stack));
        if (result == com.craisinlord.antos.content.computer.ComputerDisks.InstallResult.INVALID) return false;
        if (result == com.craisinlord.antos.content.computer.ComputerDisks.InstallResult.INSTALLED) stack.shrink(1);
        activate();
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        return true;
    }

    public void activate() {
        if (level != null && !level.isClientSide()) {
            activeUntil = Math.max(activeUntil, level.getGameTime() + 40L);
            if (!getBlockState().getValue(ComputerBlock.ACTIVE) && isActive()) {
                level.setBlock(worldPosition, getBlockState().setValue(ComputerBlock.ACTIVE, true), 3);
            }
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public boolean isActive() {
        if (level == null) return false;
        if (level.isClientSide()) return getBlockState().getValue(ComputerBlock.ACTIVE);
        return neighborSignal(level, worldPosition) || isAuthenticated();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("state", state -> state.setAndContinue(isActive() ? ON : OFF)));
        controllers.add(new AnimationController<>("transitions", state -> PlayState.STOP)
                .triggerableAnim("turn_on", TURN_ON)
                .triggerableAnim("turn_off", TURN_OFF));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animatableCache;
    }

    public List<Identifier> diskIds() {
        List<Identifier> installed = List.of();
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel && workspaceOwner != null) {
            ComputerWorkspaceData data = ComputerWorkspaceData.access(serverLevel.getServer());
            if (data.account(workspaceOwner) != null) installed = data.profileDisks(workspaceOwner).stream().filter(data.activatedDisks(workspaceOwner)::contains).toList();
        }
        java.util.ArrayList<Identifier> ids = new java.util.ArrayList<>(installed.size() + 1);
        ids.add(INTRODUCTION_DISK);
        ids.addAll(installed);
        return List.copyOf(ids);
    }

    public boolean ejectOne(ServerPlayer player, Identifier diskId) {
        if (level == null || level.isClientSide() || diskId == null || INTRODUCTION_DISK.equals(diskId)) {
            return false;
        }
        ComputerWorkspaceData.AccountInfo account = com.craisinlord.antos.content.network.AnternetAccountHandler.session(player);
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel) || workspaceOwner == null
                || account == null || !workspaceOwner.equals(account.accountId()) || !playerCanReach(player)) return false;
        ComputerWorkspaceData accountData = ComputerWorkspaceData.access(serverLevel.getServer());
        if (!accountData.removeProfileDisk(workspaceOwner, diskId)) return false;
        ItemStack disk = diskStack(diskId);
        net.minecraft.world.entity.item.ItemEntity dropped = player.drop(disk, false, net.minecraft.util.Prediction.SERVER_ONLY);
        if (dropped == null) {
            accountData.addProfileDisk(workspaceOwner, diskId);
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
        if (level == null || level.isClientSide() || !playerCanReach(player)) {
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
        if (owner == null || level == null || level.isClientSide() || workspaceOwner != null && !workspaceOwner.equals(owner)) return false;
        workspaceOwner = owner;
        setChanged();
        return true;
    }

    public boolean bindWorkspaceId(java.util.UUID id) {
        if (id == null || level == null || level.isClientSide() || workspaceId != null && !workspaceId.equals(id)) return false;
        workspaceId = id;
        setChanged();
        return true;
    }

    public void captureDiskArchiveEntries() {
        for (Identifier diskId : diskIds()) {
            ComputerGuideData.Disk disk = ComputerGuideData.disk(diskId);
            if (disk != null) disk.entries().forEach(taskProgress::unlockArchiveEntry);
        }
    }

    public void saveWorkspaceProgress() {
        if (workspaceId != null && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            ComputerWorkspaceData data = ComputerWorkspaceData.access(serverLevel.getServer());
            if (workspaceOwner != null && data.account(workspaceOwner) != null) {
                data.saveWorkspaceSection(workspaceId, TASK_PROGRESS_TAG, taskProgress.save());
            } else {
                data.save(workspaceId, taskProgress);
            }
        }
    }

    public boolean authenticateAccount(ServerPlayer player, ComputerWorkspaceData.AccountInfo account) {
        if (account == null || level == null || level.isClientSide() || !playerCanReach(player)
                || workspaceOwner != null && !workspaceOwner.equals(account.accountId()) && !workspaceOwner.equals(player.getUUID())
                || activeUser != null && !activeUser.equals(player.getUUID()) && isAuthenticated()) return false;
        ComputerWorkspaceData data = ComputerWorkspaceData.access(player.level().getServer());
        CompoundTag snapshot = data.workspaceSnapshot(account.workspaceId());
        boolean hasLegacyState = workspaceId != null || workspaceOwner != null || taskProgress.hasRecoverableProgress()
                || fileSystem.list().size() > 3 || hasCustomDesktopState();
        if (snapshot == null || snapshot.isEmpty()) {
            if (hasLegacyState && workspaceOwner != null && !workspaceOwner.equals(player.getUUID())
                    && !workspaceOwner.equals(account.accountId())) return false;
        }
        if (snapshot == null) snapshot = new CompoundTag();
        if (snapshot.isEmpty() && hasLegacyState && (workspaceOwner == null || workspaceOwner.equals(player.getUUID()) || workspaceOwner.equals(account.accountId()))) {
            snapshot = saveWorkspaceSnapshot(player.level().registryAccess());
        } else {
            fileSystem.load(snapshot, player.level().registryAccess());
            desktopState.load(snapshot.getCompoundOrEmpty(DESKTOP_TAG));
            taskProgress.load(snapshot.getCompoundOrEmpty(TASK_PROGRESS_TAG));
        }
        workspaceOwner = account.accountId();
        workspaceId = account.workspaceId();
        activeUser = player.getUUID();
        authenticated = true;
        authenticatedUntil = 0L;
        captureDiskArchiveEntries();
        data.saveWorkspaceSnapshot(workspaceId, saveWorkspaceSnapshot(player.level().registryAccess()));
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

    public boolean unlockWallpaper(Identifier id) {
        return serverDesktopMutation(() -> ComputerGuideData.wallpaper(id) != null && desktopState.unlockWallpaper(id));
    }

    public boolean installProgram(Identifier id) {
        return serverDesktopMutation(() -> desktopState.installProgram(id));
    }

    public boolean uninstallProgram(Identifier id) {
        return serverDesktopMutation(() -> desktopState.uninstallProgram(id));
    }

    public boolean selectWallpaper(Identifier id) {
        return serverDesktopMutation(() -> ComputerGuideData.wallpaper(id) != null && desktopState.selectWallpaper(id));
    }

    public boolean setIconPosition(Identifier id, int x, int y) {
        return serverDesktopMutation(() -> desktopState.setIconPosition(id, x, y));
    }

    public boolean removeIconPosition(Identifier id) {
        return serverDesktopMutation(() -> desktopState.removeIconPosition(id));
    }

    private boolean serverDesktopMutation(java.util.function.BooleanSupplier mutation) {
        if (level == null || level.isClientSide()) {
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
        return level != null && !level.isClientSide() && isAuthenticated() && playerCanReach(player) && (activeUser == null || activeUser.equals(player.getUUID()));
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

    private static ItemStack diskStack(Identifier diskId) {
        return com.craisinlord.antos.content.item.FloppyDiskItem.ejectedCopy(diskId);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        activeUntil = input.getLongOr(ACTIVE_UNTIL_TAG, 0L);
        password = input.getStringOr(PASSWORD_TAG, "");
        workspaceOwner = input.read(WORKSPACE_OWNER_TAG, UUIDUtil.CODEC).orElse(null);
        workspaceId = input.read(WORKSPACE_ID_TAG, UUIDUtil.CODEC).orElse(null);
        fileSystem.load(input);
        desktopState.load(input.read(DESKTOP_TAG, CompoundTag.CODEC).orElseGet(CompoundTag::new));
        taskProgress.load(input.read(TASK_PROGRESS_TAG, CompoundTag.CODEC).orElseGet(CompoundTag::new));
        authenticated = level != null && level.isClientSide() && input.getBooleanOr(AUTHENTICATED_TAG, false);
        authenticatedUntil = 0L;
        activeUser = null;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong(ACTIVE_UNTIL_TAG, activeUntil);
        boolean accountWorkspace = level instanceof net.minecraft.server.level.ServerLevel serverLevel && workspaceOwner != null
                && ComputerWorkspaceData.access(serverLevel.getServer()).account(workspaceOwner) != null;
        if (!accountWorkspace) {
            output.putString(PASSWORD_TAG, password);
            fileSystem.save(output);
            output.store(DESKTOP_TAG, CompoundTag.CODEC, desktopState.save());
            output.store(TASK_PROGRESS_TAG, CompoundTag.CODEC, taskProgress.save());
        }
        output.storeNullable(WORKSPACE_OWNER_TAG, UUIDUtil.CODEC, workspaceOwner);
        output.storeNullable(WORKSPACE_ID_TAG, UUIDUtil.CODEC, workspaceId);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("HasPassword", hasPassword());
        tag.putBoolean(AUTHENTICATED_TAG, isAuthenticated());
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
}


