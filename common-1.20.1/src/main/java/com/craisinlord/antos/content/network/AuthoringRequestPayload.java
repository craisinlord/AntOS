package com.craisinlord.antos.content.network;

import com.craisinlord.antos.content.editor.AuthoringWire;

public record AuthoringRequestPayload(String id, int index, int total, String data) {
    public AuthoringWire.Chunk chunk() { return new AuthoringWire.Chunk(id, index, total, data); }
}
