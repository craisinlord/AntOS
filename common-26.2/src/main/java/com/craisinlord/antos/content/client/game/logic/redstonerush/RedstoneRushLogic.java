package com.craisinlord.antos.content.client.game.logic.redstonerush;

import java.util.ArrayList;
import java.util.List;

public final class RedstoneRushLogic {
    public static final int COLUMNS = 12;
    public static final int ROWS = 9;
    public static final int MAX_POWER = 15;
    public static final int REPEATER_LEVEL = 4;
    public static final int FAST_FACTOR = 8;
    public static final int TILE_POINTS = 10;
    public static final int CROSS_BONUS = 50;
    public static final int LAMP_POINTS = 100;
    public static final int REPLACE_PENALTY = 25;
    public static final int UNPOWERED_PENALTY = 5;
    public static final long REPLACE_DELAY_MS = 350L;
    public static final int REWARD_LEVEL = 5;

    public enum State { COUNTDOWN, FLOWING, WON, LOST }

    public enum Loss { NONE, LEAK, FADED }

    public enum PlaceResult { PLACED, REPLACED, SAME, LOCKED, BUSY, INVALID }

    public enum EventKind { PLACE, REPLACE, POWER, REPEATER, CROSS, WIN, LEAK, FADED }

    public record Event(EventKind kind, int power) { }

    private final List<Event> events = new ArrayList<>();
    private long runSeed;
    private int level;
    private int score;
    private State state;
    private Loss loss;
    private RedstoneRushLayout layout;
    private final int[] rotations = new int[Piece.values().length];
    private Piece selected = Piece.STRAIGHT;
    private Tile[][] tiles;
    private boolean[][] placed;
    private int[][][] power;
    private long countdownLeft;
    private long elapsed;
    private long placeCooldown;
    private boolean fast;
    private int headX;
    private int headY;
    private int headDirection;
    private int nextPower;
    private int lossX;
    private int lossY;
    private int poweredTiles;
    private int crossBonuses;

    public RedstoneRushLogic(long runSeed) {
        for (Piece piece : Piece.values()) rotations[piece.ordinal()] = piece.defaultRotation();
        restart(runSeed);
    }

    public static long countdownMs(int level) {
        return Math.max(6000L, 20000L - 1500L * (Math.max(1, level) - 1));
    }

    public static long flowMs(int level) {
        return Math.max(600L, Math.round(2000.0 * Math.pow(0.92, Math.max(1, level) - 1)));
    }

    public static int minDistance(int level) {
        return Math.min(18, 4 + 2 * (Math.max(1, level) - 1));
    }

    public static int obsidianCount(int level) {
        return Math.min(6, Math.max(1, level) - 1);
    }

    public void restart(long seed) {
        runSeed = seed;
        score = 0;
        startLevel(1, RedstoneRushGenerator.generate(seed, 1, COLUMNS, ROWS));
    }

    public void nextLevel() {
        if (state != State.WON) return;
        startLevel(level + 1, RedstoneRushGenerator.generate(runSeed, level + 1, COLUMNS, ROWS));
    }

    public void startLevel(int number, RedstoneRushLayout next) {
        level = Math.max(1, number);
        layout = next;
        int columns = next.columns();
        int rows = next.rows();
        tiles = new Tile[columns][rows];
        placed = new boolean[columns][rows];
        power = new int[columns][rows][2];
        for (int x = 0; x < columns; x++) for (int y = 0; y < rows; y++) tiles[x][y] = next.tile(x, y);
        if (!unlocked(selected)) selected = Piece.STRAIGHT;
        state = State.COUNTDOWN;
        loss = Loss.NONE;
        countdownLeft = countdownMs(level);
        elapsed = 0L;
        placeCooldown = 0L;
        fast = false;
        headX = next.sourceX();
        headY = next.sourceY();
        headDirection = next.sourceDirection();
        nextPower = MAX_POWER;
        lossX = -1;
        lossY = -1;
        poweredTiles = 0;
        crossBonuses = 0;
        events.clear();
    }

    public void advance(long dtMs) {
        if (dtMs <= 0L || state == State.WON || state == State.LOST) return;
        placeCooldown = Math.max(0L, placeCooldown - dtMs);
        long remaining = dtMs;
        if (state == State.COUNTDOWN) {
            countdownLeft -= remaining;
            if (countdownLeft > 0L) return;
            remaining = -countdownLeft;
            countdownLeft = 0L;
            state = State.FLOWING;
        }
        elapsed += remaining;
        while (state == State.FLOWING && elapsed >= stepMs()) {
            elapsed -= stepMs();
            step();
        }
    }

    public void fastForward() {
        if (state == State.COUNTDOWN) {
            countdownLeft = 0L;
            state = State.FLOWING;
        }
        if (state == State.FLOWING) fast = true;
    }

    public PlaceResult place(int x, int y) {
        if (state != State.COUNTDOWN && state != State.FLOWING) return PlaceResult.INVALID;
        if (!inside(x, y)) return PlaceResult.INVALID;
        Tile current = tiles[x][y];
        if (current == Tile.EMPTY) {
            if (placeCooldown > 0L) return PlaceResult.BUSY;
            tiles[x][y] = selectedTile();
            placed[x][y] = true;
            events.add(new Event(EventKind.PLACE, 0));
            return PlaceResult.PLACED;
        }
        if (!placed[x][y] || powered(x, y)) return PlaceResult.LOCKED;
        if (current == selectedTile()) return PlaceResult.SAME;
        if (placeCooldown > 0L) return PlaceResult.BUSY;
        tiles[x][y] = selectedTile();
        score = Math.max(0, score - REPLACE_PENALTY);
        placeCooldown = REPLACE_DELAY_MS;
        events.add(new Event(EventKind.REPLACE, 0));
        return PlaceResult.REPLACED;
    }

    public boolean unlocked(Piece piece) {
        return piece != Piece.REPEATER || level >= REPEATER_LEVEL;
    }

    public boolean select(Piece piece) {
        if (!unlocked(piece)) return false;
        selected = piece;
        return true;
    }

    public void rotate(int delta) {
        rotations[selected.ordinal()] = Math.floorMod(rotations[selected.ordinal()] + delta, selected.orientations());
    }

    public Piece selected() {
        return selected;
    }

    public Tile tileFor(Piece piece) {
        return piece.tile(rotations[piece.ordinal()]);
    }

    public Tile selectedTile() {
        return tileFor(selected);
    }

    public void setTile(int x, int y, Tile tile) {
        if (!inside(x, y) || tiles[x][y] != Tile.EMPTY && !placed[x][y]) return;
        tiles[x][y] = tile;
        placed[x][y] = tile != Tile.EMPTY;
    }

    public List<Event> drainEvents() {
        List<Event> drained = List.copyOf(events);
        events.clear();
        return drained;
    }

    private long stepMs() {
        long interval = fast ? Math.max(1L, flowMs(level) / FAST_FACTOR) : flowMs(level);
        return tiles[headX][headY].repeater() ? interval * 2L : interval;
    }

    private void step() {
        int nx = headX + Tile.DX[headDirection];
        int ny = headY + Tile.DY[headDirection];
        if (!inside(nx, ny)) {
            lose(Loss.LEAK, headX, headY);
            return;
        }
        Tile tile = tiles[nx][ny];
        if (tile == Tile.LAMP) {
            if (headDirection == Tile.opposite(layout.lampSide())) win();
            else lose(Loss.LEAK, nx, ny);
            return;
        }
        int exit = tile.exit(headDirection);
        if (exit < 0) {
            lose(Loss.LEAK, nx, ny);
            return;
        }
        int axis = tile == Tile.CROSS && Tile.vertical(headDirection) ? 1 : 0;
        if (power[nx][ny][axis] > 0) {
            lose(Loss.LEAK, nx, ny);
            return;
        }
        int multiplier = fast ? 2 : 1;
        if (tile.repeater()) {
            power[nx][ny][axis] = MAX_POWER;
            nextPower = MAX_POWER;
            events.add(new Event(EventKind.REPEATER, MAX_POWER));
        } else {
            if (nextPower <= 0) {
                lose(Loss.FADED, nx, ny);
                return;
            }
            power[nx][ny][axis] = nextPower;
            events.add(new Event(EventKind.POWER, nextPower));
            nextPower--;
        }
        score += TILE_POINTS * multiplier;
        poweredTiles++;
        if (tile == Tile.CROSS && power[nx][ny][1 - axis] > 0) {
            score += CROSS_BONUS * multiplier;
            crossBonuses++;
            events.add(new Event(EventKind.CROSS, power[nx][ny][axis]));
        }
        headX = nx;
        headY = ny;
        headDirection = exit;
    }

    private void win() {
        state = State.WON;
        headX = layout.lampX();
        headY = layout.lampY();
        score += LAMP_POINTS * level;
        score = Math.max(0, score - UNPOWERED_PENALTY * unpoweredPlaced());
        events.add(new Event(EventKind.WIN, 0));
    }

    private void lose(Loss reason, int x, int y) {
        state = State.LOST;
        loss = reason;
        lossX = x;
        lossY = y;
        events.add(new Event(reason == Loss.FADED ? EventKind.FADED : EventKind.LEAK, 0));
    }

    public int unpoweredPlaced() {
        int count = 0;
        for (int x = 0; x < tiles.length; x++) for (int y = 0; y < tiles[x].length; y++) if (placed[x][y] && !powered(x, y)) count++;
        return count;
    }

    public boolean inside(int x, int y) {
        return x >= 0 && y >= 0 && x < tiles.length && y < tiles[0].length;
    }

    public boolean powered(int x, int y) {
        return power[x][y][0] > 0 || power[x][y][1] > 0;
    }

    public int power(int x, int y, int axis) { return power[x][y][axis]; }
    public int power(int x, int y) { return Math.max(power[x][y][0], power[x][y][1]); }
    public Tile tile(int x, int y) { return tiles[x][y]; }
    public boolean placed(int x, int y) { return placed[x][y]; }
    public boolean replaceable(int x, int y) { return inside(x, y) && (tiles[x][y] == Tile.EMPTY || placed[x][y] && !powered(x, y)); }
    public int columns() { return tiles.length; }
    public int rows() { return tiles[0].length; }
    public RedstoneRushLayout layout() { return layout; }
    public State state() { return state; }
    public Loss loss() { return loss; }
    public int level() { return level; }
    public int score() { return score; }
    public long countdownLeft() { return countdownLeft; }
    public long countdownTotal() { return countdownMs(level); }
    public long placeCooldown() { return placeCooldown; }
    public boolean fast() { return fast; }
    public int headX() { return headX; }
    public int headY() { return headY; }
    public int headDirection() { return headDirection; }
    public int nextPower() { return nextPower; }
    public int lossX() { return lossX; }
    public int lossY() { return lossY; }
    public int poweredTiles() { return poweredTiles; }
    public int crossBonuses() { return crossBonuses; }
    public boolean lampLit() { return state == State.WON; }
    public float flowProgress() { return state == State.FLOWING ? Math.min(1.0F, elapsed / (float) stepMs()) : 0.0F; }
}
