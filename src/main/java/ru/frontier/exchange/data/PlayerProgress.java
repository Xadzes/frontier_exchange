package ru.frontier.exchange.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PlayerProgress {
    private Runnable changeListener = () -> {};
    private final Map<String, Long> currencies = new HashMap<>();
    private final Set<String> completedQuests = new HashSet<>();
    private final Set<String> openChapters = new HashSet<>();
    private final Set<String> openCategories = new HashSet<>();
    private final Set<String> achievements = new HashSet<>();
    private final List<String> purchaseHistory = new ArrayList<>();

    public static final int PURCHASE_HISTORY_LIMIT = 100;

    void setChangeListener(Runnable changeListener) {
        this.changeListener = changeListener == null ? () -> {} : changeListener;
    }

    private void markChanged() {
        changeListener.run();
    }

    public long getCurrency(String currencyId) {
        if (currencyId == null || currencyId.isBlank()) {
            return 0L;
        }
        return currencies.getOrDefault(currencyId, 0L);
    }

    public boolean addCurrency(String currencyId, long amount) {
        if (currencyId == null || currencyId.isBlank() || amount <= 0L) {
            return false;
        }

        long current = getCurrency(currencyId);
        if (current > Long.MAX_VALUE - amount) {
            return false;
        }

        long updated = current + amount;
        currencies.put(currencyId, updated);
        markChanged();
        return true;
    }

    public boolean spendCurrency(String currencyId, long amount) {
        if (amount < 0L) {
            return false;
        }
        long current = getCurrency(currencyId);
        if (amount > current) {
            return false;
        }
        currencies.put(currencyId, current - amount);
        markChanged();
        return true;
    }

    public boolean completeQuest(String questId) {
        boolean changed = questId != null && !questId.isBlank() && completedQuests.add(questId);
        if (changed) {
            markChanged();
        }
        return changed;
    }

    public boolean isQuestCompleted(String questId) {
        return questId != null && completedQuests.contains(questId);
    }

    public boolean openChapter(String chapterId) {
        boolean changed = chapterId != null && !chapterId.isBlank() && openChapters.add(chapterId);
        if (changed) {
            markChanged();
        }
        return changed;
    }

    public boolean isChapterOpen(String chapterId) {
        return chapterId != null && openChapters.contains(chapterId);
    }

    public boolean openCategory(String categoryId) {
        boolean changed = categoryId != null && !categoryId.isBlank() && openCategories.add(categoryId);
        if (changed) {
            markChanged();
        }
        return changed;
    }

    public boolean isCategoryOpen(String categoryId) {
        return categoryId != null && openCategories.contains(categoryId);
    }

    public boolean unlockAchievement(String achievementId) {
        boolean changed = achievementId != null && !achievementId.isBlank() && achievements.add(achievementId);
        if (changed) {
            markChanged();
        }
        return changed;
    }

    public boolean hasAchievement(String achievementId) {
        return achievementId != null && achievements.contains(achievementId);
    }

    public void addPurchaseHistory(String entry) {
        if (entry == null || entry.isBlank()) {
            return;
        }
        purchaseHistory.add(entry);
        while (purchaseHistory.size() > PURCHASE_HISTORY_LIMIT) {
            purchaseHistory.removeFirst();
        }
        markChanged();
    }

    public Set<String> getCompletedQuests() {
        return Collections.unmodifiableSet(completedQuests);
    }

    public Set<String> getOpenChapters() {
        return Collections.unmodifiableSet(openChapters);
    }

    public Set<String> getOpenCategories() {
        return Collections.unmodifiableSet(openCategories);
    }

    public Set<String> getAchievements() {
        return Collections.unmodifiableSet(achievements);
    }

    public Map<String, Long> getCurrencies() {
        return Collections.unmodifiableMap(currencies);
    }

    public List<String> getPurchaseHistory() {
        return Collections.unmodifiableList(purchaseHistory);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        CompoundTag currencyTag = new CompoundTag();
        currencies.forEach(currencyTag::putLong);
        tag.put("Currencies", currencyTag);

        writeStringSet(tag, "CompletedQuests", completedQuests);
        writeStringSet(tag, "OpenChapters", openChapters);
        writeStringSet(tag, "OpenCategories", openCategories);
        writeStringSet(tag, "Achievements", achievements);
        writeStringList(tag, "PurchaseHistory", purchaseHistory);
        return tag;
    }

    public static PlayerProgress load(CompoundTag tag) {
        PlayerProgress progress = new PlayerProgress();

        CompoundTag currencyTag = tag.getCompound("Currencies");
        for (String key : currencyTag.getAllKeys()) {
            long amount = currencyTag.getLong(key);
            if (amount > 0L) {
                progress.currencies.put(key, amount);
            }
        }

        readStringSet(tag, "CompletedQuests", progress.completedQuests);
        readStringSet(tag, "OpenChapters", progress.openChapters);
        readStringSet(tag, "OpenCategories", progress.openCategories);
        readStringSet(tag, "Achievements", progress.achievements);
        readStringList(tag, "PurchaseHistory", progress.purchaseHistory);
        return progress;
    }

    private static void writeStringSet(CompoundTag tag, String key, Set<String> values) {
        writeStringList(tag, key, values.stream().sorted().toList());
    }

    private static void writeStringList(CompoundTag tag, String key, List<String> values) {
        ListTag list = new ListTag();
        for (String value : values) {
            list.add(StringTag.valueOf(value));
        }
        tag.put(key, list);
    }

    private static void readStringSet(CompoundTag tag, String key, Set<String> target) {
        if (!tag.contains(key, 9)) {
            return;
        }
        ListTag list = tag.getList(key, 8);
        for (int i = 0; i < list.size(); i++) {
            target.add(list.getString(i));
        }
    }

    private static void readStringList(CompoundTag tag, String key, List<String> target) {
        if (!tag.contains(key, 9)) {
            return;
        }
        ListTag list = tag.getList(key, 8);
        int start = Math.max(0, list.size() - PURCHASE_HISTORY_LIMIT);
        for (int i = start; i < list.size(); i++) {
            target.add(list.getString(i));
        }
    }
}
