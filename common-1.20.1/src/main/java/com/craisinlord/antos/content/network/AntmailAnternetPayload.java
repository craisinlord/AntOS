package com.craisinlord.antos.content.network;

import net.minecraft.resources.ResourceLocation;

public record AntmailAnternetPayload(int action, String first, String second, String third, String fourth, String fifth,
                                    long number, long version) {
    public static final int STATE = 1;
    public static final int MESSAGE = 2;
    public static final int SEND = 3;
    public static final int READ = 4;
    public static final int DELETE = 5;
    public static final int DRAFT = 6;
    public static final int RETRY = 7;
    public static final int ARCHIVE = 8;
    public static final int TRASH = 9;
    public static final int RESTORE = 10;
    public static final int PERMANENT_DELETE = 11;
    public static final int UNARCHIVE = 12;
    public static final int UPDATE_PROFILE = 13;
    public static final int MAX_FIELD_CHARS = 65536;


    public boolean fits() {
        return fits(first) && fits(second) && fits(third) && fits(fourth) && fits(fifth);
    }

    private static boolean fits(String value) {
        return value != null && value.length() <= MAX_FIELD_CHARS;
    }
}
