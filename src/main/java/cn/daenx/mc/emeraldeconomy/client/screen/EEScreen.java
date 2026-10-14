package cn.daenx.mc.emeraldeconomy.client.screen;

import cn.daenx.mc.emeraldeconomy.client.ClientEconomyCache;
import cn.daenx.mc.emeraldeconomy.core.Amounts;
import cn.daenx.mc.emeraldeconomy.network.ScreenType;
import cn.daenx.mc.emeraldeconomy.network.payload.ClientPayloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 本模组所有界面的基类。
 * <p>
 * 统一处理：面板绘制、配色、数据刷新、向服务端拉取数据。
 * 子类只需要在 {@code init()} 里摆放控件、在 {@code render} 里画文字与图表。
 */
public abstract class EEScreen extends Screen {

    /** 界面配色。 */
    public static final int COLOR_PANEL = 0xE6101010;
    public static final int COLOR_PANEL_ALT = 0x60FFFFFF;
    public static final int COLOR_BORDER = 0xFF4A4A4A;
    public static final int COLOR_TITLE = 0xFFFFD75E;
    public static final int COLOR_TEXT = 0xFFE6E6E6;
    public static final int COLOR_MUTED = 0xFF9A9A9A;
    public static final int COLOR_UP = 0xFF55FF55;
    public static final int COLOR_DOWN = 0xFFFF6060;
    public static final int COLOR_FLAT = 0xFFAAAAAA;
    public static final int COLOR_GOLD = 0xFFFFC24D;

    /** 面板与控件之间的内边距。 */
    protected static final int PAD = 8;
    /** 列表行高。 */
    protected static final int ROW_H = 20;
    /** 界面内提示的显示时长（毫秒）。 */
    private static final long NOTICE_MILLIS = 6000L;

    /** 是否已经向服务端请求过初始数据，避免每次重建控件都重发请求。 */
    private boolean dataRequested;

    /** 面板区域，在 {@link #init()} 里计算。 */
    protected int panelX;
    protected int panelY;
    protected int panelW;
    protected int panelH;

    protected EEScreen(Component title) {
        super(title);
    }

    // ------------------------------------------------------------------
    // 生命周期
    // ------------------------------------------------------------------

    /**
     * 这个界面对应服务端的哪一种快照。
     * <p>
     * 子界面（持仓 / 市场新闻 / 交易弹窗）也返回 {@link ScreenType#EXCHANGE}，
     * 因为展示的都是交易所的数据。
     */
    protected abstract ScreenType screenType();

    @Override
    protected void init() {
        super.init();
        // 必须先登记界面类型再请求数据，否则会请求到上一个界面的快照
        ClientEconomyCache.currentScreen = screenType();
        // 界面第一次打开时主动拉一次最新数据。
        // 服务端在右键机器时也会推一次快照，但客户端不能依赖它：
        // 万一推送丢失或延迟，界面就会一直停在「加载中」。
        if (!this.dataRequested) {
            this.dataRequested = true;
            requestData();
        }
        this.panelW = Math.min(maxPanelWidth(), this.width - 20);
        this.panelH = Math.min(maxPanelHeight(), this.height - 20);
        this.panelX = (this.width - this.panelW) / 2;
        this.panelY = (this.height - this.panelH) / 2;
    }

    /** 面板最大宽度，子类可覆盖。 */
    protected int maxPanelWidth() {
        return 380;
    }

    /** 面板最大高度，子类可覆盖。 */
    protected int maxPanelHeight() {
        return 240;
    }

    /** 服务端推来新快照后重建控件。 */
    public void onDataUpdated() {
        this.rebuildWidgets();
    }

    /** 主动向服务端请求当前界面的数据。 */
    protected void requestData() {
        PacketDistributor.sendToServer(
                new ClientPayloads.RequestScreen(screenType().ordinal()));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 面板内容区域的起点。 */
    protected int contentX() {
        return panelX + PAD;
    }

    /** 面板内容区域的宽度。 */
    protected int contentW() {
        return panelW - PAD * 2;
    }

    // ------------------------------------------------------------------
    // 绘制辅助
    // ------------------------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, COLOR_PANEL);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 1, COLOR_BORDER);
        graphics.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, COLOR_BORDER);
        graphics.fill(panelX, panelY, panelX + 1, panelY + panelH, COLOR_BORDER);
        graphics.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, COLOR_BORDER);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        drawContents(graphics, mouseX, mouseY, partialTick);
        drawNotice(graphics);
    }

    /**
     * 画服务端刚推来的操作提示。
     * <p>
     * 需求：操作不满足条件时，机器界面上也要给出提示，不能只发聊天栏——
     * 否则玩家得先关掉界面才知道为什么点不动。成功与失败用不同颜色区分。
     */
    private void drawNotice(GuiGraphics graphics) {
        ClientEconomyCache.Notice notice = ClientEconomyCache.notice;
        if (notice == null) {
            return;
        }
        if (System.currentTimeMillis() - notice.millis() > NOTICE_MILLIS) {
            ClientEconomyCache.notice = null;
            return;
        }
        int barH = 14;
        int y = Math.min(panelY + panelH + 4, this.height - 12);
        graphics.fill(panelX, y - 3, panelX + panelW, y + barH - 3,
                notice.error() ? 0xD0401010 : 0xD0103010);
        graphics.fill(panelX, y - 3, panelX + panelW, y - 2,
                notice.error() ? COLOR_DOWN : COLOR_UP);
        drawTrimmed(graphics, notice.message().getString(), contentX(), y,
                notice.error() ? COLOR_DOWN : COLOR_UP, contentW());
    }

    /** 子类在这里画文字、图表等非控件内容（会画在控件之上）。 */
    protected void drawContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    /** 画标题。 */
    protected void drawTitle(GuiGraphics graphics, Component title) {
        graphics.drawString(this.font, title, contentX(), panelY + PAD, COLOR_TITLE, false);
    }

    /** 画一行普通文字。 */
    protected void drawText(GuiGraphics graphics, String text, int x, int y, int color) {
        graphics.drawString(this.font, text, x, y, color, false);
    }

    /** 画一行文字（自动按宽度截断）。 */
    protected void drawTrimmed(GuiGraphics graphics, String text, int x, int y, int color, int maxWidth) {
        String value = text;
        if (this.font.width(value) > maxWidth) {
            value = this.font.plainSubstrByWidth(value, Math.max(0, maxWidth - 6)) + "…";
        }
        graphics.drawString(this.font, value, x, y, color, false);
    }

    /** 右对齐画一行文字。 */
    protected void drawRight(GuiGraphics graphics, String text, int right, int y, int color) {
        graphics.drawString(this.font, text, right - this.font.width(text), y, color, false);
    }

    /** 画一个横向分隔线。 */
    protected void drawSeparator(GuiGraphics graphics, int x, int y, int width) {
        graphics.fill(x, y, x + width, y + 1, COLOR_BORDER);
    }

    /** 把金额格式化成易读文本。 */
    protected static String money(long value) {
        return Amounts.format(value);
    }

    /** 涨跌颜色。 */
    protected static int changeColor(double ratio) {
        if (ratio > 0.0D) {
            return COLOR_UP;
        }
        if (ratio < 0.0D) {
            return COLOR_DOWN;
        }
        return COLOR_FLAT;
    }
}
