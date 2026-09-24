package com.craisinlord.antos.content.client;

import com.craisinlord.antos.content.network.AnternetAccountResultPayload;

public final class AnternetAccountClientState {
    private static volatile AnternetAccountResultPayload latest;
    private static volatile String faceIdUsername = "";
    private static volatile String faceIdError = "";
    private static volatile long faceIdStatusRevision;
    private static volatile java.util.UUID faceIdAccountId;

    private AnternetAccountClientState() { }

    public static void update(AnternetAccountResultPayload result) {
        if (result.result() == AnternetAccountResultPayload.LOGIN_REQUIRED) {
            latest = null;
            AntOSClientHooks.openComputer();
            return;
        }
        if (result.result() == AnternetAccountResultPayload.FACE_ID_STATUS) {
            faceIdUsername = result.username() == null ? "" : result.username();
            faceIdAccountId = faceIdUsername.isBlank() ? null : result.accountId();
            faceIdError = "";
            faceIdStatusRevision++;
            return;
        }
        if (result.result() == AnternetAccountResultPayload.FACE_ID_CONFLICT
                || result.result() == AnternetAccountResultPayload.FACE_ID_PASSWORD_INVALID
                || result.result() == AnternetAccountResultPayload.FACE_ID_RATE_LIMITED) {
            faceIdError = switch (result.result()) {
                case AnternetAccountResultPayload.FACE_ID_CONFLICT -> "THIS PLAYER OR ACCOUNT IS ALREADY LINKED";
                case AnternetAccountResultPayload.FACE_ID_PASSWORD_INVALID -> "PASSWORD NOT RECOGNIZED";
                default -> "PLEASE WAIT BEFORE TRYING AGAIN";
            };
            faceIdStatusRevision++;
            return;
        }
        if (result.result() == AnternetAccountResultPayload.LOGGED_OUT) {
            clear();
            return;
        }
        latest = result;
    }

    public static AnternetAccountResultPayload latest() {
        return latest;
    }

    public static boolean faceIdLinked() {
        return !faceIdUsername.isBlank();
    }

    public static String faceIdUsername() {
        return faceIdUsername;
    }

    public static java.util.UUID faceIdAccountId() {
        return faceIdAccountId;
    }

    public static long faceIdStatusRevision() {
        return faceIdStatusRevision;
    }

    public static String faceIdError() {
        return faceIdError;
    }

    public static void clear() {
        latest = null;
        faceIdUsername = "";
        faceIdAccountId = null;
        faceIdError = "";
        faceIdStatusRevision++;
        ComputerAccessClientState.clearAll();
        AntmailClientState.clear();
        com.craisinlord.antos.content.client.screen.ComputerScreen.clearCachedSessions();
    }

    public static void clearResult() {
        latest = null;
    }
}
