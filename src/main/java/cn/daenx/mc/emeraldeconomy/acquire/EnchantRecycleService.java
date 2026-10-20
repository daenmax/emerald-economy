package cn.daenx.mc.emeraldeconomy.acquire;

import cn.daenx.mc.emeraldeconomy.data.AccountData;
import cn.daenx.mc.emeraldeconomy.data.EconomyData;
import cn.daenx.mc.emeraldeconomy.data.TransactionType;
import cn.daenx.mc.emeraldeconomy.shop.EnchantCatalog;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;

/**
 * 自动收购机的「附魔回收」分类。
 * <p>
 * 规则（需求）：
 * <ul>
 *     <li>列出玩家背包（含快捷栏，0~35 格）里<b>所有带附魔的物品</b>，
 *         附魔书也算（它带的是 {@code STORED_ENCHANTMENTS} 组件）；</li>
 *     <li>每个附魔的回收价 = <b>自动售货机里该附魔的最低价 ÷ 3 ÷ 该附魔最大等级 × 当前等级</b>，
 *         向下取整、最低 1；
 *         例：经验修补最低价 1560、最大等级 1、等级 1 → 1560 ÷ 3 = 520；
 *         保护最低价 896、最大等级 4、等级 3 → 896 ÷ 3 ÷ 4 × 3 = 224；合计 744。</li>
 *     <li>只看附魔，不看物品本身是皮革还是钻石；没写进售货机价格表的附魔用兜底最低价；</li>
 *     <li>一行代表背包里的<b>一整叠</b>，回收时整叠消失，总价 = 单件价格 × 数量。</li>
 * </ul>
 * 服务端负责扫描、计价与扣除；客户端只负责展示与发「回收这个格子」的意图，
 * 并会再弹一次确认框，防止误操作。
 */
public final class EnchantRecycleService {

    /**
     * 回收价相对售货机最低价的折算比例：最低价的 1/3。
     * <p>
     * 收购价永远低于售价，玩家没法靠「买书 → 卖回」套利。
     */
    private static final int RECYCLE_PRICE_DIVISOR = 3;

    /** 回收结果。 */
    public enum Result {
        OK,
        /** 格子序号不合法 */
        INVALID_SLOT,
        /** 背包内容已经变了（界面上的信息和实际不一致） */
        ITEM_CHANGED,
        /** 这一叠没有任何附魔 */
        NO_ENCHANTS
    }

    /** 单个附魔的回收价。 */
    public record EnchantLine(String enchantId, int level, long price) {
    }

    /**
     * 背包里的一叠附魔物品。
     *
     * @param slot       背包格子序号（0~35，快捷栏也是 0~8）
     * @param itemId     物品注册名
     * @param count      这一叠的数量
     * @param unitPrice  单件回收总价（它所有附魔的价格之和）
     * @param totalPrice 整叠回收总价（unitPrice × count）
     * @param enchants   每个附魔及其回收价
     */
    public record ItemEntry(int slot, String itemId, int count, long unitPrice, long totalPrice,
                            List<EnchantLine> enchants) {
    }

    /**
     * 一次回收的结果。
     *
     * @param result 结果状态
     * @param count  实际回收的数量
     * @param total  实际入账的绿宝石
     */
    public record Outcome(Result result, int count, long total) {
        public boolean ok() {
            return result == Result.OK;
        }

        static Outcome fail(Result result, int count) {
            return new Outcome(result, count, 0L);
        }
    }

    private EnchantRecycleService() {
    }

    // ------------------------------------------------------------------
    // 扫描与计价
    // ------------------------------------------------------------------

    /** 扫描玩家背包（0~35 格），返回所有带附魔的物品。 */
    public static List<ItemEntry> scan(ServerPlayer player, RegistryAccess access) {
        List<ItemEntry> out = new ArrayList<>();
        Inventory inventory = player.getInventory();
        int slots = Math.min(Inventory.INVENTORY_SIZE, inventory.getContainerSize());
        for (int slot = 0; slot < slots; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !EnchantmentHelper.hasAnyEnchantments(stack)) {
                continue;
            }
            List<EnchantLine> lines = linesOf(stack, access);
            if (lines.isEmpty()) {
                continue;
            }
            long unit = sum(lines);
            long total = unit * stack.getCount();
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            out.add(new ItemEntry(slot, itemId, stack.getCount(), unit, total, lines));
        }
        return out;
    }

    /**
     * 计算一叠物品每个附魔的回收价。
     * <p>
     * 附魔书读 {@code STORED_ENCHANTMENTS}，普通物品读 {@code ENCHANTMENTS}，
     * {@link EnchantmentHelper#getEnchantmentsForCrafting} 已经帮我们区分好了。
     */
    public static List<EnchantLine> linesOf(ItemStack stack, RegistryAccess access) {
        List<EnchantLine> out = new ArrayList<>();
        ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        for (var entry : enchantments.entrySet()) {
            String id = entry.getKey().unwrapKey()
                    .map(key -> key.location().toString())
                    .orElse("");
            if (id.isBlank()) {
                continue;
            }
            int level = Math.max(1, entry.getIntValue());
            out.add(new EnchantLine(id, level, priceOf(access, id, level)));
        }
        return out;
    }

    /**
     * 单个附魔的回收价：最低价 ÷ 3 ÷ 最大等级 × 当前等级（向下取整，最低 1）。
     * <p>
     * 先把乘法做完再一次性除，避免 {@code 896 ÷ 3 ÷ 4 × 3} 在整数运算里丢精度：
     * 正确结果是 {@code 896 × 3 ÷ (3 × 4) = 224}，而不是 {@code 298 ÷ 4 × 3 = 222}。
     */
    public static long priceOf(RegistryAccess access, String enchantId, int level) {
        int maxLevel = EnchantCatalog.level(access, enchantId);
        if (maxLevel <= 0) {
            maxLevel = Math.max(1, level);
        }
        int effective = Math.max(1, Math.min(level, maxLevel));
        int minPrice = EnchantCatalog.minPriceOf(access, enchantId);
        long denominator = (long) RECYCLE_PRICE_DIVISOR * maxLevel;
        return Math.max(1L, (long) minPrice * effective / denominator);
    }

    private static long sum(List<EnchantLine> lines) {
        long total = 0L;
        for (EnchantLine line : lines) {
            total += line.price();
        }
        return total;
    }

    // ------------------------------------------------------------------
    // 回收
    // ------------------------------------------------------------------

    /**
     * 回收背包里指定格子的一整叠附魔物品。
     * <p>
     * 客户端会带上「它看到的是哪件物品、多少个」，服务端逐一核对：
     * 背包在确认框打开的这段时间里被改动过（例如被别的模组/命令动了），就直接拒绝，
     * 避免把玩家不想卖的东西卖掉。
     */
    public static Outcome recycle(EconomyData data, ServerPlayer player, int slot,
                                  String expectedItemId, int expectedCount) {
        Inventory inventory = player.getInventory();
        int slots = Math.min(Inventory.INVENTORY_SIZE, inventory.getContainerSize());
        if (slot < 0 || slot >= slots || expectedCount < 1) {
            return Outcome.fail(Result.INVALID_SLOT, expectedCount);
        }
        ItemStack stack = inventory.getItem(slot);
        if (stack.isEmpty()) {
            return Outcome.fail(Result.ITEM_CHANGED, expectedCount);
        }
        String actualId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (!actualId.equals(expectedItemId) || stack.getCount() != expectedCount) {
            return Outcome.fail(Result.ITEM_CHANGED, expectedCount);
        }
        List<EnchantLine> lines = linesOf(stack, player.level().registryAccess());
        if (lines.isEmpty()) {
            return Outcome.fail(Result.NO_ENCHANTS, expectedCount);
        }

        long total = sum(lines) * stack.getCount();
        // 先扣除物品，再入账：中间没有任何可能失败的步骤
        inventory.setItem(slot, ItemStack.EMPTY);
        inventory.setChanged();

        AccountData account = data.account(player.getUUID());
        long balanceBefore = account.balance();
        long debtBefore = account.debt();
        account.addBalance(total);
        account.pushRecord(TransactionType.ENCHANT_RECYCLE, total, balanceBefore, debtBefore,
                player.level().getGameTime());
        data.setDirty();
        return new Outcome(Result.OK, expectedCount, total);
    }
}
