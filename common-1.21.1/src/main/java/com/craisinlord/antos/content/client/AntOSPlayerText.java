package com.craisinlord.antos.content.client;

import com.craisinlord.antos.content.network.AnternetAccountResultPayload;
import net.minecraft.client.Minecraft;

/** Client-side {@code {player}} substitution for mail, Archive and Task text. */
public final class AntOSPlayerText {
    public static final String TOKEN = "{player}";

    private AntOSPlayerText() { }

    /** The signed-in Anternet username, or the Minecraft username when no account is signed in. */
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
}
