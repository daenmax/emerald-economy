package cn.daenx.mc.emeraldeconomy.client.screen;

import cn.daenx.mc.emeraldeconomy.client.ClientEconomyCache;
import cn.daenx.mc.emeraldeconomy.client.widget.NumberBox;
import cn.daenx.mc.emeraldeconomy.core.EEConstants;
import cn.daenx.mc.emeraldeconomy.network.payload.ClientPayloads;
import cn.daenx.mc.emeraldeconomy.network.payload.ServerPayloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

/**
 * ATM · 存款和取款子页面。
 * <p>
 * 存款 / 取款 / 存入全部 / 取出全部都在这里；手续费、账户日息、计息起算线
 * 这些「钱怎么算」的说明也放在这一页（需求 1.2：说明文字跟着对应功能走）。
 */
public class DepositWithdrawScreen extends AtmSubScreen {

    private static final int ACTION_ROW_H = 22;
    private static final int ROW_TOP_OFFSET = 74;

    private NumberBox depositBox;
    private NumberBox withdrawBox;

    public DepositWithdrawScreen() {
        super(Component.translatable(EEConstants.langKey("screen.atm.deposit_withdraw.title")));
    }

    @Override
    protected int maxPanelHeight() {
        return 220;
    }

    private int rowTop() {
        return panelY + PAD + ROW_TOP_OFFSET;
    }

    @Override
    protected void init() {
        super.init();
        int x = contentX();
        int y = rowTop();
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

        y += ACTION_ROW_H;
        withdrawBox = addRenderableWidget(new NumberBox(this.font, x + 62, y, boxWidth, 18, "10"));
        addRenderableWidget(Button.builder(label("screen.atm.button.withdraw"),
                        b -> send(ClientPayloads.AtmAction.WITHDRAW, withdrawBox))
                .bounds(x + 62 + boxWidth + 6, y, buttonWidth, 18).build());
        addRenderableWidget(Button.builder(label("screen.atm.button.withdraw_all"),
                        b -> sendAll(ClientPayloads.AtmAction.WITHDRAW_ALL))
                .bounds(x + 62 + boxWidth + 6 + buttonWidth + 4, y, allButtonWidth, 18).build());

        addBackButton();
    }

    private void send(ClientPayloads.AtmAction action, NumberBox box) {
        long amount = box.longValue();
        if (amount <= 0L) {
            return;
        }
        PacketDistributor.sendToServer(new ClientPayloads.AtmActionRequest(action.ordinal(), amount));
    }

    private void sendAll(ClientPayloads.AtmAction action) {
        PacketDistributor.sendToServer(new ClientPayloads.AtmActionRequest(action.ordinal(), 0L));
    }

    @Override
    protected void drawContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = contentX();
        int y = panelY + PAD;
        drawTitle(graphics, label("screen.atm.deposit_withdraw.title"));

        ServerPayloads.SyncAccount account = ClientEconomyCache.account;
        if (account == null) {
            drawText(graphics, label("screen.loading").getString(), x, y + 20, COLOR_MUTED);
            return;
        }

        y += 18;
        drawText(graphics, label("screen.atm.balance").getString() + " " + money(account.balance()),
                x, y, COLOR_UP);
        drawRight(graphics, label("screen.atm.debt").getString() + " " + money(account.debt()),
                x + contentW(), y, account.debt() > 0 ? COLOR_DOWN : COLOR_TEXT);

        y += 14;
        drawText(graphics, String.format(Locale.ROOT, "%s %.2f%%   %s %.2f%%",
                        label("screen.atm.fee_deposit").getString(), account.depositFeeRate() * 100.0D,
                        label("screen.atm.fee_withdraw").getString(), account.withdrawFeeRate() * 100.0D),
                x, y, COLOR_MUTED);

        y += 12;
        drawText(graphics, String.format(Locale.ROOT, "%s %.3f%%",
                        label("screen.atm.rate_daily").getString(), account.dailyInterestRate() * 100.0D),
                x, y, COLOR_MUTED);
        drawRight(graphics, Component.translatable(EEConstants.langKey("screen.atm.interest_line"),
                money(account.interestMinBalance())).getString(), x + contentW(), y, COLOR_MUTED);

        y += 12;
        drawText(graphics, label("screen.atm.deposit_withdraw.hint").getString(), x, y, COLOR_MUTED);

        y += 12;
        drawSeparator(graphics, x, y, contentW());

        int rowY = rowTop();
        drawText(graphics, label("screen.atm.label.deposit").getString(), x, rowY + 5, COLOR_TEXT);
        drawText(graphics, label("screen.atm.label.withdraw").getString(), x, rowY + ACTION_ROW_H + 5,
                COLOR_TEXT);
    }
}
