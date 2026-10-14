package cn.daenx.mc.emeraldeconomy.client.screen;

import cn.daenx.mc.emeraldeconomy.client.ClientEconomyCache;
import cn.daenx.mc.emeraldeconomy.client.widget.NumberBox;
import cn.daenx.mc.emeraldeconomy.core.EEConstants;
import cn.daenx.mc.emeraldeconomy.lottery.LotteryType;
import cn.daenx.mc.emeraldeconomy.network.ScreenType;
import cn.daenx.mc.emeraldeconomy.network.payload.ClientPayloads;
import cn.daenx.mc.emeraldeconomy.network.payload.ServerPayloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

/**
 * 抽奖界面（需求文档 4.5）。
 * <p>
 * 三个场次各占一块：场次名与赔率、今日状态、选项按钮、下注金额与确认按钮。
 * 选项按钮的宽度会随选项数量自动调整（猜尾数有 10 个选项）。
 * <p>
 * 「上次开奖」除了开奖数字，还要说明这次开奖给玩家带来的余额变化：
 * 赢了多少钱 / 参与但没中（余额不变）/ 上一期没参与。
 * <p>
 * 有未还清的贷款时界面照常打开，但不下注控件，只显示一条红色提示。
 */
public class LotteryScreen extends EEScreen {

    private static final int SECTION_H = 66;
    /** 有贷款时顶部多出的提示条高度。 */
    private static final int DEBT_BANNER_H = 12;

    /** 每个场次当前选中的选项，-1 表示还没选。 */
    private final int[] selected = {-1, -1, -1};
    /** 每个场次的下注金额输入框。 */
    private final NumberBox[] amountBoxes = new NumberBox[LotteryType.values().length];

    /** 该界面对应的服务端快照类型。 */
    @Override
    protected ScreenType screenType() {
        return ScreenType.LOTTERY;
    }

    public LotteryScreen() {
        super(Component.translatable(EEConstants.langKey("screen.lottery.title")));
    }

    @Override
    protected int maxPanelWidth() {
        return 340;
    }

    @Override
    protected int maxPanelHeight() {
        return 268;
    }

    /** 是否处于「有欠款、不能下注」的状态。 */
    private static boolean bettingBlocked() {
        ServerPayloads.SyncLottery lottery = ClientEconomyCache.lottery;
        return lottery != null && lottery.hasDebt();
    }

    /** 第一个场次区块的纵坐标（有欠款提示时整体下移）。 */
    private int sectionTop() {
        return panelY + PAD + 30 + (bettingBlocked() ? DEBT_BANNER_H : 0);
    }

    @Override
    protected void init() {
        super.init();

        int x = contentX();
        int top = sectionTop();
        boolean blocked = bettingBlocked();

        for (LotteryType type : LotteryType.values()) {
            int index = type.ordinal();
            int sectionY = top + index * SECTION_H;
            if (blocked) {
                // 有欠款：不摆放任何下注控件，玩家点不到自然也就下不了注
                continue;
            }
            int options = type.optionCount();
            int totalWidth = contentW() - 100;
            int optionWidth = Math.max(16, totalWidth / options);

            for (int option = 0; option < options; option++) {
                final int choice = option;
                final int typeIndex = index;
                addRenderableWidget(Button.builder(type.optionName(option), b -> {
                            selected[typeIndex] = choice;
                        })
                        .bounds(x + 96 + option * optionWidth, sectionY + 4, optionWidth - 2, 16).build());
            }

            amountBoxes[index] = addRenderableWidget(new NumberBox(this.font, x + 96, sectionY + 24, 70, 18, "10"));
            addRenderableWidget(Button.builder(
                            Component.translatable(EEConstants.langKey("screen.lottery.bet")),
                            b -> bet(type))
                    .bounds(x + 170, sectionY + 24, 60, 18).build());
        }
    }

    private void bet(LotteryType type) {
        int index = type.ordinal();
        int choice = selected[index];
        long amount = amountBoxes[index] == null ? 0L : amountBoxes[index].longValue();
        if (choice < 0 || amount <= 0L) {
            return;
        }
        PacketDistributor.sendToServer(
                new ClientPayloads.LotteryBetRequest(type.ordinal(), choice, amount));
    }

    @Override
    protected void drawContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = contentX();
        int y = panelY + PAD;
        drawTitle(graphics, Component.translatable(EEConstants.langKey("screen.lottery.title")));

        ServerPayloads.SyncLottery lottery = ClientEconomyCache.lottery;
        if (lottery == null) {
            drawText(graphics, Component.translatable(EEConstants.langKey("screen.loading")).getString(),
                    x, y + 20, COLOR_MUTED);
            return;
        }

        drawRight(graphics, Component.translatable(EEConstants.langKey("screen.exchange.balance")).getString()
                + " " + money(lottery.balance()), x + contentW(), y, COLOR_UP);
        y += 14;
        drawText(graphics, Component.translatable(EEConstants.langKey("screen.lottery.hint"),
                lottery.day()).getString(), x, y, COLOR_MUTED);

        if (lottery.hasDebt()) {
            // 提示条画在「第 N 天」那一行的下一行（sectionTop 会为它让出 12 像素的高度）
            int bannerY = y + 12;
            graphics.fill(x, bannerY - 1, x + contentW(), bannerY + DEBT_BANNER_H - 2, 0x50FF5E5E);
            drawTrimmed(graphics, Component.translatable(
                            EEConstants.langKey("screen.lottery.debt_blocked")).getString(),
                    x + 4, bannerY, COLOR_DOWN, contentW() - 8);
        }

        int top = sectionTop();
        for (ServerPayloads.LotterySlot slot : lottery.slots()) {
            LotteryType type = LotteryType.byIndex(slot.lotteryType());
            int index = type.ordinal();
            int sectionY = top + index * SECTION_H;
            boolean betted = slot.betChoice() >= 0;

            graphics.fill(x, sectionY - 2, x + contentW(), sectionY + SECTION_H - 4, COLOR_PANEL_ALT);

            drawText(graphics, type.displayName().getString(), x + 4, sectionY + 6, COLOR_TITLE);
            drawText(graphics, String.format(Locale.ROOT,
                            Component.translatable(EEConstants.langKey("screen.lottery.odds")).getString(),
                            slot.odds()),
                    x + 4, sectionY + 20, COLOR_MUTED);

            if (betted) {
                drawText(graphics, Component.translatable(EEConstants.langKey("screen.lottery.betted"),
                                type.optionName(slot.betChoice()), money(slot.betAmount())).getString(),
                        x + 4, sectionY + 34, COLOR_GOLD);
            } else if (lottery.hasDebt()) {
                drawText(graphics, Component.translatable(
                                EEConstants.langKey("screen.lottery.pick_blocked")).getString(),
                        x + 4, sectionY + 34, COLOR_DOWN);
            } else {
                drawText(graphics, Component.translatable(EEConstants.langKey(
                                "screen.lottery.pick." + type.name().toLowerCase(Locale.ROOT))).getString(),
                        x + 4, sectionY + 34, COLOR_MUTED);
            }

            if (slot.lastResult() >= 0) {
                drawText(graphics, lastDrawText(slot), x + 4, sectionY + 48, lastDrawColor(slot));
            }

            // 高亮当前选中的选项
            if (!betted && !lottery.hasDebt() && selected[index] >= 0) {
                int options = type.optionCount();
                int totalWidth = contentW() - 100;
                int optionWidth = Math.max(16, totalWidth / options);
                int hx = x + 96 + selected[index] * optionWidth;
                graphics.fill(hx - 1, sectionY + 3, hx + optionWidth - 1, sectionY + 21, 0x60FFD75E);
            }
        }
    }

    /**
     * 「上次开奖」那一行：开奖数字 + 这次开奖带来的余额变化。
     * <p>
     * {@code lastPrize} 为 -1 表示上一期没参与；为 0 表示参与了但没中；
     * 大于 0 就是开奖时加到账户上的奖金，也就是余额的增加量。
     */
    private static String lastDrawText(ServerPayloads.LotterySlot slot) {
        if (slot.lastPrize() > 0L) {
            return Component.translatable(EEConstants.langKey("screen.lottery.last_win"),
                    slot.lastResult(), money(slot.lastPrize())).getString();
        }
        if (slot.lastPrize() == 0L) {
            return Component.translatable(EEConstants.langKey("screen.lottery.last_lose"),
                    slot.lastResult(), money(slot.lastBet())).getString();
        }
        return Component.translatable(EEConstants.langKey("screen.lottery.last_none"),
                slot.lastResult()).getString();
    }

    /** 上次开奖那一行的颜色：赢了金色、没中灰色、未参与更暗。 */
    private static int lastDrawColor(ServerPayloads.LotterySlot slot) {
        if (slot.lastPrize() > 0L) {
            return COLOR_GOLD;
        }
        return slot.lastPrize() == 0L ? COLOR_MUTED : 0xFF808080;
    }
}
