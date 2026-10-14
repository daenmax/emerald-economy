package cn.daenx.mc.emeraldeconomy.market;

import cn.daenx.mc.emeraldeconomy.core.EEConstants;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * 七支基金的定义（需求文档 3.2）。
 * <p>
 * 这里只放「不会变」的东西：代码、显示名语言键、是否综合基金、在综合指数里的权重。
 * 基础价格、波动率、均值回归系数、每日预期供给量都是可配置的，存放在 {@code EEConfig} 中。
 */
public enum FundType {

    /** 矿业基金 —— 波动大 */
    EMR_MIN(0.20D, true),
    /** 农业基金 */
    EMR_AGR(0.20D, true),
    /** 畜牧基金 */
    EMR_LIV(0.15D, true),
    /** 建筑基金 */
    EMR_BLD(0.15D, true),
    /** 水电基金 */
    EMR_HYD(0.15D, true),
    /** 狩猎基金 */
    EMR_HUN(0.15D, true),
    /** 综合基金 —— 由其它六支加权平均得出，不单独受事件直接影响 */
    EMR_CMP(0.0D, false);

    private final double compositeWeight;
    private final boolean industryFund;
    private final String langKey = EEConstants.langKey("fund." + name().toLowerCase(Locale.ROOT));

    FundType(double compositeWeight, boolean industryFund) {
        this.compositeWeight = compositeWeight;
        this.industryFund = industryFund;
    }

    /** 基金代码，例如 {@code EMR_MIN}。 */
    public String code() {
        return name();
    }

    /** 在综合指数中的权重（综合基金自身为 0）。 */
    public double compositeWeight() {
        return compositeWeight;
    }

    /** 是否是六大行业基金之一（综合基金返回 false）。 */
    public boolean isIndustryFund() {
        return industryFund;
    }

    /** 是否是综合基金。 */
    public boolean isComposite() {
        return this == EMR_CMP;
    }

    /** 显示名翻译键。 */
    public String langKey() {
        return langKey;
    }

    /** 显示名。 */
    public Component displayName() {
        return Component.translatable(langKey);
    }

    /**
     * 该基金是否会累积世界事件供给量。
     * <p>
     * 六支行业基金分别对应不同的世界事件；综合基金只接收「与村民交易」这一项微调。
     */
    public boolean tracksSupply() {
        return true;
    }

    /** 六大行业基金列表（不含综合基金）。 */
    public static FundType[] industryFunds() {
        return new FundType[]{EMR_MIN, EMR_AGR, EMR_LIV, EMR_BLD, EMR_HYD, EMR_HUN};
    }

    /** 按代码解析，失败返回 null。 */
    public static FundType byCode(String code) {
        if (code == null) {
            return null;
        }
        try {
            return valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** 按序号取，越界返回 EMR_CMP。 */
    public static FundType byIndex(int index) {
        FundType[] values = values();
        if (index < 0 || index >= values.length) {
            return EMR_CMP;
        }
        return values[index];
    }
}
