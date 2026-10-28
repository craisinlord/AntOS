package com.craisinlord.antos.content.client.game;

import com.craisinlord.antos.api.client.game.ComputerGame;
import com.craisinlord.antos.content.client.game.logic.RestartGate;
import com.craisinlord.antos.content.client.game.logic.ore2048.Ore2048Board;
import com.craisinlord.antos.content.client.game.logic.ore2048.Ore2048Board.Dir;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

public final class Ore2048Session implements ComputerGame.Session {
    static final String GAME_ID = "ore_2048";
    private static final String DIAMOND_MODE = "ore_2048_diamond";
    private static final String BEACON_MODE = "ore_2048_beacon";
    private static final long EVENT_MS = 2000L;
    private static final String TNT_TIP = "ore_2048.tip.tnt";

    private final GameRewardTracker rewards = new GameRewardTracker();
    private final RestartGate restart = new RestartGate();
    private final Ore2048Renderer renderer = new Ore2048Renderer();
    private Ore2048Board board;
    private Ore2048Board.MoveResult last;
    private long movedAt;
    private long best;
    private boolean beaconOverlay;
    private boolean soundEnabled = true;
    private boolean tntTip;
    private Component event;
    private long eventUntil;

    public Ore2048Session() {
        reset();
    }

    @Override
    public void tick() {
        rewards.tick();
        if (board.over()) restart.tick();
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int width, int height) {
        render(graphics, font, x, y, width, height, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int width, int height, int mouseX, int mouseY) {
        best = Math.max(best, Math.max(board.score(), GameData.best(GAME_ID)));
        Component shownEvent = event != null && System.currentTimeMillis() < eventUntil ? event : null;
        renderer.render(graphics, font, x, y, width, height, mouseX, mouseY, board, last, movedAt, best,
                beaconOverlay, soundEnabled, shownEvent, tipVisible());
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 400.0F);
        rewards.render(graphics, font, x, y, width, height);
        graphics.pose().popPose();
    }

    @Override
    public boolean keyPressed(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_M) {
            soundEnabled = !soundEnabled;
            sound(SoundEvents.NOTE_BLOCK_HAT.value(), 1.2F, 0.25F);
            return true;
        }
        boolean confirm = keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER;
        if (board.over()) {
            if ((confirm || keyCode == GLFW.GLFW_KEY_R) && restart.ready()) reset();
            return confirm || keyCode == GLFW.GLFW_KEY_R;
        }
        if (keyCode == GLFW.GLFW_KEY_R) {
            submit();
            reset();
            return true;
        }
        if (beaconOverlay) {
            if (confirm) beaconOverlay = false;
            return confirm;
        }
        if (tntTip) {
            if (confirm) dismissTip();
            return true;
        }
        Dir dir = switch (keyCode) {
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> Dir.UP;
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> Dir.DOWN;
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> Dir.LEFT;
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> Dir.RIGHT;
            default -> null;
        };
        if (dir == null) return false;
        move(dir);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (board.over()) {
            if (restart.ready()) reset();
            return true;
        }
        if (beaconOverlay) {
            beaconOverlay = false;
            return true;
        }
        if (tntTip) {
            dismissTip();
            return true;
        }
        Dir dir = renderer.edgeAt(mouseX, mouseY);
        if (dir == null) return false;
        move(dir);
        return true;
    }

    @Override
    public void close() {
        submit();
    }

    private void move(Dir dir) {
        Ore2048Board.MoveResult result = board.move(dir);
        if (!result.moved()) return;
        last = result;
        movedAt = System.currentTimeMillis();
        best = Math.max(best, board.score());
        if (result.beaconReached()) {
            beaconOverlay = true;
            rewards.claimOnce(BEACON_MODE);
            sound(SoundEvents.PLAYER_LEVELUP, 1.0F, 0.6F);
        } else if (!result.merges().isEmpty()) {
            sound(SoundEvents.NOTE_BLOCK_PLING.value(), Math.min(2.0F, 0.6F + result.topMergeTier() * 0.12F), 0.35F);
        } else {
            sound(SoundEvents.NOTE_BLOCK_HAT.value(), 1.0F, 0.25F);
        }
        if (result.diamondReached()) {
            rewards.claimOnce(DIAMOND_MODE);
            show(Component.translatable("computer.antos.ore_2048.diamond"));
        }
        if (result.spawn() != null && result.spawn().tier() == Ore2048Board.TNT) {
            show(Component.translatable("computer.antos.ore_2048.tnt"));
            if (!result.lost() && !GameTip.seen(TNT_TIP)) tntTip = true;
        }
        if (result.fuseWarning()) sound(SoundEvents.TNT_PRIMED, 1.0F, 0.4F);
        if (result.blasted()) {
            show(Component.translatable("computer.antos.ore_2048.blast"));
            sound(SoundEvents.GENERIC_EXPLODE, 1.2F, 0.2F);
        }
        if (result.lost()) {
            restart.reset();
            submit();
            sound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F, 0.4F);
        }
    }

    private boolean tipVisible() {
        return tntTip && !beaconOverlay && System.currentTimeMillis() - movedAt >= Ore2048Renderer.TIP_DELAY_MS;
    }

    private void dismissTip() {
        if (!tipVisible()) return;
        tntTip = false;
        GameTip.markSeen(TNT_TIP);
    }

    private void show(Component message) {
        event = message;
        eventUntil = System.currentTimeMillis() + EVENT_MS;
    }

    private void sound(SoundEvent sound, float pitch, float volume) {
        if (soundEnabled) GameSounds.play(sound, pitch, volume);
    }

    private void submit() {
        if (board != null) GameData.submitBest(GAME_ID, board.score());
    }

    private void reset() {
        board = new Ore2048Board();
        last = null;
        movedAt = 0L;
        beaconOverlay = false;
        tntTip = false;
        event = null;
        eventUntil = 0L;
        restart.reset();
    }
}
