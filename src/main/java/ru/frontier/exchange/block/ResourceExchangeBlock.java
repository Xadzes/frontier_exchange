package ru.frontier.exchange.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.frontier.exchange.menu.ExchangeMenu;
import ru.frontier.exchange.network.ExchangeCatalogSyncPayload;

public class ResourceExchangeBlock extends Block {
    public ResourceExchangeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, opener) ->
                            new ExchangeMenu(containerId, inventory),
                    Component.translatable("screen.frontier_exchange.resource_exchange")
            ));

            PacketDistributor.sendToPlayer(
                    serverPlayer,
                    ExchangeCatalogSyncPayload.forPlayer(serverPlayer)
            );
        }
        return InteractionResult.CONSUME;
    }
}
