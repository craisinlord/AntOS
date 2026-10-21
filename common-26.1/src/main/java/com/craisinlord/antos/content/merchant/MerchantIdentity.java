package com.craisinlord.antos.content.merchant;

import com.craisinlord.antos.content.antmail.AntmailAddress;
import com.craisinlord.antos.content.antmail.AntmailProfile;
import com.google.gson.JsonObject;

public record MerchantIdentity(String username, String displayName, String avatarItem, String address) {
    public static MerchantIdentity of(String username, AntmailProfile profile) {
        String account = username == null ? "" : username;
        String name = profile == null || profile.displayName().isBlank() ? account : profile.displayName();
        String avatar = profile == null ? "" : profile.avatarItem();
        String address = AntmailAddress.isValidUsername(account) ? AntmailAddress.ofUsername(account).fullAddress() : account;
        return new MerchantIdentity(account, name.isBlank() ? "?" : name, avatar, address);
    }

    public static MerchantIdentity read(JsonObject profiles, String username) {
        if (profiles == null || !profiles.has(username) || !profiles.get(username).isJsonObject()) return of(username, null);
        JsonObject profile = profiles.getAsJsonObject(username);
        return of(username, new AntmailProfile(text(profile, "displayName"), text(profile, "avatarItem")));
    }

    public JsonObject encode() {
        JsonObject result = new JsonObject();
        result.addProperty("displayName", displayName);
        result.addProperty("avatarItem", avatarItem);
        return result;
    }

    private static String text(JsonObject value, String key) {
        return value.has(key) && value.get(key).isJsonPrimitive() && value.getAsJsonPrimitive(key).isString() ? value.get(key).getAsString() : "";
    }
}
