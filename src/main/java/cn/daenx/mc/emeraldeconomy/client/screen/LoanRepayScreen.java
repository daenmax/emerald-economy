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
 * ATM · 贷款和还款子页面。
 * <p>
 * 信用额度、可贷额度、贷款日息（复利）的说明都在这一页。
 */
public class LoanRepayScreen extends AtmSubScreen {

    private static final int ACTION_ROW_H = 22;
    private static final int ROW_TOP_OFFSET = 86;

    private NumberBox loanBox;
    private NumberBox repayBox;

    public LoanRepayScreen() {
        super(Component.translatable(EEConstants.langKey("screen.atm.loan_repay.title")));
    }

    @Override
    protected int maxPanelHeight() {
        return 236;
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

        loanBox = addRenderableWidget(new NumberBox(this.font, x + 62, y, boxWidth, 18, "100"));
        addRenderableWidget(Button.builder(label("screen.atm.button.loan"),
                        b -> send(ClientPayloads.AtmAction.LOAN, loanBox))
                .bounds(x + 62 + boxWidth + 6, y, buttonWidth, 18).build());

        y += ACTION_ROW_H;
        repayBox = addRenderableWidget(new NumberBox(this.font, x + 62, y, boxWidth, 18, "100"));
        addRenderableWidget(Button.builder(label("screen.atm.button.repay"),
                        b -> send(ClientPayloads.AtmAction.REPAY, repayBox))
                .bounds(x + 62 + boxWidth + 6, y, buttonWidth, 18).build());

        addBackButton();
    }

    private void send(ClientPayloads.AtmAction action, NumberBox box) {
        long amount = box.longValue();
        if (amount <= 0L) {
            return;
        }
        PacketDistributor.sendToServer(new ClientPayloads.AtmActionRequest(action.ordinal(), amount));
    }

    @Override
    protected void drawContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = contentX();
        int y = panelY + PAD;
        drawTitle(graphics, label("screen.atm.loan_repay.title"));

        ServerPayloads.SyncAccount account = ClientEconomyCache.account;
        if (account == null) {
            drawText(graphics, label("screen.loading").getString(), x, y + 20, COLOR_MUTED);
            return;
        }

        y += 18;
        drawText(graphics, label("screen.atm.debt").getString() + " " + money(account.debt()),
                x, y, account.debt() > 0 ? COLOR_DOWN : COLOR_TEXT);
        drawRight(graphics, label("screen.atm.available").getString() + " " + money(account.availableCredit()),
                x + contentW(), y, COLOR_GOLD);

        y += 14;
        drawText(graphics, label("screen.atm.credit").getString() + " " + money(account.creditLimit()),
                x, y, COLOR_TEXT);
        drawRight(graphics, String.format(Locale.ROOT, "%s %.3f%%",
                        label("screen.atm.rate_loan").getString(), account.loanInterestRate() * 100.0D),
                x + contentW(), y, COLOR_MUTED);

        y += 14;
        drawText(graphics, label("screen.atm.loan_repay.hint").getString(), x, y, COLOR_MUTED);

        y += 12;
        drawSeparator(graphics, x, y, contentW());

        int rowY = rowTop();
        drawText(graphics, label("screen.atm.label.loan").getString(), x, rowY + 5, COLOR_TEXT);
        drawText(graphics, label("screen.atm.label.repay").getString(), x, rowY + ACTION_ROW_H + 5,
                COLOR_TEXT);
        drawText(graphics, label("screen.atm.loan_repay.repay_hint").getString(), x,
                rowY + ACTION_ROW_H + 26, COLOR_MUTED);
    }
}
