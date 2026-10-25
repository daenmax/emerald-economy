package cn.daenx.mc.emeraldeconomy.data;

import cn.daenx.mc.emeraldeconomy.config.EEConfig;

import java.util.Locale;

/**
 * 需要「每个玩家付费解锁一次」的功能。
 * <p>
 * 这些功能对所有玩家默认关闭：没有解锁记录（旧存档里没有对应字段）就视为未解锁，
 * 想用必须先付一笔一次性的解锁费（金额来自服务端配置，可调）。
 * 解锁状态随玩家账户持久化，付过一次之后永久可用。
 * <ul>
 *     <li>{@link #EXPERIENCE_STORAGE}：ATM 的「经验存取」</li>
 *     <li>{@link #ENCHANT_RECYCLE}：自动收购机的「附魔回收」分类</li>
 *     <li>{@link #CRAZY_FRIDAY}：自动收购机的「疯狂星期五」</li>
 *     <li>{@link #ENCHANT_BOOK}：自动售货机的「附魔书」大分类</li>
 * </ul>
 */
public enum UnlockFeature {

    /** ATM 的「经验存取」（最早推出的解锁功能，费用沿用 account.levelUnlockCost）。 */
    EXPERIENCE_STORAGE("atm.level_unlock_ok"),

    /** 自动收购机的「附魔回收」分类。 */
    ENCHANT_RECYCLE("acquire.enchant_unlock_ok"),

    /** 自动收购机的「疯狂星期五」。 */
    CRAZY_FRIDAY("acquire.friday_unlock_ok"),

    /** 自动售货机的「附魔书」大分类。 */
    ENCHANT_BOOK("shop.book_unlock_ok");

    /**
     * 解锁成功提示的语言键（<b>短键</b>，不含模组前缀）。
     * <p>
     * 调用方会把它交给 {@code Notify} / {@code EEConstants.langKey}，
     * 那一层会自动补上 {@code emerald_economy.} 前缀——这里如果存成完整键，
     * 就会出现「emerald_economy.emerald_economy.xxx」的双重前缀，客户端查不到翻译，
     * 只能把原始键名显示在聊天栏里。
     */
    private final String successLangKey;

    UnlockFeature(String successLangKey) {
        this.successLangKey = successLangKey;
    }

    /** 解锁费用（绿宝石），来自服务端配置。 */
    public long cost() {
        return switch (this) {
            case EXPERIENCE_STORAGE -> EEConfig.levelUnlockCost();
            case ENCHANT_RECYCLE -> EEConfig.enchantRecycleUnlockCost();
            case CRAZY_FRIDAY -> EEConfig.crazyFridayUnlockCost();
            case ENCHANT_BOOK -> EEConfig.enchantBookUnlockCost();
        };
    }

    /** 解锁时记入交易流水的类型。 */
    public TransactionType transactionType() {
        return switch (this) {
            case EXPERIENCE_STORAGE -> TransactionType.LEVEL_UNLOCK;
            case ENCHANT_RECYCLE -> TransactionType.ENCHANT_RECYCLE_UNLOCK;
            case CRAZY_FRIDAY -> TransactionType.CRAZY_FRIDAY_UNLOCK;
            case ENCHANT_BOOK -> TransactionType.ENCHANT_BOOK_UNLOCK;
        };
    }

    /** 解锁成功提示的语言键（短键，交给 Notify 时会自动补模组前缀）。 */
    public String successLangKey() {
        return successLangKey;
    }

    /** 存档里用的名字（小写枚举名）。 */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** 按存档名字解析，失败时返回 {@code null}。 */
    public static UnlockFeature byCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return UnlockFeature.valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
