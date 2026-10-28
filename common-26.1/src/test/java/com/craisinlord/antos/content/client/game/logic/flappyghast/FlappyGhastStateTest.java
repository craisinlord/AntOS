package com.craisinlord.antos.content.client.game.logic.flappyghast;

import java.util.List;

public final class FlappyGhastStateTest {
    private static final double EPSILON = 1.0E-9;

    public static void main(String[] args) {
        gravityAndTerminal();
        flapCooldown();
        readyHasNoGravity();
        ceilingKills();
        floorKills();
        pillarLip();
        onePointPerPillar();
        gapRules();
        seededDeterminism();
        quartzCountedOnce();
        gustPushAndReturn();
        stageThresholds();
        pauseFreezes();
        resizeKeepsPillars();
        System.out.println("FlappyGhastStateTest passed");
    }

    private static FlappyGhastState playing() {
        FlappyGhastState state = new FlappyGhastState(7L);
        state.setAutoSpawn(false);
        state.flap();
        state.drainEvents();
        return state;
    }

    private static void gravityAndTerminal() {
        FlappyGhastState state = playing();
        check(near(state.vy(), FlappyGhastState.FLAP_VELOCITY), "flap sets upward velocity");
        state.tick();
        check(near(state.vy(), FlappyGhastState.FLAP_VELOCITY + FlappyGhastState.GRAVITY), "gravity adds each tick");
        state.place(20, 7.3);
        state.tick();
        check(near(state.vy(), FlappyGhastState.TERMINAL_VELOCITY), "fall speed caps at terminal velocity");
        check(near(state.y(), 27.5), "position uses the capped velocity");
    }

    private static void flapCooldown() {
        FlappyGhastState state = playing();
        state.place(70, 0);
        check(!state.flap(), "second flap is blocked by the cooldown");
        state.tick();
        state.tick();
        check(!state.flap(), "flap still cooling down after two ticks");
        state.tick();
        state.place(70, 0);
        check(state.flap(), "flap allowed after three ticks");
        check(near(state.vy(), FlappyGhastState.FLAP_VELOCITY), "flap resets velocity");
    }

    private static void readyHasNoGravity() {
        FlappyGhastState state = new FlappyGhastState(3L);
        for (int i = 0; i < 60; i++) state.tick();
        check(state.phase() == FlappyGhastState.Phase.READY, "ready waits for input");
        check(Math.abs(state.y() - FlappyGhastState.READY_Y) <= 3 + EPSILON, "ready only bobs");
        check(near(state.vy(), 0), "ready has no velocity");
        check(state.pillars().isEmpty(), "ready spawns no pillars");
    }

    private static void ceilingKills() {
        FlappyGhastState state = playing();
        state.place(8, FlappyGhastState.FLAP_VELOCITY);
        state.tick();
        check(state.phase() == FlappyGhastState.Phase.DYING, "touching the ceiling kills");
        check(state.drainEvents().contains(FlappyGhastState.Event.DEATH), "ceiling death emits an event");
        for (int i = 0; i < FlappyGhastState.DYING_TICKS; i++) state.tick();
        check(state.phase() == FlappyGhastState.Phase.GAME_OVER, "dying ends in game over");
        check(!state.canRestart(), "restart gate starts closed");
        for (int i = 0; i < 12; i++) state.tick();
        check(state.canRestart(), "restart gate opens");
    }

    private static void floorKills() {
        FlappyGhastState state = playing();
        state.place(128, 0);
        for (int i = 0; i < 10 && state.alive(); i++) state.tick();
        check(state.phase() == FlappyGhastState.Phase.DYING, "lava floor kills");
    }

    private static void pillarLip() {
        FlappyGhastState state = playing();
        double halfHeight = FlappyGhastState.HITBOX_HEIGHT / 2;
        double halfWidth = FlappyGhastState.HITBOX_WIDTH / 2;
        state.addPillar(state.x() - 4, 75, 50, false, 0, false, 1);
        state.place(50 + halfHeight - 0.1, 0);
        check(state.collides(), "hitbox above the top lip collides");
        state.place(50 + halfHeight + 0.1, 0);
        check(!state.collides(), "hitbox just below the top lip is clear");
        state.place(100 - halfHeight + 0.1, 0);
        check(state.collides(), "hitbox below the bottom lip collides");
        state.place(100 - halfHeight - 0.1, 0);
        check(!state.collides(), "hitbox just above the bottom lip is clear");
        FlappyGhastState edge = playing();
        edge.addPillar(edge.x() + halfWidth, 120, 44, false, 0, false, 1);
        edge.place(60, 0);
        check(!edge.collides(), "touching the pillar face edge is not a hit");
        FlappyGhastState inside = playing();
        inside.addPillar(inside.x() + halfWidth - 0.1, 120, 44, false, 0, false, 1);
        inside.place(60, 0);
        check(inside.collides(), "overlapping the pillar face is a hit");
    }

    private static void onePointPerPillar() {
        FlappyGhastState state = playing();
        state.addPillar(state.x() + 20, 70, 100, false, 0, false, 1);
        for (int i = 0; i < 60; i++) {
            state.place(70, 0);
            state.tick();
        }
        check(state.alive(), "ghast survives a wide gap");
        check(state.score() == 1, "one point per pillar");
        long scores = state.drainEvents().stream().filter(event -> event == FlappyGhastState.Event.SCORE).count();
        check(scores == 1, "one score event per pillar");
    }

    private static void gapRules() {
        FlappyGhastState state = new FlappyGhastState(11L);
        double previous = FlappyGhastState.FLOOR_Y / 2;
        for (int i = 0; i < 600; i++) {
            state.setScore(i % 80);
            FlappyGhastState.Pillar pillar = state.spawn(400);
            check(pillar.gapSize() >= FlappyGhastState.MIN_GAP - EPSILON, "gap never below minimum");
            check(Math.abs(pillar.gapCentre() - previous) <= FlappyGhastState.MAX_CENTRE_DELTA + EPSILON, "gap centre moves at most 55");
            check(pillar.gapTop() >= FlappyGhastState.GAP_MARGIN - EPSILON, "gap stays below the ceiling margin");
            check(pillar.gapBottom() <= FlappyGhastState.FLOOR_Y - FlappyGhastState.GAP_MARGIN + EPSILON, "gap stays above the lava margin");
            previous = pillar.gapCentre();
        }
        check(near(FlappyGhastState.gapSize(0), 60), "starting gap is 60");
        check(near(FlappyGhastState.gapSize(40), 46), "hardest gap is 46");
        check(near(FlappyGhastState.scrollSpeed(0), 2.0), "starting scroll is 2");
        check(near(FlappyGhastState.scrollSpeed(100), 2.9), "scroll caps at 2.9");
        check(near(FlappyGhastState.spacing(40), 100), "spacing tightens to 100");
        check(near(FlappyGhastState.PILLAR_WIDTH, 2 * FlappyGhastState.BLOCK_SIZE), "pillars are two blocks wide");
    }

    private static void seededDeterminism() {
        FlappyGhastState first = new FlappyGhastState(99L);
        FlappyGhastState second = new FlappyGhastState(99L);
        for (int i = 0; i < 400; i++) {
            if (first.y() > 80) first.flap();
            if (second.y() > 80) second.flap();
            first.tick();
            second.tick();
            if (i == 0) {
                first.flap();
                second.flap();
            }
        }
        check(near(first.y(), second.y()), "same seed gives same flight");
        check(first.score() == second.score(), "same seed gives same score");
        List<FlappyGhastState.Pillar> a = first.pillars();
        List<FlappyGhastState.Pillar> b = second.pillars();
        check(a.size() == b.size(), "same seed gives same pillar count");
        for (int i = 0; i < a.size(); i++) {
            check(near(a.get(i).gapCentre(), b.get(i).gapCentre()), "same seed gives same gaps");
            check(a.get(i).hasQuartz() == b.get(i).hasQuartz(), "same seed gives same quartz");
        }
        FlappyGhastState third = new FlappyGhastState(5L);
        FlappyGhastState fourth = new FlappyGhastState(5L);
        for (int i = 0; i < 50; i++) check(near(third.spawn(300).gapCentre(), fourth.spawn(300).gapCentre()), "spawn sequence repeats");
    }

    private static void quartzCountedOnce() {
        FlappyGhastState state = playing();
        double gap = 100;
        state.addPillar(state.x() - FlappyGhastState.PILLAR_WIDTH / 2, 75, gap, true, gap / 4, false, 1);
        for (int i = 0; i < 3; i++) {
            state.place(75 + gap / 4, 0);
            state.tick();
        }
        check(state.alive(), "ghast survives inside the gap");
        check(state.quartz() == 1, "quartz is counted once");
        check(state.score() == 2, "quartz adds two points");
        check(!state.pillars().get(0).hasQuartz(), "quartz is removed after pickup");
        long pickups = state.drainEvents().stream().filter(event -> event == FlappyGhastState.Event.QUARTZ).count();
        check(pickups == 1, "one quartz event");
    }

    private static void gustPushAndReturn() {
        for (int direction : new int[]{-1, 1}) {
            FlappyGhastState state = playing();
            double home = state.home();
            state.addPillar(state.x() + 200, 75, 90, false, 0, true, direction);
            boolean warned = false;
            int warnTick = -1;
            int gustTick = -1;
            double extreme = home;
            for (int i = 0; i < 220; i++) {
                state.place(75, 0);
                state.tick();
                List<FlappyGhastState.Event> events = state.drainEvents();
                if (events.contains(FlappyGhastState.Event.GUST_WARNING)) { warned = true; warnTick = i; }
                if (events.contains(FlappyGhastState.Event.GUST)) gustTick = i;
                extreme = direction > 0 ? Math.max(extreme, state.x()) : Math.min(extreme, state.x());
            }
            check(warned, "gust warns first");
            check(gustTick - warnTick == FlappyGhastState.GUST_WARNING_TICKS, "warning lasts 30 ticks");
            double expected = FlappyGhastState.GUST_PUSH * FlappyGhastState.GUST_PUSH_TICKS;
            check(Math.abs(Math.abs(extreme - home) - expected) < 0.5, "gust pushes sideways by 1.4 for 16 ticks");
            check(near(state.x(), home), "ghast springs back home");
        }
        FlappyGhastState narrow = new FlappyGhastState(1L);
        narrow.setViewWidth(80);
        check(near(narrow.home(), FlappyGhastState.HOME_MIN), "home clamps to 36");
        narrow.setViewWidth(2000);
        check(near(narrow.home(), FlappyGhastState.HOME_MAX), "home clamps to 110");
    }

    private static void stageThresholds() {
        check(FlappyGhastState.Biome.forScore(0) == FlappyGhastState.Biome.CRIMSON, "crimson at 0");
        check(FlappyGhastState.Biome.forScore(9) == FlappyGhastState.Biome.CRIMSON, "crimson until 9");
        check(FlappyGhastState.Biome.forScore(10) == FlappyGhastState.Biome.WARPED, "warped at 10");
        check(FlappyGhastState.Biome.forScore(24) == FlappyGhastState.Biome.WARPED, "warped until 24");
        check(FlappyGhastState.Biome.forScore(25) == FlappyGhastState.Biome.SOUL_SAND_VALLEY, "soul sand valley at 25");
        check(FlappyGhastState.Biome.forScore(45) == FlappyGhastState.Biome.BASALT_DELTAS, "basalt deltas at 45");
        FlappyGhastState state = playing();
        state.setScore(9);
        state.addPillar(state.x() - FlappyGhastState.PILLAR_WIDTH - 8, 75, 100, false, 0, false, 1);
        state.place(75, 0);
        state.tick();
        check(state.score() == 10, "passing a pillar scores");
        check(state.biome() == FlappyGhastState.Biome.WARPED, "stage advances with score");
        check(state.previousBiome() == FlappyGhastState.Biome.CRIMSON, "previous stage kept for blending");
        check(state.drainEvents().contains(FlappyGhastState.Event.STAGE), "stage change emits an event");
        check(state.stageBlend() < 1.0F, "stage blend restarts");
    }

    private static void pauseFreezes() {
        FlappyGhastState state = new FlappyGhastState(21L);
        state.flap();
        state.tick();
        check(state.setPaused(true), "pause while playing");
        double y = state.y();
        double pillarX = state.pillars().get(0).x();
        int ticks = state.ticks();
        for (int i = 0; i < 20; i++) state.tick();
        check(near(state.y(), y), "pause freezes the ghast");
        check(near(state.pillars().get(0).x(), pillarX), "pause freezes pillars");
        check(state.ticks() == ticks, "pause freezes the clock");
        check(!state.flap(), "no flapping while paused");
        state.setPaused(false);
        state.tick();
        check(state.ticks() == ticks + 1, "unpause resumes");
        FlappyGhastState ready = new FlappyGhastState(21L);
        check(!ready.setPaused(true), "cannot pause before playing");
    }

    private static void resizeKeepsPillars() {
        FlappyGhastState state = new FlappyGhastState(13L);
        state.setViewWidth(300);
        state.flap();
        for (int i = 0; i < 5; i++) state.tick();
        check(state.pillars().size() == 1, "first pillar spawned");
        double before = state.pillars().get(0).x();
        check(before >= 300 - 5 * FlappyGhastState.scrollSpeed(0) - EPSILON, "first pillar spawns at the right edge");
        state.setViewWidth(600);
        check(state.pillars().size() == 1 && near(state.pillars().get(0).x(), before), "resize keeps pillars");
        state.place(70, 0);
        state.tick();
        check(state.pillars().size() == 2, "wider view spawns the next pillar");
        check(near(state.pillars().get(1).x(), 600), "new pillar spawns at the new right edge");
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1.0E-6;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
