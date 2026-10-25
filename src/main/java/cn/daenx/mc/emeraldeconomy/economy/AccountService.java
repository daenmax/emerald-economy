package cn.daenx.mc.emeraldeconomy.economy;

import cn.daenx.mc.emeraldeconomy.config.EEConfig;
import cn.daenx.mc.emeraldeconomy.core.Amounts;
import cn.daenx.mc.emeraldeconomy.core.Experience;
import cn.daenx.mc.emeraldeconomy.core.Notify;
import cn.daenx.mc.emeraldeconomy.data.AccountData;
import cn.daenx.mc.emeraldeconomy.data.EconomyData;
import cn.daenx.mc.emeraldeconomy.data.TransactionType;
import cn.daenx.mc.emeraldeconomy.data.UnlockFeature;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 统一经济账户的业务逻辑（需求文档 1.1 / 2.2 / 2.3 / 2.4 / 2.5）。
 * <p>
 * 所有金额结算都在服务端完成，客户端只负责发起请求。账户余额与背包里的绿宝石是分开的，
 * 只有存款 / 取款会在两者之间搬运物品。
 */
public final class AccountService {

    private AccountService() {
    }

    /** 操作结果。 */
    public enum Result {
        /** 成功 */
        OK,
        /** 金额非法（必须为不小于 1 的整数） */
        INVALID_AMOUNT,
        /** 背包里的绿宝石不够 */
        INSUFFICIENT_ITEMS,
        /** 账户余额不足 */
        INSUFFICIENT_BALANCE,
        /** 超出可贷额度 */
        EXCEEDS_CREDIT,
        /** 当前没有欠款 */
        NO_DEBT,
        /** 背包放不下（「取出全部」时用） */
        NO_SPACE,
        /** 「经验存取」还没付费解锁 */
        LEVEL_LOCKED,
        /** 「经验存取」已经解锁过了 */
        LEVEL_ALREADY_UNLOCKED,
        /** 玩家身上 / ATM 里的经验不够 */
        INSUFFICIENT_EXPERIENCE,
        /** 今天没有可领取的在线奖励 */
        NO_ONLINE_REWARD,
        /** 转账目标玩家不存在 */
        TARGET_NOT_FOUND,
        /** 目标玩家不在线（转账 / 赠送要求对方在线） */
        TARGET_OFFLINE,
        /** 不能给自己转账 */
        SELF_TRANSFER
    }

    /**
     * 一次账户操作的结果。
     *
     * @param result   结果状态
     * @param amount   操作金额
     * @param fee      手续费
     * @param credited 实际进出账户的净额
     */
    public record Outcome(Result result, long amount, long fee, long credited) {
        public boolean ok() {
            return result == Result.OK;
        }

        static Outcome fail(Result result, long amount) {
            return new Outcome(result, amount, 0L, 0L);
        }
    }

    // ------------------------------------------------------------------
    // 存款 / 取款
    // ------------------------------------------------------------------

    /**
     * 存款：把背包里的绿宝石放进账户，收取手续费。
     * <p>
     * 例：存入 100，费率 1%，则手续费 1，账户实际增加 99。
     */
    public static Outcome deposit(EconomyData data, ServerPlayer player, long amount) {
        if (amount < 1L) {
            return Outcome.fail(Result.INVALID_AMOUNT, amount);
        }
        if (countEmeralds(player) < amount) {
            return Outcome.fail(Result.INSUFFICIENT_ITEMS, amount);
        }
        long fee = Amounts.fee(amount, EEConfig.depositFeeRate());
        if (fee > amount) {
            fee = amount;
        }
        long credited = amount - fee;
        if (!removeEmeralds(player, amount)) {
            return Outcome.fail(Result.INSUFFICIENT_ITEMS, amount);
        }
        AccountData account = data.account(player.getUUID());
        long balanceBefore = account.balance();
        long debtBefore = account.debt();
        account.addBalance(credited);
        account.pushRecord(TransactionType.DEPOSIT, credited, balanceBefore, debtBefore,
                player.level().getGameTime());
        data.setDirty();
        return new Outcome(Result.OK, amount, fee, credited);
    }

    /**
     * 存入全部：把背包里的绿宝石一个不留地存进账户（ATM 的「存入全部」按钮）。
     *
     * @return 与 {@link #deposit} 同样的结果，金额就是背包里的绿宝石总数
     */
    public static Outcome depositAll(EconomyData data, ServerPlayer player) {
        long amount = countEmeralds(player);
        if (amount < 1L) {
            return Outcome.fail(Result.INSUFFICIENT_ITEMS, 0L);
        }
        return deposit(data, player, amount);
    }

    /**
     * 取款：从账户里取出绿宝石放进背包，手续费在账户侧扣除。
     * <p>
     * 例：取出 100，费率 1%，则账户扣 101，背包获得 100。
     */
    public static Outcome withdraw(EconomyData data, ServerPlayer player, long amount) {
        if (amount < 1L) {
            return Outcome.fail(Result.INVALID_AMOUNT, amount);
        }
        long fee = Amounts.fee(amount, EEConfig.withdrawFeeRate());
        long total = amount + fee;
        AccountData account = data.account(player.getUUID());
        if (!account.canAfford(total)) {
            return Outcome.fail(Result.INSUFFICIENT_BALANCE, amount);
        }
        long balanceBefore = account.balance();
        long debtBefore = account.debt();
        account.addBalance(-total);
        giveEmeralds(player, amount);
        account.pushRecord(TransactionType.WITHDRAW, -total, balanceBefore, debtBefore,
                player.level().getGameTime());
        data.setDirty();
        return new Outcome(Result.OK, amount, fee, total);
    }

    /**
     * 取出全部：把账户余额全部换成绿宝石塞进背包（ATM 的「取出全部」按钮）。
     * <p>
     * 手续费按正常规则算，所以实际能取出的上限是「余额 ÷ (1 + 费率)」；
     * 只要背包装不下，就整笔拒绝、一个也不取（需求：背包容量不足则失败）。
     */
    public static Outcome withdrawAll(EconomyData data, ServerPlayer player) {
        AccountData account = data.account(player.getUUID());
        long balance = account.balance();
        if (balance < 1L) {
            return Outcome.fail(Result.INSUFFICIENT_BALANCE, 0L);
        }
        double rate = EEConfig.withdrawFeeRate();
        // 逐分位试探：找出「本金 + 手续费 ≤ 余额」的最大本金
        long amount = (long) Math.floor(balance / (1.0D + Math.max(0.0D, rate)));
        // 向下取整可能多留 1 个手续费的钱，这里补一次（最多补几十次，成本可忽略）
        while (amount < balance) {
            long fee = Amounts.fee(amount + 1L, rate);
            if (amount + 1L + fee > balance) {
                break;
            }
            amount++;
        }
        if (amount < 1L) {
            return Outcome.fail(Result.INSUFFICIENT_BALANCE, 0L);
        }
        if (!canHoldEmeralds(player, amount)) {
            return Outcome.fail(Result.NO_SPACE, amount);
        }
        return withdraw(data, player, amount);
    }

    // ------------------------------------------------------------------
    // 贷款 / 还款
    // ------------------------------------------------------------------

    /** 申请贷款：金额立即进入账户，同时产生等额欠款。 */
    public static Outcome loan(EconomyData data, ServerPlayer player, long amount) {
        if (amount < 1L) {
            return Outcome.fail(Result.INVALID_AMOUNT, amount);
        }
        AccountData account = data.account(player.getUUID());
        if (amount > account.availableCredit()) {
            return Outcome.fail(Result.EXCEEDS_CREDIT, amount);
        }
        long balanceBefore = account.balance();
        long debtBefore = account.debt();
        account.addBalance(amount);
        account.addDebt(amount);
        account.pushRecord(TransactionType.LOAN, amount, balanceBefore, debtBefore,
                player.level().getGameTime());
        data.setDirty();
        return new Outcome(Result.OK, amount, 0L, amount);
    }

    /**
     * 还款：从账户余额扣款，优先偿还利息，再偿还本金。
     * <p>
     * 还款金额超过欠款时，只扣除欠款数额。
     */
    public static Outcome repay(EconomyData data, ServerPlayer player, long amount) {
        if (amount < 1L) {
            return Outcome.fail(Result.INVALID_AMOUNT, amount);
        }
        AccountData account = data.account(player.getUUID());
        if (account.debt() <= 0L) {
            return Outcome.fail(Result.NO_DEBT, amount);
        }
        long actual = Math.min(amount, account.debt());
        if (!account.canAfford(actual)) {
            return Outcome.fail(Result.INSUFFICIENT_BALANCE, amount);
        }
        long balanceBefore = account.balance();
        long debtBefore = account.debt();
        account.addBalance(-actual);
        account.repayDebt(actual);
        account.pushRecord(TransactionType.REPAY, -actual, balanceBefore, debtBefore,
                player.level().getGameTime());
        data.setDirty();
        return new Outcome(Result.OK, amount, 0L, actual);
    }

    // ------------------------------------------------------------------
    // 玩家之间的转账（排行榜界面用）
    // ------------------------------------------------------------------

    /**
     * 一次转账的结果。
     *
     * @param result     结果状态
     * @param amount     转账金额（自己账户扣掉的部分）
     * @param fee        手续费
     * @param received   对方实际到账（金额 - 手续费）
     * @param targetName 收款人名字
     */
    public record TransferOutcome(Result result, long amount, long fee, long received, String targetName) {
        public boolean ok() {
            return result == Result.OK;
        }

        static TransferOutcome fail(Result result, long amount, String targetName) {
            return new TransferOutcome(result, amount, 0L, 0L, targetName);
        }
    }

    /**
     * 给另一个玩家转账。
     * <p>
     * 需求示例：转 1000、手续费率 1% 时，自己账户扣 1000，对方实际只收到 990，
     * 10 个绿宝石作为手续费被系统收走。
     * <p>
     * 所有校验都在服务端完成：金额必须为正整数、不能转给自己、目标账户必须存在、
     * 自己的余额必须够转账金额（手续费从转账金额里扣，不再额外加钱）。
     */
    public static TransferOutcome transfer(EconomyData data, ServerPlayer player, UUID targetId, long amount) {
        if (amount < 1L) {
            return TransferOutcome.fail(Result.INVALID_AMOUNT, amount, "");
        }
        if (targetId == null || targetId.equals(player.getUUID())) {
            return TransferOutcome.fail(Result.SELF_TRANSFER, amount, "");
        }
        AccountData target = data.accountOrNull(targetId);
        if (target == null) {
            return TransferOutcome.fail(Result.TARGET_NOT_FOUND, amount, "");
        }
        String targetName = target.name() == null || target.name().isBlank()
                ? targetId.toString() : target.name();
        // 新规则：目标玩家必须在线才能接收转账
        MinecraftServer server = player.getServer();
        ServerPlayer onlineTarget = server == null ? null : server.getPlayerList().getPlayer(targetId);
        if (onlineTarget == null) {
            return TransferOutcome.fail(Result.TARGET_OFFLINE, amount, targetName);
        }
        AccountData sender = data.account(player.getUUID());
        if (!sender.canAfford(amount)) {
            return TransferOutcome.fail(Result.INSUFFICIENT_BALANCE, amount, targetName);
        }

        long fee = Math.min(amount, Amounts.fee(amount, EEConfig.transferFeeRate()));
        long received = amount - fee;
        long gameTime = player.level().getGameTime();

        long senderBalanceBefore = sender.balance();
        long senderDebtBefore = sender.debt();
        sender.addBalance(-amount);
        sender.pushRecord(TransactionType.TRANSFER_OUT, -amount, senderBalanceBefore, senderDebtBefore,
                gameTime);

        long targetBalanceBefore = target.balance();
        long targetDebtBefore = target.debt();
        target.addBalance(received);
        target.pushRecord(TransactionType.TRANSFER_IN, received, targetBalanceBefore, targetDebtBefore,
                gameTime);

        data.setDirty();

        // 目标必须在线，直接告诉他钱到账了
        Notify.send(onlineTarget, "atm.transfer_received", player.getGameProfile().getName(),
                received, fee);
        return new TransferOutcome(Result.OK, amount, fee, received, targetName);
    }

    /**
     * 一次经验赠送的结果。
     *
     * @param result     结果状态
     * @param amount     赠送的经验点
     * @param targetName 接收者名字
     */
    public record ExperienceGiftOutcome(Result result, long amount, String targetName) {
        public boolean ok() {
            return result == Result.OK;
        }

        static ExperienceGiftOutcome fail(Result result, long amount, String targetName) {
            return new ExperienceGiftOutcome(result, amount, targetName);
        }
    }

    /**
     * 把 ATM 里已存的经验赠送给另一个在线玩家。
     * <p>
     * 不收取手续费，赠送的是<b>ATM 里已存的经验点</b>（不是余额，也不是当前等级）。
     * 接收者必须在线，否则拒绝。
     */
    public static ExperienceGiftOutcome giftExperience(EconomyData data, ServerPlayer player,
                                                       UUID targetId, long amount) {
        if (amount < 1L) {
            return ExperienceGiftOutcome.fail(Result.INVALID_AMOUNT, amount, "");
        }
        if (targetId == null || targetId.equals(player.getUUID())) {
            return ExperienceGiftOutcome.fail(Result.SELF_TRANSFER, amount, "");
        }
        AccountData target = data.accountOrNull(targetId);
        if (target == null) {
            return ExperienceGiftOutcome.fail(Result.TARGET_NOT_FOUND, amount, "");
        }
        String targetName = target.name() == null || target.name().isBlank()
                ? targetId.toString() : target.name();
        MinecraftServer server = player.getServer();
        ServerPlayer onlineTarget = server == null ? null : server.getPlayerList().getPlayer(targetId);
        if (onlineTarget == null) {
            return ExperienceGiftOutcome.fail(Result.TARGET_OFFLINE, amount, targetName);
        }
        AccountData sender = data.account(player.getUUID());
        if (sender.storedExperience() < amount) {
            return ExperienceGiftOutcome.fail(Result.INSUFFICIENT_EXPERIENCE, amount, targetName);
        }

        long gameTime = player.level().getGameTime();
        long senderBalanceBefore = sender.balance();
        long senderDebtBefore = sender.debt();
        sender.addStoredExperience(-amount);
        sender.pushRecord(TransactionType.EXPERIENCE_GIFT_OUT, -amount, senderBalanceBefore,
                senderDebtBefore, gameTime);

        long targetBalanceBefore = target.balance();
        long targetDebtBefore = target.debt();
        target.addStoredExperience(amount);
        target.pushRecord(TransactionType.EXPERIENCE_GIFT_IN, amount, targetBalanceBefore,
                targetDebtBefore, gameTime);

        data.setDirty();
        Notify.send(onlineTarget, "atm.gift_received", player.getGameProfile().getName(), amount);
        return new ExperienceGiftOutcome(Result.OK, amount, targetName);
    }

    // ------------------------------------------------------------------
    // ATM 经验存取
    // ------------------------------------------------------------------

    /**
     * 一次经验存取的结果。
     *
     * @param result     结果状态
     * @param experience 本次实际搬运的经验点数（失败时为 0）
     * @param stored     操作后 ATM 里保存的经验点数
     */
    public record ExperienceOutcome(Result result, long experience, long stored) {
        public boolean ok() {
            return result == Result.OK;
        }

        static ExperienceOutcome fail(Result result, long stored) {
            return new ExperienceOutcome(result, 0L, stored);
        }
    }

    /**
     * 付费解锁一个功能（每个玩家一次，费用与流水类型由 {@link UnlockFeature} 决定）。
     * <p>
     * 需求：这类功能默认对所有人关闭，想用的玩家先付一笔余额解锁，解锁后不再有任何限制；
     * 没有解锁记录（旧存档里没有对应字段）就视为未解锁。
     */
    public static Outcome unlockFeature(EconomyData data, ServerPlayer player, UnlockFeature feature) {
        AccountData account = data.account(player.getUUID());
        if (account.isUnlocked(feature)) {
            return Outcome.fail(Result.LEVEL_ALREADY_UNLOCKED, 0L);
        }
        long cost = Math.max(0L, feature.cost());
        if (!account.canAfford(cost)) {
            return Outcome.fail(Result.INSUFFICIENT_BALANCE, cost);
        }
        long balanceBefore = account.balance();
        long debtBefore = account.debt();
        account.addBalance(-cost);
        account.unlockFeature(feature);
        account.pushRecord(feature.transactionType(), -cost, balanceBefore, debtBefore,
                player.level().getGameTime());
        data.setDirty();
        return new Outcome(Result.OK, cost, 0L, cost);
    }

    /**
     * 按「等级」把经验存进 ATM。
     * <p>
     * 例：当前 27 级，输入 1，就把「27 级当前经验条 + 27 级升到 28 级所需」这一整段经验存起来，
     * 玩家变成刚好 26 级、经验条清空；ATM 里记录的是<b>经验点数</b>，不是等级。
     * <p>
     * 上限是玩家当前等级：存 N 级不会把等级压成负数。存入不收任何手续费。
     */
    public static ExperienceOutcome depositExperience(EconomyData data, ServerPlayer player, int levels) {
        AccountData account = data.account(player.getUUID());
        if (!account.levelUnlocked()) {
            return ExperienceOutcome.fail(Result.LEVEL_LOCKED, account.storedExperience());
        }
        if (levels < 1) {
            return ExperienceOutcome.fail(Result.INVALID_AMOUNT, account.storedExperience());
        }
        if (player.experienceLevel < levels) {
            return ExperienceOutcome.fail(Result.INSUFFICIENT_EXPERIENCE, account.storedExperience());
        }
        int targetLevel = player.experienceLevel - levels;
        long current = Experience.totalExperience(player);
        long target = Experience.totalXpForLevel(targetLevel);
        long moved = current - target;
        if (moved < 1L) {
            return ExperienceOutcome.fail(Result.INVALID_AMOUNT, account.storedExperience());
        }
        if (!setExperience(player, target)) {
            return ExperienceOutcome.fail(Result.INVALID_AMOUNT, account.storedExperience());
        }
        long balanceBefore = account.balance();
        long debtBefore = account.debt();
        account.addStoredExperience(moved);
        account.pushRecord(TransactionType.LEVEL_DEPOSIT, moved, balanceBefore, debtBefore,
                player.level().getGameTime());
        data.setDirty();
        return new ExperienceOutcome(Result.OK, moved, account.storedExperience());
    }

    /**
     * 把玩家当前的全部经验存进 ATM（等级降到 0、经验条清空）。
     */
    public static ExperienceOutcome depositAllExperience(EconomyData data, ServerPlayer player) {
        AccountData account = data.account(player.getUUID());
        if (!account.levelUnlocked()) {
            return ExperienceOutcome.fail(Result.LEVEL_LOCKED, account.storedExperience());
        }
        long moved = Experience.totalExperience(player);
        if (moved < 1L) {
            return ExperienceOutcome.fail(Result.INVALID_AMOUNT, account.storedExperience());
        }
        if (!setExperience(player, 0L)) {
            return ExperienceOutcome.fail(Result.INVALID_AMOUNT, account.storedExperience());
        }
        long balanceBefore = account.balance();
        long debtBefore = account.debt();
        account.addStoredExperience(moved);
        account.pushRecord(TransactionType.LEVEL_DEPOSIT, moved, balanceBefore, debtBefore,
                player.level().getGameTime());
        data.setDirty();
        return new ExperienceOutcome(Result.OK, moved, account.storedExperience());
    }

    /**
     * 按「等级」从 ATM 取回经验。
     * <p>
     * 输入 N 表示「把等级提升 N 级所需要的经验」一次性还给你；经验不够 N 级的份量时拒绝，
     * 不会把 ATM 里的经验扣成负数。
     */
    public static ExperienceOutcome withdrawExperience(EconomyData data, ServerPlayer player, int levels) {
        AccountData account = data.account(player.getUUID());
        if (!account.levelUnlocked()) {
            return ExperienceOutcome.fail(Result.LEVEL_LOCKED, account.storedExperience());
        }
        if (levels < 1) {
            return ExperienceOutcome.fail(Result.INVALID_AMOUNT, account.storedExperience());
        }
        long current = Experience.totalExperience(player);
        long targetLevel = (long) player.experienceLevel + levels;
        if (targetLevel > Integer.MAX_VALUE) {
            return ExperienceOutcome.fail(Result.INVALID_AMOUNT, account.storedExperience());
        }
        long target = Experience.totalXpForLevel((int) targetLevel);
        long needed = target - current;
        if (needed < 1L) {
            return ExperienceOutcome.fail(Result.INVALID_AMOUNT, account.storedExperience());
        }
        if (account.storedExperience() < needed) {
            return ExperienceOutcome.fail(Result.INSUFFICIENT_EXPERIENCE, account.storedExperience());
        }
        if (!setExperience(player, target)) {
            return ExperienceOutcome.fail(Result.INVALID_AMOUNT, account.storedExperience());
        }
        long balanceBefore = account.balance();
        long debtBefore = account.debt();
        account.addStoredExperience(-needed);
        account.pushRecord(TransactionType.LEVEL_WITHDRAW, -needed, balanceBefore, debtBefore,
                player.level().getGameTime());
        data.setDirty();
        return new ExperienceOutcome(Result.OK, needed, account.storedExperience());
    }

    /**
     * 把 ATM 里的经验全部取回。
     * <p>
     * {@code totalExperience} 是 int，超过上限的部分会留在 ATM 里（正常玩法远远到不了这个量级）。
     */
    public static ExperienceOutcome withdrawAllExperience(EconomyData data, ServerPlayer player) {
        AccountData account = data.account(player.getUUID());
        if (!account.levelUnlocked()) {
            return ExperienceOutcome.fail(Result.LEVEL_LOCKED, account.storedExperience());
        }
        long current = Experience.totalExperience(player);
        long room = Math.max(0L, (long) Integer.MAX_VALUE - current);
        long moved = Math.min(account.storedExperience(), room);
        if (moved < 1L) {
            return ExperienceOutcome.fail(Result.INSUFFICIENT_EXPERIENCE, account.storedExperience());
        }
        if (!setExperience(player, current + moved)) {
            return ExperienceOutcome.fail(Result.INVALID_AMOUNT, account.storedExperience());
        }
        long balanceBefore = account.balance();
        long debtBefore = account.debt();
        account.addStoredExperience(-moved);
        account.pushRecord(TransactionType.LEVEL_WITHDRAW, -moved, balanceBefore, debtBefore,
                player.level().getGameTime());
        data.setDirty();
        return new ExperienceOutcome(Result.OK, moved, account.storedExperience());
    }

    /**
     * 把玩家经验精确设置到「累计经验 = total」的状态，并立即同步给客户端。
     * <p>
     * 直接改三个经验字段而不是反复调用 {@code giveExperiencePoints}，
     * 是为了避免浮点误差导致「存 5 级后停在 4.999 级」这种问题。
     *
     * @return 是否成功（超出 int 表示范围时返回 {@code false}）
     */
    private static boolean setExperience(ServerPlayer player, long total) {
        if (total < 0L || total > Integer.MAX_VALUE) {
            return false;
        }
        int value = (int) total;
        int level = Experience.levelForTotal(value);
        float progress = Experience.progressForTotal(value);
        player.experienceLevel = level;
        player.experienceProgress = progress;
        player.totalExperience = value;
        if (player.connection != null) {
            // 服务端每刻也会比对这三个字段并补发，这里主动发一次让界面立刻刷新。
            // 注意：1.21.1 这个包的构造器参数顺序是 (进度, 总经验, 等级)，
            // 与它内部的网络读写顺序 (进度, 等级, 总经验) 不同，传反了客户端会把
            // 总经验当成等级显示（例如取回 1 级后 HUD 闪一下 7 级）。
            player.connection.send(new ClientboundSetExperiencePacket(progress, value, level));
        }
        return true;
    }

    // ------------------------------------------------------------------
    // 每日在线奖励（本轮新增）
    // ------------------------------------------------------------------

    /**
     * 每日结算利息时重置在线奖励资格。
     * <p>
     * 需求：重置的那一刻<b>只有在线玩家</b>能拿到当天的领取资格；不在线的玩家今天就没有资格，
     * 也不能事后补领。每个玩家每天最多领一次。
     *
     * @param day 结算后进入的新的一天
     */
    public static void resetOnlineReward(MinecraftServer server, EconomyData data, long day) {
        for (Map.Entry<UUID, AccountData> entry : data.accounts().entrySet()) {
            AccountData account = entry.getValue();
            boolean online = server.getPlayerList().getPlayer(entry.getKey()) != null;
            if (online) {
                account.grantOnlineReward(day);
            } else {
                account.revokeOnlineReward();
            }
        }
        data.setDirty();
    }

    /**
     * 领取当日的在线奖励（奖励是<b>经验值</b>，公式为「基础值 + 当前游戏日」）。
     *
     * @return 是否领取成功（没有资格或已经领过都返回 {@code false}）
     */
    public static boolean claimOnlineReward(EconomyData data, ServerPlayer player, long day) {
        AccountData account = data.account(player.getUUID());
        if (!account.hasOnlineReward(day)) {
            return false;
        }
        int experience = EEConfig.onlineRewardExperience(day);
        long balanceBefore = account.balance();
        long debtBefore = account.debt();
        player.giveExperiencePoints(experience);
        account.markOnlineRewardClaimed();
        account.pushRecord(TransactionType.ONLINE_REWARD, experience, balanceBefore, debtBefore,
                player.level().getGameTime());
        data.setDirty();
        Notify.send(player, "atm.online_reward_ok", experience, player.experienceLevel);
        return true;
    }

    // ------------------------------------------------------------------
    // 每日利息（需求文档 2.4 / 2.5 / 七.2）
    // ------------------------------------------------------------------

    /**
     * 每日结算时统一处理账户利息与贷款利息。
     * <p>
     * 两种利息都向下取整、最低 0；贷款利息按复利直接累加到欠款里。
     * <p>
     * 账户日息有一条起算线：余额<b>达到</b> {@code interestMinBalance}（默认 200）
     * 才开始计息，低于这条线的小额余额不计息、也不扣息。
     */
    public static void applyDailyInterest(MinecraftServer server, EconomyData data, long day) {
        double depositRate = EEConfig.dailyInterestRate();
        long minBalance = EEConfig.interestMinBalance();
        double loanRate = EEConfig.loanInterestRate();
        long gameTime = server.overworld().getGameTime();

        for (Map.Entry<UUID, AccountData> entry : data.accounts().entrySet()) {
            AccountData account = entry.getValue();
            ServerPlayer online = server.getPlayerList().getPlayer(entry.getKey());

            long balance = account.balance();
            if (balance >= minBalance) {
                long interest = Amounts.interest(balance, depositRate);
                if (interest > 0L) {
                    long debtBefore = account.debt();
                    account.addBalance(interest);
                    account.pushRecord(TransactionType.INTEREST, interest, balance, debtBefore, gameTime);
                    if (online != null) {
                        Notify.send(online, "atm.interest_received", interest, account.balance());
                    }
                }
            }

            long debt = account.debt();
            if (debt > 0L) {
                long interest = Amounts.interest(debt, loanRate);
                if (interest > 0L) {
                    long balanceBefore = account.balance();
                    account.addDebtInterest(interest);
                    account.pushRecord(TransactionType.LOAN_INTEREST, interest, balanceBefore, debt,
                            gameTime);
                    if (online != null) {
                        Notify.send(online, "atm.loan_interest_added", interest, account.debt());
                        if (account.balance() < interest) {
                            Notify.send(online, "atm.loan_interest_warning", account.debt());
                        }
                    }
                }
            }
        }
        data.setDirty();
    }

    // ------------------------------------------------------------------
    // 背包绿宝石读写
    // ------------------------------------------------------------------

    /** 统计玩家背包中的绿宝石数量。 */
    public static long countEmeralds(ServerPlayer player) {
        return countItems(player, Items.EMERALD);
    }

    /**
     * 从背包扣除指定数量的绿宝石。
     *
     * @return 是否扣除成功（数量不足时不做任何修改）
     */
    public static boolean removeEmeralds(ServerPlayer player, long amount) {
        return removeItems(player, Items.EMERALD, amount);
    }

    /** 统计背包中某种物品的总数量。 */
    public static long countItems(ServerPlayer player, Item item) {
        Inventory inventory = player.getInventory();
        long total = 0L;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * 统计背包中「一组物品」的总数量（收购买卖机的标签行用：卖「任意音乐唱片」时，
     * 数量要把背包里各种唱片加在一起）。
     */
    public static long countItems(ServerPlayer player, Collection<Item> items) {
        if (items == null || items.isEmpty()) {
            return 0L;
        }
        Set<Item> lookup = new HashSet<>(items);
        Inventory inventory = player.getInventory();
        long total = 0L;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && lookup.contains(stack.getItem())) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * 从背包扣除指定数量的某种物品。
     *
     * @return 是否扣除成功（数量不足时不做任何修改）
     */
    public static boolean removeItems(ServerPlayer player, Item item, long amount) {
        return removeItems(player, List.of(item), amount);
    }

    /**
     * 从背包扣除指定数量的「一组物品」（按背包格顺序拿，不要求是同一种）。
     * <p>
     * 收购买卖机的标签行（例如「任意音乐唱片」）用它：卖 3 张唱片时，背包里是哪几种都行。
     *
     * @return 是否扣除成功（总数不足时不做任何修改）
     */
    public static boolean removeItems(ServerPlayer player, Collection<Item> items, long amount) {
        if (amount <= 0L) {
            return true;
        }
        if (items == null || items.isEmpty()) {
            return false;
        }
        Set<Item> lookup = new HashSet<>(items);
        Inventory inventory = player.getInventory();
        if (countItems(player, lookup) < amount) {
            return false;
        }
        long remaining = amount;
        for (int i = 0; i < inventory.getContainerSize() && remaining > 0L; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || !lookup.contains(stack.getItem())) {
                continue;
            }
            int take = (int) Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
            if (stack.isEmpty()) {
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
        inventory.setChanged();
        return true;
    }

    /**
     * 把绿宝石给到玩家：优先放进背包，背包放不下就掉落在脚下（需求文档 5.4 的处理方式）。
     */
    public static void giveEmeralds(ServerPlayer player, long amount) {
        if (amount <= 0L) {
            return;
        }
        long remaining = amount;
        int maxStack = Items.EMERALD.getDefaultMaxStackSize();
        while (remaining > 0L) {
            int count = (int) Math.min(remaining, maxStack);
            giveItem(player, new ItemStack(Items.EMERALD, count));
            remaining -= count;
        }
        player.getInventory().setChanged();
    }

    /**
     * 背包主栏（36 格）还能装下多少个指定物品。
     * <p>
     * 只看主栏，因为原版 {@code Inventory#add} 也只往主栏放；盔甲与副手不算空间。
     * 「取出全部」用它判断「背包容量不足」，不足时整笔拒绝而不是把绿宝石丢在地上。
     */
    public static long spaceFor(ServerPlayer player, Item item) {
        Inventory inventory = player.getInventory();
        int maxStack = item.getDefaultMaxStackSize();
        int slots = Math.min(Inventory.INVENTORY_SIZE, inventory.getContainerSize());
        long space = 0L;
        for (int i = 0; i < slots; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                space += maxStack;
            } else if (stack.is(item)) {
                space += Math.max(0, maxStack - stack.getCount());
            }
        }
        return space;
    }

    /** 背包主栏能否装下这么多绿宝石。 */
    public static boolean canHoldEmeralds(ServerPlayer player, long amount) {
        return spaceFor(player, Items.EMERALD) >= amount;
    }

    /**
     * 把一件物品给到玩家：优先放入背包，背包满则掉落在脚下，保证物品不会凭空消失。
     */
    public static void giveItem(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(stack)) {
            ItemEntity drop = player.drop(stack, false);
            if (drop != null) {
                drop.setNoPickUpDelay();
            }
        }
        player.getInventory().setChanged();
    }
}
