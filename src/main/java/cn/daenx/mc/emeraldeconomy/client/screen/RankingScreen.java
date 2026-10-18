package cn.daenx.mc.emeraldeconomy.client.screen;

import cn.daenx.mc.emeraldeconomy.client.ClientEconomyCache;
import cn.daenx.mc.emeraldeconomy.core.EEConstants;
import cn.daenx.mc.emeraldeconomy.network.payload.ServerPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * ATM · 排行榜子页面。
 * <p>
 * 需求：每个游戏日更新一次（每日结算时刷新），按净值（账户余额 - 贷款欠款）排序；
 * 每一名玩家后面都有一个「转账」按钮，可以给对方转任意金额，转账收手续费。
 * <p>
 * 榜单数据来自服务端快照（{@link ServerPayloads.SyncAccount#ranking()}），
 * 客户端只负责展示；转账的校验与扣款全部在服务端完成。
 */
public class RankingScreen extends EEScreen {

    private static final int PAGE_SIZE = 10;
    private static final int ROW_H = 18;
    /** 各列相对内容区左边缘的位置（右对齐列用右边界坐标）。 */
    private static final int COL_RANK = 0;
    private static final int COL_NAME = 32;
    private static final int TRANSFER_WIDTH = 50;
    /** 转账按钮与欠款列之间、以及各数字列之间的间距（按内容宽度动态排布，窄窗口也不会顶出面板）。 */
    private static final int COL_GAP = 10;

    private int page;

    @Override
    protected cn.daenx.mc.emeraldeconomy.network.ScreenType screenType() {
        return cn.daenx.mc.emeraldeconomy.network.ScreenType.ATM;
    }

    public RankingScreen() {
        super(Component.translatable(EEConstants.langKey("screen.atm.ranking.title")));
    }

    @Override
    protected int maxPanelWidth() {
        return 500;
    }

    @Override
    protected int maxPanelHeight() {
        return 320;
    }

    /** 列表表头纵坐标。 */
    private int headerTop() {
        return panelY + PAD + 42;
    }

    /** 第一行数据的纵坐标。 */
    private int listTop() {
        return headerTop() + 16;
    }

    private static List<ServerPayloads.RankingEntry> ranking() {
        ServerPayloads.SyncAccount account = ClientEconomyCache.account;
        return account == null ? List.of() : account.ranking();
    }

    @Override
    protected void init() {
        super.init();
        int x = contentX();
        List<ServerPayloads.RankingEntry> entries = ranking();
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page >= pages) {
            page = pages - 1;
        }
        if (page < 0) {
            page = 0;
        }

        ServerPayloads.SyncAccount account = ClientEconomyCache.account;
        String selfId = account == null ? "" : account.playerId();
        int from = page * PAGE_SIZE;
        int to = Math.min(entries.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            ServerPayloads.RankingEntry entry = entries.get(i);
            int rowY = listTop() + (i - from) * ROW_H;
            Button transfer = Button.builder(
                            Component.translatable(EEConstants.langKey("screen.atm.ranking.transfer")),
                            b -> Minecraft.getInstance().setScreen(
                                    new TransferScreen(entry.playerId(), entry.name())))
                    .bounds(x + contentW() - TRANSFER_WIDTH, rowY - 1, TRANSFER_WIDTH, 16).build();
            // 不能给自己转账：自己的那一行按钮置灰
            transfer.active = !entry.playerId().equals(selfId);
            addRenderableWidget(transfer);
        }

        int bottom = panelY + panelH - 24;
        addRenderableWidget(Button.builder(Component.translatable(EEConstants.langKey("screen.common.back")),
                        b -> Minecraft.getInstance().setScreen(new AtmScreen()))
                .bounds(x, bottom, 60, 18).build());

        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("◀"), b -> {
                        page--;
                        this.rebuildWidgets();
                    })
                    .bounds(x + contentW() - 48, bottom, 22, 18).build());
            addRenderableWidget(Button.builder(Component.literal("▶"), b -> {
                        page++;
                        this.rebuildWidgets();
                    })
                    .bounds(x + contentW() - 24, bottom, 22, 18).build());
        }
    }

    @Override
    protected void drawContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = contentX();
        int y = panelY + PAD;
        drawTitle(graphics, Component.translatable(EEConstants.langKey("screen.atm.ranking.title")));

        ServerPayloads.SyncAccount account = ClientEconomyCache.account;
        if (account == null) {
            drawText(graphics, Component.translatable(EEConstants.langKey("screen.loading")).getString(),
                    x, y + 20, COLOR_MUTED);
            return;
        }

        y += 16;
        drawText(graphics, Component.translatable(EEConstants.langKey("screen.atm.ranking.hint"),
                account.rankingDay()).getString(), x, y, COLOR_MUTED);
        drawRight(graphics, Component.translatable(EEConstants.langKey("screen.atm.ranking.fee"),
                String.format(java.util.Locale.ROOT, "%.2f", account.transferFeeRate() * 100.0D)).getString(),
                x + contentW(), y, COLOR_GOLD);

        int headerY = headerTop();
        // 列位置按内容宽度动态算：窗口窄的时候数字列会一起往左收，不会跑到面板外面
        int buttonX = x + contentW() - TRANSFER_WIDTH;
        int debtRight = buttonX - COL_GAP;
        int balanceRight = debtRight - 78;
        int netRight = balanceRight - 88;
        int nameWidth = Math.max(60, netRight - (x + COL_NAME) - 8);

        drawSeparator(graphics, x, headerY - 3, contentW());
        drawText(graphics, Component.translatable(EEConstants.langKey("screen.atm.ranking.rank")).getString(),
                x + COL_RANK, headerY, COLOR_TITLE);
        drawText(graphics, Component.translatable(EEConstants.langKey("screen.atm.ranking.player")).getString(),
                x + COL_NAME, headerY, COLOR_TITLE);
        drawRight(graphics, Component.translatable(EEConstants.langKey("screen.atm.ranking.net")).getString(),
                netRight, headerY, COLOR_TITLE);
        drawRight(graphics, Component.translatable(EEConstants.langKey("screen.atm.ranking.balance")).getString(),
                balanceRight, headerY, COLOR_TITLE);
        drawRight(graphics, Component.translatable(EEConstants.langKey("screen.atm.ranking.debt")).getString(),
                debtRight, headerY, COLOR_TITLE);
        drawSeparator(graphics, x, headerY + 10, contentW());

        List<ServerPayloads.RankingEntry> entries = ranking();
        if (entries.isEmpty()) {
            drawText(graphics, Component.translatable(EEConstants.langKey("screen.atm.ranking.empty")).getString(),
                    x, headerY + 20, COLOR_MUTED);
            return;
        }

        int from = page * PAGE_SIZE;
        int to = Math.min(entries.size(), from + PAGE_SIZE);
        int rowY = listTop();
        String selfId = account.playerId();
        for (int i = from; i < to; i++) {
            ServerPayloads.RankingEntry entry = entries.get(i);
            boolean self = entry.playerId().equals(selfId);
            int rowColor = self ? COLOR_GOLD : (i % 2 == 0 ? COLOR_TEXT : 0xFFC0C0C0);
            drawText(graphics, String.valueOf(i + 1), x + COL_RANK, rowY + 2, rowColor);
            drawTrimmed(graphics, entry.name(), x + COL_NAME, rowY + 2, rowColor, nameWidth);
            drawRight(graphics, money(entry.net()), netRight, rowY + 2,
                    entry.net() >= 0 ? COLOR_UP : COLOR_DOWN);
            drawRight(graphics, money(entry.balance()), balanceRight, rowY + 2, COLOR_MUTED);
            drawRight(graphics, money(entry.debt()), debtRight, rowY + 2,
                    entry.debt() > 0 ? COLOR_DOWN : COLOR_MUTED);
            rowY += ROW_H;
        }

        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        drawText(graphics, Component.translatable(EEConstants.langKey("screen.atm.ranking.page"),
                        page + 1, pages, entries.size()).getString(),
                x + 66, panelY + panelH - 19, COLOR_MUTED);
    }
}
