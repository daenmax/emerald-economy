package cn.daenx.mc.emeraldeconomy.economy;

import cn.daenx.mc.emeraldeconomy.config.EEConfig;
import cn.daenx.mc.emeraldeconomy.core.Amounts;
import cn.daenx.mc.emeraldeconomy.core.Notify;
import cn.daenx.mc.emeraldeconomy.data.AccountData;
import cn.daenx.mc.emeraldeconomy.data.EconomyData;
import cn.daenx.mc.emeraldeconomy.data.TransactionType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;
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
        NO_SPACE
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
     * 从背包扣除指定数量的某种物品。
     *
     * @return 是否扣除成功（数量不足时不做任何修改）
     */
    public static boolean removeItems(ServerPlayer player, Item item, long amount) {
        if (amount <= 0L) {
            return true;
        }
        Inventory inventory = player.getInventory();
        if (countItems(player, item) < amount) {
            return false;
        }
        long remaining = amount;
        for (int i = 0; i < inventory.getContainerSize() && remaining > 0L; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || !stack.is(item)) {
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
