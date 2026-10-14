package cn.daenx.mc.emeraldeconomy.market;

import cn.daenx.mc.emeraldeconomy.config.EEConfig;
import cn.daenx.mc.emeraldeconomy.core.Amounts;
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

            // 步骤 8：约束价格到 [基础价 × 0.3, 基础价 × 3.0]
            newPrice = clampPriceToBounds(newPrice, base);

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
        price = clampPriceToBounds(price, composite.basePrice());
        composite.setPrice(price);
        composite.pushHistory(price);
    }

    /** 把价格限制在基础价格的配置倍数范围内。 */
    private static int clampPriceToBounds(int price, int base) {
        int min = Amounts.roundPrice(base * EEConfig.priceMinFactor());
        int max = Amounts.roundPrice(base * EEConfig.priceMaxFactor());
        if (min > max) {
            min = max;
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

        // 步骤 6：记录每支基金当天的收盘价
        for (FundType type : FundType.values()) {
            FundState state = market.fund(type);
            state.setPreviousClose(state.price());
        }

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
