package cn.daenx.mc.emeraldeconomy.acquire;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自动收购机的持久化数据，主要是「疯狂星期五」的当日状态（需求文档 6.4）。
 */
public class AcquireBook {

    private static final String KEY_ACTIVE = "fridayActive";
    private static final String KEY_DAY = "fridayDay";
    private static final String KEY_ITEMS = "fridayItems";
    private static final String KEY_MULTIPLIERS = "fridayMultipliers";
    private static final String KEY_BASE_PRICES = "fridayBasePrices";

    /** 疯狂星期五是否处于开放状态。 */
    private boolean fridayActive;
    /** 这份状态所属的游戏日，用于判断是否已经过期。 */
    private long fridayDay = -1L;
    /** 当日被选中的重金收购物品（注册名）。 */
    private final List<String> fridayItems = new ArrayList<>();
    /** 当日每个物品的收购倍率。 */
    private final Map<String, Double> fridayMultipliers = new HashMap<>();
    /**
     * 当日每个物品的基准价。
     * <p>
     * 挑中时就写进存档：池子里现在支持「整类」条目（某个标签、某个通配），
     * 选中那一刻就把价定死，当天中途改配置也不会把已经广播出去的价格改掉。
     */
    private final Map<String, Integer> fridayBasePrices = new HashMap<>();

    public boolean isFridayActive() {
        return fridayActive;
    }

    public void setFridayActive(boolean fridayActive) {
        this.fridayActive = fridayActive;
    }

    public long fridayDay() {
        return fridayDay;
    }

    public void setFridayDay(long fridayDay) {
        this.fridayDay = fridayDay;
    }

    public List<String> fridayItems() {
        return fridayItems;
    }

    public Map<String, Double> fridayMultipliers() {
        return fridayMultipliers;
    }

    /** 指定物品当日的收购倍率。 */
    public double multiplierOf(String itemId) {
        return fridayMultipliers.getOrDefault(itemId, 1.0D);
    }

    /** 当日每个物品的基准价（挑中时定下，随存档保存）。 */
    public Map<String, Integer> fridayBasePrices() {
        return fridayBasePrices;
    }

    /**
     * 指定物品当日的基准价。
     *
     * @param fallback 旧存档里没有这个字段时用配置里的基准价兜底
     */
    public long basePriceOf(String itemId, long fallback) {
        Integer stored = fridayBasePrices.get(itemId);
        return stored == null ? Math.max(1L, fallback) : Math.max(1, stored);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(KEY_ACTIVE, fridayActive);
        tag.putLong(KEY_DAY, fridayDay);
        ListTag list = new ListTag();
        fridayItems.forEach(id -> list.add(StringTag.valueOf(id)));
        tag.put(KEY_ITEMS, list);
        CompoundTag multipliersTag = new CompoundTag();
        fridayMultipliers.forEach(multipliersTag::putDouble);
        tag.put(KEY_MULTIPLIERS, multipliersTag);
        CompoundTag baseTag = new CompoundTag();
        fridayBasePrices.forEach(baseTag::putInt);
        tag.put(KEY_BASE_PRICES, baseTag);
        return tag;
    }

    public void load(CompoundTag tag) {
        fridayActive = tag.getBoolean(KEY_ACTIVE);
        fridayDay = tag.contains(KEY_DAY) ? tag.getLong(KEY_DAY) : -1L;
        fridayItems.clear();
        ListTag list = tag.getList(KEY_ITEMS, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            fridayItems.add(list.getString(i));
        }
        fridayMultipliers.clear();
        CompoundTag multipliersTag = tag.getCompound(KEY_MULTIPLIERS);
        for (String key : multipliersTag.getAllKeys()) {
            fridayMultipliers.put(key, multipliersTag.getDouble(key));
        }
        fridayBasePrices.clear();
        CompoundTag baseTag = tag.getCompound(KEY_BASE_PRICES);
        for (String key : baseTag.getAllKeys()) {
            fridayBasePrices.put(key, baseTag.getInt(key));
        }
    }
}
