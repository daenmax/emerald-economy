package cn.daenx.mc.emeraldeconomy.core;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 全局常量。
 * <p>
 * 模组内所有资源路径、语言键前缀、时间换算都集中在这里，避免硬编码字符串散落各处。
 */
public final class EEConstants {

    private EEConstants() {
    }

    /** 模组 ID，必须与 {@code neoforge.mods.toml} 以及 {@code @Mod} 注解一致。 */
    public static final String MOD_ID = "emerald_economy";

    /** 模组显示名。 */
    public static final String MOD_NAME = "Emerald Economy";

    /** 一个游戏日包含的游戏刻数（20 tick/秒 × 1200 秒）。 */
    public static final int TICKS_PER_DAY = 24000;

    /** 一秒包含的游戏刻数。 */
    public static final int TICKS_PER_SECOND = 20;

    /** 一分钟包含的游戏刻数。 */
    public static final int TICKS_PER_MINUTE = TICKS_PER_SECOND * 60;

    /** 金额、份额、数量的最小值：1 绿宝石。 */
    public static final long MIN_AMOUNT = 1L;

    /** 价格的最小值：1 绿宝石。 */
    public static final int MIN_PRICE = 1;

    /**
     * 两次每日结算之间的最小游戏刻间隔。
     * <p>
     * 玩家频繁使用 {@code /time set} 时 dayTime 会来回跳，这里加一个下限避免重复结算。
     */
    public static final long MIN_TICKS_BETWEEN_SETTLEMENTS = 1200L;

    /** 默认的价格历史记录长度（需求文档 3.5.1：走势图显示最近 20 个价格点）。 */
    public static final int DEFAULT_HISTORY_SIZE = 20;

    /** 生成一个本模组的命名空间资源路径。 */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    /** 生成本模组的翻译键（{@code emerald_economy.xxx}）。 */
    public static String langKey(String path) {
        return MOD_ID + "." + path;
    }

    /**
     * 生成<b>原版格式</b>的方块 / 物品翻译键：{@code block.<命名空间>.<路径>}。
     * <p>
     * 这个键不能自己发挥：{@code Block.getDescriptionId()} 与
     * {@code Item.getDescriptionId()} 都是「类型.命名空间.路径」这么拼的，
     * 写成 {@code emerald_economy.block.atm} 游戏查不到，界面就会退回显示注册名英文原文。
     * 方块物品的名字也走这个键。
     */
    public static String blockDescriptionKey(String path) {
        return "block." + MOD_ID + "." + path;
    }

    /** 生成本模组的可翻译文本组件。 */
    public static Component tr(String path, Object... args) {
        return Component.translatable(langKey(path), args);
    }
}
