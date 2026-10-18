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
 * ATM · 等级存取子页面。
 * <p>
 * 搬的是「等级」不是经验值；每个玩家默认关闭，付费解锁一次后永久可用。
 * 解锁费用、当前等级、ATM 库存与规则说明都在这一页。
 */
public class LevelStorageScreen extends AtmSubScreen {

    private static final int BOX_WIDTH = 100;
    private static final int BUTTON_WIDTH = 64;
    private static final int ALL_BUTTON_WIDTH = 72;

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
                            b -> send(ClientPayloads.AtmAction.LEVEL_DEPOSIT, levelBox))
                    .bounds(x + 62 + BOX_WIDTH + 6, y, BUTTON_WIDTH, 18).build());
            addRenderableWidget(Button.builder(label("screen.atm.level.withdraw"),
                            b -> send(ClientPayloads.AtmAction.LEVEL_WITHDRAW, levelBox))
                    .bounds(x + 62 + BOX_WIDTH + 6 + BUTTON_WIDTH + 4, y, ALL_BUTTON_WIDTH, 18).build());
        } else {
            long cost = account == null ? 0L : account.levelUnlockCost();
            addRenderableWidget(Button.builder(
                            Component.translatable(EEConstants.langKey("screen.atm.level.unlock"), money(cost)),
                            b -> PacketDistributor.sendToServer(new ClientPayloads.AtmActionRequest(
                                    ClientPayloads.AtmAction.LEVEL_UNLOCK.ordinal(), 0L)))
                    .bounds(x + 62, y, 160, 18).build());
        }

        addBackButton();
    }

    private void send(ClientPayloads.AtmAction action, NumberBox box) {
        long amount = box.longValue();
        if (amount <= 0L) {
            return;
        }
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
                    account.playerLevel(), account.storedLevels()).getString(), x, y, COLOR_TEXT);
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
