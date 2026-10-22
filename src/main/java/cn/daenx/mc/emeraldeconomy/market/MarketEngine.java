package cn.daenx.mc.emeraldeconomy.market;

import cn.daenx.mc.emeraldeconomy.config.EEConfig;
import cn.daenx.mc.emeraldeconomy.core.Amounts;
import cn.daenx.mc.emeraldeconomy.core.EEConstants;
import cn.daenx.mc.emeraldeconomy.core.Params;
import cn.daenx.mc.emeraldeconomy.data.EconomyData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 基金价格引擎（需求文档 3.3）。
 * <p>
 * 全部在服务端运行，客户端只负责展示。包含三块：
 * <ul>
 *     <li>{@link #updatePrices} 每游戏内 5 分钟执行一次的市场模拟（动量 + 均值回归 + 随机噪声 + 边界约束）</li>
 *     <li>{@link #settleDay} 每日结算：世界事件供给修正、天气修正、随机市场新闻、记录收盘价</li>
 *     <li>{@link #recomputeComposite} 综合基金价格 = 其它六支的加权平均</li>
 * </ul>
 */
public final class MarketEngine {

    /** 供给比的上下限，避免 {@code ln(0)} 之类的极端值把动量顶爆。 */
    private static final double MIN_SUPPLY_RATIO = 0.05D;
    private static final double MAX_SUPPLY_RATIO = 20.0D;

    private MarketEngine() {
    }

    /** 市场新闻事件的配置项。 */
    private record MarketEventSpec(String scope, double delta, String langKey) {
    }

    // ------------------------------------------------------------------
    // 1. 市场模拟（每 5 分钟一次）
    // ------------------------------------------------------------------

    /**
     * 执行一次基础价格波动（需求文档 3.3.1 的 8 个步骤）。
     */
    public static void updatePrices(EconomyData data, RandomSource random) {
        MarketBook market = data.market();
        market.ensureInitialized();

        double decay = EEConfig.momentumDecay();
        double maxMomentum = EEConfig.maxMomentum();
        Map<String, Double> volatilities = Params.parseDoubleMap(EEConfig.fundVolatilities());
        Map<String, Double> meanReversions = Params.parseDoubleMap(EEConfig.fundMeanReversions());

        for (FundType type : FundType.industryFunds()) {
            FundState state = market.fund(type);
            int base = state.basePrice();

            // 步骤 1：动量衰减
            double momentum = state.momentum() * decay;

            // 步骤 2：价格偏离度
            double deviation = (state.price() - base) / (double) base;

            // 步骤 3：均值回归力
            double reversion = -deviation * Params.getDouble(meanReversions, type.code(), EEConfig.meanReversion());

            // 步骤 4：随机噪声
            double volatility = Params.getDouble(volatilities, type.code(), 0.03D);
            double noise = (random.nextDouble() - 0.5D) * 2.0D * volatility;

            // 步骤 5、6：更新并约束动量
            momentum = clamp(momentum + reversion + noise, -maxMomentum, maxMomentum);

            // 步骤 7：计算新价格
            int newPrice = Amounts.roundPrice(state.price() * (1.0D + momentum));

            // 步骤 8：约束价格到 [基础价 × 0.3, 基础价 × 3.0]，并限制当日涨跌幅（涨停 / 跌停）
            newPrice = clampPriceToBounds(newPrice, base, state.previousClose());

            state.setMomentum(momentum);
            state.setPrice(newPrice);
            state.pushHistory(newPrice);
        }

        recomputeComposite(data);
        data.setDirty();
    }

    /**
     * 综合基金的价格由其它六支基金的加权平均计算得出（需求文档 3.2）。
     * <p>
     * 因为六支基金的基础价格不同，这里先算「相对基础价的加权指数」，
     * 再乘回综合基金自己的基础价格，这样综合指数才具备可比的量纲。
     * <p>
     * 综合基金自身也会累积一点情绪动量（天气表与「绿宝石贬值」这类新闻会给它加动量），
     * 但主项始终是六支基金的加权平均，符合「不单独受事件直接影响」的描述。
     */
    public static void recomputeComposite(EconomyData data) {
        MarketBook market = data.market();
        FundState composite = market.fund(FundType.EMR_CMP);

        double index = 0.0D;
        for (FundType type : FundType.industryFunds()) {
            FundState state = market.fund(type);
            index += type.compositeWeight() * ((double) state.price() / (double) state.basePrice());
        }

        // 综合基金的情绪动量按同样的衰减规则缓慢释放
        double mood = composite.momentum() * EEConfig.momentumDecay();
        composite.setMomentum(mood);

        int price = Amounts.roundPrice(composite.basePrice() * index * (1.0D + mood));
        price = clampPriceToBounds(price, composite.basePrice(), composite.previousClose());
        composite.setPrice(price);
        composite.pushHistory(price);
    }

    /**
     * 把价格限制在「基础价格的配置倍数范围」与「当日涨跌停范围」的交集内。
     * <p>
     * 涨跌停以「上一次每日结算的收盘价」为基准：当天无论更新多少次，
     * 价格都不会超过 {@code 收盘价 × (1 ± dailyLimitRate)}，也就是常见的涨停 / 跌停。
     *
     * @param previousClose 上一日收盘价，用于计算当日涨跌停价
     */
    private static int clampPriceToBounds(int price, int base, int previousClose) {
        int hardMin = Amounts.roundPrice(base * EEConfig.priceMinFactor());
        int hardMax = Amounts.roundPrice(base * EEConfig.priceMaxFactor());
        if (hardMin > hardMax) {
            hardMin = hardMax;
        }

        int min = hardMin;
        int max = hardMax;
        double limit = EEConfig.dailyLimitRate();
        if (previousClose > 0) {
            // 涨停价向下取整、跌停价向上取整，保证不会因为四舍五入越过限制。
            // 减去 / 加上一个极小量是为了抵消浮点误差：100 × (1 - 0.10) 在 double 下是
            // 90.000000000000002，直接向上取整会得到 91，跌停价就被悄悄抬高了。
            int dailyMin = (int) Math.ceil(previousClose * (1.0D - limit) - 1.0E-9D);
            int dailyMax = (int) Math.floor(previousClose * (1.0D + limit) + 1.0E-9D);
            min = Math.max(min, Math.max(EEConstants.MIN_PRICE, dailyMin));
            max = Math.min(max, Math.max(EEConstants.MIN_PRICE, dailyMax));
        }
        if (min > max) {
            // 配置的硬性区间与当日涨跌停区间完全不重叠（例如中途改了价格上下界配置）：
            // 把上一日收盘价夹进硬性区间当作唯一合法价格，避免价格卡死在矛盾区间里
            int anchor = (int) Amounts.clamp(previousClose, hardMin, hardMax);
            min = Math.max(EEConstants.MIN_PRICE, anchor);
            max = min;
        }
        return (int) Amounts.clamp(price, min, max);
    }

    // ------------------------------------------------------------------
    // 2. 每日结算
    // ------------------------------------------------------------------

    /**
     * 每日结算（需求文档 3.3.5 + 七.1）。
     *
     * @param server  服务端实例（用于广播）
     * @param data    模组数据
     * @param endDay  刚刚结束的游戏日
     * @param weather 当天天气
     * @param random  随机源
     */
    public static void settleDay(MinecraftServer server, EconomyData data, long endDay,
                                 WeatherKind weather, RandomSource random) {
        MarketBook market = data.market();
        market.ensureInitialized();

        // 步骤 1：统计当日供给量，按公式计算事件修正并累加到动量
        applySupplyCorrections(data);

        // 步骤 2、3：读取当天天气，把天气修正累加到动量
        applyWeatherCorrections(data, weather);

        // 步骤 3.5：叠加当天的随机情绪修正。
        // 这一条是为了「即便今天谁都没做任何事，行情也要有随机性」：
        // 世界供给（挖矿/收割/杀怪……）与天气都是「有方向」的修正，
        // 长期下来会把行情拧成固定的涨跌方向，玩家只要买涨的那几支、什么都不干就能稳赚。
        // 每天给每支基金加一个正负对称的随机动量之后，任何一支基金都不可能长期单边走。
        applyRandomDrift(data, random);

        // 步骤 4：清空当日供给量
        market.resetSupply();

        // 记录当天天气，供「市场新闻」界面查看
        market.setLastWeather(weather);
        market.setWeatherDay(endDay);
        pushNews(server, data, new NewsEntry(
                "news.weather_daily",
                List.of(NewsText.weatherArg(weather)),
                endDay,
                NewsEntry.KIND_WEATHER));

        // 步骤 5：有概率触发随机市场事件并广播
        maybeFireMarketEvent(server, data, endDay, random);

        // 步骤 6：记录每支基金当天的收盘价（作为新一天涨跌幅的基准）
        for (FundType type : FundType.values()) {
            FundState state = market.fund(type);
            state.setPreviousClose(state.price());
        }

        // 步骤 7：立刻按新一天的动量走一次行情，也就是「开盘价」。
        // 如果只记录收盘价、不立刻更新价格，那么从结算完成到下一次
        // 5 分钟价格更新之间，七支基金的涨跌幅会全部显示为 0.00% 且没有颜色，
        // 走势图也会被画成灰色，看起来像是功能坏了。
        updatePrices(data, random);

        data.setDirty();
    }

    /** 供给比 → 事件修正 → 动量。 */
    private static void applySupplyCorrections(EconomyData data) {
        MarketBook market = data.market();
        double sensitivity = EEConfig.eventSensitivity();
        Map<String, Long> expected = Params.parseLongMap(EEConfig.fundDailySupplies());

        for (FundType type : FundType.values()) {
            long expectedSupply = Params.getLong(expected, type.code(), 0L);
            if (expectedSupply <= 0L) {
                continue;
            }
            long supply = market.supply(type);
            if (supply <= 0L) {
                // 当天没有任何相关行为，视为没有信号，不做修正
                continue;
            }
            double ratio = (double) supply / (double) expectedSupply;
            ratio = Math.max(MIN_SUPPLY_RATIO, Math.min(MAX_SUPPLY_RATIO, ratio));
            double correction = -Math.log(ratio) * sensitivity;
            market.fund(type).addMomentum(correction);
        }
    }

    /** 天气 → 动量修正。 */
    private static void applyWeatherCorrections(EconomyData data, WeatherKind weather) {
        MarketBook market = data.market();
        double strength = EEConfig.weatherStrength();
        Map<String, Double> modifiers = Params.parseDoubleMap(EEConfig.weatherModifiers());
        for (FundType type : FundType.values()) {
            double value = Params.getDouble(modifiers, weather.modifierKey(type), 0.0D);
            if (value == 0.0D) {
                continue;
            }
            market.fund(type).addMomentum(value * strength);
        }
    }

    /**
     * 每日随机情绪修正（本轮新增）。
     * <p>
     * 给七支基金各叠加一个 {@code ±dailyRandomDrift} 之间的随机动量，正负完全对称：
     * 这样一来「今天什么都没发生」的日子行情同样会变，而且没有任何一支基金能长期单边走
     * （否则玩家只要买入一直涨的那几支、什么都不干就能稳赚）。
     * <p>
     * 综合基金在这里同样会被随机化——它的动量会按同样的衰减规则作用到自身价格上，
     * 用户看到的「综合基金只会跟着涨」的现象也就消失了。
     */
    private static void applyRandomDrift(EconomyData data, RandomSource random) {
        double drift = EEConfig.dailyRandomDrift();
        if (drift <= 0.0D) {
            return;
        }
        MarketBook market = data.market();
        for (FundType type : FundType.values()) {
            double value = (random.nextDouble() * 2.0D - 1.0D) * drift;
            market.fund(type).addMomentum(value);
        }
    }

    /** 随机市场新闻事件（需求文档 3.3.4）。 */
    private static void maybeFireMarketEvent(MinecraftServer server, EconomyData data, long day, RandomSource random) {
        List<MarketEventSpec> events = parseMarketEvents();
        if (events.isEmpty()) {
            return;
        }
        if (random.nextDouble() >= EEConfig.marketEventChance()) {
            return;
        }
        MarketEventSpec spec = events.get(random.nextInt(events.size()));
        MarketBook market = data.market();

        List<String> args = new ArrayList<>();
        if ("ALL".equalsIgnoreCase(spec.scope())) {
            for (FundType type : FundType.values()) {
                market.fund(type).addMomentum(spec.delta());
            }
            args.add(formatPercent(spec.delta()));
        } else {
            FundType target = FundType.byCode(spec.scope());
            if (target == null) {
                return;
            }
            market.fund(target).addMomentum(spec.delta());
            args.add(NewsText.fundArg(target));
            args.add(formatPercent(spec.delta()));
        }

        NewsEntry entry = new NewsEntry(spec.langKey(), args, day, NewsEntry.KIND_EVENT);
        pushNews(server, data, entry);
    }

    /** 记录新闻并广播到聊天栏。 */
    private static void pushNews(MinecraftServer server, EconomyData data, NewsEntry entry) {
        data.market().addNews(entry);
        if (server == null) {
            return;
        }
        Component message = Component.literal(NewsText.prefixOf(entry.kind())).append(NewsText.resolve(entry));
        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    /** 解析配置中的市场事件表，格式 {@code 作用范围|动量修正|语言键}。 */
    private static List<MarketEventSpec> parseMarketEvents() {
        List<MarketEventSpec> out = new ArrayList<>();
        for (String raw : EEConfig.marketEvents()) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\|");
            if (parts.length < 3) {
                continue;
            }
            try {
                out.add(new MarketEventSpec(parts[0].trim(), Double.parseDouble(parts[1].trim()), parts[2].trim()));
            } catch (NumberFormatException ignored) {
                // 配置写错时跳过该条
            }
        }
        return out;
    }

    /** 把动量数值格式化成百分比，用于新闻文案。 */
    private static String formatPercent(double ratio) {
        return String.format("%.1f%%", ratio * 100.0D);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** 全服广播一条市场新闻（供其它模块复用）。 */
    public static void broadcastNews(MinecraftServer server, NewsEntry entry) {
        Component message = Component.literal(NewsText.prefixOf(entry.kind())).append(NewsText.resolve(entry));
        server.getPlayerList().broadcastSystemMessage(message, false);
    }
}
