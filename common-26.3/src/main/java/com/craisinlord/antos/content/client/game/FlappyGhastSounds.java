package com.craisinlord.antos.content.client.game;

import net.minecraft.sounds.SoundEvents;

public final class FlappyGhastSounds {
    private FlappyGhastSounds() { }

    public static void flap() {
        GameSounds.play(SoundEvents.PHANTOM_FLAP, 1.35F, 0.22F);
    }

    public static void score(int score) {
        GameSounds.play(SoundEvents.NOTE_BLOCK_PLING.value(), Math.min(1.9F, 0.85F + Math.floorMod(score, 15) * 0.07F), 0.3F);
    }

    public static void quartz() {
        GameSounds.play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.3F, 0.55F);
    }

    public static void gustWarning() {
        GameSounds.play(SoundEvents.SOUL_ESCAPE.value(), 0.8F, 0.5F);
    }

    public static void gust() {
        GameSounds.play(SoundEvents.SOUL_ESCAPE.value(), 1.4F, 0.6F);
    }

    public static void stage() {
        GameSounds.play(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.2F, 0.45F);
    }

    public static void death() {
        GameSounds.play(SoundEvents.GHAST_HURT, 1.1F, 0.35F);
    }
}
