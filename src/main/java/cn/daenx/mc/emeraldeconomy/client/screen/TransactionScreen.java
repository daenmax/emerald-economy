package cn.daenx.mc.emeraldeconomy.client.screen;

import cn.daenx.mc.emeraldeconomy.client.ClientEconomyCache;
import cn.daenx.mc.emeraldeconomy.core.EEConstants;
import cn.daenx.mc.emeraldeconomy.data.TransactionType;
import cn.daenx.mc.emeraldeconomy.network.ScreenType;
import cn.daenx.mc.emeraldeconomy.network.payload.ServerPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * ATM 的「交易记录」界面。
 * <p>
 * 需求：最近交易记录改成按钮，点开之后能看到这个账户的<b>全部</b>流水，
 * 并且每一条都要说清楚「变动前 / 变动后」各是多少。
 * 后来又要求把两类数字分开看，于是界面顶部有<b>两个分类</b>：
 * <ul>
 *     <li><b>账户余额</b>：只列真正动了余额的记录（贷款利息那种只加欠款的不算），
 *         列「变动前余额 → 变动后余额」</li>
 *     <li><b>贷款欠款</b>：只列动了欠款的记录，列「变动前欠款 → 变动后欠款」</li>
 * </ul>
 * 数据完全来自服务端快照，客户端只负责排版。正因为数据跟着账户快照一起下发，
 * 这个界面的 {@link #screenType()} 与 ATM 相同——服务端在结算与操作后回推的快照
 * 会同时刷新这两个界面。
 */
public class TransactionScreen extends EEScreen {

    /** 每页行数。 */
    private static final int PAGE_SIZE = 11;
    /** 行高。 */
    private static final int LINE_H = 12;
    /** 列位置（相对内容区左边缘）。 */
    private static final int COL_TYPE = 0;
    private static final int COL_AMOUNT = 170;
    private static final int COL_BEFORE = 262;
    private static final int COL_AFTER = 366;

    /** 分类：账户余额。 */
    private static final int TAB_BALANCE = 0;
    /** 分类：贷款欠款。 */
    private static final int TAB_DEBT = 1;

    private int page;
    private int tab = TAB_BALANCE;

    @Override
    protected ScreenType screenType() {
        return ScreenType.ATM;
    }

    public TransactionScreen() {
        super(Component.translatable(EEConstants.langKey("screen.atm.records.title")));
    }

    /** 构造时可以指定默认打开哪个分类（ATM 上有两个入口按钮）。 */
    public TransactionScreen(int tab) {
        this();
        this.tab = tab == TAB_DEBT ? TAB_DEBT : TAB_BALANCE;
    }

    @Override
    protected int maxPanelWidth() {
        return 420;
    }

    @Override
    protected int maxPanelHeight() {
        return 268;
    }

    /** 当前分类下要显示哪些流水。 */
    private static List<ServerPayloads.TxLine> linesOf(int tab) {
        ServerPayloads.SyncAccount account = ClientEconomyCache.account;
        List<ServerPayloads.TxLine> out = new ArrayList<>();
        if (account == null) {
            return out;
        }
        for (ServerPayloads.TxLine line : account.transactions()) {
            boolean affectsDebt = line.debtAfter() != line.debtBefore();
            boolean affectsBalance = line.balanceAfter() != line.balanceBefore();
            if (tab == TAB_DEBT ? affectsDebt : affectsBalance) {
                out.add(line);
            }
        }
        return out;
    }

    @Override
    protected void init() {
        super.init();
        int x = contentX();
        int tabsY = panelY + PAD + 26;
        int bottom = panelY + panelH - 24;

        // 两个分类标签
        int tabWidth = Math.max(60, contentW() / 2);
        addRenderableWidget(Button.builder(Component.translatable(EEConstants.langKey("screen.atm.records.tab.balance")),
                        b -> {
                            tab = TAB_BALANCE;
                            page = 0;
                            this.rebuildWidgets();
                        })
                .bounds(x, tabsY, tabWidth - 2, 18).build());
        addRenderableWidget(Button.builder(Component.translatable(EEConstants.langKey("screen.atm.records.tab.debt")),
                        b -> {
                            tab = TAB_DEBT;
                            page = 0;
                            this.rebuildWidgets();
                        })
                .bounds(x + tabWidth, tabsY, tabWidth - 2, 18).build());

        int pages = Math.max(1, (linesOf(tab).size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page >= pages) {
            page = pages - 1;
        }
        if (page < 0) {
            page = 0;
        }

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
        drawTitle(graphics, Component.translatable(EEConstants.langKey("screen.atm.records.title")));

        ServerPayloads.SyncAccount account = ClientEconomyCache.account;
        if (account == null) {
            drawText(graphics, label("screen.loading"), x, y + 20, COLOR_MUTED);
            return;
        }

        // 顶部：账户当前状态，方便与流水第一行的「变动后」数字对照
        drawText(graphics, label("screen.atm.balance") + " " + money(account.balance()), x, y + 14, COLOR_UP);
        drawRight(graphics, label("screen.atm.debt") + " " + money(account.debt()), x + contentW(), y + 14,
                account.debt() > 0 ? COLOR_DOWN : COLOR_TEXT);

        int tabsY = panelY + PAD + 26;
        // 高亮当前分类
        int tabWidth = Math.max(60, contentW() / 2);
        graphics.fill(x + tab * tabWidth, tabsY - 1, x + tab * tabWidth + tabWidth - 2, tabsY + 19,
                0x40FFD75E);

        int headerY = tabsY + 24;
        drawSeparator(graphics, x, headerY - 3, contentW());
        drawText(graphics, label("screen.atm.records.type"), x + COL_TYPE, headerY, COLOR_TITLE);
        drawRight(graphics, label("screen.atm.records.amount"), x + COL_AMOUNT, headerY, COLOR_TITLE);
        drawRight(graphics, tab == TAB_DEBT
                        ? label("screen.atm.records.debt_before") : label("screen.atm.records.balance_before"),
                x + COL_BEFORE, headerY, COLOR_TITLE);
        drawRight(graphics, tab == TAB_DEBT
                        ? label("screen.atm.records.debt_after") : label("screen.atm.records.balance_after"),
                x + COL_AFTER, headerY, COLOR_TITLE);
        drawSeparator(graphics, x, headerY + 10, contentW());

        List<ServerPayloads.TxLine> lines = linesOf(tab);
        if (lines.isEmpty()) {
            drawText(graphics, label(tab == TAB_DEBT
                    ? "screen.atm.records.no_debt" : "screen.atm.no_records"),
                    x, headerY + 18, COLOR_MUTED);
            return;
        }

        int from = page * PAGE_SIZE;
        int to = Math.min(lines.size(), from + PAGE_SIZE);
        int lineY = headerY + 16;
        for (int i = from; i < to; i++) {
            ServerPayloads.TxLine line = lines.get(i);
            TransactionType type = TransactionType.byName(line.type(), TransactionType.DEPOSIT);
            int rowColor = i % 2 == 0 ? COLOR_TEXT : 0xFFC0C0C0;
            long delta = tab == TAB_DEBT
                    ? line.debtAfter() - line.debtBefore() : line.balanceAfter() - line.balanceBefore();

            drawTrimmed(graphics, Component.translatable(type.langKey()).getString(), x + COL_TYPE, lineY,
                    rowColor, COL_AMOUNT - 8);
            drawRight(graphics, (delta >= 0 ? "+" : "") + money(delta), x + COL_AMOUNT, lineY,
                    delta >= 0 ? COLOR_UP : COLOR_DOWN);
            drawRight(graphics, money(tab == TAB_DEBT ? line.debtBefore() : line.balanceBefore()),
                    x + COL_BEFORE, lineY, COLOR_MUTED);
            drawRight(graphics, money(tab == TAB_DEBT ? line.debtAfter() : line.balanceAfter()),
                    x + COL_AFTER, lineY, rowColor);
            lineY += LINE_H;
        }

        int pages = Math.max(1, (lines.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        // 页码写在「返回」按钮右边，不然两者会叠在一起
        drawText(graphics, Component.translatable(EEConstants.langKey("screen.atm.records.page"),
                page + 1, pages, lines.size()).getString(), x + 66, panelY + panelH - 19, COLOR_MUTED);
    }

    /** 取翻译键（与其它界面保持一致的写法）。 */
    private static String label(String path) {
        return Component.translatable(EEConstants.langKey(path)).getString();
    }
}
