package cn.daenx.mc.emeraldeconomy.content;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 自动售货机的货架内容（来自 {@code config/emerald_economy/shop.json}）。
 * <p>
 * 结构是「大分类 → 子分类 → 商品」：
 * <ul>
 *     <li>大分类：附魔书 / 其他（可以自己加，例如「食物」「建材」）</li>
 *     <li>子分类：附魔书下面是武器 / 远程 / 工具 / 盔甲 / 通用与诅咒；其他下面是种子 / 树苗</li>
 *     <li>商品：附魔书用 {@code enchants}（附魔 ID + 价格区间），普通物品用 {@code items}（物品 ID + 价格）</li>
 * </ul>
 * 名字用 {@code lang}（翻译键）与 {@code name}（原样文本）两个字段描述：
 * 客户端优先用能查到的翻译键，查不到就用 {@code name}，所以自己加的分类直接写中文名也能显示。
 * <p>
 * {@code unlockable = true} 的大分类走「花绿宝石逐格解锁」的进度（只有附魔书是这样）；
 * 其他大分类的商品一开始就能买。
 */
public record ShopContent(List<Group> groups) {

    /** 大分类。 */
    public record Group(String id, String lang, String name, String icon, Boolean unlockable,
                        List<Sub> subcategories) {

        public String idOr(String fallback) {
            return id == null || id.isBlank() ? fallback : id;
        }

        /** 是否走解锁进度（缺省 false）。 */
        public boolean isUnlockable() {
            return unlockable != null && unlockable;
        }

        public String iconOr(String fallback) {
            return icon == null ? fallback : icon;
        }
    }

    /** 子分类。 */
    public record Sub(String id, String lang, String name, String icon,
                      List<EnchantSpec> enchants, List<ItemSpec> items) {

        public String idOr(String fallback) {
            return id == null || id.isBlank() ? fallback : id;
        }

        public String iconOr(String fallback) {
            return icon == null ? fallback : icon;
        }

        public List<EnchantSpec> enchantList() {
            return enchants == null ? List.of() : enchants;
        }

        public List<ItemSpec> itemList() {
            return items == null ? List.of() : items;
        }
    }

    /** 一本附魔书：附魔 ID + 售价区间。 */
    public record EnchantSpec(String id, Integer min, Integer max) {

        public int minPrice() {
            return min == null ? 256 : Math.max(1, min);
        }

        public int maxPrice() {
            return max == null ? Math.max(minPrice(), 1280) : Math.max(minPrice(), max);
        }
    }

    /**
     * 一件普通商品：物品 ID + 售价，或者「经验值商品」：经验点数 + 售价。
     * <p>
     * 写 {@code items} 时一条要么给 {@code item}（买一件物品放背包），
     * 要么给 {@code exp}（买一份经验点，直接加到玩家的经验条上）。
     *
     * @param item  物品 ID（与 {@code exp} 二选一）
     * @param exp   经验点数（与 {@code item} 二选一，注意是经验值不是等级）
     * @param price 售价（绿宝石）
     */
    public record ItemSpec(String item, Integer exp, Integer price) {

        /** 是不是经验值商品。 */
        public boolean isExp() {
            return exp != null && exp > 0;
        }

        /** 经验点数（不是经验值商品时为 0）。 */
        public int expAmount() {
            return isExp() ? exp : 0;
        }

        public int priceOr(int fallback) {
            return price == null ? fallback : Math.max(1, price);
        }
    }

    /** 空内容（文件缺失或解析失败时的兜底）。 */
    public static ShopContent empty() {
        return new ShopContent(List.of());
    }

    // ------------------------------------------------------------------
    // 查询辅助
    // ------------------------------------------------------------------

    /** 全部子分类（按文件里的顺序），元素是「大分类 + 子分类」的组合。 */
    public List<Tab> tabs() {
        List<Tab> out = new ArrayList<>();
        for (Group group : groupList()) {
            for (Sub sub : subList(group)) {
                out.add(new Tab(group, sub));
            }
        }
        return out;
    }

    /**
     * 一个「大分类 + 子分类」的组合（也就是界面上的一页）。
     *
     * @param unlockable 该页的商品是否走解锁进度
     */
    public record Tab(Group group, Sub sub) {
        public boolean unlockable() {
            return group.isUnlockable();
        }
    }

    public List<Group> groupList() {
        return groups == null ? List.of() : groups;
    }

    public static List<Sub> subList(Group group) {
        return group == null || group.subcategories() == null ? List.of() : group.subcategories();
    }

    /** 所有走解锁进度的附魔（ID -> 价格区间），顺序就是文件里的顺序。 */
    public Map<String, EnchantSpec> unlockableEnchants() {
        Map<String, EnchantSpec> out = new LinkedHashMap<>();
        for (Group group : groupList()) {
            if (!group.isUnlockable()) {
                continue;
            }
            for (Sub sub : subList(group)) {
                for (EnchantSpec spec : sub.enchantList()) {
                    if (spec.id() != null && !spec.id().isBlank()) {
                        out.putIfAbsent(spec.id().trim().toLowerCase(Locale.ROOT), spec);
                    }
                }
            }
        }
        return out;
    }

    /** 所有普通商品（物品 ID -> 价格）。经验值商品不在这里（它们没有物品 ID）。 */
    public Map<String, Integer> plainItems() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Group group : groupList()) {
            if (group.isUnlockable()) {
                continue;
            }
            for (Sub sub : subList(group)) {
                for (ItemSpec spec : sub.itemList()) {
                    if (spec.item() != null && !spec.item().isBlank()) {
                        out.putIfAbsent(spec.item().trim().toLowerCase(Locale.ROOT), spec.priceOr(1));
                    }
                }
            }
        }
        return out;
    }

    /** 所有经验值商品（点数 -> 售价），用于「买了直接加经验」的货架条目。 */
    public Map<Integer, Integer> expItems() {
        Map<Integer, Integer> out = new LinkedHashMap<>();
        for (Group group : groupList()) {
            if (group.isUnlockable()) {
                continue;
            }
            for (Sub sub : subList(group)) {
                for (ItemSpec spec : sub.itemList()) {
                    if (spec.isExp()) {
                        out.putIfAbsent(spec.expAmount(), spec.priceOr(1));
                    }
                }
            }
        }
        return out;
    }

    /** 某份经验值商品属于哪个「大分类 / 子分类」（不在表里时返回 null）。 */
    public Tab tabOfExp(int exp) {
        for (Tab tab : tabs()) {
            for (ItemSpec spec : tab.sub().itemList()) {
                if (spec.isExp() && spec.expAmount() == exp) {
                    return tab;
                }
            }
        }
        return null;
    }

    /** 某份经验值商品的单价（不在表里时返回 0）。 */
    public int expPrice(int exp) {
        return expItems().getOrDefault(exp, 0);
    }

    /** 某个附魔属于哪个「大分类 / 子分类」（不在表里时返回 null）。 */
    public Tab tabOfEnchant(String enchantId) {
        if (enchantId == null) {
            return null;
        }
        String key = enchantId.toLowerCase(Locale.ROOT);
        for (Tab tab : tabs()) {
            for (EnchantSpec spec : tab.sub().enchantList()) {
                if (spec.id() != null && spec.id().trim().toLowerCase(Locale.ROOT).equals(key)) {
                    return tab;
                }
            }
        }
        return null;
    }

    /** 某个普通物品属于哪个「大分类 / 子分类」（不在表里时返回 null）。 */
    public Tab tabOfItem(String itemId) {
        if (itemId == null) {
            return null;
        }
        String key = itemId.toLowerCase(Locale.ROOT);
        for (Tab tab : tabs()) {
            for (ItemSpec spec : tab.sub().itemList()) {
                if (spec.item() != null && spec.item().trim().toLowerCase(Locale.ROOT).equals(key)) {
                    return tab;
                }
            }
        }
        return null;
    }

    /** 附魔的价格区间（查不到时返回 null）。 */
    public EnchantSpec enchantSpec(String enchantId) {
        return enchantId == null ? null : unlockableEnchants().get(enchantId.toLowerCase(Locale.ROOT));
    }
}
