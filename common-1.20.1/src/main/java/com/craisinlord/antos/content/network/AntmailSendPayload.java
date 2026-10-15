package com.craisinlord.antos.content.network;

import net.minecraft.resources.ResourceLocation;

public record AntmailSendPayload(String recipient, String subject, String body, String attachments) {
}


