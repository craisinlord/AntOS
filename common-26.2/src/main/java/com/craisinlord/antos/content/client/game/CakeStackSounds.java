package com.craisinlord.antos.content.client.game;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

public final class CakeStackSounds {
    private CakeStackSounds() { }

    public static void place(int score, boolean perfect) {
        float pitch = perfect ? 1.45F : 0.78F + Math.floorMod(score, 8) * 0.045F;
        float volume = perfect ? 0.42F : 0.28F;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), pitch, volume));
    }

    public static void miss() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), 0.68F, 0.34F));
    }
}
