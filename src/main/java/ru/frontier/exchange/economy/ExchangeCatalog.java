package ru.frontier.exchange.economy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ExchangeCatalog {
    private static final Logger LOGGER = LoggerFactory.getLogger("FrontierExchange");
    private static final ExchangeCatalog INSTANCE = new ExchangeCatalog();

    private Map<String, ExchangeContract> contracts = Collections.emptyMap();
    private boolean loaded;

    private ExchangeCatalog() {
    }

    public static ExchangeCatalog get() {
        return INSTANCE;
    }

    public void load(Path configFile) {
        try {
            Files.createDirectories(configFile.getParent());
            if (!Files.exists(configFile)) {
                writeDefaultConfig(configFile);
            }

            loadContracts(configFile);
            loaded = true;
        } catch (Exception exception) {
            LOGGER.error("Failed to load Resource Exchange catalog '{}'; using no contracts", configFile, exception);
            contracts = Collections.emptyMap();
            loaded = true;
        }
    }

    public boolean isLoaded() {
        return loaded;
    }

    public List<ExchangeContract> getContracts() {
        return List.copyOf(contracts.values());
    }

    public ExchangeContract getContract(String id) {
        if (id == null) {
            return null;
        }
        return contracts.get(id.trim().toLowerCase(java.util.Locale.ROOT));
    }

    private void loadContracts(Path configFile) throws IOException {
        Map<String, ExchangeContract> loadedContracts = new LinkedHashMap<>();

        try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            JsonElement rootElement = JsonParser.parseReader(reader);
            if (!rootElement.isJsonObject()) {
                throw new JsonParseException("Exchanges config root must be a JSON object");
            }

            JsonObject root = rootElement.getAsJsonObject();
            JsonArray contractsArray = root.getAsJsonArray("contracts");
            if (contractsArray == null) {
                throw new JsonParseException("Missing 'contracts' array");
            }

            for (JsonElement element : contractsArray) {
                if (!element.isJsonObject()) {
                    LOGGER.error("Skipping contract because it is not a JSON object in {}", configFile);
                    continue;
                }
                try {
                    ExchangeContract contract = parseContract(element.getAsJsonObject());
                    if (!contract.enabled()) {
                        continue;
                    }
                    if (loadedContracts.containsKey(contract.id())) {
                        LOGGER.error("Skipping duplicate contract ID '{}' in {}", contract.id(), configFile);
                        continue;
                    }
                    if (!contract.isChunkSafe()) {
                        LOGGER.error("Skipping contract '{}' because input or output item is not registered", contract.id());
                        continue;
                    }
                    loadedContracts.put(contract.id(), contract);
                } catch (RuntimeException exception) {
                    LOGGER.error("Skipping invalid contract in {}: {}", configFile, exception.getMessage());
                }
            }
        }

        contracts = Collections.unmodifiableMap(loadedContracts);
        LOGGER.info("Loaded {} Resource Exchange contracts", contracts.size());
    }

    private ExchangeContract parseContract(JsonObject json) {
        String id = requiredString(json, "id");

        JsonObject inputJson = requiredObject(json, "input");
        JsonObject outputJson = requiredObject(json, "output");

        ItemSpec input = parseItemSpec(inputJson);
        ItemSpec output = parseItemSpec(outputJson);
        String category = json.has("category") ? json.get("category").getAsString() : "uncategorized";
        String unlockChapter = json.has("unlock_chapter") ? json.get("unlock_chapter").getAsString() : "foundation";
        boolean enabled = !json.has("enabled") || json.get("enabled").getAsBoolean();

        return new ExchangeContract(id, category, unlockChapter, input, output, enabled);
    }

    private ItemSpec parseItemSpec(JsonObject json) {
        String itemId = requiredString(json, "item");
        if (!json.has("count") || !json.get("count").isJsonPrimitive()) {
            throw new JsonParseException("Missing item count");
        }

        int count = json.get("count").getAsInt();
        if (count < 1 || count > 64) {
            throw new JsonParseException("Item count must be between 1 and 64");
        }

        ResourceLocation resourceLocation = ResourceLocation.parse(itemId);
        return new ItemSpec(resourceLocation, count);
    }

    private static String requiredString(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            throw new JsonParseException("Missing string '" + key + "'");
        }
        return json.get(key).getAsString();
    }

    private static JsonObject requiredObject(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonObject()) {
            throw new JsonParseException("Missing object '" + key + "'");
        }
        return json.getAsJsonObject(key);
    }

    private void writeDefaultConfig(Path configFile) throws IOException {
        Files.writeString(configFile, DEFAULT_CONFIG, StandardCharsets.UTF_8);
        LOGGER.info("Created default Resource Exchange catalog at {}", configFile);
    }

    private static final String DEFAULT_CONFIG = """
            {
              "contracts": [
                {"id":"coal_to_iron","category":"basic_materials","unlock_chapter":"foundation","input":{"item":"minecraft:coal","count":5},"output":{"item":"minecraft:iron_ingot","count":1},"enabled":true},
                {"id":"iron_to_coal","category":"basic_materials","unlock_chapter":"foundation","input":{"item":"minecraft:iron_ingot","count":1},"output":{"item":"minecraft:coal","count":3},"enabled":true},
                {"id":"coal_to_copper","category":"basic_materials","unlock_chapter":"foundation","input":{"item":"minecraft:coal","count":2},"output":{"item":"minecraft:copper_ingot","count":1},"enabled":true},
                {"id":"copper_to_coal","category":"basic_materials","unlock_chapter":"foundation","input":{"item":"minecraft:copper_ingot","count":1},"output":{"item":"minecraft:coal","count":1},"enabled":true},
                {"id":"coal_to_redstone","category":"basic_materials","unlock_chapter":"foundation","input":{"item":"minecraft:coal","count":2},"output":{"item":"minecraft:redstone","count":1},"enabled":true},
                {"id":"redstone_to_coal","category":"basic_materials","unlock_chapter":"foundation","input":{"item":"minecraft:redstone","count":1},"output":{"item":"minecraft:coal","count":1},"enabled":true},
                {"id":"coal_to_lapis","category":"basic_materials","unlock_chapter":"foundation","input":{"item":"minecraft:coal","count":3},"output":{"item":"minecraft:lapis_lazuli","count":1},"enabled":true},
                {"id":"lapis_to_coal","category":"basic_materials","unlock_chapter":"foundation","input":{"item":"minecraft:lapis_lazuli","count":1},"output":{"item":"minecraft:coal","count":1},"enabled":true},
                {"id":"coal_to_quartz","category":"basic_materials","unlock_chapter":"foundation","input":{"item":"minecraft:coal","count":4},"output":{"item":"minecraft:quartz","count":1},"enabled":true},
                {"id":"quartz_to_coal","category":"basic_materials","unlock_chapter":"foundation","input":{"item":"minecraft:quartz","count":1},"output":{"item":"minecraft:coal","count":2},"enabled":true},
                {"id":"iron_to_gold","category":"basic_materials","unlock_chapter":"foundation","input":{"item":"minecraft:iron_ingot","count":2},"output":{"item":"minecraft:gold_ingot","count":1},"enabled":true},
                {"id":"gold_to_coal","category":"basic_materials","unlock_chapter":"foundation","input":{"item":"minecraft:gold_ingot","count":1},"output":{"item":"minecraft:coal","count":6},"enabled":true},
                {"id":"iron_to_obsidian","category":"basic_materials","unlock_chapter":"foundation","input":{"item":"minecraft:iron_ingot","count":2},"output":{"item":"minecraft:obsidian","count":1},"enabled":true},
                {"id":"obsidian_to_coal","category":"basic_materials","unlock_chapter":"foundation","input":{"item":"minecraft:obsidian","count":1},"output":{"item":"minecraft:coal","count":4},"enabled":true},
                {"id":"cobble_to_gravel","category":"basic_materials","unlock_chapter":"foundation","input":{"item":"minecraft:cobblestone","count":16},"output":{"item":"minecraft:gravel","count":8},"enabled":true},
                {"id":"sand_to_gravel","category":"basic_materials","unlock_chapter":"foundation","input":{"item":"minecraft:sand","count":16},"output":{"item":"minecraft:gravel","count":8},"enabled":true},
                {"id":"logs_to_coal","category":"basic_materials","unlock_chapter":"foundation","input":{"item":"minecraft:oak_log","count":8},"output":{"item":"minecraft:coal","count":3},"enabled":true},
                {"id":"copper_to_iron","category":"basic_materials","unlock_chapter":"foundation","input":{"item":"minecraft:copper_ingot","count":8},"output":{"item":"minecraft:iron_ingot","count":3},"enabled":true},
                {"id":"coal_to_gold","category":"supply","unlock_chapter":"supply","input":{"item":"minecraft:coal","count":10},"output":{"item":"minecraft:gold_ingot","count":1},"enabled":true},
                {"id":"coal_to_obsidian","category":"supply","unlock_chapter":"supply","input":{"item":"minecraft:coal","count":8},"output":{"item":"minecraft:obsidian","count":1},"enabled":true},
                {"id":"coal_to_emerald","category":"supply","unlock_chapter":"supply","input":{"item":"minecraft:coal","count":15},"output":{"item":"minecraft:emerald","count":1},"enabled":true},
                {"id":"coal_to_ender_pearl","category":"supply","unlock_chapter":"supply","input":{"item":"minecraft:coal","count":15},"output":{"item":"minecraft:ender_pearl","count":1},"enabled":true},
                {"id":"coal_to_blaze_rod","category":"supply","unlock_chapter":"supply","input":{"item":"minecraft:coal","count":13},"output":{"item":"minecraft:blaze_rod","count":1},"enabled":true},
                {"id":"iron_to_emerald","category":"supply","unlock_chapter":"supply","input":{"item":"minecraft:iron_ingot","count":8},"output":{"item":"minecraft:emerald","count":1},"enabled":true},
                {"id":"gold_to_obsidian","category":"supply","unlock_chapter":"supply","input":{"item":"minecraft:gold_ingot","count":6},"output":{"item":"minecraft:obsidian","count":1},"enabled":true},
                {"id":"coal_to_diamond","category":"frontier","unlock_chapter":"frontier","input":{"item":"minecraft:coal","count":40},"output":{"item":"minecraft:diamond","count":1},"enabled":true},
                {"id":"iron_to_diamond","category":"frontier","unlock_chapter":"frontier","input":{"item":"minecraft:iron_ingot","count":24},"output":{"item":"minecraft:diamond","count":1},"enabled":true},
                {"id":"diamond_to_netherite_scrap","category":"frontier","unlock_chapter":"frontier","input":{"item":"minecraft:diamond","count":4},"output":{"item":"minecraft:netherite_scrap","count":1},"enabled":true},
                {"id":"coal_to_netherite_scrap","category":"frontier","unlock_chapter":"frontier","input":{"item":"minecraft:coal","count":100},"output":{"item":"minecraft:netherite_scrap","count":1},"enabled":true},
                {"id":"emerald_to_netherite_scrap","category":"frontier","unlock_chapter":"frontier","input":{"item":"minecraft:emerald","count":8},"output":{"item":"minecraft:netherite_scrap","count":1},"enabled":true}
              ]
            }
            """;
}
