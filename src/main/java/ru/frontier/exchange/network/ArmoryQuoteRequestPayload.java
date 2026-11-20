package ru.frontier.exchange.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.frontier.exchange.FrontierExchange;

public record ArmoryQuoteRequestPayload(int containerId, int requestId, String enchantmentId, int targetLevel)
        implements CustomPacketPayload {
    public static final Type<ArmoryQuoteRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FrontierExchange.MOD_ID, "armory_quote_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ArmoryQuoteRequestPayload> STREAM_CODEC =
            StreamCodec.ofMember(ArmoryQuoteRequestPayload::encode, ArmoryQuoteRequestPayload::decode);

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeVarInt(requestId);
        buffer.writeUtf(enchantmentId, 128);
        buffer.writeVarInt(targetLevel);
    }

    private static ArmoryQuoteRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ArmoryQuoteRequestPayload(
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readUtf(128),
                buffer.readVarInt()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
