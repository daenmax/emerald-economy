package cn.daenx.mc.emeraldeconomy.lottery;

import cn.daenx.mc.emeraldeconomy.config.EEConfig;
import cn.daenx.mc.emeraldeconomy.core.EEConstants;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * 抽奖场次（需求文档 4.2）。
 * <p>
 * 三种场次各自独立开奖，开奖结果统一是 0-9 的随机整数。
 * <ul>
 *     <li>{@link #ODD_EVEN} 猜单双：选项 0=单、1=双</li>
 *     <li>{@link #BIG_SMALL} 猜大小：选项 0=小(0-4)、1=大(5-9)</li>
 *     <li>{@link #LAST_DIGIT} 猜尾数：选项 0-9，与开奖数字相同即中奖</li>
 * </ul>
 */
public enum LotteryType {

    /** 猜单双。 */
    ODD_EVEN(2),
    /** 猜大小。 */
    BIG_SMALL(2),
    /** 猜尾数。 */
    LAST_DIGIT(10);

    /** 开奖数字的范围：0 到 {@code RESULT_MAX}（含）。 */
    public static final int RESULT_MAX = 9;
    /** 单数选项的索引。 */
    public static final int OPTION_ODD = 0;
    /** 双数选项的索引。 */
    public static final int OPTION_EVEN = 1;
    /** 小数选项的索引。 */
    public static final int OPTION_SMALL = 0;
    /** 大数选项的索引。 */
    public static final int OPTION_BIG = 1;
    /** 猜大小的分界：0-4 为小，5-9 为大。 */
    public static final int BIG_SMALL_BOUNDARY = 5;

    private final int optionCount;
    private final String langKey = EEConstants.langKey("lottery." + name().toLowerCase(Locale.ROOT));

    LotteryType(int optionCount) {
        this.optionCount = optionCount;
    }

    /** 可选项数量。 */
    public int optionCount() {
        return optionCount;
    }

    /** 场次名语言键。 */
    public String langKey() {
        return langKey;
    }

    /** 场次名。 */
    public Component displayName() {
        return Component.translatable(langKey);
    }

    /** 单个选项的语言键。 */
    public String optionLangKey(int option) {
        return EEConstants.langKey("lottery." + name().toLowerCase(Locale.ROOT) + ".option." + option);
    }

    /** 单个选项的显示名。 */
    public Component optionName(int option) {
        return Component.translatable(optionLangKey(option));
    }

    /** 该场次的赔率（来自配置）。 */
    public double odds() {
        if (!EEConfig.isLoaded()) {
            return 1.0D;
        }
        return switch (this) {
            case ODD_EVEN -> EEConfig.oddsOddEven();
            case BIG_SMALL -> EEConfig.oddsBigSmall();
            case LAST_DIGIT -> EEConfig.oddsLastDigit();
        };
    }

    /**
     * 判断一次下注是否中奖。
     *
     * @param choice 玩家选择的选项
     * @param result 开奖结果（0-9）
     */
    public boolean isWinner(int choice, int result) {
        return switch (this) {
            case ODD_EVEN -> choice == OPTION_ODD ? result % 2 == 1 : result % 2 == 0;
            case BIG_SMALL -> choice == OPTION_SMALL ? result < BIG_SMALL_BOUNDARY : result >= BIG_SMALL_BOUNDARY;
            case LAST_DIGIT -> choice == result;
        };
    }

    /** 校验收到的选项是否合法。 */
    public boolean isValidChoice(int choice) {
        return choice >= 0 && choice < optionCount;
    }

    public static LotteryType byIndex(int index) {
        LotteryType[] values = values();
        if (index < 0 || index >= values.length) {
            return ODD_EVEN;
        }
        return values[index];
    }
}
