package com.craisinlord.antos.content.client.game.logic.pistonpush;

import java.util.ArrayDeque;
import java.util.Arrays;

public final class PistonPushBoard {
    public static final int MAX_WIDTH = 14;
    public static final int MAX_HEIGHT = 10;

    public enum Terrain { VOID, WALL, FLOOR, PLATE, ICE }

    public enum Block { NONE, BASIC, SLIME, HONEY }

    private final int width;
    private final int height;
    private final Terrain[] terrain;
    private final Block[] blocks;
    private int player;

    private PistonPushBoard(int width, int height, Terrain[] terrain, Block[] blocks, int player) {
        this.width = width;
        this.height = height;
        this.terrain = terrain;
        this.blocks = blocks;
        this.player = player;
    }

    public static PistonPushBoard parse(String... rows) {
        if (rows == null || rows.length == 0) throw new IllegalArgumentException("level is empty");
        int height = rows.length;
        int width = 0;
        for (String row : rows) {
            if (row == null) throw new IllegalArgumentException("level has a missing row");
            width = Math.max(width, row.length());
        }
        if (width == 0) throw new IllegalArgumentException("level is empty");
        if (width > MAX_WIDTH) throw new IllegalArgumentException("level is wider than " + MAX_WIDTH);
        if (height > MAX_HEIGHT) throw new IllegalArgumentException("level is taller than " + MAX_HEIGHT);
        char[] tiles = new char[width * height];
        Arrays.fill(tiles, '\0');
        int player = -1;
        for (int y = 0; y < height; y++) {
            String row = rows[y];
            for (int x = 0; x < row.length(); x++) {
                char tile = row.charAt(x);
                if ("# .@+$*SsHh~".indexOf(tile) < 0) throw new IllegalArgumentException("unknown tile '" + tile + "' at " + x + "," + y);
                if (tile == '@' || tile == '+') {
                    if (player >= 0) throw new IllegalArgumentException("level has more than one player");
                    player = y * width + x;
                }
                tiles[y * width + x] = tile;
            }
        }
        if (player < 0) throw new IllegalArgumentException("level has no player");
        boolean[] inside = new boolean[width * height];
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        pending.add(player);
        inside[player] = true;
        int[] dx = PistonPushRules.DX;
        int[] dy = PistonPushRules.DY;
        while (!pending.isEmpty()) {
            int cell = pending.poll();
            int cx = cell % width;
            int cy = cell / width;
            for (int direction = 0; direction < 4; direction++) {
                int nx = cx + dx[direction];
                int ny = cy + dy[direction];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) throw new IllegalArgumentException("level is not enclosed by walls");
                int next = ny * width + nx;
                char tile = tiles[next];
                if (tile == '\0') throw new IllegalArgumentException("level is not enclosed by walls");
                if (tile == '#' || inside[next]) continue;
                inside[next] = true;
                pending.add(next);
            }
        }
        Terrain[] terrain = new Terrain[width * height];
        Block[] blocks = new Block[width * height];
        int plates = 0;
        int count = 0;
        int covered = 0;
        for (int cell = 0; cell < tiles.length; cell++) {
            char tile = tiles[cell];
            blocks[cell] = Block.NONE;
            if (tile == '#') {
                terrain[cell] = Terrain.WALL;
                continue;
            }
            if (!inside[cell]) {
                if (tile != ' ' && tile != '\0') throw new IllegalArgumentException("tile '" + tile + "' outside the walls at " + cell % width + "," + cell / width);
                terrain[cell] = Terrain.VOID;
                continue;
            }
            terrain[cell] = switch (tile) {
                case '.', '+', '*', 's', 'h' -> Terrain.PLATE;
                case '~' -> Terrain.ICE;
                default -> Terrain.FLOOR;
            };
            blocks[cell] = switch (tile) {
                case '$', '*' -> Block.BASIC;
                case 'S', 's' -> Block.SLIME;
                case 'H', 'h' -> Block.HONEY;
                default -> Block.NONE;
            };
            if (terrain[cell] == Terrain.PLATE) plates++;
            if (blocks[cell] != Block.NONE) count++;
            if (terrain[cell] == Terrain.PLATE && blocks[cell] != Block.NONE) covered++;
        }
        if (plates == 0) throw new IllegalArgumentException("level has no pressure plates");
        if (count < plates) throw new IllegalArgumentException("level has fewer blocks than plates");
        if (covered == plates) throw new IllegalArgumentException("level starts solved");
        return new PistonPushBoard(width, height, terrain, blocks, player);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int player() {
        return player;
    }

    public int playerX() {
        return player % width;
    }

    public int playerY() {
        return player / width;
    }

    public boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    public Terrain terrain(int x, int y) {
        return inBounds(x, y) ? terrain[y * width + x] : Terrain.VOID;
    }

    public Block block(int x, int y) {
        return inBounds(x, y) ? blocks[y * width + x] : Block.NONE;
    }

    public boolean solid(int x, int y) {
        Terrain tile = terrain(x, y);
        return tile == Terrain.WALL || tile == Terrain.VOID;
    }

    public int blockCount() {
        int count = 0;
        for (Block block : blocks) if (block != Block.NONE) count++;
        return count;
    }

    public int plateCount() {
        int count = 0;
        for (Terrain tile : terrain) if (tile == Terrain.PLATE) count++;
        return count;
    }

    public int coveredPlates() {
        int count = 0;
        for (int cell = 0; cell < terrain.length; cell++) if (terrain[cell] == Terrain.PLATE && blocks[cell] != Block.NONE) count++;
        return count;
    }

    public int floorCount() {
        int count = 0;
        for (Terrain tile : terrain) if (tile == Terrain.FLOOR || tile == Terrain.PLATE || tile == Terrain.ICE) count++;
        return count;
    }

    public boolean solved() {
        for (int cell = 0; cell < terrain.length; cell++) if (terrain[cell] == Terrain.PLATE && blocks[cell] == Block.NONE) return false;
        return true;
    }

    public Snapshot snapshot() {
        return new Snapshot(player, blocks.clone());
    }

    public void restore(Snapshot snapshot) {
        if (snapshot.blocks.length != blocks.length) throw new IllegalArgumentException("snapshot is for another board");
        player = snapshot.player;
        System.arraycopy(snapshot.blocks, 0, blocks, 0, blocks.length);
    }

    public PistonPushBoard copy() {
        return new PistonPushBoard(width, height, terrain, blocks.clone(), player);
    }

    Block[] blocks() {
        return blocks;
    }

    Terrain[] terrain() {
        return terrain;
    }

    void setPlayer(int cell) {
        player = cell;
    }

    public void setState(int playerCell, int[] cells, Block[] types) {
        Arrays.fill(blocks, Block.NONE);
        for (int index = 0; index < cells.length; index++) blocks[cells[index]] = types[index];
        player = playerCell;
    }

    public static final class Snapshot {
        private final int player;
        private final Block[] blocks;

        private Snapshot(int player, Block[] blocks) {
            this.player = player;
            this.blocks = blocks;
        }

        public int player() {
            return player;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Snapshot snapshot && snapshot.player == player && Arrays.equals(snapshot.blocks, blocks);
        }

        @Override
        public int hashCode() {
            return 31 * player + Arrays.hashCode(blocks);
        }
    }
}
