package com.craisinlord.antos.content.antmail;

import com.craisinlord.antos.content.AntOSObjects;
import com.craisinlord.antos.content.network.AnternetAccountHandler;
import com.craisinlord.antos.content.network.ComputerAccessHandler;
import com.craisinlord.antos.content.guide.ComputerGuideData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public final class AntmailNotifier {
    private AntmailNotifier() { }

    public static void notify(MinecraftServer server, AntmailMessage message) {
        if (server == null || message == null || message.notification().equals("silent")) return;
        for (ServerPlayer player : AntmailServerData.access(server).onlinePlayersFor(server, message.recipient())) {
            if (AnternetAccountHandler.session(player) == null && !carriesPhone(player)) continue;
            SoundEvent sound = sound(message.notification());
            ComputerAccessHandler.sendNotification(player, "mail", message.displaySender(), String.valueOf(message.corrupted()),
                    sound == null ? "" : sound.getLocation().toString(), message.senderAvatarItem(), message.senderAvatarEntity());
        }
    }

    public static void notifyObjectiveCompleted(ServerPlayer player, ComputerGuideData.Task task,
                                                ComputerGuideData.Objective objective) {
        if (player == null || task == null || objective == null || !objective.notifyPlayer()) return;
        ComputerAccessHandler.sendNotification(player, "task", task.titleKey(), objective.descriptionKey(),
                SoundEvents.NOTE_BLOCK_CHIME.value().getLocation().toString(), task.iconItem(), task.iconEntity());
    }

    private static boolean carriesPhone(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(AntOSObjects.ANTROID_PHONE.get())) return true;
        }
        return false;
    }

    private static SoundEvent sound(String notification) {
        if (notification.isBlank() || notification.equals("normal")) return SoundEvents.NOTE_BLOCK_CHIME.value();
        ResourceLocation id = ResourceLocation.tryParse(notification);
        if (id == null) return SoundEvents.NOTE_BLOCK_CHIME.value();
        return BuiltInRegistries.SOUND_EVENT.getOptional(id).orElseGet(() -> SoundEvent.createVariableRangeEvent(id));
    }
}
