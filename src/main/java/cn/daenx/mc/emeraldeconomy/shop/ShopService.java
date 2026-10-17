package cn.daenx.mc.emeraldeconomy.shop;

import cn.daenx.mc.emeraldeconomy.config.EEConfig;
import cn.daenx.mc.emeraldeconomy.content.EconomyContent;
import cn.daenx.mc.emeraldeconomy.content.ShopContent;
import cn.daenx.mc.emeraldeconomy.core.EEConstants;
import cn.daenx.mc.emeraldeconomy.core.Notify;
import cn.daenx.mc.emeraldeconomy.data.AccountData;
import cn.daenx.mc.emeraldeconomy.data.EconomyData;
import cn.daenx.mc.emeraldeconomy.data.TransactionType;
import cn.daenx.mc.emeraldeconomy.economy.AccountService;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 自动售货机业务（需求文档 5.3 / 5.4）。
 * <p>
 * 卖什么完全由 {@code config/emerald_economy/shop.json} 决定（见 {@link ShopContent}）：
 * <ul>
 *     <li><b>解锁制</b>大分类（{@code unlockable = true}，默认是附魔书）：
 *         走「全服共享升级解锁格子」的进度，附魔书一律满级。</li>
 *     <li><b>常驻</b>大分类：普通物品（种子、树苗……）一开始就能买，不参与解锁进度。</li>
 * </ul>
 * 限购按「玩家 × 商品」计算，每天结算时重置——商品用{@link #enchantKey}/{@link #itemKey}
 * 生成的商品键标识，这样两类货架共用同一套限购逻辑。
 */
public final class ShopService {

    /** 商品键前缀：附魔书。 */
    public static final String KEY_ENCHANT = "E:";
    /** 商品键前缀：普通物品。 */
    public static final String KEY_ITEM = "I:";
    /** 商品键前缀：经验值商品（买一份直接加经验点，不是等级）。 */
    public static final String KEY_EXP = "X:";

    private ShopService() {
    }

    /** 操作结果。 */
    public enum Result {
        OK,
        /** 格子还没解锁、商品不存在或是非法键 */
        SLOT_LOCKED,
        /** 今日限购已用完 */
        LIMIT_REACHED,
        /** 账户余额不足 */
        INSUFFICIENT_BALANCE,
        /** 所有格子都已解锁 */
        ALL_UNLOCKED
    }

    /**
     * 升级结果。
     *
     * @param result    结果状态
     * @param enchantId 本次新解锁的附魔（失败时为 {@code null}）
     */
    public record UpgradeOutcome(Result result, String enchantId) {
        public boolean ok() {
            return result == Result.OK;
        }
    }

    /** 附魔书的商品键。 */
    public static String enchantKey(String enchantId) {
        return KEY_ENCHANT + enchantId;
    }

    /** 普通物品的商品键。 */
    public static String itemKey(String itemId) {
        return KEY_ITEM + itemId;
    }

    /** 经验值商品的商品键（{@code X:1000} 表示 1000 点经验）。 */
    public static String expKey(int exp) {
        return KEY_EXP + exp;
    }

    /** 判断一个商品键是不是附魔书。 */
    public static boolean isEnchantKey(String goodKey) {
        return goodKey != null && goodKey.startsWith(KEY_ENCHANT);
    }

    /** 判断一个商品键是不是经验值商品。 */
    public static boolean isExpKey(String goodKey) {
        return goodKey != null && goodKey.startsWith(KEY_EXP);
    }

    /** 去掉前缀后的商品 ID（附魔 ID 或物品 ID）。 */
    public static String idOf(String goodKey) {
        if (goodKey == null || goodKey.length() < 2) {
            return "";
        }
        return goodKey.substring(2);
    }

    // ------------------------------------------------------------------
    // 进度
    // ------------------------------------------------------------------

    /**
     * 首次访问时初始化商店：随机解锁 {@code initialSlots} 个格子并分配附魔。
     * <p>
     * 分配结果会写进存档，之后不再重新随机。
     */
    public static void ensureInitialized(EconomyData data, RegistryAccess access, RandomSource random) {
        ShopBook shop = data.shop();
        if (shop.isInitialized()) {
            return;
        }
        List<String> pool = new ArrayList<>(EnchantCatalog.availableEnchantments(access));
        List<String> unlocked = shop.enchantments();
        unlocked.clear();
        int initial = Math.max(1, Math.min(EEConfig.shopInitialSlots(), pool.size()));
        for (int i = 0; i < initial && !pool.isEmpty(); i++) {
            unlocked.add(pool.remove(random.nextInt(pool.size())));
        }
        shop.setUnlockedSlots(unlocked.size());
        shop.setInitialized(true);
        data.setDirty();
    }

    /** 商店一共能解锁多少个附魔格子（等于内容文件里解锁制附魔的条数）。 */
    public static int maxSlots(RegistryAccess access) {
        return EnchantCatalog.availableEnchantments(access).size();
    }

    /** 解锁下一个格子需要多少绿宝石（已全部解锁时返回 -1）。 */
    public static long nextUpgradeCost(EconomyData data, RegistryAccess access) {
        int unlocked = data.shop().unlockedSlots();
        if (unlocked >= maxSlots(access)) {
            return -1L;
        }
        return upgradeCost(unlocked);
    }

    /**
     * 解锁第 {@code unlocked + 1} 个格子的费用。
     * <p>
     * 每一次升级的费用在配置的 {@code upgradeCostMin ~ upgradeCostMax} 之间随机
     * （默认 512-1280）。这里用「目标格子序号」做种子计算，所以同一级的价格是固定的：
     * 界面上看到的价与真正扣的价永远一致，重启服务器后也不会变。
     */
    public static long upgradeCost(int unlockedSlots) {
        int min = EEConfig.shopUpgradeCostMin();
        int max = Math.max(min, EEConfig.shopUpgradeCostMax());
        int level = Math.max(1, unlockedSlots);
        if (max <= min) {
            return min;
        }
        long hash = level * 0x9E3779B97F4A7C15L;
        hash ^= hash >>> 29;
        hash *= 0xBF58476D1CE4E5B9L;
        hash ^= hash >>> 32;
        return min + Math.floorMod(hash, (long) (max - min + 1));
    }

    // ------------------------------------------------------------------
    // 货架
    // ------------------------------------------------------------------

    /**
     * 一个货架条目。
     *
     * @param key       商品键（{@code E:附魔ID} / {@code I:物品ID} / {@code X:经验点数}）
     * @param group     大分类 ID（内容文件里写的 id）
     * @param sub       子分类 ID
     * @param id        附魔 ID 或物品 ID（经验值商品写经验点数）
     * @param price     售价
     * @param remaining 今日剩余可购次数
     * @param enchant   是不是附魔书
     * @param exp       经验值商品的点数（0 表示不是经验值商品）
     */
    public record Good(String key, String group, String sub, String id, int price, int remaining,
                       boolean enchant, int exp) {
    }

    /** 商店的全部货架内容（解锁制大分类只列已解锁的，常驻大分类全部列出）。 */
    public static List<Good> goods(EconomyData data, RegistryAccess access, java.util.UUID playerId) {
        ShopContent content = EconomyContent.shop();
        List<Good> out = new ArrayList<>();

        List<String> unlocked = data.shop().enchantments();
        for (int i = 0; i < data.shop().unlockedSlots() && i < unlocked.size(); i++) {
            String id = unlocked.get(i);
            ShopContent.Tab tab = content.tabOfEnchant(id);
            if (tab == null) {
                // 内容文件里已经删掉这个附魔：跳过（不影响其它格子）
                continue;
            }
            String key = enchantKey(id);
            out.add(new Good(key, tab.group().idOr("ENCHANTED_BOOK"), tab.sub().idOr(""), id,
                    EnchantCatalog.priceOf(access, id), remainingPurchases(data, playerId, key), true, 0));
        }

        content.plainItems().forEach((itemId, price) -> {
            ShopContent.Tab tab = content.tabOfItem(itemId);
            if (tab == null || !exists(itemId)) {
                return;
            }
            String key = itemKey(itemId);
            out.add(new Good(key, tab.group().idOr("OTHER"), tab.sub().idOr(""), itemId, price,
                    remainingPurchases(data, playerId, key), false, 0));
        });

        // 经验值商品：没有物品形态，买下就直接加到经验条上
        content.expItems().forEach((exp, price) -> {
            ShopContent.Tab tab = content.tabOfExp(exp);
            if (tab == null) {
                return;
            }
            String key = expKey(exp);
            out.add(new Good(key, tab.group().idOr("OTHER"), tab.sub().idOr(""), String.valueOf(exp),
                    price, remainingPurchases(data, playerId, key), false, exp));
        });
        return out;
    }

    private static boolean exists(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) {
            return false;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        return item != null && item != Items.AIR;
    }

    // ------------------------------------------------------------------
    // 购买
    // ------------------------------------------------------------------

    /** 购买一个商品（附魔书 / 普通物品 / 经验值商品）。 */
    public static Result buy(EconomyData data, ServerPlayer player, RegistryAccess access,
                             String goodKey, long day) {
        if (goodKey == null || goodKey.length() < 2) {
            return Result.SLOT_LOCKED;
        }
        boolean enchant = isEnchantKey(goodKey);
        boolean expGood = isExpKey(goodKey);
        String id = idOf(goodKey);
        ShopBook shop = data.shop();

        int price;
        ItemStack stack = ItemStack.EMPTY;
        int expAmount = 0;
        if (enchant) {
            // 必须是已经解锁的格子，而且内容文件里还有它
            if (!shop.enchantments().contains(id) || shop.enchantments().indexOf(id) >= shop.unlockedSlots()
                    || EconomyContent.shop().enchantSpec(id) == null) {
                return Result.SLOT_LOCKED;
            }
            price = EnchantCatalog.priceOf(access, id);
            stack = EnchantCatalog.createMaxLevelBook(access, id);
            if (stack.isEmpty()) {
                return Result.SLOT_LOCKED;
            }
        } else if (expGood) {
            int exp;
            try {
                exp = Integer.parseInt(id.trim());
            } catch (NumberFormatException ex) {
                return Result.SLOT_LOCKED;
            }
            price = EconomyContent.shop().expPrice(exp);
            if (price <= 0) {
                return Result.SLOT_LOCKED;
            }
            expAmount = exp;
        } else {
            Integer configured = EconomyContent.shop().plainItems().get(id);
            if (configured == null) {
                return Result.SLOT_LOCKED;
            }
            price = configured;
            ResourceLocation location = ResourceLocation.tryParse(id);
            Item item = location == null ? null : BuiltInRegistries.ITEM.get(location);
            if (item == null || item == Items.AIR) {
                return Result.SLOT_LOCKED;
            }
            stack = new ItemStack(item);
        }

        int limit = Math.max(1, EEConfig.shopDailyPurchaseLimit());
        if (shop.purchasedCount(player.getUUID(), goodKey) >= limit) {
            return Result.LIMIT_REACHED;
        }
        AccountData account = data.account(player.getUUID());
        if (!account.canAfford(price)) {
            return Result.INSUFFICIENT_BALANCE;
        }
        long balanceBefore = account.balance();
        long debtBefore = account.debt();
        account.addBalance(-price);
        account.pushRecord(TransactionType.SHOP_BUY, -price, balanceBefore, debtBefore,
                player.level().getGameTime());
        if (expAmount > 0) {
            // 经验值商品：直接把经验点加到玩家身上（不是等级）
            player.giveExperiencePoints(expAmount);
        } else {
            AccountService.giveItem(player, stack);
        }
        shop.addPurchase(player.getUUID(), goodKey);
        data.setDirty();
        return Result.OK;
    }

    /**
     * 升级商店（全服共享进度）。
     *
     * @return 结果 + 本次解锁的附魔 ID（界面与广播都要喊出「解锁了什么」）
     */
    public static UpgradeOutcome upgrade(EconomyData data, ServerPlayer player, RegistryAccess access,
                                         RandomSource random) {
        ShopBook shop = data.shop();
        List<String> pool = new ArrayList<>(EnchantCatalog.availableEnchantments(access));
        pool.removeAll(shop.enchantments());
        // 内容文件里删掉过的附魔留在存档里没有意义，这里一并清理，免得池子被占满
        shop.enchantments().removeIf(id -> EconomyContent.shop().enchantSpec(id) == null);
        shop.setUnlockedSlots(Math.min(shop.unlockedSlots(), shop.enchantments().size()));
        if (pool.isEmpty()) {
            return new UpgradeOutcome(Result.ALL_UNLOCKED, null);
        }
        long cost = upgradeCost(shop.unlockedSlots());
        AccountData account = data.account(player.getUUID());
        if (!account.canAfford(cost)) {
            return new UpgradeOutcome(Result.INSUFFICIENT_BALANCE, null);
        }
        String newEnchant = pool.get(random.nextInt(pool.size()));
        long balanceBefore = account.balance();
        long debtBefore = account.debt();
        account.addBalance(-cost);
        account.pushRecord(TransactionType.SHOP_UPGRADE, -cost, balanceBefore, debtBefore,
                player.level().getGameTime());
        shop.enchantments().add(newEnchant);
        shop.setUnlockedSlots(shop.unlockedSlots() + 1);
        data.setDirty();
        Component enchantName = EnchantCatalog.displayName(access, newEnchant);
        Notify.broadcastShop(player.getServer(), "shop.upgraded", player.getGameProfile().getName(),
                enchantName, shop.unlockedSlots());
        return new UpgradeOutcome(Result.OK, newEnchant);
    }

    /** 每日重置限购次数。 */
    public static void resetDaily(EconomyData data, long day) {
        data.shop().resetPurchases();
        data.shop().setPurchaseDay(day);
        data.setDirty();
    }

    /** 某个商品今日剩余可购买次数。 */
    public static int remainingPurchases(EconomyData data, java.util.UUID playerId, String goodKey) {
        int limit = Math.max(1, EEConfig.shopDailyPurchaseLimit());
        return Math.max(0, limit - data.shop().purchasedCount(playerId, goodKey));
    }

    /** 商品键的显示名（附魔书报附魔名，普通商品报物品名，经验值商品报「N 点经验值」）。 */
    public static Component goodName(RegistryAccess access, String goodKey) {
        String id = idOf(goodKey);
        if (isEnchantKey(goodKey)) {
            return EnchantCatalog.displayName(access, id);
        }
        if (isExpKey(goodKey)) {
            return Component.translatable(EEConstants.langKey("screen.vending.exp_goods"), id);
        }
        ResourceLocation location = ResourceLocation.tryParse(id);
        Item item = location == null ? null : BuiltInRegistries.ITEM.get(location);
        return item == null || item == Items.AIR
                ? Component.literal(id) : Component.translatable(item.getDescriptionId());
    }

    /** 子分类 ID 的小写形式（给日志用）。 */
    public static String normalize(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
