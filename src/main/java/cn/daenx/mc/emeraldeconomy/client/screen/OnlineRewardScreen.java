package cn.daenx.mc.emeraldeconomy.client.screen;

import cn.daenx.mc.emeraldeconomy.client.ClientEconomyCache;
import cn.daenx.mc.emeraldeconomy.core.EEConstants;
import cn.daenx.mc.emeraldeconomy.network.payload.ClientPayloads;
import cn.daenx.mc.emeraldeconomy.network.payload.ServerPayloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * ATM · 在线奖励子页面。
 * <p>
 * 每日结算那一刻在线的玩家才有当天的领取资格，每人每天一次，奖励是「等级」。
 * 三种状态（可领取 / 已领取 / 今天无资格）与规则说明都在这一页。
 */
public class OnlineRewardScreen extends AtmSubScreen {

    public OnlineRewardScreen() {
        super(Component.translatable(EEConstants.langKey("screen.atm.online.title")));
    }

    @Override
    protected int maxPanelHeight() {
        return 190;
    }

    @Override
    protected void init() {
        super.init();
        ServerPayloads.SyncAccount account = ClientEconomyCache.account;
        // 只有「今天有资格且没领过」时才给按钮
        if (account != null && account.onlineRewardReady()) {
            addRenderableWidget(Button.builder(label("screen.atm.online.claim"),
                            b -> PacketDistributor.sendToServer(new ClientPayloads.AtmActionRequest(
                                    ClientPayloads.AtmAction.CLAIM_ONLINE_REWARD.ordinal(), 0L)))
                    .bounds(contentX() + 62, panelY + PAD + 46, 160, 18).build());
        }
        addBackButton();
    }

    @Override
    protected void drawContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = contentX();
        int y = panelY + PAD;
        drawTitle(graphics, label("screen.atm.online.title"));

        ServerPayloads.SyncAccount account = ClientEconomyCache.account;
        if (account == null) {
            drawText(graphics, label("screen.loading").getString(), x, y + 20, COLOR_MUTED);
            return;
        }

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
        y += 18;
        drawText(graphics, Component.translatable(EEConstants.langKey(key),
                account.onlineRewardLevels()).getString(), x, y, color);

        y += 16;
        drawText(graphics, label("screen.atm.online.hint").getString(), x, y, COLOR_MUTED);
        y += 12;
        drawText(graphics, label("screen.atm.online.hint2").getString(), x, y, COLOR_MUTED);

        y += 12;
        drawSeparator(graphics, x, y, contentW());
    }
}
