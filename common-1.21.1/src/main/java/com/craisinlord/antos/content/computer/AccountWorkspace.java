package com.craisinlord.antos.content.computer;

import com.craisinlord.antos.content.AntOSObjects;
import com.craisinlord.antos.content.guide.ComputerGuideData;
import com.craisinlord.antos.content.network.AnternetAccountHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AccountWorkspace implements ComputerWorkspace {
    private static final ResourceLocation INTRODUCTION_DISK = ResourceLocation.fromNamespaceAndPath("antos", "introduction");
    private final ServerPlayer owner;
    private final ComputerWorkspaceData.AccountInfo account;
    private final ComputerFileSystem fileSystem = new ComputerFileSystem();
    private final ComputerDesktopState desktopState = new ComputerDesktopState();
    private final ComputerTaskProgress taskProgress = new ComputerTaskProgress();
    private boolean authenticated = true;

    public AccountWorkspace(ServerPlayer owner, ComputerWorkspaceData.AccountInfo account) {
        this.owner = owner;
        this.account = account;
        CompoundTag snapshot = ComputerWorkspaceData.access(owner.server).workspaceSnapshot(account.workspaceId());
        if (snapshot != null) {
            fileSystem.load(snapshot, owner.serverLevel().registryAccess());
            desktopState.load(snapshot.getCompound("Desktop"));
            taskProgress.load(snapshot.getCompound("TaskProgress"));
        }
    }

    @Override public ComputerFileSystem fileSystem() { return fileSystem; }
    @Override public ComputerDesktopState desktopState() { return desktopState; }
    @Override public ComputerTaskProgress taskProgress() { return taskProgress; }
    @Override public UUID workspaceOwner() { return account.accountId(); }
    @Override public UUID workspaceId() { return account.workspaceId(); }
    @Override public boolean isRemoteWorkspace() { return true; }
    @Override public boolean hasPassword() { return false; }
    @Override public boolean isAuthenticated() { return authenticated; }
    @Override public boolean isAuthenticatedBy(ServerPlayer player) { return authenticated && owner.getUUID().equals(player.getUUID()); }
    @Override public boolean canUseFileSystem(ServerPlayer player) {
        var session = AnternetAccountHandler.session(player);
        return isAuthenticatedBy(player) && session != null && account.accountId().equals(session.accountId());
    }
    @Override public boolean authenticateAccount(ServerPlayer player, ComputerWorkspaceData.AccountInfo requested) {
        if (requested == null || !account.accountId().equals(requested.accountId()) || !owner.getUUID().equals(player.getUUID())) return false;
        authenticated = true;
        return canUseFileSystem(player);
    }
    @Override public void logout() { saveAccountWorkspace(); authenticated = false; }
    @Override public void releaseUser(ServerPlayer player) { if (owner.getUUID().equals(player.getUUID())) authenticated = false; }
    @Override public void setChanged() { }

    @Override public List<ResourceLocation> diskIds() {
        List<ResourceLocation> ids = new ArrayList<>();
        ids.add(INTRODUCTION_DISK);
        ids.addAll(ComputerWorkspaceData.access(owner.server).profileDisks(account.accountId()));
        return List.copyOf(ids);
    }

    @Override public boolean ejectOne(ServerPlayer player, ResourceLocation diskId) {
        if (!canUseFileSystem(player) || diskId == null || INTRODUCTION_DISK.equals(diskId)) return false;
        ComputerWorkspaceData data = ComputerWorkspaceData.access(owner.server);
        if (!data.removeProfileDisk(account.accountId(), diskId)) return false;
        ItemStack disk = new ItemStack(AntOSObjects.FLOPPY_DISK.get());
        disk.set(AntOSObjects.FLOPPY_DISK_COMPONENT.get(), diskId);
        if (player.drop(disk, false) != null) return true;
        data.addProfileDisk(account.accountId(), diskId);
        return false;
    }

    @Override public boolean selectWallpaper(ResourceLocation id) {
        if (ComputerGuideData.wallpaper(id) == null || !desktopState.selectWallpaper(id)) return false;
        saveAccountWorkspace();
        return true;
    }

    @Override public void saveAccountWorkspace() {
        CompoundTag snapshot = new CompoundTag();
        fileSystem.save(snapshot, owner.serverLevel().registryAccess());
        snapshot.put("Desktop", desktopState.save());
        snapshot.put("TaskProgress", taskProgress.save());
        ComputerWorkspaceData.access(owner.server).saveWorkspaceSnapshot(account.workspaceId(), snapshot);
    }

    @Override public void saveWorkspaceProgress() {
        ComputerWorkspaceData data = ComputerWorkspaceData.access(owner.server);
        CompoundTag snapshot = data.workspaceSnapshot(account.workspaceId());
        if (snapshot == null) snapshot = new CompoundTag();
        snapshot.put("TaskProgress", taskProgress.save());
        data.saveWorkspaceSnapshot(account.workspaceId(), snapshot);
    }
}
