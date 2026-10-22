package cn.daenx.mc.emeraldeconomy.client.screen;

import cn.daenx.mc.emeraldeconomy.client.ClientEconomyCache;
import cn.daenx.mc.emeraldeconomy.client.widget.NumberBox;
import cn.daenx.mc.emeraldeconomy.core.Amounts;
import cn.daenx.mc.emeraldeconomy.core.EEConstants;
import cn.daenx.mc.emeraldeconomy.network.ScreenType;
import cn.daenx.mc.emeraldeconomy.market.FundType;
import cn.daenx.mc.emeraldeconomy.network.payload.ClientPayloads;
import cn.daenx.mc.emeraldeconomy.network.payload.ServerPayloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

/**
 * 交易弹窗（需求文档 3.5.2）。
 * <p>
 * 显示基金名称、当前价格、数量输入框、预计花费 / 获得与手续费。
 * 卖出页还会显示自己当前持有的份额，并提供「全部卖出」按钮。
 * 这里的金额只是「预估」，真正的结算与校验都在服务端。
 */
public class TradeScreen extends EEScreen {

    private final Screen parent;
    private final FundType fund;
    private final boolean buy;
    private NumberBox quantityBox;

    /** 该界面对应的服务端快照类型。 */
    @Override
    protected ScreenType screenType() {
        return ScreenType.EXCHANGE;
    }

    public TradeScreen(Screen parent, FundType fund, boolean buy) {
        super(Component.translatable(EEConstants.langKey("screen.trade.title")));
        this.parent = parent;
        this.fund = fund;
        this.buy = buy;
    }

    @Override
    protected int maxPanelWidth() {
        return 320;
    }

    @Override
    protected int maxPanelHeight() {
        return 184;
    }

    @Override
    protected void init() {
        super.init();
        int x = contentX();
        int boxY = panelY + PAD + 74;
        quantityBox = addRenderableWidget(new NumberBox(this.font, x, boxY, 90, 18, "1"));

        // 输入框下方留一行「最多能买/卖多少份」的提示，按钮再往下让开这一行
        int buttonY = boxY + 36;
        addRenderableWidget(Button.builder(
                        Component.translatable(EEConstants.langKey("screen.common.confirm")),
                        b -> confirm())
                .bounds(x, buttonY, 84, 18).build());
        if (!buy) {
            // 卖出页多一个「全部卖出」：一键把当前持有的份额全部卖掉
            addRenderableWidget(Button.builder(
                            Component.translatable(EEConstants.langKey("screen.trade.sell_all")),
                            b -> sellAll())
                    .bounds(x + 90, buttonY, 84, 18).build());
        }
        addRenderableWidget(Button.builder(
                        Component.translatable(EEConstants.langKey("screen.common.cancel")),
                        b -> this.minecraft.setScreen(parent))
                .bounds(x + contentW() - 84, buttonY, 84, 18).build());
    }

    private void confirm() {
        long quantity = quantityBox.longValue();
        if (quantity <= 0L) {
            return;
        }
        send(quantity);
    }

    /** 全部卖出：份额从服务端快照里读，客户端只负责发起请求。 */
    private void sellAll() {
        ServerPayloads.FundSnapshot snapshot = snapshot();
        long shares = snapshot == null ? 0L : snapshot.shares();
        // 每日限售优先：超出今日剩余额度时只卖能卖的那部分，避免发一个必然被拒的请求
        ServerPayloads.SyncMarket market = ClientEconomyCache.market;
        if (market != null && market.dailySellLimit() > 0) {
            shares = Math.min(shares, Math.max(0L, market.dailySellRemaining()));
        }
        if (shares <= 0L) {
            return;
        }
        send(shares);
    }

    private void send(long quantity) {
        PacketDistributor.sendToServer(new ClientPayloads.FundTradeRequest(fund.ordinal(), buy, quantity));
        this.minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private ServerPayloads.FundSnapshot snapshot() {
        return ExchangeScreen.findFund(ClientEconomyCache.market, fund.ordinal());
    }

    /**
     * 今日买卖额度提示。
     * <p>
     * 配置里把上限设为 0 表示不限制，此时显示「今日买卖份额不限」。
     */
    private static String dailyLimitText(ServerPayloads.SyncMarket market, boolean buy) {
        if (market == null) {
            return "";
        }
        int limit = buy ? market.dailyBuyLimit() : market.dailySellLimit();
        if (limit <= 0) {
            return Component.translatable(
                    EEConstants.langKey("screen.trade.daily_unlimited")).getString();
        }
        long remaining = buy ? market.dailyBuyRemaining() : market.dailySellRemaining();
        return Component.translatable(
                EEConstants.langKey(buy ? "screen.trade.daily_buy" : "screen.trade.daily_sell"),
                money(remaining), limit).getString();
    }

    @Override
    protected void drawContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = contentX();
        int y = panelY + PAD;
        drawTitle(graphics, Component.translatable(EEConstants.langKey(
                buy ? "screen.trade.title.buy" : "screen.trade.title.sell")));

        y += 18;
        drawText(graphics, fund.displayName().getString() + " §7(" + fund.code() + ")",
                x, y, COLOR_TEXT);

        ServerPayloads.SyncMarket market = ClientEconomyCache.market;
        ServerPayloads.FundSnapshot snapshot = snapshot();
        int price = snapshot == null ? 0 : snapshot.price();
        double feeRate = market == null ? 0.0D : market.tradeFeeRate();

        y += 14;
        drawText(graphics, Component.translatable(EEConstants.langKey("screen.trade.price")).getString()
                + " " + price, x, y, COLOR_GOLD);

        y += 14;
        if (!buy) {
            long shares = snapshot == null ? 0L : snapshot.shares();
            drawText(graphics, Component.translatable(EEConstants.langKey("screen.trade.holding"),
                    money(shares)).getString(), x, y, shares > 0L ? COLOR_TEXT : COLOR_MUTED);
        }

        y += 14;
        drawText(graphics, Component.translatable(EEConstants.langKey("screen.trade.quantity")).getString(),
                x, y, COLOR_TEXT);

        // 输入框下方：买入时提示「余额最多能买多少份」，并附带今日剩余额度
        int hintY = panelY + PAD + 74 + 22;
        if (buy) {
            long balance = market == null ? 0L : market.balance();
            long maxBuy = Amounts.affordableQuantity(balance, price, feeRate);
            drawTrimmed(graphics, Component.translatable(
                            EEConstants.langKey("screen.trade.max_buy"), money(maxBuy)).getString()
                            + " · " + dailyLimitText(market, true),
                    x, hintY, COLOR_MUTED, contentW());
        } else {
            drawTrimmed(graphics, dailyLimitText(market, false), x, hintY, COLOR_MUTED, contentW());
        }

        // 右侧实时预估
        long quantity = quantityBox == null ? 0L : quantityBox.longValue();
        long gross = (long) price * quantity;
        long fee = Math.min(gross, Amounts.fee(gross, feeRate));
        long amount = buy ? gross + fee : Math.max(0L, gross - fee);

        int infoY = panelY + PAD + 18;
        drawText(graphics, Component.translatable(EEConstants.langKey("screen.trade.gross")).getString()
                + " " + money(gross), x + 120, infoY, COLOR_TEXT);
        drawText(graphics, Component.translatable(EEConstants.langKey("screen.trade.fee")).getString()
                + " " + money(fee), x + 120, infoY + 12, COLOR_MUTED);
        drawText(graphics, Component.translatable(EEConstants.langKey(
                        buy ? "screen.trade.cost" : "screen.trade.gain")).getString()
                + " " + money(amount), x + 120, infoY + 24, buy ? COLOR_DOWN : COLOR_UP);

        if (market != null && market.balance() < (buy ? amount : 0L)) {
            drawText(graphics, String.format(Locale.ROOT,
                            Component.translatable(EEConstants.langKey("screen.trade.not_enough")).getString(),
                            money(market.balance())),
                    x, panelY + panelH - 16, COLOR_DOWN);
        }
    }
}
