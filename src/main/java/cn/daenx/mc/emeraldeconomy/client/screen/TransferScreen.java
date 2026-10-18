package cn.daenx.mc.emeraldeconomy.client.screen;

import cn.daenx.mc.emeraldeconomy.client.ClientEconomyCache;
import cn.daenx.mc.emeraldeconomy.client.widget.NumberBox;
import cn.daenx.mc.emeraldeconomy.core.Amounts;
import cn.daenx.mc.emeraldeconomy.core.EEConstants;
import cn.daenx.mc.emeraldeconomy.network.payload.ClientPayloads;
import cn.daenx.mc.emeraldeconomy.network.payload.ServerPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

/**
 * ATM · 转账子页面（从排行榜某一行点「转账」进入）。
 * <p>
 * 手续费从转账金额里扣：转 1000、费率 1% 时，对方实际到账 990。
 * 这里展示的「预计到账」只是给玩家看的预估值，真正的计算与扣款在服务端完成。
 */
public class TransferScreen extends AtmSubScreen {

    private final String targetId;
    private final String targetName;
    private NumberBox amountBox;

    public TransferScreen(String targetId, String targetName) {
        super(Component.translatable(EEConstants.langKey("screen.atm.transfer.title")));
        this.targetId = targetId == null ? "" : targetId;
        this.targetName = targetName == null || targetName.isBlank() ? this.targetId : targetName;
    }

    @Override
    protected int maxPanelHeight() {
        return 218;
    }

    @Override
    protected void init() {
        super.init();
        int x = contentX();
        int y = panelY + PAD + 76;

        amountBox = addRenderableWidget(new NumberBox(this.font, x + 62, y, 100, 18, "100"));
        addRenderableWidget(Button.builder(
                        Component.translatable(EEConstants.langKey("screen.atm.transfer.confirm")),
                        b -> confirm())
                .bounds(x + 62 + 100 + 6, y, 72, 18).build());

        addRenderableWidget(Button.builder(Component.translatable(EEConstants.langKey("screen.common.back")),
                        b -> Minecraft.getInstance().setScreen(new RankingScreen()))
                .bounds(x, panelY + panelH - 24, 60, 18).build());
    }

    private void confirm() {
        long amount = amountBox == null ? 0L : amountBox.longValue();
        if (amount <= 0L || targetId.isBlank()) {
            return;
        }
        PacketDistributor.sendToServer(new ClientPayloads.AtmTransferRequest(targetId, amount));
        // 回到排行榜；服务端处理完会把结果提示推回来，显示在界面底部
        Minecraft.getInstance().setScreen(new RankingScreen());
    }

    @Override
    protected void drawContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = contentX();
        int y = panelY + PAD;
        drawTitle(graphics, label("screen.atm.transfer.title"));

        ServerPayloads.SyncAccount account = ClientEconomyCache.account;
        if (account == null) {
            drawText(graphics, label("screen.loading").getString(), x, y + 20, COLOR_MUTED);
            return;
        }

        y += 18;
        drawText(graphics, Component.translatable(EEConstants.langKey("screen.atm.transfer.target"),
                targetName).getString(), x, y, COLOR_GOLD);

        y += 14;
        drawText(graphics, label("screen.atm.balance").getString() + " " + money(account.balance()),
                x, y, COLOR_UP);
        drawRight(graphics, Component.translatable(EEConstants.langKey("screen.atm.transfer.fee_rate"),
                        String.format(Locale.ROOT, "%.2f", account.transferFeeRate() * 100.0D)).getString(),
                x + contentW(), y, COLOR_MUTED);

        long amount = amountBox == null ? 0L : amountBox.longValue();
        long fee = amount <= 0L ? 0L : Math.min(amount,
                Amounts.fee(amount, account.transferFeeRate()));
        long received = Math.max(0L, amount - fee);

        y += 14;
        drawText(graphics, Component.translatable(EEConstants.langKey("screen.atm.transfer.received"),
                money(received)).getString(), x, y, COLOR_UP);
        drawRight(graphics, Component.translatable(EEConstants.langKey("screen.atm.transfer.fee"),
                money(fee)).getString(), x + contentW(), y, COLOR_MUTED);

        int rowY = panelY + PAD + 76;
        drawText(graphics, label("screen.atm.transfer.amount").getString(), x, rowY + 5, COLOR_TEXT);

        drawText(graphics, label("screen.atm.transfer.hint").getString(), x, rowY + 28, COLOR_MUTED);
        drawText(graphics, label("screen.atm.transfer.hint2").getString(), x, rowY + 40, COLOR_MUTED);
    }
}
