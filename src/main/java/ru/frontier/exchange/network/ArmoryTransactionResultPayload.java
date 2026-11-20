package ru.frontier.exchange.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.frontier.exchange.FrontierExchange;

public record ArmoryTransactionResultPayload(boolean success, String message)
        implements CustomPacketPayload {
    public static final Type<ArmoryTransactionResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FrontierExchange.MOD_ID, "armory_transaction_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ArmoryTransactionResultPayload> STREAM_CODEC =
            StreamCodec.ofMember(ArmoryTransactionResultPayload::encode, ArmoryTransactionResultPayload::decode);

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(success);
        buffer.writeUtf(message, 256);
    }

    private static ArmoryTransactionResultPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ArmoryTransactionResultPayload(buffer.readBoolean(), buffer.readUtf(256));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
