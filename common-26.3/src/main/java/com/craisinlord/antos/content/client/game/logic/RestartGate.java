package com.craisinlord.antos.content.client.game.logic;

public final class RestartGate {
    public static final int DEFAULT_DELAY = 12;
    private final int delay;
    private int ticks;

    public RestartGate() {
        this(DEFAULT_DELAY);
    }

    public RestartGate(int delay) {
        this.delay = Math.max(0, delay);
    }

    public void reset() {
        ticks = 0;
    }

    public void tick() {
        if (ticks < delay) ticks++;
    }

    public boolean ready() {
        return ticks >= delay;
    }
}
