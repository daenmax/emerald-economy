package cn.daenx.mc.emeraldeconomy.market;

import cn.daenx.mc.emeraldeconomy.core.EEConstants;
import net.minecraft.network.chat.Component;

/**
 * 把 {@link NewsEntry} 还原成可翻译的文本组件。
 * <p>
 * 新闻里需要插入「基金名 / 天气名」这类也要跟随语言变化的内容，
 * 但存档只保存字符串。这里约定两个标记：
 * <ul>
 *     <li>{@code #fund:EMR_MIN} —— 替换成该基金的显示名</li>
 *     <li>{@code #weather:RAIN} —— 替换成该天气的显示名</li>
 * </ul>
 * 其它字符串一律按字面量处理（例如金额数字）。
 * <p>
 * 这个类同时被服务端（广播）和客户端（市场新闻界面）使用，因此不能引用仅客户端存在的类。
 */
public final class NewsText {

    private static final String MARKER_FUND = "#fund:";
    private static final String MARKER_WEATHER = "#weather:";

    private NewsText() {
    }

    /**
     * 把一条新闻还原成文本组件。
     * <p>
     * 新闻里存的是「语言键后缀」（例如 {@code news.weather_daily}），
     * 这里要补上 {@code emerald_economy.} 前缀才是完整键；
     * 漏了这一步界面上就会直接显示出键名。
     */
    public static Component resolve(NewsEntry entry) {
        Object[] args = new Object[entry.args().size()];
        for (int i = 0; i < args.length; i++) {
            args[i] = resolveArg(entry.args().get(i));
        }
        return Component.translatable(EEConstants.langKey(entry.langKey()), args);
    }

    private static Object resolveArg(String raw) {
        if (raw.startsWith(MARKER_FUND)) {
            FundType fund = FundType.byCode(raw.substring(MARKER_FUND.length()));
            return fund == null ? Component.literal(raw) : fund.displayName();
        }
        if (raw.startsWith(MARKER_WEATHER)) {
            WeatherKind weather = WeatherKind.byName(raw.substring(MARKER_WEATHER.length()), WeatherKind.CLEAR);
            return weather.displayName();
        }
        return Component.literal(raw);
    }

    /** 构造一个「基金名」参数。 */
    public static String fundArg(FundType fund) {
        return MARKER_FUND + fund.code();
    }

    /** 构造一个「天气名」参数。 */
    public static String weatherArg(WeatherKind weather) {
        return MARKER_WEATHER + weather.name();
    }

    /** 新闻种类对应的 emoji 前缀。 */
    public static String prefixOf(String kind) {
        return switch (kind) {
            case NewsEntry.KIND_WEATHER -> "🌤️ ";
            case NewsEntry.KIND_SETTLE -> "📊 ";
            default -> "📰 ";
        };
    }
}
