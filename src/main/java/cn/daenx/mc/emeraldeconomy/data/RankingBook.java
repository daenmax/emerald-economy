package cn.daenx.mc.emeraldeconomy.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ATM 排行榜快照。
 * <p>
 * 需求：排行榜<b>每个游戏日更新一次</b>（在每日结算时更新），按「净值」排序，
 * 净值 = 账户余额 - 贷款欠款。
 * <p>
 * 为什么不实时计算：需求要的是「每天更新一次」的榜单，每天固定一份快照，
 * 玩家当天的转账、消费不会立刻改变名次，第二天结算后才刷新。
 * 快照随世界存档持久化，服务器重启后仍然是当天那份。
 * <p>
 * 如果服务器在新的一天启动、还没跑过结算，{@link #updateIfStale} 会补算一次，
 * 保证榜单不会一直停在「空」或上一天的旧数据上（仍然是一天最多更新一次）。
 */
public class RankingBook {

    private static final String KEY_DAY = "day";
    private static final String KEY_ENTRIES = "entries";
    private static final String ENTRY_UUID = "player";
    private static final String ENTRY_NAME = "name";
    private static final String ENTRY_BALANCE = "balance";
    private static final String ENTRY_DEBT = "debt";

    /** 排名条目。净值由余额与欠款相减得到。 */
    public record Entry(UUID playerId, String name, long balance, long debt) {

        /** 净值 = 账户余额 - 贷款欠款（欠款更多时是负数）。 */
        public long net() {
            return balance - debt;
        }

        public String playerIdString() {
            return playerId == null ? "" : playerId.toString();
        }
    }

    /** 上一次更新对应的游戏日（-1 表示还没有任何榜单）。 */
    private long day = -1L;
    private final List<Entry> entries = new ArrayList<>();

    public long day() {
        return day;
    }

    public List<Entry> entries() {
        return entries;
    }

    /**
     * 按当前账户数据重新生成榜单。
     * <p>
     * 只收录有玩家名的账户（从没登录过的空账户不会出现在榜上）。
     */
    public void update(EconomyData data, long today) {
        entries.clear();
        for (Map.Entry<UUID, AccountData> item : data.accounts().entrySet()) {
            AccountData account = item.getValue();
            String name = account.name();
            if (name == null || name.isBlank()) {
                continue;
            }
            entries.add(new Entry(item.getKey(), name, account.balance(), account.debt()));
        }
        entries.sort(Comparator.comparingLong(Entry::net).reversed()
                .thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
        day = today;
        data.setDirty();
    }

    /** 榜单不是今天的就补算一次（一天最多更新一次）。 */
    public void updateIfStale(EconomyData data, long today) {
        if (day != today) {
            update(data, today);
        }
    }

    // ------------------------------------------------------------------
    // 序列化
    // ------------------------------------------------------------------

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong(KEY_DAY, day);
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            CompoundTag item = new CompoundTag();
            item.putUUID(ENTRY_UUID, entry.playerId());
            item.putString(ENTRY_NAME, entry.name());
            item.putLong(ENTRY_BALANCE, entry.balance());
            item.putLong(ENTRY_DEBT, entry.debt());
            list.add(item);
        }
        tag.put(KEY_ENTRIES, list);
        return tag;
    }

    public void load(CompoundTag tag) {
        entries.clear();
        day = tag.contains(KEY_DAY) ? tag.getLong(KEY_DAY) : -1L;
        ListTag list = tag.getList(KEY_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompound(i);
            // 旧存档 / 手改存档缺少字段时按空值读，不会抛异常
            UUID uuid = item.contains(ENTRY_UUID) ? item.getUUID(ENTRY_UUID) : new UUID(0L, 0L);
            entries.add(new Entry(uuid, item.getString(ENTRY_NAME),
                    Math.max(0L, item.getLong(ENTRY_BALANCE)),
                    Math.max(0L, item.getLong(ENTRY_DEBT))));
        }
    }
}
