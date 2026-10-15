package com.craisinlord.antos.content.network;

import net.minecraft.resources.ResourceLocation;

public record AntmailDraftPayload(int action, String data) {
    public static final int SAVE = 0;
    public static final int DELETE = 1;
}


