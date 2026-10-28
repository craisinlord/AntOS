package com.craisinlord.antos.content.computer.games;

import java.util.HashMap;
import java.util.Map;

public final class GameDataCodecTest {
    public static void main(String[] args) {
        check(GameDataCodec.validKey("flappy_ghast.best"), "game key is valid");
        check(!GameDataCodec.validKey("Bad Key"), "uppercase and spaces are rejected");
        check(!GameDataCodec.validKey(""), "empty key is rejected");
        check(!GameDataCodec.validValue("a\nb"), "newlines are rejected");
        check(GameDataCodec.parseRequest("get")[0].equals("get"), "get parses");
        check(GameDataCodec.parseRequest(GameDataCodec.request("best", "x.best", "nope")) == null, "best needs a number");
        check(GameDataCodec.parseRequest("put\0x\0y\0z") == null, "extra fields are rejected");
        Map<String, String> entries = new HashMap<>();
        check(GameDataCodec.apply(entries, "best", "x.best", "10"), "first best is stored");
        check(!GameDataCodec.apply(entries, "best", "x.best", "5"), "lower best is ignored");
        check(GameDataCodec.apply(entries, "best", "x.best", "12"), "higher best replaces");
        check(GameDataCodec.apply(entries, "put", "p.progress", "w1_01=4"), "put stores");
        check(!GameDataCodec.apply(entries, "put", "p.progress", "w1_01=4"), "same value is not a change");
        Map<String, String> decoded = GameDataCodec.decode(GameDataCodec.encode(entries));
        check(decoded.equals(entries), "encode and decode round trip");
        check(GameDataCodec.apply(entries, "put", "p.progress", ""), "empty put removes");
        check(!entries.containsKey("p.progress"), "removed key is gone");
        Map<String, String> full = new HashMap<>();
        for (int i = 0; i < GameDataCodec.MAX_ENTRIES; i++) full.put("k" + i, "v");
        check(!GameDataCodec.apply(full, "put", "extra", "v"), "entry cap holds");
        check(GameDataCodec.decode("=x\nok=1\nBAD=2").size() == 1, "garbage lines are skipped");
        System.out.println("GameDataCodecTest passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
