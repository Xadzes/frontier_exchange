package ru.frontier.exchange.economy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import ru.frontier.exchange.data.FrontierPlayerData;
import ru.frontier.exchange.data.PlayerProgress;
import ru.frontier.exchange.menu.ArmoryEnchantmentMenu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ArmoryEnchantingService {
    private static final Logger LOGGER = LoggerFactory.getLogger("FrontierExchange");
    private static final ArmoryEnchantingService INSTANCE = new ArmoryEnchantingService();

    private final Map<Integer, List<PriceItem>> levelCosts = new HashMap<>();
    private final Map<String, EnchantmentRule> rules = new HashMap<>();
    private final Map<Category, BigDecimal> multipliers = new HashMap<>();
    private final Map<UUID, LinkedHashSet<UUID>> seenTransactions = new HashMap<>();
    private EnchantmentRule defaultRule = new EnchantmentRule(Category.ADVANCED, "frontier", null);
    private boolean loaded;

    private ArmoryEnchantingService() {
    }

    public static ArmoryEnchantingService get() {
        return INSTANCE;
    }

    public void load(Path configFile) {
        try {
            Files.createDirectories(configFile.getParent());
            if (!Files.exists(configFile)) {
                Files.writeString(configFile, DEFAULT_CONFIG, StandardCharsets.UTF_8);
                LOGGER.info("Created default armory enchantment economy at {}", configFile);
            }

            Map<Integer, List<PriceItem>> newLevelCosts = new HashMap<>();
            Map<String, EnchantmentRule> newRules = new HashMap<>();
            Map<Category, BigDecimal> newMultipliers = new HashMap<>();
            EnchantmentRule newDefaultRule;

            try (var reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                JsonElement rootElement = JsonParser.parseReader(reader);
                if (!rootElement.isJsonObject()) {
                    throw new JsonParseException("Config root must be a JSON object");
                }
                JsonObject root = rootElement.getAsJsonObject();
                newDefaultRule = parseRule(requiredObject(root, "default_rule"));

                JsonObject multiplierObject = requiredObject(root, "category_multipliers");
                for (Category category : Category.values()) {
                    if (!multiplierObject.has(category.name())) {
                        throw new JsonParseException("Missing multiplier for " + category);
                    }
                    BigDecimal multiplier = new BigDecimal(multiplierObject.get(category.name()).getAsString());
                    if (multiplier.signum() <= 0 || multiplier.compareTo(BigDecimal.valueOf(100)) > 0) {
                        throw new JsonParseException("Multiplier for " + category + " must be between 0 and 100");
                    }
                    newMultipliers.put(category, multiplier);
                }

                JsonObject transitionObject = requiredObject(root, "level_transition_costs");
                for (String levelKey : transitionObject.keySet()) {
                    int level = Integer.parseInt(levelKey);
                    if (level < 1 || level > Enchantment.MAX_LEVEL) {
                        throw new JsonParseException("Invalid transition level " + levelKey);
                    }
                    newLevelCosts.put(level, parsePriceList(transitionObject.getAsJsonArray(levelKey)));
                }

                JsonObject enchantmentObject = requiredObject(root, "enchantments");
                for (String id : enchantmentObject.keySet()) {
                    try {
                        ResourceLocation enchantmentId = ResourceLocation.parse(id);
                        EnchantmentRule rule = parseRule(enchantmentObject.getAsJsonObject(id));
                        newRules.put(enchantmentId.toString(), rule);
                    } catch (RuntimeException exception) {
                        LOGGER.error("Ignoring invalid armory enchantment rule '{}': {}", id, exception.getMessage());
                    }
                }
            }

            levelCosts.clear();
            levelCosts.putAll(newLevelCosts);
            rules.clear();
            rules.putAll(newRules);
            multipliers.clear();
            multipliers.putAll(newMultipliers);
            defaultRule = newDefaultRule;
            loaded = true;
            LOGGER.info("Loaded {} armory enchantment rules and {} level prices",
                    rules.size(), levelCosts.size());
        } catch (Exception exception) {
            loaded = false;
            levelCosts.clear();
            rules.clear();
            multipliers.clear();
            LOGGER.error("Could not load armory enchantment economy '{}'; enchanting is disabled", configFile, exception);
        }
    }

    public Quote quote(ServerPlayer player, ArmoryEnchantmentMenu menu, String enchantmentId, int targetLevel) {
        if (!loaded) {
            return Quote.failed("Экономика зачарований не загружена", enchantmentId, targetLevel);
        }
        if (menu == null || player.containerMenu != menu) {
            return Quote.failed("Интерфейс зачаровательни закрыт", enchantmentId, targetLevel);
        }

        ItemStack target = menu.getTargetStack();
        if (target.isEmpty()) {
            return Quote.failed("Поместите предмет в слот", enchantmentId, targetLevel);
        }
        if (target.getCount() != 1) {
            return Quote.failed("Можно зачаровать только один предмет за операцию", enchantmentId, targetLevel);
        }

        ResourceLocation id = ResourceLocation.tryParse(enchantmentId);
        if (id == null) {
            return Quote.failed("Некорректный ID зачарования", enchantmentId, targetLevel);
        }

        Registry<Enchantment> registry = player.getServer().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        Holder<Enchantment> holder = registry.getHolder(ResourceKey.create(Registries.ENCHANTMENT, id))
                .orElse(null);
        if (holder == null) {
            return Quote.failed("Зачарование не зарегистрировано", id.toString(), targetLevel);
        }
        if (!target.supportsEnchantment(holder)) {
            return Quote.failed("Это зачарование несовместимо с предметом", id.toString(), targetLevel);
        }

        var currentEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(target);
        List<Holder<Enchantment>> otherEnchantments = currentEnchantments.keySet().stream()
                .filter(existing -> !existing.equals(holder))
                .toList();
        if (!EnchantmentHelper.isEnchantmentCompatible(otherEnchantments, holder)) {
            return Quote.failed("Конфликт с другим зачарованием предмета", id.toString(), targetLevel);
        }

        int currentLevel = currentEnchantments.getLevel(holder);
        if (targetLevel <= currentLevel) {
            return Quote.failed("Нельзя понизить уровень или повторно применить тот же",
                    id.toString(), targetLevel);
        }
        if (targetLevel > holder.value().getMaxLevel()) {
            return Quote.failed("Уровень выше максимального для этого зачарования", id.toString(), targetLevel);
        }

        String normalizedId = id.toString();
        EnchantmentRule rule = rules.getOrDefault(normalizedId, defaultRule);
        PlayerProgress progress = FrontierPlayerData.get(player.getServer()).getOrCreatePlayer(player.getUUID());
        if (!"foundation".equalsIgnoreCase(rule.unlockChapter())
                && !progress.isChapterOpen(rule.unlockChapter())) {
            return new Quote(false, false,
                    "Закрыто главой: " + rule.unlockChapter(), normalizedId, targetLevel, List.of());
        }

        List<PriceItem> price = calculatePrice(holder.value(), rule, currentLevel, targetLevel);
        if (price == null || price.isEmpty()) {
            return new Quote(false, false,
                    "Цена для этого зачарования или перехода уровня не настроена",
                    normalizedId, targetLevel, List.of());
        }
        RegistryAccess registryAccess = player.getServer().registryAccess();
        if (!allPriceItemsRegistered(registryAccess, price)) {
            return new Quote(false, false, "В конфигурации указан незарегистрированный предмет",
                    normalizedId, targetLevel, List.of());
        }

        boolean affordable = hasAllPayments(player.getInventory(), registryAccess, price);
        return new Quote(true, affordable,
                affordable ? "Можно зачаровать" : "Недостаточно ресурсов",
                normalizedId, targetLevel, price);
    }

    public TransactionResult enchant(
            ServerPlayer player,
            ArmoryEnchantmentMenu menu,
            String enchantmentId,
            int targetLevel
    ) {
        Quote quote = quote(player, menu, enchantmentId, targetLevel);
        if (!quote.valid()) {
            return new TransactionResult(false, quote.message());
        }
        if (!quote.affordable()) {
            return new TransactionResult(false, quote.message());
        }

        ResourceLocation id = ResourceLocation.parse(enchantmentId);
        RegistryAccess registryAccess = player.getServer().registryAccess();
        Registry<Enchantment> registry = registryAccess.registryOrThrow(Registries.ENCHANTMENT);
        Holder<Enchantment> holder = registry.getHolder(ResourceKey.create(Registries.ENCHANTMENT, id))
                .orElse(null);
        if (holder == null) {
            return new TransactionResult(false, "Зачарование больше не зарегистрировано");
        }

        ItemStack originalTarget = menu.getTargetStack().copy();
        ItemStack upgradedTarget = originalTarget.copy();
        try {
            EnchantmentHelper.updateEnchantments(upgradedTarget,
                    enchantments -> enchantments.set(holder, targetLevel));
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to prepare enchantment {} for player {}", id, player.getUUID(), exception);
            return new TransactionResult(false, "Не удалось подготовить результат зачарования");
        }

        Inventory inventory = player.getInventory();
        List<ItemStack> inventorySnapshot = inventory.items.stream().map(ItemStack::copy).toList();
        if (!hasAllPayments(inventory, registryAccess, quote.price())) {
            return new TransactionResult(false, "Недостаточно ресурсов");
        }

        try {
            if (!removePayments(inventory, registryAccess, quote.price())) {
                restore(inventory, inventorySnapshot);
                return new TransactionResult(false, "Не удалось списать ресурсы; операция отменена");
            }
            menu.setTargetStack(upgradedTarget);
            inventory.setChanged();
            menu.broadcastChanges();
            return new TransactionResult(true, "Зачарование успешно применено");
        } catch (RuntimeException exception) {
            restore(inventory, inventorySnapshot);
            menu.setTargetStack(originalTarget);
            menu.broadcastChanges();
            LOGGER.error("Failed to apply enchantment {} for player {}; transaction rolled back",
                    id, player.getUUID(), exception);
            return new TransactionResult(false, "Ошибка операции; предмет и ресурсы восстановлены");
        }
    }

    public boolean acceptTransaction(UUID playerId, UUID transactionId) {
        if (playerId == null || transactionId == null) {
            return false;
        }
        LinkedHashSet<UUID> playerTransactions =
                seenTransactions.computeIfAbsent(playerId, ignored -> new LinkedHashSet<>());
        if (!playerTransactions.add(transactionId)) {
            return false;
        }
        if (playerTransactions.size() > 512) {
            playerTransactions.remove(playerTransactions.iterator().next());
        }
        return true;
    }

    public void forgetPlayer(UUID playerId) {
        seenTransactions.remove(playerId);
    }

    private List<PriceItem> calculatePrice(
            Enchantment enchantment,
            EnchantmentRule rule,
            int currentLevel,
            int targetLevel
    ) {
        Map<ResourceLocation, Long> totals = new LinkedHashMap<>();
        if (enchantment.getMaxLevel() == 1) {
            if (currentLevel != 0 || targetLevel != 1 || rule.fixedPrice() == null) {
                return null;
            }
            addPrice(totals, rule.fixedPrice());
        } else {
            for (int level = currentLevel + 1; level <= targetLevel; level++) {
                List<PriceItem> transition = levelCosts.get(level);
                if (transition == null) {
                    return null;
                }
                addPrice(totals, transition);
            }
        }

        BigDecimal multiplier = multipliers.get(rule.category());
        if (multiplier == null) {
            return null;
        }
        List<PriceItem> result = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Long> entry : totals.entrySet()) {
            long adjusted;
            try {
                adjusted = BigDecimal.valueOf(entry.getValue())
                        .multiply(multiplier)
                        .setScale(0, RoundingMode.CEILING)
                        .longValueExact();
            } catch (ArithmeticException exception) {
                return null;
            }
            if (adjusted < 1 || adjusted > Integer.MAX_VALUE) {
                return null;
            }
            result.add(new PriceItem(entry.getKey(), (int) adjusted));
        }
        return result;
    }

    private static void addPrice(Map<ResourceLocation, Long> totals, List<PriceItem> items) {
        for (PriceItem item : items) {
            totals.merge(item.itemId(), (long) item.count(), Math::addExact);
        }
    }

    private static boolean allPriceItemsRegistered(RegistryAccess registries, List<PriceItem> price) {
        var items = registries.registryOrThrow(Registries.ITEM);
        return price.stream().allMatch(item -> items.containsKey(item.itemId()));
    }

    private static boolean hasAllPayments(Inventory inventory, RegistryAccess registries, List<PriceItem> price) {
        var items = registries.registryOrThrow(Registries.ITEM);
        for (PriceItem item : price) {
            Item registeredItem = items.get(item.itemId());
            if (countItems(inventory, registeredItem) < item.count()) {
                return false;
            }
        }
        return true;
    }

    private static boolean removePayments(Inventory inventory, RegistryAccess registries, List<PriceItem> price) {
        var items = registries.registryOrThrow(Registries.ITEM);
        for (PriceItem item : price) {
            int remaining = item.count();
            Item registeredItem = items.get(item.itemId());
            for (ItemStack stack : inventory.items) {
                if (remaining == 0) {
                    break;
                }
                if (isPlainItem(stack, registeredItem)) {
                    int removed = Math.min(remaining, stack.getCount());
                    stack.shrink(removed);
                    remaining -= removed;
                }
            }
            if (remaining != 0) {
                return false;
            }
        }
        return true;
    }

    private static int countItems(Inventory inventory, Item item) {
        int count = 0;
        for (ItemStack stack : inventory.items) {
            if (isPlainItem(stack, item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean isPlainItem(ItemStack stack, Item item) {
        return !stack.isEmpty() && stack.is(item)
                && stack.getComponents().equals(item.getDefaultInstance().getComponents());
    }

    private static void restore(Inventory inventory, List<ItemStack> snapshot) {
        for (int i = 0; i < snapshot.size(); i++) {
            inventory.items.set(i, snapshot.get(i).copy());
        }
        inventory.setChanged();
    }

    private EnchantmentRule parseRule(JsonObject json) {
        Category category;
        try {
            category = Category.valueOf(requiredString(json, "category").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new JsonParseException("Unknown enchantment category");
        }
        String chapter = json.has("unlock_chapter")
                ? requiredString(json, "unlock_chapter")
                : "foundation";
        if (chapter.isBlank()) {
            throw new JsonParseException("unlock_chapter cannot be blank");
        }
        List<PriceItem> fixedPrice = json.has("fixed_price")
                ? parsePriceList(json.getAsJsonArray("fixed_price"))
                : null;
        return new EnchantmentRule(category, chapter, fixedPrice);
    }

    private static List<PriceItem> parsePriceList(com.google.gson.JsonArray array) {
        if (array == null || array.isEmpty()) {
            throw new JsonParseException("Price list cannot be empty");
        }
        if (array.size() > 32) {
            throw new JsonParseException("Price list cannot contain more than 32 entries");
        }
        Map<ResourceLocation, Integer> items = new LinkedHashMap<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                throw new JsonParseException("Price entry must be an object");
            }
            JsonObject item = element.getAsJsonObject();
            ResourceLocation itemId = ResourceLocation.tryParse(requiredString(item, "item"));
            if (itemId == null || !item.has("count")) {
                throw new JsonParseException("Invalid price item");
            }
            int count = item.get("count").getAsInt();
            if (count < 1 || count > 4096) {
                throw new JsonParseException("Price item count must be between 1 and 4096");
            }
            items.merge(itemId, count, Math::addExact);
        }
        return items.entrySet().stream()
                .map(entry -> new PriceItem(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static String requiredString(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            throw new JsonParseException("Missing '" + key + "'");
        }
        return json.get(key).getAsString();
    }

    private static JsonObject requiredObject(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonObject()) {
            throw new JsonParseException("Missing object '" + key + "'");
        }
        return json.getAsJsonObject(key);
    }

    public enum Category {
        COMMON,
        ADVANCED,
        RARE
    }

    public record PriceItem(ResourceLocation itemId, int count) {
    }

    private record EnchantmentRule(Category category, String unlockChapter, List<PriceItem> fixedPrice) {
    }

    public record Quote(
            boolean valid,
            boolean affordable,
            String message,
            String enchantmentId,
            int targetLevel,
            List<PriceItem> price
    ) {
        private static Quote failed(String message, String enchantmentId, int targetLevel) {
            return new Quote(false, false, message,
                    enchantmentId == null ? "" : enchantmentId, targetLevel, List.of());
        }
    }

    public record TransactionResult(boolean success, String message) {
    }

    private static final String DEFAULT_CONFIG = """
            {
              "default_rule": {
                "category": "ADVANCED",
                "unlock_chapter": "frontier"
              },
              "category_multipliers": {
                "COMMON": 1.0,
                "ADVANCED": 1.5,
                "RARE": 2.0
              },
              "level_transition_costs": {
                "1": [{"item":"minecraft:iron_ingot","count":2}],
                "2": [{"item":"minecraft:iron_ingot","count":4}],
                "3": [{"item":"minecraft:diamond","count":1}],
                "4": [{"item":"minecraft:diamond","count":2}],
                "5": [{"item":"minecraft:diamond","count":3},{"item":"minecraft:netherite_scrap","count":1}]
              },
              "enchantments": {
                "minecraft:efficiency": {"category":"COMMON","unlock_chapter":"foundation"},
                "minecraft:unbreaking": {"category":"COMMON","unlock_chapter":"foundation"},
                "minecraft:protection": {"category":"COMMON","unlock_chapter":"foundation"},
                "minecraft:sharpness": {"category":"ADVANCED","unlock_chapter":"supply"},
                "minecraft:fortune": {"category":"ADVANCED","unlock_chapter":"supply"},
                "minecraft:looting": {"category":"ADVANCED","unlock_chapter":"supply"},
                "minecraft:mending": {"category":"RARE","unlock_chapter":"frontier","fixed_price":[{"item":"minecraft:iron_ingot","count":8}]},
                "minecraft:silk_touch": {"category":"RARE","unlock_chapter":"frontier"}
              }
            }
            """;
}
