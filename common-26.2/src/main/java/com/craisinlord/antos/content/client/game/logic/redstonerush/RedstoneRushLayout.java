package com.craisinlord.antos.content.client.game.logic.redstonerush;

import java.util.ArrayList;
import java.util.List;

public final class RedstoneRushLayout {
    private final int columns;
    private final int rows;
    private final Tile[][] tiles;
    private final int sourceX;
    private final int sourceY;
    private final int sourceDirection;
    private final int lampX;
    private final int lampY;
    private final int lampSide;
    private final List<Placement> solution;

    public RedstoneRushLayout(int columns, int rows, int sourceX, int sourceY, int sourceDirection, int lampX, int lampY, int lampSide) {
        this.columns = columns;
        this.rows = rows;
        this.tiles = new Tile[columns][rows];
        for (Tile[] column : tiles) java.util.Arrays.fill(column, Tile.EMPTY);
        this.sourceX = sourceX;
        this.sourceY = sourceY;
        this.sourceDirection = sourceDirection;
        this.lampX = lampX;
        this.lampY = lampY;
        this.lampSide = lampSide;
        this.solution = new ArrayList<>();
        tiles[sourceX][sourceY] = Tile.SOURCE;
        tiles[lampX][lampY] = Tile.LAMP;
    }

    public RedstoneRushLayout obsidian(int x, int y) {
        if (tiles[x][y] == Tile.EMPTY) tiles[x][y] = Tile.OBSIDIAN;
        return this;
    }

    void solution(List<Placement> path) {
        solution.clear();
        solution.addAll(path);
    }

    public int columns() { return columns; }
    public int rows() { return rows; }
    public Tile tile(int x, int y) { return tiles[x][y]; }
    public int sourceX() { return sourceX; }
    public int sourceY() { return sourceY; }
    public int sourceDirection() { return sourceDirection; }
    public int lampX() { return lampX; }
    public int lampY() { return lampY; }
    public int lampSide() { return lampSide; }
    public List<Placement> solution() { return List.copyOf(solution); }

    public int obsidianCount() {
        int count = 0;
        for (Tile[] column : tiles) for (Tile tile : column) if (tile == Tile.OBSIDIAN) count++;
        return count;
    }

    public record Placement(int x, int y, Tile tile) { }
}
