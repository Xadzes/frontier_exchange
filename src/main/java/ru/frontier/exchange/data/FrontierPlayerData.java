package ru.frontier.exchange.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class FrontierPlayerData extends SavedData {
    public static final String DATA_NAME = "frontier_exchange_players";
    public static final SavedData.Factory<FrontierPlayerData> FACTORY =
            new SavedData.Factory<>(FrontierPlayerData::new, FrontierPlayerData::load);

    private final Map<UUID, PlayerProgress> players = new HashMap<>();

    public static FrontierPlayerData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public PlayerProgress getOrCreatePlayer(UUID playerId) {
        if (playerId == null) {
            throw new IllegalArgumentException("playerId cannot be null");
        }
        PlayerProgress progress = players.get(playerId);
        if (progress == null) {
            progress = new PlayerProgress();
            players.put(playerId, progress);
            setDirty();
        }
        progress.setChangeListener(this::setDirty);
        return progress;
    }

    public PlayerProgress getPlayer(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        return players.get(playerId);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag playersTag = new CompoundTag();
        for (Map.Entry<UUID, PlayerProgress> entry : players.entrySet()) {
            playersTag.put(entry.getKey().toString(), entry.getValue().save());
        }
        tag.put("Players", playersTag);
        return tag;
    }

    private static FrontierPlayerData load(CompoundTag tag, HolderLookup.Provider registries) {
        FrontierPlayerData data = new FrontierPlayerData();
        if (!tag.contains("Players", 10)) {
            return data;
        }

        CompoundTag playersTag = tag.getCompound("Players");
        for (String key : playersTag.getAllKeys()) {
            try {
                UUID playerId = UUID.fromString(key);
                PlayerProgress progress = PlayerProgress.load(playersTag.getCompound(key));
                progress.setChangeListener(data::setDirty);
                data.players.put(playerId, progress);
            } catch (IllegalArgumentException exception) {
                // Ignore malformed UUID entries instead of preventing the whole world from loading.
            }
        }
        return data;
    }
}
