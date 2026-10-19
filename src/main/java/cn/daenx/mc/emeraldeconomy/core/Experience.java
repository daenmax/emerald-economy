package cn.daenx.mc.emeraldeconomy.core;

import net.minecraft.world.entity.player.Player;

/**
 * 经验值与等级之间的换算（Minecraft 1.21.1 的官方升级曲线）。
 * <p>
 * ATM 的「经验存取」按<b>等级</b>输入：输入 1 级，就把「从当前等级掉到下一级」所需的经验
 * 存进 ATM。为了做到「存完正好停在目标等级、经验条为 0」，这里需要精确知道
 * 每一级起点对应的累计经验值，而不是依赖 {@code totalExperience} 字段
 * （它只记录玩家一共获得过多少经验，掉级时并不会同步减少）。
 * <p>
 * 1.21.1 的升级曲线分三段：
 * <ul>
 *     <li>0 ~ 14 级：升下一级需要 {@code 7 + 2 × 等级} 点</li>
 *     <li>15 ~ 29 级：升下一级需要 {@code 37 + 5 × (等级 - 15)} 点</li>
 *     <li>30 级及以上：升下一级需要 {@code 112 + 9 × (等级 - 30)} 点</li>
 * </ul>
 * 累计经验就是把这些「升下一级所需」逐级加起来，本类用闭式公式计算，避免循环。
 */
public final class Experience {

    /** 第一段与第二段的分界等级。 */
    private static final int TIER_2_START = 15;
    /** 第二段与第三段的分界等级。 */
    private static final int TIER_3_START = 30;
    /** 第一段：升下一级的基础经验与每级增量。 */
    private static final int TIER_1_BASE = 7;
    private static final int TIER_1_STEP = 2;
    /** 第二段：升下一级的基础经验与每级增量。 */
    private static final int TIER_2_BASE = 37;
    private static final int TIER_2_STEP = 5;
    /** 第三段：升下一级的基础经验与每级增量。 */
    private static final int TIER_3_BASE = 112;
    private static final int TIER_3_STEP = 9;
    /** 计算「累计经验 → 等级」时的等级搜索上限（远高于正常玩法能达到的等级）。 */
    private static final int MAX_SEARCH_LEVEL = 1_000_000;

    private Experience() {
    }

    /** 从 {@code level} 升到下一级需要多少经验点。 */
    public static int xpToNextLevel(int level) {
        int value = Math.max(0, level);
        if (value >= TIER_3_START) {
            return TIER_3_BASE + (value - TIER_3_START) * TIER_3_STEP;
        }
        if (value >= TIER_2_START) {
            return TIER_2_BASE + (value - TIER_2_START) * TIER_2_STEP;
        }
        return TIER_1_BASE + value * TIER_1_STEP;
    }

    /**
     * 从 0 级升到 {@code level} 级一共需要多少经验点（也就是「等级刚好为 level、经验条为 0」时的累计经验）。
     */
    public static long totalXpForLevel(int level) {
        int value = Math.max(0, level);
        if (value <= TIER_2_START) {
            // 0 ~ 15：sum(7 + 2i) = 7n + n(n-1) = n² + 6n
            return (long) value * value + 6L * value;
        }
        // 0 ~ 15 级的累计值：15² + 6×15 = 315
        long total = (long) TIER_2_START * TIER_2_START + 6L * TIER_2_START;
        // 15 ~ min(level, 30) 级：每级 37 + 5k
        int tier2Count = Math.min(value, TIER_3_START) - TIER_2_START;
        total += (long) TIER_2_BASE * tier2Count
                + (long) TIER_2_STEP * tier2Count * (tier2Count - 1) / 2L;
        if (value > TIER_3_START) {
            // 30 级及以上：每级 112 + 9k
            int tier3Count = value - TIER_3_START;
            total += (long) TIER_3_BASE * tier3Count
                    + (long) TIER_3_STEP * tier3Count * (tier3Count - 1) / 2L;
        }
        return total;
    }

    /**
     * 玩家当前拥有的累计经验（等级起点 + 经验条里的进度）。
     * <p>
     * 不用 {@code Player#totalExperience}：那个字段记录的是「一共获得过多少经验」，
     * 掉级时并不会减少，不能用来判断「现在还剩多少」。
     */
    public static long totalExperience(Player player) {
        if (player == null) {
            return 0L;
        }
        int level = Math.max(0, player.experienceLevel);
        long base = totalXpForLevel(level);
        int next = Math.max(1, xpToNextLevel(level));
        float progress = Math.max(0.0F, Math.min(1.0F, player.experienceProgress));
        return base + Math.round(progress * next);
    }

    /** 累计经验对应的等级（经验条进度不计入，结果满足 {@code totalXpForLevel(level) <= total}）。 */
    public static int levelForTotal(long total) {
        long value = Math.max(0L, total);
        int low = 0;
        int high = 1;
        while (high < MAX_SEARCH_LEVEL && totalXpForLevel(high) <= value) {
            high = Math.min(MAX_SEARCH_LEVEL, high * 2);
        }
        while (low + 1 < high) {
            int mid = (low + high) >>> 1;
            if (totalXpForLevel(mid) <= value) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return totalXpForLevel(high) <= value ? high : low;
    }

    /** 累计经验对应的经验条进度（0 ~ 1，不含 1）。 */
    public static float progressForTotal(long total) {
        long value = Math.max(0L, total);
        int level = levelForTotal(value);
        long base = totalXpForLevel(level);
        int next = Math.max(1, xpToNextLevel(level));
        return (float) Math.max(0.0D, Math.min(1.0D - 1.0E-4D,
                (double) (value - base) / (double) next));
    }
}
