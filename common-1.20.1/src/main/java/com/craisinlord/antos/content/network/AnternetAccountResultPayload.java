package com.craisinlord.antos.content.network;

import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record AnternetAccountResultPayload(int result, UUID accountId, UUID workspaceId, String username) {
    public static final int SUCCESS = 0;
    public static final int INVALID_REQUEST = 1;
    public static final int USERNAME_TAKEN = 2;
    public static final int INVALID_CREDENTIALS = 3;
    public static final int LOGGED_OUT = 5;
    public static final int RATE_LIMITED = 6;
    public static final int FACE_ID_STATUS = 7;
    public static final int FACE_ID_CONFLICT = 8;
    public static final int FACE_ID_PASSWORD_INVALID = 9;
    public static final int FACE_ID_RATE_LIMITED = 10;
    public static final int LOGIN_REQUIRED = 11;
    public static final int PASSWORD_CHANGED = 12;
    public static final int PASSWORD_CHANGE_DENIED = 13;
    public static final int PASSWORD_CHANGE_INVALID = 14;
    public static final int PASSWORD_CHANGE_RATE_LIMITED = 15;
    public static final int HINT_SAVED = 16;
    public static final int HINT_DENIED = 17;
    public static final int HINT_INVALID = 18;
    public static final int PASSWORD_CHANGE_REQUIRED = 19;
    public static final int HINT_RATE_LIMITED = 20;
    public static final UUID EMPTY_ID = new UUID(0L, 0L);

}
