package cn.daenx.mc.emeraldeconomy.market;

import cn.daenx.mc.emeraldeconomy.config.EEConfig;
import cn.daenx.mc.emeraldeconomy.core.Params;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 基金市场数据的持久化容器。
 * <p>
 * 基金数据所有玩家共享（需求文档 3.1），持仓数据则挂在每个玩家的账户里。
 */
public class MarketBook {

    private static final String KEY_FUNDS = "funds";
    private static final String KEY_SUPPLY = "supply";
    private static final String KEY_NEWS = "news";
    private static final String KEY_WEATHER = "weather";
    private static final String KEY_WEATHER_DAY = "weatherDay";

    /** 新闻列表最多保留多少条。 */
    public static final int MAX_NEWS = 20;

    private final Map<FundType, FundState> funds = new EnumMap<>(FundType.class);
    private final Map<FundType, Long> dailySupply = new EnumMap<>(FundType.class);
    /** 最近的新闻，新的在前。 */
    private final List<NewsEntry> news = new ArrayList<>();

    private WeatherKind lastWeather = WeatherKind.CLEAR;
    private long weatherDay = -1L;
    /** 市场数据是否已经按配置初始化过。 */
    private boolean initialized = false;

    public MarketBook() {
        for (FundType type : FundType.values()) {
            funds.put(type, new FundState(type, 100, 64));
            dailySupply.put(type, 0L);
        }
    }

    /**
     * 按配置文件初始化基础价格等参数。
     * <p>
     * 只在第一次需要时执行，之后以存档中的数据为准，避免改配置把价格重置。
     */
    public void ensureInitialized() {
        if (initialized) {
            return;
        }
        int historySize = EEConfig.isLoaded() ? EEConfig.historySize() : 64;
        Map<String, Long> basePrices = Params.parseRoundedLongMap(EEConfig.fundBasePrices());
        for (FundType type : FundType.values()) {
            FundState state = funds.get(type);
            int base = (int) Math.max(1L, Params.getLong(basePrices, type.code(), state.basePrice()));
            state.setBasePrice(base);
            state.setHistorySize(historySize);
            if (state.price() <= 1) {
                state.setPrice(base);
            }
            if (state.previousClose() <= 1) {
                state.setPreviousClose(base);
            }
        }
        initialized = true;
    }

    public Map<FundType, FundState> funds() {
        return funds;
    }

    public FundState fund(FundType type) {
        return funds.get(type);
    }

    public Map<FundType, Long> dailySupply() {
        return dailySupply;
    }

    /** 累加某行业的当日供给量。 */
    public void addSupply(FundType type, long amount) {
        if (!type.tracksSupply() || amount <= 0L) {
            return;
        }
        dailySupply.merge(type, amount, Long::sum);
    }

    public long supply(FundType type) {
        return dailySupply.getOrDefault(type, 0L);
    }

    /** 清空当日供给量。 */
    public void resetSupply() {
        dailySupply.replaceAll((k, v) -> 0L);
    }

    /** 追加一条新闻。 */
    public void addNews(NewsEntry entry) {
        news.add(0, entry);
        while (news.size() > MAX_NEWS) {
            news.remove(news.size() - 1);
        }
    }

    public List<NewsEntry> news() {
        return news;
    }

    public WeatherKind lastWeather() {
        return lastWeather;
    }

    public void setLastWeather(WeatherKind lastWeather) {
        this.lastWeather = lastWeather;
    }

    public long weatherDay() {
        return weatherDay;
    }

    public void setWeatherDay(long weatherDay) {
        this.weatherDay = weatherDay;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        CompoundTag fundsTag = new CompoundTag();
        funds.forEach((type, state) -> fundsTag.put(type.code(), state.save()));
        tag.put(KEY_FUNDS, fundsTag);

        CompoundTag supplyTag = new CompoundTag();
        dailySupply.forEach((type, value) -> supplyTag.putLong(type.code(), value));
        tag.put(KEY_SUPPLY, supplyTag);

        ListTag newsTag = new ListTag();
        news.forEach(entry -> newsTag.add(entry.save()));
        tag.put(KEY_NEWS, newsTag);

        tag.putString(KEY_WEATHER, lastWeather.name());
        tag.putLong(KEY_WEATHER_DAY, weatherDay);
        return tag;
    }

    public void load(CompoundTag tag) {
        CompoundTag fundsTag = tag.getCompound(KEY_FUNDS);
        for (FundType type : FundType.values()) {
            if (fundsTag.contains(type.code())) {
                funds.get(type).load(fundsTag.getCompound(type.code()), 100);
            }
        }
        CompoundTag supplyTag = tag.getCompound(KEY_SUPPLY);
        for (FundType type : FundType.values()) {
            dailySupply.put(type, supplyTag.getLong(type.code()));
        }
        news.clear();
        ListTag newsTag = tag.getList(KEY_NEWS, Tag.TAG_COMPOUND);
        for (int i = 0; i < newsTag.size(); i++) {
            news.add(NewsEntry.load(newsTag.getCompound(i)));
        }
        lastWeather = WeatherKind.byName(tag.getString(KEY_WEATHER), WeatherKind.CLEAR);
        weatherDay = tag.contains(KEY_WEATHER_DAY) ? tag.getLong(KEY_WEATHER_DAY) : -1L;
        initialized = true;
    }
}
