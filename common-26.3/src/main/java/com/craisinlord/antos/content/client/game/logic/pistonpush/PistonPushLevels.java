package com.craisinlord.antos.content.client.game.logic.pistonpush;

import java.util.ArrayList;
import java.util.List;

public final class PistonPushLevels {
    public static final int WORLDS = 4;
    public static final int PER_WORLD = 6;

    private static final List<Level> LEVELS = List.of(
            new Level("w1_01", "First Push", 0, 3, new String[]{
                    "#######",
                    "#@ $ .#",
                    "#######"
            }),
            new Level("w1_02", "Double Shove", 0, 4, new String[]{
                    "#########",
                    "###   ###",
                    "#@$$  ..#",
                    "###   ###",
                    "#########"
            }),
            new Level("w1_03", "Two Crates", 0, 8, new String[]{
                    "#######",
                    "#     #",
                    "#@$ $.#",
                    "#    .#",
                    "#######"
            }),
            new Level("w1_04", "Chain Reaction", 0, 14, new String[]{
                    "########",
                    "#.     #",
                    "#@$$ $.#",
                    "#.     #",
                    "########"
            }),
            new Level("w1_05", "Storeroom", 0, 22, new String[]{
                    "#######",
                    "#.   .#",
                    "# $#$ #",
                    "#  @  #",
                    "# $#  #",
                    "#   . #",
                    "#######"
            }),
            new Level("w1_06", "Warehouse", 0, 31, new String[]{
                    "##########",
                    "#   #  . #",
                    "# $   $  #",
                    "## #### ##",
                    "#. $@  $.#",
                    "#    #   #",
                    "# .  #   #",
                    "##########"
            }),
            new Level("w2_01", "Sticky", 1, 4, new String[]{
                    "#######",
                    "#@S$ .#",
                    "#  $ .#",
                    "#######"
            }),
            new Level("w2_02", "Drag Along", 1, 8, new String[]{
                    "########",
                    "#     .#",
                    "#  $  .#",
                    "#@ S  .#",
                    "#  $   #",
                    "#      #",
                    "########"
            }),
            new Level("w2_03", "Slime Pair", 1, 13, new String[]{
                    "#########",
                    "#       #",
                    "# S  S  #",
                    "#@  ##  #",
                    "#  .  . #",
                    "#########"
            }),
            new Level("w2_04", "Peel Away", 1, 18, new String[]{
                    "########",
                    "#  .   #",
                    "#  $   #",
                    "#@ S$ .#",
                    "#      #",
                    "#  .   #",
                    "########"
            }),
            new Level("w2_05", "Slime Factory", 1, 23, new String[]{
                    "###########",
                    "#   #     #",
                    "# $ S $ . #",
                    "#   #  #  #",
                    "#@  S  # .#",
                    "#   #  .  #",
                    "###########"
            }),
            new Level("w2_06", "Gooey Gate", 1, 28, new String[]{
                    "##########",
                    "#    #   #",
                    "# $S $ . #",
                    "#@   #   #",
                    "##  ### ##",
                    "# .    . #",
                    "##########"
            }),
            new Level("w3_01", "Thin Ice", 2, 2, new String[]{
                    "#########",
                    "#@ $~~~.#",
                    "#########"
            }),
            new Level("w3_02", "Skid Mark", 2, 5, new String[]{
                    "#########",
                    "#@  #   #",
                    "# $~~~~ #",
                    "#  ~~~~.#",
                    "#   #   #",
                    "#########"
            }),
            new Level("w3_03", "Stopper", 2, 8, new String[]{
                    "#########",
                    "#@  #   #",
                    "# $ ~~~ #",
                    "#   ~~~ #",
                    "# $ ~~~ #",
                    "#   ~.~ #",
                    "#########"
            }),
            new Level("w3_04", "Anchor", 2, 12, new String[]{
                    "##########",
                    "#@   #   #",
                    "# S$ ~~~.#",
                    "#    ~~~ #",
                    "# $  ~~~.#",
                    "#        #",
                    "##########"
            }),
            new Level("w3_05", "Frozen Lake", 2, 20, new String[]{
                    "##########",
                    "#   .    #",
                    "# ~~~~~~ #",
                    "#@$~~~~$ #",
                    "# ~~~~~~ #",
                    "#   .    #",
                    "##########"
            }),
            new Level("w3_06", "Rink", 2, 24, new String[]{
                    "###########",
                    "#@  .     #",
                    "# $~~~~~~ #",
                    "# ~~~~~~~ #",
                    "# ~~~~~~$ #",
                    "# $  .  . #",
                    "###########"
            }),
            new Level("w4_01", "Honey Comb", 3, 5, new String[]{
                    "########",
                    "#@H$  .#",
                    "#  $  .#",
                    "########"
            }),
            new Level("w4_02", "Split Decision", 3, 11, new String[]{
                    "##########",
                    "#   .    #",
                    "# $SH$   #",
                    "#@     ..#",
                    "#        #",
                    "##########"
            }),
            new Level("w4_03", "Not Friends", 3, 13, new String[]{
                    "#########",
                    "#   $   #",
                    "#@ SH  .#",
                    "#   $  .#",
                    "#      .#",
                    "#########"
            }),
            new Level("w4_04", "Sticky Ice", 3, 13, new String[]{
                    "###########",
                    "#@        #",
                    "# H$~~~~~.#",
                    "#   ~~~~~ #",
                    "# S$~~~~~.#",
                    "#         #",
                    "###########"
            }),
            new Level("w4_05", "Sweet Spot", 3, 19, new String[]{
                    "#########",
                    "#  .    #",
                    "# H$ #  #",
                    "#@   H .#",
                    "#  #    #",
                    "#  .    #",
                    "#########"
            }),
            new Level("w4_06", "Grand Finale", 3, 32, new String[]{
                    "############",
                    "#    #     #",
                    "# $S # H$  #",
                    "#@     ~~~.#",
                    "#  ##  ~~~ #",
                    "#  .  .   .#",
                    "############"
            })
    );

    private PistonPushLevels() {}

    public static List<Level> all() {
        return LEVELS;
    }

    public static List<Level> world(int world) {
        List<Level> levels = new ArrayList<>();
        for (Level level : LEVELS) if (level.world() == world) levels.add(level);
        return levels;
    }

    public static Level byId(String id) {
        for (Level level : LEVELS) if (level.id().equals(id)) return level;
        return null;
    }

    public static Level next(Level level) {
        int index = LEVELS.indexOf(level);
        return index < 0 || index + 1 >= LEVELS.size() ? null : LEVELS.get(index + 1);
    }

    public record Level(String id, String name, int world, int par, String[] rows) {
        public Level {
            rows = rows.clone();
        }

        @Override
        public String[] rows() {
            return rows.clone();
        }

        public int number() {
            return Integer.parseInt(id.substring(id.indexOf('_') + 1));
        }
    }
}
