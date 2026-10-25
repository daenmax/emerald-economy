package cn.daenx.mc.emeraldeconomy.data;

import cn.daenx.mc.emeraldeconomy.config.EEConfig;
import cn.daenx.mc.emeraldeconomy.core.Experience;
import cn.daenx.mc.emeraldeconomy.market.FundType;
import cn.daenx.mc.emeraldeconomy.market.Holding;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 单个玩家的统一经济账户。
 * <p>
 * 需求文档 1.1：每个玩家拥有独立账户，包含绿宝石余额、贷款欠款、信用额度、交易记录。
 * 账户余额与背包里的绿宝石是两回事——只有通过 ATM 才能互相转换。
 * <p>
 * 本类只负责数据存放与最基本的不变量维护，具体业务规则在
 * {@link cn.daenx.mc.emeraldeconomy.economy.AccountService} 中实现。
 */
public class AccountData {

    private static final String KEY_UUID = "player";
    private static final String KEY_NAME = "name";
    private static final String KEY_BALANCE = "balance";
    private static final String KEY_DEBT = "debt";
    private static final String KEY_DEBT_INTEREST = "debtInterest";
    private static final String KEY_CREDIT_LIMIT = "creditLimit";
    private static final String KEY_RECORDS = "records";
    private static final String KEY_HOLDINGS = "holdings";
    /** 旧版本字段：是否解锁「经验存取」。仍会写入（兼容降级），读取时作为兜底。 */
    private static final String KEY_LEVEL_UNLOCKED = "levelUnlocked";
    /** 付费解锁过的功能列表（元素是 {@link UnlockFeature} 的小写名字）。 */
    private static final String KEY_UNLOCKS = "unlocks";
    /** ATM 里存的经验点数（新字段）。 */
    private static final String KEY_STORED_EXPERIENCE = "storedXp";
    /** 旧版本字段：ATM 里存的「等级数」，只在读旧存档时用来换算。 */
    private static final String KEY_LEGACY_STORED_LEVELS = "storedLevels";
    private static final String KEY_ONLINE_REWARD_DAY = "onlineRewardDay";
    private static final String KEY_ONLINE_REWARD_CLAIMED = "onlineRewardClaimed";
    /** 基金每日买入份额限额的统计日与已用份额。 */
    private static final String KEY_FUND_BUY_DAY = "fundBuyDay";
    private static final String KEY_FUND_BUY_SHARES = "fundBuyShares";
    /** 基金每日卖出份额限额的统计日与已用份额。 */
    private static final String KEY_FUND_SELL_DAY = "fundSellDay";
    private static final String KEY_FUND_SELL_SHARES = "fundSellShares";

    private final UUID playerId;
    private String name;
    /** 绿宝石余额，整数，最小 0。 */
    private long balance;
    /** 贷款欠款（本金 + 已计入的利息），整数，最小 0。 */
    private long debt;
    /** 欠款中属于「已产生利息」的部分，还款时优先偿还这一块。 */
    private long debtInterest;
    /** 该玩家的信用额度，可被单独调整，默认取配置值。 */
    private long creditLimit;
    /**
     * 付费解锁过的功能（每人独立，默认一个都没有）。
     * <p>
     * 旧存档里没有这个字段：读出来就是空集合，即「没有解锁记录 = 需要解锁」。
     */
    private final Set<UnlockFeature> unlocks = EnumSet.noneOf(UnlockFeature.class);
    /** 存在 ATM 里的经验点数（不是等级；界面按等级输入，内部按经验结算）。 */
    private long storedExperience;
    /** 最近一次被发放「在线奖励领取资格」的游戏日（-1 表示从没拿到过）。 */
    private long onlineRewardDay = NO_REWARD_DAY;
    /** 当日资格是否已经被领走。 */
    private boolean onlineRewardClaimed;
    /** 买入份额限额对应的游戏日（-1 表示还没有统计过）。 */
    private long fundBuyDay = NO_REWARD_DAY;
    /** 该游戏日已经买入的基金份额（所有基金合计）。 */
    private long fundBuyShares;
    /** 卖出份额限额对应的游戏日（-1 表示还没有统计过）。 */
    private long fundSellDay = NO_REWARD_DAY;
    /** 该游戏日已经卖出的基金份额（所有基金合计）。 */
    private long fundSellShares;

    /** 没有在线奖励资格时的游戏日标记。 */
    public static final long NO_REWARD_DAY = -1L;
    /** 最近若干笔收支记录，新的在前。 */
    private final Deque<TransactionRecord> records = new ArrayDeque<>();
    /** 基金持仓，与账户一起持久化，保证「账户数据持久化到世界存档」。 */
    private final Map<FundType, Holding> holdings = new EnumMap<>(FundType.class);

    public AccountData(UUID playerId) {
        this.playerId = playerId;
        this.name = "";
        this.creditLimit = defaultCreditLimit();
    }

    /** 配置中的默认信用额度；配置尚未加载时退化为 0。 */
    private static long defaultCreditLimit() {
        return EEConfig.isLoaded() ? EEConfig.creditLimit() : 0L;
    }

    public UUID playerId() {
        return playerId;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        if (name != null && !name.isBlank() && !name.equals(this.name)) {
            this.name = name;
        }
    }

    public long balance() {
        return balance;
    }

    public long debt() {
        return debt;
    }

    /** 欠款中属于利息的部分。 */
    public long debtInterest() {
        return Math.min(debtInterest, debt);
    }

    /** 欠款中属于本金的部分。 */
    public long debtPrincipal() {
        return debt - debtInterest();
    }

    /** 记入一笔贷款利息（复利：直接加进欠款总额）。 */
    public void addDebtInterest(long interest) {
        if (interest <= 0L) {
            return;
        }
        debt += interest;
        debtInterest += interest;
    }

    /**
     * 偿还欠款：优先冲抵利息，再冲抵本金。
     *
     * @return 实际冲抵的利息金额
     */
    public long repayDebt(long amount) {
        if (amount <= 0L) {
            return 0L;
        }
        long paidInterest = Math.min(amount, debtInterest);
        debtInterest -= paidInterest;
        debt = Math.max(0L, debt - amount);
        if (debt == 0L) {
            debtInterest = 0L;
        }
        return paidInterest;
    }

    public long creditLimit() {
        return creditLimit;
    }

    public void setCreditLimit(long creditLimit) {
        this.creditLimit = Math.max(0L, creditLimit);
    }

    /** 可贷额度 = 信用额度 - 当前欠款（含利息），最小 0。 */
    public long availableCredit() {
        return Math.max(0L, creditLimit - debt);
    }

    // ------------------------------------------------------------------
    // 付费解锁的功能与在线奖励
    // ------------------------------------------------------------------

    /** 是否已解锁某个付费功能（没有解锁记录就视为未解锁）。 */
    public boolean isUnlocked(UnlockFeature feature) {
        return feature != null && unlocks.contains(feature);
    }

    /** 是否已解锁「经验存取」（等价于 {@code isUnlocked(EXPERIENCE_STORAGE)}）。 */
    public boolean levelUnlocked() {
        return isUnlocked(UnlockFeature.EXPERIENCE_STORAGE);
    }

    /** 解锁某个付费功能（调用方负责扣费）。 */
    public void unlockFeature(UnlockFeature feature) {
        if (feature != null) {
            unlocks.add(feature);
        }
    }

    /** 兼容旧代码：设置「经验存取」的解锁状态。 */
    public void setLevelUnlocked(boolean unlocked) {
        if (unlocked) {
            unlocks.add(UnlockFeature.EXPERIENCE_STORAGE);
        } else {
            unlocks.remove(UnlockFeature.EXPERIENCE_STORAGE);
        }
    }

    /** ATM 里存着的经验点数（不是等级）。 */
    public long storedExperience() {
        return storedExperience;
    }

    /** 直接增减库存经验（调用方负责与玩家身上的经验同步）。 */
    public void addStoredExperience(long delta) {
        storedExperience = Math.max(0L, storedExperience + delta);
    }

    /** 最近一次获得「在线奖励领取资格」的游戏日。 */
    public long onlineRewardDay() {
        return onlineRewardDay;
    }

    /** 当日在线奖励是否已经领过。 */
    public boolean onlineRewardClaimed() {
        return onlineRewardClaimed;
    }

    /**
     * 发放某一天的在线奖励领取资格（每日结算时对在线玩家调用）。
     *
     * @param day 新的一天（结算后进入的那一天）
     */
    public void grantOnlineReward(long day) {
        this.onlineRewardDay = day;
        this.onlineRewardClaimed = false;
    }

    /** 收回资格（结算时不在线的玩家：今天没有资格）。 */
    public void revokeOnlineReward() {
        this.onlineRewardDay = NO_REWARD_DAY;
        this.onlineRewardClaimed = false;
    }

    /** 标记当日奖励已领取。 */
    public void markOnlineRewardClaimed() {
        this.onlineRewardClaimed = true;
    }

    /** 今天是否有可领取的在线奖励（结算时在线且还没领）。 */
    public boolean hasOnlineReward(long today) {
        return onlineRewardDay == today && !onlineRewardClaimed;
    }

    /** 今天是否已经领过在线奖励。 */
    public boolean onlineRewardClaimedToday(long today) {
        return onlineRewardDay == today && onlineRewardClaimed;
    }

    /** 余额是否够支付指定金额。 */
    public boolean canAfford(long amount) {
        return amount >= 0 && balance >= amount;
    }

    /**
     * 直接增减余额（仅供服务端业务代码调用）。
     * <p>
     * 余额不允许为负，调用方需要先用 {@link #canAfford(long)} 校验。
     */
    public void addBalance(long delta) {
        balance = Math.max(0L, balance + delta);
    }

    /** 直接增减欠款（仅供服务端业务代码调用）。 */
    public void addDebt(long delta) {
        debt = Math.max(0L, debt + delta);
    }

    /**
     * 记录一笔收支（超出配置条数时丢弃最旧的）。
     * <p>
     * 「变动前」的两个数字要由调用方在<b>改动账户之前</b>取好传进来，
     * 「变动后」的由本方法自动填写（就是当前的余额与欠款）。
     * 这样界面上既能显示这笔操作改了多少，也能显示改完之后是多少。
     */
    public void pushRecord(TransactionType type, long amount, long balanceBefore, long debtBefore,
                           long gameTime) {
        int limit = EEConfig.isLoaded() ? EEConfig.maxTransactionRecords() : 10;
        if (limit <= 0) {
            return;
        }
        records.addFirst(new TransactionRecord(type, amount, balanceBefore, balance, debtBefore, debt,
                gameTime));
        while (records.size() > limit) {
            records.removeLast();
        }
    }

    /** 最近的交易记录（新的在前）。 */
    public Deque<TransactionRecord> records() {
        return records;
    }

    /** 取某支基金的持仓，不存在时创建空持仓。 */
    public Holding holding(FundType fund) {
        return holdings.computeIfAbsent(fund, k -> new Holding());
    }

    /** 取某支基金的持仓，不存在时返回 {@code null}（只读场景使用）。 */
    public Holding holdingOrNull(FundType fund) {
        return holdings.get(fund);
    }

    /** 全部持仓。 */
    public Map<FundType, Holding> holdings() {
        return holdings;
    }

    // ------------------------------------------------------------------
    // 基金每日买卖份额统计（每日限购 / 限售）
    // ------------------------------------------------------------------

    /**
     * 指定游戏日已经买入的份额（所有基金合计）。
     * <p>
     * 不做「定时清零」，而是用「统计日」与当前游戏日比较：
     * 日期一变，旧数字就自然失效，避免依赖结算流程是否跑过。
     */
    public long fundBoughtShares(long day) {
        return fundBuyDay == day ? fundBuyShares : 0L;
    }

    /** 指定游戏日已经卖出的份额（所有基金合计）。 */
    public long fundSoldShares(long day) {
        return fundSellDay == day ? fundSellShares : 0L;
    }

    /** 记录一笔买入份额（跨天时先清掉旧统计）。 */
    public void recordFundBuy(long day, long quantity) {
        if (quantity <= 0L) {
            return;
        }
        if (fundBuyDay != day) {
            fundBuyDay = day;
            fundBuyShares = 0L;
        }
        fundBuyShares += quantity;
    }

    /** 记录一笔卖出份额（跨天时先清掉旧统计）。 */
    public void recordFundSell(long day, long quantity) {
        if (quantity <= 0L) {
            return;
        }
        if (fundSellDay != day) {
            fundSellDay = day;
            fundSellShares = 0L;
        }
        fundSellShares += quantity;
    }

    // ------------------------------------------------------------------
    // 序列化
    // ------------------------------------------------------------------

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(KEY_UUID, playerId);
        tag.putString(KEY_NAME, name);
        tag.putLong(KEY_BALANCE, balance);
        tag.putLong(KEY_DEBT, debt);
        tag.putLong(KEY_DEBT_INTEREST, debtInterest);
        tag.putLong(KEY_CREDIT_LIMIT, creditLimit);
        tag.putBoolean(KEY_LEVEL_UNLOCKED, levelUnlocked());
        tag.putLong(KEY_STORED_EXPERIENCE, storedExperience);
        tag.putLong(KEY_ONLINE_REWARD_DAY, onlineRewardDay);
        tag.putBoolean(KEY_ONLINE_REWARD_CLAIMED, onlineRewardClaimed);
        tag.putLong(KEY_FUND_BUY_DAY, fundBuyDay);
        tag.putLong(KEY_FUND_BUY_SHARES, fundBuyShares);
        tag.putLong(KEY_FUND_SELL_DAY, fundSellDay);
        tag.putLong(KEY_FUND_SELL_SHARES, fundSellShares);
        ListTag list = new ListTag();
        for (TransactionRecord record : records) {
            list.add(record.save());
        }
        tag.put(KEY_RECORDS, list);
        CompoundTag holdingsTag = new CompoundTag();
        holdings.forEach((fund, holding) -> holdingsTag.put(fund.code(), holding.save()));
        tag.put(KEY_HOLDINGS, holdingsTag);
        ListTag unlocksTag = new ListTag();
        for (UnlockFeature feature : unlocks) {
            unlocksTag.add(StringTag.valueOf(feature.code()));
        }
        tag.put(KEY_UNLOCKS, unlocksTag);
        return tag;
    }

    public static AccountData load(CompoundTag tag) {
        // 1.21.1 的 CompoundTag#getUUID 在缺少字段时会抛异常，这里做一次保护
        UUID uuid = tag.contains(KEY_UUID) ? tag.getUUID(KEY_UUID) : new UUID(0L, 0L);
        AccountData data = new AccountData(uuid);
        data.name = tag.getString(KEY_NAME);
        data.balance = Math.max(0L, tag.getLong(KEY_BALANCE));
        data.debt = Math.max(0L, tag.getLong(KEY_DEBT));
        data.debtInterest = Math.max(0L, tag.getLong(KEY_DEBT_INTEREST));
        data.creditLimit = Math.max(0L, tag.getLong(KEY_CREDIT_LIMIT));
        // 旧存档没有这几个字段：按「未解锁 / 没有库存 / 没有资格」读取，不会读出乱数字。
        // 没有解锁记录 = 需要解锁（默认一个功能都不送）
        data.unlocks.clear();
        if (tag.contains(KEY_UNLOCKS)) {
            ListTag unlocksTag = tag.getList(KEY_UNLOCKS, Tag.TAG_STRING);
            for (int i = 0; i < unlocksTag.size(); i++) {
                UnlockFeature feature = UnlockFeature.byCode(unlocksTag.getString(i));
                if (feature != null) {
                    data.unlocks.add(feature);
                }
            }
        } else {
            // 更旧的存档只有「经验存取」一个布尔字段：为 true 时保留该功能，其余仍视为未解锁
            data.setLevelUnlocked(tag.getBoolean(KEY_LEVEL_UNLOCKED));
        }
        if (tag.contains(KEY_STORED_EXPERIENCE)) {
            data.storedExperience = Math.max(0L, tag.getLong(KEY_STORED_EXPERIENCE));
        } else if (tag.contains(KEY_LEGACY_STORED_LEVELS)) {
            // 旧版本存的是「等级数」：按「升到该等级所需的累计经验」折算，保证老存档不丢数据
            long legacyLevels = Math.max(0L, tag.getLong(KEY_LEGACY_STORED_LEVELS));
            data.storedExperience = Experience.totalXpForLevel(
                    (int) Math.min(Integer.MAX_VALUE, legacyLevels));
        }
        data.onlineRewardDay = tag.contains(KEY_ONLINE_REWARD_DAY)
                ? tag.getLong(KEY_ONLINE_REWARD_DAY) : NO_REWARD_DAY;
        data.onlineRewardClaimed = tag.getBoolean(KEY_ONLINE_REWARD_CLAIMED);
        // 旧存档没有这几个字段：读成「没统计过」，当天即可正常交易
        data.fundBuyDay = tag.contains(KEY_FUND_BUY_DAY) ? tag.getLong(KEY_FUND_BUY_DAY) : NO_REWARD_DAY;
        data.fundBuyShares = Math.max(0L, tag.getLong(KEY_FUND_BUY_SHARES));
        data.fundSellDay = tag.contains(KEY_FUND_SELL_DAY) ? tag.getLong(KEY_FUND_SELL_DAY) : NO_REWARD_DAY;
        data.fundSellShares = Math.max(0L, tag.getLong(KEY_FUND_SELL_SHARES));
        ListTag list = tag.getList(KEY_RECORDS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            data.records.addLast(TransactionRecord.load(list.getCompound(i)));
        }
        CompoundTag holdingsTag = tag.getCompound(KEY_HOLDINGS);
        for (String key : holdingsTag.getAllKeys()) {
            FundType fund = FundType.byCode(key);
            if (fund != null) {
                data.holdings.put(fund, Holding.load(holdingsTag.getCompound(key)));
            }
        }
        return data;
    }
}
