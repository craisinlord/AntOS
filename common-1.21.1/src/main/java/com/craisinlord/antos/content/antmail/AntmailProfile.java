package com.craisinlord.antos.content.antmail;

public record AntmailProfile(String displayName, String avatarItem) {
    public AntmailProfile {
        displayName = displayName == null ? "" : displayName;
        avatarItem = avatarItem == null ? "" : avatarItem;
    }
}
