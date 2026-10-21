package com.craisinlord.antos.content.client.game;

import com.craisinlord.antos.config.AntOSSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

public final class CakeStackSounds {
    private CakeStackSounds() { }

    public static void place(int score, boolean perfect) {
        var preferences = AntOSSettings.preferences();
        if (!preferences.interfaceSounds || preferences.interfaceVolume <= 0) return;
        float pitch = perfect ? 1.45F : 0.78F + Math.floorMod(score, 8) * 0.045F;
        float volume = (perfect ? 0.42F : 0.28F) * preferences.interfaceVolume / 100.0F;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), pitch, volume));
    }

    public static void miss() {
        var preferences = AntOSSettings.preferences();
        if (!preferences.interfaceSounds || preferences.interfaceVolume <= 0) return;
        float volume = 0.34F * preferences.interfaceVolume / 100.0F;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), 0.68F, volume));
    }
}
