package com.craisinlord.antos.content.network;

import net.minecraft.resources.ResourceLocation;

public record AnternetComputerResultPayload(int result, boolean hasPassword, boolean authenticated, String data) {
}
