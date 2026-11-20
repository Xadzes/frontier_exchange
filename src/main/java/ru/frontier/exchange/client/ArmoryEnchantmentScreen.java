package ru.frontier.exchange.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import ru.frontier.exchange.menu.ArmoryEnchantmentMenu;

import java.util.ArrayList;
import java.util.List;

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

    public ArmoryEnchantmentScreen(ArmoryEnchantmentMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = PANEL_WIDTH;
        imageHeight = PANEL_HEIGHT;
        inventoryLabelY = 162;
    }

    @Override
    protected void init() {
        super.init();
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
        Button applyButton = addButton("Подтвердить", leftPos + 274, topPos + 228, 78, () -> {});
        applyButton.active = false;
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
        selectedLevel = Math.min(maxLevel, Math.max(Math.max(1, current + 1),
                selectedLevel + offset));
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
        graphics.drawString(font, "Стоимость: будет настроена позже",
                leftPos + 270, topPos + 157, MUTED);
        graphics.drawString(font, "Подтверждение появится после настройки цен",
                leftPos + 76, topPos + 270, MUTED);
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
