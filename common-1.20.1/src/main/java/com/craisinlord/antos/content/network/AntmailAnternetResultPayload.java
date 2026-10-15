package com.craisinlord.antos.content.network;

import net.minecraft.resources.ResourceLocation;

public record AntmailAnternetResultPayload(int status, String address, String messageId, String detail, String data, long version) {
    public static final int MAX_DATA_CHARS = 65536;
}
