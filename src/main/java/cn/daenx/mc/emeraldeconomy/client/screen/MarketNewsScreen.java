package cn.daenx.mc.emeraldeconomy.client.screen;

import cn.daenx.mc.emeraldeconomy.client.ClientEconomyCache;
import cn.daenx.mc.emeraldeconomy.core.EEConstants;
import cn.daenx.mc.emeraldeconomy.network.ScreenType;
import cn.daenx.mc.emeraldeconomy.market.NewsEntry;
import cn.daenx.mc.emeraldeconomy.market.NewsText;
import cn.daenx.mc.emeraldeconomy.network.payload.ServerPayloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 市场新闻界面（需求文档 3.5.4）。
 * <p>
 * 显示最近发生的市场事件与天气影响，让玩家理解价格波动的来源。
 */
public class MarketNewsScreen extends EEScreen {

    private static final int MAX_LINES = 13;

    private final Screen parent;

    /** 该界面对应的服务端快照类型。 */
    @Override
    protected ScreenType screenType() {
        return ScreenType.EXCHANGE;
    }

    public MarketNewsScreen(Screen parent) {
        super(Component.translatable(EEConstants.langKey("screen.news.title")));
        this.parent = parent;
    }

    @Override
    protected int maxPanelWidth() {
        return 380;
    }

    @Override
    protected int maxPanelHeight() {
        return 230;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(
                        Component.translatable(EEConstants.langKey("screen.common.back")),
                        b -> this.minecraft.setScreen(parent))
                .bounds(contentX(), panelY + panelH - 24, 70, 18).build());
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    protected void drawContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = contentX();
        int y = panelY + PAD;
        drawTitle(graphics, Component.translatable(EEConstants.langKey("screen.news.title")));

        ServerPayloads.SyncMarket market = ClientEconomyCache.market;
        if (market == null || market.news().isEmpty()) {
            drawText(graphics, Component.translatable(EEConstants.langKey("screen.news.empty")).getString(),
                    x, y + 22, COLOR_MUTED);
            return;
        }

        y += 18;
        int shown = 0;
        for (ServerPayloads.NewsLine line : market.news()) {
            if (shown >= MAX_LINES) {
                break;
            }
            NewsEntry entry = new NewsEntry(line.langKey(), line.args(), line.day(), line.kind());
            // 前缀 + 正文拼成一个组件再按宽度折行，避免手工缩进对不准把正文压在前缀上
            Component head = Component.literal(NewsText.prefixOf(line.kind()))
                    .append(Component.translatable(EEConstants.langKey("screen.news.day_prefix"), line.day())
                            .copy().withStyle(ChatFormatting.GRAY));
            Component full = head.copy().append(NewsText.resolve(entry));
            for (net.minecraft.util.FormattedCharSequence sequence : this.font.split(full, contentW())) {
                if (shown >= MAX_LINES) {
                    break;
                }
                graphics.drawString(this.font, sequence, x, y, COLOR_TEXT, false);
                y += 11;
                shown++;
            }
            y += 2;
        }
    }
}
