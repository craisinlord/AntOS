package com.craisinlord.antos.content.client.game;

import com.craisinlord.antos.config.AntOSSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

public final class GameSounds {
    private GameSounds() { }

    public static void play(SoundEvent sound, float pitch, float volume) {
        var preferences = AntOSSettings.preferences();
        if (!preferences.interfaceSounds || preferences.interfaceVolume <= 0) return;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume * preferences.interfaceVolume / 100.0F));
    }
}
