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

    private static Object currencyLanguage;
    private static String currencyName;
    private static String currencySymbol;

    public static String currencyName() {
        refreshCurrency();
        return currencyName;
    }

    public static String currencySymbol() {
        refreshCurrency();
        return currencySymbol;
    }

    private static void refreshCurrency() {
        Object language = net.minecraft.locale.Language.getInstance();
        if (currencyLanguage == language && currencyName != null) return;
        currencyName = net.minecraft.network.chat.Component.translatable("computer.antos.currency.name").getString();
        currencySymbol = net.minecraft.network.chat.Component.translatable("computer.antos.currency.symbol").getString();
        currencyLanguage = language;
    }
}
