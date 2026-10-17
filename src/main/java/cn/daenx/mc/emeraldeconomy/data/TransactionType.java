package cn.daenx.mc.emeraldeconomy.data;

import cn.daenx.mc.emeraldeconomy.core.EEConstants;

import java.util.Locale;

/**
 * 交易类型。
 * <p>
 * 账户的交易记录用它来标注每一笔收支的来源，界面中显示对应的语言键。
 */
public enum TransactionType {
    /** 存款 */
    DEPOSIT,
    /** 取款 */
    WITHDRAW,
    /** 贷款到账 */
    LOAN,
    /** 还款 */
    REPAY,
    /** 账户利息 */
    INTEREST,
    /** 贷款利息（欠款增加） */
    LOAN_INTEREST,
    /** 买入基金 */
    FUND_BUY,
    /** 卖出基金 */
    FUND_SELL,
    /** 抽奖下注 */
    LOTTERY_BET,
    /** 抽奖中奖 */
    LOTTERY_WIN,
    /** 商店购买 */
    SHOP_BUY,
    /** 商店升级 */
    SHOP_UPGRADE,
    /** 收购出售 */
    ACQUIRE_SELL,
    /** 解锁 ATM 的「等级存取」功能（花绿宝石） */
    LEVEL_UNLOCK,
    /** 把玩家身上的等级存进 ATM（金额字段记的是「等级数」） */
    LEVEL_DEPOSIT,
    /** 从 ATM 取回等级（金额字段记的是「等级数」） */
    LEVEL_WITHDRAW,
    /** 每日在线奖励（金额字段记的是「赠送的等级数」） */
    ONLINE_REWARD;

    private final String langKey = EEConstants.langKey("tx." + name().toLowerCase(Locale.ROOT));

    /** 该交易类型的翻译键。 */
    public String langKey() {
        return langKey;
    }

    /**
     * 这笔记录动的是「等级」而不是「绿宝石余额」。
     * <p>
     * 等级存取不会改变账户余额，所以交易记录界面不能只按「余额有没有变」来筛选，
     * 否则这些记录会被静默丢掉；金额列也要按「级」而不是「绿宝石」来展示。
     */
    public boolean isLevelRecord() {
        return this == LEVEL_DEPOSIT || this == LEVEL_WITHDRAW || this == ONLINE_REWARD;
    }

    /** 按名字解析，失败时返回默认值（用于 NBT 反序列化）。 */
    public static TransactionType byName(String name, TransactionType fallback) {
        if (name == null) {
            return fallback;
        }
        try {
            return valueOf(name);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
