package com.craisinlord.antos.content.network;

import net.minecraft.resources.ResourceLocation;

public record AntmailReadPayload(String messageId, boolean read) {
}


