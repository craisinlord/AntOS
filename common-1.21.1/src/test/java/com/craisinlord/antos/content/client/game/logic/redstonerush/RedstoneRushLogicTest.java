package com.craisinlord.antos.content.client.game.logic.redstonerush;

import java.util.List;

public final class RedstoneRushLogicTest {
    private static final int C = RedstoneRushLogic.COLUMNS;
    private static final int R = RedstoneRushLogic.ROWS;

    public static void main(String[] args) {
        tiles();
        leaks();
        power();
        lamp();
        crossBonus();
        placing();
        palette();
        generator();
        curves();
        fastForward();
        repeaterDelay();
        scriptedRun();
        System.out.println("RedstoneRushLogicTest passed");
    }

    private static void tiles() {
        check(Tile.HORIZONTAL.exit(Tile.EAST) == Tile.EAST && Tile.HORIZONTAL.exit(Tile.WEST) == Tile.WEST, "horizontal passes both ways");
        check(Tile.HORIZONTAL.exit(Tile.NORTH) < 0, "horizontal rejects vertical travel");
        check(Tile.NORTH_EAST.exit(Tile.WEST) == Tile.NORTH && Tile.NORTH_EAST.exit(Tile.SOUTH) == Tile.EAST, "north east corner turns");
        check(Tile.EAST_SOUTH.exit(Tile.NORTH) == Tile.EAST && Tile.SOUTH_WEST.exit(Tile.EAST) == Tile.SOUTH && Tile.WEST_NORTH.exit(Tile.SOUTH) == Tile.WEST, "other corners turn");
        for (int d = 0; d < 4; d++) check(Tile.CROSS.exit(d) == d, "cross goes straight through");
        check(Tile.REPEATER_EAST.exit(Tile.EAST) == Tile.EAST && Tile.REPEATER_EAST.exit(Tile.WEST) < 0, "repeater is one way");
        check(Tile.REPEATER_NORTH.exit(Tile.EAST) < 0, "repeater rejects side entry");
        check(Tile.EMPTY.exit(Tile.EAST) < 0 && Tile.OBSIDIAN.exit(Tile.EAST) < 0, "empty and obsidian block");
        check(Tile.wire(Tile.NORTH, Tile.EAST) == Tile.NORTH_EAST && Tile.wire(Tile.WEST, Tile.EAST) == Tile.HORIZONTAL, "wire lookup works");
    }

    private static RedstoneRushLogic line(Tile... path) {
        RedstoneRushLayout layout = new RedstoneRushLayout(C, R, 0, 4, Tile.EAST, path.length + 1, 4, Tile.WEST);
        RedstoneRushLogic logic = new RedstoneRushLogic(1L);
        logic.startLevel(1, layout);
        for (int i = 0; i < path.length; i++) logic.setTile(i + 1, 4, path[i]);
        return logic;
    }

    private static void finish(RedstoneRushLogic logic) {
        for (int i = 0; i < 100000 && (logic.state() == RedstoneRushLogic.State.COUNTDOWN || logic.state() == RedstoneRushLogic.State.FLOWING); i++) logic.advance(50L);
    }

    private static void leaks() {
        RedstoneRushLogic empty = line(Tile.HORIZONTAL, Tile.EMPTY, Tile.HORIZONTAL);
        finish(empty);
        check(empty.state() == RedstoneRushLogic.State.LOST && empty.loss() == RedstoneRushLogic.Loss.LEAK && empty.lossX() == 2, "leak into empty loses");
        RedstoneRushLayout layout = new RedstoneRushLayout(C, R, 0, 4, Tile.EAST, 4, 4, Tile.WEST).obsidian(2, 4);
        RedstoneRushLogic blocked = new RedstoneRushLogic(1L);
        blocked.startLevel(1, layout);
        blocked.setTile(1, 4, Tile.HORIZONTAL);
        blocked.setTile(3, 4, Tile.HORIZONTAL);
        finish(blocked);
        check(blocked.state() == RedstoneRushLogic.State.LOST && blocked.loss() == RedstoneRushLogic.Loss.LEAK, "leak into obsidian loses");
        RedstoneRushLogic mismatched = line(Tile.HORIZONTAL, Tile.VERTICAL, Tile.HORIZONTAL);
        finish(mismatched);
        check(mismatched.state() == RedstoneRushLogic.State.LOST && mismatched.lossX() == 2, "mismatched tile loses");
        RedstoneRushLogic backwards = line(Tile.HORIZONTAL, Tile.REPEATER_WEST, Tile.HORIZONTAL);
        finish(backwards);
        check(backwards.state() == RedstoneRushLogic.State.LOST, "backwards repeater leaks");
        RedstoneRushLogic wrongSide = new RedstoneRushLogic(1L);
        wrongSide.startLevel(1, new RedstoneRushLayout(C, R, 0, 4, Tile.EAST, 3, 4, Tile.NORTH));
        wrongSide.setTile(1, 4, Tile.HORIZONTAL);
        wrongSide.setTile(2, 4, Tile.HORIZONTAL);
        finish(wrongSide);
        check(wrongSide.state() == RedstoneRushLogic.State.LOST, "lamp entered from a closed side leaks");
    }

    private static RedstoneRushLogic snake(boolean repeater) {
        RedstoneRushLogic logic = new RedstoneRushLogic(1L);
        logic.startLevel(4, new RedstoneRushLayout(C, R, 0, 0, Tile.EAST, 11, 8, Tile.NORTH));
        for (int x = 1; x <= 10; x++) logic.setTile(x, 0, Tile.HORIZONTAL);
        logic.setTile(11, 0, Tile.SOUTH_WEST);
        for (int y = 1; y <= 7; y++) logic.setTile(11, y, Tile.VERTICAL);
        if (repeater) logic.setTile(11, 3, Tile.REPEATER_SOUTH);
        return logic;
    }

    private static void power() {
        RedstoneRushLogic faded = snake(false);
        finish(faded);
        check(faded.state() == RedstoneRushLogic.State.LOST && faded.loss() == RedstoneRushLogic.Loss.FADED, "signal dies at zero power");
        check(faded.lossX() == 11 && faded.lossY() == 5, "the sixteenth wire is where it fades");
        check(faded.power(1, 0) == 15 && faded.power(11, 4) == 1, "power fades by one per wire");
        RedstoneRushLogic boosted = snake(true);
        finish(boosted);
        check(boosted.state() == RedstoneRushLogic.State.WON, "repeater carries the signal to the lamp");
        check(boosted.power(11, 2) == 3 && boosted.power(11, 3) == 15 && boosted.power(11, 4) == 15 && boosted.power(11, 7) == 12, "repeater resets power to fifteen");
    }

    private static void lamp() {
        RedstoneRushLogic logic = line(Tile.HORIZONTAL);
        check(logic.state() == RedstoneRushLogic.State.COUNTDOWN, "level starts in countdown");
        finish(logic);
        check(logic.state() == RedstoneRushLogic.State.WON && logic.lampLit(), "reaching the lamp wins");
        check(logic.score() == 10 + 100, "one tile and the lamp score");
    }

    private static void crossBonus() {
        RedstoneRushLogic logic = new RedstoneRushLogic(1L);
        logic.startLevel(1, new RedstoneRushLayout(C, R, 0, 2, Tile.EAST, 2, 0, Tile.SOUTH));
        logic.setTile(1, 2, Tile.HORIZONTAL);
        logic.setTile(2, 2, Tile.CROSS);
        logic.setTile(3, 2, Tile.SOUTH_WEST);
        logic.setTile(3, 3, Tile.WEST_NORTH);
        logic.setTile(2, 3, Tile.NORTH_EAST);
        logic.setTile(2, 1, Tile.VERTICAL);
        finish(logic);
        check(logic.state() == RedstoneRushLogic.State.WON, "loop through the cross wins");
        check(logic.crossBonuses() == 1 && logic.power(2, 2, 0) == 14 && logic.power(2, 2, 1) == 10, "cross powers both axes separately");
        check(logic.score() == 7 * 10 + 50 + 100, "cross passed twice earns the bonus");
    }

    private static void placing() {
        RedstoneRushLogic logic = line(Tile.HORIZONTAL, Tile.HORIZONTAL, Tile.HORIZONTAL, Tile.HORIZONTAL);
        logic.fastForward();
        while (logic.poweredTiles() < 3) logic.advance(10L);
        check(logic.score() == 60, "three fast tiles scored");
        check(logic.place(1, 4) == RedstoneRushLogic.PlaceResult.LOCKED, "powered tiles are locked");
        check(logic.place(0, 4) == RedstoneRushLogic.PlaceResult.LOCKED, "source is locked");
        check(logic.place(6, 6) == RedstoneRushLogic.PlaceResult.PLACED && logic.tile(6, 6) == Tile.HORIZONTAL, "empty cell takes the selected tile");
        check(logic.place(6, 6) == RedstoneRushLogic.PlaceResult.SAME && logic.score() == 60, "replacing with the same tile is free and does nothing");
        logic.select(Piece.CORNER);
        check(logic.place(6, 6) == RedstoneRushLogic.PlaceResult.REPLACED && logic.tile(6, 6) == Tile.NORTH_EAST, "unpowered placed tile can be replaced");
        check(logic.score() == 35, "replacing costs points");
        check(logic.place(7, 6) == RedstoneRushLogic.PlaceResult.BUSY, "replacing has a short delay");
        logic.advance(RedstoneRushLogic.REPLACE_DELAY_MS);
        check(logic.place(-1, 0) == RedstoneRushLogic.PlaceResult.INVALID, "outside is invalid");
        RedstoneRushLogic fresh = line(Tile.HORIZONTAL);
        fresh.place(5, 5);
        fresh.rotate(1);
        check(fresh.place(5, 5) == RedstoneRushLogic.PlaceResult.REPLACED && fresh.score() == 0, "score never goes negative");
    }

    private static void palette() {
        RedstoneRushLogic logic = line(Tile.HORIZONTAL);
        check(logic.selected() == Piece.STRAIGHT && logic.selectedTile() == Tile.HORIZONTAL, "straight is selected first");
        logic.rotate(1);
        check(logic.selectedTile() == Tile.VERTICAL, "straight rotates to vertical");
        logic.rotate(1);
        check(logic.selectedTile() == Tile.HORIZONTAL, "straight has two orientations");
        check(logic.select(Piece.CORNER) && logic.selectedTile() == Tile.NORTH_EAST, "corner selected");
        Tile[] corners = {Tile.EAST_SOUTH, Tile.SOUTH_WEST, Tile.WEST_NORTH, Tile.NORTH_EAST};
        for (Tile corner : corners) {
            logic.rotate(1);
            check(logic.selectedTile() == corner, "corner rotates clockwise");
        }
        logic.rotate(-1);
        check(logic.selectedTile() == Tile.WEST_NORTH, "rotation goes back too");
        logic.select(Piece.STRAIGHT);
        check(logic.selectedTile() == Tile.HORIZONTAL, "each piece keeps its own rotation");
        logic.select(Piece.CORNER);
        check(logic.selectedTile() == Tile.WEST_NORTH, "corner rotation is remembered");
        check(logic.select(Piece.CROSS), "cross selected");
        logic.rotate(1);
        check(logic.selectedTile() == Tile.CROSS && Piece.CROSS.orientations() == 1, "cross has one orientation");
        check(!logic.unlocked(Piece.REPEATER) && !logic.select(Piece.REPEATER) && logic.selected() == Piece.CROSS, "repeater is locked before level four");
        RedstoneRushLogic late = new RedstoneRushLogic(3L);
        late.startLevel(RedstoneRushLogic.REPEATER_LEVEL, new RedstoneRushLayout(C, R, 0, 4, Tile.EAST, 4, 4, Tile.WEST));
        check(late.select(Piece.REPEATER) && late.selectedTile() == Tile.REPEATER_EAST, "repeater unlocks at level four facing east");
        for (int i = 0; i < 4; i++) late.rotate(1);
        check(late.selectedTile() == Tile.REPEATER_EAST && Piece.REPEATER.orientations() == 4, "repeater has four orientations");
        check(late.place(1, 4) == RedstoneRushLogic.PlaceResult.PLACED && late.tile(1, 4) == Tile.REPEATER_EAST, "placing uses the selected tile");
        late.startLevel(1, new RedstoneRushLayout(C, R, 0, 4, Tile.EAST, 4, 4, Tile.WEST));
        check(late.selected() == Piece.STRAIGHT, "locked selection falls back to straight");
        for (Tile tile : Tile.values()) {
            Piece piece = Piece.of(tile);
            check((piece != null) == tile.playable(), "every playable tile has a piece");
        }
    }

    private static void generator() {
        for (long seed = 0; seed < 200; seed++) {
            for (int level : new int[]{1, 3, 5, 8, 12}) {
                RedstoneRushLayout layout = RedstoneRushGenerator.generate(seed * 977L + 3L, level, C, R);
                List<RedstoneRushLayout.Placement> solution = layout.solution();
                check(!solution.isEmpty(), "generator returns a solution");
                int distance = Math.abs(layout.lampX() - layout.sourceX()) + Math.abs(layout.lampY() - layout.sourceY());
                check(distance >= Math.min(RedstoneRushLogic.minDistance(level), 3), "lamp is far enough away");
                check(layout.obsidianCount() <= 6, "obsidian stays within limits");
                RedstoneRushLogic logic = new RedstoneRushLogic(seed);
                logic.startLevel(level, layout);
                for (RedstoneRushLayout.Placement step : solution) {
                    check(level >= RedstoneRushLogic.REPEATER_LEVEL || !step.tile().repeater(), "no repeaters needed before level four");
                    logic.setTile(step.x(), step.y(), step.tile());
                }
                logic.fastForward();
                finish(logic);
                check(logic.state() == RedstoneRushLogic.State.WON, "seed " + seed + " level " + level + " is solvable within power rules");
            }
        }
        int needRepeaters = 0;
        int earlyNeed = 0;
        for (long seed = 0; seed < 100; seed++) {
            if (RedstoneRushGenerator.solve(RedstoneRushGenerator.generate(seed, 10, C, R), false) == null) needRepeaters++;
            if (RedstoneRushGenerator.solve(RedstoneRushGenerator.generate(seed, 2, C, R), false) == null) earlyNeed++;
        }
        check(needRepeaters >= 30, "late levels need repeaters: " + needRepeaters);
        check(earlyNeed == 0, "early levels never need repeaters");
        RedstoneRushLayout a = RedstoneRushGenerator.generate(11L, 4, C, R);
        RedstoneRushLayout b = RedstoneRushGenerator.generate(11L, 4, C, R);
        check(a.lampX() == b.lampX() && a.lampY() == b.lampY() && a.sourceX() == b.sourceX() && a.obsidianCount() == b.obsidianCount(), "generator is deterministic");
    }

    private static void curves() {
        check(RedstoneRushLogic.countdownMs(1) == 20000L && RedstoneRushLogic.countdownMs(2) == 18500L, "countdown starts at twenty seconds");
        check(RedstoneRushLogic.flowMs(1) == 2000L && RedstoneRushLogic.flowMs(2) == 1840L, "flow starts at two seconds");
        for (int level = 1; level <= 200; level++) {
            check(RedstoneRushLogic.countdownMs(level) >= 6000L && RedstoneRushLogic.countdownMs(level) <= 20000L, "countdown within limits");
            check(RedstoneRushLogic.flowMs(level) >= 600L && RedstoneRushLogic.flowMs(level) <= 2000L, "flow within limits");
            check(RedstoneRushLogic.obsidianCount(level) >= 0 && RedstoneRushLogic.obsidianCount(level) <= 6, "obsidian within limits");
            check(RedstoneRushLogic.minDistance(level) <= 18, "distance within the grid");
            if (level > 1) check(RedstoneRushLogic.minDistance(level) >= RedstoneRushLogic.minDistance(level - 1), "distance grows");
        }
        check(RedstoneRushLogic.countdownMs(100) == 6000L && RedstoneRushLogic.flowMs(100) == 600L, "curves bottom out");
    }

    private static void fastForward() {
        RedstoneRushLogic slow = line(Tile.HORIZONTAL, Tile.HORIZONTAL, Tile.HORIZONTAL);
        finish(slow);
        RedstoneRushLogic quick = line(Tile.HORIZONTAL, Tile.HORIZONTAL, Tile.HORIZONTAL);
        quick.fastForward();
        check(quick.state() == RedstoneRushLogic.State.FLOWING && quick.fast(), "fast forward skips the countdown");
        finish(quick);
        check(slow.score() == 30 + 100 && quick.score() == 60 + 100, "fast forward doubles tile points");
        RedstoneRushLogic timed = line(Tile.HORIZONTAL);
        timed.advance(RedstoneRushLogic.countdownMs(1) - 1L);
        check(timed.state() == RedstoneRushLogic.State.COUNTDOWN, "countdown still running");
        timed.advance(1L);
        check(timed.state() == RedstoneRushLogic.State.FLOWING, "countdown ends into flow");
    }

    private static void repeaterDelay() {
        RedstoneRushLogic logic = line(Tile.REPEATER_EAST, Tile.HORIZONTAL);
        logic.fastForward();
        long interval = RedstoneRushLogic.flowMs(1) / RedstoneRushLogic.FAST_FACTOR;
        logic.advance(interval);
        check(logic.powered(1, 4) && !logic.powered(2, 4), "repeater powers first");
        logic.advance(interval);
        check(!logic.powered(2, 4), "repeater waits an extra step");
        logic.advance(interval);
        check(logic.powered(2, 4), "repeater passes the signal on after two steps");
    }

    private static void scriptedRun() {
        RedstoneRushLogic logic = line(Tile.HORIZONTAL, Tile.HORIZONTAL, Tile.HORIZONTAL);
        check(logic.place(6, 7) == RedstoneRushLogic.PlaceResult.PLACED, "stray tile placed");
        finish(logic);
        check(logic.state() == RedstoneRushLogic.State.WON && logic.score() == 30 + 100 - 5, "level one score with an unpowered tile");
        logic.startLevel(2, new RedstoneRushLayout(C, R, 0, 4, Tile.EAST, 4, 4, Tile.WEST));
        check(logic.score() == 125, "score carries over");
        for (int x = 1; x <= 3; x++) logic.setTile(x, 4, Tile.HORIZONTAL);
        logic.place(8, 8);
        logic.rotate(1);
        logic.place(8, 8);
        check(logic.score() == 100, "replace penalty applied");
        logic.fastForward();
        finish(logic);
        check(logic.score() == 100 + 60 + 200 - 5, "level two fast score");
        RedstoneRushLogic run = new RedstoneRushLogic(99L);
        run.nextLevel();
        check(run.level() == 1, "next level needs a win");
        for (RedstoneRushLayout.Placement step : run.layout().solution()) run.setTile(step.x(), step.y(), step.tile());
        finish(run);
        check(run.state() == RedstoneRushLogic.State.WON, "generated level one solved");
        run.nextLevel();
        check(run.level() == 2 && run.state() == RedstoneRushLogic.State.COUNTDOWN, "next level begins");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
