package com.craisinlord.antos.content.client.game.chess;

public enum ChessDifficulty {
    EASY("easy", 1, 0.3, 80),
    NORMAL("normal", 2, 0.06, 25),
    HARD("hard", 3, 0.0, 8),
    EXPERT("expert", 4, 0.0, 0);

    private final String id;
    private final int depth;
    private final double blunderChance;
    private final int noise;

    ChessDifficulty(String id, int depth, double blunderChance, int noise) {
        this.id = id;
        this.depth = depth;
        this.blunderChance = blunderChance;
        this.noise = noise;
    }

    public String id() {
        return id;
    }

    public String rewardMode() {
        return "chess_" + id;
    }

    public int depth() {
        return depth;
    }

    public double blunderChance() {
        return blunderChance;
    }

    public int noise() {
        return noise;
    }
}
