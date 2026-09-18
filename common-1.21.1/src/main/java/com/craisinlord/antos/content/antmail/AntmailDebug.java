package com.craisinlord.antos.content.antmail;

import com.craisinlord.antos.AntOS;

public final class AntmailDebug {
    private static final String PROPERTY = "antos.antmail.debug";

    private AntmailDebug() {
    }

    public static void log(String message) {
        if (Boolean.getBoolean(PROPERTY)) AntOS.LOGGER.info("[AntMail debug] {}", message);
    }

    public static void error(String message, Throwable throwable) {
        AntOS.LOGGER.error("[AntMail] {}", message, throwable);
    }
}


