package com.craisinlord.antos.content.computer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

public interface ComputerWorkspace {
    ComputerFileSystem fileSystem();
    ComputerDesktopState desktopState();
    ComputerTaskProgress taskProgress();
    UUID workspaceOwner();
    UUID workspaceId();
    boolean isRemoteWorkspace();
    List<ResourceLocation> diskIds();
    boolean ejectOne(ServerPlayer player, ResourceLocation diskId);
    boolean hasPassword();
    boolean isAuthenticated();
    boolean isAuthenticatedBy(ServerPlayer player);
    boolean authenticateAccount(ServerPlayer player, ComputerWorkspaceData.AccountInfo account);
    boolean canUseFileSystem(ServerPlayer player);
    boolean selectWallpaper(ResourceLocation id);
    void logout();
    void releaseUser(ServerPlayer player);
    void saveAccountWorkspace();
    void saveWorkspaceProgress();
    void setChanged();
}
