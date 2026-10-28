package com.craisinlord.antos.content.client.game;

import com.craisinlord.antos.api.client.game.ComputerGame;
import com.craisinlord.antos.content.client.game.logic.RestartGate;
import com.craisinlord.antos.content.client.game.logic.pistonpush.PistonPushBoard;
import com.craisinlord.antos.content.client.game.logic.pistonpush.PistonPushGame;
import com.craisinlord.antos.content.client.game.logic.pistonpush.PistonPushLevels;
import com.craisinlord.antos.content.client.game.logic.pistonpush.PistonPushProgress;
import com.craisinlord.antos.content.client.game.logic.pistonpush.PistonPushRules;
import com.craisinlord.antos.content.client.palette.Ui;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PistonPushSession implements ComputerGame.Session {
    private static final String KEY = "computer.antos.piston_push.";
    private static final String REWARD = "piston_push";
    private static final String REWARD_PAR = "piston_push_par";
    private static final int SIDE_WIDTH = 104;
    private static final int SIDE_GAP = 8;
    private static final int PAD = 8;
    private static final int LIST_TOP = 29;
    private static final long STEP_MILLIS = 90L;
    private static final long SLIDE_MILLIS = 35L;
    private static final String[] TIPS = {"basics", "slime", "ice", "honey"};

    private final GameButtons buttons = new GameButtons();
    private final GameRewardTracker rewards = new GameRewardTracker();
    private final RestartGate gate = new RestartGate();
    private int world = -1;
    private PistonPushLevels.Level level;
    private PistonPushGame game;
    private PistonPushRules.MoveResult animation;
    private long animationStart;
    private long animationLength;
    private boolean cleared;
    private boolean clearedAtPar;
    private boolean sound = true;
    private int tipWorld = -1;
    private boolean tipFirst;
    private int fieldTop;
    private int fieldWidth;
    private int fieldHeight;

    public PistonPushSession() {
        progress();
    }

    @Override
    public void tick() {
        rewards.tick();
        if (cleared) gate.tick();
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int width, int height) {
        render(graphics, font, x, y, width, height, -1, -1);
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int width, int height, int mouseX, int mouseY) {
        buttons.begin(x, y);
        int right = x + width - 1;
        graphics.fill(x, y, right, y + height, Ui.GAME_FILL);
        graphics.renderOutline(x, y, width - 1, height - 1, Ui.GAME_OUTLINE);
        if (game != null) renderLevel(graphics, font, x, y, width, height, mouseX, mouseY);
        else if (world >= 0) renderLevels(graphics, font, x, y, width, height, mouseX, mouseY);
        else renderWorlds(graphics, font, x, y, width, height, mouseX, mouseY);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 400.0F);
        rewards.render(graphics, font, x, y, width, height);
        graphics.pose().popPose();
    }

    private void header(GuiGraphics graphics, Font font, int x, int y, int width, String stat, String detail) {
        int right = x + width - 1;
        String sfx = tr(sound ? "sfx_on" : "sfx_off");
        graphics.drawString(font, GameOverlay.fit(font, tr("title"), width - font.width(stat) - 21), x + 7, y + 4, Ui.TEXT_STRONG, false);
        graphics.drawString(font, stat, right - font.width(stat) - 7, y + 4, Ui.TEXT, false);
        graphics.drawString(font, GameOverlay.fit(font, detail, width - font.width(sfx) - 21), x + 7, y + 15, Ui.TEXT_MUTED, false);
        graphics.drawString(font, sfx, right - font.width(sfx) - 7, y + 15, sound ? Ui.TEXT_MUTED : Ui.ERROR, false);
        graphics.fill(x + 7, y + 23, right - 7, y + 24, Ui.LINE);
    }

    private static void footer(GuiGraphics graphics, Font font, int x, int y, int width, int height, String text, int color) {
        graphics.drawCenteredString(font, GameOverlay.fit(font, text, width - 14), x + width / 2, y + height - 11, color);
    }

    private void renderWorlds(GuiGraphics graphics, Font font, int x, int y, int width, int height, int mouseX, int mouseY) {
        Map<String, Integer> progress = progress();
        int total = PistonPushLevels.all().size();
        int solved = 0;
        int par = 0;
        for (PistonPushLevels.Level option : PistonPushLevels.all()) {
            if (PistonPushProgress.solved(progress, option.id())) solved++;
            if (PistonPushProgress.atPar(progress, option)) par++;
        }
        header(graphics, font, x, y, width, tr("cleared_total", solved, total), tr("par_total", par, total));
        int top = y + LIST_TOP;
        for (int index = 0; index < PistonPushLevels.WORLDS; index++) {
            int option = index;
            boolean open = PistonPushProgress.unlocked(progress, option);
            int clears = PistonPushProgress.clears(progress, option);
            int atPar = 0;
            for (PistonPushLevels.Level entry : PistonPushLevels.world(option)) if (PistonPushProgress.atPar(progress, entry)) atPar++;
            Component detail = open
                    ? Component.translatable(KEY + "world_detail", clears, PistonPushLevels.PER_WORLD, atPar)
                    : Component.translatable(KEY + "world_locked", Math.max(0, PistonPushProgress.UNLOCK_CLEARS - PistonPushProgress.clears(progress, option - 1)), option);
            Component badge = open ? GameRewardTracker.dailyBonus(REWARD) : Component.translatable(KEY + "locked");
            buttons.row(graphics, font, x + 7, top, width - 14, worldTitle(option), detail, badge, mouseX, mouseY, () -> openWorld(option));
            top += GameButtons.ROW_HEIGHT + GameButtons.ROW_GAP;
        }
        footer(graphics, font, x, y, width, height, tr("pick_world"), Ui.TEXT);
    }

    private void renderLevels(GuiGraphics graphics, Font font, int x, int y, int width, int height, int mouseX, int mouseY) {
        Map<String, Integer> progress = progress();
        List<PistonPushLevels.Level> levels = PistonPushLevels.world(world);
        header(graphics, font, x, y, width, tr("cleared_world", PistonPushProgress.clears(progress, world), levels.size()), worldTitle(world).getString());
        int gap = GameButtons.ROW_GAP;
        int columnWidth = (width - 14 - gap) / 2;
        for (int index = 0; index < levels.size(); index++) {
            PistonPushLevels.Level option = levels.get(index);
            int rowX = x + 7 + (index % 2) * (columnWidth + gap);
            int rowY = y + LIST_TOP + (index / 2) * (GameButtons.ROW_HEIGHT + gap);
            Integer best = progress.get(option.id());
            Component title = Component.translatable(KEY + "level_row", option.number(), levelName(option));
            Component detail = best == null
                    ? Component.translatable(KEY + "level_detail_new", option.par())
                    : Component.translatable(KEY + "level_detail", option.par(), best);
            buttons.row(graphics, font, rowX, rowY, columnWidth, title, detail, levelBadge(progress, option), mouseX, mouseY, () -> start(option));
        }
        int backY = y + LIST_TOP + ((levels.size() + 1) / 2) * (GameButtons.ROW_HEIGHT + gap);
        buttons.button(graphics, font, x + 7, backY, Math.min(90, width - 14), Component.translatable(KEY + "back"), mouseX, mouseY, this::openWorlds);
        footer(graphics, font, x, y, width, height, tr("pick_level"), Ui.TEXT);
    }

    private Component levelBadge(Map<String, Integer> progress, PistonPushLevels.Level option) {
        if (PistonPushProgress.atPar(progress, option)) return Component.translatable(KEY + "badge_par");
        if (PistonPushProgress.solved(progress, option.id())) {
            Component parBonus = option.world() >= 1 ? GameRewardTracker.dailyBonus(REWARD_PAR) : Component.empty();
            return parBonus.getString().isEmpty() ? Component.translatable(KEY + "badge_clear") : parBonus;
        }
        return GameRewardTracker.dailyBonus(REWARD);
    }

    private void renderLevel(GuiGraphics graphics, Font font, int x, int y, int width, int height, int mouseX, int mouseY) {
        PistonPushBoard board = game.board();
        int right = x + width - 1;
        Integer best = progress().get(level.id());
        String bestText = best == null ? tr("none") : Integer.toString(best);
        int top = y + 25;
        int bottom = y + height - 15;
        boolean side = width - 14 - SIDE_WIDTH - SIDE_GAP >= 120;
        String detail = hudLabel(level);
        if (!side) detail += "  " + tr("par_stat", level.par()) + "  " + tr("best_stat", bestText);
        header(graphics, font, x, y, width, tr("moves_stat", game.moves()), detail);
        int fieldLeft = x + 7;
        fieldTop = top;
        fieldWidth = width - 14 - (side ? SIDE_WIDTH + SIDE_GAP : 0);
        fieldHeight = Math.max(20, bottom - top);
        int tile = PistonPushRenderer.tileSize(board, fieldWidth - 6, fieldHeight - 6);
        int boardWidth = board.width() * tile;
        int boardHeight = board.height() * tile;
        int left = fieldLeft + Math.max(3, (fieldWidth - boardWidth) / 2);
        int boardTop = fieldTop + Math.max(3, (fieldHeight - boardHeight) / 2);
        float progress = 1.0F;
        if (animation != null) {
            progress = (System.currentTimeMillis() - animationStart) / (float) Math.max(1L, animationLength);
            if (progress >= 1.0F) animation = null;
        }
        scissor(graphics, fieldLeft, fieldTop, fieldLeft + fieldWidth, fieldTop + fieldHeight);
        PistonPushRenderer.render(graphics, board, left, boardTop, tile, animation, progress);
        graphics.disableScissor();
        if (side) renderSide(graphics, font, right - 7 - SIDE_WIDTH, fieldTop + 2, SIDE_WIDTH, fieldHeight - 4, bestText, best, mouseX, mouseY);
        footer(graphics, font, x, y, width, height, tr("hint"), Ui.TEXT_MUTED);
        if (tipWorld >= 0) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 400.0F);
            GameTip.render(graphics, font, fieldLeft, fieldTop, fieldWidth, fieldHeight, Component.translatable(KEY + "tip." + TIPS[tipWorld] + ".title"),
                    Component.translatable(KEY + "tip." + TIPS[tipWorld] + ".body"), Component.translatable(KEY + "tip.hint"));
            graphics.pose().popPose();
        } else if (cleared && animation == null) {
            String title = tr(clearedAtPar ? "cleared_par" : "cleared", game.moves());
            Component hint = gate.ready() ? Component.translatable(KEY + (nextPlayable() == null ? "cleared_last" : "cleared_next")) : null;
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 400.0F);
            GameOverlay.render(graphics, font, fieldLeft, fieldTop, fieldWidth, fieldHeight, Component.literal(title), hint, Ui.ACCENT);
            graphics.pose().popPose();
        }
    }

    private void renderSide(GuiGraphics graphics, Font font, int x, int y, int width, int height, String bestText, Integer best, int mouseX, int mouseY) {
        GameButtons.panel(graphics, x, y, width, height, false);
        int inner = width - PAD * 2;
        stat(graphics, font, x, y + PAD, width, tr("moves"), Integer.toString(game.moves()), Ui.TEXT_STRONG);
        stat(graphics, font, x, y + PAD + 10, width, tr("par"), Integer.toString(level.par()), Ui.TEXT);
        stat(graphics, font, x, y + PAD + 20, width, tr("best"), bestText, best != null && best <= level.par() ? Ui.ACCENT : Ui.TEXT);
        int step = GameButtons.BUTTON_HEIGHT + 4;
        int buttonsTop = Math.max(y + PAD + 34, y + height - PAD - step * 4 + 4);
        buttons.button(graphics, font, x + PAD, buttonsTop, inner, Component.translatable(KEY + "undo"), mouseX, mouseY, this::undo);
        buttons.button(graphics, font, x + PAD, buttonsTop + step, inner, Component.translatable(KEY + "restart"), mouseX, mouseY, this::restart);
        buttons.button(graphics, font, x + PAD, buttonsTop + step * 2, inner, Component.translatable(KEY + "levels"), mouseX, mouseY, this::openLevels);
        buttons.button(graphics, font, x + PAD, buttonsTop + step * 3, inner, Component.translatable(KEY + "help"), mouseX, mouseY, this::showTip);
    }

    private static void scissor(GuiGraphics graphics, int left, int top, int right, int bottom) {
        Matrix4f matrix = graphics.pose().last().pose();
        Vector3f start = matrix.transformPosition(left, top, 0.0F, new Vector3f());
        Vector3f end = matrix.transformPosition(right, bottom, 0.0F, new Vector3f());
        graphics.enableScissor((int) Math.floor(Math.min(start.x, end.x)), (int) Math.floor(Math.min(start.y, end.y)),
                (int) Math.ceil(Math.max(start.x, end.x)), (int) Math.ceil(Math.max(start.y, end.y)));
    }

    private static void stat(GuiGraphics graphics, Font font, int x, int y, int width, String label, String value, int color) {
        graphics.drawString(font, label, x + PAD, y, Ui.TEXT_MUTED, false);
        graphics.drawString(font, value, x + width - PAD - font.width(value), y, color, false);
    }

    private static Component worldTitle(int world) {
        return Component.translatable(KEY + "world_label", world + 1, Component.translatable(KEY + "world." + world));
    }

    private static Component levelName(PistonPushLevels.Level level) {
        return Component.translatableWithFallback(KEY + "level_name." + level.id(), level.name());
    }

    private static String hudLabel(PistonPushLevels.Level level) {
        return tr("level", level.world() + 1, level.number(), levelName(level).getString().toUpperCase(Locale.ROOT));
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(KEY + key, args).getString();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (game != null && tipWorld >= 0) {
            dismissTip();
            return true;
        }
        if (buttons.click(mouseX, mouseY)) return true;
        if (game != null && cleared && animation == null && gate.ready()) {
            if (mouseX >= 7 && mouseX < 7 + fieldWidth && mouseY >= 25 && mouseY < 25 + fieldHeight) {
                advance();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_M) {
            sound = !sound;
            return true;
        }
        boolean confirm = keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER;
        if (game == null && world < 0) {
            if (confirm) {
                start(resumeLevel());
                return true;
            }
            int pick = keyCode - GLFW.GLFW_KEY_1;
            if (pick >= 0 && pick < PistonPushLevels.WORLDS) {
                openWorld(pick);
                return true;
            }
            return false;
        }
        if (game == null) {
            int pick = keyCode - GLFW.GLFW_KEY_1;
            List<PistonPushLevels.Level> levels = PistonPushLevels.world(world);
            if (pick >= 0 && pick < levels.size()) {
                start(levels.get(pick));
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_L || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                openWorlds();
                return true;
            }
            return false;
        }
        if (tipWorld >= 0) {
            if (confirm) dismissTip();
            return confirm;
        }
        if (keyCode == GLFW.GLFW_KEY_H) {
            showTip();
            return true;
        }
        if (confirm) {
            if (!cleared || animation != null || !gate.ready()) return cleared;
            advance();
            return true;
        }
        int direction = switch (keyCode) {
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> PistonPushRules.UP;
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> PistonPushRules.RIGHT;
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> PistonPushRules.DOWN;
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> PistonPushRules.LEFT;
            default -> -1;
        };
        if (direction >= 0) {
            move(direction);
            return true;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_U, GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_BACKSPACE -> undo();
            case GLFW.GLFW_KEY_R -> restart();
            case GLFW.GLFW_KEY_L -> openLevels();
            default -> {
                return false;
            }
        }
        return true;
    }

    private void move(int direction) {
        if (cleared) return;
        animation = null;
        PistonPushRules.MoveResult result = game.move(direction);
        if (!result.moved()) return;
        animation = result;
        animationStart = System.currentTimeMillis();
        animationLength = STEP_MILLIS + SLIDE_MILLIS * result.slide();
        if (result.pushed()) {
            sound(SoundEvents.PISTON_EXTEND, 1.0F, 0.35F);
            if (result.slime()) sound(SoundEvents.SLIME_BLOCK_PLACE, 1.0F, 0.5F);
            if (result.honey()) sound(SoundEvents.HONEY_BLOCK_SLIDE, 1.0F, 0.6F);
            if (result.slide() > 0) sound(SoundEvents.GLASS_STEP, 1.2F, 0.5F);
        } else {
            sound(SoundEvents.PISTON_EXTEND, 1.8F, 0.08F);
        }
        if (result.platesCovered() > 0) sound(SoundEvents.STONE_PRESSURE_PLATE_CLICK_ON, 1.0F, 0.6F);
        if (result.solved()) finish();
    }

    private void finish() {
        cleared = true;
        gate.reset();
        int moves = game.moves();
        clearedAtPar = moves <= level.par();
        Map<String, Integer> progress = progress();
        boolean firstClear = !PistonPushProgress.solved(progress, level.id());
        if (PistonPushProgress.record(progress, level.id(), moves)) GameData.put(PistonPushProgress.KEY, PistonPushProgress.encode(progress));
        if (firstClear) rewards.claimOnce(REWARD);
        if (clearedAtPar && level.world() >= 1) rewards.claimOnce(REWARD_PAR);
        sound(SoundEvents.PLAYER_LEVELUP, 1.0F, 0.6F);
    }

    private void advance() {
        PistonPushLevels.Level next = nextPlayable();
        if (next == null) openWorlds();
        else start(next);
    }

    private void undo() {
        if (game == null || !game.undo()) return;
        animation = null;
        cleared = false;
        clearedAtPar = false;
        sound(SoundEvents.PISTON_CONTRACT, 1.0F, 0.35F);
    }

    private void restart() {
        if (game == null) return;
        game.restart();
        animation = null;
        cleared = false;
        clearedAtPar = false;
    }

    private void openWorlds() {
        closeLevel();
        world = -1;
    }

    private void openWorld(int option) {
        if (!PistonPushProgress.unlocked(progress(), option)) return;
        closeLevel();
        world = option;
    }

    private void openLevels() {
        int current = level == null ? -1 : level.world();
        closeLevel();
        world = current;
    }

    private void closeLevel() {
        tipWorld = -1;
        tipFirst = false;
        game = null;
        level = null;
        animation = null;
        cleared = false;
        clearedAtPar = false;
    }

    private void start(PistonPushLevels.Level next) {
        level = next;
        world = next.world();
        game = new PistonPushGame(next.rows());
        animation = null;
        cleared = false;
        clearedAtPar = false;
        tipWorld = -1;
        tipFirst = false;
        if (next.world() >= 1 && !GameTip.seen(tipKey(next.world()))) {
            tipWorld = next.world();
            tipFirst = true;
        }
    }

    private void showTip() {
        if (level == null) return;
        tipWorld = level.world();
        tipFirst = false;
    }

    private void dismissTip() {
        if (tipFirst && tipWorld >= 1) GameTip.markSeen(tipKey(tipWorld));
        tipWorld = -1;
        tipFirst = false;
    }

    private static String tipKey(int world) {
        return "piston_push.tip." + TIPS[Math.max(0, Math.min(TIPS.length - 1, world))];
    }

    private void sound(SoundEvent event, float pitch, float volume) {
        if (sound) GameSounds.play(event, pitch, volume);
    }

    private PistonPushLevels.Level nextPlayable() {
        PistonPushLevels.Level next = PistonPushLevels.next(level);
        if (next == null || !PistonPushProgress.unlocked(progress(), next.world())) return null;
        return next;
    }

    private PistonPushLevels.Level resumeLevel() {
        Map<String, Integer> progress = progress();
        for (PistonPushLevels.Level option : PistonPushLevels.all()) {
            if (PistonPushProgress.unlocked(progress, option.world()) && !PistonPushProgress.solved(progress, option.id())) return option;
        }
        return PistonPushLevels.all().get(0);
    }

    private static Map<String, Integer> progress() {
        return PistonPushProgress.decode(GameData.get(PistonPushProgress.KEY, ""));
    }
}
