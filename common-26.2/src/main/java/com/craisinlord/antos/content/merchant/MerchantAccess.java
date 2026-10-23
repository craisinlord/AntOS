package com.craisinlord.antos.content.merchant;

public final class MerchantAccess {
    private MerchantAccess() { }

    public static String failure(String action, boolean alive, boolean spectator) {
        if (action.equals("state")) return "";
        if (!alive) return "respawn_required";
        return spectator ? "spectator_restricted" : "";
    }

    public static boolean clearAfterRefresh(String code, boolean queryFailure, boolean alive, boolean spectator) {
        return queryFailure || (code.equals("respawn_required") || code.equals("spectator_restricted") || code.equals("unavailable"))
                && failure("buy", alive, spectator).isEmpty();
    }
}
