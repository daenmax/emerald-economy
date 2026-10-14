package cn.daenx.mc.emeraldeconomy.market;

import cn.daenx.mc.emeraldeconomy.core.EEConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * 世界事件 → 基金供给 的分类标签。
 * <p>
 * 用数据包标签而不是硬编码列表，好处是：
 * <ul>
 *     <li>标签文件里可以直接引用原版标签（例如 {@code #minecraft:logs}），维护量小</li>
 *     <li>整合包作者可以自己往标签里加方块，不需要改代码</li>
 * </ul>
 * 标签文件位于 {@code data/emerald_economy/tags/block/} 与 {@code tags/entity_type/}。
 */
public final class SupplyTags {

    /** 矿石类方块 → 矿业基金。 */
    public static final TagKey<Block> MINING = blockTag("supply/mining");
    /** 原木、石头、沙子等 → 建筑基金。 */
    public static final TagKey<Block> BUILDING = blockTag("supply/building");
    /** 煤炭 / 红石 / 水 / 熔岩 → 水电基金。 */
    public static final TagKey<Block> HYDRO = blockTag("supply/hydro");
    /** 非年龄制作物（甘蔗、竹子、南瓜等）→ 农业基金。 */
    public static final TagKey<Block> CROPS = blockTag("supply/crops");

    /**
     * 能源类物品（水桶、熔岩桶）→ 水电基金。
     * <p>
     * 注意：NeoForge 的 {@code EntityPlaceEvent} **不会**为桶的放置触发
     * （见 {@code CommonHooks.onPlaceItemIntoWorld} 里对 {@code BucketItem} 的排除），
     * 所以「放置水或熔岩」必须靠物品使用事件来统计。
     */
    public static final TagKey<Item> HYDRO_ITEMS = TagKey.create(Registries.ITEM, EEConstants.id("supply/hydro_items"));

    /** 被动动物 → 畜牧基金。 */
    public static final TagKey<EntityType<?>> LIVESTOCK = entityTag("supply/livestock");
    /** 怪物与水生生物 → 狩猎基金。 */
    public static final TagKey<EntityType<?>> HUNTING = entityTag("supply/hunting");

    private SupplyTags() {
    }

    private static TagKey<Block> blockTag(String path) {
        return TagKey.create(Registries.BLOCK, EEConstants.id(path));
    }

    private static TagKey<EntityType<?>> entityTag(String path) {
        return TagKey.create(Registries.ENTITY_TYPE, EEConstants.id(path));
    }
}
