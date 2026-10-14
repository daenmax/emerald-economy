package cn.daenx.mc.emeraldeconomy.shop;

import cn.daenx.mc.emeraldeconomy.config.EEConfig;
import cn.daenx.mc.emeraldeconomy.content.EconomyContent;
import cn.daenx.mc.emeraldeconomy.content.ShopContent;
import cn.daenx.mc.emeraldeconomy.core.EEConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 附魔书目录（需求文档 5.3）。
 * <p>
 * 卖什么、卖多少钱由 {@code config/emerald_economy/shop.json} 决定（见 {@link ShopContent}），
 * 本类只负责「把配置里的附魔 ID 变成游戏里的东西」：
 * <ul>
 *     <li>按 ID 取附魔（{@link #holder(RegistryAccess, String)}）</li>
 *     <li>按内容文件给的区间算价格（同一附魔价格稳定不变）</li>
 *     <li>给出等级上限与「适用物品」文案（读原版附魔定义里的 {@code supported_items} 标签）</li>
 *     <li>生成一本「满级」附魔书</li>
 * </ul>
 * <p>
 * <b>注意</b>：{@link #level(RegistryAccess, String)} 与 {@link #applicable(RegistryAccess, String)}
 * 只依赖客户端也有的附魔注册表，不读任何配置——因为服务端配置在联机客户端上是拿不到的。
 * 分类与价格属于服务端数据，随快照一起下发。
 */
public final class EnchantCatalog {

    /** 原版附魔权重：10=常见、1=极稀有（仅用于内容文件里没写的附魔）。 */
    private static final int WEIGHT_COMMON = 10;
    private static final int WEIGHT_VERY_RARE = 1;

    /**
     * 原版附魔定义里的 {@code supported_items} 标签 → 「适用物品」文案的键后缀。
     * <p>
     * 例如 {@code minecraft:enchantable/sharp_weapon} 对应「剑、斧、重锤」。
     * 亡灵杀手与节肢杀手在原版是 {@code enchantable/weapon}（剑 + 斧），
     * 需求表格里写的是「剑、斧、重锤」，这里按需求统一到同一句话。
     */
    private static final Map<String, String> TAG_TO_TEXT = Map.ofEntries(
            Map.entry("enchantable/sharp_weapon", "sword_axe_mace"),
            Map.entry("enchantable/weapon", "sword_axe_mace"),
            Map.entry("enchantable/sword", "sword"),
            Map.entry("enchantable/fire_aspect", "sword"),
            Map.entry("enchantable/trident", "trident"),
            Map.entry("enchantable/mace", "mace"),
            Map.entry("enchantable/bow", "bow"),
            Map.entry("enchantable/crossbow", "crossbow"),
            Map.entry("enchantable/mining", "mining"),
            Map.entry("enchantable/mining_loot", "mining_loot"),
            Map.entry("enchantable/fishing", "fishing"),
            Map.entry("enchantable/armor", "armor"),
            Map.entry("enchantable/foot_armor", "foot_armor"),
            Map.entry("enchantable/head_armor", "head_armor"),
            Map.entry("enchantable/leg_armor", "leg_armor"),
            Map.entry("enchantable/durability", "durability"),
            Map.entry("enchantable/vanishing", "any"),
            Map.entry("enchantable/equippable", "armor"));

    private EnchantCatalog() {
    }

    /**
     * 可售卖的附魔（走解锁进度的那些），顺序就是内容文件里的顺序。
     * <p>
     * 内容文件里一条附魔都没写时退回「注册表里除诅咒以外的全部附魔」，
     * 免得有人把文件清空之后商店变成空壳。
     */
    public static List<String> availableEnchantments(RegistryAccess access) {
        List<String> ids = new ArrayList<>(EconomyContent.shop().unlockableEnchants().keySet());
        if (!ids.isEmpty()) {
            return ids;
        }
        if (access == null) {
            return ids;
        }
        for (ResourceLocation id : access.registryOrThrow(Registries.ENCHANTMENT).keySet()) {
            ids.add(id.toString());
        }
        return ids;
    }

    /** 取附魔的 Holder，找不到返回 null。 */
    public static Holder<Enchantment> holder(RegistryAccess access, String id) {
        ResourceLocation key = parseId(id);
        if (key == null || access == null) {
            return null;
        }
        return access.registryOrThrow(Registries.ENCHANTMENT).getHolder(key).orElse(null);
    }

    private static ResourceLocation parseId(String id) {
        try {
            return ResourceLocation.parse(id);
        } catch (Exception ex) {
            return null;
        }
    }

    /** 附魔的等级上限（拿不到附魔时返回 0）。 */
    public static int level(RegistryAccess access, String id) {
        Holder<Enchantment> holder = holder(access, id);
        return holder == null ? 0 : holder.value().getMaxLevel();
    }

    /** 附魔的显示名（可翻译组件；取不到时退回注册名）。 */
    public static Component displayName(RegistryAccess access, String id) {
        Holder<Enchantment> holder = holder(access, id);
        return holder == null ? Component.literal(id) : holder.value().description();
    }

    /**
     * 「适用物品」的显示文本（读附魔定义里的 {@code supported_items}）。
     * <p>
     * 能对上已知标签时返回可翻译组件；否则退回标签名或前几个物品名，避免界面上出现空白。
     */
    public static Component applicable(RegistryAccess access, String id) {
        Holder<Enchantment> holder = holder(access, id);
        if (holder == null) {
            return Component.literal("-");
        }
        HolderSet<Item> items = holder.value().getSupportedItems();
        Optional<TagKey<Item>> tag = items.unwrapKey();
        if (tag.isPresent()) {
            String path = tag.get().location().getPath();
            String mapped = TAG_TO_TEXT.get(path);
            return mapped == null
                    ? Component.literal(tag.get().location().toString())
                    : Component.translatable(EEConstants.langKey("shop.applicable." + mapped));
        }
        // 不是标签而是物品清单（整合包的附魔可能这么写）：列出前三个物品名
        StringBuilder builder = new StringBuilder();
        int shown = 0;
        for (Holder<Item> entry : items) {
            if (shown >= 3) {
                break;
            }
            if (shown > 0) {
                builder.append("、");
            }
            builder.append(Component.translatable(entry.value().getDescriptionId()).getString());
            shown++;
        }
        return builder.length() == 0 ? Component.literal("-") : Component.literal(builder.toString());
    }

    /**
     * 计算附魔书的价格。
     * <p>
     * 价格区间来自内容文件（{@code shop.json} 里每个附魔的 min/max），
     * 用附魔 ID 的哈希在其中稳定取值——同一附魔永远是同一个价，界面与扣费不会打架。
     * 内容文件里没写的附魔退回「按稀有度定价」的旧规则（整合包加了附魔也不会没价）。
     */
    public static int priceOf(RegistryAccess access, String id) {
        ShopContent.EnchantSpec spec = EconomyContent.shop().enchantSpec(id);
        if (spec != null) {
            int lo = spec.minPrice();
            int hi = spec.maxPrice();
            if (hi <= lo) {
                return lo;
            }
            return lo + (int) Math.floor(stableRatio(id) * (hi - lo + 1));
        }

        int min = EEConfig.isLoaded() ? EEConfig.shopPriceMin() : 256;
        int max = EEConfig.isLoaded() ? EEConfig.shopPriceMax() : 1280;
        Holder<Enchantment> holder = holder(access, id);
        if (holder == null) {
            return min;
        }
        int weight = Math.max(WEIGHT_VERY_RARE, Math.min(WEIGHT_COMMON, holder.value().getWeight()));
        double rarity = (double) (WEIGHT_COMMON - weight) / (double) (WEIGHT_COMMON - WEIGHT_VERY_RARE);
        double price = min + rarity * (max - min);

        if (isTreasure(holder)) {
            double lo = EEConfig.isLoaded() ? EEConfig.shopTreasureMultiplierMin() : 3.0D;
            double hi = EEConfig.isLoaded() ? EEConfig.shopTreasureMultiplierMax() : 5.0D;
            if (hi < lo) {
                hi = lo;
            }
            price *= lo + stableRatio(id) * (hi - lo);
        }
        return (int) Math.max(1L, Math.round(price));
    }

    /** 是否宝藏附魔（1.21 用 {@code minecraft:treasure} 标签判定）。 */
    public static boolean isTreasure(Holder<Enchantment> holder) {
        return holder.is(EnchantmentTags.TREASURE);
    }

    /** 用 ID 的哈希生成一个稳定的 [0,1) 比例，避免每次刷新价格都变。 */
    private static double stableRatio(String id) {
        long hash = id.toLowerCase(Locale.ROOT).hashCode() * 0x9E3779B97F4A7C15L;
        hash ^= hash >>> 29;
        hash *= 0xBF58476D1CE4E5B9L;
        hash ^= hash >>> 32;
        return (double) Math.floorMod(hash, 1000L) / 1000.0D;
    }

    /**
     * 生成一本满级附魔书。
     * <p>
     * 直接用 {@code STORED_ENCHANTMENTS} 组件写入，这样宝藏附魔（无法通过附魔台获得）
     * 也能正常出现在书上。
     */
    public static ItemStack createMaxLevelBook(RegistryAccess access, String id) {
        Holder<Enchantment> holder = holder(access, id);
        if (holder == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        mutable.set(holder, holder.value().getMaxLevel());
        stack.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
        return stack;
    }
}
