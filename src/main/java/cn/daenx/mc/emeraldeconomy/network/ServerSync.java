package cn.daenx.mc.emeraldeconomy.network;

import cn.daenx.mc.emeraldeconomy.acquire.AcquireService;
import cn.daenx.mc.emeraldeconomy.acquire.EnchantRecycleService;
import cn.daenx.mc.emeraldeconomy.content.AcquireContent;
import cn.daenx.mc.emeraldeconomy.content.EconomyContent;
import cn.daenx.mc.emeraldeconomy.content.ShopContent;
import cn.daenx.mc.emeraldeconomy.config.EEConfig;
import cn.daenx.mc.emeraldeconomy.core.DaySettlement;
import cn.daenx.mc.emeraldeconomy.data.AccountData;
import cn.daenx.mc.emeraldeconomy.data.EconomyData;
import cn.daenx.mc.emeraldeconomy.data.TransactionRecord;
import cn.daenx.mc.emeraldeconomy.lottery.BetRecord;
import cn.daenx.mc.emeraldeconomy.lottery.LotteryBook;
import cn.daenx.mc.emeraldeconomy.lottery.LotteryType;
import cn.daenx.mc.emeraldeconomy.market.FundState;
import cn.daenx.mc.emeraldeconomy.market.FundType;
import cn.daenx.mc.emeraldeconomy.market.Holding;
import cn.daenx.mc.emeraldeconomy.market.NewsEntry;
import cn.daenx.mc.emeraldeconomy.network.payload.ServerPayloads;
import cn.daenx.mc.emeraldeconomy.shop.ShopService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 服务端 → 客户端的「快照」组装与发送。
 * <p>
 * 需求文档 3.4.3 要求「打开界面时主动请求一次最新数据，平时只广播最新价格」，
 * 因此这里的每个方法都只发给指定的玩家，避免无关数据占用带宽。
 */
public final class ServerSync {

    /**
     * 每个玩家当前打开的机器界面。
     * <p>
     * 需求文档 3.4.3 要求「价格更新后要把最新价格同步给客户端」，
     * 而抽奖开奖、限购重置这些每日结算结果也要能刷新到已经打开的界面上，
     * 所以服务端需要知道该给谁推哪一种快照。只记在内存里，退出即清理。
     */
    private static final Map<UUID, ScreenType> OPEN_SCREENS = new HashMap<>();

    private ServerSync() {
    }

    /** 记录玩家打开了哪个界面（客户端每次请求数据时调用）。 */
    public static void rememberOpenScreen(ServerPlayer player, ScreenType type) {
        OPEN_SCREENS.put(player.getUUID(), type);
    }

    /** 玩家断开连接时清理。 */
    public static void forget(ServerPlayer player) {
        OPEN_SCREENS.remove(player.getUUID());
    }

    /**
     * 给所有「正开着本模组界面」的玩家推送最新快照。
     * <p>
     * 在每日结算与每次价格更新后调用，保证界面上的余额、价格、开奖结果、
     * 剩余限购次数都是最新的。
     */
    public static void refreshOpenScreens(MinecraftServer server) {
        if (OPEN_SCREENS.isEmpty()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ScreenType type = OPEN_SCREENS.get(player.getUUID());
            if (type != null) {
                sendFor(player, type);
            }
        }
    }

    /** 按界面类型发送对应快照。 */
    public static void sendFor(ServerPlayer player, ScreenType type) {
        switch (type) {
            case ATM -> sendAccount(player);
            case RANKING -> sendRanking(player);
            case EXCHANGE -> sendMarket(player);
            case LOTTERY -> sendLottery(player);
            case VENDING -> sendShop(player);
            case ACQUISITION -> sendAcquire(player);
        }
    }

    /**
     * 账户快照。
     * <p>
     * 排行榜数据默认不带（ATM 的存取款页用不到），只有玩家正开着排行榜时才计算并下发，
     * 避免每次存取款都把所有账户排序一遍。
     */
    public static void sendAccount(ServerPlayer player) {
        boolean withRanking = OPEN_SCREENS.get(player.getUUID()) == ScreenType.RANKING;
        sendAccount(player, withRanking);
    }

    /** 排行榜快照：每次打开排行榜页面都会实时重算一次。 */
    public static void sendRanking(ServerPlayer player) {
        sendAccount(player, true);
    }

    private static void sendAccount(ServerPlayer player, boolean withRanking) {
        EconomyData data = dataOf(player);
        if (data == null) {
            return;
        }
        AccountData account = data.account(player.getUUID());
        long today = currentDay(player);
        List<ServerPayloads.RankingEntry> ranking = withRanking
                ? buildRanking(player.getServer(), data, false) : List.of();
        List<ServerPayloads.RankingEntry> experienceRanking = withRanking
                ? buildRanking(player.getServer(), data, true) : List.of();
        List<ServerPayloads.TxLine> transactions = new ArrayList<>();
        for (TransactionRecord record : account.records()) {
            transactions.add(new ServerPayloads.TxLine(record.type().name(), record.amount(),
                    record.balanceBefore(), record.balanceAfter(), record.debtBefore(), record.debtAfter()));
        }
        PacketDistributor.sendToPlayer(player, new ServerPayloads.SyncAccount(
                account.balance(),
                account.debt(),
                account.creditLimit(),
                account.availableCredit(),
                EEConfig.depositFeeRate(),
                EEConfig.withdrawFeeRate(),
                EEConfig.dailyInterestRate(),
                EEConfig.loanInterestRate(),
                EEConfig.interestMinBalance(),
                account.levelUnlocked(),
                account.storedExperience(),
                player.experienceLevel,
                EEConfig.levelUnlockCost(),
                account.hasOnlineReward(today),
                account.onlineRewardClaimedToday(today),
                EEConfig.onlineRewardExperience(today),
                player.getUUID().toString(),
                EEConfig.transferFeeRate(),
                today,
                ranking,
                experienceRanking,
                transactions));
    }

    /**
     * 实时生成一个排行榜：按余额或按 ATM 已存经验从高到低排序。
     * <p>
     * 复杂度是 {@code O(n log n)}（n = 有名字的账户数）。只会在玩家打开排行榜页面时调用，
     * 几十到几百人的服务器几乎没有感觉；上千账户也只是毫秒级。
     *
     * @param byExperience true 表示经验榜，false 表示余额榜
     */
    private static List<ServerPayloads.RankingEntry> buildRanking(MinecraftServer server, EconomyData data,
                                                                  boolean byExperience) {
        List<AccountData> accounts = new ArrayList<>();
        for (AccountData account : data.accounts().values()) {
            if (account.name() != null && !account.name().isBlank()) {
                accounts.add(account);
            }
        }
        Comparator<AccountData> order = byExperience
                ? Comparator.comparingLong(AccountData::storedExperience)
                : Comparator.comparingLong(AccountData::balance);
        accounts.sort(order.reversed().thenComparing(AccountData::name, String.CASE_INSENSITIVE_ORDER));

        List<ServerPayloads.RankingEntry> out = new ArrayList<>(accounts.size());
        for (AccountData account : accounts) {
            boolean online = server != null
                    && server.getPlayerList().getPlayer(account.playerId()) != null;
            out.add(new ServerPayloads.RankingEntry(
                    account.playerId().toString(),
                    account.name(),
                    account.balance(),
                    account.debt(),
                    account.balance() - account.debt(),
                    account.storedExperience(),
                    online));
        }
        return out;
    }

    /**
     * 在玩家当前打开的机器界面上弹一条提示。
     * <p>
     * 需求：操作不满足条件时也要在机器界面上提示，不能只发聊天栏。
     * 界面没开着时发过去也无害（客户端只是缓存下来，下次打开界面时清掉）。
     */
    public static void sendNotice(ServerPlayer player, Component message, boolean error) {
        PacketDistributor.sendToPlayer(player, new ServerPayloads.ScreenNotice(message, error));
    }

    /** 市场快照（价格、走势、持仓、新闻）。 */
    public static void sendMarket(ServerPlayer player) {
        EconomyData data = dataOf(player);
        if (data == null) {
            return;
        }
        AccountData account = data.account(player.getUUID());
        List<ServerPayloads.FundSnapshot> funds = new ArrayList<>();
        for (FundType type : FundType.values()) {
            FundState state = data.market().fund(type);
            Holding holding = account.holdingOrNull(type);
            funds.add(new ServerPayloads.FundSnapshot(
                    type.ordinal(),
                    state.price(),
                    state.previousClose(),
                    state.basePrice(),
                    state.history(),
                    holding == null ? 0L : holding.shares(),
                    holding == null ? 0L : holding.totalCost(),
                    holding == null ? 0L : holding.realizedProfit()));
        }
        List<ServerPayloads.NewsLine> news = new ArrayList<>();
        for (NewsEntry entry : data.market().news()) {
            news.add(new ServerPayloads.NewsLine(entry.langKey(), entry.kind(), entry.args(), entry.day()));
        }
        PacketDistributor.sendToPlayer(player, new ServerPayloads.SyncMarket(
                funds, news, currentDay(player), EEConfig.tradeFeeRate(), EEConfig.tradeCooldownMillis(),
                account.balance()));
    }

    /** 抽奖快照。 */
    public static void sendLottery(ServerPlayer player) {
        EconomyData data = dataOf(player);
        if (data == null) {
            return;
        }
        LotteryBook book = data.lottery();
        AccountData account = data.account(player.getUUID());
        List<ServerPayloads.LotterySlot> slots = new ArrayList<>();
        for (LotteryType type : LotteryType.values()) {
            BetRecord bet = book.findBet(type, player.getUUID());
            Integer last = book.lastResult(type);
            slots.add(new ServerPayloads.LotterySlot(
                    type.ordinal(),
                    type.odds(),
                    bet == null ? -1 : bet.choice(),
                    bet == null ? 0L : bet.amount(),
                    last == null ? -1 : last,
                    book.lastBet(type, player.getUUID()),
                    book.lastPrize(type, player.getUUID())));
        }
        PacketDistributor.sendToPlayer(player, new ServerPayloads.SyncLottery(
                slots, currentDay(player), account.balance(), account.debt() > 0L));
    }

    /** 商店快照（货架标签页 + 商品行）。 */
    public static void sendShop(ServerPlayer player) {
        EconomyData data = dataOf(player);
        MinecraftServer server = player.getServer();
        if (data == null || server == null) {
            return;
        }
        var access = server.registryAccess();
        // 标签页直接来自内容文件（shop.json），所以「加一个大分类」就是加一段 JSON
        List<ServerPayloads.ShopTab> tabs = new ArrayList<>();
        for (ShopContent.Tab tab : EconomyContent.shop().tabs()) {
            tabs.add(new ServerPayloads.ShopTab(
                    tab.group().idOr(""), nullSafe(tab.group().lang()), nullSafe(tab.group().name()),
                    tab.group().iconOr(""),
                    tab.sub().idOr(""), nullSafe(tab.sub().lang()), nullSafe(tab.sub().name()),
                    tab.sub().iconOr(""), tab.unlockable()));
        }
        List<ServerPayloads.ShopGood> goods = new ArrayList<>();
        for (ShopService.Good good : ShopService.goods(data, access, player.getUUID())) {
            goods.add(new ServerPayloads.ShopGood(good.key(), good.group(), good.sub(), good.id(),
                    good.price(), good.remaining(), good.enchant(), good.exp(), good.limited()));
        }
        PacketDistributor.sendToPlayer(player, new ServerPayloads.SyncShop(
                tabs,
                goods,
                ShopService.nextUpgradeCost(data, access),
                data.shop().unlockedSlots(),
                ShopService.maxSlots(access),
                EEConfig.shopDailyPurchaseLimit(),
                data.account(player.getUUID()).balance()));
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    /** 收购快照。 */
    public static void sendAcquire(ServerPlayer player) {
        EconomyData data = dataOf(player);
        MinecraftServer server = player.getServer();
        if (data == null || server == null) {
            return;
        }
        AccountData account = data.account(player.getUUID());
        List<ServerPayloads.AcquireCategoryLine> categories = new ArrayList<>();
        for (AcquireContent.Category category : AcquireService.categories()) {
            categories.add(new ServerPayloads.AcquireCategoryLine(category.idOr(""), nullSafe(category.lang()),
                    nullSafe(category.name()), toLines(data, category.idOr(""))));
        }
        // 「附魔回收」不来自 JSON：它扫描的是玩家此刻的背包，所以每行都是实时算出来的
        categories.add(new ServerPayloads.AcquireCategoryLine(AcquireContent.ENCHANT_ID,
                "acquire.category.enchant", "附魔回收", List.of()));
        if (data.acquire().isFridayActive() && AcquireService.hasFriday()) {
            AcquireContent.Friday friday = EconomyContent.acquire().friday();
            categories.add(new ServerPayloads.AcquireCategoryLine(AcquireContent.FRIDAY_ID,
                    nullSafe(friday.lang()), nullSafe(friday.name()),
                    toLines(data, AcquireContent.FRIDAY_ID)));
        }
        List<ServerPayloads.EnchantItemLine> enchantItems = new ArrayList<>();
        for (EnchantRecycleService.ItemEntry item : EnchantRecycleService.scan(player, server.registryAccess())) {
            List<ServerPayloads.EnchantPriceLine> enchantLines = new ArrayList<>();
            for (EnchantRecycleService.EnchantLine line : item.enchants()) {
                enchantLines.add(new ServerPayloads.EnchantPriceLine(line.enchantId(), line.level(),
                        line.price()));
            }
            enchantItems.add(new ServerPayloads.EnchantItemLine(item.slot(), item.itemId(), item.count(),
                    item.unitPrice(), item.totalPrice(), enchantLines));
        }
        PacketDistributor.sendToPlayer(player, new ServerPayloads.SyncAcquire(
                categories,
                data.acquire().isFridayActive(),
                currentDay(player),
                EEConfig.fridayIntervalDays(),
                account.balance(),
                account.debt() > 0L,
                enchantItems));
    }

    private static List<ServerPayloads.AcquireLine> toLines(EconomyData data, String categoryId) {
        List<ServerPayloads.AcquireLine> lines = new ArrayList<>();
        for (AcquireService.Entry entry : AcquireService.entries(data, categoryId)) {
            lines.add(new ServerPayloads.AcquireLine(entry.itemId(), entry.price(), entry.friday(),
                    entry.batch(), entry.tag(), entry.wildcard(), nullSafe(entry.lang()),
                    nullSafe(entry.name()), nullSafe(entry.filter())));
        }
        return lines;
    }

    private static EconomyData dataOf(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return server == null ? null : EconomyData.get(server);
    }

    private static long currentDay(ServerPlayer player) {
        return DaySettlement.currentDay(player.serverLevel());
    }
}
