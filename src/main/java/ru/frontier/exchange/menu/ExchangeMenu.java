package ru.frontier.exchange.menu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.RegistryFriendlyByteBuf;
import ru.frontier.exchange.FrontierExchange;

public class ExchangeMenu extends AbstractContainerMenu {
    public ExchangeMenu(int containerId, Inventory inventory) {
        super(FrontierExchange.RESOURCE_EXCHANGE_MENU.get(), containerId);
    }

    public ExchangeMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
