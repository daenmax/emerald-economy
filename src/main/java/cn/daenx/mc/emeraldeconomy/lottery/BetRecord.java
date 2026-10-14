package cn.daenx.mc.emeraldeconomy.lottery;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * 一笔抽奖下注记录。
 *
 * @param playerId 下注玩家
 * @param name     玩家名（用于开奖广播）
 * @param choice   选择的选项
 * @param amount   下注金额
 * @param day      下注的游戏日
 */
public record BetRecord(UUID playerId, String name, int choice, long amount, long day) {

    private static final String KEY_PLAYER = "player";
    private static final String KEY_NAME = "name";
    private static final String KEY_CHOICE = "choice";
    private static final String KEY_AMOUNT = "amount";
    private static final String KEY_DAY = "day";

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(KEY_PLAYER, playerId);
        tag.putString(KEY_NAME, name);
        tag.putInt(KEY_CHOICE, choice);
        tag.putLong(KEY_AMOUNT, amount);
        tag.putLong(KEY_DAY, day);
        return tag;
    }

    public static BetRecord load(CompoundTag tag) {
        return new BetRecord(
                tag.contains(KEY_PLAYER) ? tag.getUUID(KEY_PLAYER) : new UUID(0L, 0L),
                tag.getString(KEY_NAME),
                tag.getInt(KEY_CHOICE),
                tag.getLong(KEY_AMOUNT),
                tag.getLong(KEY_DAY));
    }
}
