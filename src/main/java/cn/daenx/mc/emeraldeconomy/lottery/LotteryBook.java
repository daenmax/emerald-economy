package cn.daenx.mc.emeraldeconomy.lottery;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 抽奖数据的持久化容器。
 */
public class LotteryBook {

    private static final String KEY_BETS = "bets";
    private static final String KEY_RESULTS = "results";
    private static final String KEY_DRAW_DAY = "drawDay";
    private static final String KEY_LAST_BETS = "lastBets";
    private static final String KEY_LAST_PRIZES = "lastPrizes";

    /** 场次 -> 当日下注列表。 */
    private final Map<LotteryType, List<BetRecord>> bets = new EnumMap<>(LotteryType.class);
    /** 场次 -> 最近一次开奖结果。 */
    private final Map<LotteryType, Integer> lastResults = new EnumMap<>(LotteryType.class);
    /** 场次 -> 玩家 -> 上一次开奖时该玩家的下注额（用于界面显示「上次开奖的余额变化」）。 */
    private final Map<LotteryType, Map<UUID, Long>> lastBets = new EnumMap<>(LotteryType.class);
    /** 场次 -> 玩家 -> 上一次开奖时该玩家赢得的金额（0 表示参与但未中奖）。 */
    private final Map<LotteryType, Map<UUID, Long>> lastPrizes = new EnumMap<>(LotteryType.class);
    /** 最近一次开奖的游戏日。 */
    private long lastDrawDay = -1L;

    public LotteryBook() {
        for (LotteryType type : LotteryType.values()) {
            bets.put(type, new ArrayList<>());
            lastBets.put(type, new LinkedHashMap<>());
            lastPrizes.put(type, new LinkedHashMap<>());
        }
    }

    /**
     * 记录一个玩家在某个场次的开奖结果。
     *
     * @param betAmount 该玩家的下注额
     * @param prize     该玩家赢得的金额（0 表示未中奖）
     */
    public void recordOutcome(LotteryType type, UUID playerId, long betAmount, long prize) {
        lastBets.computeIfAbsent(type, k -> new LinkedHashMap<>()).put(playerId, Math.max(0L, betAmount));
        lastPrizes.computeIfAbsent(type, k -> new LinkedHashMap<>()).put(playerId, Math.max(0L, prize));
    }

    /** 清空某个场次上一次开奖的玩家记录（开新一期前调用）。 */
    public void clearOutcomes(LotteryType type) {
        lastBets.computeIfAbsent(type, k -> new LinkedHashMap<>()).clear();
        lastPrizes.computeIfAbsent(type, k -> new LinkedHashMap<>()).clear();
    }

    /** 某玩家上一次开奖的下注额，没参与过返回 -1。 */
    public long lastBet(LotteryType type, UUID playerId) {
        Long value = lastBets.getOrDefault(type, Map.of()).get(playerId);
        return value == null ? -1L : value;
    }

    /** 某玩家上一次开奖赢得的金额，没参与过返回 -1。 */
    public long lastPrize(LotteryType type, UUID playerId) {
        Long value = lastPrizes.getOrDefault(type, Map.of()).get(playerId);
        return value == null ? -1L : value;
    }

    /** 某场次当日的下注列表。 */
    public List<BetRecord> bets(LotteryType type) {
        return bets.computeIfAbsent(type, k -> new ArrayList<>());
    }

    /** 清空所有场次的下注。 */
    public void clearBets() {
        bets.values().forEach(List::clear);
    }

    /** 某玩家在某场次的下注（没有则返回 null）。 */
    public BetRecord findBet(LotteryType type, UUID playerId) {
        for (BetRecord bet : bets(type)) {
            if (bet.playerId().equals(playerId)) {
                return bet;
            }
        }
        return null;
    }

    /** 某玩家在某场次当日的下注次数。 */
    public int betCount(LotteryType type, UUID playerId) {
        int count = 0;
        for (BetRecord bet : bets(type)) {
            if (bet.playerId().equals(playerId)) {
                count++;
            }
        }
        return count;
    }

    public Map<LotteryType, Integer> lastResults() {
        return lastResults;
    }

    public Integer lastResult(LotteryType type) {
        return lastResults.get(type);
    }

    public void setLastResult(LotteryType type, int result) {
        lastResults.put(type, result);
    }

    public long lastDrawDay() {
        return lastDrawDay;
    }

    public void setLastDrawDay(long day) {
        this.lastDrawDay = day;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        CompoundTag betsTag = new CompoundTag();
        bets.forEach((type, list) -> {
            ListTag listTag = new ListTag();
            list.forEach(bet -> listTag.add(bet.save()));
            betsTag.put(type.name(), listTag);
        });
        tag.put(KEY_BETS, betsTag);
        CompoundTag resultsTag = new CompoundTag();
        lastResults.forEach((type, value) -> resultsTag.putInt(type.name(), value));
        tag.put(KEY_RESULTS, resultsTag);
        tag.put(KEY_LAST_BETS, saveOutcomes(lastBets));
        tag.put(KEY_LAST_PRIZES, saveOutcomes(lastPrizes));
        tag.putLong(KEY_DRAW_DAY, lastDrawDay);
        return tag;
    }

    /** 把「场次 -> 玩家 -> 金额」两级表写成一个嵌套 CompoundTag。 */
    private static CompoundTag saveOutcomes(Map<LotteryType, Map<UUID, Long>> source) {
        CompoundTag tag = new CompoundTag();
        source.forEach((type, values) -> {
            CompoundTag inner = new CompoundTag();
            values.forEach((uuid, amount) -> inner.putLong(uuid.toString(), amount));
            tag.put(type.name(), inner);
        });
        return tag;
    }

    private static void loadOutcomes(CompoundTag tag, Map<LotteryType, Map<UUID, Long>> target) {
        target.clear();
        for (LotteryType type : LotteryType.values()) {
            Map<UUID, Long> values = new LinkedHashMap<>();
            CompoundTag inner = tag.getCompound(type.name());
            for (String key : inner.getAllKeys()) {
                try {
                    values.put(UUID.fromString(key), inner.getLong(key));
                } catch (IllegalArgumentException ignored) {
                    // 非法 UUID 直接跳过
                }
            }
            target.put(type, values);
        }
    }

    public void load(CompoundTag tag) {
        clearBets();
        CompoundTag betsTag = tag.getCompound(KEY_BETS);
        for (LotteryType type : LotteryType.values()) {
            ListTag listTag = betsTag.getList(type.name(), Tag.TAG_COMPOUND);
            List<BetRecord> list = bets(type);
            for (int i = 0; i < listTag.size(); i++) {
                list.add(BetRecord.load(listTag.getCompound(i)));
            }
        }
        lastResults.clear();
        CompoundTag resultsTag = tag.getCompound(KEY_RESULTS);
        for (LotteryType type : LotteryType.values()) {
            if (resultsTag.contains(type.name())) {
                lastResults.put(type, resultsTag.getInt(type.name()));
            }
        }
        loadOutcomes(tag.getCompound(KEY_LAST_BETS), lastBets);
        loadOutcomes(tag.getCompound(KEY_LAST_PRIZES), lastPrizes);
        lastDrawDay = tag.contains(KEY_DRAW_DAY) ? tag.getLong(KEY_DRAW_DAY) : -1L;
    }
}
