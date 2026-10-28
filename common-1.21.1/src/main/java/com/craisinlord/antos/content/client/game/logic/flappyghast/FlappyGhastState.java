package com.craisinlord.antos.content.client.game.logic.flappyghast;

import com.craisinlord.antos.content.client.game.logic.RestartGate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class FlappyGhastState {
    public static final double FIELD_HEIGHT = 150;
    public static final double LAVA_HEIGHT = 10;
    public static final double FLOOR_Y = FIELD_HEIGHT - LAVA_HEIGHT;
    public static final double GRAVITY = 0.55;
    public static final double FLAP_VELOCITY = -5.2;
    public static final double TERMINAL_VELOCITY = 7.5;
    public static final int FLAP_COOLDOWN = 3;
    public static final double SPRITE_SIZE = 16;
    public static final double HITBOX_WIDTH = 13;
    public static final double HITBOX_HEIGHT = 11;
    public static final double PILLAR_WIDTH = 32;
    public static final double BLOCK_SIZE = 16;
    public static final double MIN_GAP = 46;
    public static final double MAX_CENTRE_DELTA = 55;
    public static final double GAP_MARGIN = 10;
    public static final double QUARTZ_SIZE = 8;
    public static final double HOME_MIN = 36;
    public static final double HOME_MAX = 110;
    public static final double READY_Y = 68;
    public static final int DYING_TICKS = 20;
    public static final int GUST_WARNING_TICKS = 30;
    public static final int GUST_PUSH_TICKS = 16;
    public static final double GUST_PUSH = 1.4;
    public static final int STAGE_BLEND_TICKS = 40;
    public static final int SQUASH_TICKS = 5;

    public enum Phase { READY, PLAYING, DYING, GAME_OVER }

    public enum Event { FLAP, SCORE, QUARTZ, GUST_WARNING, GUST, STAGE, DEATH, GAME_OVER }

    public enum Biome {
        CRIMSON("crimson", 0),
        WARPED("warped", 10),
        SOUL_SAND_VALLEY("soul_sand_valley", 25),
        BASALT_DELTAS("basalt_deltas", 45);

        private final String id;
        private final int threshold;

        Biome(String id, int threshold) {
            this.id = id;
            this.threshold = threshold;
        }

        public String id() { return id; }
        public int threshold() { return threshold; }

        public static Biome forScore(int score) {
            Biome result = CRIMSON;
            for (Biome biome : values()) if (score >= biome.threshold) result = biome;
            return result;
        }
    }

    public static final class Pillar {
        private double x;
        private double prevX;
        private final double gapCentre;
        private final double gapSize;
        private final Biome biome;
        private final boolean lavafall;
        private final boolean quartz;
        private final double quartzOffset;
        private final boolean gust;
        private final int gustDirection;
        private boolean quartzTaken;
        private boolean gustTriggered;
        private boolean scored;

        Pillar(double x, double gapCentre, double gapSize, Biome biome, boolean lavafall, boolean quartz, double quartzOffset, boolean gust, int gustDirection) {
            this.x = x;
            this.prevX = x;
            this.gapCentre = gapCentre;
            this.gapSize = gapSize;
            this.biome = biome;
            this.lavafall = lavafall;
            this.quartz = quartz;
            this.quartzOffset = quartzOffset;
            this.gust = gust;
            this.gustDirection = gustDirection;
        }

        public double x() { return x; }
        public double renderX(float partial) { return prevX + (x - prevX) * partial; }
        public double gapCentre() { return gapCentre; }
        public double gapSize() { return gapSize; }
        public double gapTop() { return gapCentre - gapSize / 2; }
        public double gapBottom() { return gapCentre + gapSize / 2; }
        public Biome biome() { return biome; }
        public boolean lavafall() { return lavafall; }
        public boolean hasQuartz() { return quartz && !quartzTaken; }
        public boolean quartzTaken() { return quartzTaken; }
        public double quartzY() { return gapCentre + quartzOffset; }
        public double quartzCentreX() { return x + PILLAR_WIDTH / 2; }
        public boolean gust() { return gust; }
        public boolean gustTriggered() { return gustTriggered; }
        public int gustDirection() { return gustDirection; }
        public boolean scored() { return scored; }
    }

    private final Random random;
    private final List<Pillar> pillars = new ArrayList<>();
    private final List<Event> events = new ArrayList<>();
    private final RestartGate gate = new RestartGate();
    private Phase phase = Phase.READY;
    private Biome biome = Biome.CRIMSON;
    private Biome previousBiome = Biome.CRIMSON;
    private double viewWidth = 372;
    private double home;
    private double x;
    private double y;
    private double prevX;
    private double prevY;
    private double vy;
    private double lastCentre;
    private int score;
    private int quartz;
    private int ticks;
    private int flapCooldown;
    private int squashTicks;
    private int dyingTicks;
    private int stageTicks;
    private int gustWarning;
    private int gustPush;
    private int gustDirection;
    private boolean paused;
    private boolean autoSpawn = true;

    public FlappyGhastState(long seed) {
        random = new Random(seed);
        home = homeFor(viewWidth);
        reset();
    }

    public void reset() {
        pillars.clear();
        events.clear();
        gate.reset();
        phase = Phase.READY;
        biome = Biome.CRIMSON;
        previousBiome = Biome.CRIMSON;
        stageTicks = STAGE_BLEND_TICKS;
        x = home;
        y = READY_Y;
        prevX = x;
        prevY = y;
        vy = 0;
        lastCentre = FLOOR_Y / 2;
        score = 0;
        quartz = 0;
        ticks = 0;
        flapCooldown = 0;
        squashTicks = 0;
        dyingTicks = 0;
        gustWarning = 0;
        gustPush = 0;
        gustDirection = 0;
        paused = false;
    }

    public void tick() {
        if (paused) return;
        ticks++;
        prevX = x;
        prevY = y;
        for (Pillar pillar : pillars) pillar.prevX = pillar.x;
        if (stageTicks < STAGE_BLEND_TICKS) stageTicks++;
        if (squashTicks > 0) squashTicks--;
        if (flapCooldown > 0) flapCooldown--;
        switch (phase) {
            case READY -> {
                y = READY_Y + Math.sin(ticks * 0.12) * 3;
                spring();
            }
            case PLAYING -> playTick();
            case DYING -> {
                vy = Math.min(vy + GRAVITY, TERMINAL_VELOCITY);
                y = Math.min(y + vy, FLOOR_Y + 2);
                if (--dyingTicks <= 0) {
                    phase = Phase.GAME_OVER;
                    gate.reset();
                    events.add(Event.GAME_OVER);
                }
            }
            case GAME_OVER -> gate.tick();
        }
    }

    public boolean flap() {
        if (paused) return false;
        if (phase == Phase.READY) {
            phase = Phase.PLAYING;
            if (autoSpawn && pillars.isEmpty()) spawn(Math.max(viewWidth, x + 140));
        } else if (phase != Phase.PLAYING || flapCooldown > 0) return false;
        vy = FLAP_VELOCITY;
        flapCooldown = FLAP_COOLDOWN;
        squashTicks = SQUASH_TICKS;
        events.add(Event.FLAP);
        return true;
    }

    public boolean canRestart() {
        return phase == Phase.GAME_OVER && gate.ready();
    }

    public boolean setPaused(boolean value) {
        if (value && phase != Phase.PLAYING) return false;
        paused = value;
        return true;
    }

    public void setViewWidth(double width) {
        viewWidth = Math.max(60, width);
        home = homeFor(viewWidth);
        if (phase == Phase.READY && ticks == 0) {
            x = home;
            prevX = x;
        }
    }

    public List<Event> drainEvents() {
        if (events.isEmpty()) return List.of();
        List<Event> result = List.copyOf(events);
        events.clear();
        return result;
    }

    public static double difficulty(int score) { return Math.min(1.0, Math.max(0, score) / 40.0); }
    public static double scrollSpeed(int score) { return 2.0 + 0.9 * difficulty(score); }
    public static double gapSize(int score) { return Math.max(MIN_GAP, 60 - 14 * difficulty(score)); }
    public static double spacing(int score) { return 118 - 18 * difficulty(score); }

    public Phase phase() { return phase; }
    public Biome biome() { return biome; }
    public Biome previousBiome() { return previousBiome; }
    public float stageBlend() { return Math.min(1.0F, stageTicks / (float) STAGE_BLEND_TICKS); }
    public List<Pillar> pillars() { return Collections.unmodifiableList(pillars); }
    public double viewWidth() { return viewWidth; }
    public double home() { return home; }
    public double x() { return x; }
    public double y() { return y; }
    public double vy() { return vy; }
    public double renderX(float partial) { return prevX + (x - prevX) * partial; }
    public double renderY(float partial) { return prevY + (y - prevY) * partial; }
    public int score() { return score; }
    public int quartz() { return quartz; }
    public int ticks() { return ticks; }
    public int squashTicks() { return squashTicks; }
    public int dyingTicks() { return dyingTicks; }
    public int gustWarning() { return gustWarning; }
    public int gustPush() { return gustPush; }
    public int gustDirection() { return gustDirection; }
    public boolean paused() { return paused; }
    public boolean alive() { return phase == Phase.READY || phase == Phase.PLAYING; }

    private void playTick() {
        vy = Math.min(vy + GRAVITY, TERMINAL_VELOCITY);
        y += vy;
        double speed = scrollSpeed(score);
        updateGust(speed);
        for (Pillar pillar : pillars) pillar.x -= speed;
        pillars.removeIf(pillar -> pillar.x + PILLAR_WIDTH < -20);
        if (autoSpawn) spawnIfNeeded();
        collect();
        if (collides()) die();
    }

    private void updateGust(double speed) {
        if (gustPush > 0) {
            x += gustDirection * GUST_PUSH;
            gustPush--;
        } else {
            if (gustWarning > 0 && --gustWarning == 0) {
                gustPush = GUST_PUSH_TICKS;
                events.add(Event.GUST);
            }
            spring();
        }
        if (gustWarning > 0 || gustPush > 0) return;
        for (Pillar pillar : pillars) {
            if (!pillar.gust || pillar.gustTriggered || pillar.x + PILLAR_WIDTH < x) continue;
            if (pillar.x > x + speed * GUST_WARNING_TICKS + 24) continue;
            pillar.gustTriggered = true;
            gustWarning = GUST_WARNING_TICKS;
            gustDirection = pillar.gustDirection;
            events.add(Event.GUST_WARNING);
            return;
        }
    }

    private void spring() {
        double diff = home - x;
        x = Math.abs(diff) < 0.05 ? home : x + diff * 0.12;
    }

    private void collect() {
        double left = x - HITBOX_WIDTH / 2;
        for (Pillar pillar : pillars) {
            if (pillar.hasQuartz() && overlaps(pillar.quartzCentreX() - QUARTZ_SIZE / 2, pillar.quartzY() - QUARTZ_SIZE / 2, QUARTZ_SIZE, QUARTZ_SIZE)) {
                pillar.quartzTaken = true;
                quartz++;
                score += 2;
                events.add(Event.QUARTZ);
                updateStage();
            }
            if (!pillar.scored && pillar.x + PILLAR_WIDTH < left) {
                pillar.scored = true;
                score++;
                events.add(Event.SCORE);
                updateStage();
            }
        }
    }

    private void updateStage() {
        Biome next = Biome.forScore(score);
        if (next == biome) return;
        previousBiome = biome;
        biome = next;
        stageTicks = 0;
        events.add(Event.STAGE);
    }

    boolean collides() {
        double top = y - HITBOX_HEIGHT / 2;
        double bottom = y + HITBOX_HEIGHT / 2;
        if (top <= 0 || bottom >= FLOOR_Y) return true;
        double left = x - HITBOX_WIDTH / 2;
        double right = x + HITBOX_WIDTH / 2;
        for (Pillar pillar : pillars) {
            if (right <= pillar.x || left >= pillar.x + PILLAR_WIDTH) continue;
            if (top < pillar.gapTop() || bottom > pillar.gapBottom()) return true;
        }
        return false;
    }

    private boolean overlaps(double bx, double by, double bw, double bh) {
        double left = x - HITBOX_WIDTH / 2;
        double top = y - HITBOX_HEIGHT / 2;
        return left < bx + bw && left + HITBOX_WIDTH > bx && top < by + bh && top + HITBOX_HEIGHT > by;
    }

    private void die() {
        phase = Phase.DYING;
        dyingTicks = DYING_TICKS;
        vy = y - HITBOX_HEIGHT / 2 <= 0 ? 1 : Math.min(vy, -2.5);
        gustWarning = 0;
        gustPush = 0;
        events.add(Event.DEATH);
    }

    private void spawnIfNeeded() {
        Pillar last = pillars.isEmpty() ? null : pillars.get(pillars.size() - 1);
        if (last == null) spawn(Math.max(viewWidth, x + 140));
        else if (last.x <= viewWidth - spacing(score)) spawn(viewWidth);
    }

    Pillar spawn(double px) {
        double gap = gapSize(score);
        double low = GAP_MARGIN + gap / 2;
        double high = FLOOR_Y - GAP_MARGIN - gap / 2;
        double centre = lastCentre + (random.nextDouble() * 2 - 1) * MAX_CENTRE_DELTA;
        centre = Math.max(low, Math.min(high, centre));
        lastCentre = centre;
        boolean hasQuartz = random.nextInt(5) == 0;
        double offset = (random.nextBoolean() ? 1 : -1) * gap / 4;
        int gustRoll = random.nextInt(12);
        boolean gust = score >= 45 ? gustRoll % 3 == 0 : score >= 25 && gustRoll % 4 == 0;
        int direction = random.nextBoolean() ? 1 : -1;
        boolean lavafall = random.nextInt(4) == 0 && biome == Biome.WARPED;
        Pillar pillar = new Pillar(px, centre, gap, biome, lavafall, hasQuartz, offset, gust, direction);
        pillars.add(pillar);
        return pillar;
    }

    Pillar addPillar(double px, double gapCentre, double gap, boolean hasQuartz, double quartzOffset, boolean gust, int direction) {
        Pillar pillar = new Pillar(px, gapCentre, gap, biome, false, hasQuartz, quartzOffset, gust, direction);
        pillars.add(pillar);
        return pillar;
    }

    void setAutoSpawn(boolean value) { autoSpawn = value; }

    void place(double newY, double newVy) {
        y = newY;
        prevY = newY;
        vy = newVy;
    }

    void setScore(int value) {
        score = value;
        biome = Biome.forScore(value);
        previousBiome = biome;
    }

    private static double homeFor(double width) {
        return Math.max(HOME_MIN, Math.min(HOME_MAX, width * 0.25));
    }
}
