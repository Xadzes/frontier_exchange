package ru.frontier.exchange.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.frontier.exchange.FrontierExchange;

public record ExchangeExecutePayload(String contractId, int operations)
        implements CustomPacketPayload {
    public static final Type<ExchangeExecutePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FrontierExchange.MOD_ID, "exchange_execute"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ExchangeExecutePayload> STREAM_CODEC =
            StreamCodec.ofMember(ExchangeExecutePayload::encode, ExchangeExecutePayload::decode);

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(contractId, 64);
        buffer.writeVarInt(operations);
    }

    private static ExchangeExecutePayload decode(RegistryFriendlyByteBuf buffer) {
        return new ExchangeExecutePayload(buffer.readUtf(64), buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
