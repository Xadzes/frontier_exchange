package ru.frontier.exchange.client;

import ru.frontier.exchange.network.ArmoryQuoteResponsePayload;
import ru.frontier.exchange.network.ArmoryTransactionResultPayload;

public final class ArmoryClientState {
    private static ArmoryQuoteResponsePayload quote;
    private static ArmoryTransactionResultPayload transactionResult;
    private static boolean transactionPending;

    private ArmoryClientState() {
    }

    public static void setQuote(ArmoryQuoteResponsePayload quote) {
        ArmoryClientState.quote = quote;
    }

    public static ArmoryQuoteResponsePayload getQuote() {
        return quote;
    }

    public static void beginTransaction() {
        transactionPending = true;
    }

    public static boolean isTransactionPending() {
        return transactionPending;
    }

    public static void setTransactionResult(ArmoryTransactionResultPayload result) {
        transactionResult = result;
        transactionPending = false;
    }

    public static ArmoryTransactionResultPayload getTransactionResult() {
        return transactionResult;
    }

    public static void clear() {
        quote = null;
        transactionResult = null;
        transactionPending = false;
    }
}
