package cn.daenx.mc.emeraldeconomy.client;

import cn.daenx.mc.emeraldeconomy.network.ScreenType;
import cn.daenx.mc.emeraldeconomy.network.payload.ServerPayloads;
import net.minecraft.network.chat.Component;

/**
 * 客户端数据缓存。
 * <p>
 * 客户端不参与任何计算，只把服务端发来的快照存下来给界面展示。
 * 每次界面操作后服务端都会回推新的快照，界面因此在 {@code onDataUpdated} 里刷新。
 */
public final class ClientEconomyCache {

    /** 当前打开的机器界面类型。 */
    public static ScreenType currentScreen = ScreenType.ATM;

    public static ServerPayloads.SyncAccount account;
    public static ServerPayloads.SyncMarket market;
    public static ServerPayloads.SyncLottery lottery;
    public static ServerPayloads.SyncShop shop;
    public static ServerPayloads.SyncAcquire acquire;

    /** 最近一条操作提示（在机器界面底部显示），没有时为 {@code null}。 */
    public static Notice notice;

    /**
     * 一条界面内提示。
     *
     * @param message 文本（可翻译组件，按客户端语言渲染）
     * @param error   是否是失败提示
     * @param millis  收到的本地时间，用来做过期判断
     */
    public record Notice(Component message, boolean error, long millis) {
    }

    private ClientEconomyCache() {
    }

    /** 断开连接时清空，避免把上个服务器的数据带进新世界。 */
    public static void clear() {
        account = null;
        market = null;
        lottery = null;
        shop = null;
        acquire = null;
        notice = null;
    }
}
