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
 * 疯狂星期五的每日随机池与基准价也在这里（{@code friday} 段）。
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
     */
    public record Item(String item, String tag, Integer price, Integer batch) {

        /** 是不是标签条目。 */
        public boolean isTag() {
            return tag != null && !tag.isBlank();
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
    }

    /** 疯狂星期五的配置段。 */
    public record Friday(String lang, String name, List<String> pool, Map<String, Integer> basePrices) {

        public List<String> poolList() {
            return pool == null ? List.of() : pool;
        }

        public Map<String, Integer> basePriceMap() {
            if (basePrices == null) {
                return Map.of();
            }
            Map<String, Integer> out = new LinkedHashMap<>();
            basePrices.forEach((key, value) -> {
                if (key != null && value != null) {
                    out.put(key.trim().toLowerCase(Locale.ROOT), value);
                }
            });
            return out;
        }
    }

    /** 疯狂星期五分类在界面/存档里用的固定 ID。 */
    public static final String FRIDAY_ID = "FRIDAY";

    /** 空内容（文件缺失或解析失败时的兜底）。 */
    public static AcquireContent empty() {
        return new AcquireContent(List.of(), null);
    }

    public List<Category> categoryList() {
        return categories == null ? List.of() : categories;
    }

    /** 全部条目的「物品 ID -> (价格, 批次)」，标签会展开成具体物品。 */
    public Map<String, long[]> flatten() {
        Map<String, long[]> out = new LinkedHashMap<>();
        for (Category category : categoryList()) {
            for (Item item : category.itemList()) {
                if (item.isTag()) {
                    continue;
                }
                if (!item.raw().isEmpty()) {
                    out.putIfAbsent(item.raw(), new long[]{item.priceOr(0L), item.batchOrOne()});
                }
            }
        }
        return out;
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
