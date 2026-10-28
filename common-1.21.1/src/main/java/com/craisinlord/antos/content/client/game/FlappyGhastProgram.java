package com.craisinlord.antos.content.client.game;

import com.craisinlord.antos.api.client.game.ComputerGame;
import com.craisinlord.antos.content.client.game.logic.flappyghast.FlappyGhastState;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class FlappyGhastProgram implements ComputerGame.Session {
    private static final String GAME_ID = "flappy_ghast";
    private static final String REWARD_FIRST = "flappy_ghast_15";
    private static final String REWARD_SECOND = "flappy_ghast_40";
    private static final int FIRST_GOAL = 15;
    private static final int SECOND_GOAL = 40;
    private static final long TICK_MILLIS = 50L;
    private static final long STALL_MILLIS = 250L;
    private final FlappyGhastState state = new FlappyGhastState(System.nanoTime());
    private final GameRewardTracker rewards = new GameRewardTracker();
    private boolean sound = true;
    private boolean submitted;
    private int best;
    private int runStartBest;
    private long lastTickAt;
    private String message = "";
    private int messageTicks;
    private int sfxLeft = Integer.MAX_VALUE;

    public FlappyGhastProgram() {
        best = (int) GameData.best(GAME_ID);
        runStartBest = best;
    }

    @Override
    public void tick() {
        long now = System.currentTimeMillis();
        if (lastTickAt != 0L && now - lastTickAt > STALL_MILLIS && state.phase() == FlappyGhastState.Phase.PLAYING) state.setPaused(true);
        lastTickAt = now;
        rewards.tick();
        if (!state.paused() && messageTicks > 0 && --messageTicks == 0) message = "";
        state.tick();
        handle(state.drainEvents());
    }

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int width, int height) {
        render(g, font, x, y, width, height, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int width, int height, int mouseX, int mouseY) {
        state.setViewWidth(FlappyGhastRenderer.viewWidth(width, height));
        best = (int) Math.max(best, GameData.best(GAME_ID));
        int shownBest = Math.max(best, state.score());
        float partial = state.paused() || lastTickAt == 0L ? 1.0F : Math.max(0.0F, Math.min(1.0F, (System.currentTimeMillis() - lastTickAt) / (float) TICK_MILLIS));
        boolean record = state.score() > runStartBest;
        FlappyGhastRenderer.render(g, font, x, y, width, height, state, partial,
                new FlappyGhastRenderer.View(shownBest, sound, message, bonusText(), record));
        sfxLeft = FlappyGhastRenderer.sfxLeft(font, width, sound);
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 300.0F);
        rewards.render(g, font, x, y, width, height);
        g.pose().popPose();
    }

    @Override
    public boolean keyPressed(int keyCode) {
        if (keyCode == 82) {
            if (state.phase() != FlappyGhastState.Phase.GAME_OVER || state.canRestart()) restart();
            return true;
        }
        if (keyCode == 77) {
            sound = !sound;
            return true;
        }
        if (keyCode == 80) {
            state.setPaused(!state.paused());
            return true;
        }
        if (keyCode == 32 || keyCode == 265 || keyCode == 87 || keyCode == 257 || keyCode == 335) {
            action();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (mouseY >= 13 && mouseY < 24 && mouseX >= sfxLeft - 2) {
            sound = !sound;
            return true;
        }
        action();
        return true;
    }

    @Override
    public void close() {
        finishRun();
    }

    private void action() {
        if (state.paused()) state.setPaused(false);
        else if (state.phase() == FlappyGhastState.Phase.GAME_OVER) {
            if (state.canRestart()) restart();
        } else if (state.flap()) handle(state.drainEvents());
    }

    private void restart() {
        finishRun();
        state.reset();
        submitted = false;
        runStartBest = best;
        message = "";
        messageTicks = 0;
    }

    private void finishRun() {
        if (submitted || state.score() <= 0) return;
        submitted = true;
        best = Math.max(best, state.score());
        GameData.submitBest(GAME_ID, state.score());
    }

    private void handle(List<FlappyGhastState.Event> events) {
        for (FlappyGhastState.Event event : events) {
            switch (event) {
                case FLAP -> { if (sound) FlappyGhastSounds.flap(); }
                case SCORE -> { if (sound) FlappyGhastSounds.score(state.score()); }
                case QUARTZ -> {
                    if (sound) FlappyGhastSounds.quartz();
                    show(tr("computer.antos.flappy_ghast.quartz_pickup"), 40);
                }
                case GUST_WARNING -> {
                    if (sound) FlappyGhastSounds.gustWarning();
                    show(tr("computer.antos.flappy_ghast.gust"), 40);
                }
                case GUST -> { if (sound) FlappyGhastSounds.gust(); }
                case STAGE -> {
                    if (sound) FlappyGhastSounds.stage();
                    show(tr("computer.antos.flappy_ghast.stage", tr("computer.antos.flappy_ghast.biome." + state.biome().id())), 40);
                }
                case DEATH -> { if (sound) FlappyGhastSounds.death(); }
                case GAME_OVER -> finishRun();
            }
        }
        if (state.score() >= FIRST_GOAL) rewards.claimOnce(REWARD_FIRST);
        if (state.score() >= SECOND_GOAL) rewards.claimOnce(REWARD_SECOND);
    }

    private void show(String text, int ticks) {
        message = text;
        messageTicks = ticks;
    }

    private String bonusText() {
        if (state.phase() != FlappyGhastState.Phase.READY) return "";
        StringBuilder text = new StringBuilder();
        append(text, GameRewardTracker.dailyBonus(REWARD_FIRST), FIRST_GOAL);
        append(text, GameRewardTracker.dailyBonus(REWARD_SECOND), SECOND_GOAL);
        return text.toString();
    }

    private static void append(StringBuilder text, Component bonus, int goal) {
        String value = bonus.getString();
        if (value.isEmpty()) return;
        if (!text.isEmpty()) text.append("  ");
        text.append(tr("computer.antos.flappy_ghast.bonus", value, goal));
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }
}
