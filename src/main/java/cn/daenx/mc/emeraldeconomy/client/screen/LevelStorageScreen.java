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

/**
 * ATM · 经验存取子页面。
 * <p>
 * 输入的是「等级」，搬的是「经验值」：输入 1 级，就把「从当前等级掉到下一级」所需的经验
 * 全部存进 ATM，玩家等级正好 -1、经验条清空；ATM 里显示的是累计经验点数，不再显示等级。
 * <p>
 * 每个玩家默认关闭，付费解锁一次后永久可用。「全部存入」会把当前经验清空，
 * 「全部取出」则把 ATM 里的经验一次性还给你。
 */
public class LevelStorageScreen extends AtmSubScreen {

    private static final int BOX_WIDTH = 80;
    private static final int BUTTON_WIDTH = 64;
    private static final int ALL_BUTTON_WIDTH = 104;
    /** 两排按钮之间的距离。 */
    private static final int ROW_GAP = 24;

    private NumberBox levelBox;

    public LevelStorageScreen() {
        super(Component.translatable(EEConstants.langKey("screen.atm.level.title")));
    }

    @Override
    protected int maxPanelHeight() {
        return 210;
    }

    /** 输入框 / 按钮所在行的纵坐标。 */
    private int controlsTop() {
        return panelY + PAD + 62;
    }

    @Override
    protected void init() {
        super.init();
        int x = contentX();
        int y = controlsTop();
        ServerPayloads.SyncAccount account = ClientEconomyCache.account;

        if (account != null && account.levelUnlocked()) {
            levelBox = addRenderableWidget(new NumberBox(this.font, x + 62, y, BOX_WIDTH, 18, "1"));
            addRenderableWidget(Button.builder(label("screen.atm.level.deposit"),
                            b -> sendAmount(ClientPayloads.AtmAction.LEVEL_DEPOSIT, levelBox))
                    .bounds(x + 62 + BOX_WIDTH + 6, y, BUTTON_WIDTH, 18).build());
            addRenderableWidget(Button.builder(label("screen.atm.level.withdraw"),
                            b -> sendAmount(ClientPayloads.AtmAction.LEVEL_WITHDRAW, levelBox))
                    .bounds(x + 62 + BOX_WIDTH + 6 + BUTTON_WIDTH + 4, y, BUTTON_WIDTH, 18).build());

            // 第二排：全部存入 / 全部取出
            int row2 = y + ROW_GAP;
            addRenderableWidget(Button.builder(label("screen.atm.level.deposit_all"),
                            b -> send(ClientPayloads.AtmAction.LEVEL_DEPOSIT_ALL))
                    .bounds(x + 62, row2, ALL_BUTTON_WIDTH, 18).build());
            addRenderableWidget(Button.builder(label("screen.atm.level.withdraw_all"),
                            b -> send(ClientPayloads.AtmAction.LEVEL_WITHDRAW_ALL))
                    .bounds(x + 62 + ALL_BUTTON_WIDTH + 4, row2, ALL_BUTTON_WIDTH, 18).build());
        } else {
            long cost = account == null ? 0L : account.levelUnlockCost();
            addRenderableWidget(Button.builder(
                            Component.translatable(EEConstants.langKey("screen.atm.level.unlock"), money(cost)),
                            b -> send(ClientPayloads.AtmAction.LEVEL_UNLOCK))
                    .bounds(x + 62, y, 160, 18).build());
        }

        addBackButton();
    }

    /** 读取输入框里的等级数并发送请求。 */
    private void sendAmount(ClientPayloads.AtmAction action, NumberBox box) {
        long amount = box == null ? 0L : box.longValue();
        if (amount <= 0L) {
            return;
        }
        send(action, amount);
    }

    private void send(ClientPayloads.AtmAction action) {
        send(action, 0L);
    }

    private void send(ClientPayloads.AtmAction action, long amount) {
        PacketDistributor.sendToServer(new ClientPayloads.AtmActionRequest(action.ordinal(), amount));
    }

    @Override
    protected void drawContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = contentX();
        int y = panelY + PAD;
        drawTitle(graphics, label("screen.atm.level.title"));

        ServerPayloads.SyncAccount account = ClientEconomyCache.account;
        if (account == null) {
            drawText(graphics, label("screen.loading").getString(), x, y + 20, COLOR_MUTED);
            return;
        }

        y += 18;
        if (account.levelUnlocked()) {
            drawText(graphics, Component.translatable(EEConstants.langKey("screen.atm.level.status"),
                    account.playerLevel(), money(account.storedExperience())).getString(), x, y, COLOR_TEXT);
        } else {
            drawText(graphics, Component.translatable(EEConstants.langKey("screen.atm.level.locked"),
                    money(account.levelUnlockCost())).getString(), x, y, COLOR_DOWN);
        }

        y += 14;
        drawText(graphics, label("screen.atm.level.hint").getString(), x, y, COLOR_MUTED);
        y += 12;
        drawText(graphics, label("screen.atm.level.hint2").getString(), x, y, COLOR_MUTED);

        y += 12;
        drawSeparator(graphics, x, y, contentW());
    }
}
