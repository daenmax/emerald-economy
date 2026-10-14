package cn.daenx.mc.emeraldeconomy.market;

import net.minecraft.nbt.CompoundTag;

/**
 * 玩家的基金持仓（需求文档 3.4.1）。
 * <p>
 * 用「持有份额 + 累计成本」表示，平均成本按需计算，避免整数除法带来的精度损失。
 */
public class Holding {

    private static final String KEY_SHARES = "shares";
    private static final String KEY_TOTAL_COST = "totalCost";
    private static final String KEY_REALIZED = "realized";

    /** 持有份额（整数，最小 0）。 */
    private long shares;
    /** 累计买入成本（含手续费）。 */
    private long totalCost;
    /** 已实现收益（卖出后结算）。 */
    private long realizedProfit;

    public long shares() {
        return shares;
    }

    public long totalCost() {
        return totalCost;
    }

    public long realizedProfit() {
        return realizedProfit;
    }

    /** 平均成本（每份），无持仓时返回 0。 */
    public long averageCost() {
        return shares > 0 ? totalCost / shares : 0L;
    }

    /** 记录一次买入。 */
    public void onBuy(long quantity, long cost) {
        if (quantity <= 0) {
            return;
        }
        shares += quantity;
        totalCost += Math.max(0L, cost);
    }

    /**
     * 记录一次卖出。
     *
     * @param quantity 卖出份额
     * @param proceeds 实际到手的绿宝石（已扣手续费）
     * @return 本次实现的盈亏
     */
    public long onSell(long quantity, long proceeds) {
        if (quantity <= 0 || quantity > shares) {
            return 0L;
        }
        long costPart = shares > 0 ? (totalCost / shares) * quantity : 0L;
        long profit = proceeds - costPart;
        shares -= quantity;
        totalCost -= costPart;
        if (shares == 0) {
            // 清仓时把剩余的取整误差一并清掉，避免残留
            totalCost = 0L;
        }
        realizedProfit += profit;
        return profit;
    }

    /** 按给定价格计算当前市值。 */
    public long marketValue(int price) {
        return shares * (long) price;
    }

    /** 按给定价格计算浮动盈亏。 */
    public long unrealizedProfit(int price) {
        return marketValue(price) - totalCost;
    }

    public void setShares(long shares) {
        this.shares = Math.max(0L, shares);
    }

    public void setTotalCost(long totalCost) {
        this.totalCost = Math.max(0L, totalCost);
    }

    public void setRealizedProfit(long realizedProfit) {
        this.realizedProfit = realizedProfit;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong(KEY_SHARES, shares);
        tag.putLong(KEY_TOTAL_COST, totalCost);
        tag.putLong(KEY_REALIZED, realizedProfit);
        return tag;
    }

    public static Holding load(CompoundTag tag) {
        Holding holding = new Holding();
        holding.shares = Math.max(0L, tag.getLong(KEY_SHARES));
        holding.totalCost = Math.max(0L, tag.getLong(KEY_TOTAL_COST));
        holding.realizedProfit = tag.getLong(KEY_REALIZED);
        return holding;
    }
}
