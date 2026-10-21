package com.craisinlord.antos.content.merchant;

import com.craisinlord.antos.content.antmail.AntmailProfile;
import com.google.gson.JsonObject;

public final class MerchantIdentityTest {
    public static void run() {
        MerchantIdentity fallback = MerchantIdentity.read(null, "alice");
        check(fallback.displayName().equals("alice") && fallback.avatarItem().isEmpty(), "Legacy listings retain username and initials fallback");
        check(fallback.address().equals("alice@antmail.com"), "Stable account address is independent of display names");
        MerchantIdentity seller = MerchantIdentity.of("alice", new AntmailProfile("Alice's Shop", "minecraft:emerald"));
        JsonObject profiles = new JsonObject();
        profiles.add("alice", seller.encode());
        check(MerchantIdentity.read(profiles, "alice").equals(seller), "Public profile survives the marketplace response");
        check(MerchantIdentity.read(profiles, "bob").displayName().equals("bob"), "Missing profiles never reuse another seller's identity");
        profiles.add("alice", MerchantIdentity.of("alice", new AntmailProfile("New Shop", "minecraft:diamond")).encode());
        MerchantIdentity updated = MerchantIdentity.read(profiles, "alice");
        check(updated.displayName().equals("New Shop") && updated.avatarItem().equals("minecraft:diamond"), "Refreshed profiles replace old name and avatar");
        check(updated.username().equals("alice") && updated.address().equals(seller.address()), "Profile edits preserve the seller's account identity");
        check(MerchantIdentity.of("old user", null).address().equals("old user"), "Invalid legacy account labels remain readable");
        check(MerchantIdentity.of(null, null).displayName().equals("?"), "Missing identity has a safe initials fallback");
        profiles.add("alice", new JsonObject());
        check(MerchantIdentity.read(profiles, "alice").equals(fallback), "Empty profiles fall back to the account name");
        profiles.getAsJsonObject("alice").addProperty("displayName", 123);
        profiles.getAsJsonObject("alice").add("avatarItem", new JsonObject());
        check(MerchantIdentity.read(profiles, "alice").equals(fallback), "Malformed optional profile fields cannot break listings");
        check(MerchantIdentity.of("alice", new AntmailProfile("   ", null)).equals(fallback), "Blank names and avatars use the mail fallback");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
