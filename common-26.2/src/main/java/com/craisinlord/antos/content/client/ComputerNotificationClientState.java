package com.craisinlord.antos.content.client;

import com.craisinlord.antos.config.AntOSSettings;
import com.craisinlord.antos.config.NotificationHistory;
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
    private static String workspace = "";
    private static NotificationHistory history = new NotificationHistory();
    private static final java.util.List<NotificationHistory.Entry> UNBOUND = new java.util.ArrayList<>();

    private ComputerNotificationClientState() {
    }

    public static NotificationHistory history() {
        String current = ComputerWorkspaceClientKey.of();
        if (!current.equals(workspace)) {
            workspace = current;
            history = NotificationHistory.decode(AntOSSettings.notificationHistory(current));
            if (!current.equals("workspace:unbound") && !UNBOUND.isEmpty()) {
                for (NotificationHistory.Entry entry : UNBOUND) {
                    history.add(entry.kind(), entry.title(), entry.message(), entry.receivedAt(), entry.source(), entry.iconItem(), entry.iconEntity());
                }
                UNBOUND.clear();
                saveHistory();
            }
        }
        return history;
    }

    public static long unreadCount() { return history().unreadCount(); }

    private static void saveHistory() {
        AntOSSettings.setNotificationHistory(workspace, history.encode());
    }

    public static void record(String kind, Component title, Component message) {
        record(kind, title, message, "", "", "");
    }

    public static void record(String kind, Component title, Component message, String source, String iconItem, String iconEntity) {
        if (ComputerWorkspaceClientKey.of().equals("workspace:unbound")) {
            UNBOUND.add(new NotificationHistory.Entry("", kind, title.getString(), message.getString(), System.currentTimeMillis(), false, source, iconItem, iconEntity));
            while (UNBOUND.size() > NotificationHistory.LIMIT) UNBOUND.remove(0);
            return;
        }
        history().add(kind, title.getString(), message.getString(), System.currentTimeMillis(), source, iconItem, iconEntity);
        saveHistory();
    }

    public static void markRead(String id) {
        history().markRead(id);
        saveHistory();
    }

    public static void markAllRead() {
        history().markAllRead();
        saveHistory();
    }

    public static void clearHistory() {
        history().clear();
        saveHistory();
    }

    public static void clearConnection() {
        UNBOUND.clear();
        workspace = "";
        history = new NotificationHistory();
    }

    public static void update(ComputerAccessResultPayload result) {
        if (!result.data().startsWith(ComputerAccessPayload.NOTIFICATION + "\0")) return;
        String[] fields = result.data().split("\u0000", -1);
        if (fields.length < 2) return;
        if (fields[1].equals("mail") && fields.length >= 5) mail(fields[2], Boolean.parseBoolean(fields[3]), fields[4], field(fields, 5), field(fields, 6));
        else if (fields[1].equals("task") && fields.length >= 5) task(fields[2], fields[3], fields[4], field(fields, 5), field(fields, 6));
    }

    private static String field(String[] fields, int index) {
        return index < fields.length ? fields[index] : "";
    }

    public static void mail(String sender, boolean corrupted, String sound, String avatarItem, String avatarEntity) {
        var preferences = AntOSSettings.preferences();
        Component name = corrupted
                ? Component.literal("????????").withStyle(ChatFormatting.OBFUSCATED, ChatFormatting.DARK_RED)
                : Component.literal(sender);
        Component title = Component.translatable("computer.antos.notify.mail_title");
        Component message = Component.translatable("antmail.antos.notification", name);
        record("mail", title, message, corrupted ? "" : sender, corrupted ? "" : avatarItem, corrupted ? "" : avatarEntity);
        show(preferences.mailAlerts, title, message);
        if (!preferences.quietNotifications && preferences.mailSound && !preferences.mailAlerts.equals("off")) play(sound, corrupted ? 0.5F : 1.4F);
    }

    public static void task(String titleKey, String descriptionKey, String sound, String iconItem, String iconEntity) {
        var preferences = AntOSSettings.preferences();
        Component title = Component.translatable("computer.antos.notify.task_title");
        Component message = Component.translatable("antos.tasks.objective_complete", plain(titleKey), plain(descriptionKey));
        record("task", title, message, "", iconItem, iconEntity);
        show(preferences.taskAlerts, title, message);
        if (!preferences.quietNotifications && preferences.taskSound && !preferences.taskAlerts.equals("off")) play(sound, 1.4F);
    }

    public static void test(String kind) {
        String chime = SoundEvents.NOTE_BLOCK_CHIME.value().location().toString();
        if (kind.equals("task")) {
            var preferences = AntOSSettings.preferences();
            show(preferences.taskAlerts, Component.translatable("computer.antos.notify.task_title"), Component.translatable("computer.antos.notify.test_task"));
            if (!preferences.quietNotifications && preferences.taskSound && !preferences.taskAlerts.equals("off")) play(chime, 1.4F);
        } else {
            var preferences = AntOSSettings.preferences();
            show(preferences.mailAlerts, Component.translatable("computer.antos.notify.mail_title"), Component.translatable("computer.antos.notify.test_mail"));
            if (!preferences.quietNotifications && preferences.mailSound && !preferences.mailAlerts.equals("off")) play(chime, 1.4F);
        }
    }

    private static Component plain(String key) {
        return Component.literal(com.craisinlord.antos.content.guide.TaskMarkup.strip(Component.translatable(key).getString()));
    }

    private static void show(String style, Component title, Component message) {
        if (AntOSSettings.preferences().quietNotifications) return;
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
