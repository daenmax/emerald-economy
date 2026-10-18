package cn.daenx.mc.emeraldeconomy.client.screen;

import cn.daenx.mc.emeraldeconomy.client.ClientEconomyCache;
import cn.daenx.mc.emeraldeconomy.client.widget.MiniChart;
import cn.daenx.mc.emeraldeconomy.core.EEConstants;
import cn.daenx.mc.emeraldeconomy.market.FundType;
import cn.daenx.mc.emeraldeconomy.network.ScreenType;
import cn.daenx.mc.emeraldeconomy.network.payload.ServerPayloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * 基金交易所列表界面（需求文档 3.5.1）。
 * <p>
 * 顶部是账户余额，中间是七支基金的价格与涨跌幅（点击某一行可选中），
 * 底部是所选基金的迷你走势图与「查看持仓 / 市场新闻」入口。
 */
public class ExchangeScreen extends EEScreen {

    /** 当前选中的基金（用于底部走势图）。 */
    private int selectedFund = 0;

    /** 列表区域起点（相对于面板）。 */
    private static final int LIST_OFFSET_Y = 42;

    /** 该界面对应的服务端快照类型。 */
    @Override
    protected ScreenType screenType() {
        return ScreenType.EXCHANGE;
    }

    public ExchangeScreen() {
        super(Component.translatable(EEConstants.langKey("screen.exchange.title")));
    }

    @Override
    protected int maxPanelWidth() {
        return 400;
    }

    @Override
    protected int maxPanelHeight() {
        return 262;
    }

    @Override
    protected void init() {
        super.init();

        int x = contentX();
        int rowY = panelY + LIST_OFFSET_Y;
        for (FundType type : FundType.values()) {
            int index = type.ordinal();
            int y = rowY + index * 18;
            addRenderableWidget(Button.builder(Component.translatable(EEConstants.langKey("screen.common.buy")),
                            b -> openTrade(index, true))
                    .bounds(x + 226, y, 30, 16).build());
            addRenderableWidget(Button.builder(Component.translatable(EEConstants.langKey("screen.common.sell")),
                            b -> openTrade(index, false))
                    .bounds(x + 260, y, 30, 16).build());
        }

        int bottom = panelY + panelH - 24;
        addRenderableWidget(Button.builder(
                        Component.translatable(EEConstants.langKey("screen.exchange.holdings")),
                        b -> this.minecraft.setScreen(new HoldingsScreen(this)))
                .bounds(x, bottom, 88, 18).build());
        addRenderableWidget(Button.builder(
                        Component.translatable(EEConstants.langKey("screen.exchange.news")),
                        b -> this.minecraft.setScreen(new MarketNewsScreen(this)))
                .bounds(x + 94, bottom, 88, 18).build());
        addRenderableWidget(Button.builder(
                        Component.translatable(EEConstants.langKey("screen.common.refresh")),
                        b -> requestData())
                .bounds(x + contentW() - 60, bottom, 60, 18).build());
    }

    private void openTrade(int fundIndex, boolean buy) {
        this.minecraft.setScreen(new TradeScreen(this, FundType.byIndex(fundIndex), buy));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 点击列表左侧区域选中基金，用于底部走势图
        int x = contentX();
        int rowY = panelY + LIST_OFFSET_Y;
        if (mouseX >= x && mouseX < x + 220) {
            int index = (int) ((mouseY - rowY) / 18);
            if (index >= 0 && index < FundType.values().length && mouseY >= rowY) {
                selectedFund = index;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void drawContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = contentX();
        int y = panelY + PAD;
        drawTitle(graphics, Component.translatable(EEConstants.langKey("screen.exchange.title")));

        ServerPayloads.SyncMarket market = ClientEconomyCache.market;
        if (market == null) {
            drawText(graphics, Component.translatable(EEConstants.langKey("screen.loading")).getString(),
                    x, y + 20, COLOR_MUTED);
            return;
        }

        drawRight(graphics, Component.translatable(EEConstants.langKey("screen.exchange.balance")).getString()
                        + " " + money(market.balance()),
                x + contentW(), y, COLOR_UP);
        y += 14;
        drawText(graphics, Component.translatable(EEConstants.langKey("screen.exchange.hint")).getString(),
                x, y, COLOR_MUTED);
        drawSeparator(graphics, x, y + 11, contentW());

        int rowY = panelY + LIST_OFFSET_Y;
        // 表头
        drawText(graphics, Component.translatable(EEConstants.langKey("screen.exchange.col.fund")).getString(),
                x + 10, rowY - 11, COLOR_TITLE);
        drawRight(graphics, Component.translatable(EEConstants.langKey("screen.exchange.col.price")).getString(),
                x + 168, rowY - 11, COLOR_TITLE);
        drawRight(graphics, Component.translatable(EEConstants.langKey("screen.exchange.col.change")).getString(),
                x + 220, rowY - 11, COLOR_TITLE);

        for (ServerPayloads.FundSnapshot fund : market.funds()) {
            int index = fund.fund();
            int rowTop = rowY + index * 18;
            FundType type = FundType.byIndex(index);
            boolean selected = index == selectedFund;

            if (selected) {
                graphics.fill(x, rowTop - 1, x + contentW(), rowTop + 16, 0x40FFD75E);
            }
            drawText(graphics, (selected ? "§e▸ §f" : "  ") + type.displayName().getString(),
                    x, rowTop + 4, COLOR_TEXT);
            drawText(graphics, "§7" + type.code(), x + 96, rowTop + 4, COLOR_MUTED);

            int price = fund.price();
            drawRight(graphics, String.valueOf(price), x + 168, rowTop + 4, COLOR_GOLD);

            double change = changeRatio(fund);
            drawRight(graphics, String.format(Locale.ROOT, "%s%.2f%%", change > 0 ? "+" : "", change * 100.0D),
                    x + 220, rowTop + 4, changeColor(change));
        }

        // 走势图
        int chartY = rowY + FundType.values().length * 18 + 4;
        int chartH = 42;
        ServerPayloads.FundSnapshot current = findFund(market, selectedFund);
        int[] history = current == null ? new int[0] : current.history();
        // 线条颜色跟着「这张图里」的涨跌走，而不是当日涨跌幅：
        // 结算刚完成时当日涨跌幅是 0，但图上可能明显在下跌，再用涨跌色就会画成灰色。
        int lineColor = current == null ? COLOR_FLAT : chartTrendColor(history);
        MiniChart.draw(graphics, x, chartY, contentW(), chartH, history, lineColor,
                current == null ? 0 : Math.max(1, current.previousClose()));

        FundType selectedType = FundType.byIndex(selectedFund);
        drawText(graphics, selectedType.displayName().getString()
                        + Component.translatable(EEConstants.langKey("screen.exchange.chart_suffix")).getString(),
                x + 2, chartY + chartH + 2, COLOR_MUTED);
    }

    private static double changeRatio(ServerPayloads.FundSnapshot fund) {
        int reference = fund.previousClose() > 0 ? fund.previousClose() : fund.price();
        if (reference <= 0) {
            return 0.0D;
        }
        return (double) (fund.price() - reference) / (double) reference;
    }

    /**
     * 走势图线条颜色：按当前窗口内最后一个点相对第一个点的涨跌着色。
     * <p>
     * 这样即使当日涨跌幅刚好是 0（例如每日结算刚完成），
     * 图上能看出来的上涨 / 下跌也会用绿 / 红画出来，而不是一律灰色。
     */
    private static int chartTrendColor(int[] history) {
        if (history == null || history.length < 2 || history[0] <= 0) {
            return COLOR_FLAT;
        }
        int first = history[0];
        int last = history[history.length - 1];
        return changeColor((double) (last - first) / (double) first);
    }

    /** 找到指定序号的基金快照。 */
    public static ServerPayloads.FundSnapshot findFund(ServerPayloads.SyncMarket market, int index) {
        if (market == null) {
            return null;
        }
        for (ServerPayloads.FundSnapshot fund : market.funds()) {
            if (fund.fund() == index) {
                return fund;
            }
        }
        return null;
    }
}
