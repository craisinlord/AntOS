package com.craisinlord.antos.content.network;

import java.util.List;

public final class AntmailPayloadTypes {
    private AntmailPayloadTypes() {
    }

    public static List<Class<?>> all() {
        return List.of(AntmailAnternetPayload.class, AntmailAnternetResultPayload.class);
    }
}