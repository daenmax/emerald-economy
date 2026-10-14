package cn.daenx.mc.emeraldeconomy.lottery;

import cn.daenx.mc.emeraldeconomy.config.EEConfig;
import cn.daenx.mc.emeraldeconomy.core.Amounts;
import cn.daenx.mc.emeraldeconomy.core.EEConstants;
import cn.daenx.mc.emeraldeconomy.core.Notify;
import cn.daenx.mc.emeraldeconomy.data.AccountData;
import cn.daenx.mc.emeraldeconomy.data.EconomyData;
import cn.daenx.mc.emeraldeconomy.data.TransactionType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * 抽奖业务（需求文档 4.2 / 4.3 / 4.4）。
 * <p>
 * 每天每种场次每个玩家只能下一次注，下注金额立即从账户扣除，买定离手不可撤销。
 * 开奖在每日结算时统一进行。
 */
public final class LotteryService {

    private LotteryService() {
    }

    /** 下注结果。 */
    public enum Result {
        OK,
        /** 金额非法 */
        INVALID_AMOUNT,
        /** 选项非法 */
        INVALID_CHOICE,
        /** 账户余额不足 */
        INSUFFICIENT_BALANCE,
        /** 今日该场次已下注 */
        ALREADY_BET,
        /** 还有贷款没还清（界面可以打开，但不允许下注） */
        HAS_DEBT
    }

    /**
     * 下注。
     *
     * @param type   场次
     * @param choice 选择（单/双、大/小、0-9）
     * @param amount 下注金额
     * @param day    当前游戏日
     */
    public static Result placeBet(EconomyData data, ServerPlayer player, LotteryType type,
                                 int choice, long amount, long day) {
        if (amount < 1L) {
            return Result.INVALID_AMOUNT;
        }
        if (!type.isValidChoice(choice)) {
            return Result.INVALID_CHOICE;
        }
        AccountData account = data.account(player.getUUID());
        // 欠债不许赌：有未还清的贷款时界面能打开，但不能下注
        if (account.debt() > 0L) {
            return Result.HAS_DEBT;
        }
        LotteryBook book = data.lottery();
        if (book.betCount(type, player.getUUID()) >= Math.max(1, EEConfig.lotteryDailyLimit())) {
            return Result.ALREADY_BET;
        }
        if (!account.canAfford(amount)) {
            return Result.INSUFFICIENT_BALANCE;
        }
        long balanceBefore = account.balance();
        long debtBefore = account.debt();
        account.addBalance(-amount);
        account.pushRecord(TransactionType.LOTTERY_BET, -amount, balanceBefore, debtBefore,
                player.level().getGameTime());
        book.bets(type).add(new BetRecord(player.getUUID(), player.getGameProfile().getName(), choice, amount, day));
        data.setDirty();
        return Result.OK;
    }

    /**
     * 每日开奖（需求文档 4.4 + 七.3）。
     * <p>
     * 为每个场次生成 0-9 的随机结果，中奖者按「下注金额 × 赔率」向下取整（最低 1）入账，
     * 然后在聊天栏广播结果与中奖名单，最后清空当天所有下注。
     * <p>
     * 每个参与者的「下注额 / 中奖额」会一并记进 {@link LotteryBook}，
     * 这样界面上的「上次开奖」就能显示这一期的余额变化（加了多少 / 没变）。
     */
    public static void draw(MinecraftServer server, EconomyData data, long day, RandomSource random) {
        LotteryBook book = data.lottery();
        for (LotteryType type : LotteryType.values()) {
            List<BetRecord> bets = book.bets(type);
            if (bets.isEmpty()) {
                continue;
            }
            int result = random.nextInt(LotteryType.RESULT_MAX + 1);
            book.setLastResult(type, result);
            book.clearOutcomes(type);

            List<String> winners = new ArrayList<>();
            double odds = type.odds();
            for (BetRecord bet : bets) {
                boolean win = type.isWinner(bet.choice(), result);
                long prize = win ? Amounts.floorAtLeastOne(bet.amount() * odds) : 0L;
                book.recordOutcome(type, bet.playerId(), bet.amount(), prize);
                if (!win) {
                    continue;
                }
                AccountData account = data.account(bet.playerId());
                long balanceBefore = account.balance();
                long debtBefore = account.debt();
                account.addBalance(prize);
                account.pushRecord(TransactionType.LOTTERY_WIN, prize, balanceBefore, debtBefore,
                        server.overworld().getGameTime());
                winners.add(bet.name() + "(" + prize + ")");

                ServerPlayer online = server.getPlayerList().getPlayer(bet.playerId());
                if (online != null) {
                    Notify.sendPrefixed(online, "🎲 ", "lottery.win_notice",
                            type.displayName(), result, prize);
                }
            }
            // 中奖名单直接传可翻译组件：原来先在服务端 getString() 成字符串，
            // 既漏了语言键前缀，又会把文案固定成服务端的语言。
            Component winnerText = winners.isEmpty()
                    ? Component.translatable(EEConstants.langKey("lottery.no_winner"))
                    : Component.literal(String.join("、", winners));
            Notify.broadcastLottery(server, "lottery.broadcast",
                    type.displayName(), result, winnerText);
        }
        book.clearBets();
        book.setLastDrawDay(day);
        data.setDirty();
    }
}
