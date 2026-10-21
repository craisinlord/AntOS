package com.craisinlord.antos.content.network;

import net.minecraft.resources.ResourceLocation;

public record AnternetAccountPayload(int action, String username, String password, String extra) {
    public static final int CREATE = 0;
    public static final int LOGIN = 1;
    public static final int LOGOUT = 2;
    public static final int CHANGE_PASSWORD = 3;
    public static final int FACE_ID_LOGIN = 4;
    public static final int FACE_ID_LINK = 5;
    public static final int FACE_ID_UNLINK = 6;
    public static final int FACE_ID_STATUS = 7;
    public static final int SET_HINT = 8;

    public AnternetAccountPayload(int action, String username, String password) {
        this(action, username, password, "");
    }
}
