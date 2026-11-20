package ru.frontier.exchange.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import ru.frontier.exchange.FrontierExchange;

import java.util.UUID;

public record ArmoryEnchantRequestPayload(
        int containerId,
        String enchantmentId,
        int targetLevel,
        UUID transactionId
) implements CustomPacketPayload {
    public static final Type<ArmoryEnchantRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FrontierExchange.MOD_ID, "armory_enchant_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ArmoryEnchantRequestPayload> STREAM_CODEC =
            StreamCodec.ofMember(ArmoryEnchantRequestPayload::encode, ArmoryEnchantRequestPayload::decode);

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeUtf(enchantmentId, 128);
        buffer.writeVarInt(targetLevel);
        buffer.writeUUID(transactionId);
    }

    private static ArmoryEnchantRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ArmoryEnchantRequestPayload(
                buffer.readVarInt(),
                buffer.readUtf(128),
                buffer.readVarInt(),
                buffer.readUUID()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
