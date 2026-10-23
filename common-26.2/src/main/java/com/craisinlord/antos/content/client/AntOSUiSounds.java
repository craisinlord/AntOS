package com.craisinlord.antos.content.client;

import com.craisinlord.antos.config.AntOSSettings;
import com.craisinlord.antos.content.client.screen.ComputerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class AntOSUiSounds {
    public enum Cue {
        CLICK(0, 0.28F), OPEN(1, 0.22F), CLOSE(1, 0.22F),
        MINIMIZE(1, 0.25F), MAXIMIZE(1, 0.25F), COMMAND(1, 0.35F),
        LOGIN(2, 0.5F), SAVE(2, 0.45F), DISK(2, 0.4F), ORDER(2, 0.45F), SALE(2, 0.45F), ERROR(3, 0.4F),
        BREAKOUT_PADDLE(0, 0.3F), BREAKOUT_HIT(0, 0.28F), BREAKOUT_BRICK(1, 0.35F), BREAKOUT_POWERUP(2, 0.42F), BREAKOUT_MISS(3, 0.35F);

        final int priority;
        final float volume;
        final SoundEvent sound;

        Cue(int priority, float volume) {
            this.priority = priority;
            this.volume = volume;
            sound = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("antos", "ui." + name().toLowerCase(java.util.Locale.ROOT)));
        }
    }

    private static int depth;
    private static Cue pending;
    private static long lastPlayed;
    private static Cue lastCue;
    private static int previewTicks;
    private static int previewIndex;
    private static final Cue[] PREVIEW = {Cue.CLICK, Cue.OPEN, Cue.CLOSE, Cue.MINIMIZE, Cue.MAXIMIZE, Cue.LOGIN, Cue.SAVE, Cue.COMMAND, Cue.DISK, Cue.ORDER, Cue.SALE, Cue.ERROR};

    private AntOSUiSounds() { }

    public static void action(Runnable action) {
        depth++;
        try {
            play(Cue.CLICK);
            action.run();
        } finally {
            if (--depth == 0 && pending != null) {
                Cue cue = pending;
                pending = null;
                play(cue);
            }
        }
    }

    public static void play(Cue cue) {
        if (depth > 0) {
            if (pending == null || cue.priority >= pending.priority) pending = cue;
            return;
        }
        var preferences = AntOSSettings.preferences();
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof ComputerScreen) || !preferences.interfaceSounds || preferences.interfaceVolume <= 0) return;
        long now = System.nanoTime();
        if (now - lastPlayed < 60_000_000L && lastCue != null && cue.priority <= lastCue.priority) return;
        if (cue == lastCue && now - lastPlayed < 120_000_000L) return;
        lastPlayed = now;
        lastCue = cue;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(cue.sound, 1.0F, cue.volume * preferences.interfaceVolume / 100.0F));
    }

    public static void preview() {
        previewIndex = 0;
        previewTicks = 1;
    }

    public static void tick() {
        if (previewTicks <= 0 || --previewTicks > 0) return;
        play(PREVIEW[previewIndex++]);
        if (previewIndex < PREVIEW.length) previewTicks = 12;
    }

    public static void stopPreview() {
        previewTicks = 0;
    }
}
