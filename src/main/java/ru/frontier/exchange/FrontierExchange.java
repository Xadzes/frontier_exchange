
package ru.frontier.exchange;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.fml.loading.FMLPaths;
import ru.frontier.exchange.economy.ExchangeCatalog;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ru.frontier.exchange.block.ResourceExchangeBlock;
import ru.frontier.exchange.menu.ExchangeMenu;
import ru.frontier.exchange.network.ExchangeCatalogSyncPayload;
import ru.frontier.exchange.network.ExchangeExecutePayload;
import ru.frontier.exchange.network.ExchangeResultPayload;
import ru.frontier.exchange.economy.ExchangeTransaction;
import net.neoforged.fml.loading.FMLEnvironment;
import ru.frontier.exchange.client.ResourceExchangeClient;
import ru.frontier.exchange.client.ResourceExchangeClientCatalog;


@Mod(FrontierExchange.MOD_ID)
public class FrontierExchange {

    public static final String MOD_ID = "frontier_exchange";

    // Регистрация блоков
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(MOD_ID);

    // Регистрация предметов
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(MOD_ID);

    // Регистрация вкладки креативного режима
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, MOD_ID);

    // Три торговых блока
    public static final DeferredBlock<Block> RESOURCE_EXCHANGE =
            BLOCKS.register("resource_exchange",
                    () -> new ResourceExchangeBlock(BlockBehaviour.Properties.of()
                            .strength(3.0F)
                            .requiresCorrectToolForDrops()));
    public static final DeferredHolder<MenuType<?>, MenuType<ExchangeMenu>> RESOURCE_EXCHANGE_MENU =
            MENUS.register("resource_exchange",
                    () -> IMenuTypeExtension.create(ExchangeMenu::new));

    public static final DeferredBlock<Block> ARMORY_TERMINAL =
            BLOCKS.register("armory_terminal",
                    () -> new Block(BlockBehaviour.Properties.of()
                            .strength(3.0F)
                            .requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> UPGRADE_WORKBENCH =
            BLOCKS.register("upgrade_workbench",
                    () -> new Block(BlockBehaviour.Properties.of()
                            .strength(3.0F)
                            .requiresCorrectToolForDrops()));

    // Предметы блоков
    public static final DeferredHolder<Item, BlockItem> RESOURCE_EXCHANGE_ITEM =
            ITEMS.register("resource_exchange",
                    () -> new BlockItem(RESOURCE_EXCHANGE.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> ARMORY_TERMINAL_ITEM =
            ITEMS.register("armory_terminal",
                    () -> new BlockItem(ARMORY_TERMINAL.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> UPGRADE_WORKBENCH_ITEM =
            ITEMS.register("upgrade_workbench",
                    () -> new BlockItem(UPGRADE_WORKBENCH.get(), new Item.Properties()));

    // Вкладка предметов
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB =
            TABS.register("frontier_tab",
                    () -> CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.frontier_exchange"))
                            .icon(() -> new ItemStack(RESOURCE_EXCHANGE_ITEM.get()))
                            .displayItems((parameters, output) -> {
                                output.accept(RESOURCE_EXCHANGE_ITEM.get());
                                output.accept(ARMORY_TERMINAL_ITEM.get());
                                output.accept(UPGRADE_WORKBENCH_ITEM.get());
                            })
                            .build());


public FrontierExchange(IEventBus modBus) {
    BLOCKS.register(modBus);
    ITEMS.register(modBus);
    TABS.register(modBus);
    MENUS.register(modBus);
    ExchangeCatalog.get().load(FMLPaths.CONFIGDIR.get()
            .resolve(MOD_ID)
            .resolve("exchanges.json"));
    modBus.addListener(this::registerNetwork);
    if (FMLEnvironment.dist.isClient()) {
        modBus.addListener(ResourceExchangeClient::registerScreens);
    }
}

private void registerNetwork(RegisterPayloadHandlersEvent event) {
    var registrar = event.registrar("1");
    registrar.playToClient(
            ExchangeCatalogSyncPayload.TYPE,
            ExchangeCatalogSyncPayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(
                    () -> ResourceExchangeClientCatalog.set(payload)
            )
    );
    registrar.playToClient(
            ExchangeResultPayload.TYPE,
            ExchangeResultPayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(
                    () -> ResourceExchangeClientCatalog.setResult(payload)
            )
    );
    registrar.playToServer(
            ExchangeExecutePayload.TYPE,
            ExchangeExecutePayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                if (context.player() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    ExchangeTransaction.Result result =
                            ExchangeTransaction.execute(serverPlayer, payload.contractId(), payload.operations());
                    String message = result.success()
                            ? "Обмен выполнен"
                            : switch (result.failure()) {
                                case INSUFFICIENT_INPUT -> "Недостаточно предметов";
                                case NO_OUTPUT_SPACE -> "Недостаточно места в инвентаре";
                                case CHAPTER_LOCKED -> "Контракт закрыт главой сюжета";
                                case INVALID_OPERATIONS -> "Некорректное количество операций";
                                case UNKNOWN_CONTRACT -> "Неизвестный контракт";
                                case CONTRACT_DISABLED -> "Контракт отключён";
                                case ITEM_NOT_REGISTERED -> "Предмет не зарегистрирован";
                                case INTERNAL_ERROR -> "Внутренняя ошибка";
                            };
                    PacketDistributor.sendToPlayer(
                            serverPlayer,
                            new ExchangeResultPayload(result.success(), message)
                    );
                }
            })
    );
}

}
