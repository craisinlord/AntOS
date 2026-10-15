package com.craisinlord.antos.content.network;

import net.minecraft.resources.ResourceLocation;

public record AntmailDeletePayload(String messageId, boolean sent) {
}


