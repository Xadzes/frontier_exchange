package ru.frontier.exchange.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import ru.frontier.exchange.FrontierExchange;
import ru.frontier.exchange.data.FrontierPlayerData;
import ru.frontier.exchange.data.PlayerProgress;
import ru.frontier.exchange.economy.ExchangeCatalog;
import ru.frontier.exchange.economy.ExchangeContract;

import java.util.ArrayList;
import java.util.List;

public record ExchangeCatalogSyncPayload(List<ContractView> contracts, List<String> openChapters)
        implements CustomPacketPayload {
    public static final Type<ExchangeCatalogSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FrontierExchange.MOD_ID, "exchange_catalog"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ExchangeCatalogSyncPayload> STREAM_CODEC =
            StreamCodec.ofMember(ExchangeCatalogSyncPayload::encode, ExchangeCatalogSyncPayload::decode);

    public static ExchangeCatalogSyncPayload forPlayer(ServerPlayer player) {
        PlayerProgress progress = FrontierPlayerData.get(player.getServer())
                .getOrCreatePlayer(player.getUUID());

        List<ContractView> views = new ArrayList<>();
        for (ExchangeContract contract : ExchangeCatalog.get().getContracts()) {
            views.add(new ContractView(
                    contract.id(),
                    contract.category(),
                    contract.unlockChapter(),
                    contract.input().itemId().toString(),
                    contract.input().count(),
                    contract.output().itemId().toString(),
                    contract.output().count()
            ));
        }
        return new ExchangeCatalogSyncPayload(
                views,
                new ArrayList<>(progress.getOpenChapters())
        );
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(contracts.size());
        for (ContractView contract : contracts) {
            buffer.writeUtf(contract.id(), 64);
            buffer.writeUtf(contract.category(), 64);
            buffer.writeUtf(contract.unlockChapter(), 64);
            buffer.writeUtf(contract.inputItem(), 128);
            buffer.writeVarInt(contract.inputCount());
            buffer.writeUtf(contract.outputItem(), 128);
            buffer.writeVarInt(contract.outputCount());
        }
        buffer.writeVarInt(openChapters.size());
        for (String chapter : openChapters) {
            buffer.writeUtf(chapter, 64);
        }
    }

    private static ExchangeCatalogSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        int contractCount = buffer.readVarInt();
        if (contractCount < 0 || contractCount > 256) {
            throw new IllegalArgumentException("Invalid exchange contract count");
        }
        List<ContractView> contracts = new ArrayList<>(contractCount);
        for (int i = 0; i < contractCount; i++) {
            contracts.add(new ContractView(
                    buffer.readUtf(64),
                    buffer.readUtf(64),
                    buffer.readUtf(64),
                    buffer.readUtf(128),
                    buffer.readVarInt(),
                    buffer.readUtf(128),
                    buffer.readVarInt()
            ));
        }

        int chapterCount = buffer.readVarInt();
        if (chapterCount < 0 || chapterCount > 256) {
            throw new IllegalArgumentException("Invalid chapter count");
        }
        List<String> openChapters = new ArrayList<>(chapterCount);
        for (int i = 0; i < chapterCount; i++) {
            openChapters.add(buffer.readUtf(64));
        }
        return new ExchangeCatalogSyncPayload(contracts, openChapters);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record ContractView(
            String id,
            String category,
            String unlockChapter,
            String inputItem,
            int inputCount,
            String outputItem,
            int outputCount
    ) {
    }
}
