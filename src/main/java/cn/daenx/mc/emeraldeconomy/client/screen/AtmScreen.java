package cn.daenx.mc.emeraldeconomy.client.screen;

import cn.daenx.mc.emeraldeconomy.client.ClientEconomyCache;
import cn.daenx.mc.emeraldeconomy.client.widget.NumberBox;
import cn.daenx.mc.emeraldeconomy.core.EEConstants;
import cn.daenx.mc.emeraldeconomy.network.ScreenType;
import cn.daenx.mc.emeraldeconomy.network.payload.ClientPayloads;
import cn.daenx.mc.emeraldeconomy.network.payload.ServerPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

/**
 * ATM 界面（需求文档 2.6）。
 * <p>
 * 显示余额、欠款、信用额度、利率与手续费率，并提供存款 / 取款 / 贷款 / 还款四个入口。
 * 所有金额校验都在服务端完成，这里只负责收集输入并发请求。
 */
public class AtmScreen extends EEScreen {

    /** 四个操作各自的输入框。 */
    private NumberBox depositBox;
    private NumberBox withdrawBox;
    private NumberBox loanBox;
    private NumberBox repayBox;

    /** 该界面对应的服务端快照类型。 */
    @Override
    protected ScreenType screenType() {
        return ScreenType.ATM;
    }

    public AtmScreen() {
        super(Component.translatable(EEConstants.langKey("screen.atm.title")));
    }

    @Override
    protected void init() {
        super.init();

        int x = contentX();
        int y = panelY + PAD + 62;
        int boxWidth = 100;
        int buttonWidth = 64;
        int allButtonWidth = 72;

        depositBox = addRenderableWidget(new NumberBox(this.font, x + 62, y, boxWidth, 18, "10"));
        addRenderableWidget(Button.builder(label("screen.atm.button.deposit"),
                        b -> send(ClientPayloads.AtmAction.DEPOSIT, depositBox))
                .bounds(x + 62 + boxWidth + 6, y, buttonWidth, 18).build());
        addRenderableWidget(Button.builder(label("screen.atm.button.deposit_all"),
                        b -> sendAll(ClientPayloads.AtmAction.DEPOSIT_ALL))
                .bounds(x + 62 + boxWidth + 6 + buttonWidth + 4, y, allButtonWidth, 18).build());
        y += 24;

        withdrawBox = addRenderableWidget(new NumberBox(this.font, x + 62, y, boxWidth, 18, "10"));
        addRenderableWidget(Button.builder(label("screen.atm.button.withdraw"),
                        b -> send(ClientPayloads.AtmAction.WITHDRAW, withdrawBox))
                .bounds(x + 62 + boxWidth + 6, y, buttonWidth, 18).build());
        addRenderableWidget(Button.builder(label("screen.atm.button.withdraw_all"),
                        b -> sendAll(ClientPayloads.AtmAction.WITHDRAW_ALL))
                .bounds(x + 62 + boxWidth + 6 + buttonWidth + 4, y, allButtonWidth, 18).build());
        y += 24;

        loanBox = addRenderableWidget(new NumberBox(this.font, x + 62, y, boxWidth, 18, "100"));
        addRenderableWidget(Button.builder(label("screen.atm.button.loan"),
                        b -> send(ClientPayloads.AtmAction.LOAN, loanBox))
                .bounds(x + 62 + boxWidth + 6, y, buttonWidth, 18).build());
        y += 24;

        repayBox = addRenderableWidget(new NumberBox(this.font, x + 62, y, boxWidth, 18, "100"));
        addRenderableWidget(Button.builder(label("screen.atm.button.repay"),
                        b -> send(ClientPayloads.AtmAction.REPAY, repayBox))
                .bounds(x + 62 + boxWidth + 6, y, buttonWidth, 18).build());

        // 「交易记录」按钮：点开后是独立的界面，列出这个账户全部流水
        addRenderableWidget(Button.builder(
                        Component.translatable(EEConstants.langKey("screen.atm.records"),
                                recordCount()),
                        b -> Minecraft.getInstance().setScreen(new TransactionScreen()))
                .bounds(x, recordButtonY(), 160, 18).build());
    }

    /** 「交易记录」按钮的纵坐标（在费率信息下方）。 */
    private int recordButtonY() {
        return panelY + PAD + 62 + 104 + 40;
    }

    /** 当前账户的流水条数（快照还没到时显示 0）。 */
    private static int recordCount() {
        ServerPayloads.SyncAccount account = ClientEconomyCache.account;
        return account == null ? 0 : account.transactions().size();
    }

    private void send(ClientPayloads.AtmAction action, NumberBox box) {
        long amount = box.longValue();
        if (amount <= 0L) {
            return;
        }
        PacketDistributor.sendToServer(new ClientPayloads.AtmActionRequest(action.ordinal(), amount));
    }

    /** 「存入全部 / 取出全部」不需要金额，服务端自己算。 */
    private void sendAll(ClientPayloads.AtmAction action) {
        PacketDistributor.sendToServer(new ClientPayloads.AtmActionRequest(action.ordinal(), 0L));
    }

    @Override
    protected void drawContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = contentX();
        int y = panelY + PAD;
        drawTitle(graphics, Component.translatable(EEConstants.langKey("screen.atm.title")));

        ServerPayloads.SyncAccount account = ClientEconomyCache.account;
        if (account == null) {
            drawText(graphics, Component.translatable(EEConstants.langKey("screen.loading")).getString(),
                    x, y + 24, COLOR_MUTED);
            return;
        }

        y += 16;
        drawText(graphics, label("screen.atm.balance").getString() + " " + money(account.balance()),
                x, y, COLOR_UP);
        drawRight(graphics, label("screen.atm.debt").getString() + " " + money(account.debt()),
                x + contentW(), y, account.debt() > 0 ? COLOR_DOWN : COLOR_TEXT);
        y += 12;
        drawText(graphics, label("screen.atm.credit").getString() + " " + money(account.creditLimit()),
                x, y, COLOR_TEXT);
        drawRight(graphics, label("screen.atm.available").getString() + " " + money(account.availableCredit()),
                x + contentW(), y, COLOR_GOLD);
        y += 15;
        drawSeparator(graphics, x, y, contentW());

        // 四个操作行的标签
        int rowY = panelY + PAD + 62;
        drawText(graphics, label("screen.atm.label.deposit").getString(), x, rowY + 5, COLOR_TEXT);
        drawText(graphics, label("screen.atm.label.withdraw").getString(), x, rowY + 29, COLOR_TEXT);
        drawText(graphics, label("screen.atm.label.loan").getString(), x, rowY + 53, COLOR_TEXT);
        drawText(graphics, label("screen.atm.label.repay").getString(), x, rowY + 77, COLOR_TEXT);

        // 费率信息
        int infoY = rowY + 104;
        drawText(graphics, String.format(Locale.ROOT,
                        "%s %.2f%%   %s %.2f%%",
                        label("screen.atm.fee_deposit").getString(), account.depositFeeRate() * 100.0D,
                        label("screen.atm.fee_withdraw").getString(), account.withdrawFeeRate() * 100.0D),
                x, infoY, COLOR_MUTED);
        drawText(graphics, String.format(Locale.ROOT,
                        "%s %.3f%%   %s %.2f%%",
                        label("screen.atm.rate_daily").getString(), account.dailyInterestRate() * 100.0D,
                        label("screen.atm.rate_loan").getString(), account.loanInterestRate() * 100.0D),
                x, infoY + 12, COLOR_MUTED);
        drawText(graphics, Component.translatable(EEConstants.langKey("screen.atm.interest_line"),
                        money(account.interestMinBalance())).getString(),
                x, infoY + 24, COLOR_MUTED);
    }

    private static Component label(String path) {
        return Component.translatable(EEConstants.langKey(path));
    }
}
