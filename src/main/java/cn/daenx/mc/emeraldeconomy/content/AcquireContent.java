package cn.daenx.mc.emeraldeconomy.content;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 自动收购机的内容（来自 {@code config/emerald_economy/acquire.json}）。
 * <p>
 * 一个分类下面是一串收购买卖条目，每条可以是具体物品，也可以是<b>物品标签</b>
 * （写 {@code tag} 而不是 {@code item}，例如 {@code minecraft:saplings} 收所有树苗）；
 * {@code batch} 表示「多少个算一份」（鸡蛋 8 个一份），不写就是按个收。
 * <p>
 * 标签条目在界面上是<b>一行</b>（不会把标签里的东西一件件列出来），
 * 玩家卖背包里的任意一种都行 —— 唱片、树苗、花、蘑菇这类「种类一大堆、价格还一样」的
 * 东西因此只占一行。想给它起个好名字就写 {@code lang}（翻译键）或 {@code name}（原样文本）。
 * <p>
 * 疯狂星期五的每日随机池与基准价也在这里（{@code friday} 段），
 * 池子里除了具体物品，还支持 {@code #标签} 与 {@code *_spawn_egg} 这种通配写法，
 * 并用 {@code poolMaxPicks} 限制「某个池子最多被挑中几件」（头颅最多 1 个、刷怪蛋最多 1 个）。
 */
public record AcquireContent(List<Category> categories, Friday friday) {

    /** 一个收购分类。 */
    public record Category(String id, String lang, String name, List<Item> items) {

        public String idOr(String fallback) {
            return id == null || id.isBlank() ? fallback : id;
        }

        public List<Item> itemList() {
            return items == null ? List.of() : items;
        }
    }

    /**
     * 一条收购条目。
     *
     * @param item  具体物品 ID（二选一）
     * @param tag   物品标签（二选一，写 {@code minecraft:saplings} 这种不带 # 的形式）
     * @param price 每「一份」的价格
     * @param batch 一份等于几个（缺省 1）
     * @param lang  显示名翻译键（可选，标签行尤其需要它）
     * @param name  显示名原样文本（可选，翻译键查不到时用它）
     * @param filter 额外的匹配规则（可选，目前只支持 {@code treasure_map}：
     *               只收带结构标记的藏宝图，不收普通已探索地图）
     */
    public record Item(String item, String tag, Integer price, Integer batch, String lang, String name,
                       String filter) {

        /** 「只收藏宝图」的过滤规则。 */
        public static final String FILTER_TREASURE_MAP = "treasure_map";

        /** 是不是标签条目。 */
        public boolean isTag() {
            return tag != null && !tag.isBlank();
        }

        /** 过滤规则（null 表示不额外过滤）。 */
        public String filterOrNull() {
            return filter == null || filter.isBlank() ? null : filter.trim().toLowerCase(Locale.ROOT);
        }

        /** 是否只收「藏宝图」（带结构标记的已填写地图）。 */
        public boolean isTreasureMapFilter() {
            return FILTER_TREASURE_MAP.equals(filterOrNull());
        }

        /** 用于日志/键的原始标识。 */
        public String raw() {
            return isTag() ? "#" + tag.trim() : (item == null ? "" : item.trim().toLowerCase(Locale.ROOT));
        }

        public long priceOr(long fallback) {
            return price == null ? fallback : Math.max(1, price);
        }

        public int batchOrOne() {
            return batch == null ? 1 : Math.max(1, batch);
        }

        public String langOrNull() {
            return lang == null || lang.isBlank() ? null : lang.trim();
        }

        public String nameOrNull() {
            return name == null || name.isBlank() ? null : name.trim();
        }
    }

    /**
     * 疯狂星期五的配置段。
     *
     * @param pool          随机池：具体物品 ID、{@code #标签}、或 {@code *_spawn_egg} 这类通配
     * @param poolMaxPicks  池子条目 → 每天最多被挑中几件（不写的条目不受限制）
     * @param basePrices    基准价：具体物品 ID 或池子条目（{@code #标签} / 通配）→ 基准价
     */
    public record Friday(String lang, String name, List<String> pool, Map<String, Integer> poolMaxPicks,
                         Map<String, Integer> basePrices) {

        public List<String> poolList() {
            return pool == null ? List.of() : pool;
        }

        public Map<String, Integer> basePriceMap() {
            return cleanIntMap(basePrices);
        }

        /** 池子条目的挑选上限（不配置就是不限制）。 */
        public Map<String, Integer> poolMaxPickMap() {
            return cleanIntMap(poolMaxPicks);
        }

        private static Map<String, Integer> cleanIntMap(Map<String, Integer> source) {
            if (source == null) {
                return Map.of();
            }
            Map<String, Integer> out = new LinkedHashMap<>();
            source.forEach((key, value) -> {
                if (key != null && value != null) {
                    out.put(key.trim().toLowerCase(Locale.ROOT), value);
                }
            });
            return out;
        }
    }

    /** 疯狂星期五分类在界面/存档里用的固定 ID。 */
    public static final String FRIDAY_ID = "FRIDAY";

    /** 「附魔回收」分类的固定 ID（它不来自 JSON，由服务端扫描背包生成）。 */
    public static final String ENCHANT_ID = "ENCHANT";

    /** 空内容（文件缺失或解析失败时的兜底）。 */
    public static AcquireContent empty() {
        return new AcquireContent(List.of(), null);
    }

    public List<Category> categoryList() {
        return categories == null ? List.of() : categories;
    }

    /** 按 ID 找分类（找不到返回 null）。 */
    public Category categoryById(String id) {
        if (id == null) {
            return null;
        }
        for (Category category : categoryList()) {
            if (id.equalsIgnoreCase(category.idOr(""))) {
                return category;
            }
        }
        return null;
    }

    /** 一条条目的展示/日志文本。 */
    public static List<String> describe(AcquireContent content) {
        List<String> lines = new ArrayList<>();
        for (Category category : content.categoryList()) {
            lines.add(category.idOr("?") + "(" + category.itemList().size() + " 条)");
        }
        return lines;
    }
}
