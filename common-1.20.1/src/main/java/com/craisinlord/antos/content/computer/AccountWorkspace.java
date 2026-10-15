package com.craisinlord.antos.content.computer;

import com.craisinlord.antos.content.AntOSObjects;
import com.craisinlord.antos.content.guide.ComputerGuideData;
import com.craisinlord.antos.content.network.AnternetAccountHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AccountWorkspace implements ComputerWorkspace {
    private static final ResourceLocation INTRODUCTION_DISK = new ResourceLocation("antos", "introduction");
    private final ServerPlayer owner;
    private final ComputerWorkspaceData.AccountInfo account;
    private final ComputerFileSystem fileSystem = new ComputerFileSystem();
    private final ComputerDesktopState desktopState = new ComputerDesktopState();
    private final ComputerTaskProgress taskProgress = new ComputerTaskProgress();
    private ComputerWorkspaceData data;
    private boolean authenticated = true;
    private boolean workspaceLoaded;
    private boolean progressLoaded;
    private int savedProgressRevision;
    private int savedFileSystemRevision;
    private CompoundTag savedDesktop;

    public AccountWorkspace(ServerPlayer owner, ComputerWorkspaceData.AccountInfo account) {
        this.owner = owner;
        this.account = account;
    }

    private ComputerWorkspaceData data() {
        if (data == null) data = ComputerWorkspaceData.access(owner.server);
        return data;
    }

    private void loadProgress() {
        if (progressLoaded) return;
        progressLoaded = true;
        data().readWorkspaceSnapshot(account.workspaceId(), snapshot -> taskProgress.load(snapshot.getCompound("TaskProgress")));
        savedProgressRevision = taskProgress.revision();
    }

    private void loadWorkspace() {
        if (workspaceLoaded) return;
        workspaceLoaded = true;
        data().readWorkspaceSnapshot(account.workspaceId(), snapshot -> {
            fileSystem.load(snapshot, owner.serverLevel().registryAccess());
            desktopState.load(snapshot.getCompound("Desktop"));
        });
        savedFileSystemRevision = fileSystem.revision();
        savedDesktop = desktopState.save();
    }

    @Override public ComputerFileSystem fileSystem() { loadWorkspace(); return fileSystem; }
    @Override public ComputerDesktopState desktopState() { loadWorkspace(); return desktopState; }
    @Override public ComputerTaskProgress taskProgress() { loadProgress(); return taskProgress; }
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
        ComputerWorkspaceData data = data();
        ids.addAll(data.profileDisks(account.accountId()).stream().filter(data.activatedDisks(account.accountId())::contains).toList());
        return List.copyOf(ids);
    }

    @Override public boolean ejectOne(ServerPlayer player, ResourceLocation diskId) {
        if (!canUseFileSystem(player) || diskId == null || INTRODUCTION_DISK.equals(diskId)) return false;
        ComputerWorkspaceData data = data();
        if (!data.removeProfileDisk(account.accountId(), diskId)) return false;
        ItemStack disk = com.craisinlord.antos.content.item.FloppyDiskItem.ejectedCopy(diskId);
        if (player.drop(disk, false) != null) return true;
        data.addProfileDisk(account.accountId(), diskId);
        return false;
    }

    @Override public boolean selectWallpaper(ResourceLocation id) {
        if (ComputerGuideData.wallpaper(id) == null || !desktopState().selectWallpaper(id)) return false;
        saveAccountWorkspace();
        return true;
    }

    @Override public void saveAccountWorkspace() {
        if (!workspaceLoaded) {
            saveWorkspaceProgress();
            return;
        }
        Map<String, Tag> sections = new LinkedHashMap<>();
        boolean filesChanged = fileSystem.revision() != savedFileSystemRevision;
        if (filesChanged) {
            CompoundTag files = new CompoundTag();
            fileSystem.save(files, owner.serverLevel().registryAccess());
            for (String key : files.getAllKeys()) sections.put(key, files.get(key));
        }
        CompoundTag desktop = desktopState.save();
        boolean desktopChanged = !desktop.equals(savedDesktop);
        if (desktopChanged) sections.put("Desktop", desktop);
        boolean progressChanged = progressLoaded && taskProgress.revision() != savedProgressRevision;
        if (progressChanged) sections.put("TaskProgress", taskProgress.save());
        if (sections.isEmpty()) return;
        data().saveOwnedWorkspaceSections(account.workspaceId(), sections);
        savedFileSystemRevision = fileSystem.revision();
        if (desktopChanged) savedDesktop = desktop;
        if (progressChanged) savedProgressRevision = taskProgress.revision();
    }

    @Override public void saveWorkspaceProgress() {
        if (!progressLoaded || taskProgress.revision() == savedProgressRevision) return;
        data().saveOwnedWorkspaceSections(account.workspaceId(), Map.of("TaskProgress", taskProgress.save()));
        savedProgressRevision = taskProgress.revision();
    }
}
