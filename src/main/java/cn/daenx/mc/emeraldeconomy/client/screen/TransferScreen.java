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
 * ATM · 转账 / 经验赠送子页面（从排行榜某一行点「转账」或「赠送」进入）。
 * <p>
 * 余额转账：手续费从转账金额里扣（转 1000、费率 1% 时对方到账 990）。
 * 经验赠送：从自己 ATM 里已存的经验中扣除，不收费，全额加到对方 ATM 里。
 * 两种操作都要求对方在线；真正的校验与结算在服务端完成。
 */
public class TransferScreen extends AtmSubScreen {

    private final String targetId;
    private final String targetName;
    /** true 表示赠送经验，false 表示余额转账。 */
    private final boolean experience;
    private NumberBox amountBox;

    public TransferScreen(String targetId, String targetName) {
        this(targetId, targetName, false);
    }

    public TransferScreen(String targetId, String targetName, boolean experience) {
        super(Component.translatable(EEConstants.langKey(
                experience ? "screen.atm.gift.title" : "screen.atm.transfer.title")));
        this.targetId = targetId == null ? "" : targetId;
        this.targetName = targetName == null || targetName.isBlank() ? this.targetId : targetName;
        this.experience = experience;
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
                        Component.translatable(EEConstants.langKey(experience
                                ? "screen.atm.gift.confirm" : "screen.atm.transfer.confirm")),
                        b -> confirm())
                .bounds(x + 62 + 100 + 6, y, 72, 18).build());

        addRenderableWidget(Button.builder(Component.translatable(EEConstants.langKey("screen.common.back")),
                        b -> Minecraft.getInstance().setScreen(
                                new RankingScreen(experience ? 1 : 0)))
                .bounds(x, panelY + panelH - 24, 60, 18).build());
    }

    private void confirm() {
        long amount = amountBox == null ? 0L : amountBox.longValue();
        if (amount <= 0L || targetId.isBlank()) {
            return;
        }
        PacketDistributor.sendToServer(new ClientPayloads.AtmTransferRequest(targetId, amount,
                experience ? ClientPayloads.AtmTransferRequest.MODE_EXPERIENCE
                        : ClientPayloads.AtmTransferRequest.MODE_BALANCE));
        // 回到排行榜；服务端处理完会把结果提示推回来，显示在界面底部
        Minecraft.getInstance().setScreen(new RankingScreen(experience ? 1 : 0));
    }

    @Override
    protected void drawContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = contentX();
        int y = panelY + PAD;
        drawTitle(graphics, label(experience ? "screen.atm.gift.title" : "screen.atm.transfer.title"));

        ServerPayloads.SyncAccount account = ClientEconomyCache.account;
        if (account == null) {
            drawText(graphics, label("screen.loading").getString(), x, y + 20, COLOR_MUTED);
            return;
        }

        y += 18;
        drawText(graphics, Component.translatable(EEConstants.langKey("screen.atm.transfer.target"),
                targetName).getString(), x, y, COLOR_GOLD);

        y += 14;
        if (experience) {
            drawText(graphics, label("screen.atm.gift.current").getString() + " "
                    + money(account.storedExperience()), x, y, COLOR_GOLD);
            drawRight(graphics, label("screen.atm.ranking.gift_free").getString(),
                    x + contentW(), y, COLOR_MUTED);
        } else {
            drawText(graphics, label("screen.atm.balance").getString() + " " + money(account.balance()),
                    x, y, COLOR_UP);
            drawRight(graphics, Component.translatable(EEConstants.langKey("screen.atm.transfer.fee_rate"),
                            String.format(Locale.ROOT, "%.2f", account.transferFeeRate() * 100.0D)).getString(),
                    x + contentW(), y, COLOR_MUTED);
        }

        long amount = amountBox == null ? 0L : amountBox.longValue();
        long fee = experience || amount <= 0L ? 0L : Math.min(amount,
                Amounts.fee(amount, account.transferFeeRate()));
        long received = Math.max(0L, amount - fee);

        y += 14;
        drawText(graphics, Component.translatable(EEConstants.langKey(experience
                        ? "screen.atm.gift.received" : "screen.atm.transfer.received"),
                money(received)).getString(), x, y, COLOR_UP);
        drawRight(graphics, Component.translatable(EEConstants.langKey("screen.atm.transfer.fee"),
                money(fee)).getString(), x + contentW(), y, COLOR_MUTED);

        int rowY = panelY + PAD + 76;
        drawText(graphics, label(experience
                ? "screen.atm.gift.amount" : "screen.atm.transfer.amount").getString(), x, rowY + 5, COLOR_TEXT);

        drawText(graphics, label(experience
                ? "screen.atm.gift.hint" : "screen.atm.transfer.hint").getString(), x, rowY + 28, COLOR_MUTED);
        drawText(graphics, label(experience
                ? "screen.atm.gift.hint2" : "screen.atm.transfer.hint2").getString(), x, rowY + 40, COLOR_MUTED);
        drawText(graphics, label("screen.atm.transfer.hint_online").getString(), x, rowY + 52, COLOR_MUTED);
    }
}
