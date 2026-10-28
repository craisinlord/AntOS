package com.craisinlord.antos.content.client.game.logic.redstonerush;

public enum Tile {
    EMPTY(0, -1),
    HORIZONTAL(Tile.bit(Tile.EAST) | Tile.bit(Tile.WEST), -1),
    VERTICAL(Tile.bit(Tile.NORTH) | Tile.bit(Tile.SOUTH), -1),
    NORTH_EAST(Tile.bit(Tile.NORTH) | Tile.bit(Tile.EAST), -1),
    EAST_SOUTH(Tile.bit(Tile.EAST) | Tile.bit(Tile.SOUTH), -1),
    SOUTH_WEST(Tile.bit(Tile.SOUTH) | Tile.bit(Tile.WEST), -1),
    WEST_NORTH(Tile.bit(Tile.WEST) | Tile.bit(Tile.NORTH), -1),
    CROSS(15, -1),
    REPEATER_NORTH(Tile.bit(Tile.NORTH) | Tile.bit(Tile.SOUTH), Tile.NORTH),
    REPEATER_EAST(Tile.bit(Tile.EAST) | Tile.bit(Tile.WEST), Tile.EAST),
    REPEATER_SOUTH(Tile.bit(Tile.NORTH) | Tile.bit(Tile.SOUTH), Tile.SOUTH),
    REPEATER_WEST(Tile.bit(Tile.EAST) | Tile.bit(Tile.WEST), Tile.WEST),
    SOURCE(0, -1),
    LAMP(0, -1),
    OBSIDIAN(0, -1);

    public static final int NORTH = 0;
    public static final int EAST = 1;
    public static final int SOUTH = 2;
    public static final int WEST = 3;
    public static final int[] DX = {0, 1, 0, -1};
    public static final int[] DY = {-1, 0, 1, 0};
    public static final Tile[] WIRES = {HORIZONTAL, VERTICAL, NORTH_EAST, EAST_SOUTH, SOUTH_WEST, WEST_NORTH};

    private final int mask;
    private final int facing;

    Tile(int mask, int facing) {
        this.mask = mask;
        this.facing = facing;
    }

    public static int bit(int direction) {
        return 1 << direction;
    }

    public static int opposite(int direction) {
        return (direction + 2) & 3;
    }

    public static boolean vertical(int direction) {
        return direction == NORTH || direction == SOUTH;
    }

    public static Tile repeater(int facing) {
        return switch (facing) {
            case NORTH -> REPEATER_NORTH;
            case EAST -> REPEATER_EAST;
            case SOUTH -> REPEATER_SOUTH;
            default -> REPEATER_WEST;
        };
    }

    public static Tile wire(int first, int second) {
        int wanted = bit(first) | bit(second);
        for (Tile tile : WIRES) if (tile.mask == wanted) return tile;
        return EMPTY;
    }

    public int mask() {
        return mask;
    }

    public int facing() {
        return facing;
    }

    public boolean opens(int side) {
        return (mask & bit(side)) != 0;
    }

    public boolean repeater() {
        return facing >= 0;
    }

    public boolean playable() {
        return mask != 0;
    }

    public int exit(int travel) {
        if (!playable()) return -1;
        int entry = opposite(travel);
        if (!opens(entry)) return -1;
        if (repeater()) return travel == facing ? facing : -1;
        if (this == CROSS) return travel;
        for (int side = 0; side < 4; side++) if (side != entry && opens(side)) return side;
        return -1;
    }
}
