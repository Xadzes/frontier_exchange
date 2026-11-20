package ru.frontier.exchange.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import ru.frontier.exchange.FrontierExchange;

@OnlyIn(Dist.CLIENT)
public final class ResourceExchangeClient {
    private ResourceExchangeClient() {
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(FrontierExchange.RESOURCE_EXCHANGE_MENU.get(),
                ResourceExchangeScreen::new);
        event.register(FrontierExchange.ARMORY_ENCHANTMENT_MENU.get(),
                ArmoryEnchantmentScreen::new);
    }
}
