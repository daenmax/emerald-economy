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
 * 界面顶部有三个分类：
 * <ul>
 *     <li><b>账户余额明细</b>：只列真正动了余额的记录（贷款利息那种只加欠款的不算）；</li>
 *     <li><b>贷款欠款明细</b>：只列动了欠款的记录；</li>
 *     <li><b>经验存取明细</b>：只列经验类记录（存入 / 取出 / 在线奖励 / 经验赠送），
 *         列「变动前经验 → 变动后经验」。</li>
 * </ul>
 * 数据完全来自服务端快照，客户端只负责排版。经验类记录的前后值由客户端按
 * 「当前已存经验 + 每条记录的变动额」倒推，所以不需要额外持久化经验前后值。
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
    /** 分类：经验存取。 */
    private static final int TAB_EXPERIENCE = 2;

    private int page;
    private int tab = TAB_BALANCE;

    /**
     * 一行展示数据：类型、金额列、变动前、变动后。
     * <p>
     * {@code showRange} 为 false 时（例如在线奖励，它加的是玩家身上的经验，不改变 ATM 库存），
     * 前后两列显示「-」，避免让人误以为库存经验从 N 变成了 N。
     */
    private record Row(TransactionType type, long amount, long before, long after, boolean showRange) {
    }

    @Override
    protected ScreenType screenType() {
        return ScreenType.ATM;
    }

    public TransactionScreen() {
        super(Component.translatable(EEConstants.langKey("screen.atm.records.title")));
    }

    /** 构造时可以指定默认打开哪个分类（ATM 上有对应的入口按钮）。 */
    public TransactionScreen(int tab) {
        this();
        this.tab = tab == TAB_DEBT ? TAB_DEBT
                : tab == TAB_EXPERIENCE ? TAB_EXPERIENCE : TAB_BALANCE;
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
    private static List<Row> rowsOf(int tab) {
        ServerPayloads.SyncAccount account = ClientEconomyCache.account;
        List<Row> out = new ArrayList<>();
        if (account == null) {
            return out;
        }
        if (tab == TAB_EXPERIENCE) {
            // 从最新的记录往回倒推：最新一条的「变动后」就是当前的已存经验。
            // 在线奖励不改变 ATM 库存经验，所以它的前后值相同。
            long running = account.storedExperience();
            for (ServerPayloads.TxLine line : account.transactions()) {
                TransactionType type = TransactionType.byName(line.type(), TransactionType.DEPOSIT);
                if (!type.isExperienceRecord()) {
                    continue;
                }
                boolean changesStorage = type == TransactionType.LEVEL_DEPOSIT
                        || type == TransactionType.LEVEL_WITHDRAW
                        || type == TransactionType.EXPERIENCE_GIFT_OUT
                        || type == TransactionType.EXPERIENCE_GIFT_IN;
                long after = running;
                long before = changesStorage ? running - line.amount() : running;
                if (changesStorage) {
                    running = before;
                }
                out.add(new Row(type, line.amount(), before, after, changesStorage));
            }
            return out;
        }
        for (ServerPayloads.TxLine line : account.transactions()) {
            TransactionType type = TransactionType.byName(line.type(), TransactionType.DEPOSIT);
            if (tab == TAB_DEBT) {
                if (line.debtAfter() == line.debtBefore()) {
                    continue;
                }
                out.add(new Row(type, line.debtAfter() - line.debtBefore(),
                        line.debtBefore(), line.debtAfter(), true));
            } else {
                if (line.balanceAfter() == line.balanceBefore()) {
                    continue;
                }
                out.add(new Row(type, line.balanceAfter() - line.balanceBefore(),
                        line.balanceBefore(), line.balanceAfter(), true));
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

        // 三个分类标签
        int tabWidth = Math.max(60, contentW() / 3);
        addTabButton(x, tabsY, tabWidth, "screen.atm.records.tab.balance", TAB_BALANCE);
        addTabButton(x + tabWidth, tabsY, tabWidth, "screen.atm.records.tab.debt", TAB_DEBT);
        addTabButton(x + tabWidth * 2, tabsY, tabWidth, "screen.atm.records.tab.experience",
                TAB_EXPERIENCE);

        int pages = Math.max(1, (rowsOf(tab).size() + PAGE_SIZE - 1) / PAGE_SIZE);
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

    private void addTabButton(int x, int y, int width, String langKey, int targetTab) {
        addRenderableWidget(Button.builder(Component.translatable(EEConstants.langKey(langKey)),
                        b -> {
                            tab = targetTab;
                            page = 0;
                            this.rebuildWidgets();
                        })
                .bounds(x, y, Math.max(40, width - 2), 18).build());
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
        int tabWidth = Math.max(60, contentW() / 3);
        // 高亮当前分类
        graphics.fill(x + tab * tabWidth, tabsY - 1, x + tab * tabWidth + tabWidth - 2, tabsY + 19,
                0x40FFD75E);

        int headerY = tabsY + 24;
        drawSeparator(graphics, x, headerY - 3, contentW());
        drawText(graphics, label("screen.atm.records.type"), x + COL_TYPE, headerY, COLOR_TITLE);
        drawRight(graphics, label("screen.atm.records.amount"), x + COL_AMOUNT, headerY, COLOR_TITLE);
        String beforeKey;
        String afterKey;
        if (tab == TAB_EXPERIENCE) {
            beforeKey = "screen.atm.records.experience_before";
            afterKey = "screen.atm.records.experience_after";
        } else if (tab == TAB_DEBT) {
            beforeKey = "screen.atm.records.debt_before";
            afterKey = "screen.atm.records.debt_after";
        } else {
            beforeKey = "screen.atm.records.balance_before";
            afterKey = "screen.atm.records.balance_after";
        }
        drawRight(graphics, label(beforeKey), x + COL_BEFORE, headerY, COLOR_TITLE);
        drawRight(graphics, label(afterKey), x + COL_AFTER, headerY, COLOR_TITLE);
        drawSeparator(graphics, x, headerY + 10, contentW());

        List<Row> rows = rowsOf(tab);
        if (rows.isEmpty()) {
            String emptyKey = tab == TAB_EXPERIENCE ? "screen.atm.records.no_experience"
                    : tab == TAB_DEBT ? "screen.atm.records.no_debt" : "screen.atm.no_records";
            drawText(graphics, label(emptyKey), x, headerY + 18, COLOR_MUTED);
            return;
        }

        int from = page * PAGE_SIZE;
        int to = Math.min(rows.size(), from + PAGE_SIZE);
        int lineY = headerY + 16;
        for (int i = from; i < to; i++) {
            Row row = rows.get(i);
            int rowColor = i % 2 == 0 ? COLOR_TEXT : 0xFFC0C0C0;
            drawTrimmed(graphics, Component.translatable(row.type().langKey()).getString(), x + COL_TYPE,
                    lineY, rowColor, COL_AMOUNT - 8);
            String amountText = (row.amount() >= 0 ? "+" : "") + money(row.amount());
            if (tab == TAB_EXPERIENCE) {
                amountText = amountText + " " + label("screen.atm.records.unit_xp");
            }
            drawRight(graphics, amountText, x + COL_AMOUNT, lineY,
                    row.amount() >= 0 ? COLOR_UP : COLOR_DOWN);
            if (row.showRange()) {
                drawRight(graphics, money(row.before()), x + COL_BEFORE, lineY, COLOR_MUTED);
                drawRight(graphics, money(row.after()), x + COL_AFTER, lineY, rowColor);
            } else {
                drawRight(graphics, "-", x + COL_BEFORE, lineY, COLOR_MUTED);
                drawRight(graphics, "-", x + COL_AFTER, lineY, COLOR_MUTED);
            }
            lineY += LINE_H;
        }

        int pages = Math.max(1, (rows.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        // 页码写在「返回」按钮右边，不然两者会叠在一起
        drawText(graphics, Component.translatable(EEConstants.langKey("screen.atm.records.page"),
                page + 1, pages, rows.size()).getString(), x + 66, panelY + panelH - 19, COLOR_MUTED);
    }

    /** 取翻译键（与其它界面保持一致的写法）。 */
    private static String label(String path) {
        return Component.translatable(EEConstants.langKey(path)).getString();
    }
}
