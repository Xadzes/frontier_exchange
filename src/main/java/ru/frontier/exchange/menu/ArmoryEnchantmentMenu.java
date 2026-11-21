package ru.frontier.exchange.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import ru.frontier.exchange.FrontierExchange;

public class ArmoryEnchantmentMenu extends AbstractContainerMenu {
    private static final int INVENTORY_START_X = 99;
    private final SimpleContainer itemContainer = new SimpleContainer(1);

    public ArmoryEnchantmentMenu(int containerId, Inventory inventory) {
        super(FrontierExchange.ARMORY_ENCHANTMENT_MENU.get(), containerId);

        addSlot(new Slot(itemContainer, 0, 50, 49));

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9,
                        INVENTORY_START_X + column * 18, 181 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, INVENTORY_START_X + column * 18, 239));
        }
    }

    public ArmoryEnchantmentMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory);
    }

    public ItemStack getTargetStack() {
        return itemContainer.getItem(0);
    }

    public void setTargetStack(ItemStack stack) {
        itemContainer.setItem(0, stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack original = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return original;
        }

        ItemStack stack = slot.getItem();
        original = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (getSlot(0).hasItem() || !moveItemStackTo(stack, 0, 1, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        clearContainer(player, itemContainer);
    }
}
