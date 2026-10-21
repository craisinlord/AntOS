package com.craisinlord.antos.content.client;

import com.craisinlord.antos.AntOS;
import com.craisinlord.antos.content.guide.CustomDisplays;
import com.craisinlord.antos.content.network.DisplaySyncPayload;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

public final class DisplaySyncClientState {
    private static long version = Long.MIN_VALUE;
    private static byte[][] parts;

    private DisplaySyncClientState() { }

    public static void clear() {
        version = Long.MIN_VALUE;
        parts = null;
    }

    public static void update(DisplaySyncPayload payload) {
        if (payload.parts() < 1 || payload.parts() > DisplaySyncPayload.MAX_PARTS || payload.part() < 0 || payload.part() >= payload.parts()) return;
        if (parts == null || version != payload.version() || parts.length != payload.parts()) {
            version = payload.version();
            parts = new byte[payload.parts()][];
        }
        parts[payload.part()] = payload.data();
        for (byte[] part : parts) if (part == null) return;
        byte[][] complete = parts;
        parts = null;
        if (Minecraft.getInstance().hasSingleplayerServer()) return;
        try {
            CustomDisplays.apply(CustomDisplays.decode(JsonParser.parseString(decompress(complete))));
        } catch (IOException | RuntimeException exception) {
            AntOS.LOGGER.warn("Ignoring unreadable AntOS display data", exception);
        }
    }

    private static String decompress(byte[][] chunks) throws IOException {
        ByteArrayOutputStream joined = new ByteArrayOutputStream();
        for (byte[] part : chunks) joined.write(part);
        try (InputStream input = new GZIPInputStream(new ByteArrayInputStream(joined.toByteArray()))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
