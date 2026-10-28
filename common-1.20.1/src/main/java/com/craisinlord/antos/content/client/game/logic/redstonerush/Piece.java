package com.craisinlord.antos.content.client.game.logic.redstonerush;

public enum Piece {
    STRAIGHT(0, Tile.HORIZONTAL, Tile.VERTICAL),
    CORNER(0, Tile.NORTH_EAST, Tile.EAST_SOUTH, Tile.SOUTH_WEST, Tile.WEST_NORTH),
    CROSS(0, Tile.CROSS),
    REPEATER(1, Tile.REPEATER_NORTH, Tile.REPEATER_EAST, Tile.REPEATER_SOUTH, Tile.REPEATER_WEST);

    private final int defaultRotation;
    private final Tile[] tiles;

    Piece(int defaultRotation, Tile... tiles) {
        this.defaultRotation = defaultRotation;
        this.tiles = tiles;
    }

    public int orientations() {
        return tiles.length;
    }

    public int defaultRotation() {
        return defaultRotation;
    }

    public Tile tile(int rotation) {
        return tiles[Math.floorMod(rotation, tiles.length)];
    }

    public static Piece of(Tile tile) {
        for (Piece piece : values()) for (Tile option : piece.tiles) if (option == tile) return piece;
        return null;
    }
}
