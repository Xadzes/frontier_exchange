package ru.frontier.exchange.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.frontier.exchange.FrontierExchange;
import ru.frontier.exchange.economy.ArmoryEnchantingService;

import java.util.ArrayList;
import java.util.List;

public record ArmoryQuoteResponsePayload(
        int requestId,
        boolean valid,
        boolean affordable,
        String message,
        String enchantmentId,
        int targetLevel,
        List<PriceItem> price
) implements CustomPacketPayload {
    public static final Type<ArmoryQuoteResponsePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FrontierExchange.MOD_ID, "armory_quote_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ArmoryQuoteResponsePayload> STREAM_CODEC =
            StreamCodec.ofMember(ArmoryQuoteResponsePayload::encode, ArmoryQuoteResponsePayload::decode);

    public static ArmoryQuoteResponsePayload from(int requestId, ArmoryEnchantingService.Quote quote) {
        List<PriceItem> price = quote.price().stream()
                .map(item -> new PriceItem(item.itemId().toString(), item.count()))
                .toList();
        return new ArmoryQuoteResponsePayload(
                requestId, quote.valid(), quote.affordable(), quote.message(),
                quote.enchantmentId(), quote.targetLevel(), price
        );
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(requestId);
        buffer.writeBoolean(valid);
        buffer.writeBoolean(affordable);
        buffer.writeUtf(message, 256);
        buffer.writeUtf(enchantmentId, 128);
        buffer.writeVarInt(targetLevel);
        buffer.writeVarInt(price.size());
        for (PriceItem item : price) {
            buffer.writeUtf(item.itemId(), 128);
            buffer.writeVarInt(item.count());
        }
    }

    private static ArmoryQuoteResponsePayload decode(RegistryFriendlyByteBuf buffer) {
        int requestId = buffer.readVarInt();
        boolean valid = buffer.readBoolean();
        boolean affordable = buffer.readBoolean();
        String message = buffer.readUtf(256);
        String enchantmentId = buffer.readUtf(128);
        int targetLevel = buffer.readVarInt();
        int count = buffer.readVarInt();
        if (count < 0 || count > 32) {
            throw new IllegalArgumentException("Invalid armory quote item count");
        }
        List<PriceItem> price = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            price.add(new PriceItem(buffer.readUtf(128), buffer.readVarInt()));
        }
        return new ArmoryQuoteResponsePayload(
                requestId, valid, affordable, message, enchantmentId, targetLevel, price);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record PriceItem(String itemId, int count) {
    }
}
