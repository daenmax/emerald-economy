package cn.daenx.mc.emeraldeconomy.data;

import net.minecraft.nbt.CompoundTag;

/**
 * 一条账户收支记录。
 * <p>
 * 同时记下这笔操作<b>前后</b>的「余额」与「贷款欠款」，这样 ATM 的交易记录界面
 * 能分两个分类把「账户余额明细」和「贷款欠款明细」都列出来，并且每一条都能显示
 * 变动前 / 变动后各是多少（需求：显示余额变动情况、贷款欠额变动情况）。
 *
 * @param type          交易类型
 * @param amount        变动金额（正数表示收入，负数表示支出）
 * @param balanceBefore 操作前的余额
 * @param balanceAfter  操作后的余额
 * @param debtBefore    操作前的欠款总额（本金 + 利息）
 * @param debtAfter     操作后的欠款总额
 * @param gameTime      发生时间（游戏刻）
 */
public record TransactionRecord(TransactionType type, long amount, long balanceBefore, long balanceAfter,
                                long debtBefore, long debtAfter, long gameTime) {

    private static final String KEY_TYPE = "type";
    private static final String KEY_AMOUNT = "amount";
    private static final String KEY_BALANCE_BEFORE = "balanceBefore";
    private static final String KEY_BALANCE = "balance";
    private static final String KEY_DEBT_BEFORE = "debtBefore";
    private static final String KEY_DEBT = "debt";
    private static final String KEY_TIME = "time";

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY_TYPE, type.name());
        tag.putLong(KEY_AMOUNT, amount);
        tag.putLong(KEY_BALANCE_BEFORE, balanceBefore);
        tag.putLong(KEY_BALANCE, balanceAfter);
        tag.putLong(KEY_DEBT_BEFORE, debtBefore);
        tag.putLong(KEY_DEBT, debtAfter);
        tag.putLong(KEY_TIME, gameTime);
        return tag;
    }

    public static TransactionRecord load(CompoundTag tag) {
        // before / debt 都是后加的字段：旧存档缺这几项时按「改动前后一样」处理，
        // 不会让已有流水读出乱数字。
        long balanceAfter = tag.getLong(KEY_BALANCE);
        long debtAfter = tag.contains(KEY_DEBT) ? tag.getLong(KEY_DEBT) : 0L;
        return new TransactionRecord(
                TransactionType.byName(tag.getString(KEY_TYPE), TransactionType.DEPOSIT),
                tag.getLong(KEY_AMOUNT),
                tag.contains(KEY_BALANCE_BEFORE) ? tag.getLong(KEY_BALANCE_BEFORE) : balanceAfter,
                balanceAfter,
                tag.contains(KEY_DEBT_BEFORE) ? tag.getLong(KEY_DEBT_BEFORE) : debtAfter,
                debtAfter,
                tag.getLong(KEY_TIME));
    }
}
