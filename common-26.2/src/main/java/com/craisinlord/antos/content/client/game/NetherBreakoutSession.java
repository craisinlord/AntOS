package com.craisinlord.antos.content.client.game;

import com.craisinlord.antos.api.client.game.ComputerGame;
import com.craisinlord.antos.content.client.AntOSUiSounds;
import com.craisinlord.antos.content.client.palette.Ui;
import net.minecraft.client.gui.Font;
import com.craisinlord.antos.content.client.ThemedGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.Random;

public final class NetherBreakoutSession implements ComputerGame.Session {
    private static final int COLS = 10;
    private static final int ROWS = 6;
    private static final int BEDROCK = 5;
    private static final int BRICK_TOP = 30;
    private static final int CEILING = 25;
    private static final int BALL_RADIUS = 4;
    private static final int MAX_LIVES = 5;
    private static final int RESTART_DELAY = 12;
    private static final double MAX_BOUNCE_ANGLE = Math.toRadians(60);
    private static final ItemStack[] BRICK_BLOCKS = {
            ItemStack.EMPTY,
            new ItemStack(Blocks.NETHERRACK),
            new ItemStack(Blocks.NETHER_GOLD_ORE),
            new ItemStack(Blocks.NETHER_QUARTZ_ORE),
            new ItemStack(Blocks.ANCIENT_DEBRIS),
            new ItemStack(Blocks.BEDROCK)
    };
    private static final ItemStack TNT = new ItemStack(Blocks.TNT);
    private static final ItemStack SLIME = new ItemStack(Blocks.SLIME_BLOCK);
    private static final ItemStack EXTRA_LIFE = new ItemStack(Items.GHAST_TEAR);
    private static final ItemStack PICKAXE = new ItemStack(Items.NETHERITE_PICKAXE);
    private final Random random = new Random();
    private final int[][] bricks = new int[ROWS][COLS];
    private double ballX;
    private double ballY;
    private double velocityX;
    private double velocityY;
    private double paddleX;
    private double dropX;
    private double dropY;
    private int dropType;
    private int dropBrickRow;
    private int dropBrickColumn;
    private int paddleTicks;
    private int serveTicks;
    private int score;
    private int lives;
    private int wave;
    private int cleared;
    private int width = 374;
    private int height = 190;
    private int lastMouseX = Integer.MIN_VALUE;
    private int overTicks;
    private boolean launched;
    private boolean paused;
    private boolean gameOver;
    private boolean waveCleared;
    private boolean soundEnabled = true;
    private boolean widePaddle;
    private int hitFlash;
    private int messageTicks;
    private String message = "";

    public NetherBreakoutSession() {
        reset();
    }

    @Override
    public void tick() {
        if (gameOver || waveCleared) {
            if (overTicks < RESTART_DELAY) overTicks++;
            return;
        }
        if (paused) return;
        if (hitFlash > 0) hitFlash--;
        if (messageTicks > 0) messageTicks--;
        if (paddleTicks > 0 && --paddleTicks == 0) {
            widePaddle = false;
            clampPaddle();
        }
        if (dropType != 0) {
            dropY += 1.2;
            int paddleTop = paddleTop();
            if (dropY + 10 >= paddleTop && dropY - 10 <= paddleTop + 4 && Math.abs(dropX - paddleX - paddleWidth() / 2.0) < paddleWidth() / 2.0 + 7) {
                activateDrop();
                dropType = 0;
            } else if (dropY - 10 > height - 15) dropType = 0;
        }
        if (waveCleared) return;
        if (!launched) {
            restBall();
            if (serveTicks > 0 && --serveTicks == 0) launch();
            return;
        }
        double speed = Math.hypot(velocityX, velocityY);
        int steps = Math.max(1, (int) Math.ceil(speed / 2.0));
        for (int i = 0; i < steps && launched && !waveCleared; i++) step(velocityX / steps, velocityY / steps);
    }

    @Override
    public void render(ThemedGraphics g, Font font, int x, int y, int width, int height) {
        render(g, font, x, y, width, height, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    @Override
    public void render(ThemedGraphics g, Font font, int x, int y, int width, int height, int mouseX, int mouseY) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        boolean mouseInside = mouseX >= 0 && mouseX < width && mouseY >= 0 && mouseY < height;
        if (mouseInside && mouseX != lastMouseX && !paused && !gameOver) paddleX = mouseX - paddleWidth() / 2.0;
        if (mouseInside) lastMouseX = mouseX;
        clampPaddle();
        if (!launched) restBall();
        int right = x + width - 1;
        g.fill(x, y, right, y + height, Ui.GAME_FILL);
        g.renderOutline(x, y, width - 1, height - 1, Ui.GAME_OUTLINE);
        String scoreText = tr("computer.antos.nether_breakout.score", score);
        String sfx = tr(soundEnabled ? "computer.antos.nether_breakout.sfx_on" : "computer.antos.nether_breakout.sfx_off");
        g.drawString(font, fit(font, tr("computer.antos.nether_breakout.title"), width - font.width(scoreText) - 21), x + 7, y + 4, Ui.TEXT_STRONG, false);
        g.drawString(font, scoreText, right - font.width(scoreText) - 7, y + 4, Ui.TEXT, false);
        g.drawString(font, fit(font, tr("computer.antos.nether_breakout.stats", wave, lives, remainingBricks()), width - font.width(sfx) - 21), x + 7, y + 15, Ui.TEXT_MUTED, false);
        g.drawString(font, sfx, right - font.width(sfx) - 7, y + 15, soundEnabled ? Ui.TEXT_MUTED : Ui.ERROR, false);
        g.fill(x + 7, y + 23, right - 7, y + 24, Ui.LINE);
        int brickWidth = brickWidth();
        int brickHeight = rowHeight() - 2;
        boolean icons = brickWidth >= 18 && brickHeight >= 16;
        for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++) {
            int hp = bricks[r][c];
            if (hp == 0) continue;
            int bx = x + brickX(c);
            int by = y + brickY(r);
            g.fill(bx, by, bx + brickWidth, by + brickHeight, Ui.GAME_OUTLINE);
            g.fill(bx + 1, by + 1, bx + brickWidth - 1, by + brickHeight - 1, icons ? Ui.PANEL : brickColor(hp));
            if (icons) g.renderItem(BRICK_BLOCKS[hp], bx + (brickWidth - 16) / 2, by + (brickHeight - 16) / 2);
            if (hp > 1 && hp < BEDROCK && brickWidth >= 18 && brickHeight >= 10) {
                g.fill(bx + brickWidth - 9, by + 1, bx + brickWidth - 1, by + 9, Ui.PANEL_ALT);
                g.drawCenteredString(font, Integer.toString(hp), bx + brickWidth - 5, by + 1, Ui.TEXT_STRONG);
            }
        }
        int paddleTop = paddleTop();
        int px = x + (int) paddleX;
        g.fill(px - 2, y + paddleTop + 2, px + paddleWidth() + 2, y + paddleTop + 6, Ui.GAME_OUTLINE);
        g.fill(px, y + paddleTop, px + paddleWidth(), y + paddleTop + 4, Ui.ACCENT);
        g.fill(px + 2, y + paddleTop, px + paddleWidth() - 2, y + paddleTop + 1, Ui.alpha(Ui.TEXT_BRIGHT, 0x50));
        float pickaxeScale = widePaddle ? 2.0F : 1.4F;
        g.pose().pushPose();
        g.pose().translate(x + paddleX + paddleWidth() / 2.0 - 8 * pickaxeScale, y + paddleTop + 4 - 16 * pickaxeScale, 0);
        g.pose().scale(pickaxeScale, pickaxeScale, 1.0F);
        g.renderItem(PICKAXE, 0, 0);
        g.pose().popPose();
        int bx = x + (int) Math.round(ballX);
        int by = y + (int) Math.round(ballY);
        g.fill(bx - BALL_RADIUS, by - BALL_RADIUS, bx + BALL_RADIUS, by + BALL_RADIUS, Ui.TEXT_STRONG);
        g.fill(bx - 2, by - 2, bx + 2, by + 2, hitFlash > 0 ? Ui.TEXT_BRIGHT : Ui.TEXT);
        if (dropType != 0) drawDrop(g, x, y);
        if (paused || gameOver || waveCleared) {
            int boxWidth = Math.max(20, Math.min(width - 24, 150));
            int boxX = x + (width - boxWidth) / 2;
            int boxY = Math.max(y + 2, y + height / 2 - 15);
            int accent = gameOver ? Ui.ERROR : Ui.ACCENT;
            g.fill(boxX, boxY, boxX + boxWidth, boxY + 30, Ui.alpha(Ui.BACKGROUND, 0xE8));
            g.renderOutline(boxX, boxY, boxWidth, 30, accent);
            String title = tr(gameOver ? "computer.antos.nether_breakout.game_over" : waveCleared ? "computer.antos.nether_breakout.cleared" : "computer.antos.nether_breakout.paused");
            String hint = tr(gameOver ? "computer.antos.nether_breakout.restart" : waveCleared ? "computer.antos.nether_breakout.next_wave" : "computer.antos.nether_breakout.resume");
            g.drawCenteredString(font, fit(font, title, boxWidth - 8), x + width / 2, boxY + 5, accent);
            g.drawCenteredString(font, fit(font, hint, boxWidth - 8), x + width / 2, boxY + 17, Ui.TEXT_BRIGHT);
        } else {
            boolean event = messageTicks > 0;
            String hint = event ? message : !launched ? tr("computer.antos.nether_breakout.launch") : tr("computer.antos.nether_breakout.controls");
            g.drawCenteredString(font, fit(font, hint, width - 14), x + width / 2, y + height - 11, event ? Ui.ACCENT : !launched ? Ui.TEXT : Ui.TEXT_MUTED);
        }
    }

    @Override
    public boolean keyPressed(int keyCode) {
        if (keyCode == InputConstants.KEY_R) { reset(); return true; }
        if (keyCode == InputConstants.KEY_M) { soundEnabled = !soundEnabled; if (soundEnabled) AntOSUiSounds.play(AntOSUiSounds.Cue.BREAKOUT_POWERUP); return true; }
        if (keyCode == InputConstants.KEY_P) {
            if (gameOver || waveCleared) return true;
            paused = !paused;
            if (soundEnabled) AntOSUiSounds.play(paused ? AntOSUiSounds.Cue.CLICK : AntOSUiSounds.Cue.BREAKOUT_POWERUP);
            return true;
        }
        if (keyCode == InputConstants.KEY_SPACE || keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
            action();
            return true;
        }
        if (keyCode == InputConstants.KEY_LEFT || keyCode == InputConstants.KEY_A || keyCode == InputConstants.KEY_RIGHT || keyCode == InputConstants.KEY_D) {
            if (paused || gameOver || waveCleared) return true;
            paddleX += keyCode == InputConstants.KEY_LEFT || keyCode == InputConstants.KEY_A ? -14 : 14;
            clampPaddle();
            if (!launched) restBall();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (paused || gameOver || waveCleared || !launched) action();
        else {
            paddleX = mouseX - paddleWidth() / 2.0;
            clampPaddle();
        }
        return true;
    }

    private void action() {
        if (gameOver) { if (overTicks >= RESTART_DELAY) reset(); }
        else if (waveCleared) { if (overTicks >= RESTART_DELAY) nextWave(); }
        else if (paused) paused = false;
        else if (!launched) launch();
    }

    private void reset() {
        score = 0;
        lives = 3;
        wave = 1;
        gameOver = false;
        paused = false;
        widePaddle = false;
        paddleTicks = 0;
        messageTicks = 0;
        paddleX = Math.max(5, (width - paddleWidth()) / 2.0);
        buildWave();
    }

    private void nextWave() {
        wave++;
        message = tr("computer.antos.nether_breakout.wave", wave);
        messageTicks = 40;
        buildWave();
    }

    private void buildWave() {
        for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++) {
            if (r == ROWS - 1) bricks[r][c] = c % 4 == 1 ? 0 : BEDROCK;
            else bricks[r][c] = Math.max(1, 4 - r / 2);
        }
        cleared = 0;
        dropType = 0;
        waveCleared = false;
        launched = false;
        serveTicks = 0;
        overTicks = 0;
        clampPaddle();
        restBall();
    }

    private void launch() {
        launched = true;
        double speed = baseSpeed();
        double angle = Math.toRadians(30) * (random.nextBoolean() ? 1 : -1);
        velocityX = speed * Math.sin(angle);
        velocityY = -speed * Math.cos(angle);
        if (soundEnabled) AntOSUiSounds.play(AntOSUiSounds.Cue.BREAKOUT_PADDLE);
    }

    private void step(double dx, double dy) {
        double previousBottom = ballY + BALL_RADIUS;
        ballX += dx;
        if (ballX - BALL_RADIUS < 3) { ballX = 3 + BALL_RADIUS; velocityX = Math.abs(velocityX); }
        if (ballX + BALL_RADIUS > width - 3) { ballX = width - 3 - BALL_RADIUS; velocityX = -Math.abs(velocityX); }
        if (dx != 0 && hitBrick()) { ballX -= dx; velocityX = -velocityX; }
        ballY += dy;
        if (ballY - BALL_RADIUS < CEILING) { ballY = CEILING + BALL_RADIUS; velocityY = Math.abs(velocityY); }
        if (dy != 0 && hitBrick()) { ballY -= dy; velocityY = -velocityY; }
        int paddleTop = paddleTop();
        if (velocityY > 0 && previousBottom <= paddleTop && ballY + BALL_RADIUS >= paddleTop
                && ballX + BALL_RADIUS >= paddleX && ballX - BALL_RADIUS <= paddleX + paddleWidth()) {
            ballY = paddleTop - BALL_RADIUS;
            double offset = Math.max(-1, Math.min(1, (ballX - (paddleX + paddleWidth() / 2.0)) / (paddleWidth() / 2.0)));
            double speed = Math.min(maxSpeed(), Math.hypot(velocityX, velocityY) + 0.05);
            double angle = offset * MAX_BOUNCE_ANGLE;
            velocityX = speed * Math.sin(angle);
            velocityY = -speed * Math.cos(angle);
            if (soundEnabled) AntOSUiSounds.play(AntOSUiSounds.Cue.BREAKOUT_PADDLE);
        }
        if (ballY - BALL_RADIUS > height - 15) loseBall();
    }

    private void loseBall() {
        lives--;
        launched = false;
        if (lives <= 0) {
            lives = 0;
            gameOver = true;
            overTicks = 0;
        } else {
            serveTicks = 16;
            message = tr("computer.antos.nether_breakout.next_ball");
            messageTicks = 30;
        }
        if (soundEnabled) AntOSUiSounds.play(AntOSUiSounds.Cue.BREAKOUT_MISS);
    }

    private boolean hitBrick() {
        int brickWidth = brickWidth();
        int brickHeight = rowHeight() - 2;
        double left = ballX - BALL_RADIUS;
        double right = ballX + BALL_RADIUS;
        double top = ballY - BALL_RADIUS;
        double bottom = ballY + BALL_RADIUS;
        for (int r = 0; r < ROWS; r++) {
            int by = brickY(r);
            if (bottom <= by || top >= by + brickHeight) continue;
            for (int c = 0; c < COLS; c++) {
                if (bricks[r][c] == 0) continue;
                int bx = brickX(c);
                if (right <= bx || left >= bx + brickWidth) continue;
                damage(r, c);
                hitFlash = 3;
                return true;
            }
        }
        return false;
    }

    private void damage(int r, int c) {
        if (bricks[r][c] == BEDROCK) {
            if (soundEnabled) AntOSUiSounds.play(AntOSUiSounds.Cue.BREAKOUT_HIT);
            return;
        }
        bricks[r][c]--;
        if (bricks[r][c] > 0) {
            if (soundEnabled) AntOSUiSounds.play(AntOSUiSounds.Cue.BREAKOUT_HIT);
            return;
        }
        breakBrick(r);
        if (dropType == 0 && cleared % 5 == 0) {
            dropType = random.nextInt(3) + 1;
            dropBrickRow = r;
            dropBrickColumn = c;
            dropX = brickX(c) + brickWidth() / 2.0;
            dropY = brickY(r) + (rowHeight() - 2) / 2.0;
        }
        if (soundEnabled) AntOSUiSounds.play(AntOSUiSounds.Cue.BREAKOUT_BRICK);
        checkCleared();
    }

    private void breakBrick(int r) {
        score += (ROWS - r) * 10;
        cleared++;
    }

    private void checkCleared() {
        if (remainingBricks() > 0) return;
        waveCleared = true;
        launched = false;
        overTicks = 0;
        dropType = 0;
    }

    private void activateDrop() {
        if (dropType == 1) {
            int row = dropBrickRow;
            int col = dropBrickColumn;
            for (int r = Math.max(0, row - 1); r <= Math.min(ROWS - 1, row + 1); r++) for (int c = Math.max(0, col - 1); c <= Math.min(COLS - 1, col + 1); c++) {
                if (bricks[r][c] != 0 && bricks[r][c] != BEDROCK) { bricks[r][c] = 0; breakBrick(r); }
            }
            message = tr("computer.antos.nether_breakout.tnt");
        } else if (dropType == 2) {
            widePaddle = true;
            paddleTicks = 600;
            clampPaddle();
            message = tr("computer.antos.nether_breakout.slime");
        } else {
            if (lives < MAX_LIVES) lives++;
            else score += 50;
            message = tr("computer.antos.nether_breakout.extra_life");
        }
        messageTicks = 50;
        if (soundEnabled) AntOSUiSounds.play(AntOSUiSounds.Cue.BREAKOUT_POWERUP);
        checkCleared();
    }

    private void drawDrop(ThemedGraphics g, int x, int y) {
        int cx = x + (int) dropX;
        int cy = y + (int) dropY;
        g.fill(cx - 10, cy - 10, cx + 10, cy + 10, Ui.PANEL);
        g.renderOutline(cx - 10, cy - 10, 20, 20, Ui.GAME_OUTLINE);
        g.renderItem(dropType == 1 ? TNT : dropType == 2 ? SLIME : EXTRA_LIFE, cx - 8, cy - 8);
    }

    private void restBall() {
        ballX = paddleX + paddleWidth() / 2.0;
        ballY = paddleTop() - BALL_RADIUS;
    }

    private void clampPaddle() {
        paddleX = Math.max(5, Math.min(width - paddleWidth() - 5, paddleX));
    }

    private static int brickColor(int hp) {
        return switch (hp) {
            case 1 -> Ui.mix(Ui.ERROR, Ui.PANEL, 0.35F);
            case 2 -> Ui.mix(Ui.ACCENT, Ui.ERROR, 0.45F);
            case 3 -> Ui.mix(Ui.TEXT_BRIGHT, Ui.PANEL, 0.25F);
            case 4 -> Ui.mix(Ui.GAME_OUTLINE, Ui.TEXT_STRONG, 0.35F);
            default -> Ui.mix(Ui.BACKGROUND, Ui.TEXT_MUTED, 0.30F);
        };
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static String fit(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        if (width <= font.width("...")) return "";
        return font.plainSubstrByWidth(text, width - font.width("...")) + "...";
    }

    private double baseSpeed() { return Math.min(5.5, 3.9 + (wave - 1) * 0.35); }
    private double maxSpeed() { return Math.min(7.5, 5.6 + (wave - 1) * 0.4); }
    private int paddleTop() { return height - 26; }
    private int rowHeight() { return Math.max(10, Math.min(18, (height - 90) / ROWS)); }
    private int brickWidth() { return Math.max(10, (width - 16 - (COLS - 1) * 2) / COLS); }
    private int brickX(int c) { return Math.max(7, (width - (COLS * (brickWidth() + 2) - 2)) / 2) + c * (brickWidth() + 2); }
    private int brickY(int r) { return BRICK_TOP + r * rowHeight(); }
    private int paddleWidth() { return widePaddle ? 58 : 38; }
    private int remainingBricks() {
        int remaining = 0;
        for (int[] row : bricks) for (int brick : row) if (brick > 0 && brick != BEDROCK) remaining++;
        return remaining;
    }
}
