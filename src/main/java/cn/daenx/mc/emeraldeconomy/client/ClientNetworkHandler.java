package cn.daenx.mc.emeraldeconomy.client;

import cn.daenx.mc.emeraldeconomy.client.screen.AcquisitionScreen;
import cn.daenx.mc.emeraldeconomy.client.screen.AtmScreen;
import cn.daenx.mc.emeraldeconomy.client.screen.EEScreen;
import cn.daenx.mc.emeraldeconomy.client.screen.ExchangeScreen;
import cn.daenx.mc.emeraldeconomy.client.screen.LotteryScreen;
import cn.daenx.mc.emeraldeconomy.client.screen.RankingScreen;
import cn.daenx.mc.emeraldeconomy.client.screen.VendingScreen;
import cn.daenx.mc.emeraldeconomy.network.ScreenType;
import cn.daenx.mc.emeraldeconomy.network.payload.ServerPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端的网络包处理。
 * <p>
 * 这个类只能在物理客户端加载，因此所有引用都必须写在
 * {@link cn.daenx.mc.emeraldeconomy.network.NetworkHandler} 的 lambda 体内。
 */
public final class ClientNetworkHandler {

    private ClientNetworkHandler() {
    }

    /** 服务端要求打开某个界面。 */
    public static void handleOpenScreen(ServerPayloads.OpenScreen payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ScreenType type = ScreenType.byIndex(payload.screenType());
            ClientEconomyCache.currentScreen = type;
            // 刚打开的界面上不该残留上一次操作留下的提示
            ClientEconomyCache.notice = null;
            Minecraft.getInstance().setScreen(create(type));
        });
    }

    /** 服务端要求在界面上弹一条提示。 */
    public static void handleScreenNotice(ServerPayloads.ScreenNotice payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientEconomyCache.notice = new ClientEconomyCache.Notice(
                    payload.message(), payload.error(), System.currentTimeMillis());
            refresh();
        });
    }

    public static void handleSyncAccount(ServerPayloads.SyncAccount payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientEconomyCache.account = payload;
            refresh();
        });
    }

    public static void handleSyncMarket(ServerPayloads.SyncMarket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientEconomyCache.market = payload;
            refresh();
        });
    }

    public static void handleSyncLottery(ServerPayloads.SyncLottery payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientEconomyCache.lottery = payload;
            refresh();
        });
    }

    public static void handleSyncShop(ServerPayloads.SyncShop payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientEconomyCache.shop = payload;
            refresh();
        });
    }

    public static void handleSyncAcquire(ServerPayloads.SyncAcquire payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientEconomyCache.acquire = payload;
            refresh();
        });
    }

    /** 如果当前正开着本模组的界面，通知它重建控件。 */
    private static void refresh() {
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof EEScreen eeScreen) {
            eeScreen.onDataUpdated();
        }
    }

    private static Screen create(ScreenType type) {
        return switch (type) {
            case ATM -> new AtmScreen();
            case EXCHANGE -> new ExchangeScreen();
            case LOTTERY -> new LotteryScreen();
            case VENDING -> new VendingScreen();
            case ACQUISITION -> new AcquisitionScreen();
            case RANKING -> new RankingScreen();
        };
    }
}
