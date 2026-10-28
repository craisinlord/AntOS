package com.craisinlord.antos.content.client.game.logic.redstonerush;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class RedstoneRushGenerator {
    private static final int ATTEMPTS = 600;
    private static final int POWER_STATES = RedstoneRushLogic.MAX_POWER + 1;

    private RedstoneRushGenerator() { }

    public static RedstoneRushLayout generate(long seed, int level, int columns, int rows) {
        Random random = new Random(seed * 0x9E3779B97F4A7C15L + level * 1_000_003L);
        int wanted = RedstoneRushLogic.minDistance(level);
        boolean repeaters = level >= RedstoneRushLogic.REPEATER_LEVEL;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            int need = Math.max(3, wanted - attempt / 100);
            RedstoneRushLayout layout = attempt(random, level, columns, rows, need);
            if (layout == null) continue;
            List<RedstoneRushLayout.Placement> path = solve(layout, repeaters);
            if (path == null) continue;
            layout.solution(path);
            return layout;
        }
        RedstoneRushLayout fallback = new RedstoneRushLayout(columns, rows, 0, rows / 2, Tile.EAST, Math.min(columns - 1, 4), rows / 2, Tile.WEST);
        fallback.solution(solve(fallback, repeaters));
        return fallback;
    }

    private static RedstoneRushLayout attempt(Random random, int level, int columns, int rows, int need) {
        int edge = random.nextInt(4);
        int sx;
        int sy;
        int direction;
        switch (edge) {
            case Tile.NORTH -> { sx = 1 + random.nextInt(columns - 2); sy = 0; direction = Tile.SOUTH; }
            case Tile.SOUTH -> { sx = 1 + random.nextInt(columns - 2); sy = rows - 1; direction = Tile.NORTH; }
            case Tile.WEST -> { sx = 0; sy = 1 + random.nextInt(rows - 2); direction = Tile.EAST; }
            default -> { sx = columns - 1; sy = 1 + random.nextInt(rows - 2); direction = Tile.WEST; }
        }
        int lx = random.nextInt(columns);
        int ly = random.nextInt(rows);
        int distance = Math.abs(lx - sx) + Math.abs(ly - sy);
        if (distance < need || distance > need + 3) return null;
        List<Integer> sides = new ArrayList<>();
        for (int side = 0; side < 4; side++) {
            int nx = lx + Tile.DX[side];
            int ny = ly + Tile.DY[side];
            if (nx < 0 || ny < 0 || nx >= columns || ny >= rows || nx == sx && ny == sy) continue;
            sides.add(side);
        }
        if (sides.isEmpty()) return null;
        int lampSide = sides.get(random.nextInt(sides.size()));
        RedstoneRushLayout layout = new RedstoneRushLayout(columns, rows, sx, sy, direction, lx, ly, lampSide);
        int firstX = sx + Tile.DX[direction];
        int firstY = sy + Tile.DY[direction];
        int lastX = lx + Tile.DX[lampSide];
        int lastY = ly + Tile.DY[lampSide];
        if (firstX == lx && firstY == ly) return null;
        int obsidian = RedstoneRushLogic.obsidianCount(level);
        for (int placed = 0, tries = 0; placed < obsidian && tries < 200; tries++) {
            int ox = random.nextInt(columns);
            int oy = random.nextInt(rows);
            if (layout.tile(ox, oy) != Tile.EMPTY || ox == firstX && oy == firstY || ox == lastX && oy == lastY) continue;
            layout.obsidian(ox, oy);
            placed++;
        }
        return layout;
    }

    public static List<RedstoneRushLayout.Placement> solve(RedstoneRushLayout layout, boolean repeaters) {
        int columns = layout.columns();
        int rows = layout.rows();
        int startX = layout.sourceX() + Tile.DX[layout.sourceDirection()];
        int startY = layout.sourceY() + Tile.DY[layout.sourceDirection()];
        if (!inside(columns, rows, startX, startY)) return null;
        int total = columns * rows * 4 * POWER_STATES;
        int[] parent = new int[total];
        Tile[] choice = new Tile[total];
        boolean[] seen = new boolean[total];
        Arrays.fill(parent, -1);
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        int start = index(rows, startX, startY, layout.sourceDirection(), RedstoneRushLogic.MAX_POWER);
        seen[start] = true;
        pending.add(start);
        int arrival = Tile.opposite(layout.lampSide());
        while (!pending.isEmpty()) {
            int state = pending.poll();
            int power = state % POWER_STATES;
            int travel = state / POWER_STATES % 4;
            int cell = state / POWER_STATES / 4;
            int x = cell / rows;
            int y = cell % rows;
            if (x == layout.lampX() && y == layout.lampY()) {
                if (travel != arrival) continue;
                return rebuild(rows, state, parent, choice);
            }
            if (layout.tile(x, y) != Tile.EMPTY) continue;
            if (power >= 1) {
                for (int exit : new int[]{travel, (travel + 1) & 3, (travel + 3) & 3}) {
                    visit(columns, rows, x, y, exit, power - 1, Tile.wire(Tile.opposite(travel), exit), state, parent, choice, seen, pending);
                }
            }
            if (repeaters) visit(columns, rows, x, y, travel, RedstoneRushLogic.MAX_POWER, Tile.repeater(travel), state, parent, choice, seen, pending);
        }
        return null;
    }

    private static void visit(int columns, int rows, int x, int y, int exit, int power, Tile tile, int from, int[] parent, Tile[] choice, boolean[] seen, ArrayDeque<Integer> pending) {
        int nx = x + Tile.DX[exit];
        int ny = y + Tile.DY[exit];
        if (!inside(columns, rows, nx, ny)) return;
        int next = index(rows, nx, ny, exit, power);
        if (seen[next]) return;
        seen[next] = true;
        parent[next] = from;
        choice[next] = tile;
        pending.add(next);
    }

    private static List<RedstoneRushLayout.Placement> rebuild(int rows, int state, int[] parent, Tile[] choice) {
        List<RedstoneRushLayout.Placement> path = new ArrayList<>();
        Set<Integer> cells = new HashSet<>();
        int current = state;
        while (parent[current] >= 0) {
            int previous = parent[current];
            int cell = previous / POWER_STATES / 4;
            if (!cells.add(cell)) return null;
            path.add(new RedstoneRushLayout.Placement(cell / rows, cell % rows, choice[current]));
            current = previous;
        }
        Collections.reverse(path);
        return path;
    }

    private static int index(int rows, int x, int y, int travel, int power) {
        return ((x * rows + y) * 4 + travel) * POWER_STATES + power;
    }

    private static boolean inside(int columns, int rows, int x, int y) {
        return x >= 0 && y >= 0 && x < columns && y < rows;
    }
}
