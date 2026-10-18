package com.craisinlord.antos.content.client;

enum AntazonTransactionFeedback {
    NONE, ORDER, SALE, ERROR;

    static AntazonTransactionFeedback purchase(boolean success) {
        return success ? ORDER : ERROR;
    }

    static AntazonTransactionFeedback sale(boolean success) {
        return success ? SALE : ERROR;
    }

    static AntazonTransactionFeedback checkout(boolean valid, Iterable<String> statuses) {
        if (!valid) return ERROR;
        boolean attempted = false;
        for (String status : statuses) {
            attempted = true;
            if ("accepted".equals(status)) return ORDER;
        }
        return attempted ? ERROR : NONE;
    }
}
