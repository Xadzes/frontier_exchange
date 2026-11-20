package ru.frontier.exchange.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.frontier.exchange.FrontierExchange;

public record ExchangeResultPayload(boolean success, String message)
        implements CustomPacketPayload {
    public static final Type<ExchangeResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FrontierExchange.MOD_ID, "exchange_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ExchangeResultPayload> STREAM_CODEC =
            StreamCodec.ofMember(ExchangeResultPayload::encode, ExchangeResultPayload::decode);

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(success);
        buffer.writeUtf(message, 128);
    }

    private static ExchangeResultPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ExchangeResultPayload(buffer.readBoolean(), buffer.readUtf(128));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
