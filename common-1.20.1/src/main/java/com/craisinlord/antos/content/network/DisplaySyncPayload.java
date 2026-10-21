package com.craisinlord.antos.content.network;

public record DisplaySyncPayload(long version, int part, int parts, byte[] data) {
    public static final int MAX_CHUNK = 900_000;
    public static final int MAX_PARTS = 64;
}
