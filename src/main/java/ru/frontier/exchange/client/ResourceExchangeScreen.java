package ru.frontier.exchange.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.frontier.exchange.menu.ExchangeMenu;
import ru.frontier.exchange.network.ExchangeCatalogSyncPayload;
import ru.frontier.exchange.network.ExchangeExecutePayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ResourceExchangeScreen extends Screen implements MenuAccess<ExchangeMenu> {
    private static final int COLOR_BACKGROUND = 0xFF101010;
    private static final int COLOR_PANEL = 0xFF1B1712;
    private static final int COLOR_BORDER = 0xFF7A4A16;
    private static final int COLOR_GOLD = 0xFFFFC247;
    private static final int COLOR_TEXT = 0xFFE6DCC8;
    private static final int COLOR_MUTED = 0xFF9B8F78;
    private static final int COLOR_LOCKED = 0xFFB35B42;

    private final ExchangeMenu menu;
    private final List<ExchangeCatalogSyncPayload.ContractView> visibleContracts = new ArrayList<>();
    private ExchangeCatalogSyncPayload.ContractView selected;
    private String category = "all";
    private int operations = 1;
    private int scrollOffset;
    private String status = "";

    public ResourceExchangeScreen(ExchangeMenu menu, Inventory inventory, Component title) {
        super(title);
        this.menu = menu;
    }

    @Override
    protected void init() {
        int left = (this.width - 260) / 2;
        int top = (this.height - 210) / 2;

        addRenderableWidget(Button.builder(Component.literal("Все"), button -> setCategory("all"))
                .bounds(left + 8, top + 28, 58, 18).build(GoldButton::new));
        addRenderableWidget(Button.builder(Component.literal("База"), button -> setCategory("basic_materials"))
                .bounds(left + 70, top + 28, 58, 18).build(GoldButton::new));
        addRenderableWidget(Button.builder(Component.literal("Supply"), button -> setCategory("supply"))
                .bounds(left + 132, top + 28, 58, 18).build(GoldButton::new));
        addRenderableWidget(Button.builder(Component.literal("Frontier"), button -> setCategory("frontier"))
                .bounds(left + 194, top + 28, 58, 18).build(GoldButton::new));

        int opsY = top + 184;
        addRenderableWidget(Button.builder(Component.literal("x1"), button -> operations = 1)
                .bounds(left + 8, opsY, 38, 18).build(GoldButton::new));
        addRenderableWidget(Button.builder(Component.literal("x4"), button -> operations = 4)
                .bounds(left + 50, opsY, 38, 18).build(GoldButton::new));
        addRenderableWidget(Button.builder(Component.literal("x16"), button -> operations = 16)
                .bounds(left + 92, opsY, 38, 18).build(GoldButton::new));
        addRenderableWidget(Button.builder(Component.literal("x64"), button -> operations = 64)
                .bounds(left + 134, opsY, 38, 18).build(GoldButton::new));
        addRenderableWidget(Button.builder(Component.literal("Обменять"), button -> confirm())
                .bounds(left + 176, opsY, 76, 18).build(GoldButton::new));

        refreshContracts();
    }

    private void setCategory(String category) {
        this.category = category;
        scrollOffset = 0;
        selected = null;
        refreshContracts();
    }

    private void refreshContracts() {
        visibleContracts.clear();
        for (ExchangeCatalogSyncPayload.ContractView contract :
                ResourceExchangeClientCatalog.getContracts()) {
            if ("all".equals(category) || category.equalsIgnoreCase(contract.category())) {
                visibleContracts.add(contract);
            }
        }
    }

    private boolean isUnlocked(ExchangeCatalogSyncPayload.ContractView contract) {
        return "foundation".equalsIgnoreCase(contract.unlockChapter())
                || ResourceExchangeClientCatalog.getOpenChapters().contains(contract.unlockChapter());
    }

    private void confirm() {
        if (selected == null) {
            status = "Выберите контракт";
            return;
        }
        if (!isUnlocked(selected)) {
            status = "Контракт закрыт главой: " + selected.unlockChapter();
            return;
        }
        PacketDistributor.sendToServer(new ExchangeExecutePayload(selected.id(), operations));
        status = "Запрос отправлен...";
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = (this.width - 260) / 2;
        int top = (this.height - 210) / 2;

        graphics.fill(0, 0, this.width, this.height, 0xE0060606);
        graphics.fill(left, top, left + 260, top + 210, COLOR_BACKGROUND);
        graphics.renderOutline(left, top, 260, 210, COLOR_BORDER);
        graphics.drawCenteredString(this.font, this.title, left + 130, top + 8, COLOR_GOLD);

        super.render(graphics, mouseX, mouseY, partialTick);

        int listTop = top + 50;
        int visibleRows = 5;
        maxScrollOffset = Math.max(0, visibleContracts.size() - visibleRows);
        if (scrollOffset > maxScrollOffset) {
            scrollOffset = maxScrollOffset;
        }

        for (int row = 0; row < visibleRows; row++) {
            int index = scrollOffset + row;
            if (index >= visibleContracts.size()) {
                break;
            }
            ExchangeCatalogSyncPayload.ContractView contract = visibleContracts.get(index);
            int y = listTop + row * 24;
            boolean hovered = mouseX >= left + 8 && mouseX <= left + 252
                    && mouseY >= y && mouseY <= y + 21;
            boolean chosen = contract == selected;
            boolean unlocked = isUnlocked(contract);

            int panelColor = chosen ? 0xFF4A351A : hovered ? 0xFF29231B : COLOR_PANEL;
            graphics.fill(left + 8, y, left + 252, y + 21, panelColor);
            graphics.renderOutline(left + 8, y, 244, 21,
                    chosen ? COLOR_GOLD : unlocked ? COLOR_BORDER : 0xFF5A2E25);

            ItemStack input = getItem(contract.inputItem());
            ItemStack output = getItem(contract.outputItem());
            if (!input.isEmpty()) {
                input.setCount(contract.inputCount());
                graphics.renderItem(input, left + 12, y + 2);
            }
            if (!output.isEmpty()) {
                output.setCount(contract.outputCount());
                graphics.renderItem(output, left + 58, y + 2);
            }

            graphics.drawString(this.font,
                    contract.inputCount() + " → " + contract.outputCount(),
                    left + 82, y + 7,
                    unlocked ? COLOR_TEXT : COLOR_LOCKED);

            String inputName = input.getHoverName().getString();
            String outputName = output.getHoverName().getString();
            if (!inputName.isBlank() && !outputName.isBlank()) {
                String names = inputName + " → " + outputName;
                int maxWidth = 112;
                if (this.font.width(names) > maxWidth) {
                    while (!names.isEmpty() && this.font.width(names) > maxWidth) {
                        names = names.substring(0, names.length() - 1);
                    }
                    names = names + "...";
                }
                graphics.drawString(this.font, names, left + 82, y + 15, COLOR_MUTED);
            }

            if (!unlocked) {
                graphics.drawString(this.font,
                        "Закрыто: " + contract.unlockChapter().toUpperCase(Locale.ROOT),
                        left + 132, y + 7, COLOR_LOCKED);
            }
        }

        var result = ResourceExchangeClientCatalog.getResult();
        if (result != null) {
            status = result.message();
        }
        graphics.drawString(this.font, status, left + 8, top + 174,
                status.contains("Нет") || status.contains("Недостаточно")
                        || status.contains("закрыт") ? COLOR_LOCKED : COLOR_GOLD);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // This screen uses an opaque custom panel and must not invoke Minecraft's blurred world shader.
    }

    private int maxScrollOffset;

    private static final class GoldButton extends Button {
        private GoldButton(Builder builder) {
            super(builder);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int background = this.isHoveredOrFocused() ? 0xFF302416 : 0xFF16120D;
            graphics.fill(this.getX(), this.getY(),
                    this.getX() + this.getWidth(), this.getY() + this.getHeight(),
                    background);
            graphics.renderOutline(this.getX(), this.getY(),
                    this.getWidth(), this.getHeight(), 0xFFFFC247);
            graphics.drawCenteredString(
                    Minecraft.getInstance().font,
                    this.getMessage(),
                    this.getX() + this.getWidth() / 2,
                    this.getY() + (this.getHeight() - 8) / 2,
                    0xFFFFD76A
            );
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int left = (this.width - 260) / 2;
        int top = (this.height - 210) / 2;
        int listTop = top + 50;
        for (int row = 0; row < 5; row++) {
            int index = scrollOffset + row;
            if (index >= visibleContracts.size()) {
                break;
            }
            int y = listTop + row * 24;
            if (mouseX >= left + 8 && mouseX <= left + 252
                    && mouseY >= y && mouseY <= y + 21) {
                selected = visibleContracts.get(index);
                status = isUnlocked(selected)
                        ? "Контракт выбран"
                        : "Контракт закрыт главой: " + selected.unlockChapter();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset = Math.max(0, Math.min(maxScrollOffset,
                scrollOffset - (verticalAmount > 0 ? 1 : -1)));
        return true;
    }

    private ItemStack getItem(String itemId) {
        ResourceLocation location = ResourceLocation.tryParse(itemId);
        if (location == null) {
            return ItemStack.EMPTY;
        }
        if (!BuiltInRegistries.ITEM.containsKey(location)) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(location));
    }

    @Override
    public ExchangeMenu getMenu() {
        return menu;
    }
}
