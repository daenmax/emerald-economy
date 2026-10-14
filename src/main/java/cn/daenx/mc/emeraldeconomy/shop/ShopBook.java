package cn.daenx.mc.emeraldeconomy.shop;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 自动售货机（附魔书商店）的持久化数据（需求文档 5.3）。
 * <p>
 * 格子解锁进度是「全服共享」的：任意玩家付费升级后，所有玩家都能看到新格子。
 */
public class ShopBook {

    private static final String KEY_UNLOCKED = "unlockedSlots";
    private static final String KEY_ENCHANTS = "enchantments";
    private static final String KEY_PURCHASES = "purchases";
    private static final String KEY_PURCHASE_DAY = "purchaseDay";
    private static final String KEY_INITIALIZED = "initialized";

    /** 已解锁的格子数量。 */
    private int unlockedSlots;
    /** 每个格子售卖的附魔（注册名），格子与附魔一一对应且不重复。 */
    private final List<String> enchantments = new ArrayList<>();
    /** 玩家 UUID -> (商品键 -> 今日已购次数)。商品键见 {@link ShopService#goodKeyOf}。 */
    private final Map<UUID, Map<String, Integer>> purchases = new HashMap<>();
    /** 限购计数所属的游戏日。 */
    private long purchaseDay = -1L;
    /** 是否已经按配置初始化过。 */
    private boolean initialized = false;

    public int unlockedSlots() {
        return unlockedSlots;
    }

    public void setUnlockedSlots(int unlockedSlots) {
        this.unlockedSlots = Math.max(0, unlockedSlots);
    }

    public List<String> enchantments() {
        return enchantments;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    public long purchaseDay() {
        return purchaseDay;
    }

    public void setPurchaseDay(long purchaseDay) {
        this.purchaseDay = purchaseDay;
    }

    /** 某玩家在某个商品上今日已购买的次数。 */
    public int purchasedCount(UUID playerId, String goodKey) {
        Map<String, Integer> map = purchases.get(playerId);
        if (map == null) {
            return 0;
        }
        return map.getOrDefault(goodKey, 0);
    }

    /** 记录一次购买。 */
    public void addPurchase(UUID playerId, String goodKey) {
        purchases.computeIfAbsent(playerId, k -> new HashMap<>())
                .merge(goodKey, 1, Integer::sum);
    }

    /** 每日结算时重置限购。 */
    public void resetPurchases() {
        purchases.clear();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(KEY_UNLOCKED, unlockedSlots);
        tag.putBoolean(KEY_INITIALIZED, initialized);
        ListTag list = new ListTag();
        enchantments.forEach(id -> list.add(StringTag.valueOf(id)));
        tag.put(KEY_ENCHANTS, list);
        CompoundTag purchasesTag = new CompoundTag();
        purchases.forEach((uuid, map) -> {
            CompoundTag slotTag = new CompoundTag();
            map.forEach((slot, count) -> slotTag.putInt(slot, count));
            purchasesTag.put(uuid.toString(), slotTag);
        });
        tag.put(KEY_PURCHASES, purchasesTag);
        tag.putLong(KEY_PURCHASE_DAY, purchaseDay);
        return tag;
    }

    public void load(CompoundTag tag) {
        unlockedSlots = tag.getInt(KEY_UNLOCKED);
        initialized = tag.getBoolean(KEY_INITIALIZED);
        enchantments.clear();
        ListTag list = tag.getList(KEY_ENCHANTS, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            enchantments.add(list.getString(i));
        }
        purchases.clear();
        CompoundTag purchasesTag = tag.getCompound(KEY_PURCHASES);
        for (String key : purchasesTag.getAllKeys()) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            CompoundTag slotTag = purchasesTag.getCompound(key);
            Map<String, Integer> map = new HashMap<>();
            for (String slotKey : slotTag.getAllKeys()) {
                map.put(slotKey, slotTag.getInt(slotKey));
            }
            purchases.put(uuid, map);
        }
        purchaseDay = tag.contains(KEY_PURCHASE_DAY) ? tag.getLong(KEY_PURCHASE_DAY) : -1L;
    }
}
