package cn.daenx.mc.emeraldeconomy.core;

import cn.daenx.mc.emeraldeconomy.acquire.AcquireService;
import cn.daenx.mc.emeraldeconomy.config.EEConfig;
import cn.daenx.mc.emeraldeconomy.data.EconomyData;
import cn.daenx.mc.emeraldeconomy.economy.AccountService;
import cn.daenx.mc.emeraldeconomy.lottery.LotteryService;
import cn.daenx.mc.emeraldeconomy.market.FundTradeService;
import cn.daenx.mc.emeraldeconomy.market.MarketEngine;
import cn.daenx.mc.emeraldeconomy.market.SupplyTracker;
import cn.daenx.mc.emeraldeconomy.market.WeatherKind;
import cn.daenx.mc.emeraldeconomy.network.ServerSync;
import cn.daenx.mc.emeraldeconomy.shop.ShopService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * 每日结算调度器（需求文档 七）。
 * <p>
 * 由服务端每刻调用一次，负责两件周期性工作：
 * <ol>
 *     <li>价格引擎：每游戏内 5 分钟（默认 6000 刻）执行一次市场模拟</li>
 *     <li>每日结算：游戏日切换时按固定顺序跑完基金、利息、抽奖、限购、疯狂星期五</li>
 * </ol>
 * 用 {@code level.getDayTime() / 24000} 作为「游戏日」编号，与游戏内时间显示一致。
 */
public final class DaySettlement {

    /** 交易统计的轮询间隔（刻），每秒一次足够。 */
    private static final int TRADE_POLL_INTERVAL = EEConstants.TICKS_PER_SECOND;

    private DaySettlement() {
    }

    /** 服务端每刻调用。 */
    public static void onServerTick(MinecraftServer server) {
        if (!EEConfig.isLoaded()) {
            // 配置还没加载完（例如服务器启动早期）时什么都不做
            return;
        }
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return;
        }
        EconomyData data = EconomyData.get(server);
        data.ensureMarketInitialized();

        long gameTime = overworld.getGameTime();
        long day = currentDay(overworld);

        // 商店首次访问时按配置初始化（随机解锁初始格子）
        ShopService.ensureInitialized(data, server.registryAccess(), overworld.random);

        // 每秒统计一次「与村民交易」的增量
        if (gameTime % TRADE_POLL_INTERVAL == 0L) {
            SupplyTracker.pollVillagerTrades(data, server);
        }

        // 疯狂星期五：跨天或服务器重启后按当前游戏日校准状态
        if (AcquireService.ensureFridayState(data, day, overworld.random) && data.acquire().isFridayActive()) {
            AcquireService.announceFriday(server, data);
        }

        // 限购重置的兜底：跨天立刻生效，不依赖结算流程是否已经跑过
        if (data.shop().purchaseDay() != day) {
            ShopService.resetDaily(data, day);
        }

        updatePricesIfDue(server, data, overworld, gameTime);

        settleIfNewDay(server, data, overworld, day, gameTime);
    }

    /** 当前游戏日编号。 */
    public static long currentDay(ServerLevel level) {
        return level.getDayTime() / EEConstants.TICKS_PER_DAY;
    }

    // ------------------------------------------------------------------
    // 价格更新
    // ------------------------------------------------------------------

    private static void updatePricesIfDue(MinecraftServer server, EconomyData data, ServerLevel level,
                                          long gameTime) {
        long interval = Math.max(1, EEConfig.priceUpdateIntervalTicks());
        long last = data.lastPriceTick();
        if (last < 0L) {
            data.setLastPriceTick(gameTime);
            return;
        }
        if (gameTime - last >= interval) {
            data.setLastPriceTick(gameTime);
            MarketEngine.updatePrices(data, level.random);
            // 需求 3.4.3：价格更新后要把最新价格同步给客户端
            ServerSync.refreshOpenScreens(server);
        }
    }

    // ------------------------------------------------------------------
    // 每日结算
    // ------------------------------------------------------------------

    private static void settleIfNewDay(MinecraftServer server, EconomyData data, ServerLevel level,
                                       long day, long gameTime) {
        if (data.lastSettledDay() < 0L) {
            // 新存档或第一次加载：只记录基线，不结算
            data.setLastSettledDay(day);
            data.setLastSettleGameTime(gameTime);
            return;
        }
        if (day <= data.lastSettledDay()) {
            return;
        }
        // 玩家频繁 /time set 时避免连续结算
        if (gameTime - data.lastSettleGameTime() < EEConstants.MIN_TICKS_BETWEEN_SETTLEMENTS) {
            return;
        }
        long endDay = data.lastSettledDay();
        performSettlement(server, data, endDay, day, readWeather(level), level.random);
        data.setLastSettledDay(day);
        data.setLastSettleGameTime(gameTime);
    }

    /**
     * 执行一次完整的每日结算，顺序与需求文档第七节一致。
     *
     * @param endDay 刚刚结束的游戏日
     */
    public static void performSettlement(MinecraftServer server, EconomyData data, long endDay, long newDay,
                                         WeatherKind weather, RandomSource random) {
        // 1. 基金交易所结算：价格引擎、事件修正、天气修正、市场事件、记录收盘价
        MarketEngine.settleDay(server, data, endDay, weather, random);

        // 2. 账户利息与贷款利息
        AccountService.applyDailyInterest(server, data, endDay);

        // 3. 抽奖开奖并广播
        LotteryService.draw(server, data, endDay, random);

        // 4. 限购重置（按真实的当天，而不是「结束日 + 1」——用 /time set 一次跳好几天时两者不等）
        ShopService.resetDaily(data, newDay);

        // 5. 疯狂星期五状态交给 tick 流程按真实当天校准并广播。
        //    这里再滚一次的话，跨多天跳时间时会用错误的日期覆盖掉正确状态，
        //    结果同一天广播两次、两次物品还不一样。

        // 6. 记录玩家持仓等数据（持仓随账户一起写入存档，这里只需要标脏）
        data.setDirty();

        // 把结算结果推到已经打开界面的玩家，否则界面会一直显示结算前的旧数据
        ServerSync.refreshOpenScreens(server);

        Notify.broadcastSystem(server, "settlement.done", newDay, weather.displayName());
    }

    /** 读取当前天气（需求文档 3.3.3）。 */
    public static WeatherKind readWeather(ServerLevel level) {
        if (level.isThundering()) {
            return WeatherKind.THUNDER;
        }
        if (level.isRaining()) {
            return WeatherKind.RAIN;
        }
        return WeatherKind.CLEAR;
    }

    /** 服务器停止时清理内存中的临时数据。 */
    public static void onServerStopping() {
        FundTradeService.clearCooldowns();
        SupplyTracker.clearCache();
    }
}
