package com.craisinlord.antos.content.client;

import com.craisinlord.antos.config.AntOSSettings;
import com.craisinlord.antos.content.network.ComputerAccessPayload;
import com.craisinlord.antos.content.network.ComputerAccessResultPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public final class ComputerNotificationClientState {
    private ComputerNotificationClientState() {
    }

    public static void update(ComputerAccessResultPayload result) {
        if (!result.data().startsWith(ComputerAccessPayload.NOTIFICATION + "\0")) return;
        String[] fields = result.data().split("\u0000", -1);
        if (fields.length < 2) return;
        if (fields[1].equals("mail") && fields.length >= 5) mail(fields[2], Boolean.parseBoolean(fields[3]), fields[4]);
        else if (fields[1].equals("task") && fields.length >= 5) task(fields[2], fields[3], fields[4]);
    }

    public static void mail(String sender, boolean corrupted, String sound) {
        var preferences = AntOSSettings.preferences();
        Component name = corrupted
                ? Component.literal("????????").withStyle(ChatFormatting.OBFUSCATED, ChatFormatting.DARK_RED)
                : Component.literal(sender);
        show(preferences.mailAlerts, Component.translatable("computer.antos.notify.mail_title"),
                Component.translatable("antmail.antos.notification", name));
        if (preferences.mailSound && !preferences.mailAlerts.equals("off")) play(sound, corrupted ? 0.5F : 1.4F);
    }

    public static void task(String titleKey, String descriptionKey, String sound) {
        var preferences = AntOSSettings.preferences();
        show(preferences.taskAlerts, Component.translatable("computer.antos.notify.task_title"),
                Component.translatable("antos.tasks.objective_complete", plain(titleKey), plain(descriptionKey)));
        if (preferences.taskSound && !preferences.taskAlerts.equals("off")) play(sound, 1.4F);
    }

    public static void test(String kind) {
        String chime = SoundEvents.NOTE_BLOCK_CHIME.value().location().toString();
        if (kind.equals("task")) {
            var preferences = AntOSSettings.preferences();
            show(preferences.taskAlerts, Component.translatable("computer.antos.notify.task_title"), Component.translatable("computer.antos.notify.test_task"));
            if (preferences.taskSound && !preferences.taskAlerts.equals("off")) play(chime, 1.4F);
        } else {
            var preferences = AntOSSettings.preferences();
            show(preferences.mailAlerts, Component.translatable("computer.antos.notify.mail_title"), Component.translatable("computer.antos.notify.test_mail"));
            if (preferences.mailSound && !preferences.mailAlerts.equals("off")) play(chime, 1.4F);
        }
    }

    private static Component plain(String key) {
        return Component.literal(com.craisinlord.antos.content.guide.TaskMarkup.strip(Component.translatable(key).getString()));
    }

    private static void show(String style, Component title, Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (style.equals("toast") || style.equals("both")) minecraft.gui.toastManager().addToast(new AntOSToast(title, message));
        if ((style.equals("actionbar") || style.equals("both")) && minecraft.gui != null) minecraft.gui.hud.setOverlayMessage(message, false);
    }

    private static void play(String sound, float pitch) {
        Identifier id = sound == null || sound.isBlank() ? null : Identifier.tryParse(sound);
        if (id == null) return;
        SoundEvent event = BuiltInRegistries.SOUND_EVENT.getOptional(id).orElseGet(() -> SoundEvent.createVariableRangeEvent(id));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, 0.6F));
    }
}
