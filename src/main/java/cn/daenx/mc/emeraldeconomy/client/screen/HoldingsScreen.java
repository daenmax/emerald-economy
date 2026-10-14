package cn.daenx.mc.emeraldeconomy.client.screen;

import cn.daenx.mc.emeraldeconomy.client.ClientEconomyCache;
import cn.daenx.mc.emeraldeconomy.core.EEConstants;
import cn.daenx.mc.emeraldeconomy.network.ScreenType;
import cn.daenx.mc.emeraldeconomy.market.FundType;
import cn.daenx.mc.emeraldeconomy.network.payload.ServerPayloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 持仓界面（需求文档 3.5.3）。
 * <p>
 * 显示每支基金的持有份额、平均成本、当前市值、浮动盈亏、已实现收益。
 */
public class HoldingsScreen extends EEScreen {

    private final Screen parent;

    /** 该界面对应的服务端快照类型。 */
    @Override
    protected ScreenType screenType() {
        return ScreenType.EXCHANGE;
    }

    public HoldingsScreen(Screen parent) {
        super(Component.translatable(EEConstants.langKey("screen.holdings.title")));
        this.parent = parent;
    }

    @Override
    protected int maxPanelWidth() {
        return 400;
    }

    @Override
    protected int maxPanelHeight() {
        return 240;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(
                        Component.translatable(EEConstants.langKey("screen.common.back")),
                        b -> this.minecraft.setScreen(parent))
                .bounds(contentX(), panelY + panelH - 24, 70, 18).build());
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    protected void drawContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = contentX();
        int y = panelY + PAD;
        drawTitle(graphics, Component.translatable(EEConstants.langKey("screen.holdings.title")));

        ServerPayloads.SyncMarket market = ClientEconomyCache.market;
        if (market == null) {
            drawText(graphics, Component.translatable(EEConstants.langKey("screen.loading")).getString(),
                    x, y + 20, COLOR_MUTED);
            return;
        }

        y += 16;
        drawText(graphics, Component.translatable(EEConstants.langKey("screen.holdings.balance")).getString()
                + " " + money(market.balance()), x, y, COLOR_UP);
        drawSeparator(graphics, x, y + 12, contentW());

        int rowY = y + 20;
        // 表头
        drawText(graphics, Component.translatable(EEConstants.langKey("screen.exchange.col.fund")).getString(),
                x, rowY, COLOR_TITLE);
        drawRight(graphics, Component.translatable(EEConstants.langKey("screen.holdings.col.shares")).getString(),
                x + 150, rowY, COLOR_TITLE);
        drawRight(graphics, Component.translatable(EEConstants.langKey("screen.holdings.col.avg")).getString(),
                x + 210, rowY, COLOR_TITLE);
        drawRight(graphics, Component.translatable(EEConstants.langKey("screen.holdings.col.value")).getString(),
                x + 275, rowY, COLOR_TITLE);
        drawRight(graphics, Component.translatable(EEConstants.langKey("screen.holdings.col.profit")).getString(),
                x + contentW() - 60, rowY, COLOR_TITLE);
        drawRight(graphics, Component.translatable(EEConstants.langKey("screen.holdings.col.realized")).getString(),
                x + contentW(), rowY, COLOR_TITLE);
        drawSeparator(graphics, x, rowY + 11, contentW());

        int lineY = rowY + 16;
        long totalValue = 0L;
        long totalCost = 0L;
        for (FundType type : FundType.values()) {
            ServerPayloads.FundSnapshot fund = ExchangeScreen.findFund(market, type.ordinal());
            if (fund == null) {
                continue;
            }
            long shares = fund.shares();
            long value = shares * fund.price();
            long profit = value - fund.totalCost();
            totalValue += value;
            totalCost += fund.totalCost();

            drawText(graphics, type.displayName().getString(), x, lineY, shares > 0 ? COLOR_TEXT : COLOR_MUTED);
            drawRight(graphics, money(shares), x + 150, lineY, COLOR_TEXT);
            drawRight(graphics, money(shares > 0 ? fund.totalCost() / shares : 0L), x + 210, lineY, COLOR_MUTED);
            drawRight(graphics, money(value), x + 275, lineY, COLOR_GOLD);
            drawRight(graphics, (profit >= 0 ? "§a+" : "§c") + money(profit), x + contentW() - 60, lineY,
                    profit >= 0 ? COLOR_UP : COLOR_DOWN);
            drawRight(graphics, (fund.realized() >= 0 ? "§a+" : "§c") + money(fund.realized()),
                    x + contentW(), lineY, fund.realized() >= 0 ? COLOR_UP : COLOR_DOWN);
            lineY += 14;
        }

        drawSeparator(graphics, x, lineY + 2, contentW());
        long totalProfit = totalValue - totalCost;
        drawText(graphics, Component.translatable(EEConstants.langKey("screen.holdings.total_value")).getString()
                + " " + money(totalValue) + "   "
                + Component.translatable(EEConstants.langKey("screen.holdings.total_cost")).getString()
                + " " + money(totalCost) + "   "
                + Component.translatable(EEConstants.langKey("screen.holdings.total_profit")).getString()
                + " " + (totalProfit >= 0 ? "§a+" : "§c") + money(totalProfit),
                x, lineY + 8, totalProfit >= 0 ? COLOR_UP : COLOR_DOWN);
    }
}
