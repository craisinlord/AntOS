package com.craisinlord.antos.content.merchant;

public final class MerchantAccessTest {
    public static void run() {
        for (boolean alive : new boolean[] {false, true}) {
            for (boolean spectator : new boolean[] {false, true}) {
                check(MerchantAccess.failure("state", alive, spectator).isEmpty(), "Signed-in players can always browse, including Spectator mode");
                for (String action : new String[] {"buy", "publish", "cancel", "deliver"}) {
                    String failure = MerchantAccess.failure(action, alive, spectator);
                    check(failure.isEmpty() == (alive && !spectator), "Only active players can move marketplace property");
                    if (!alive) check(failure.equals("respawn_required"), "Death has a precise respawn message");
                    else if (spectator) check(failure.equals("spectator_restricted"), "Spectator restrictions do not claim the player is signed out");
                }
            }
        }
        check(MerchantAccess.clearAfterRefresh("unauthorized", true, true, true), "Successful authenticated browsing clears old query errors in Spectator mode");
        check(MerchantAccess.clearAfterRefresh("spectator_restricted", false, true, false), "Leaving Spectator mode clears its stale action warning");
        check(!MerchantAccess.clearAfterRefresh("spectator_restricted", false, true, true), "An unresolved action restriction remains visible");
        check(MerchantAccess.clearAfterRefresh("respawn_required", false, true, false), "Respawning clears the obsolete death warning");
        check(!MerchantAccess.clearAfterRefresh("insufficient_payment", false, true, false), "Background refresh does not hide a failed trade's explanation");
        check(!MerchantAccess.clearAfterRefresh("storage_error", false, true, false), "Background refresh preserves unresolved mutation errors");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
