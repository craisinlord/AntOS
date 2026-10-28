package com.craisinlord.antos.content.client.game.logic.pistonpush;

import com.craisinlord.antos.content.client.game.logic.pistonpush.PistonPushBoard.Block;
import com.craisinlord.antos.content.client.game.logic.pistonpush.PistonPushBoard.Terrain;
import com.craisinlord.antos.content.client.game.logic.pistonpush.PistonPushRules.MoveResult;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static com.craisinlord.antos.content.client.game.logic.pistonpush.PistonPushRules.DOWN;
import static com.craisinlord.antos.content.client.game.logic.pistonpush.PistonPushRules.LEFT;
import static com.craisinlord.antos.content.client.game.logic.pistonpush.PistonPushRules.RIGHT;

public final class PistonPushTest {
    public static void main(String[] args) {
        parsing();
        basicPushes();
        slime();
        honey();
        groupLimit();
        ice();
        undoAndRestart();
        progress();
        levels();
        System.out.println("PistonPushTest passed");
    }

    private static void parsing() {
        rejects("level has no player", "#####", "# $.#", "#####");
        rejects("two players", "######", "#@$.@#", "######");
        rejects("unknown tile", "#####", "#@$X#", "#.  #", "#####");
        rejects("open edge", "#####", "#@$. ", "#####");
        rejects("short row leaks", "#####", "#@$.", "#####");
        rejects("too wide", "###############", "#@$.          #", "###############");
        rejects("too tall", "####", "#@ #", "#$ #", "#. #", "#  #", "#  #", "#  #", "#  #", "#  #", "#  #", "####");
        rejects("no plates", "#####", "#@$ #", "#####");
        rejects("fewer blocks than plates", "######", "#@$..#", "######");
        rejects("starts solved", "####", "#@*#", "####");
        rejects("block outside walls", "#####  ", "#@$.# $", "#####  ");
        rejects("empty", new String[0]);
        PistonPushBoard board = PistonPushBoard.parse(
                "  #####",
                "###+ ~#",
                "#s h $#",
                "#######");
        check(board.width() == 7 && board.height() == 4, "board size");
        check(board.playerX() == 3 && board.playerY() == 1, "player on plate is found");
        check(board.terrain(3, 1) == Terrain.PLATE, "player plate");
        check(board.terrain(5, 1) == Terrain.ICE, "ice cell");
        check(board.terrain(0, 0) == Terrain.VOID, "outside space is void");
        check(board.block(1, 2) == Block.SLIME && board.terrain(1, 2) == Terrain.PLATE, "slime on plate");
        check(board.block(3, 2) == Block.HONEY && board.terrain(3, 2) == Terrain.PLATE, "honey on plate");
        check(board.block(5, 2) == Block.BASIC && board.terrain(5, 2) == Terrain.FLOOR, "basic block");
        check(board.plateCount() == 3 && board.coveredPlates() == 2 && !board.solved(), "plate counts");
    }

    private static void basicPushes() {
        PistonPushBoard board = PistonPushBoard.parse("#######", "#@ $ .#", "#######");
        MoveResult step = PistonPushRules.move(board, RIGHT);
        check(step.moved() && !step.pushed() && board.playerX() == 2, "walking moves the player");
        MoveResult push = PistonPushRules.move(board, RIGHT);
        check(push.pushed() && board.block(4, 1) == Block.BASIC && board.block(3, 1) == Block.NONE && board.playerX() == 3, "basic push");
        check(!push.solved(), "not solved yet");
        MoveResult last = PistonPushRules.move(board, RIGHT);
        check(last.solved() && last.platesCovered() == 1, "pushing onto the plate wins");

        PistonPushBoard chain = PistonPushBoard.parse("########", "#@$$  .#", "########");
        MoveResult chained = PistonPushRules.move(chain, RIGHT);
        check(chained.blocks().size() == 2 && chain.block(3, 1) == Block.BASIC && chain.block(4, 1) == Block.BASIC && chain.block(2, 1) == Block.NONE, "chain push");

        PistonPushBoard wall = PistonPushBoard.parse("#####", "#@ $#", "#.  #", "#####");
        PistonPushRules.move(wall, RIGHT);
        MoveResult blocked = PistonPushRules.move(wall, RIGHT);
        check(blocked.blocked() && wall.playerX() == 2 && wall.block(3, 1) == Block.BASIC, "push into wall does nothing");
        PistonPushBoard chainWall = PistonPushBoard.parse("#####", "#@$$#", "#.. #", "#####");
        check(PistonPushRules.move(chainWall, RIGHT).blocked() && chainWall.playerX() == 1, "chain into wall does nothing");
        check(PistonPushRules.move(chainWall, LEFT).blocked(), "walking into a wall does nothing");

        PistonPushBoard partial = PistonPushBoard.parse("#######", "#@$ ..#", "#  $  #", "#######");
        PistonPushRules.move(partial, RIGHT);
        PistonPushRules.move(partial, RIGHT);
        check(partial.coveredPlates() == 1 && !partial.solved(), "one of two plates is not a win");
    }

    private static void slime() {
        PistonPushBoard seed = PistonPushBoard.parse("#######", "#@S$ .#", "#  $ .#", "#######");
        MoveResult first = PistonPushRules.move(seed, RIGHT);
        check(first.slime() && seed.block(3, 1) == Block.SLIME && seed.block(4, 1) == Block.BASIC && seed.block(3, 2) == Block.BASIC, "slime pushes its chain only");
        MoveResult second = PistonPushRules.move(seed, RIGHT);
        check(second.blocks().size() == 3 && seed.block(4, 1) == Block.SLIME && seed.block(5, 1) == Block.BASIC && seed.block(4, 2) == Block.BASIC && seed.block(3, 2) == Block.NONE, "slime drags its side neighbour");
        PistonPushRules.move(seed, DOWN);
        MoveResult finish = PistonPushRules.move(seed, RIGHT);
        check(seed.block(5, 2) == Block.BASIC && seed.block(4, 1) == Block.SLIME, "plain block does not pull the slime");
        check(finish.solved(), "seed level solves in four moves");

        PistonPushBoard pair = PistonPushBoard.parse(
                "########",
                "#$S    #",
                "#@S   .#",
                "#     .#",
                "#     .#",
                "########");
        MoveResult moved = PistonPushRules.move(pair, RIGHT);
        check(moved.blocks().size() == 3, "slime chain group size");
        check(pair.block(3, 2) == Block.SLIME && pair.block(3, 1) == Block.SLIME && pair.block(2, 1) == Block.BASIC && pair.block(1, 1) == Block.NONE, "slime sticks to slime and pulls the block behind");

        PistonPushBoard pinned = PistonPushBoard.parse("######", "##$# #", "#@S .#", "#   .#", "######");
        MoveResult stuck = PistonPushRules.move(pinned, RIGHT);
        check(stuck.blocked() && pinned.block(2, 2) == Block.SLIME && pinned.block(2, 1) == Block.BASIC && pinned.playerX() == 1, "sideways member blocked by wall stops everything");

        PistonPushBoard plain = PistonPushBoard.parse("#######", "# S   #", "#@$  .#", "#######");
        MoveResult push = PistonPushRules.move(plain, RIGHT);
        check(!push.slime() && plain.block(3, 2) == Block.BASIC && plain.block(2, 1) == Block.SLIME, "plain block does not drag an adjacent slime");

        PistonPushBoard player = PistonPushBoard.parse(
                "#######",
                "#SSS  #",
                "#S@S .#",
                "#    .#",
                "#######");
        PistonPushBoard.Snapshot before = player.snapshot();
        check(PistonPushRules.move(player, RIGHT).blocked() && player.snapshot().equals(before), "group moving into the player is blocked");
    }

    private static void honey() {
        PistonPushBoard board = PistonPushBoard.parse("#######", "# H   #", "#@S  .#", "#  $ .#", "#######");
        MoveResult push = PistonPushRules.move(board, RIGHT);
        check(push.slime() && !push.honey() && board.block(2, 1) == Block.HONEY && board.block(3, 2) == Block.SLIME, "slime does not stick to honey");

        PistonPushBoard sticky = PistonPushBoard.parse("#######", "#@H  .#", "# $  .#", "#######");
        MoveResult moved = PistonPushRules.move(sticky, RIGHT);
        check(moved.honey() && sticky.block(3, 1) == Block.HONEY && sticky.block(3, 2) == Block.BASIC, "honey drags a basic block");

        PistonPushBoard front = PistonPushBoard.parse("#######", "#@SH .#", "#   ..#", "# $   #", "#######");
        PistonPushRules.move(front, RIGHT);
        check(front.block(3, 1) == Block.SLIME && front.block(4, 1) == Block.HONEY, "honey in front of a slime is still pushed");

        PistonPushBoard combo = PistonPushBoard.parse("########", "# HH   #", "#@SH  .#", "#  $  .#", "#     .#", "########");
        PistonPushRules.move(combo, RIGHT);
        check(combo.block(3, 2) == Block.SLIME && combo.block(4, 2) == Block.HONEY && combo.block(3, 1) == Block.HONEY && combo.block(4, 1) == Block.HONEY && combo.block(4, 3) == Block.BASIC && combo.block(2, 1) == Block.NONE, "honey sticks to honey and basic blocks");
    }

    private static void groupLimit() {
        PistonPushBoard big = PistonPushBoard.parse(
                "##############",
                "#            #",
                "#@SSSSSSS    #",
                "#  SSSSSS    #",
                "#           .#",
                "##############");
        check(PistonPushRules.move(big, RIGHT).blocked() && big.block(2, 2) == Block.SLIME, "thirteen blocks are too many");
        PistonPushBoard twelve = PistonPushBoard.parse(
                "##############",
                "#            #",
                "#@SSSSSSS    #",
                "#   SSSSS    #",
                "#           .#",
                "##############");
        MoveResult moved = PistonPushRules.move(twelve, RIGHT);
        check(moved.moved() && moved.blocks().size() == 12 && twelve.block(9, 2) == Block.SLIME, "twelve blocks still move");
    }

    private static void ice() {
        PistonPushBoard seed = PistonPushBoard.parse("#########", "#@ $~~~.#", "#########");
        PistonPushRules.move(seed, RIGHT);
        MoveResult slide = PistonPushRules.move(seed, RIGHT);
        check(slide.slide() == 3 && seed.block(7, 1) == Block.BASIC && slide.solved(), "block slides to the plate");
        check(slide.blocks().get(0).toX() == 7 && seed.playerX() == 3, "slide result and player stays");

        PistonPushBoard wall = PistonPushBoard.parse("#######", "#@$~~~#", "#.    #", "#######");
        MoveResult walled = PistonPushRules.move(wall, RIGHT);
        check(walled.slide() == 2 && wall.block(5, 1) == Block.BASIC, "slide stops at a wall");

        PistonPushBoard floor = PistonPushBoard.parse("########", "#@$~~ .#", "########");
        MoveResult exit = PistonPushRules.move(floor, RIGHT);
        check(exit.slide() == 2 && floor.block(5, 1) == Block.BASIC, "slide stops after leaving the ice");

        PistonPushBoard block = PistonPushBoard.parse("########", "#@$~~$.#", "########");
        MoveResult hit = PistonPushRules.move(block, RIGHT);
        check(hit.slide() == 1 && block.block(4, 1) == Block.BASIC && block.block(5, 1) == Block.BASIC, "slide stops at a block");

        PistonPushBoard group = PistonPushBoard.parse("#########", "#@$$~~~.#", "#########");
        MoveResult together = PistonPushRules.move(group, RIGHT);
        check(together.slide() == 0 && group.block(3, 1) == Block.BASIC && group.block(4, 1) == Block.BASIC, "groups do not slide");

        PistonPushBoard sticky = PistonPushBoard.parse("#########", "#@S~~~~.#", "# $     #", "#########");
        PistonPushRules.move(sticky, RIGHT);
        check(sticky.block(3, 1) == Block.SLIME && sticky.block(3, 2) == Block.BASIC, "slime group does not slide");

        PistonPushBoard walker = PistonPushBoard.parse("#######", "#@~~~$#", "#.    #", "#######");
        PistonPushRules.move(walker, RIGHT);
        check(walker.playerX() == 2, "player does not slide");
    }

    private static void undoAndRestart() {
        PistonPushGame game = new PistonPushGame("#######", "#@S$ .#", "#  $ .#", "#######");
        PistonPushBoard.Snapshot start = game.board().snapshot();
        game.move(LEFT);
        check(game.moves() == 0 && !game.canUndo(), "blocked move is not counted");
        game.move(RIGHT);
        game.move(RIGHT);
        PistonPushBoard.Snapshot middle = game.board().snapshot();
        game.move(DOWN);
        game.move(RIGHT);
        check(game.board().solved() && game.moves() == 4, "solved in four");
        check(!game.move(LEFT).moved() && game.moves() == 4, "no moves after solving");
        game.undo();
        game.undo();
        check(game.moves() == 2 && game.board().snapshot().equals(middle), "undo restores the board and the counter");
        game.undo();
        game.undo();
        check(game.moves() == 0 && game.board().snapshot().equals(start) && !game.undo(), "undo to start");
        game.move(RIGHT);
        game.move(RIGHT);
        game.restart();
        check(game.moves() == 0 && game.board().snapshot().equals(start) && !game.canUndo(), "restart reparses");

        PistonPushGame walk = new PistonPushGame("#######", "#@   .#", "#  $  #", "#######");
        for (int index = 0; index < PistonPushGame.MAX_UNDO + 5; index++) walk.move(index % 2 == 0 ? RIGHT : LEFT);
        int undone = 0;
        while (walk.undo()) undone++;
        check(undone == PistonPushGame.MAX_UNDO, "undo history is capped");
    }

    private static void progress() {
        Map<String, Integer> progress = new TreeMap<>();
        check(PistonPushProgress.record(progress, "w1_01", 14), "first clear records");
        check(!PistonPushProgress.record(progress, "w1_01", 20), "worse clear ignored");
        check(PistonPushProgress.record(progress, "w1_01", 3), "better clear records");
        PistonPushProgress.record(progress, "w1_02", 9);
        String encoded = PistonPushProgress.encode(progress);
        check(encoded.equals("w1_01=3;w1_02=9"), "encoded form " + encoded);
        check(PistonPushProgress.decode(encoded).equals(progress), "codec round trip");
        Map<String, Integer> garbage = PistonPushProgress.decode("w1_01=14;;=3;x=;W1=4;w1_02=abc;w1_03=0;w1_04=5=6;w1_05=7;w1_06=-2;w1_07=999999;w1_05=4");
        check(garbage.size() == 2 && garbage.get("w1_01") == 14 && garbage.get("w1_05") == 4, "garbage is skipped " + garbage);
        check(PistonPushProgress.decode(null).isEmpty() && PistonPushProgress.decode("").isEmpty() && PistonPushProgress.decode("\0;=;;").isEmpty(), "empty input");
        check(PistonPushProgress.encode(PistonPushProgress.decode(encoded + ";" + encoded)).length() <= 2048, "fits in game data");
        Map<String, Integer> everything = new TreeMap<>();
        for (PistonPushLevels.Level level : PistonPushLevels.all()) PistonPushProgress.record(everything, level.id(), 99999);
        check(PistonPushProgress.encode(everything).length() <= 2048, "full progress fits in game data");

        Map<String, Integer> unlocks = new TreeMap<>();
        check(PistonPushProgress.unlocked(unlocks, 0) && !PistonPushProgress.unlocked(unlocks, 1), "only the first world starts open");
        for (int index = 1; index <= 3; index++) PistonPushProgress.record(unlocks, "w1_0" + index, 10);
        check(!PistonPushProgress.unlocked(unlocks, 1), "three clears are not enough");
        PistonPushProgress.record(unlocks, "w1_06", 40);
        check(PistonPushProgress.unlocked(unlocks, 1) && !PistonPushProgress.unlocked(unlocks, 2), "four clears open the next world");
        for (int index = 1; index <= 4; index++) PistonPushProgress.record(unlocks, "w3_0" + index, 10);
        check(!PistonPushProgress.unlocked(unlocks, 3), "a world needs its previous world open");
        PistonPushLevels.Level first = PistonPushLevels.byId("w1_01");
        check(PistonPushProgress.atPar(unlocks, first) == false, "unsolved is not par");
        PistonPushProgress.record(unlocks, "w1_01", first.par());
        check(PistonPushProgress.atPar(unlocks, first), "par clear");
    }

    private static void levels() {
        check(PistonPushLevels.all().size() == PistonPushLevels.WORLDS * PistonPushLevels.PER_WORLD, "level count");
        Set<String> ids = new HashSet<>();
        for (int world = 0; world < PistonPushLevels.WORLDS; world++) {
            check(PistonPushLevels.world(world).size() == PistonPushLevels.PER_WORLD, "world size " + world);
        }
        for (PistonPushLevels.Level level : PistonPushLevels.all()) {
            String expected = String.format(java.util.Locale.ROOT, "w%d_%02d", level.world() + 1, level.number());
            check(level.id().equals(expected) && ids.add(level.id()), "level id " + level.id());
            check(!level.name().isBlank() && level.par() > 0, "level metadata " + level.id());
            PistonPushBoard board;
            try {
                board = PistonPushBoard.parse(level.rows());
            } catch (IllegalArgumentException failure) {
                throw new AssertionError(level.id() + " does not parse: " + failure.getMessage());
            }
            check(board.blockCount() <= 5, level.id() + " has too many blocks");
            check(board.floorCount() <= 70, level.id() + " has too much floor");
            int optimal;
            try {
                optimal = PistonPushSolver.solve(level.rows(), PistonPushSolver.DEFAULT_CAP);
            } catch (IllegalStateException failure) {
                throw new AssertionError(level.id() + " exceeded the solver state cap");
            }
            check(optimal > 0, level.id() + " is unsolvable");
            check(optimal == level.par(), level.id() + " par " + level.par() + " but optimum is " + optimal);
        }
        check(PistonPushLevels.next(PistonPushLevels.byId("w1_06")) == PistonPushLevels.byId("w2_01"), "next level crosses worlds");
        check(PistonPushLevels.next(PistonPushLevels.byId("w4_06")) == null, "last level has no next");
    }

    private static void rejects(String message, String... rows) {
        try {
            PistonPushBoard.parse(rows);
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("expected a parse error: " + message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
