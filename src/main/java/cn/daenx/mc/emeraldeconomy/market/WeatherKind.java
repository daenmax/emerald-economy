package cn.daenx.mc.emeraldeconomy.market;

import cn.daenx.mc.emeraldeconomy.core.EEConstants;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * 天气类型（需求文档 3.3.3）。
 * <p>
 * 天气是独立于玩家行为的持续修正源，在每日结算时对相关基金施加额外动量修正。
 */
public enum WeatherKind {

    /** 晴天 */
    CLEAR,
    /** 雨天 */
    RAIN,
    /** 雷暴 */
    THUNDER;

    private final String langKey = EEConstants.langKey("weather." + name().toLowerCase(Locale.ROOT));

    public String langKey() {
        return langKey;
    }

    public Component displayName() {
        return Component.translatable(langKey);
    }

    /** 用于天气修正表的键，例如 {@code RAIN:EMR_AGR}。 */
    public String modifierKey(FundType fund) {
        return name() + ":" + fund.code();
    }

    public static WeatherKind byName(String name, WeatherKind fallback) {
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
