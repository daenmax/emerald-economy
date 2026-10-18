package cn.daenx.mc.emeraldeconomy.acquire;

import cn.daenx.mc.emeraldeconomy.config.EEConfig;
import cn.daenx.mc.emeraldeconomy.content.AcquireContent;
import cn.daenx.mc.emeraldeconomy.content.EconomyContent;
import cn.daenx.mc.emeraldeconomy.core.Amounts;
import cn.daenx.mc.emeraldeconomy.core.EEConstants;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 自动收购机业务（需求文档 6.3 / 6.4）。
 * <p>
 * 收什么、多少钱、几个算一份，全部由 {@code config/emerald_economy/acquire.json} 决定
 * （见 {@link AcquireContent}）；本类负责把配置翻译成游戏里的物品与结算规则：
 * <ul>
 *     <li>条目可以写具体物品，也可以写<b>物品标签</b>（收一整类，能收原版标签）</li>
 *     <li><b>标签条目在界面上只占一行</b>：玩家卖背包里该类中的任意一种都行
 *         （唱片、树苗、花、蘑菇这种「种类多、价格又一样」的东西因此不再刷屏）</li>
 *     <li>{@code batch} 表示「多少个算一份」：鸡蛋 8 个一份，一次出售里每满一份才按 1 个计价，
 *         不足一份的数量不会被吃掉</li>
 *     <li>疯狂星期五逢 N 天开放，随机挑几件按倍率收购；池子支持 {@code #标签} 与
 *         {@code *_spawn_egg} 通配，并能用 {@code poolMaxPicks} 限制「最多挑几件」
 *         （默认就是：头颅最多 1 个、刷怪蛋最多 1 个）；<b>欠款没还清不能参加</b></li>
 * </ul>
 * 收购所得直接进入 ATM 账户，不经过背包，也不收手续费。
 */
public final class AcquireService {

    /** 标签行的标识前缀（界面上与网络包里用它表示「卖这一类里的任意一种」）。 */
    public static final String TAG_PREFIX = "#";

    /** 疯狂星期五池子里没有被单独配价的物品使用的兜底基准价。 */
    private static final long DEFAULT_FRIDAY_BASE_PRICE = 100L;

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
     * 一条收购条目（也就是界面上的「一行」）。
     *
     * @param itemId 物品注册名；标签行是 {@code #标签名}
     * @param price  单价（每「一份」的价格）
     * @param friday 是否来自疯狂星期五
     * @param batch  一份等于几个（1 表示按个收）
     * @param tag    是不是「整类收购」的标签行
     * @param lang   显示名翻译键（可为空）
     * @param name   显示名原样文本（可为空）
     * @param filter 额外的匹配规则（可为空，目前是 {@code treasure_map}）
     */
    public record Entry(String itemId, long price, boolean friday, int batch, boolean tag,
                        String lang, String name, String filter) {
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
     * 展开一个分类：<b>标签条目保留成一行</b>，具体物品照原样，并丢掉游戏里不存在的物品。
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
                // 标签行：整类一起卖，界面上只占一行；标签当前一个物品都没有时跳过
                if (!tagMemberIds(item.tag()).isEmpty()) {
                    out.add(new Entry(TAG_PREFIX + item.tag().trim().toLowerCase(Locale.ROOT),
                            item.priceOr(0L), false, batch, true, item.langOrNull(), item.nameOrNull(),
                            item.filterOrNull()));
                }
            } else if (exists(item.raw())) {
                out.add(new Entry(item.raw(), item.priceOr(0L), false, batch, false,
                        item.langOrNull(), item.nameOrNull(), item.filterOrNull()));
            }
        }
        return out;
    }

    /** 疯狂星期五当日的重金收购条目（当天挑中的东西，价格 = 基准价 × 当日倍率）。 */
    public static List<Entry> fridayEntries(EconomyData data) {
        List<Entry> out = new ArrayList<>();
        AcquireContent.Friday friday = EconomyContent.acquire().friday();
        // data 为 null 表示「只看配置、不看存档」（自检与静态查询会这么调）
        if (data == null || friday == null || !data.acquire().isFridayActive()) {
            return out;
        }
        AcquireBook book = data.acquire();
        Map<String, Integer> basePrices = friday.basePriceMap();
        for (String id : book.fridayItems()) {
            if (!exists(id)) {
                continue;
            }
            long base = book.basePriceOf(id, basePrices.getOrDefault(id, (int) DEFAULT_FRIDAY_BASE_PRICE));
            long price = Amounts.floorAtLeastOne(base * book.multiplierOf(id));
            out.add(new Entry(id, price, true, 1, false, null, null, null));
        }
        return out;
    }

    /**
     * 全部常驻分类的「具体物品 → {价格, 批次}」表（标签会被展开成具体物品）。
     * <p>
     * 界面展示用的是 {@link #entries}（标签占一行），这张表只用于「拿着一个具体物品 ID 查价」。
     */
    public static Map<String, long[]> itemTable(EconomyData data) {
        Map<String, long[]> out = new LinkedHashMap<>();
        for (AcquireContent.Category category : categories()) {
            for (AcquireContent.Item item : category.itemList()) {
                int batch = item.batchOrOne();
                if (item.isTag()) {
                    for (String id : tagMemberIds(item.tag())) {
                        out.putIfAbsent(id, new long[]{item.priceOr(0L), batch});
                    }
                } else if (exists(item.raw())) {
                    out.putIfAbsent(item.raw(), new long[]{item.priceOr(0L), batch});
                }
            }
        }
        return out;
    }

    /** 标签行（{@code #标签名}）→ {价格, 批次}。 */
    public static Map<String, long[]> tagRowTable(EconomyData data) {
        Map<String, long[]> out = new LinkedHashMap<>();
        for (AcquireContent.Category category : categories()) {
            for (AcquireContent.Item item : category.itemList()) {
                if (item.isTag() && !tagMemberIds(item.tag()).isEmpty()) {
                    out.putIfAbsent(TAG_PREFIX + item.tag().trim().toLowerCase(Locale.ROOT),
                            new long[]{item.priceOr(0L), item.batchOrOne()});
                }
            }
        }
        return out;
    }

    /** 查询某个物品（或标签行）的收购单价（优先匹配疯狂星期五；不在表里返回 0）。 */
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
        if (key.startsWith(TAG_PREFIX)) {
            long[] row = tagRowTable(data).get(key);
            return row == null ? 0L : row[0];
        }
        return itemTable(data).getOrDefault(key, new long[]{0L, 1L})[0];
    }

    /** 某个物品（或标签行）的批次大小（不在表里按 1 算）。 */
    public static int batchOf(EconomyData data, String itemId) {
        if (itemId == null) {
            return 1;
        }
        String key = itemId.toLowerCase(Locale.ROOT);
        if (key.startsWith(TAG_PREFIX)) {
            long[] row = tagRowTable(data).get(key);
            return row == null ? 1 : (int) Math.max(1L, row[1]);
        }
        return (int) Math.max(1L, itemTable(data).getOrDefault(key, new long[]{0L, 1L})[1]);
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

    /**
     * 某个物品行配置的额外过滤规则（没有配置时返回 {@code null}）。
     * <p>
     * 目前只有「藏宝图」用它：物品类型是 {@code minecraft:filled_map}，
     * 但普通已探索地图不能卖，只有带结构标记（林地府邸 / 海底神殿 / 埋藏的宝藏等）的才算藏宝图。
     */
    public static String filterOf(EconomyData data, String itemId) {
        if (itemId == null) {
            return null;
        }
        String key = itemId.toLowerCase(Locale.ROOT);
        for (AcquireContent.Category category : categories()) {
            for (AcquireContent.Item item : category.itemList()) {
                if (!item.isTag() && item.raw().equals(key) && item.filterOrNull() != null) {
                    return item.filterOrNull();
                }
            }
        }
        return null;
    }

    /** 这一行是不是「只收藏宝图」。 */
    public static boolean isTreasureMapRow(EconomyData data, String itemId) {
        return AcquireContent.Item.FILTER_TREASURE_MAP.equals(filterOf(data, itemId));
    }

    /**
     * 判断一张已填写的地图是不是藏宝图。
     * <p>
     * 1.21.1 里藏宝图没有独立的物品 ID，都是 {@code minecraft:filled_map}，
     * 区别在它保存的地图数据上：藏宝图会带林地府邸 / 海底神殿 / 红色 X（埋藏的宝藏）
     * 等结构标记。普通玩家自己合成、自己探索的地图只有玩家指针或旗帜标记，不会被收。
     */
    public static boolean isTreasureMap(ItemStack stack, Level level) {
        if (stack == null || stack.isEmpty() || level == null) {
            return false;
        }
        MapItemSavedData mapData = MapItem.getSavedData(stack, level);
        if (mapData == null) {
            return false;
        }
        for (MapDecoration decoration : mapData.getDecorations()) {
            // Holder.is(Holder) 在 1.21.1 已标记过时，直接比较注册对象本体即可
            if (decoration.type().value() == MapDecorationTypes.WOODLAND_MANSION.value()
                    || decoration.type().value() == MapDecorationTypes.OCEAN_MONUMENT.value()
                    || decoration.type().value() == MapDecorationTypes.RED_X.value()
                    || decoration.type().value() == MapDecorationTypes.TRIAL_CHAMBERS.value()) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 标签 / 物品解析
    // ------------------------------------------------------------------

    /** 标签里的全部物品 ID（标签不存在时返回空表）。 */
    public static List<String> tagMemberIds(String tagId) {
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

    /** 标签里的全部物品对象（标签不存在时返回空表）。 */
    private static List<Item> tagMemberItems(String tagId) {
        List<Item> out = new ArrayList<>();
        for (String id : tagMemberIds(tagId)) {
            Item item = resolveItem(id);
            if (item != null && item != Items.AIR) {
                out.add(item);
            }
        }
        return out;
    }

    /** 物品 ID 在游戏里是否存在。 */
    private static boolean exists(String itemId) {
        Item item = resolveItem(itemId);
        return item != null && item != Items.AIR;
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
        if (itemId == null) {
            return Component.literal("");
        }
        String key = itemId.toLowerCase(Locale.ROOT);
        // 先看配置里这一行有没有自定义名字（标签行、藏宝图、不祥旗帜这类尤其需要）
        for (AcquireContent.Category category : categories()) {
            for (AcquireContent.Item item : category.itemList()) {
                String rowId = item.isTag()
                        ? TAG_PREFIX + item.tag().trim().toLowerCase(Locale.ROOT)
                        : item.raw();
                if (!rowId.equals(key)) {
                    continue;
                }
                if (item.langOrNull() != null) {
                    return Component.translatable(EEConstants.langKey(item.langOrNull()));
                }
                if (item.nameOrNull() != null) {
                    return Component.literal(item.nameOrNull());
                }
            }
        }
        if (itemId.startsWith(TAG_PREFIX)) {
            return Component.literal(itemId);
        }
        Item item = resolveItem(itemId);
        return item == null ? Component.literal(itemId) : Component.translatable(item.getDescriptionId());
    }

    /** 物品图标用的注册表（客户端画界面时也要用）。 */
    public static Registry<Item> itemRegistry() {
        return BuiltInRegistries.ITEM;
    }

    // ------------------------------------------------------------------
    // 出售
    // ------------------------------------------------------------------

    /**
     * 出售物品：从背包扣除物品，把绿宝石直接存入账户。
     * <p>
     * 配了批次的物品（例如鸡蛋 8 个一份）按「满一份才算 1 个」计价：
     * 卖 20 个鸡蛋按 2 份算、只扣 16 个、给 2 绿宝石，剩下 4 个留在背包里。
     * <p>
     * {@code itemId} 写成 {@code #标签名} 时表示「卖这一类里的任意一种」：
     * 数量按背包里该类的总数算，扣除时从背包里逐格拿（不要求是同一种物品）。
     *
     * @param itemId 物品注册名，或 {@code #标签名}
     * @param count  出售数量（实际扣除的是「份数 × 每份个数」）
     */
    public static Result sell(EconomyData data, ServerPlayer player, String itemId, long count) {
        if (count < 1L) {
            return Result.INVALID_COUNT;
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

        if (isTreasureMapRow(data, itemId)) {
            // 藏宝图：只认带结构标记的已填写地图，普通已探索地图不算
            if (countTreasureMaps(player) < consumed) {
                return Result.INSUFFICIENT_ITEMS;
            }
            if (!removeTreasureMaps(player, consumed)) {
                return Result.INSUFFICIENT_ITEMS;
            }
        } else if (itemId != null && itemId.startsWith(TAG_PREFIX)) {
            List<Item> members = tagMemberItems(itemId.substring(1));
            if (members.isEmpty()) {
                return Result.UNKNOWN_ITEM;
            }
            if (AccountService.countItems(player, members) < consumed) {
                return Result.INSUFFICIENT_ITEMS;
            }
            if (!AccountService.removeItems(player, members, consumed)) {
                return Result.INSUFFICIENT_ITEMS;
            }
        } else {
            Item item = resolveItem(itemId);
            if (item == null || item == Items.AIR) {
                return Result.UNKNOWN_ITEM;
            }
            if (AccountService.countItems(player, item) < consumed) {
                return Result.INSUFFICIENT_ITEMS;
            }
            if (!AccountService.removeItems(player, item, consumed)) {
                return Result.INSUFFICIENT_ITEMS;
            }
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

    /** 背包（0~35 格）里藏宝图的总数量。 */
    private static long countTreasureMaps(ServerPlayer player) {
        long total = 0L;
        var inventory = player.getInventory();
        int slots = Math.min(net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE,
                inventory.getContainerSize());
        for (int slot = 0; slot < slots; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isTreasureMap(stack, player.level())) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * 从背包里扣除指定数量的藏宝图。
     * <p>
     * 只动真正带结构标记的地图，普通地图原样保留。
     */
    private static boolean removeTreasureMaps(ServerPlayer player, long amount) {
        if (amount <= 0L) {
            return true;
        }
        if (countTreasureMaps(player) < amount) {
            return false;
        }
        var inventory = player.getInventory();
        int slots = Math.min(net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE,
                inventory.getContainerSize());
        long remaining = amount;
        for (int slot = 0; slot < slots && remaining > 0L; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!isTreasureMap(stack, player.level())) {
                continue;
            }
            int take = (int) Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
            if (stack.isEmpty()) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
        inventory.setChanged();
        return true;
    }

    // ------------------------------------------------------------------
    // 疯狂星期五
    // ------------------------------------------------------------------

    /** 池子里的一个条目（具体物品 / 标签 / 通配）以及它的挑选上限。 */
    private record PoolEntry(String key, int maxPick, List<String> members) {
    }

    /**
     * 池子条目默认的挑选上限：<b>每天最多 1 件</b>。
     * <p>
     * 也就是说，一天挑 3 件时：同一个具体物品不会出现两次（候选本来就做了去重），
     * 同一个「整类」条目（例如所有头颅、所有刷怪蛋）也只会贡献 1 件。
     * 如果以后真想让某一类一天出现多件，在 {@code friday.poolMaxPicks} 里把它显式调大即可。
     */
    private static final int DEFAULT_POOL_MAX_PICK = 1;

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
            book.fridayBasePrices().clear();
            data.setDirty();
            return false;
        }
        rollFridayItems(data, random);
        data.setDirty();
        return true;
    }

    /**
     * 随机挑选当日的重金收购物品并决定倍率。
     * <p>
     * 池子里可以有具体物品、{@code #标签}（例如「所有怪物的头颅」）与通配（例如
     * {@code *_spawn_egg} 表示所有刷怪蛋）。每个池子条目<b>默认每天最多贡献 1 件</b>：
     * 同一件物品不会出现两次，同一个「整类」条目也不会一次挑出两件（例如不会同时出现两个下界之星、
     * 两种头颅），具体挑中类里的哪一个仍然是随机的。
     * <p>
     * 基准价选中时就算好并写进存档，这样当天中途改配置也不会把已公布的价格改掉。
     */
    private static void rollFridayItems(EconomyData data, RandomSource random) {
        AcquireBook book = data.acquire();
        AcquireContent.Friday friday = EconomyContent.acquire().friday();
        book.fridayItems().clear();
        book.fridayMultipliers().clear();
        book.fridayBasePrices().clear();
        if (friday == null) {
            return;
        }

        List<PoolEntry> pool = resolvePool(friday);
        // 一个物品可能同时属于「具体物品条目」和「标签条目」，这里记下它的全部归属，
        // 判断上限时按所有归属一起算（否则「龙首」既在头颅标签里、又被单独列出来时会重复计数）
        Map<String, List<PoolEntry>> owners = new LinkedHashMap<>();
        List<String> candidates = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (PoolEntry entry : pool) {
            for (String id : entry.members()) {
                owners.computeIfAbsent(id, key -> new ArrayList<>()).add(entry);
                if (seen.add(id)) {
                    candidates.add(id);
                }
            }
        }
        // 洗牌用 RandomSource 自己实现（Collections.shuffle 只接受 java.util.Random）
        for (int i = candidates.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Collections.swap(candidates, i, j);
        }

        int pick = Math.min(Math.max(1, EEConfig.fridayPickCount()), candidates.size());
        Map<String, Integer> pickedPerEntry = new HashMap<>();
        for (String id : candidates) {
            if (book.fridayItems().size() >= pick) {
                break;
            }
            List<PoolEntry> itemOwners = owners.getOrDefault(id, List.of());
            if (!withinLimits(itemOwners, pickedPerEntry)) {
                continue;
            }
            book.fridayItems().add(id);
            for (PoolEntry entry : itemOwners) {
                pickedPerEntry.merge(entry.key(), 1, Integer::sum);
            }
        }

        Map<String, Integer> basePrices = friday.basePriceMap();
        double lo = EEConfig.fridayMultiplierMin();
        double hi = Math.max(lo, EEConfig.fridayMultiplierMax());
        for (String id : book.fridayItems()) {
            long base = basePriceFor(id, owners.getOrDefault(id, List.of()), basePrices);
            book.fridayBasePrices().put(id, (int) base);
            book.fridayMultipliers().put(id, lo + random.nextDouble() * (hi - lo));
        }
        book.setFridayActive(true);
    }

    /** 挑中这个物品会不会超出它所属池子条目的上限。 */
    private static boolean withinLimits(List<PoolEntry> owners, Map<String, Integer> pickedPerEntry) {
        for (PoolEntry entry : owners) {
            if (pickedPerEntry.getOrDefault(entry.key(), 0) + 1 > entry.maxPick()) {
                return false;
            }
        }
        return true;
    }

    /** 解析配置里的池子条目（具体物品 / {@code #标签} / {@code *通配}）。 */
    private static List<PoolEntry> resolvePool(AcquireContent.Friday friday) {
        List<PoolEntry> out = new ArrayList<>();
        Map<String, Integer> limits = friday.poolMaxPickMap();
        for (String raw : friday.poolList()) {
            if (raw == null) {
                continue;
            }
            String key = raw.trim().toLowerCase(Locale.ROOT);
            if (key.isEmpty()) {
                continue;
            }
            List<String> members = new ArrayList<>();
            if (key.startsWith(TAG_PREFIX)) {
                members.addAll(tagMemberIds(key.substring(1)));
            } else if (key.contains("*")) {
                members.addAll(wildcardMemberIds(key));
            } else if (exists(key)) {
                members.add(key);
            }
            if (members.isEmpty()) {
                continue;
            }
            out.add(new PoolEntry(key,
                    Math.max(1, limits.getOrDefault(key, DEFAULT_POOL_MAX_PICK)), members));
        }
        return out;
    }

    /**
     * 通配匹配物品 ID：{@code *_spawn_egg} 表示「所有刷怪蛋」。
     * <p>
     * 用通配而不是把几十个刷怪蛋一个个写出来，是为了以后原版加了新怪物也不用改配置。
     */
    private static List<String> wildcardMemberIds(String pattern) {
        List<String> out = new ArrayList<>();
        boolean prefixWildcard = pattern.startsWith("*");
        boolean suffixWildcard = pattern.endsWith("*");
        String core = pattern.replace("*", "");
        if (core.isEmpty()) {
            return out;
        }
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            String value = id.toString();
            boolean match;
            if (prefixWildcard && suffixWildcard) {
                match = value.contains(core);
            } else if (prefixWildcard) {
                match = value.endsWith(core);
            } else if (suffixWildcard) {
                match = value.startsWith(core);
            } else {
                match = value.equals(core);
            }
            if (match && exists(value)) {
                out.add(value);
            }
        }
        return out;
    }

    /** 一个物品的疯狂星期五基准价：先看具体物品，再看它所属的池子条目，最后用兜底价。 */
    private static long basePriceFor(String itemId, List<PoolEntry> owners, Map<String, Integer> basePrices) {
        Integer exact = basePrices.get(itemId.toLowerCase(Locale.ROOT));
        if (exact != null) {
            return Math.max(1, exact);
        }
        for (PoolEntry entry : owners) {
            Integer byEntry = basePrices.get(entry.key());
            if (byEntry != null) {
                return Math.max(1, byEntry);
            }
        }
        return DEFAULT_FRIDAY_BASE_PRICE;
    }

    /** 广播疯狂星期五开始（需求文档 6.4：广播里要带上价格）。 */
    public static void announceFriday(MinecraftServer server, EconomyData data) {
        List<Entry> entries = fridayEntries(data);
        if (entries.isEmpty() || server == null) {
            return;
        }
        Component list = Component.empty();
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (i > 0) {
                list = list.copy().append(Component.literal("、"));
            }
            Item item = resolveItem(entry.itemId());
            Component name = item == null
                    ? Component.literal(entry.itemId())
                    : Component.translatable(item.getDescriptionId());
            // 「物品 ×倍率 = 单价」都写出来，玩家一眼就能看到今天哪件最值钱
            list = list.copy().append(name)
                    .append(Component.literal(" " + entry.price() + "/个"));
        }
        Notify.broadcastAcquire(server, "acquire.friday_start", list);
    }
}
