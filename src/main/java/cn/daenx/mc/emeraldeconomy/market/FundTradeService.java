package cn.daenx.mc.emeraldeconomy.market;

import cn.daenx.mc.emeraldeconomy.config.EEConfig;
import cn.daenx.mc.emeraldeconomy.core.Amounts;
import cn.daenx.mc.emeraldeconomy.core.DaySettlement;
import cn.daenx.mc.emeraldeconomy.data.AccountData;
import cn.daenx.mc.emeraldeconomy.data.EconomyData;
import cn.daenx.mc.emeraldeconomy.data.TransactionType;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 基金买卖（需求文档 3.4.2）。
 * <p>
 * 规则：
 * <ul>
 *     <li>买卖份额必须是整数，最小 1</li>
 *     <li>每笔交易收取 2% 手续费，最低扣 1 个绿宝石</li>
 *     <li>同一支基金 5 秒内不能重复交易，防止刷价</li>
 *     <li>每人每天买入 / 卖出的份额有总量上限（所有基金合计，不是交易次数）</li>
 *     <li>所有验证都在服务端进行</li>
 * </ul>
 * 结算统一走玩家的 ATM 账户余额。
 */
public final class FundTradeService {

    /** 交易冷却记录：玩家 -> (基金 -> 上次交易时间戳毫秒)。只放内存，重启后清零即可。 */
    private static final Map<UUID, Map<FundType, Long>> LAST_TRADE = new HashMap<>();

    private FundTradeService() {
    }

    /** 交易结果。 */
    public enum Result {
        OK,
        /** 份额非法 */
        INVALID_QUANTITY,
        /** 账户余额不足 */
        INSUFFICIENT_BALANCE,
        /** 持有份额不足 */
        INSUFFICIENT_SHARES,
        /** 处于交易冷却中 */
        COOLDOWN,
        /** 超过当天的买入份额上限 */
        DAILY_BUY_LIMIT,
        /** 超过当天的卖出份额上限 */
        DAILY_SELL_LIMIT
    }

    /**
     * 一笔交易的结算明细。
     *
     * @param result   结果
     * @param quantity 份额
     * @param price    成交单价
     * @param fee      手续费
     * @param gross    成交总额（不含手续费）
     * @param net      实际影响账户的金额
     * @param cooldown 剩余冷却毫秒
     */
    public record Outcome(Result result, long quantity, int price, long fee, long gross, long net, long cooldown) {
        public boolean ok() {
            return result == Result.OK;
        }

        static Outcome fail(Result result, long cooldown) {
            return new Outcome(result, 0L, 0, 0L, 0L, 0L, cooldown);
        }
    }

    /** 清空所有冷却记录（服务器停止时调用）。 */
    public static void clearCooldowns() {
        LAST_TRADE.clear();
    }

    /** 剩余冷却时间（毫秒），0 表示可以交易。 */
    public static long remainingCooldown(UUID playerId, FundType fund) {
        Map<FundType, Long> map = LAST_TRADE.get(playerId);
        if (map == null) {
            return 0L;
        }
        Long last = map.get(fund);
        if (last == null) {
            return 0L;
        }
        long elapsed = System.currentTimeMillis() - last;
        long cooldown = EEConfig.tradeCooldownMillis();
        return Math.max(0L, cooldown - elapsed);
    }

    private static void markTraded(UUID playerId, FundType fund) {
        LAST_TRADE.computeIfAbsent(playerId, k -> new EnumMap<>(FundType.class))
                .put(fund, System.currentTimeMillis());
    }

    /** 买入总花费预览：成交额 + 手续费。 */
    public static long previewBuyCost(int price, long quantity) {
        long gross = (long) price * quantity;
        return gross + Amounts.fee(gross, EEConfig.tradeFeeRate());
    }

    /** 卖出净收入预览：成交额 - 手续费。 */
    public static long previewSellGain(int price, long quantity) {
        long gross = (long) price * quantity;
        long fee = Math.min(gross, Amounts.fee(gross, EEConfig.tradeFeeRate()));
        return Math.max(0L, gross - fee);
    }

    /**
     * 当天还剩多少份额可以买入（所有基金合计）。
     * <p>
     * 返回 {@link Long#MAX_VALUE} 表示配置里把每日限购设成了 0（不限制）。
     */
    public static long remainingDailyBuy(AccountData account, long day) {
        int limit = EEConfig.dailyBuyShareLimit();
        if (limit <= 0) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, (long) limit - account.fundBoughtShares(day));
    }

    /** 当天还剩多少份额可以卖出（所有基金合计），语义同 {@link #remainingDailyBuy}。 */
    public static long remainingDailySell(AccountData account, long day) {
        int limit = EEConfig.dailySellShareLimit();
        if (limit <= 0) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, (long) limit - account.fundSoldShares(day));
    }

    /** 买入：消耗账户余额，按当前价格获得份额。 */
    public static Outcome buy(EconomyData data, ServerPlayer player, FundType fund, long quantity) {
        if (quantity < 1L) {
            return Outcome.fail(Result.INVALID_QUANTITY, 0L);
        }
        long cooldown = remainingCooldown(player.getUUID(), fund);
        if (cooldown > 0L) {
            return Outcome.fail(Result.COOLDOWN, cooldown);
        }
        long today = DaySettlement.currentDay(player.serverLevel());
        AccountData account = data.account(player.getUUID());
        // 每日限购：统计的是「份额」，不分基金，也不是下单次数
        if (remainingDailyBuy(account, today) < quantity) {
            return Outcome.fail(Result.DAILY_BUY_LIMIT, 0L);
        }
        int price = data.market().fund(fund).price();
        long gross = (long) price * quantity;
        long fee = Amounts.fee(gross, EEConfig.tradeFeeRate());
        long total = gross + fee;

        if (!account.canAfford(total)) {
            return Outcome.fail(Result.INSUFFICIENT_BALANCE, 0L);
        }

        long balanceBefore = account.balance();
        long debtBefore = account.debt();
        account.addBalance(-total);
        account.holding(fund).onBuy(quantity, total);
        account.recordFundBuy(today, quantity);
        account.pushRecord(TransactionType.FUND_BUY, -total, balanceBefore, debtBefore,
                player.level().getGameTime());
        markTraded(player.getUUID(), fund);
        data.setDirty();
        return new Outcome(Result.OK, quantity, price, fee, gross, total, 0L);
    }

    /** 卖出：按当前价格卖出份额，获得绿宝石（扣手续费）。 */
    public static Outcome sell(EconomyData data, ServerPlayer player, FundType fund, long quantity) {
        if (quantity < 1L) {
            return Outcome.fail(Result.INVALID_QUANTITY, 0L);
        }
        long cooldown = remainingCooldown(player.getUUID(), fund);
        if (cooldown > 0L) {
            return Outcome.fail(Result.COOLDOWN, cooldown);
        }
        long today = DaySettlement.currentDay(player.serverLevel());
        AccountData account = data.account(player.getUUID());
        // 每日限售：同样按份额统计，所有基金合计
        if (remainingDailySell(account, today) < quantity) {
            return Outcome.fail(Result.DAILY_SELL_LIMIT, 0L);
        }
        Holding holding = account.holding(fund);
        if (holding.shares() < quantity) {
            return Outcome.fail(Result.INSUFFICIENT_SHARES, 0L);
        }
        int price = data.market().fund(fund).price();
        long gross = (long) price * quantity;
        long fee = Math.min(gross, Amounts.fee(gross, EEConfig.tradeFeeRate()));
        long net = Math.max(0L, gross - fee);

        long balanceBefore = account.balance();
        long debtBefore = account.debt();
        holding.onSell(quantity, net);
        account.addBalance(net);
        account.recordFundSell(today, quantity);
        account.pushRecord(TransactionType.FUND_SELL, net, balanceBefore, debtBefore,
                player.level().getGameTime());
        markTraded(player.getUUID(), fund);
        data.setDirty();
        return new Outcome(Result.OK, quantity, price, fee, gross, net, 0L);
    }
}
