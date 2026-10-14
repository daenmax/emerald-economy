package cn.daenx.mc.emeraldeconomy.acquire;

import cn.daenx.mc.emeraldeconomy.config.EEConfig;
import cn.daenx.mc.emeraldeconomy.content.AcquireContent;
import cn.daenx.mc.emeraldeconomy.content.EconomyContent;
import cn.daenx.mc.emeraldeconomy.core.Amounts;
import cn.daenx.mc.emeraldeconomy.core.Notify;
import cn.daenx.mc.emeraldeconomy.data.AccountData;
import cn.daenx.mc.emeraldeconomy.data.EconomyData;
import cn.daenx.mc.emeraldeconomy.data.TransactionType;
import cn.daenx.mc.emeraldeconomy.economy.AccountService;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 自动收购机业务（需求文档 6.3 / 6.4）。
 * <p>
 * 收什么、多少钱、几个算一份，全部由 {@code config/emerald_economy/acquire.json} 决定
 * （见 {@link AcquireContent}）；本类负责把配置翻译成游戏里的物品与结算规则：
 * <ul>
 *     <li>条目可以写具体物品，也可以写<b>物品标签</b>（收一整类，能收原版标签）</li>
 *     <li>{@code batch} 表示「多少个算一份」：鸡蛋 8 个一份，一次出售里每满一份才按 1 个计价，
 *         不足一份的数量不会被吃掉</li>
 *     <li>疯狂星期五逢 N 天开放，随机挑几件按倍率收购；<b>欠款没还清不能参加</b></li>
 * </ul>
 * 收购所得直接进入 ATM 账户，不经过背包，也不收手续费。
 */
public final class AcquireService {

    private AcquireService() {
    }

    /** 收购结果。 */
    public enum Result {
        OK,
        /** 物品不在收购列表中 */
        UNKNOWN_ITEM,
        /** 出售数量非法 */
        INVALID_COUNT,
        /** 背包里没有这么多该物品 */
        INSUFFICIENT_ITEMS,
        /** 还有贷款没还清（不能参加疯狂星期五） */
        HAS_DEBT,
        /** 数量还没凑够一份（例如鸡蛋要 8 个） */
        TOO_FEW_FOR_BATCH
    }

    /**
     * 一条收购条目。
     *
     * @param itemId 物品注册名
     * @param price  单价（每「一份」的价格）
     * @param friday 是否来自疯狂星期五
     * @param batch  一份等于几个（1 表示按个收）
     */
    public record Entry(String itemId, long price, boolean friday, int batch) {
    }

    // ------------------------------------------------------------------
    // 内容查询
    // ------------------------------------------------------------------

    /** 当前配置里的全部收购分类（顺序就是文件里的顺序）。 */
    public static List<AcquireContent.Category> categories() {
        return EconomyContent.acquire().categoryList();
    }

    /** 有没有疯狂星期五这一段配置。 */
    public static boolean hasFriday() {
        return EconomyContent.acquire().friday() != null;
    }

    /**
     * 展开一个分类：把 {@code tag} 条目展开成具体物品，并丢掉游戏里不存在的物品。
     *
     * @param categoryId 分类 ID（{@link AcquireContent#FRIDAY_ID} 表示疯狂星期五）
     */
    public static List<Entry> entries(EconomyData data, String categoryId) {
        if (AcquireContent.FRIDAY_ID.equalsIgnoreCase(categoryId)) {
            return fridayEntries(data);
        }
        List<Entry> out = new ArrayList<>();
        AcquireContent.Category category = EconomyContent.acquire().categoryById(categoryId);
        if (category == null) {
            return out;
        }
        for (AcquireContent.Item item : category.itemList()) {
            int batch = item.batchOrOne();
            if (item.isTag()) {
                for (String id : tagItems(item.tag())) {
                    out.add(new Entry(id, item.priceOr(0L), false, batch));
                }
            } else if (exists(item.raw())) {
                out.add(new Entry(item.raw(), item.priceOr(0L), false, batch));
            }
        }
        return out;
    }

    /** 疯狂星期五当日的重金收购条目。 */
    public static List<Entry> fridayEntries(EconomyData data) {
        List<Entry> out = new ArrayList<>();
        AcquireBook book = data.acquire();
        AcquireContent.Friday friday = EconomyContent.acquire().friday();
        if (friday == null || !book.isFridayActive()) {
            return out;
        }
        Map<String, Integer> basePrices = friday.basePriceMap();
        for (String id : book.fridayItems()) {
            if (!exists(id)) {
                continue;
            }
            long base = basePrices.getOrDefault(id, 100);
            long price = Amounts.floorAtLeastOne(base * book.multiplierOf(id));
            out.add(new Entry(id, price, true, 1));
        }
        return out;
    }

    /** 物品 ID -> 单价（所有常驻分类里第一个命中的）。 */
    public static Map<String, Long> priceTable(EconomyData data) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (AcquireContent.Category category : categories()) {
            for (Entry entry : entries(data, category.idOr(""))) {
                out.putIfAbsent(entry.itemId(), entry.price());
            }
        }
        return out;
    }

    /** 物品 ID -> 批次（多个分类里都写着同一个物品时取第一个）。 */
    public static Map<String, Integer> batchTable(EconomyData data) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (AcquireContent.Category category : categories()) {
            for (Entry entry : entries(data, category.idOr(""))) {
                out.putIfAbsent(entry.itemId(), entry.batch());
            }
        }
        return out;
    }

    /** 查询某个物品的收购单价（优先匹配疯狂星期五；不在表里返回 0）。 */
    public static long priceOf(EconomyData data, String itemId) {
        if (itemId == null) {
            return 0L;
        }
        String key = itemId.toLowerCase(Locale.ROOT);
        for (Entry entry : fridayEntries(data)) {
            if (entry.itemId().equals(key)) {
                return entry.price();
            }
        }
        return priceTable(data).getOrDefault(key, 0L);
    }

    /** 某个物品的批次大小（不在表里按 1 算）。 */
    public static int batchOf(EconomyData data, String itemId) {
        if (itemId == null) {
            return 1;
        }
        return batchTable(data).getOrDefault(itemId.toLowerCase(Locale.ROOT), 1);
    }

    /** 某个物品是否属于「疯狂星期五」当日的重金收购名单。 */
    public static boolean isFridayItem(EconomyData data, String itemId) {
        if (itemId == null) {
            return false;
        }
        String key = itemId.toLowerCase(Locale.ROOT);
        for (Entry entry : fridayEntries(data)) {
            if (entry.itemId().equals(key)) {
                return true;
            }
        }
        return false;
    }

    /** 标签里的全部物品 ID（标签不存在时返回空表）。 */
    private static List<String> tagItems(String tagId) {
        List<String> out = new ArrayList<>();
        ResourceLocation id = ResourceLocation.tryParse(tagId);
        if (id == null) {
            return out;
        }
        TagKey<Item> tag = TagKey.create(Registries.ITEM, id);
        for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
            holder.unwrapKey().ifPresent(key -> out.add(key.location().toString()));
        }
        return out;
    }

    /** 物品 ID 在游戏里是否存在。 */
    private static boolean exists(String itemId) {
        Item item = resolveItem(itemId);
        return item != null && item != Items.AIR;
    }

    // ------------------------------------------------------------------
    // 出售
    // ------------------------------------------------------------------

    /**
     * 出售物品：从背包扣除物品，把绿宝石直接存入账户。
     * <p>
     * 配了批次的物品（例如鸡蛋 8 个一份）按「满一份才算 1 个」计价：
     * 卖 20 个鸡蛋按 2 份算、只扣 16 个、给 2 绿宝石，剩下 4 个留在背包里。
     *
     * @param itemId 物品注册名
     * @param count  出售数量（实际扣除的是「份数 × 每份个数」）
     */
    public static Result sell(EconomyData data, ServerPlayer player, String itemId, long count) {
        if (count < 1L) {
            return Result.INVALID_COUNT;
        }
        Item item = resolveItem(itemId);
        if (item == null || item == Items.AIR) {
            return Result.UNKNOWN_ITEM;
        }
        long price = priceOf(data, itemId);
        if (price <= 0L) {
            return Result.UNKNOWN_ITEM;
        }
        AccountData account = data.account(player.getUUID());
        boolean friday = isFridayItem(data, itemId);
        // 欠债不许赌，也不许参加疯狂星期五
        if (account.debt() > 0L && friday) {
            return Result.HAS_DEBT;
        }
        int batch = friday ? 1 : batchOf(data, itemId);
        long units = count / batch;
        if (units < 1L) {
            return Result.TOO_FEW_FOR_BATCH;
        }
        long consumed = units * batch;
        if (AccountService.countItems(player, item) < consumed) {
            return Result.INSUFFICIENT_ITEMS;
        }
        if (!AccountService.removeItems(player, item, consumed)) {
            return Result.INSUFFICIENT_ITEMS;
        }
        long total = price * units;
        long balanceBefore = account.balance();
        long debtBefore = account.debt();
        account.addBalance(total);
        account.pushRecord(TransactionType.ACQUIRE_SELL, total, balanceBefore, debtBefore,
                player.level().getGameTime());
        data.setDirty();
        return Result.OK;
    }

    private static Item resolveItem(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) {
            return null;
        }
        return BuiltInRegistries.ITEM.get(id);
    }

    /**
     * 物品的显示名，用于聊天栏提示。
     * <p>
     * 返回可翻译组件，客户端会按自己的语言显示；取不到物品时退回注册名。
     */
    public static Component displayName(String itemId) {
        Item item = resolveItem(itemId);
        return item == null ? Component.literal(itemId) : Component.translatable(item.getDescriptionId());
    }

    /** 物品图标用的注册表（客户端画界面时也要用）。 */
    public static Registry<Item> itemRegistry() {
        return BuiltInRegistries.ITEM;
    }

    // ------------------------------------------------------------------
    // 疯狂星期五
    // ------------------------------------------------------------------

    /**
     * 确保疯狂星期五状态与当前游戏日一致。
     * <p>
     * 「逢 N」开放：{@code day % interval == 0 && day > 0}。状态一旦按某一天生成就固定下来，
     * 重启服务器不会重新抽选，第二天自动关闭。
     *
     * @return 如果本次调用刚刚开始了新的一天，返回 {@code true}（用于广播）
     */
    public static boolean ensureFridayState(EconomyData data, long day, RandomSource random) {
        AcquireBook book = data.acquire();
        if (book.fridayDay() == day) {
            return false;
        }
        int interval = Math.max(1, EEConfig.fridayIntervalDays());
        boolean isFriday = day > 0L && day % interval == 0 && hasFriday();
        book.setFridayDay(day);
        if (!isFriday) {
            book.setFridayActive(false);
            book.fridayItems().clear();
            book.fridayMultipliers().clear();
            data.setDirty();
            return false;
        }
        rollFridayItems(data, random);
        data.setDirty();
        return true;
    }

    /** 随机挑选当日的重金收购物品并决定倍率。 */
    private static void rollFridayItems(EconomyData data, RandomSource random) {
        AcquireBook book = data.acquire();
        AcquireContent.Friday friday = EconomyContent.acquire().friday();
        List<String> pool = new ArrayList<>();
        if (friday != null) {
            for (String raw : friday.poolList()) {
                if (raw == null) {
                    continue;
                }
                String id = raw.trim().toLowerCase(Locale.ROOT);
                if (!id.isEmpty() && !id.startsWith("#") && exists(id)) {
                    pool.add(id);
                }
            }
        }
        book.fridayItems().clear();
        book.fridayMultipliers().clear();
        int pick = Math.min(Math.max(1, EEConfig.fridayPickCount()), pool.size());
        for (int i = 0; i < pick && !pool.isEmpty(); i++) {
            String id = pool.remove(random.nextInt(pool.size()));
            book.fridayItems().add(id);
        }
        double lo = EEConfig.fridayMultiplierMin();
        double hi = Math.max(lo, EEConfig.fridayMultiplierMax());
        for (String id : book.fridayItems()) {
            book.fridayMultipliers().put(id, lo + random.nextDouble() * (hi - lo));
        }
        book.setFridayActive(true);
    }

    /** 广播疯狂星期五开始（需求文档 6.4）。 */
    public static void announceFriday(MinecraftServer server, EconomyData data) {
        List<Entry> entries = fridayEntries(data);
        if (entries.isEmpty() || server == null) {
            return;
        }
        Component names = Component.empty();
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                names = names.copy().append(Component.literal("、"));
            }
            Item item = resolveItem(entries.get(i).itemId());
            names = names.copy().append(item == null
                    ? Component.literal(entries.get(i).itemId())
                    : Component.translatable(item.getDescriptionId()));
        }
        Notify.broadcastAcquire(server, "acquire.friday_start", names);
    }
}
