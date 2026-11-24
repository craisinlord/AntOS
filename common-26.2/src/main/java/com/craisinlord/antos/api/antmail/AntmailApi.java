package com.craisinlord.antos.api.antmail;

import com.craisinlord.antos.content.antmail.AntmailDeliveryResult;
import com.craisinlord.antos.content.antmail.AntmailEventData;
import com.craisinlord.antos.content.computer.ComputerDesktopEffects;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class AntmailApi {
    private AntmailApi() { }

    public static boolean send(ServerPlayer player, Identifier messageId) {
        AntmailDeliveryResult.Status status = AntmailEventData.sendDefinition(player, messageId);
        return status == AntmailDeliveryResult.Status.DELIVERED || status == AntmailDeliveryResult.Status.QUEUED;
    }

    public static int trigger(ServerPlayer player, String type, String value) {
        return AntmailEventData.triggerEvent(player, type, value);
    }

    public static boolean isLoaded(Identifier messageId) {
        return messageId != null && AntmailEventData.definition(messageId.toString()) != null;
    }

    public static boolean queueTakeover(ServerPlayer player, Identifier takeoverId) {
        if (player == null || AntmailEventData.takeover(takeoverId) == null) return false;
        UUID profile = ComputerDesktopEffects.accountFor(player);
        return profile != null && ComputerDesktopEffects.applyToAccount(player.level().getServer(), profile, null, takeoverId);
    }

    public static boolean forceWallpaper(ServerPlayer player, Identifier wallpaperId, boolean unlock) {
        UUID profile = player == null || wallpaperId == null ? null : ComputerDesktopEffects.accountFor(player);
        return profile != null && ComputerDesktopEffects.applyToAccount(player.level().getServer(), profile,
                new ComputerDesktopEffects.WallpaperChange(wallpaperId, true, false, unlock), null);
    }

    public static boolean restoreWallpaper(ServerPlayer player) {
        UUID profile = player == null ? null : ComputerDesktopEffects.accountFor(player);
        return profile != null && ComputerDesktopEffects.applyToAccount(player.level().getServer(), profile,
                new ComputerDesktopEffects.WallpaperChange(null, false, true, false), null);
    }
}
