package com.craisinlord.antos.content.network;

import net.minecraft.resources.ResourceLocation;

public record AntmailStateRequestPayload(int folder, int page, long knownVersion) {
    public static final int INBOX = 0;
    public static final int SENT = 1;
    public static final int DRAFTS = 2;
    public static final int ARCHIVE = 3;
    public static final int TRASH = 4;
}


