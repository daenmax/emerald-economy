package cn.daenx.mc.emeraldeconomy.client.screen;

import cn.daenx.mc.emeraldeconomy.client.ClientEconomyCache;
import cn.daenx.mc.emeraldeconomy.core.EEConstants;
import cn.daenx.mc.emeraldeconomy.network.ScreenType;
import cn.daenx.mc.emeraldeconomy.network.payload.ServerPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * ATM 主界面（需求 1.2 重构后）。
 * <p>
 * 主界面只负责「看账户状态 + 选一个功能」：
 * <ul>
 *     <li>上方显示账户余额、信用额度、贷款欠款、可贷额度、ATM 里已存的经验；</li>
 *     <li>下方是一排功能按钮：存款和取款、贷款和还款、交易记录、等级存取、在线奖励、排行榜。</li>
 * </ul>
 * 手续费、日息、计息起算线这些说明不再堆在主界面，而是放在对应的子页面里
 * （例如存款 / 取款的手续费与账户日息在「存款和取款」页）。
 */
public class AtmScreen extends EEScreen {

    /** 功能按钮的起始纵坐标（相对面板左上角）。 */
    private static final int MENU_TOP_OFFSET = 86;
    /** 功能按钮的行高与行距。 */
    private static final int MENU_BUTTON_H = 20;
    private static final int MENU_ROW_GAP = 6;

    @Override
    protected ScreenType screenType() {
        return ScreenType.ATM;
    }

    public AtmScreen() {
        super(Component.translatable(EEConstants.langKey("screen.atm.title")));
    }

    @Override
    protected int maxPanelHeight() {
        return 232;
    }

    @Override
    protected void init() {
        super.init();

        int x = contentX();
        int top = panelY + PAD + MENU_TOP_OFFSET;
        int gap = 6;
        int width = (contentW() - gap) / 2;

        addMenuButton(x, top, width, "screen.atm.menu.deposit_withdraw", new DepositWithdrawScreen());
        addMenuButton(x + width + gap, top, width, "screen.atm.menu.loan_repay", new LoanRepayScreen());
        addMenuButton(x, top + MENU_BUTTON_H + MENU_ROW_GAP, width, "screen.atm.menu.records",
                new TransactionScreen());
        addMenuButton(x + width + gap, top + MENU_BUTTON_H + MENU_ROW_GAP, width, "screen.atm.menu.level",
                new LevelStorageScreen());
        addMenuButton(x, top + (MENU_BUTTON_H + MENU_ROW_GAP) * 2, width, "screen.atm.menu.online",
                new OnlineRewardScreen());
        addMenuButton(x + width + gap, top + (MENU_BUTTON_H + MENU_ROW_GAP) * 2, width,
                "screen.atm.menu.ranking", new RankingScreen());
    }

    /** 创建一个「点一下切换到某个子页面」的菜单按钮。 */
    private void addMenuButton(int x, int y, int width, String labelKey, EEScreen target) {
        addRenderableWidget(Button.builder(Component.translatable(EEConstants.langKey(labelKey)),
                        b -> Minecraft.getInstance().setScreen(target))
                .bounds(x, y, width, MENU_BUTTON_H).build());
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

        y += 18;
        // 第一行：余额 + 信用额度；第二行：贷款欠款 + 可贷额度
        drawText(graphics, label("screen.atm.balance").getString() + " " + money(account.balance()),
                x, y, COLOR_UP);
        drawRight(graphics, label("screen.atm.credit").getString() + " " + money(account.creditLimit()),
                x + contentW(), y, COLOR_TEXT);

        y += 14;
        drawText(graphics, label("screen.atm.debt").getString() + " " + money(account.debt()),
                x, y, account.debt() > 0 ? COLOR_DOWN : COLOR_TEXT);
        drawRight(graphics, label("screen.atm.available").getString() + " " + money(account.availableCredit()),
                x + contentW(), y, COLOR_GOLD);

        y += 14;
        drawText(graphics, label("screen.atm.stored_levels").getString() + " "
                        + money(account.storedExperience()),
                x, y, COLOR_TEXT);
        if (!account.levelUnlocked()) {
            drawRight(graphics, label("screen.atm.level_locked_short").getString(),
                    x + contentW(), y, COLOR_MUTED);
        }

        y += 12;
        drawSeparator(graphics, x, y, contentW());
    }

    private static Component label(String path) {
        return Component.translatable(EEConstants.langKey(path));
    }
}
