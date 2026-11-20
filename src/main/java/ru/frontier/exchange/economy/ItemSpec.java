package ru.frontier.exchange.economy;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public record ItemSpec(ResourceLocation itemId, int count) {
    public ItemSpec {
        if (count < 1 || count > 64) {
            throw new IllegalArgumentException("Item count must be between 1 and 64");
        }
    }

    public boolean isRegistered() {
        return BuiltInRegistries.ITEM.containsKey(itemId);
    }

    public Item getItem() {
        return BuiltInRegistries.ITEM.get(itemId);
    }

    public ItemStack createStack(int operations) {
        return new ItemStack(getItem(), count * operations);
    }
}
