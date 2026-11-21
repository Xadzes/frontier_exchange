package ru.frontier.exchange.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import ru.frontier.exchange.menu.ArmoryEnchantmentMenu;
import ru.frontier.exchange.network.ArmoryEnchantRequestPayload;
import ru.frontier.exchange.network.ArmoryQuoteRequestPayload;
import ru.frontier.exchange.network.ArmoryQuoteResponsePayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public class ArmoryEnchantmentScreen extends AbstractContainerScreen<ArmoryEnchantmentMenu> {
    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_HEIGHT = 280;
    private static final int LIST_ROW_HEIGHT = 17;
    private static final int GOLD = 0xFFFFC247;
    private static final int TEXT = 0xFFE6DCC8;
    private static final int MUTED = 0xFF9B8F78;

    private final List<Holder<Enchantment>> visibleEnchantments = new ArrayList<>();
    private Holder<Enchantment> selected;
    private int selectedLevel = 1;
    private int scrollOffset;
    private int maxScrollOffset;
    private int listX;
    private int listY;
    private int listWidth;
    private int listRows;
    private static int quoteRequestSequence;
    private int activeQuoteRequestId = -1;
    private Button decreaseLevelButton;
    private Button increaseLevelButton;
    private Button confirmButton;
    private ItemStack observedTarget = ItemStack.EMPTY;
    private String lastQuoteEnchantment = "";
    private int lastQuoteLevel;
    private ItemStack lastQuoteTarget = ItemStack.EMPTY;
    private List<ItemStack> lastQuoteInventory = List.of();

    public ArmoryEnchantmentScreen(ArmoryEnchantmentMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = PANEL_WIDTH;
        imageHeight = PANEL_HEIGHT;
        inventoryLabelY = 162;
    }

    @Override
    protected void init() {
        super.init();
        ArmoryClientState.clear();
        listX = leftPos + 132;
        listY = topPos + 31;
        listWidth = 216;
        listRows = 7;

        decreaseLevelButton = addButton("−", leftPos + 18, topPos + 78, 20, () -> changeLevel(-1));
        increaseLevelButton = addButton("+", leftPos + 98, topPos + 78, 20, () -> changeLevel(1));
        confirmButton = addButton("Зачаровать", leftPos + 228, topPos + 160, 120, this::confirm);
        decreaseLevelButton.active = false;
        increaseLevelButton.active = false;
        confirmButton.active = false;
        refreshEnchantments();
    }

    private Button addButton(String label, int x, int y, int width, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(label), button -> action.run())
                .bounds(x, y, width, 16)
                .build(ArmoryButton::new));
    }

    private void changeLevel(int offset) {
        if (selected == null) {
            return;
        }
        int minimumLevel = currentLevel(selected) + 1;
        int maxLevel = selected.value().getMaxLevel();
        if (minimumLevel > maxLevel) {
            return;
        }
        selectedLevel = Math.max(minimumLevel, Math.min(maxLevel, selectedLevel + offset));
        updateLevelButtons();
    }

    private void refreshEnchantments() {
        visibleEnchantments.clear();
        ItemStack target = menu.getTargetStack();
        if (!ItemStack.matches(target, observedTarget)) {
            observedTarget = target.copy();
            selected = null;
            selectedLevel = 1;
            scrollOffset = 0;
        }
        if (target.isEmpty() || Minecraft.getInstance().level == null) {
            selected = null;
            maxScrollOffset = 0;
            scrollOffset = 0;
            updateLevelButtons();
            return;
        }

        ItemEnchantments current = EnchantmentHelper.getEnchantmentsForCrafting(target);
        var registry = Minecraft.getInstance().level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT);
        registry.listElements().forEach(holder -> {
            Enchantment enchantment = holder.value();
            List<Holder<Enchantment>> otherEnchantments = current.keySet().stream()
                    .filter(existing -> !existing.equals(holder))
                    .toList();
            if (!target.supportsEnchantment(holder)
                    || !EnchantmentHelper.isEnchantmentCompatible(otherEnchantments, holder)
                    || current.getLevel(holder) >= enchantment.getMaxLevel()) {
                return;
            }
            visibleEnchantments.add(holder);
        });

        if (selected != null && !visibleEnchantments.contains(selected)) {
            selected = null;
        }
        maxScrollOffset = Math.max(0, visibleEnchantments.size() - listRows);
        scrollOffset = Math.min(scrollOffset, maxScrollOffset);
        updateLevelButtons();
    }

    private void updateLevelButtons() {
        if (selected == null) {
            decreaseLevelButton.active = false;
            increaseLevelButton.active = false;
            return;
        }
        int minimumLevel = currentLevel(selected) + 1;
        int maxLevel = selected.value().getMaxLevel();
        boolean canUpgrade = minimumLevel <= maxLevel;
        decreaseLevelButton.active = canUpgrade && selectedLevel > minimumLevel;
        increaseLevelButton.active = canUpgrade && selectedLevel < maxLevel;
    }

    private int currentLevel(Holder<Enchantment> enchantment) {
        return EnchantmentHelper.getEnchantmentsForCrafting(menu.getTargetStack()).getLevel(enchantment);
    }

    private void updateQuoteRequest() {
        ItemStack target = menu.getTargetStack();
        String enchantmentId = selected == null
                ? ""
                : selected.unwrapKey().map(key -> key.location().toString()).orElse("");
        if (target.isEmpty() || enchantmentId.isEmpty()) {
            lastQuoteEnchantment = "";
            lastQuoteLevel = 0;
            lastQuoteTarget = ItemStack.EMPTY;
            lastQuoteInventory = List.of();
            activeQuoteRequestId = -1;
            confirmButton.active = false;
            return;
        }

        if (!enchantmentId.equals(lastQuoteEnchantment)
                || selectedLevel != lastQuoteLevel
                || !ItemStack.matches(target, lastQuoteTarget)
                || !samePlayerInventory()) {
            activeQuoteRequestId = nextQuoteRequestId();
            PacketDistributor.sendToServer(new ArmoryQuoteRequestPayload(
                    menu.containerId, activeQuoteRequestId, enchantmentId, selectedLevel));
            lastQuoteEnchantment = enchantmentId;
            lastQuoteLevel = selectedLevel;
            lastQuoteTarget = target.copy();
            lastQuoteInventory = copyPlayerInventory();
            confirmButton.active = false;
        }

        ArmoryQuoteResponsePayload quote = matchingQuote(enchantmentId, selectedLevel);
        confirmButton.active = quote != null && quote.valid() && quote.affordable()
                && !ArmoryClientState.isTransactionPending();
    }

    private static int nextQuoteRequestId() {
        if (quoteRequestSequence == Integer.MAX_VALUE) {
            quoteRequestSequence = 0;
        }
        return ++quoteRequestSequence;
    }

    private ArmoryQuoteResponsePayload matchingQuote(String enchantmentId, int level) {
        ArmoryQuoteResponsePayload quote = ArmoryClientState.getQuote();
        if (quote == null || quote.requestId() != activeQuoteRequestId
                || !quote.enchantmentId().equals(enchantmentId)
                || quote.targetLevel() != level
                || !ItemStack.matches(menu.getTargetStack(), lastQuoteTarget)) {
            return null;
        }
        return quote;
    }

    private boolean samePlayerInventory() {
        List<ItemStack> current = Minecraft.getInstance().player.getInventory().items;
        if (current.size() != lastQuoteInventory.size()) {
            return false;
        }
        for (int i = 0; i < current.size(); i++) {
            if (!ItemStack.matches(current.get(i), lastQuoteInventory.get(i))) {
                return false;
            }
        }
        return true;
    }

    private List<ItemStack> copyPlayerInventory() {
        return Minecraft.getInstance().player.getInventory().items.stream()
                .map(ItemStack::copy)
                .toList();
    }

    private void confirm() {
        if (selected == null) {
            return;
        }
        String enchantmentId = selected.unwrapKey().map(key -> key.location().toString()).orElse("");
        ArmoryQuoteResponsePayload quote = matchingQuote(enchantmentId, selectedLevel);
        if (quote == null || !quote.valid() || !quote.affordable()
                || ArmoryClientState.isTransactionPending()) {
            return;
        }
        ArmoryClientState.beginTransaction();
        confirmButton.active = false;
        PacketDistributor.sendToServer(new ArmoryEnchantRequestPayload(
                menu.containerId, enchantmentId, selectedLevel, UUID.randomUUID()));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(0, 0, width, height, 0xFF000000);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF050505);
        graphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, 0xFF8A5A18);
        graphics.renderOutline(leftPos + 2, topPos + 2, imageWidth - 4, imageHeight - 4, 0xFF33240D);

        graphics.fill(leftPos + 44, topPos + 43, leftPos + 74, topPos + 73, 0xFF101010);
        graphics.renderOutline(leftPos + 44, topPos + 43, 30, 30, GOLD);
        graphics.fill(listX - 3, listY - 3,
                listX + listWidth + 3, listY + listRows * LIST_ROW_HEIGHT - 1, 0xFF080808);
        graphics.renderOutline(listX - 3, listY - 3,
                listWidth + 6, listRows * LIST_ROW_HEIGHT, 0xFF67451E);

        refreshEnchantments();
        for (int row = 0; row < listRows; row++) {
            int index = scrollOffset + row;
            int y = listY + row * LIST_ROW_HEIGHT;
            boolean isSelected = index < visibleEnchantments.size()
                    && visibleEnchantments.get(index).equals(selected);
            graphics.fill(listX, y, listX + listWidth, y + LIST_ROW_HEIGHT - 1,
                    isSelected ? 0xFF21180C : 0xFF0D0D0D);
            graphics.renderOutline(listX, y, listWidth, LIST_ROW_HEIGHT - 1,
                    isSelected ? GOLD : 0xFF392A15);
            if (index < visibleEnchantments.size()) {
                Holder<Enchantment> enchantment = visibleEnchantments.get(index);
                graphics.drawString(font, Enchantment.getFullname(enchantment,
                        Math.max(1, currentLevel(enchantment))), listX + 4, y + 4, TEXT);
            }
        }

        if (menu.getTargetStack().isEmpty()) {
            graphics.drawCenteredString(font, "Положите предмет в слот",
                    listX + listWidth / 2, listY + 53, MUTED);
        }

        graphics.drawCenteredString(font,
                selected == null ? "Уровень" : "Уровень " + selectedLevel,
                leftPos + 68, topPos + 82, selected == null ? MUTED : GOLD);
        graphics.drawString(font, "Цена:", leftPos + 14, topPos + 107, MUTED);

        String selectedId = selected == null
                ? ""
                : selected.unwrapKey().map(key -> key.location().toString()).orElse("");
        ArmoryQuoteResponsePayload quote = matchingQuote(selectedId, selectedLevel);
        if (selected != null && quote == null) {
            graphics.drawString(font, "Расчёт...", leftPos + 14, topPos + 123, MUTED);
        } else if (quote != null && quote.valid()) {
            int costY = topPos + 121;
            for (int i = 0; i < quote.price().size(); i++) {
                ArmoryQuoteResponsePayload.PriceItem price = quote.price().get(i);
                ResourceLocation itemId = ResourceLocation.tryParse(price.itemId());
                if (itemId != null && BuiltInRegistries.ITEM.containsKey(itemId)) {
                    ItemStack item = new ItemStack(BuiltInRegistries.ITEM.get(itemId));
                    int column = i % 3;
                    int row = i / 3;
                    int x = leftPos + 12 + column * 38;
                    int y = costY + row * 17;
                    graphics.renderItem(item, x, y);
                    graphics.drawString(font, "x" + price.count(), x + 17, y + 4,
                            quote.affordable() ? GOLD : 0xFFB35B42);
                }
            }
            graphics.drawWordWrap(font, Component.literal(quote.message()),
                    leftPos + 14, topPos + 141, 108,
                    quote.affordable() ? GOLD : 0xFFB35B42);
        } else if (quote != null) {
            graphics.drawWordWrap(font, Component.literal(quote.message()),
                    leftPos + 14, topPos + 122, 108, 0xFFB35B42);
        }

        var result = ArmoryClientState.getTransactionResult();
        if (result != null) {
            graphics.drawString(font, result.message(), leftPos + 76, topPos + 270,
                    result.success() ? GOLD : 0xFFB35B42);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawCenteredString(font, "Зачарование", imageWidth / 2, 8, GOLD);
        graphics.drawString(font, "Предмет", 14, 30, TEXT);
        graphics.drawString(font, "Доступные зачарования", listX - leftPos, 17, TEXT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        refreshEnchantments();
        updateQuoteRequest();
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int row = 0; row < listRows; row++) {
            int index = scrollOffset + row;
            int y = listY + row * LIST_ROW_HEIGHT;
            if (index < visibleEnchantments.size()
                    && mouseX >= listX && mouseX <= listX + listWidth
                    && mouseY >= y && mouseY <= y + LIST_ROW_HEIGHT - 1) {
                selected = visibleEnchantments.get(index);
                int current = currentLevel(selected);
                selectedLevel = Math.min(selected.value().getMaxLevel(), Math.max(1, current + 1));
                updateLevelButtons();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= listX && mouseX <= listX + listWidth
                && mouseY >= listY && mouseY <= listY + listRows * LIST_ROW_HEIGHT) {
            scrollOffset = Math.max(0, Math.min(maxScrollOffset,
                    scrollOffset - (verticalAmount > 0 ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    private static final class ArmoryButton extends Button {
        private ArmoryButton(Builder builder) {
            super(builder);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int background = active
                    ? isHoveredOrFocused() ? 0xFF302416 : 0xFF16120D
                    : 0xFF101010;
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), background);
            graphics.renderOutline(getX(), getY(), getWidth(), getHeight(),
                    active ? GOLD : 0xFF5A5140);
            graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(),
                    getX() + getWidth() / 2, getY() + 4,
                    active ? GOLD : 0xFF766B58);
        }
    }
}
