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
        int left = (this.width - 320) / 2;
        int top = (this.height - 240) / 2;

        addRenderableWidget(Button.builder(Component.literal("Все"), button -> setCategory("all"))
                .bounds(left + 8, top + 28, 58, 18).build(GoldButton::new));
        addRenderableWidget(Button.builder(Component.literal("База"), button -> setCategory("basic_materials"))
                .bounds(left + 70, top + 28, 58, 18).build(GoldButton::new));
        addRenderableWidget(Button.builder(Component.literal("Supply"), button -> setCategory("supply"))
                .bounds(left + 132, top + 28, 58, 18).build(GoldButton::new));
        addRenderableWidget(Button.builder(Component.literal("Frontier"), button -> setCategory("frontier"))
                .bounds(left + 194, top + 28, 58, 18).build(GoldButton::new));
        int opsY = top + 210;
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
        int left = (this.width - 320) / 2;
        int top = (this.height - 240) / 2;

        graphics.fill(0, 0, this.width, this.height, 0xE0060606);
        renderGradientPanel(graphics, left, top, 320, 240);
        renderGoldFrame(graphics, left, top, 320, 240);
        renderTopEmblem(graphics, left, top);

        super.render(graphics, mouseX, mouseY, partialTick);

        int listTop = top + 52;
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
            graphics.fill(left + 32, y, left + 288, y + 21, panelColor);
            graphics.renderOutline(left + 32, y, 256, 21,
                    chosen ? COLOR_GOLD : unlocked ? COLOR_BORDER : 0xFF5A2E25);

            ItemStack input = getItem(contract.inputItem());
            ItemStack output = getItem(contract.outputItem());
            int inputX = left + 74;
            int outputX = left + 206;

            if (!input.isEmpty()) {
                input.setCount(1);
                graphics.drawString(this.font, "x" + contract.inputCount(),
                        inputX - 24, y + 7, unlocked ? COLOR_GOLD : COLOR_LOCKED);
                graphics.renderItem(input, inputX - 2, y + 2);
            }
            if (!output.isEmpty()) {
                output.setCount(1);
                graphics.drawString(this.font, "x" + contract.outputCount(),
                        outputX - 24, y + 7, unlocked ? COLOR_GOLD : COLOR_LOCKED);
                graphics.renderItem(output, outputX - 2, y + 2);
            }
            graphics.drawCenteredString(this.font, "→", left + 160, y + 7,
                    unlocked ? 0xFFFFB52E : COLOR_LOCKED);

        }

        var result = ResourceExchangeClientCatalog.getResult();
        if (result != null) {
            status = result.message();
        }
        renderSideMotifs(graphics, left, top);
        graphics.drawString(this.font, status, left + 32, top + 192,
                status.contains("Нет") || status.contains("Недостаточно")
                        || status.contains("закрыт") ? COLOR_LOCKED : COLOR_GOLD);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // This screen uses an opaque custom panel and must not invoke Minecraft's blurred world shader.
    }

    private int maxScrollOffset;

    private void renderGradientPanel(GuiGraphics graphics, int left, int top, int width, int height) {
        for (int y = 0; y < height; y++) {
            int red = 16 - (y * 3 / height);
            int green = 14 - (y * 2 / height);
            int blue = 18 + (y * 10 / height);
            int color = 0xFF000000
                    | (Math.max(0, red) << 16)
                    | (Math.max(0, green) << 8)
                    | Math.max(0, blue);
            graphics.fill(left, top + y, left + width, top + y + 1, color);
        }
    }

    private void renderGoldFrame(GuiGraphics graphics, int left, int top, int width, int height) {
        graphics.renderOutline(left, top, width, height, 0xFFFFB52E);
        graphics.renderOutline(left + 2, top + 2, width - 4, height - 4, 0xFF8A5A18);
        graphics.renderOutline(left + 4, top + 4, width - 8, height - 8, 0xFF3F2A12);
    }

    private void renderTopEmblem(GuiGraphics graphics, int left, int top) {
        int leftSquare = left + 18;
        int rightSquare = left + 274;
        int y = top + 12;

        graphics.fill(leftSquare, y, leftSquare + 28, y + 28, 0xFF151015);
        graphics.renderOutline(leftSquare, y, 28, 28, 0xFFFFC247);
        graphics.renderOutline(leftSquare + 2, y + 2, 24, 24, 0xFF9A641B);

        graphics.fill(rightSquare, y, rightSquare + 28, y + 28, 0xFF151015);
        graphics.renderOutline(rightSquare, y, 28, 28, 0xFFFFC247);
        graphics.renderOutline(rightSquare + 2, y + 2, 24, 24, 0xFF9A641B);

        graphics.drawString(this.font, "M", leftSquare + 10, y + 10, 0xFFFFD76A);
        graphics.drawString(this.font, "I", rightSquare + 12, y + 10, 0xFFE0D5BF);
        graphics.drawCenteredString(this.font, "→", left + 160, y + 9, 0xFFFFB52E);
    }

    private void renderSideMotifs(GuiGraphics graphics, int left, int top) {
        int y = top + 58;
        for (int row = 0; row < 5; row++) {
            int motifY = y + row * 24;
            drawBagArrowIngot(graphics, left + 5, motifY);
            drawBagArrowIngot(graphics, left + 300, motifY);
        }
    }

    private void drawBagArrowIngot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x + 2, y + 5, x + 12, y + 15, 0xFF6A4422);
        graphics.renderOutline(x + 2, y + 5, 10, 10, 0xFFFFA92F);
        graphics.fill(x + 5, y + 2, x + 9, y + 5, 0xFFC88A31);
        graphics.drawString(this.font, "→", x + 7, y + 3, 0xFFFFC247);
        graphics.fill(x + 14, y + 7, x + 19, y + 13, 0xFFC6C0B3);
        graphics.renderOutline(x + 14, y + 7, 5, 6, 0xFF8A6A35);
    }

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
        int left = (this.width - 320) / 2;
        int top = (this.height - 240) / 2;
        int listTop = top + 52;
        for (int row = 0; row < 5; row++) {
            int index = scrollOffset + row;
            if (index >= visibleContracts.size()) {
                break;
            }
            int y = listTop + row * 24;
            if (mouseX >= left + 32 && mouseX <= left + 288
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
