package com.craisinlord.antos.content.client.game;

import com.craisinlord.antos.api.client.game.ComputerGame;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;

public final class CakeStackProgram implements ComputerGame.Session {
    private static final int TRAVEL_LEFT = 8;
    private static final int TRAVEL_RIGHT = CakeStackRenderer.FIELD_WIDTH - 8;
    private static final int RESTART_DELAY = 12;
    private final List<CakeStackRenderer.Layer> layers = new ArrayList<>();
    private int currentX;
    private int currentWidth;
    private int direction;
    private int score;
    private int streak;
    private int best;
    private int ticks;
    private int offcutX;
    private int offcutWidth;
    private int offcutTicks;
    private int perfectPulseTicks;
    private int overTicks;
    private boolean gameOver;
    private boolean greenTint;

    public CakeStackProgram() {
        reset();
    }

    @Override
    public void tick() {
        if (gameOver) {
            if (overTicks < RESTART_DELAY) overTicks++;
            if (offcutTicks > 0) offcutTicks--;
            return;
        }
        ticks++;
        currentX += direction * speed();
        int right = Math.max(TRAVEL_LEFT, TRAVEL_RIGHT - currentWidth);
        if (currentX >= right) { currentX = right; direction = -1; }
        if (currentX <= TRAVEL_LEFT) { currentX = TRAVEL_LEFT; direction = 1; }
        if (offcutTicks > 0) offcutTicks--;
        if (perfectPulseTicks > 0) perfectPulseTicks--;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int width, int height) {
        best = (int) Math.max(best, GameData.best("cake_stack"));
        CakeStackRenderer.render(graphics, font, x, y, width, height, layers, currentX, currentWidth, score, best,
                streak, gameOver, greenTint, ticks, offcutX, offcutWidth, offcutTicks, perfectPulseTicks);
    }

    @Override
    public boolean keyPressed(int keyCode) {
        if (keyCode == 71) { greenTint = !greenTint; return true; }
        if (keyCode == 82) { reset(); return true; }
        if (keyCode == 32 || keyCode == 257 || keyCode == 335) {
            action();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        action();
        return true;
    }

    private void action() {
        if (!gameOver) drop();
        else if (overTicks >= RESTART_DELAY) reset();
    }

    private int speed() {
        return Math.min(5, 2 + score / 7);
    }

    private void drop() {
        CakeStackRenderer.Layer top = layers.get(layers.size() - 1);
        int left = Math.max(currentX, top.x());
        int right = Math.min(currentX + currentWidth, top.x() + top.width());
        int overlap = right - left;
        if (overlap <= 0) {
            gameOver = true;
            overTicks = 0;
            best = Math.max(best, score);
            GameData.submitBest("cake_stack", best);
            offcutX = currentX;
            offcutWidth = currentWidth;
            offcutTicks = 10;
            CakeStackSounds.miss();
            return;
        }
        boolean perfect = Math.abs(currentX - top.x()) <= Math.max(2, (speed() + 1) / 2);
        if (perfect) {
            left = top.x();
            overlap = top.width();
            offcutTicks = 0;
            streak++;
        } else {
            offcutX = currentX < top.x() ? currentX : right;
            offcutWidth = currentWidth - overlap;
            offcutTicks = offcutWidth > 0 ? 10 : 0;
            streak = 0;
        }
        perfectPulseTicks = perfect ? 18 : 0;
        layers.add(new CakeStackRenderer.Layer(left, overlap));
        score += perfect ? 2 : 1;
        CakeStackSounds.place(score, perfect);
        best = Math.max(best, score);
        currentWidth = overlap;
        currentX = TRAVEL_LEFT;
        direction = 1;
        ticks = 0;
    }

    private void reset() {
        layers.clear();
        int baseWidth = 128;
        layers.add(new CakeStackRenderer.Layer((CakeStackRenderer.FIELD_WIDTH - baseWidth) / 2, baseWidth));
        currentWidth = baseWidth;
        currentX = TRAVEL_LEFT;
        direction = 1;
        score = 0;
        streak = 0;
        ticks = 0;
        offcutTicks = 0;
        perfectPulseTicks = 0;
        overTicks = 0;
        gameOver = false;
    }
}
