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
 * 显示余额、欠款、信用额度、利率与手续费率，并提供五组操作：
 * <ul>
 *     <li>存款 / 取款 / 存入全部 / 取出全部</li>
 *     <li>贷款 / 还款</li>
 *     <li><b>等级存取</b>：默认关闭，付费解锁后可以把「等级」存进 ATM 或取回
 *         （搬的是等级不是经验值）</li>
 *     <li><b>在线奖励</b>：每日结算时在线的玩家当天可以领一次「+N 级」</li>
 * </ul>
 * 所有金额、等级校验都在服务端完成，这里只负责收集输入并发请求。
 * <p>
 * 布局上把每个区块的起点都抽成了方法（{@link #rowTop()} 等），
 * 控件摆放与文字绘制用的是同一套坐标，避免「多加一行就错位」。
 */
public class AtmScreen extends EEScreen {

    /** 四个存取款 / 贷款行的行高。 */
    private static final int ACTION_ROW_H = 22;
    /** 每个区块之间留白。 */
    private static final int SECTION_GAP = 14;

    /** 四个操作各自的输入框。 */
    private NumberBox depositBox;
    private NumberBox withdrawBox;
    private NumberBox loanBox;
    private NumberBox repayBox;
    /** 等级存取的输入框（未解锁时不创建）。 */
    private NumberBox levelBox;

    /** 该界面对应的服务端快照类型。 */
    @Override
    protected ScreenType screenType() {
        return ScreenType.ATM;
    }

    public AtmScreen() {
        super(Component.translatable(EEConstants.langKey("screen.atm.title")));
    }

    @Override
    protected int maxPanelHeight() {
        return 320;
    }

    // ------------------------------------------------------------------
    // 区块坐标：控件与文字共用同一套（改布局只改这里）
    // ------------------------------------------------------------------

    /** 第一行操作（存款）的纵坐标（相对面板左上角）。 */
    private static final int ROW_TOP_OFFSET = 78;

    /** 第一行操作（存款）的纵坐标。 */
    private int rowTop() {
        return panelY + PAD + ROW_TOP_OFFSET;
    }

    /** 「等级存取」区块的标题纵坐标。 */
    private int levelTop() {
        return rowTop() + ACTION_ROW_H * 4 + SECTION_GAP;
    }

    /** 「在线奖励」区块的标题纵坐标。 */
    private int onlineTop() {
        return levelTop() + 52;
    }

    /** 「交易记录」按钮的纵坐标（在「在线奖励」的领取按钮下方）。 */
    private int recordButtonY() {
        return onlineTop() + 46;
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
        y += ACTION_ROW_H;

        loanBox = addRenderableWidget(new NumberBox(this.font, x + 62, y, boxWidth, 18, "100"));
        addRenderableWidget(Button.builder(label("screen.atm.button.loan"),
                        b -> send(ClientPayloads.AtmAction.LOAN, loanBox))
                .bounds(x + 62 + boxWidth + 6, y, buttonWidth, 18).build());
        y += ACTION_ROW_H;

        repayBox = addRenderableWidget(new NumberBox(this.font, x + 62, y, boxWidth, 18, "100"));
        addRenderableWidget(Button.builder(label("screen.atm.button.repay"),
                        b -> send(ClientPayloads.AtmAction.REPAY, repayBox))
                .bounds(x + 62 + boxWidth + 6, y, buttonWidth, 18).build());

        ServerPayloads.SyncAccount account = ClientEconomyCache.account;
        int levelControlsY = levelTop() + 24;
        if (account != null && account.levelUnlocked()) {
            // 已解锁：等级输入框 + 存入 / 取出
            levelBox = addRenderableWidget(new NumberBox(this.font, x + 62, levelControlsY, boxWidth, 18, "1"));
            addRenderableWidget(Button.builder(label("screen.atm.level.deposit"),
                            b -> send(ClientPayloads.AtmAction.LEVEL_DEPOSIT, levelBox))
                    .bounds(x + 62 + boxWidth + 6, levelControlsY, buttonWidth, 18).build());
            addRenderableWidget(Button.builder(label("screen.atm.level.withdraw"),
                            b -> send(ClientPayloads.AtmAction.LEVEL_WITHDRAW, levelBox))
                    .bounds(x + 62 + boxWidth + 6 + buttonWidth + 4, levelControlsY, allButtonWidth, 18).build());
        } else {
            // 未解锁：只有一个付费解锁按钮
            long cost = account == null ? 0L : account.levelUnlockCost();
            addRenderableWidget(Button.builder(
                            Component.translatable(EEConstants.langKey("screen.atm.level.unlock"), money(cost)),
                            b -> PacketDistributor.sendToServer(new ClientPayloads.AtmActionRequest(
                                    ClientPayloads.AtmAction.LEVEL_UNLOCK.ordinal(), 0L)))
                    .bounds(x + 62, levelControlsY, 160, 18).build());
        }

        // 在线奖励：只有「今天有资格且没领过」时才给按钮
        if (account != null && account.onlineRewardReady()) {
            addRenderableWidget(Button.builder(label("screen.atm.online.claim"),
                            b -> PacketDistributor.sendToServer(new ClientPayloads.AtmActionRequest(
                                    ClientPayloads.AtmAction.CLAIM_ONLINE_REWARD.ordinal(), 0L)))
                    .bounds(x + 62, onlineTop() + 24, 160, 18).build());
        }

        // 「交易记录」按钮：点开后是独立的界面，列出这个账户全部流水
        addRenderableWidget(Button.builder(
                        Component.translatable(EEConstants.langKey("screen.atm.records"),
                                recordCount()),
                        b -> Minecraft.getInstance().setScreen(new TransactionScreen()))
                .bounds(x, recordButtonY(), 160, 18).build());
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

    /** 「存入全部 / 取出全部 / 解锁 / 领奖」不需要金额，服务端自己算。 */
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

        // 费率与利率：放在标题下方，两行写完，不再占用操作区的位置
        y += 12;
        drawText(graphics, String.format(Locale.ROOT,
                        "%s %.2f%%   %s %.2f%%",
                        label("screen.atm.fee_deposit").getString(), account.depositFeeRate() * 100.0D,
                        label("screen.atm.fee_withdraw").getString(), account.withdrawFeeRate() * 100.0D),
                x, y, COLOR_MUTED);
        y += 12;
        drawText(graphics, String.format(Locale.ROOT,
                        "%s %.3f%%   %s %.2f%%",
                        label("screen.atm.rate_daily").getString(), account.dailyInterestRate() * 100.0D,
                        label("screen.atm.rate_loan").getString(), account.loanInterestRate() * 100.0D),
                x, y, COLOR_MUTED);
        drawRight(graphics, Component.translatable(EEConstants.langKey("screen.atm.interest_line"),
                money(account.interestMinBalance())).getString(), x + contentW(), y, COLOR_MUTED);
        y += 15;
        drawSeparator(graphics, x, y, contentW());

        // 四个操作行的标签
        int rowY = rowTop();
        drawText(graphics, label("screen.atm.label.deposit").getString(), x, rowY + 5, COLOR_TEXT);
        drawText(graphics, label("screen.atm.label.withdraw").getString(), x, rowY + ACTION_ROW_H + 5, COLOR_TEXT);
        drawText(graphics, label("screen.atm.label.loan").getString(), x, rowY + ACTION_ROW_H * 2 + 5, COLOR_TEXT);
        drawText(graphics, label("screen.atm.label.repay").getString(), x, rowY + ACTION_ROW_H * 3 + 5, COLOR_TEXT);

        drawLevelSection(graphics, account);
        drawOnlineSection(graphics, account);
    }

    /** 「等级存取」区块。 */
    private void drawLevelSection(GuiGraphics graphics, ServerPayloads.SyncAccount account) {
        int x = contentX();
        int top = levelTop();
        drawSeparator(graphics, x, top - 6, contentW());
        drawText(graphics, label("screen.atm.level.title").getString(), x, top, COLOR_TITLE);
        if (account.levelUnlocked()) {
            drawText(graphics, Component.translatable(EEConstants.langKey("screen.atm.level.status"),
                    account.playerLevel(), account.storedLevels()).getString(), x, top + 12, COLOR_MUTED);
        } else {
            drawText(graphics, Component.translatable(EEConstants.langKey("screen.atm.level.locked"),
                    money(account.levelUnlockCost())).getString(), x, top + 12, COLOR_DOWN);
        }
    }

    /** 「在线奖励」区块。 */
    private void drawOnlineSection(GuiGraphics graphics, ServerPayloads.SyncAccount account) {
        int x = contentX();
        int top = onlineTop();
        drawSeparator(graphics, x, top - 6, contentW());
        drawText(graphics, label("screen.atm.online.title").getString(), x, top, COLOR_TITLE);

        String key;
        int color;
        if (account.onlineRewardReady()) {
            key = "screen.atm.online.ready";
            color = COLOR_UP;
        } else if (account.onlineRewardClaimed()) {
            key = "screen.atm.online.claimed";
            color = COLOR_MUTED;
        } else {
            key = "screen.atm.online.unavailable";
            color = COLOR_DOWN;
        }
        drawText(graphics, Component.translatable(EEConstants.langKey(key),
                account.onlineRewardLevels()).getString(), x, top + 12, color);
    }

    private static Component label(String path) {
        return Component.translatable(EEConstants.langKey(path));
    }
}
