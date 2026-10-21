package com.craisinlord.antos.content.network;

public record JeiSyncPayload(int kind, long version, int part, int parts, byte[] data) {
    public static final int DEFINITIONS = 0;
    public static final int STATE = 1;
    public static final int MAX_CHUNK = 900_000;
}
