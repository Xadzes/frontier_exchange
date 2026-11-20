package ru.frontier.exchange.economy;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import ru.frontier.exchange.data.FrontierPlayerData;
import ru.frontier.exchange.data.PlayerProgress;

import java.util.ArrayList;
import java.util.List;

public final class ExchangeTransaction {
    public enum Failure {
        INVALID_OPERATIONS,
        UNKNOWN_CONTRACT,
        CONTRACT_DISABLED,
        CHAPTER_LOCKED,
        ITEM_NOT_REGISTERED,
        INSUFFICIENT_INPUT,
        NO_OUTPUT_SPACE,
        INTERNAL_ERROR
    }

    public record Result(boolean success, Failure failure) {
        public static Result ok() {
            return new Result(true, null);
        }

        public static Result failed(Failure failure) {
            return new Result(false, failure);
        }
    }

    private ExchangeTransaction() {
    }

    public static Result execute(ServerPlayer player, String contractId, int operations) {
        if (operations < 1 || operations > 64) {
            return Result.failed(Failure.INVALID_OPERATIONS);
        }
        if (player == null || player.getServer() == null) {
            return Result.failed(Failure.INTERNAL_ERROR);
        }

        ExchangeContract contract = ExchangeCatalog.get().getContract(contractId);
        if (contract == null) {
            return Result.failed(Failure.UNKNOWN_CONTRACT);
        }
        if (!contract.enabled()) {
            return Result.failed(Failure.CONTRACT_DISABLED);
        }
        if (!contract.isChunkSafe()) {
            return Result.failed(Failure.ITEM_NOT_REGISTERED);
        }

        PlayerProgress progress = FrontierPlayerData.get(player.getServer())
                .getOrCreatePlayer(player.getUUID());
        if (!isChapterAvailable(progress, contract.unlockChapter())) {
            return Result.failed(Failure.CHAPTER_LOCKED);
        }

        Inventory inventory = player.getInventory();
        int inputCount = contract.input().count() * operations;
        int outputCount = contract.output().count() * operations;
        if (inputCount > 2304 || outputCount > 2304) {
            return Result.failed(Failure.INVALID_OPERATIONS);
        }

        int present = countExactItems(inventory, contract.input().getItem());
        if (present < inputCount) {
            return Result.failed(Failure.INSUFFICIENT_INPUT);
        }

        List<ItemStack> simulated = copyInventory(inventory);
        if (!canFit(simulated, contract.output().createStack(operations))) {
            return Result.failed(Failure.NO_OUTPUT_SPACE);
        }

        int removed = removeExactItems(inventory, contract.input().getItem(), inputCount);
        if (removed != inputCount) {
            return Result.failed(Failure.INSUFFICIENT_INPUT);
        }

        ItemStack output = contract.output().createStack(operations);
        player.getInventory().add(output);
        if (!output.isEmpty()) {
            player.drop(output, false);
        }

        progress.addPurchaseHistory(contract.id() + " x" + operations);
        return Result.ok();
    }

    private static boolean isChapterAvailable(PlayerProgress progress, String chapter) {
        return "foundation".equalsIgnoreCase(chapter) || progress.isChapterOpen(chapter);
    }

    private static int countExactItems(Inventory inventory, net.minecraft.world.item.Item item) {
        int count = 0;
        for (ItemStack stack : inventory.items) {
            if (!stack.isEmpty() && stack.is(item)
                    && stack.getComponents().equals(item.getDefaultInstance().getComponents())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int removeExactItems(Inventory inventory, net.minecraft.world.item.Item item, int amount) {
        int remaining = amount;
        for (int i = 0; i < inventory.items.size() && remaining > 0; i++) {
            ItemStack stack = inventory.items.get(i);
            if (!stack.isEmpty() && stack.is(item)
                    && stack.getComponents().equals(item.getDefaultInstance().getComponents())) {
                int removed = Math.min(remaining, stack.getCount());
                stack.shrink(removed);
                remaining -= removed;
            }
        }
        return amount - remaining;
    }

    private static List<ItemStack> copyInventory(Inventory inventory) {
        List<ItemStack> copy = new ArrayList<>(inventory.items.size());
        for (ItemStack stack : inventory.items) {
            copy.add(stack.copy());
        }
        return copy;
    }

    private static boolean canFit(List<ItemStack> slots, ItemStack output) {
        ItemStack remaining = output.copy();
        for (ItemStack slot : slots) {
            if (slot.isEmpty()) {
                int moved = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                remaining.shrink(moved);
            } else if (ItemStack.isSameItemSameComponents(slot, remaining)) {
                int free = remaining.getMaxStackSize() - slot.getCount();
                if (free > 0) {
                    int moved = Math.min(free, remaining.getCount());
                    slot.grow(moved);
                    remaining.shrink(moved);
                }
            }
            if (remaining.isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
