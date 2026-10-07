package com.craisinlord.antos.content.antmail;

import com.craisinlord.antos.content.AntOSObjects;
import com.craisinlord.antos.content.network.AnternetAccountHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Tells online recipients about new mail when they carry an Antroid phone or are signed in at a computer. */
public final class AntmailNotifier {
    private AntmailNotifier() { }

    public static void notify(MinecraftServer server, AntmailMessage message) {
        if (server == null || message == null || message.notification().equals("silent")) return;
        for (ServerPlayer player : AntmailServerData.access(server).onlinePlayersFor(server, message.recipient())) {
            if (AnternetAccountHandler.session(player) == null && !carriesPhone(player)) continue;
            Component sender = message.corrupted()
                    ? Component.literal("????????").withStyle(ChatFormatting.OBFUSCATED, ChatFormatting.DARK_RED)
                    : Component.literal(message.displaySender());
            player.displayClientMessage(Component.translatable("antmail.antos.notification", sender), true);
            SoundEvent sound = sound(message.notification());
            if (sound != null) player.playNotifySound(sound, SoundSource.PLAYERS, 0.6F, message.corrupted() ? 0.5F : 1.4F);
        }
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
