package ru.frontier.exchange.client;

import ru.frontier.exchange.network.ExchangeCatalogSyncPayload;
import ru.frontier.exchange.network.ExchangeResultPayload;

import java.util.ArrayList;
import java.util.List;

public final class ResourceExchangeClientCatalog {
    private static List<ExchangeCatalogSyncPayload.ContractView> contracts = new ArrayList<>();
    private static List<String> openChapters = new ArrayList<>();
    private static ExchangeResultPayload result;

    private ResourceExchangeClientCatalog() {
    }

    public static void set(ExchangeCatalogSyncPayload payload) {
        contracts = List.copyOf(payload.contracts());
        openChapters = List.copyOf(payload.openChapters());
    }

    public static void setResult(ExchangeResultPayload payload) {
        result = payload;
    }

    public static List<ExchangeCatalogSyncPayload.ContractView> getContracts() {
        return contracts;
    }

    public static List<String> getOpenChapters() {
        return openChapters;
    }

    public static ExchangeResultPayload getResult() {
        return result;
    }
}
