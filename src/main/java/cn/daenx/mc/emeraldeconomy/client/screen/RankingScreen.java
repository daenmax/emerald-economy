package cn.daenx.mc.emeraldeconomy.client.screen;

import cn.daenx.mc.emeraldeconomy.client.ClientEconomyCache;
import cn.daenx.mc.emeraldeconomy.core.EEConstants;
import cn.daenx.mc.emeraldeconomy.network.ScreenType;
import cn.daenx.mc.emeraldeconomy.network.payload.ServerPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * ATM · 排行榜子页面。
 * <p>
 * 两个榜单：
 * <ul>
 *     <li><b>余额排行榜</b>：按账户余额从高到低，显示净值 / 余额 / 欠款，可以「转账」；</li>
 *     <li><b>经验排行榜</b>：按 ATM 里已存的经验点从高到低，可以「赠送」经验。</li>
 * </ul>
 * 两个操作都要求对方玩家<b>在线</b>；离线玩家的按钮会置灰。榜单在打开这个页面时
 * 由服务端<b>实时重算一次</b>（{@link ScreenType#RANKING}），不是每天固定一份旧快照。
 * <p>
 * 转账 / 赠送的金额校验与扣款全部在服务端完成，客户端只负责展示与发起请求。
 */
public class RankingScreen extends EEScreen {

    private static final int PAGE_SIZE = 10;
    private static final int ROW_H = 18;
    /** 榜单分类：余额。 */
    private static final int TAB_BALANCE = 0;
    /** 榜单分类：经验。 */
    private static final int TAB_EXPERIENCE = 1;
    /** 各列相对内容区左边缘的位置（右对齐列用右边界坐标）。 */
    private static final int COL_RANK = 0;
    private static final int COL_NAME = 32;
    /** 操作按钮宽度（转账 / 赠送）。 */
    private static final int ACTION_WIDTH = 52;
    private static final int COL_GAP = 10;

    private int page;
    private int tab;

    public RankingScreen() {
        this(TAB_BALANCE);
    }

    public RankingScreen(int tab) {
        super(Component.translatable(EEConstants.langKey("screen.atm.ranking.title")));
        this.tab = tab == TAB_EXPERIENCE ? TAB_EXPERIENCE : TAB_BALANCE;
    }

    @Override
    protected ScreenType screenType() {
        return ScreenType.RANKING;
    }

    @Override
    protected int maxPanelWidth() {
        return 500;
    }

    @Override
    protected int maxPanelHeight() {
        return 320;
    }

    /** 分类标签的纵坐标。 */
    private int tabsTop() {
        return panelY + PAD + 28;
    }

    /** 列表表头纵坐标。 */
    private int headerTop() {
        return tabsTop() + 24;
    }

    /** 第一行数据的纵坐标。 */
    private int listTop() {
        return headerTop() + 16;
    }

    private static List<ServerPayloads.RankingEntry> entries(int tab) {
        ServerPayloads.SyncAccount account = ClientEconomyCache.account;
        if (account == null) {
            return List.of();
        }
        return tab == TAB_EXPERIENCE ? account.experienceRanking() : account.ranking();
    }

    private boolean isExperienceTab() {
        return tab == TAB_EXPERIENCE;
    }

    @Override
    protected void init() {
        super.init();
        int x = contentX();

        // 两个分类标签
        int tabWidth = Math.max(80, contentW() / 2);
        addRenderableWidget(Button.builder(
                        Component.translatable(EEConstants.langKey("screen.atm.ranking.tab.balance")),
                        b -> switchTab(TAB_BALANCE))
                .bounds(x, tabsTop(), tabWidth - 2, 18).build());
        addRenderableWidget(Button.builder(
                        Component.translatable(EEConstants.langKey("screen.atm.ranking.tab.experience")),
                        b -> switchTab(TAB_EXPERIENCE))
                .bounds(x + tabWidth, tabsTop(), tabWidth - 2, 18).build());

        List<ServerPayloads.RankingEntry> entries = entries(tab);
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
            String actionKey = isExperienceTab()
                    ? "screen.atm.ranking.gift" : "screen.atm.ranking.transfer";
            Button action = Button.builder(
                            Component.translatable(EEConstants.langKey(actionKey)),
                            b -> Minecraft.getInstance().setScreen(
                                    new TransferScreen(entry.playerId(), entry.name(), isExperienceTab())))
                    .bounds(x + contentW() - ACTION_WIDTH, rowY - 1, ACTION_WIDTH, 16).build();
            // 不能给自己操作；转账 / 赠送都要求对方在线
            action.active = !entry.playerId().equals(selfId) && entry.online();
            addRenderableWidget(action);
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

    private void switchTab(int newTab) {
        this.tab = newTab;
        this.page = 0;
        this.rebuildWidgets();
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
        String hintKey = isExperienceTab()
                ? "screen.atm.ranking.hint.experience" : "screen.atm.ranking.hint.balance";
        drawText(graphics, Component.translatable(EEConstants.langKey(hintKey),
                account.rankingDay()).getString(), x, y, COLOR_MUTED);
        drawRight(graphics, isExperienceTab()
                        ? Component.translatable(EEConstants.langKey("screen.atm.ranking.gift_free")).getString()
                        : Component.translatable(EEConstants.langKey("screen.atm.ranking.fee"),
                        String.format(java.util.Locale.ROOT, "%.2f", account.transferFeeRate() * 100.0D))
                        .getString(),
                x + contentW(), y, COLOR_GOLD);

        // 分类标签高亮
        int tabWidth = Math.max(80, contentW() / 2);
        graphics.fill(x + tab * tabWidth, tabsTop() - 1, x + tab * tabWidth + tabWidth - 2,
                tabsTop() + 19, 0x40FFD75E);

        int headerY = headerTop();
        int buttonX = x + contentW() - ACTION_WIDTH;
        int debtRight = buttonX - COL_GAP;
        int balanceRight = debtRight - 78;
        int netRight = balanceRight - 88;
        // 经验榜只显示「已存经验」一列，不再显示余额
        int expRight = buttonX - COL_GAP;
        int firstRight = isExperienceTab() ? expRight : netRight;
        int nameWidth = Math.max(60, firstRight - (x + COL_NAME) - 8);

        drawSeparator(graphics, x, headerY - 3, contentW());
        drawText(graphics, Component.translatable(EEConstants.langKey("screen.atm.ranking.rank")).getString(),
                x + COL_RANK, headerY, COLOR_TITLE);
        drawText(graphics, Component.translatable(EEConstants.langKey("screen.atm.ranking.player")).getString(),
                x + COL_NAME, headerY, COLOR_TITLE);
        if (isExperienceTab()) {
            drawRight(graphics, Component.translatable(
                            EEConstants.langKey("screen.atm.ranking.experience")).getString(),
                    expRight, headerY, COLOR_TITLE);
        } else {
            drawRight(graphics, Component.translatable(EEConstants.langKey("screen.atm.ranking.net")).getString(),
                    netRight, headerY, COLOR_TITLE);
            drawRight(graphics, Component.translatable(EEConstants.langKey("screen.atm.ranking.balance")).getString(),
                    balanceRight, headerY, COLOR_TITLE);
            drawRight(graphics, Component.translatable(EEConstants.langKey("screen.atm.ranking.debt")).getString(),
                    debtRight, headerY, COLOR_TITLE);
        }
        drawSeparator(graphics, x, headerY + 10, contentW());

        List<ServerPayloads.RankingEntry> entries = entries(tab);
        if (entries.isEmpty()) {
            drawText(graphics, Component.translatable(EEConstants.langKey("screen.atm.ranking.empty")).getString(),
                    x, headerY + 20, COLOR_MUTED);
            return;
        }

        int from = page * PAGE_SIZE;
        int to = Math.min(entries.size(), from + PAGE_SIZE);
        int rowY = listTop();
        String selfId = account.playerId();
        String offlineMark = " §7(" + Component.translatable(
                EEConstants.langKey("screen.atm.ranking.offline")).getString() + ")";
        for (int i = from; i < to; i++) {
            ServerPayloads.RankingEntry entry = entries.get(i);
            boolean self = entry.playerId().equals(selfId);
            int rowColor = self ? COLOR_GOLD : (i % 2 == 0 ? COLOR_TEXT : 0xFFC0C0C0);
            drawText(graphics, String.valueOf(i + 1), x + COL_RANK, rowY + 2, rowColor);
            String name = entry.name() + (entry.online() ? "" : offlineMark);
            drawTrimmed(graphics, name, x + COL_NAME, rowY + 2,
                    entry.online() ? rowColor : COLOR_MUTED, nameWidth);
            if (isExperienceTab()) {
                drawRight(graphics, money(entry.experience()), expRight, rowY + 2, COLOR_GOLD);
            } else {
                drawRight(graphics, money(entry.net()), netRight, rowY + 2,
                        entry.net() >= 0 ? COLOR_UP : COLOR_DOWN);
                drawRight(graphics, money(entry.balance()), balanceRight, rowY + 2, COLOR_MUTED);
                drawRight(graphics, money(entry.debt()), debtRight, rowY + 2,
                        entry.debt() > 0 ? COLOR_DOWN : COLOR_MUTED);
            }
            rowY += ROW_H;
        }

        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        drawText(graphics, Component.translatable(EEConstants.langKey("screen.atm.ranking.page"),
                        page + 1, pages, entries.size()).getString(),
                x + 66, panelY + panelH - 19, COLOR_MUTED);
    }
}
