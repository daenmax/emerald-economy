package cn.daenx.mc.emeraldeconomy.market;

import cn.daenx.mc.emeraldeconomy.data.EconomyData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 世界事件 → 当日供给量 的累加器（需求文档 3.3.2）。
 * <p>
 * 事件回调里只做最简单的事：加一个计数。真正的价格修正放到每日结算时统一计算，
 * 避免在方块破坏、实体死亡这种高频回调里做复杂运算。
 */
public final class SupplyTracker {

    /** 每次事件累加的基础供给量。 */
    private static final long UNIT = 1L;

    /** 玩家上次记录到的「与村民交易」统计值，用于计算增量。 */
    private static final Map<UUID, Integer> LAST_TRADE_STAT = new HashMap<>();

    private SupplyTracker() {
    }

    /** 服务器停止时清理缓存。 */
    public static void clearCache() {
        LAST_TRADE_STAT.clear();
    }

    /**
     * 方块被破坏。
     * <p>
     * 收获成熟作物算农业；其它方块按标签归入矿业 / 水电 / 建筑。
     */
    public static void onBlockBroken(EconomyData data, BlockPos pos, BlockState state) {
        FundType target = classifyBlock(state);
        if (target != null) {
            data.market().addSupply(target, UNIT);
            data.setDirty();
        }
    }

    /** 把方块归类到对应行业的基金；不属于任何行业时返回 null。 */
    public static FundType classifyBlock(BlockState state) {
        // 成熟作物：原版年龄制作物统一继承 CropBlock
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) {
            return crop.isMaxAge(state) ? FundType.EMR_AGR : null;
        }
        if (state.is(SupplyTags.CROPS)) {
            return FundType.EMR_AGR;
        }
        if (state.is(SupplyTags.HYDRO)) {
            return FundType.EMR_HYD;
        }
        if (state.is(SupplyTags.MINING)) {
            return FundType.EMR_MIN;
        }
        if (state.is(SupplyTags.BUILDING)) {
            return FundType.EMR_BLD;
        }
        return null;
    }

    /** 实体被击杀：被动动物算畜牧，怪物与水生生物算狩猎。 */
    public static void onEntityKilled(EconomyData data, LivingEntity victim, Entity killer) {
        // 玩家自杀或环境死亡不计入
        if (killer == null || !(victim instanceof LivingEntity)) {
            return;
        }
        FundType target = null;
        if (victim instanceof Animal) {
            target = FundType.EMR_LIV;
        } else if (victim instanceof Monster || victim instanceof WaterAnimal) {
            target = FundType.EMR_HUN;
        }
        if (target == null) {
            // 标签兜底：允许整合包把自定义生物加进去
            var type = victim.getType();
            if (type.is(SupplyTags.LIVESTOCK)) {
                target = FundType.EMR_LIV;
            } else if (type.is(SupplyTags.HUNTING)) {
                target = FundType.EMR_HUN;
            }
        }
        if (target != null) {
            data.market().addSupply(target, UNIT);
            data.setDirty();
        }
    }

    /** 放置水或熔岩方块 → 水电基金（部分放置途径会走这里）。 */
    public static void onBlockPlaced(EconomyData data, BlockState placed) {
        if (placed.is(SupplyTags.HYDRO)) {
            data.market().addSupply(FundType.EMR_HYD, UNIT);
            data.setDirty();
        }
    }

    /**
     * 玩家使用物品 → 水电基金。
     * <p>
     * 主要用于统计「倒水 / 倒熔岩」：桶的放置不会触发方块放置事件，
     * 只能通过物品使用事件来捕获，具体哪些物品算数由
     * {@link SupplyTags#HYDRO_ITEMS} 标签决定。
     */
    public static void onItemUsed(EconomyData data, net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty() || !stack.is(SupplyTags.HYDRO_ITEMS)) {
            return;
        }
        data.market().addSupply(FundType.EMR_HYD, UNIT);
        data.setDirty();
    }

    /**
     * 与村民交易 → 综合基金微调（需求文档 3.3.2）。
     * <p>
     * NeoForge 21.1 没有「交易完成」事件，这里改用原版统计数据
     * {@code minecraft:traded_with_villager} 的增量来统计，既不需要 Mixin，
     * 也能覆盖所有交易途径。每秒钟轮询一次，开销可以忽略。
     */
    public static void pollVillagerTrades(EconomyData data, MinecraftServer server) {
        var stat = Stats.CUSTOM.get(Stats.TRADED_WITH_VILLAGER);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int current = player.getStats().getValue(stat);
            Integer last = LAST_TRADE_STAT.put(player.getUUID(), current);
            if (last == null) {
                // 第一次见到这名玩家，只记录基线
                continue;
            }
            int delta = current - last;
            if (delta > 0) {
                data.market().addSupply(FundType.EMR_CMP, delta);
                data.setDirty();
            }
        }
    }

    /** 玩家退出时清理他的统计基线。 */
    public static void forget(Player player) {
        LAST_TRADE_STAT.remove(player.getUUID());
    }
}
