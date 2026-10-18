package cn.daenx.mc.emeraldeconomy.client.screen;

import cn.daenx.mc.emeraldeconomy.content.AcquireContent;
import cn.daenx.mc.emeraldeconomy.core.EEConstants;
import cn.daenx.mc.emeraldeconomy.network.ScreenType;
import cn.daenx.mc.emeraldeconomy.network.payload.ClientPayloads;
import cn.daenx.mc.emeraldeconomy.network.payload.ServerPayloads;
import cn.daenx.mc.emeraldeconomy.shop.EnchantCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 附魔回收的二次确认框（需求：回收前必须再确认一次，防止误操作）。
 * <p>
 * 展示完整明细：物品名、数量、每个附魔的等级与回收价、整叠总价。
 * 只有点「确认回收」才会发送请求；取消则原样回到「附魔回收」分类。
 */
public class EnchantRecycleConfirmScreen extends EEScreen {

    private final ServerPayloads.EnchantItemLine item;

    public EnchantRecycleConfirmScreen(ServerPayloads.EnchantItemLine item) {
        super(Component.translatable(EEConstants.langKey("screen.acquire.enchant.confirm_title")));
        this.item = item;
    }

    @Override
    protected ScreenType screenType() {
        return ScreenType.ACQUISITION;
    }

    @Override
    protected int maxPanelHeight() {
        return 270;
    }

    @Override
    protected void init() {
        super.init();
        int x = contentX();
        int bottom = panelY + panelH - 24;

        addRenderableWidget(Button.builder(
                        Component.translatable(EEConstants.langKey("screen.acquire.enchant.confirm")),
                        b -> confirm())
                .bounds(x, bottom, 90, 18).build());
        addRenderableWidget(Button.builder(
                        Component.translatable(EEConstants.langKey("screen.common.cancel")),
                        b -> back())
                .bounds(x + 96, bottom, 70, 18).build());
    }

    private void confirm() {
        PacketDistributor.sendToServer(new ClientPayloads.AcquireEnchantSellRequest(
                item.slot(), item.itemId(), item.count()));
        back();
    }

    private void back() {
        Minecraft.getInstance().setScreen(new AcquisitionScreen(AcquireContent.ENCHANT_ID));
    }

    @Override
    protected void drawContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = contentX();
        int y = panelY + PAD;
        drawTitle(graphics, Component.translatable(
                EEConstants.langKey("screen.acquire.enchant.confirm_title")));

        ItemStack icon = iconOf(item.itemId());
        y += 20;
        if (!icon.isEmpty()) {
            graphics.renderItem(icon, x, y - 2);
        }
        String name = icon.isEmpty() ? item.itemId() : icon.getHoverName().getString();
        drawText(graphics, name + " ×" + item.count(), x + 20, y, COLOR_TEXT);

        y += 18;
        drawSeparator(graphics, x, y, contentW());
        y += 6;

        for (ServerPayloads.EnchantPriceLine line : item.enchants()) {
            drawText(graphics, enchantName(line.enchantId()),
                    x, y, COLOR_TEXT);
            drawRight(graphics, Component.translatable(EEConstants.langKey(
                            "screen.acquire.enchant.line_price"),
                    line.level(), money(line.price())).getString(),
                    x + contentW(), y, COLOR_MUTED);
            y += 12;
        }

        y += 4;
        drawSeparator(graphics, x, y, contentW());
        y += 6;
        drawText(graphics, Component.translatable(EEConstants.langKey(
                "screen.acquire.enchant.total"), money(item.totalPrice())).getString(),
                x, y, COLOR_UP);
        if (item.count() > 1) {
            drawRight(graphics, Component.translatable(EEConstants.langKey(
                            "screen.acquire.enchant.unit"), money(item.unitPrice())).getString(),
                    x + contentW(), y, COLOR_MUTED);
        }

        y += 18;
        drawText(graphics, Component.translatable(EEConstants.langKey(
                "screen.acquire.enchant.warning")).getString(), x, y, COLOR_DOWN);
    }

    /** 附魔名（客户端按自己的语言渲染）。 */
    private static String enchantName(String enchantId) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return enchantId;
        }
        return EnchantCatalog.displayName(level.registryAccess(), enchantId).getString();
    }

    private static ItemStack iconOf(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }
}
