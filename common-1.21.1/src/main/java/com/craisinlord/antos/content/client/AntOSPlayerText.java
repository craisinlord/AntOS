package com.craisinlord.antos.content.client;

import com.craisinlord.antos.content.network.AnternetAccountResultPayload;
import net.minecraft.client.Minecraft;

public final class AntOSPlayerText {
    public static final String TOKEN = "{player}";

    private AntOSPlayerText() { }

    public static String playerName() {
        AnternetAccountResultPayload account = AnternetAccountClientState.latest();
        if (account != null && account.result() == AnternetAccountResultPayload.SUCCESS
                && account.username() != null && !account.username().isBlank()) {
            return account.username();
        }
        return Minecraft.getInstance().getUser().getName();
    }

    public static String apply(String text) {
        return text == null || !text.contains(TOKEN) ? text : text.replace(TOKEN, playerName());
    }

    public static String currencyName() {
        return net.minecraft.network.chat.Component.translatable("computer.antos.currency.name").getString();
    }

    public static String currencySymbol() {
        return net.minecraft.network.chat.Component.translatable("computer.antos.currency.symbol").getString();
    }
}
