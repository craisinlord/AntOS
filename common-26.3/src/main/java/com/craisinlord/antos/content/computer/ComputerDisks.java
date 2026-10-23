package com.craisinlord.antos.content.computer;

import com.craisinlord.antos.content.antazon.AntazonUnlockMail;
import com.craisinlord.antos.content.guide.ComputerGuideData;
import com.craisinlord.antos.content.network.AnternetAccountHandler;
import com.craisinlord.antos.content.network.ComputerAccessHandler;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ComputerDisks {
    private static final Map<MinecraftServer, Map<UUID, Attempt>> ATTEMPTS = new WeakHashMap<>();

    private ComputerDisks() { }

    public static InstallResult install(ServerPlayer player, ComputerWorkspace workspace, Identifier diskId) {
        return install(player, workspace, diskId, false);
    }

    public static InstallResult install(ServerPlayer player, ComputerWorkspace workspace, Identifier diskId, boolean redeemedCopy) {
        if (player == null) return InstallResult.INVALID;
        var account = AnternetAccountHandler.session(player);
        ComputerGuideData.Disk disk = diskId == null ? null : ComputerDiskData.disk(diskId);
        if (account == null || disk == null || workspace == null || !workspace.canUseFileSystem(player)
                || !account.accountId().equals(workspace.workspaceOwner()) || !account.workspaceId().equals(workspace.workspaceId())) return InstallResult.INVALID;
        ComputerWorkspaceData data = ComputerWorkspaceData.access(player.level().getServer());
        boolean spent = redeemedCopy && disk.features().consumable();
        if (spent && data.profileDisks(account.accountId()).contains(diskId)) {
            player.sendOverlayMessage(Component.translatable("computer.antos.disks.already_redeemed", Component.translatable(disk.titleKey())));
            return InstallResult.ALREADY_INSTALLED;
        }
        if (data.profileDisks(account.accountId()).contains(diskId) && disk.features().consumable()
                && (!disk.features().encryption().enabled() || data.diskState(account.accountId()).activated(diskId))) {
            redeemAgain(player, account, disk);
            ComputerAccessHandler.refreshAfterDisk(player, workspace);
            player.sendOverlayMessage(Component.translatable("computer.antos.disks.install_success", Component.translatable(disk.titleKey())));
            return InstallResult.INSTALLED;
        }
        if (data.profileDisks(account.accountId()).contains(diskId)) {
            player.sendOverlayMessage(Component.translatable("computer.antos.disks.already_installed", Component.translatable(disk.titleKey())));
            return InstallResult.ALREADY_INSTALLED;
        }
        if (!data.addProfileDisk(account.accountId(), diskId)) return InstallResult.INVALID;
        if (spent) data.claimDiskRewards(account.accountId(), diskId);
        if (!disk.features().encryption().enabled() || data.diskState(account.accountId()).activated(diskId)) activate(player, workspace, account, disk);
        ComputerAccessHandler.refreshAfterDisk(player, workspace);
        player.sendOverlayMessage(Component.translatable(data.diskState(account.accountId()).activated(diskId)
                ? "computer.antos.disks.install_success" : "computer.antos.disks.installed_locked", Component.translatable(disk.titleKey())));
        return InstallResult.INSTALLED;
    }

    private static void activate(ServerPlayer player, ComputerWorkspace workspace, ComputerWorkspaceData.AccountInfo account, ComputerGuideData.Disk disk) {
        ComputerWorkspaceData data = ComputerWorkspaceData.access(player.level().getServer());
        DiskProfileState state = data.diskState(account.accountId());
        boolean newlyActivated = state.activate(disk.id(), disk.category());
        disk.tasks().forEach(workspace.taskProgress()::grant);
        disk.entries().forEach(workspace.taskProgress()::unlockArchiveEntry);
        Identifier wallpaper = Identifier.tryParse(disk.wallpaper());
        if (wallpaper != null && ComputerGuideData.wallpaper(wallpaper) != null) workspace.desktopState().unlockWallpaper(wallpaper);
        if (!disk.features().files().isEmpty()) ComputerDiskFiles.copy(workspace.fileSystem(), state, disk, ComputerDiskData.files(), player.getName().getString(), player.level().getServer().overworld().getGameTime());
        disk.features().subscriptions().forEach(state::subscribe);
        workspace.saveAccountWorkspace();
        data.setDirty();
        grantRewards(player, account, disk);
        if (newlyActivated) AntazonUnlockMail.onDiskInstalled(player, workspace, disk.id());
    }

    public static void reconcileRewards(ServerPlayer player, ComputerWorkspaceData.AccountInfo account) {
        if (player == null || account == null || !account.equals(AnternetAccountHandler.session(player))) return;
        ComputerWorkspaceData data = ComputerWorkspaceData.access(player.level().getServer());
        AccountWorkspace workspace = new AccountWorkspace(player, account);
        for (Identifier id : data.diskState(account.accountId()).activatedDisks()) {
            ComputerGuideData.Disk disk = ComputerDiskData.disk(id);
            if (disk != null) data.diskState(account.accountId()).activate(id, disk.category());
        }
        data.setDirty();
        for (Identifier id : data.profileDisks(account.accountId())) {
            ComputerGuideData.Disk disk = ComputerDiskData.disk(id);
            if (disk != null && (!disk.features().encryption().enabled() || data.diskState(account.accountId()).activated(id))) activate(player, workspace, account, disk);
        }
    }

    public static void preserveLegacyAccess(ComputerWorkspaceData data, UUID profile, Identifier diskId) {
        data.addProfileDisk(profile, diskId);
        ComputerGuideData.Disk disk = ComputerDiskData.disk(diskId);
        data.diskState(profile).activate(diskId, disk == null ? Identifier.fromNamespaceAndPath("antos", "general") : disk.category());
        data.setDirty();
    }

    public static String decrypt(ServerPlayer player, ComputerWorkspace workspace, Identifier diskId, String password) {
        var account = AnternetAccountHandler.session(player);
        ComputerGuideData.Disk disk = diskId == null ? null : ComputerDiskData.disk(diskId);
        if (account == null || disk == null || workspace == null || !workspace.canUseFileSystem(player)
                || !account.accountId().equals(workspace.workspaceOwner()) || !account.workspaceId().equals(workspace.workspaceId())) return "invalid_disk";
        ComputerWorkspaceData data = ComputerWorkspaceData.access(player.level().getServer());
        if (!data.profileDisks(account.accountId()).contains(diskId)) return "not_installed";
        if (data.diskState(account.accountId()).activated(diskId)) return "already_unlocked";
        if (!disk.features().encryption().enabled()) return "invalid_disk";
        long now = System.nanoTime() / 50_000_000L;
        Map<UUID, Attempt> attempts;
        synchronized (ATTEMPTS) { attempts = ATTEMPTS.computeIfAbsent(player.level().getServer(), ignored -> new HashMap<>()); }
        Attempt previous = attempts.get(account.accountId());
        if (previous != null && now < previous.nextTick()) return "rate_limited";
        if (!disk.features().encryption().accepts(password)) {
            int failures = previous == null || now - previous.nextTick() > 12000 ? 1 : Math.min(5, previous.failures() + 1);
            attempts.put(account.accountId(), new Attempt(failures, now + Math.min(200, 10L << failures)));
            return "access_denied";
        }
        attempts.remove(account.accountId());
        activate(player, workspace, account, disk);
        ComputerAccessHandler.refreshAfterDisk(player, workspace);
        return "decrypted";
    }

    private static void grantRewards(ServerPlayer player, ComputerWorkspaceData.AccountInfo account, ComputerGuideData.Disk disk) {
        if (disk.rewards().isEmpty()) return;
        ComputerWorkspaceData data = ComputerWorkspaceData.access(player.level().getServer());
        if (!data.claimDiskRewards(account.accountId(), disk.id())) return;
        List<ComputerGuideData.TaskReward> receipt = ComputerTasks.deliverRewards(player, disk.id(), disk.rewards(), Map.of());
        var encoded = ComputerGuideData.encodeRewards(receipt);
        for (var element : encoded) if (element.getAsJsonObject().has("body")) element.getAsJsonObject().addProperty("body", "");
        data.recordDiskRewardReceipt(account.accountId(), disk.id(), encoded.toString());
        if (!receipt.isEmpty()) ComputerAccessHandler.sendDiskRewards(player, account, disk, encoded);
    }

    private static void redeemAgain(ServerPlayer player, ComputerWorkspaceData.AccountInfo account, ComputerGuideData.Disk disk) {
        if (disk.rewards().isEmpty()) return;
        ComputerWorkspaceData data = ComputerWorkspaceData.access(player.level().getServer());
        List<ComputerGuideData.TaskReward> receipt = ComputerTasks.deliverRewards(player, disk.id(), disk.rewards(), Map.of());
        var encoded = ComputerGuideData.encodeRewards(receipt);
        for (var element : encoded) if (element.getAsJsonObject().has("body")) element.getAsJsonObject().addProperty("body", "");
        data.recordDiskRewardReceipt(account.accountId(), disk.id(), encoded.toString());
        data.setDirty();
        if (!receipt.isEmpty()) ComputerAccessHandler.sendDiskRewards(player, account, disk, encoded);
    }

    private record Attempt(int failures, long nextTick) { }
    public enum InstallResult { INVALID, ALREADY_INSTALLED, INSTALLED }
}
