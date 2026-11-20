package ru.frontier.exchange.economy;

import java.util.Locale;

public record ExchangeContract(
        String id,
        String category,
        String unlockChapter,
        ItemSpec input,
        ItemSpec output,
        boolean enabled
) {
    public ExchangeContract {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Contract ID cannot be blank");
        }
        id = id.trim().toLowerCase(Locale.ROOT);

        if (category == null || category.isBlank()) {
            category = "uncategorized";
        }
        if (unlockChapter == null || unlockChapter.isBlank()) {
            unlockChapter = "foundation";
        }
        if (input == null || output == null) {
            throw new IllegalArgumentException("Contract input and output are required");
        }
    }

    public boolean isChunkSafe() {
        return input.isRegistered() && output.isRegistered();
    }
}
