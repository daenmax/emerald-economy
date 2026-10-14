package cn.daenx.mc.emeraldeconomy.data;

import cn.daenx.mc.emeraldeconomy.acquire.AcquireBook;
import cn.daenx.mc.emeraldeconomy.core.EEConstants;
import cn.daenx.mc.emeraldeconomy.lottery.LotteryBook;
import cn.daenx.mc.emeraldeconomy.market.MarketBook;
import cn.daenx.mc.emeraldeconomy.shop.ShopBook;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 模组的全局存档数据（服务端权威）。
 * <p>
 * 需求文档要求「账户数据必须持久化到世界存档」，这里用 NeoForge / 原版的
 * {@link SavedData} 机制挂在主世界（Overworld）的数据存储上，随存档一起保存。
 * <p>
 * 里面聚合了五块数据：
 * <ul>
 *     <li>{@link AccountData}：每个玩家的统一经济账户（余额、欠款、信用额度、持仓、流水）</li>
 *     <li>{@link MarketBook}：七支基金的价格状态、当日供给量、市场新闻</li>
 *     <li>{@link LotteryBook}：各场次的下注与开奖结果</li>
 *     <li>{@link ShopBook}：商店格子解锁进度与限购计数</li>
 *     <li>{@link AcquireBook}：疯狂星期五状态</li>
 * </ul>
 */
public class EconomyData extends SavedData {

    /** 存档文件名。 */
    public static final String DATA_NAME = EEConstants.MOD_ID;

    private static final String KEY_ACCOUNTS = "accounts";
    private static final String KEY_MARKET = "market";
    private static final String KEY_LOTTERY = "lottery";
    private static final String KEY_SHOP = "shop";
    private static final String KEY_ACQUIRE = "acquire";
    private static final String KEY_LAST_SETTLED_DAY = "lastSettledDay";
    private static final String KEY_LAST_SETTLE_TIME = "lastSettleGameTime";
    private static final String KEY_LAST_PRICE_TICK = "lastPriceTick";
    private static final String KEY_MARKET_INITIALIZED = "marketInitialized";

    public static final SavedData.Factory<EconomyData> FACTORY =
            new SavedData.Factory<>(EconomyData::new, EconomyData::load, null);

    private final Map<UUID, AccountData> accounts = new HashMap<>();
    private final MarketBook market = new MarketBook();
    private final LotteryBook lottery = new LotteryBook();
    private final ShopBook shop = new ShopBook();
    private final AcquireBook acquire = new AcquireBook();

    /** 上一次完成每日结算的游戏日。 */
    private long lastSettledDay = -1L;
    /** 上一次完成每日结算时的游戏刻，用于防止重复结算。 */
    private long lastSettleGameTime = -1L;
    /** 上一次价格更新时的游戏刻。 */
    private long lastPriceTick = -1L;
    /** 市场初始化标记（存档里已存在时不再按配置重置基础价格）。 */
    private boolean marketInitialized = false;

    public EconomyData() {
    }

    // ------------------------------------------------------------------
    // 访问入口
    // ------------------------------------------------------------------

    /** 取得当前服务器的模组数据（不存在时创建）。 */
    public static EconomyData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /** 从任意 Level 取得数据；客户端或没有服务端时返回 {@code null}。 */
    public static EconomyData getOrNull(Level level) {
        MinecraftServer server = level == null ? null : level.getServer();
        return server == null ? null : get(server);
    }

    // ------------------------------------------------------------------
    // 账户
    // ------------------------------------------------------------------

    public Map<UUID, AccountData> accounts() {
        return accounts;
    }

    /** 取玩家账户，不存在时创建并标记存档需要保存。 */
    public AccountData account(UUID playerId) {
        AccountData data = accounts.get(playerId);
        if (data == null) {
            data = new AccountData(playerId);
            accounts.put(playerId, data);
            setDirty();
        }
        return data;
    }

    public AccountData accountOrNull(UUID playerId) {
        return accounts.get(playerId);
    }

    public MarketBook market() {
        return market;
    }

    public LotteryBook lottery() {
        return lottery;
    }

    public ShopBook shop() {
        return shop;
    }

    public AcquireBook acquire() {
        return acquire;
    }

    public long lastSettledDay() {
        return lastSettledDay;
    }

    public void setLastSettledDay(long day) {
        this.lastSettledDay = day;
        setDirty();
    }

    public long lastSettleGameTime() {
        return lastSettleGameTime;
    }

    public void setLastSettleGameTime(long gameTime) {
        this.lastSettleGameTime = gameTime;
        setDirty();
    }

    public long lastPriceTick() {
        return lastPriceTick;
    }

    public void setLastPriceTick(long tick) {
        this.lastPriceTick = tick;
        setDirty();
    }

    /** 首次访问时按配置初始化市场基础参数。 */
    public void ensureMarketInitialized() {
        if (!marketInitialized) {
            market.ensureInitialized();
            marketInitialized = true;
            setDirty();
        }
    }

    // ------------------------------------------------------------------
    // 序列化
    // ------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag accountsTag = new CompoundTag();
        accounts.forEach((uuid, data) -> accountsTag.put(uuid.toString(), data.save()));
        tag.put(KEY_ACCOUNTS, accountsTag);

        tag.put(KEY_MARKET, market.save());
        tag.put(KEY_LOTTERY, lottery.save());
        tag.put(KEY_SHOP, shop.save());
        tag.put(KEY_ACQUIRE, acquire.save());

        tag.putLong(KEY_LAST_SETTLED_DAY, lastSettledDay);
        tag.putLong(KEY_LAST_SETTLE_TIME, lastSettleGameTime);
        tag.putLong(KEY_LAST_PRICE_TICK, lastPriceTick);
        tag.putBoolean(KEY_MARKET_INITIALIZED, marketInitialized);
        return tag;
    }

    public static EconomyData load(CompoundTag tag, HolderLookup.Provider registries) {
        EconomyData data = new EconomyData();
        CompoundTag accountsTag = tag.getCompound(KEY_ACCOUNTS);
        for (String key : accountsTag.getAllKeys()) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            data.accounts.put(uuid, AccountData.load(accountsTag.getCompound(key)));
        }
        data.market.load(tag.getCompound(KEY_MARKET));
        data.lottery.load(tag.getCompound(KEY_LOTTERY));
        data.shop.load(tag.getCompound(KEY_SHOP));
        data.acquire.load(tag.getCompound(KEY_ACQUIRE));

        data.lastSettledDay = tag.contains(KEY_LAST_SETTLED_DAY) ? tag.getLong(KEY_LAST_SETTLED_DAY) : -1L;
        data.lastSettleGameTime = tag.contains(KEY_LAST_SETTLE_TIME) ? tag.getLong(KEY_LAST_SETTLE_TIME) : -1L;
        data.lastPriceTick = tag.contains(KEY_LAST_PRICE_TICK) ? tag.getLong(KEY_LAST_PRICE_TICK) : -1L;
        data.marketInitialized = tag.getBoolean(KEY_MARKET_INITIALIZED);
        return data;
    }
}
