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
import net.minecraft.tags.ItemTags;
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
    private static final int GOLD = 0xFFFFC247;
    private static final int TEXT = 0xFFE6DCC8;
    private static final int MUTED = 0xFF9B8F78;

    private final List<Holder<Enchantment>> visibleEnchantments = new ArrayList<>();
    private Holder<Enchantment> selected;
    private String categoryFilter = "all";
    private String powerFilter = "all";
    private int selectedLevel = 1;
    private int scrollOffset;
    private int maxScrollOffset;
    private int listX;
    private int listY;
    private int listWidth;
    private int listRows;
    private static int quoteRequestSequence;
    private int activeQuoteRequestId = -1;
    private Button confirmButton;
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
        listX = leftPos + 83;
        listY = topPos + 67;
        listWidth = 176;
        listRows = 6;

        addButton("Все", leftPos + 10, topPos + 28, 48, () -> setCategory("all"));
        addButton("Боевые", leftPos + 62, topPos + 28, 58, () -> setCategory("combat"));
        addButton("Защита", leftPos + 124, topPos + 28, 58, () -> setCategory("defense"));
        addButton("Инструм.", leftPos + 186, topPos + 28, 62, () -> setCategory("tools"));
        addButton("Особые", leftPos + 252, topPos + 28, 58, () -> setCategory("special"));

        addButton("Все", leftPos + 92, topPos + 48, 46, () -> setPower("all"));
        addButton("Базовая", leftPos + 142, topPos + 48, 58, () -> setPower("base"));
        addButton("Продв.", leftPos + 204, topPos + 48, 54, () -> setPower("advanced"));
        addButton("Элитная", leftPos + 262, topPos + 48, 60, () -> setPower("elite"));

        addButton("−", leftPos + 289, topPos + 121, 20, () -> changeLevel(-1));
        addButton("+", leftPos + 335, topPos + 121, 20, () -> changeLevel(1));
        confirmButton = addButton("Подтвердить", leftPos + 274, topPos + 228, 78, this::confirm);
        confirmButton.active = false;
        refreshEnchantments();
    }

    private Button addButton(String label, int x, int y, int width, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(label), button -> action.run())
                .bounds(x, y, width, 16)
                .build(ArmoryButton::new));
    }

    private void setCategory(String filter) {
        categoryFilter = filter;
        scrollOffset = 0;
        refreshEnchantments();
    }

    private void setPower(String filter) {
        powerFilter = filter;
        scrollOffset = 0;
        refreshEnchantments();
    }

    private void changeLevel(int offset) {
        if (selected == null) {
            return;
        }
        int current = currentLevel(selected);
        int maxLevel = selected.value().getMaxLevel();
        selectedLevel = Math.max(Math.max(1, current + 1),
                Math.min(maxLevel, selectedLevel + offset));
    }

    private void refreshEnchantments() {
        visibleEnchantments.clear();
        ItemStack target = menu.getTargetStack();
        if (target.isEmpty() || Minecraft.getInstance().level == null) {
            selected = null;
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
                    || !matchesCategory(holder)
                    || !matchesPower(enchantment)) {
                return;
            }
            visibleEnchantments.add(holder);
        });

        if (selected != null && !visibleEnchantments.contains(selected)) {
            selected = null;
        }
        maxScrollOffset = Math.max(0, visibleEnchantments.size() - listRows);
        scrollOffset = Math.min(scrollOffset, maxScrollOffset);
    }

    private boolean matchesCategory(Holder<Enchantment> enchantment) {
        if ("all".equals(categoryFilter)) {
            return true;
        }
        var supportedItems = enchantment.value().definition().supportedItems();
        boolean combat = supportedItems.stream().anyMatch(item ->
                item.is(ItemTags.WEAPON_ENCHANTABLE)
                        || item.is(ItemTags.SHARP_WEAPON_ENCHANTABLE)
                        || item.is(ItemTags.BOW_ENCHANTABLE)
                        || item.is(ItemTags.CROSSBOW_ENCHANTABLE)
                        || item.is(ItemTags.TRIDENT_ENCHANTABLE));
        boolean defense = supportedItems.stream().anyMatch(item -> item.is(ItemTags.ARMOR_ENCHANTABLE));
        boolean tools = supportedItems.stream().anyMatch(item -> item.is(ItemTags.MINING_ENCHANTABLE));
        return switch (categoryFilter) {
            case "combat" -> combat;
            case "defense" -> defense;
            case "tools" -> tools;
            case "special" -> !combat && !defense && !tools;
            default -> true;
        };
    }

    private boolean matchesPower(Enchantment enchantment) {
        int weight = enchantment.getWeight();
        return switch (powerFilter) {
            case "base" -> weight >= 6;
            case "advanced" -> weight >= 3 && weight <= 5;
            case "elite" -> weight <= 2;
            default -> true;
        };
    }

    private int currentLevel(Holder<Enchantment> enchantment) {
        return EnchantmentHelper.getEnchantmentsForCrafting(menu.getTargetStack()).getLevel(enchantment);
    }

    private ItemStack previewStack() {
        ItemStack target = menu.getTargetStack();
        if (target.isEmpty() || selected == null) {
            return ItemStack.EMPTY;
        }
        ItemStack preview = target.copy();
        EnchantmentHelper.updateEnchantments(preview, enchantments -> enchantments.set(selected, selectedLevel));
        return preview;
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
        graphics.fill(0, 0, width, height, 0xE0060606);
        for (int y = 0; y < imageHeight; y++) {
            int blue = 18 + y * 10 / imageHeight;
            graphics.fill(leftPos, topPos + y, leftPos + imageWidth, topPos + y + 1,
                    0xFF100E00 | blue);
        }
        graphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, 0xFFFFB52E);
        graphics.renderOutline(leftPos + 2, topPos + 2, imageWidth - 4, imageHeight - 4, 0xFF8A5A18);

        graphics.fill(leftPos + 14, topPos + 75, leftPos + 72, topPos + 133, 0xFF151015);
        graphics.renderOutline(leftPos + 14, topPos + 75, 58, 58, GOLD);
        graphics.fill(leftPos + 270, topPos + 65, leftPos + 348, topPos + 116, 0xFF151015);
        graphics.renderOutline(leftPos + 270, topPos + 65, 78, 51, 0xFF8A5A18);

        refreshEnchantments();
        for (int row = 0; row < listRows; row++) {
            int index = scrollOffset + row;
            int y = listY + row * 15;
            graphics.fill(listX, y, listX + listWidth, y + 14, 0xFF1B1712);
            graphics.renderOutline(listX, y, listWidth, 14,
                    index < visibleEnchantments.size() && visibleEnchantments.get(index).equals(selected)
                            ? GOLD : 0xFF67451E);
            if (index < visibleEnchantments.size()) {
                Holder<Enchantment> enchantment = visibleEnchantments.get(index);
                graphics.drawString(font, Enchantment.getFullname(enchantment,
                        Math.max(1, currentLevel(enchantment))), listX + 4, y + 3, TEXT);
            }
        }

        ItemStack preview = previewStack();
        if (!preview.isEmpty()) {
            graphics.renderItem(preview, leftPos + 300, topPos + 79);
        }
        if (selected != null) {
            graphics.drawCenteredString(font, "Уровень " + selectedLevel,
                    leftPos + 322, topPos + 127, GOLD);
        }
        String selectedId = selected == null
                ? ""
                : selected.unwrapKey().map(key -> key.location().toString()).orElse("");
        ArmoryQuoteResponsePayload quote = matchingQuote(selectedId, selectedLevel);
        if (quote != null && quote.valid()) {
            graphics.drawString(font, "Стоимость:", leftPos + 270, topPos + 157, MUTED);
            int costY = topPos + 171;
            for (ArmoryQuoteResponsePayload.PriceItem price : quote.price()) {
                ResourceLocation itemId = ResourceLocation.tryParse(price.itemId());
                if (itemId != null && BuiltInRegistries.ITEM.containsKey(itemId)) {
                    ItemStack item = new ItemStack(BuiltInRegistries.ITEM.get(itemId));
                    graphics.drawString(font, "x" + price.count(), leftPos + 270, costY + 4,
                            quote.affordable() ? GOLD : 0xFFB35B42);
                    graphics.renderItem(item, leftPos + 309, costY);
                    costY += 19;
                }
            }
            graphics.drawString(font, quote.message(), leftPos + 270, topPos + 215,
                    quote.affordable() ? GOLD : 0xFFB35B42);
        } else if (quote != null) {
            graphics.drawString(font, quote.message(), leftPos + 270, topPos + 157, 0xFFB35B42);
        }

        var result = ArmoryClientState.getTransactionResult();
        if (result != null) {
            graphics.drawString(font, result.message(), leftPos + 76, topPos + 270,
                    result.success() ? GOLD : 0xFFB35B42);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawCenteredString(font, "Зачаровательня", imageWidth / 2, 8, GOLD);
        graphics.drawString(font, "Предмет", 19, 67, TEXT);
        graphics.drawString(font, "Совместимые зачарования", 83, 60, TEXT);
        graphics.drawString(font, "Предпросмотр", 280, 53, TEXT);
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
            int y = listY + row * 15;
            if (index < visibleEnchantments.size()
                    && mouseX >= listX && mouseX <= listX + listWidth
                    && mouseY >= y && mouseY <= y + 14) {
                selected = visibleEnchantments.get(index);
                int current = currentLevel(selected);
                selectedLevel = Math.min(selected.value().getMaxLevel(), Math.max(1, current + 1));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= listX && mouseX <= listX + listWidth
                && mouseY >= listY && mouseY <= listY + listRows * 15) {
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
