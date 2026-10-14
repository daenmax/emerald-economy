package cn.daenx.mc.emeraldeconomy.config;

import cn.daenx.mc.emeraldeconomy.core.EEConstants;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * 模组配置（服务端配置，文件为 {@code config/emerald_economy-server.toml}）。
 * <p>
 * 需求文档第八节要求的所有可配置项都在这里暴露。为了不使用硬编码，
 * 「价格表 / 修正表」这类表结构用字符串列表表示，格式为 {@code key=value}。
 */
public final class EEConfig {

    public static final ModConfigSpec SPEC;

    // ------------------------------------------------------------------
    // 账户
    // ------------------------------------------------------------------
    private static final ModConfigSpec.DoubleValue DEPOSIT_FEE_RATE;
    private static final ModConfigSpec.DoubleValue WITHDRAW_FEE_RATE;
    private static final ModConfigSpec.DoubleValue DAILY_INTEREST_RATE;
    private static final ModConfigSpec.LongValue INTEREST_MIN_BALANCE;
    private static final ModConfigSpec.DoubleValue LOAN_INTEREST_RATE;
    private static final ModConfigSpec.LongValue CREDIT_LIMIT;
    private static final ModConfigSpec.IntValue MAX_TRANSACTION_RECORDS;

    // ------------------------------------------------------------------
    // 基金市场
    // ------------------------------------------------------------------
    private static final ModConfigSpec.IntValue PRICE_UPDATE_INTERVAL_TICKS;
    private static final ModConfigSpec.DoubleValue MOMENTUM_DECAY;
    private static final ModConfigSpec.DoubleValue MEAN_REVERSION;
    private static final ModConfigSpec.DoubleValue MAX_MOMENTUM;
    private static final ModConfigSpec.DoubleValue PRICE_MIN_FACTOR;
    private static final ModConfigSpec.DoubleValue PRICE_MAX_FACTOR;
    private static final ModConfigSpec.DoubleValue EVENT_SENSITIVITY;
    private static final ModConfigSpec.DoubleValue WEATHER_STRENGTH;
    private static final ModConfigSpec.DoubleValue MARKET_EVENT_CHANCE;
    private static final ModConfigSpec.DoubleValue TRADE_FEE_RATE;
    private static final ModConfigSpec.IntValue TRADE_COOLDOWN_MILLIS;
    private static final ModConfigSpec.IntValue HISTORY_SIZE;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> FUND_BASE_PRICES;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> FUND_VOLATILITIES;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> FUND_MEAN_REVERSIONS;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> FUND_DAILY_SUPPLIES;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> WEATHER_MODIFIERS;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> MARKET_EVENTS;

    // ------------------------------------------------------------------
    // 抽奖
    // ------------------------------------------------------------------
    private static final ModConfigSpec.DoubleValue ODDS_ODD_EVEN;
    private static final ModConfigSpec.DoubleValue ODDS_BIG_SMALL;
    private static final ModConfigSpec.DoubleValue ODDS_LAST_DIGIT;
    private static final ModConfigSpec.IntValue LOTTERY_DAILY_LIMIT;

    // ------------------------------------------------------------------
    // 商店
    // ------------------------------------------------------------------
    private static final ModConfigSpec.IntValue SHOP_INITIAL_SLOTS;
    private static final ModConfigSpec.IntValue SHOP_DAILY_PURCHASE_LIMIT;
    private static final ModConfigSpec.IntValue SHOP_UPGRADE_COST_MIN;
    private static final ModConfigSpec.IntValue SHOP_UPGRADE_COST_MAX;
    private static final ModConfigSpec.IntValue SHOP_PRICE_MIN;
    private static final ModConfigSpec.IntValue SHOP_PRICE_MAX;
    private static final ModConfigSpec.DoubleValue SHOP_TREASURE_MULTIPLIER_MIN;
    private static final ModConfigSpec.DoubleValue SHOP_TREASURE_MULTIPLIER_MAX;

    // ------------------------------------------------------------------
    // 收购
    // ------------------------------------------------------------------
    private static final ModConfigSpec.IntValue FRIDAY_INTERVAL_DAYS;
    private static final ModConfigSpec.IntValue FRIDAY_PICK_COUNT;
    private static final ModConfigSpec.DoubleValue FRIDAY_MULTIPLIER_MIN;
    private static final ModConfigSpec.DoubleValue FRIDAY_MULTIPLIER_MAX;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        // ============================ 账户 ============================
        b.comment("统一经济账户相关配置").push("account");
        DEPOSIT_FEE_RATE = b.comment("存款手续费率，默认 0.01 表示 1%")
                .defineInRange("depositFeeRate", 0.01D, 0.0D, 1.0D);
        WITHDRAW_FEE_RATE = b.comment("取款手续费率，默认 0.01 表示 1%")
                .defineInRange("withdrawFeeRate", 0.01D, 0.0D, 1.0D);
        DAILY_INTEREST_RATE = b.comment("账户余额日利息率，默认 0.005 表示每日 0.500%")
                .defineInRange("dailyInterestRate", 0.005D, 0.0D, 1.0D);
        INTEREST_MIN_BALANCE = b.comment("""
                账户余额达到该数值才开始计算日利息（含等于），低于该值不计息。
                默认 200 绿宝石。""")
                .defineInRange("interestMinBalance", 200L, 0L, Long.MAX_VALUE);
        LOAN_INTEREST_RATE = b.comment("贷款日利息率（复利），默认 0.02 表示每日 2%")
                .defineInRange("loanInterestRate", 0.02D, 0.0D, 1.0D);
        CREDIT_LIMIT = b.comment("玩家信用额度（可贷款上限），单位绿宝石")
                .defineInRange("creditLimit", 1000L, 0L, Long.MAX_VALUE);
        MAX_TRANSACTION_RECORDS = b.comment("每个账户保留的最近交易记录条数（ATM 的「交易记录」界面会全部列出）")
                .defineInRange("maxTransactionRecords", 100, 0, 1000);
        b.pop();

        // ============================ 基金市场 ============================
        b.comment("基金价格引擎与交易相关配置").push("market");
        PRICE_UPDATE_INTERVAL_TICKS = b.comment("价格更新间隔（游戏刻），默认 6000 刻 = 游戏内 5 分钟")
                .defineInRange("priceUpdateIntervalTicks", 6000, 20, 24000);
        MOMENTUM_DECAY = b.comment("每次更新的动量衰减系数")
                .defineInRange("momentumDecay", 0.95D, 0.0D, 1.0D);
        MEAN_REVERSION = b.comment("默认均值回归系数（每支基金可在 fundMeanReversions 中单独覆盖）")
                .defineInRange("meanReversion", 0.02D, 0.0D, 1.0D);
        MAX_MOMENTUM = b.comment("单次最大波动幅度（动量绝对值上限）")
                .defineInRange("maxMomentum", 0.15D, 0.0D, 1.0D);
        PRICE_MIN_FACTOR = b.comment("价格硬性下界 = 基础价格 × 该系数")
                .defineInRange("priceMinFactor", 0.3D, 0.01D, 10.0D);
        PRICE_MAX_FACTOR = b.comment("价格硬性上界 = 基础价格 × 该系数")
                .defineInRange("priceMaxFactor", 3.0D, 0.01D, 100.0D);
        EVENT_SENSITIVITY = b.comment("世界事件修正灵敏度（供给比取对数后的放大系数）")
                .defineInRange("eventSensitivity", 0.05D, 0.0D, 10.0D);
        WEATHER_STRENGTH = b.comment("天气修正强度系数，会与 weatherModifiers 中的数值相乘")
                .defineInRange("weatherStrength", 1.0D, 0.0D, 10.0D);
        MARKET_EVENT_CHANCE = b.comment("每日触发随机市场新闻事件的概率，0.15 表示 15%")
                .defineInRange("marketEventChance", 0.15D, 0.0D, 1.0D);
        TRADE_FEE_RATE = b.comment("基金交易手续费率，0.02 表示 2%")
                .defineInRange("tradeFeeRate", 0.02D, 0.0D, 1.0D);
        TRADE_COOLDOWN_MILLIS = b.comment("同一支基金的交易冷却时间（毫秒）")
                .defineInRange("tradeCooldownMillis", 5000, 0, 600000);
        HISTORY_SIZE = b.comment("走势图保留的价格点数量")
                .defineInRange("historySize", EEConstants.DEFAULT_HISTORY_SIZE, 8, 512);
        FUND_BASE_PRICES = b.comment("各基金的基础价格，格式 EMR_MIN=100")
                .defineList("fundBasePrices", defaults(
                        "EMR_MIN=100", "EMR_AGR=80", "EMR_LIV=90",
                        "EMR_BLD=70", "EMR_HYD=85", "EMR_HUN=95", "EMR_CMP=100"),
                        o -> o instanceof String);
        FUND_VOLATILITIES = b.comment("各基金波动率（矿产高、综合低），格式 EMR_MIN=0.06")
                .defineList("fundVolatilities", defaults(
                        "EMR_MIN=0.060", "EMR_AGR=0.045", "EMR_LIV=0.040",
                        "EMR_BLD=0.035", "EMR_HYD=0.050", "EMR_HUN=0.055", "EMR_CMP=0.020"),
                        o -> o instanceof String);
        FUND_MEAN_REVERSIONS = b.comment("各基金均值回归系数，格式 EMR_MIN=0.03")
                .defineList("fundMeanReversions", defaults(
                        "EMR_MIN=0.030", "EMR_AGR=0.025", "EMR_LIV=0.022",
                        "EMR_BLD=0.020", "EMR_HYD=0.025", "EMR_HUN=0.028", "EMR_CMP=0.020"),
                        o -> o instanceof String);
        FUND_DAILY_SUPPLIES = b.comment("""
                每日预期供给量（用于事件修正的供给比），格式 EMR_MIN=800。
                综合基金这一项表示「每日预期村民交易次数」。""")
                .defineList("fundDailySupplies", defaults(
                        "EMR_MIN=800", "EMR_AGR=1200", "EMR_LIV=600",
                        "EMR_BLD=1500", "EMR_HYD=700", "EMR_HUN=500", "EMR_CMP=200"),
                        o -> o instanceof String);
        WEATHER_MODIFIERS = b.comment("""
                每日结算时的天气修正表，格式 天气:基金=动量修正。
                天气取值：CLEAR（晴天）、RAIN（雨天）、THUNDER（雷暴）。
                综合基金由其它六支加权得出，这里的 EMR_CMP 只作为额外情绪修正。""")
                .defineList("weatherModifiers", defaults(
                        "CLEAR:EMR_AGR=0.030", "CLEAR:EMR_BLD=0.020",
                        "CLEAR:EMR_HYD=-0.020", "CLEAR:EMR_HUN=0.020",
                        "RAIN:EMR_AGR=0.080", "RAIN:EMR_LIV=-0.030", "RAIN:EMR_BLD=-0.060",
                        "RAIN:EMR_HYD=0.070", "RAIN:EMR_HUN=-0.060", "RAIN:EMR_CMP=0.020",
                        "THUNDER:EMR_MIN=-0.030", "THUNDER:EMR_AGR=-0.030", "THUNDER:EMR_LIV=-0.070",
                        "THUNDER:EMR_BLD=-0.060", "THUNDER:EMR_HYD=0.090", "THUNDER:EMR_HUN=-0.070",
                        "THUNDER:EMR_CMP=-0.020"),
                        o -> o instanceof String);
        MARKET_EVENTS = b.comment("""
                随机市场新闻事件表，格式 作用范围|动量修正|语言键后缀。
                作用范围可填 ALL（全部基金）或具体基金代码。""")
                .defineList("marketEvents", defaults(
                        "EMR_MIN|0.08|news.vein_found",
                        "EMR_MIN|-0.10|news.mine_disaster",
                        "EMR_AGR|0.08|news.bumper_harvest",
                        "EMR_AGR|-0.10|news.crop_blight",
                        "EMR_LIV|0.08|news.ranch_boom",
                        "EMR_LIV|-0.10|news.plague",
                        "EMR_BLD|0.08|news.construction_boom",
                        "EMR_BLD|-0.10|news.land_slump",
                        "EMR_HYD|0.08|news.energy_breakthrough",
                        "EMR_HYD|-0.10|news.power_outage",
                        "EMR_HUN|0.08|news.hunting_season",
                        "EMR_HUN|-0.10|news.game_depleted",
                        "ALL|0.05|news.trade_boom",
                        "ALL|-0.06|news.recession",
                        "EMR_CMP|-0.12|news.emerald_devalue"),
                        o -> o instanceof String);
        b.pop();

        // ============================ 抽奖 ============================
        b.comment("抽奖场次配置").push("lottery");
        ODDS_ODD_EVEN = b.comment("猜单双赔率")
                .defineInRange("oddsOddEven", 1.95D, 1.0D, 1000.0D);
        ODDS_BIG_SMALL = b.comment("猜大小赔率")
                .defineInRange("oddsBigSmall", 1.95D, 1.0D, 1000.0D);
        ODDS_LAST_DIGIT = b.comment("猜尾数赔率")
                .defineInRange("oddsLastDigit", 9.5D, 1.0D, 1000.0D);
        LOTTERY_DAILY_LIMIT = b.comment("每个玩家每个场次每天可下注次数")
                .defineInRange("dailyBetLimit", 1, 1, 100);
        b.pop();

        // ============================ 商店 ============================
        b.comment("自动售货机（附魔书商店）配置").push("shop");
        SHOP_INITIAL_SLOTS = b.comment("初始解锁的格子数量")
                .defineInRange("initialSlots", 1, 1, 128);
        SHOP_DAILY_PURCHASE_LIMIT = b.comment("每个格子每人每天限购次数")
                .defineInRange("dailyPurchaseLimit", 2, 1, 1000);
        SHOP_UPGRADE_COST_MIN = b.comment("""
                升级（解锁下一个格子）的费用下限。
                每一次升级的费用会在「下限 ~ 上限」之间随机取一个值，
                取值由目标格子的序号决定，因此同一级的价格是固定的（不会刷新一次变一次）。""")
                .defineInRange("upgradeCostMin", 512, 1, Integer.MAX_VALUE);
        SHOP_UPGRADE_COST_MAX = b.comment("升级（解锁下一个格子）的费用上限，默认与下限组成 512-1280 的随机区间")
                .defineInRange("upgradeCostMax", 1280, 1, Integer.MAX_VALUE);
        SHOP_PRICE_MIN = b.comment("未在 shop.json 里列出的附魔书的最低价格（给整合包新增的附魔兜底）")
                .defineInRange("priceMin", 256, 1, Integer.MAX_VALUE);
        SHOP_PRICE_MAX = b.comment("未在 shop.json 里列出的附魔书的最高价格（兜底）")
                .defineInRange("priceMax", 1280, 1, Integer.MAX_VALUE);
        SHOP_TREASURE_MULTIPLIER_MIN = b.comment("未在 shop.json 里列出的宝藏附魔价格倍率下限（兜底）")
                .defineInRange("treasureMultiplierMin", 3.0D, 1.0D, 100.0D);
        SHOP_TREASURE_MULTIPLIER_MAX = b.comment("未在 shop.json 里列出的宝藏附魔价格倍率上限（兜底）")
                .defineInRange("treasureMultiplierMax", 5.0D, 1.0D, 100.0D);
        b.pop();

        // ============================ 收购 ============================
        // 收什么、多少钱、几个算一份，全部在 config/emerald_economy/acquire.json 里配置
        // （见 content/EconomyContent），这里只留「疯狂星期五怎么开」与倍率这类数值。
        b.comment("自动收购机（价格表见 config/emerald_economy/acquire.json）").push("acquire");
        FRIDAY_INTERVAL_DAYS = b.comment("疯狂星期五开放间隔（游戏日，逢 N 开放）")
                .defineInRange("fridayIntervalDays", 5, 1, 1000);
        FRIDAY_PICK_COUNT = b.comment("疯狂星期五每日随机挑选的重金收购物品种类数")
                .defineInRange("fridayPickCount", 3, 1, 64);
        FRIDAY_MULTIPLIER_MIN = b.comment("疯狂星期五收购倍率下限")
                .defineInRange("fridayMultiplierMin", 5.0D, 1.0D, 1000.0D);
        FRIDAY_MULTIPLIER_MAX = b.comment("疯狂星期五收购倍率上限")
                .defineInRange("fridayMultiplierMax", 10.0D, 1.0D, 1000.0D);
        b.pop();

        SPEC = b.build();
    }

    private EEConfig() {
    }

    /** 构造一个不可变的默认字符串列表。 */
    private static List<String> defaults(String... values) {
        return List.of(values);
    }

    /** 配置是否已经加载（服务端配置在服务器启动时才可用）。 */
    public static boolean isLoaded() {
        return SPEC.isLoaded();
    }

    // ------------------------------------------------------------------
    // 读取接口：统一在这里转成业务需要的类型
    // ------------------------------------------------------------------

    public static double depositFeeRate() {
        return DEPOSIT_FEE_RATE.get();
    }

    public static double withdrawFeeRate() {
        return WITHDRAW_FEE_RATE.get();
    }

    public static double dailyInterestRate() {
        return DAILY_INTEREST_RATE.get();
    }

    /** 余额达到该数值（含等于）才开始计算日利息。 */
    public static long interestMinBalance() {
        return INTEREST_MIN_BALANCE.get();
    }

    public static double loanInterestRate() {
        return LOAN_INTEREST_RATE.get();
    }

    public static long creditLimit() {
        return CREDIT_LIMIT.get();
    }

    public static int maxTransactionRecords() {
        return MAX_TRANSACTION_RECORDS.get();
    }

    public static int priceUpdateIntervalTicks() {
        return PRICE_UPDATE_INTERVAL_TICKS.get();
    }

    public static double momentumDecay() {
        return MOMENTUM_DECAY.get();
    }

    public static double meanReversion() {
        return MEAN_REVERSION.get();
    }

    public static double maxMomentum() {
        return MAX_MOMENTUM.get();
    }

    public static double priceMinFactor() {
        return PRICE_MIN_FACTOR.get();
    }

    public static double priceMaxFactor() {
        return PRICE_MAX_FACTOR.get();
    }

    public static double eventSensitivity() {
        return EVENT_SENSITIVITY.get();
    }

    public static double weatherStrength() {
        return WEATHER_STRENGTH.get();
    }

    public static double marketEventChance() {
        return MARKET_EVENT_CHANCE.get();
    }

    public static double tradeFeeRate() {
        return TRADE_FEE_RATE.get();
    }

    public static int tradeCooldownMillis() {
        return TRADE_COOLDOWN_MILLIS.get();
    }

    public static int historySize() {
        return HISTORY_SIZE.get();
    }

    public static List<? extends String> fundBasePrices() {
        return FUND_BASE_PRICES.get();
    }

    public static List<? extends String> fundVolatilities() {
        return FUND_VOLATILITIES.get();
    }

    public static List<? extends String> fundMeanReversions() {
        return FUND_MEAN_REVERSIONS.get();
    }

    public static List<? extends String> fundDailySupplies() {
        return FUND_DAILY_SUPPLIES.get();
    }

    public static List<? extends String> weatherModifiers() {
        return WEATHER_MODIFIERS.get();
    }

    public static List<? extends String> marketEvents() {
        return MARKET_EVENTS.get();
    }

    public static double oddsOddEven() {
        return ODDS_ODD_EVEN.get();
    }

    public static double oddsBigSmall() {
        return ODDS_BIG_SMALL.get();
    }

    public static double oddsLastDigit() {
        return ODDS_LAST_DIGIT.get();
    }

    public static int lotteryDailyLimit() {
        return LOTTERY_DAILY_LIMIT.get();
    }

    public static int shopInitialSlots() {
        return SHOP_INITIAL_SLOTS.get();
    }

    public static int shopDailyPurchaseLimit() {
        return SHOP_DAILY_PURCHASE_LIMIT.get();
    }

    public static int shopUpgradeCostMin() {
        return SHOP_UPGRADE_COST_MIN.get();
    }

    public static int shopUpgradeCostMax() {
        return SHOP_UPGRADE_COST_MAX.get();
    }

    public static int shopPriceMin() {
        return SHOP_PRICE_MIN.get();
    }

    public static int shopPriceMax() {
        return SHOP_PRICE_MAX.get();
    }

    public static double shopTreasureMultiplierMin() {
        return SHOP_TREASURE_MULTIPLIER_MIN.get();
    }

    public static double shopTreasureMultiplierMax() {
        return SHOP_TREASURE_MULTIPLIER_MAX.get();
    }

    public static int fridayIntervalDays() {
        return FRIDAY_INTERVAL_DAYS.get();
    }

    public static int fridayPickCount() {
        return FRIDAY_PICK_COUNT.get();
    }

    public static double fridayMultiplierMin() {
        return FRIDAY_MULTIPLIER_MIN.get();
    }

    public static double fridayMultiplierMax() {
        return FRIDAY_MULTIPLIER_MAX.get();
    }

}
