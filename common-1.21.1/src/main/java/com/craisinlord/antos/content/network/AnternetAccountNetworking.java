package com.craisinlord.antos.content.network;

import java.util.function.Consumer;

public final class AnternetAccountNetworking {
    private static Consumer<AnternetAccountPayload> sender = payload -> { };

    private AnternetAccountNetworking() { }

    public static void setSender(Consumer<AnternetAccountPayload> sender) {
        AnternetAccountNetworking.sender = sender;
    }

    public static void create(String username, String password) {
        sender.accept(new AnternetAccountPayload(AnternetAccountPayload.CREATE, username, password));
    }

    public static void login(String username, String password) {
        sender.accept(new AnternetAccountPayload(AnternetAccountPayload.LOGIN, username, password));
    }

    public static void faceIdLogin() {
        sender.accept(new AnternetAccountPayload(AnternetAccountPayload.FACE_ID_LOGIN, "", ""));
    }

    public static void checkFaceId() {
        sender.accept(new AnternetAccountPayload(AnternetAccountPayload.FACE_ID_STATUS, "", ""));
    }

    public static void linkFaceId(String password) {
        sender.accept(new AnternetAccountPayload(AnternetAccountPayload.FACE_ID_LINK, "", password));
    }

    public static void unlinkFaceId() {
        sender.accept(new AnternetAccountPayload(AnternetAccountPayload.FACE_ID_UNLINK, "", ""));
    }

    public static void changePassword(String newPassword) {
        sender.accept(new AnternetAccountPayload(AnternetAccountPayload.CHANGE_PASSWORD, "", newPassword));
    }

    public static void logout() {
        sender.accept(new AnternetAccountPayload(AnternetAccountPayload.LOGOUT, "", ""));
    }
}
