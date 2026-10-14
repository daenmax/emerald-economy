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
        return 280;
    }

    @Override
    protected int maxPanelHeight() {
        return 150;
    }

    @Override
    protected void init() {
        super.init();
        int x = contentX();
        int boxY = panelY + PAD + 66;
        quantityBox = addRenderableWidget(new NumberBox(this.font, x, boxY, 90, 18, "1"));

        int buttonY = boxY + 30;
        addRenderableWidget(Button.builder(
                        Component.translatable(EEConstants.langKey("screen.common.confirm")),
                        b -> confirm())
                .bounds(x, buttonY, 80, 18).build());
        addRenderableWidget(Button.builder(
                        Component.translatable(EEConstants.langKey("screen.common.cancel")),
                        b -> this.minecraft.setScreen(parent))
                .bounds(x + contentW() - 80, buttonY, 80, 18).build());
    }

    private void confirm() {
        long quantity = quantityBox.longValue();
        if (quantity <= 0L) {
            return;
        }
        PacketDistributor.sendToServer(new ClientPayloads.FundTradeRequest(fund.ordinal(), buy, quantity));
        this.minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
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
        ServerPayloads.FundSnapshot snapshot = ExchangeScreen.findFund(market, fund.ordinal());
        int price = snapshot == null ? 0 : snapshot.price();
        double feeRate = market == null ? 0.0D : market.tradeFeeRate();

        y += 14;
        drawText(graphics, Component.translatable(EEConstants.langKey("screen.trade.price")).getString()
                + " " + price, x, y, COLOR_GOLD);

        y += 14;
        drawText(graphics, Component.translatable(EEConstants.langKey("screen.trade.quantity")).getString(),
                x, y, COLOR_TEXT);

        // 实时预估
        long quantity = quantityBox == null ? 0L : quantityBox.longValue();
        long gross = (long) price * quantity;
        long fee = Math.min(gross, Amounts.fee(gross, feeRate));
        long amount = buy ? gross + fee : Math.max(0L, gross - fee);

        int infoY = panelY + PAD + 66 - 30;
        drawText(graphics, Component.translatable(EEConstants.langKey("screen.trade.gross")).getString()
                + " " + money(gross), x + 96, infoY, COLOR_TEXT);
        drawText(graphics, Component.translatable(EEConstants.langKey("screen.trade.fee")).getString()
                + " " + money(fee), x + 96, infoY + 12, COLOR_MUTED);
        drawText(graphics, Component.translatable(EEConstants.langKey(
                        buy ? "screen.trade.cost" : "screen.trade.gain")).getString()
                + " " + money(amount), x + 96, infoY + 24, buy ? COLOR_DOWN : COLOR_UP);

        if (market != null && market.balance() < (buy ? amount : 0L)) {
            drawText(graphics, String.format(Locale.ROOT,
                            Component.translatable(EEConstants.langKey("screen.trade.not_enough")).getString(),
                            money(market.balance())),
                    x, panelY + panelH - 16, COLOR_DOWN);
        }
    }
}
