package com.craisinlord.antos.content.client;

import java.util.List;

public final class AntazonTransactionFeedbackTest {
    public static void main(String[] args) {
        check(AntazonTransactionFeedback.purchase(true) == AntazonTransactionFeedback.ORDER, "Confirmed order uses order cue");
        check(AntazonTransactionFeedback.purchase(false) == AntazonTransactionFeedback.ERROR, "Rejected order cannot play success");
        check(AntazonTransactionFeedback.sale(true) == AntazonTransactionFeedback.SALE, "Confirmed sale has its own cue");
        check(AntazonTransactionFeedback.sale(false) == AntazonTransactionFeedback.ERROR, "Rejected sale cannot play success");
        check(AntazonTransactionFeedback.checkout(true, List.of("accepted", "accepted")) == AntazonTransactionFeedback.ORDER, "Multiple accepted lines produce one checkout result");
        check(AntazonTransactionFeedback.checkout(true, List.of("out_of_stock", "accepted", "insufficient_payment")) == AntazonTransactionFeedback.ORDER, "Partially accepted checkout uses order cue");
        check(AntazonTransactionFeedback.checkout(true, List.of("out_of_stock", "insufficient_payment")) == AntazonTransactionFeedback.ERROR, "Fully rejected checkout uses error cue");
        check(AntazonTransactionFeedback.checkout(true, List.of()) == AntazonTransactionFeedback.NONE, "Empty cart is silent");
        check(AntazonTransactionFeedback.checkout(false, List.of()) == AntazonTransactionFeedback.ERROR, "Invalid response uses error cue");
        check(AntazonTransactionFeedback.checkout(false, List.of("accepted")) == AntazonTransactionFeedback.ERROR, "Failed envelope cannot announce an accepted order");
        System.out.println("Antazon transaction feedback checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
