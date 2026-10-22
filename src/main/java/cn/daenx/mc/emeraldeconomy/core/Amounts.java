package cn.daenx.mc.emeraldeconomy.core;

/**
 * 金额计算工具。
 * <p>
 * 本模组所有金额、数量、份额都是整数，最小单位为 1 绿宝石，不存在小数。
 * 所有涉及小数的地方都在这里统一取整，保证「取整方式：四舍五入，最小为 1」的规则一致。
 */
public final class Amounts {

    private Amounts() {
    }

    /** 四舍五入取整，并保证结果不小于 1。 */
    public static int roundPrice(double value) {
        long rounded = Math.round(value);
        return (int) Math.max(EEConstants.MIN_PRICE, rounded);
    }

    /** 四舍五入取整，并保证结果不小于 1（long 版本，用于金额）。 */
    public static long roundAmount(double value) {
        long rounded = Math.round(value);
        return Math.max(EEConstants.MIN_AMOUNT, rounded);
    }

    /** 向下取整，并保证结果不小于 0（用于利息）。 */
    public static long floorNonNegative(double value) {
        return Math.max(0L, (long) Math.floor(value));
    }

    /** 向下取整，并保证结果不小于 1（用于中奖金额、收购价格）。 */
    public static long floorAtLeastOne(double value) {
        return Math.max(EEConstants.MIN_AMOUNT, (long) Math.floor(value));
    }

    /**
     * 计算手续费。
     * <p>
     * 规则：{@code 金额 × 费率} 向下取整，最低收 1 个绿宝石；金额为 0 时手续费为 0。
     */
    public static long fee(long amount, double rate) {
        if (amount <= 0L || rate <= 0.0D) {
            return 0L;
        }
        return Math.max(EEConstants.MIN_AMOUNT, (long) Math.floor(amount * rate));
    }

    /** 计算利息（向下取整，最低 0）。 */
    public static long interest(long principal, double rate) {
        if (principal <= 0L || rate <= 0.0D) {
            return 0L;
        }
        return floorNonNegative(principal * rate);
    }

    /**
     * 按余额反推「最多能买多少份」。
     * <p>
     * 每份价格固定、手续费随成交额变大，所以花费对份额是单调递增的，
     * 这里用二分查找求满足 {@code 成交额 + 手续费 ≤ 余额} 的最大份额。
     * 只用于界面提示，真正下单时服务端还会再校验一次。
     *
     * @param balance   账户余额
     * @param unitPrice 每份单价
     * @param feeRate   手续费率（0.02 表示 2%）
     * @return 最多可买份额，余额不够买 1 份时返回 0
     */
    public static long affordableQuantity(long balance, long unitPrice, double feeRate) {
        if (balance <= 0L || unitPrice <= 0L) {
            return 0L;
        }
        long low = 0L;
        long high = balance / unitPrice;
        while (low < high) {
            long mid = low + (high - low + 1L) / 2L;
            if (buyCost(unitPrice, mid, feeRate) <= balance) {
                low = mid;
            } else {
                high = mid - 1L;
            }
        }
        return low;
    }

    /** 买入总花费（成交额 + 手续费），溢出时返回 {@link Long#MAX_VALUE}。 */
    private static long buyCost(long unitPrice, long quantity, double feeRate) {
        if (quantity <= 0L) {
            return 0L;
        }
        long gross = unitPrice * quantity;
        long fee = fee(gross, feeRate);
        if (gross > Long.MAX_VALUE - fee) {
            return Long.MAX_VALUE;
        }
        return gross + fee;
    }

    /** 把金额限制在 [min, max] 区间内。 */
    public static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    /** 安全地把 long 转成 int（用于网络同步与界面显示，超出范围时截断）。 */
    public static int toInt(long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) value;
    }

    /** 把金额格式化成带千分位的字符串，方便在界面中阅读。 */
    public static String format(long value) {
        return String.format("%,d", value);
    }
}
