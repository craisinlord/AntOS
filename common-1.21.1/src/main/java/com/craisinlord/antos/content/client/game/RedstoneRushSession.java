package com.craisinlord.antos.content.client.game;

import com.craisinlord.antos.api.client.game.ComputerGame;
import com.craisinlord.antos.content.client.game.logic.RestartGate;
import com.craisinlord.antos.content.client.game.logic.redstonerush.RedstoneRushLogic;
import com.craisinlord.antos.content.client.game.logic.redstonerush.Piece;
import com.craisinlord.antos.content.client.game.logic.redstonerush.Tile;
import com.craisinlord.antos.content.client.palette.Ui;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.Random;

public final class RedstoneRushSession implements ComputerGame.Session {
    private static final String GAME_ID = "redstone_rush";
    private static final String TUTORIAL_KEY = "redstone_rush.tutorial2";
    private static final int PANEL_MIN = 122;
    private static final int PANEL_MAX = 160;
    private static final int PANEL_GAP = 8;
    private static final int PAD = 6;
    private static final int PITCH = 10;
    private static final int TICK_MS = 50;
    private static final int MESSAGE_TICKS = 40;

    private final GameButtons buttons = new GameButtons();
    private final GameRewardTracker rewards = new GameRewardTracker();
    private final RestartGate gate = new RestartGate();
    private final Random seeds = new Random();
    private final RedstoneRushTutorial tutorial = new RedstoneRushTutorial();
    private RedstoneRushLogic logic;
    private RedstoneRushLogic.State lastState;
    private boolean tutorialOpen;
    private boolean tutorialAuto;
    private boolean tutorialTouched;
    private boolean paused;
    private boolean muted;
    private boolean keyboard;
    private int cursorX = RedstoneRushLogic.COLUMNS / 2;
    private int cursorY = RedstoneRushLogic.ROWS / 2;
    private int cell = 16;
    private int boardLeft = 9;
    private int boardTop = 27;
    private int panelLeft = -1;
    private int panelTop = -1;
    private int panelRight = -1;
    private int panelBottom = -1;
    private int bestLocal;
    private int ticks;
    private int messageTicks;
    private Component message = Component.empty();

    public RedstoneRushSession() {
        logic = new RedstoneRushLogic(seeds.nextLong());
        lastState = logic.state();
        if (!"1".equals(GameData.get(TUTORIAL_KEY, ""))) {
            tutorialAuto = true;
            openTutorial();
        }
    }

    @Override
    public void tick() {
        rewards.tick();
        ticks++;
        if (tutorialOpen) {
            if (tutorialAuto && !tutorialTouched && "1".equals(GameData.get(TUTORIAL_KEY, ""))) tutorialOpen = false;
            else tutorial.tick();
            return;
        }
        if (paused) return;
        if (messageTicks > 0) messageTicks--;
        logic.advance(TICK_MS);
        events();
        RedstoneRushLogic.State state = logic.state();
        if (state != lastState) {
            if (over()) gate.reset();
            if (state == RedstoneRushLogic.State.WON && logic.level() >= RedstoneRushLogic.REWARD_LEVEL) rewards.claimOnce(GAME_ID);
            if (state == RedstoneRushLogic.State.LOST) submit();
            lastState = state;
        }
        if (over()) gate.tick();
        bestLocal = Math.max(bestLocal, logic.score());
    }

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int width, int height) {
        render(g, font, x, y, width, height, -1, -1);
    }

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int width, int height, int mouseX, int mouseY) {
        buttons.begin(x, y);
        RedstoneRushRenderer.frame(g, x, y, width, height);
        if (tutorialOpen) {
            tutorial.render(g, font, x, y, width, height, mouseX, mouseY, buttons, this::closeTutorial, sfxText(), sfxColor());
            overlayLayer(g, () -> rewards.render(g, font, x, y, width, height));
            return;
        }
        int best = (int) Math.max(Math.max(bestLocal, logic.score()), GameData.best(GAME_ID));
        RedstoneRushRenderer.header(g, font, x, y, width, tr("title").getString(), tr("score", logic.score()).getString(),
                tr("stats", logic.level(), best).getString(), sfxText(), sfxColor());
        int columns = RedstoneRushLogic.COLUMNS;
        int rows = RedstoneRushLogic.ROWS;
        int fieldTop = y + 25;
        int fieldHeight = height - 40;
        cell = Math.max(10, Math.min(28, Math.min((fieldHeight - 6) / rows, (width - 14 - 3 - PANEL_GAP - PANEL_MIN) / columns)));
        int boardWidth = RedstoneRushRenderer.boardWidth(columns, cell);
        int boardHeight = RedstoneRushRenderer.boardHeight(rows, cell);
        int panelWidth = Math.min(PANEL_MAX, width - 14 - boardWidth - PANEL_GAP);
        boolean panel = panelWidth >= 60;
        int groupWidth = boardWidth + (panel ? PANEL_GAP + panelWidth : 0);
        int frameX = x + (width - groupWidth) / 2;
        int frameY = fieldTop + Math.max(1, (fieldHeight - boardHeight) / 2);
        boardLeft = frameX + 2 - x;
        boardTop = frameY + 2 - y;
        int hoverX = keyboard ? cursorX : Math.floorDiv(mouseX - boardLeft, cell);
        int hoverY = keyboard ? cursorY : Math.floorDiv(mouseY - boardTop, cell);
        Tile ghost = paused || over() ? null : logic.selectedTile();
        RedstoneRushRenderer.board(g, logic, frameX + 2, frameY + 2, cell, hoverX, hoverY, keyboard, ghost);
        if (panel) {
            panelLeft = frameX + boardWidth + PANEL_GAP - x;
            panelTop = frameY - y;
            panelRight = panelLeft + panelWidth;
            panelBottom = panelTop + boardHeight;
        } else panelLeft = panelTop = panelRight = panelBottom = -1;
        if (panel) renderPanel(g, font, frameX + boardWidth + PANEL_GAP, frameY, panelWidth, boardHeight, mouseX, mouseY);
        Component footer;
        int footerColor;
        if (messageTicks > 0) {
            footer = message;
            footerColor = Ui.ACCENT;
        } else if (logic.state() == RedstoneRushLogic.State.COUNTDOWN) {
            footer = tr("hint");
            footerColor = Ui.TEXT;
        } else {
            footer = tr("controls");
            footerColor = Ui.TEXT_MUTED;
        }
        RedstoneRushRenderer.footer(g, font, x, y, width, height, footer.getString(), footerColor);
        overlayLayer(g, () -> {
            if (paused) {
                GameOverlay.render(g, font, frameX, frameY, boardWidth, boardHeight, tr("paused"), tr("resume"), Ui.ACCENT);
            } else if (logic.state() == RedstoneRushLogic.State.WON) {
                GameOverlay.render(g, font, frameX, frameY, boardWidth, boardHeight, tr("won", logic.level()), tr("next_level"), Ui.ACCENT);
            } else if (logic.state() == RedstoneRushLogic.State.LOST) {
                GameOverlay.render(g, font, frameX, frameY, boardWidth, boardHeight, tr(logic.loss() == RedstoneRushLogic.Loss.FADED ? "faded" : "leak"), tr("retry"), Ui.ERROR);
            }
            rewards.render(g, font, x, y, width, height);
        });
    }

    private static void overlayLayer(GuiGraphics g, Runnable draw) {
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, RedstoneRushRenderer.OVERLAY_Z);
        draw.run();
        g.pose().popPose();
    }

    private void renderPanel(GuiGraphics g, Font font, int x, int y, int width, int height, int mouseX, int mouseY) {
        GameButtons.panel(g, x, y, width, height, false);
        int inner = width - PAD * 2;
        int row = y + PAD;
        if (logic.state() == RedstoneRushLogic.State.COUNTDOWN) {
            long left = logic.countdownLeft();
            stat(g, font, x, row, width, tr("lever"), String.format(Locale.ROOT, "%.1fs", left / 1000.0));
            float fraction = left / (float) Math.max(1L, logic.countdownTotal());
            boolean warn = fraction < 0.25F && ticks / 4 % 2 == 0;
            bar(g, x + PAD, row + PITCH, inner, fraction, warn ? Ui.REDSTONE_15 : Ui.REDSTONE_9);
        } else {
            int power = logic.state() == RedstoneRushLogic.State.FLOWING ? logic.nextPower() : logic.lampLit() ? RedstoneRushLogic.MAX_POWER : 0;
            stat(g, font, x, row, width, tr("power"), Integer.toString(power));
            bar(g, x + PAD, row + PITCH, inner, power / (float) RedstoneRushLogic.MAX_POWER, RedstoneRushRenderer.power(power));
        }
        row += PITCH * 2;
        Piece selected = logic.selected();
        stat(g, font, x, row, width, tr("piece." + selected.name().toLowerCase(Locale.ROOT)), tr("orient." + logic.selectedTile().name().toLowerCase(Locale.ROOT)).getString());
        row += PITCH + 1;
        Piece[] pieces = Piece.values();
        int slotGap = 4;
        int slot = Math.max(12, Math.min(26, (inner - 2 - slotGap * (pieces.length - 1)) / pieces.length));
        int slotsWidth = pieces.length * slot + (pieces.length - 1) * slotGap;
        int sx = x + (width - slotsWidth) / 2;
        for (int i = 0; i < pieces.length; i++) {
            Piece piece = pieces[i];
            boolean locked = !logic.unlocked(piece);
            boolean hover = buttons.over(sx, row, slot, slot + 10, mouseX, mouseY);
            RedstoneRushRenderer.slot(g, logic.tileFor(piece), sx, row, slot, piece == selected, locked, hover);
            String label = locked ? tr("locked_level", RedstoneRushLogic.REPEATER_LEVEL).getString() : Integer.toString(i + 1);
            g.drawCenteredString(font, label, sx + slot / 2, row + slot + 2, locked ? Ui.TEXT_DISABLED : piece == selected ? Ui.TEXT_STRONG : Ui.TEXT_MUTED);
            buttons.hitOnly(sx - 1, row - 1, slot + 2, slot + 12, () -> pick(piece));
            sx += slot + slotGap;
        }
        row += slot + 14;
        int buttonsTop = y + height - PAD - GameButtons.BUTTON_HEIGHT;
        int multiplier = logic.fast() ? 2 : 1;
        if (row + PITCH <= buttonsTop - 2) stat(g, font, x, row, width, tr("tile_label"), "+" + RedstoneRushLogic.TILE_POINTS * multiplier);
        if (row + PITCH * 2 <= buttonsTop - 2) stat(g, font, x, row + PITCH, width, tr("lamp_label"), "+" + RedstoneRushLogic.LAMP_POINTS * logic.level());
        int helpWidth = GameButtons.BUTTON_HEIGHT;
        buttons.button(g, font, x + PAD, buttonsTop, inner - helpWidth - 4, logic.fast() ? tr("flowing") : tr("flow"), mouseX, mouseY, this::fastForward);
        buttons.button(g, font, x + width - PAD - helpWidth, buttonsTop, helpWidth, tr("help"), mouseX, mouseY, this::openTutorialManually);
    }

    private static void stat(GuiGraphics g, Font font, int x, int y, int width, Component label, String value) {
        g.drawString(font, label, x + PAD, y, Ui.TEXT_MUTED, false);
        g.drawString(font, value, x + width - PAD - font.width(value), y, Ui.TEXT, false);
    }

    private static void bar(GuiGraphics g, int x, int y, int width, float fraction, int color) {
        g.fill(x, y, x + width, y + 5, Ui.alpha(Ui.BACKGROUND, 0xC0));
        g.renderOutline(x, y, width, 5, Ui.GAME_OUTLINE);
        int filled = Math.round((width - 2) * Math.max(0.0F, Math.min(1.0F, fraction)));
        if (filled > 0) g.fill(x + 1, y + 1, x + 1 + filled, y + 4, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tutorialOpen) tutorialTouched = true;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && buttons.click(mouseX, mouseY)) return true;
        if (tutorialOpen) return true;
        int column = (int) Math.floor((mouseX - boardLeft) / cell);
        int row = (int) Math.floor((mouseY - boardTop) / cell);
        boolean onBoard = column >= 0 && row >= 0 && column < RedstoneRushLogic.COLUMNS && row < RedstoneRushLogic.ROWS;
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            boolean onPanel = mouseX >= panelLeft && mouseX < panelRight && mouseY >= panelTop && mouseY < panelBottom;
            if (!onBoard && !onPanel) return false;
            rotate(1);
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (paused) {
            paused = false;
            return true;
        }
        if (over()) {
            proceed();
            return true;
        }
        if (!onBoard) return false;
        keyboard = false;
        cursorX = column;
        cursorY = row;
        place(column, row);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode) {
        if (tutorialOpen) {
            tutorialTouched = true;
            switch (tutorial.key(keyCode)) {
                case NEXT -> tutorial.next();
                case BACK -> tutorial.back();
                case CLOSE -> closeTutorial();
                case NONE -> { return keyCode == GLFW.GLFW_KEY_M && toggleMute(); }
            }
            return true;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_H -> { openTutorialManually(); return true; }
            case GLFW.GLFW_KEY_M -> { return toggleMute(); }
            case GLFW.GLFW_KEY_R -> {
                if (!over() || gate.ready()) restart();
                return true;
            }
            case GLFW.GLFW_KEY_P -> {
                if (!over()) paused = !paused;
                return true;
            }
            case GLFW.GLFW_KEY_Q, GLFW.GLFW_KEY_E -> {
                rotate(keyCode == GLFW.GLFW_KEY_E ? 1 : -1);
                return true;
            }
            case GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_4 -> {
                pick(Piece.values()[keyCode - GLFW.GLFW_KEY_1]);
                return true;
            }
            default -> { }
        }
        boolean confirm = keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE;
        if (confirm || keyCode == GLFW.GLFW_KEY_F) {
            if (paused) {
                if (confirm) paused = false;
            } else if (over()) {
                if (confirm) proceed();
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                keyboard = true;
                place(cursorX, cursorY);
            } else fastForward();
            return true;
        }
        int dx = keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_A ? -1 : keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_D ? 1 : 0;
        int dy = keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_W ? -1 : keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_S ? 1 : 0;
        if (dx != 0 || dy != 0) {
            if (keyboard) {
                cursorX = Math.max(0, Math.min(RedstoneRushLogic.COLUMNS - 1, cursorX + dx));
                cursorY = Math.max(0, Math.min(RedstoneRushLogic.ROWS - 1, cursorY + dy));
            }
            keyboard = true;
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        submit();
    }

    private boolean over() {
        return logic.state() == RedstoneRushLogic.State.WON || logic.state() == RedstoneRushLogic.State.LOST;
    }

    private void place(int column, int row) {
        if (paused) return;
        if (logic.place(column, row) == RedstoneRushLogic.PlaceResult.LOCKED && logic.powered(column, row)) show(tr("locked"));
        events();
    }

    private void pick(Piece piece) {
        if (tutorialOpen) return;
        if (!logic.select(piece)) {
            show(tr("repeater_locked", RedstoneRushLogic.REPEATER_LEVEL));
            return;
        }
        sound(SoundEvents.LEVER_CLICK, 1.4F, 0.2F);
    }

    private void rotate(int delta) {
        if (paused || over()) return;
        logic.rotate(delta);
        if (logic.selected().orientations() > 1) sound(SoundEvents.LEVER_CLICK, 1.7F, 0.2F);
    }

    private void fastForward() {
        if (paused || tutorialOpen || over()) return;
        logic.fastForward();
    }

    private void proceed() {
        if (!gate.ready()) return;
        if (logic.state() == RedstoneRushLogic.State.WON) {
            logic.nextLevel();
            show(logic.level() == RedstoneRushLogic.REPEATER_LEVEL ? tr("repeaters", logic.level()) : tr("level_start", logic.level()));
        } else restart();
        lastState = logic.state();
    }

    private void restart() {
        submit();
        logic.restart(seeds.nextLong());
        lastState = logic.state();
        paused = false;
        messageTicks = 0;
        gate.reset();
    }

    private void submit() {
        bestLocal = Math.max(bestLocal, logic.score());
        GameData.submitBest(GAME_ID, logic.score());
    }

    private void show(Component text) {
        message = text;
        messageTicks = MESSAGE_TICKS;
    }

    private boolean toggleMute() {
        muted = !muted;
        if (!muted) GameSounds.play(SoundEvents.LEVER_CLICK, 1.0F, 0.3F);
        return true;
    }

    private String sfxText() {
        return tr(muted ? "sfx_off" : "sfx_on").getString();
    }

    private int sfxColor() {
        return muted ? Ui.ERROR : Ui.TEXT_MUTED;
    }

    private void openTutorial() {
        tutorial.open();
        tutorialOpen = true;
    }

    private void openTutorialManually() {
        tutorialTouched = true;
        if (!over()) paused = true;
        openTutorial();
    }

    private void closeTutorial() {
        tutorialOpen = false;
        tutorialTouched = true;
        GameData.put(TUTORIAL_KEY, "1");
    }

    private void events() {
        for (RedstoneRushLogic.Event event : logic.drainEvents()) {
            switch (event.kind()) {
                case PLACE -> sound(SoundEvents.STONE_PLACE, 1.0F, 0.45F);
                case REPLACE -> {
                    sound(SoundEvents.GRAVEL_BREAK, 1.0F, 0.5F);
                    show(tr("replaced", RedstoneRushLogic.REPLACE_PENALTY));
                }
                case POWER -> sound(SoundEvents.LEVER_CLICK, 0.6F + event.power() * 0.03F, 0.3F);
                case REPEATER -> sound(SoundEvents.COMPARATOR_CLICK, 1.0F, 0.4F);
                case CROSS -> {
                    sound(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.5F, 0.35F);
                    show(tr("cross_bonus", RedstoneRushLogic.CROSS_BONUS * (logic.fast() ? 2 : 1)));
                }
                case WIN -> sound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.2F, 0.5F);
                case LEAK, FADED -> sound(SoundEvents.FIRE_EXTINGUISH, 1.0F, 0.4F);
            }
        }
    }

    private void sound(SoundEvent event, float pitch, float volume) {
        if (!muted) GameSounds.play(event, pitch, volume);
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("computer.antos.redstone_rush." + key, args);
    }
}
