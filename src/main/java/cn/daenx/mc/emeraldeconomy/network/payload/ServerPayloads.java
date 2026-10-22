package cn.daenx.mc.emeraldeconomy.network.payload;

import cn.daenx.mc.emeraldeconomy.core.EEConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 → 客户端的网络包。
 * <p>
 * 客户端只负责展示，因此这里的包都是「快照」：服务端算好结果发过来，
 * 客户端不参与任何金额计算。
 */
public final class ServerPayloads {

    private ServerPayloads() {
    }

    // ------------------------------------------------------------------
    // 通用小结构
    // ------------------------------------------------------------------

    /**
     * 一支基金的价格与持仓快照。
     *
     * @param fund          基金序号（{@code FundType.ordinal()}）
     * @param price         当前价格
     * @param previousClose 上次收盘价（用于算「日涨跌」）
     * @param basePrice     长期基准价（用于算「累计涨跌」）
     * @param history       最近的价格点（走势图）
     * @param shares        玩家持有份额
     * @param totalCost     持有总成本（用于算平均成本与浮动盈亏）
     * @param realized      已实现收益
     */
    public record FundSnapshot(int fund, int price, int previousClose, int basePrice, int[] history,
                               long shares, long totalCost, long realized) {
    }

    /**
     * 一条市场新闻。
     *
     * @param langKey 语言键
     * @param kind    新闻种类（天气 / 事件 / 结算）
     * @param args    参数，其中 {@code #fund:XXX}、{@code #weather:XXX} 需要客户端再翻译
     * @param day     发生日期（游戏日）
     */
    public record NewsLine(String langKey, String kind, List<String> args, long day) {
    }

    /**
     * 一个抽奖场次的快照。
     *
     * @param lotteryType 场次序号
     * @param odds        赔率
     * @param betChoice   玩家今日的选择，-1 表示未下注
     * @param betAmount   玩家今日的下注金额
     * @param lastResult  最近一次开奖结果，-1 表示还没开过奖
     * @param lastBet     玩家上一期在这个场次的下注额，-1 表示上一期没参与
     * @param lastPrize   玩家上一期的中奖金额，-1 表示上一期没参与，0 表示参与但没中
     */
    public record LotterySlot(int lotteryType, double odds, int betChoice, long betAmount, int lastResult,
                              long lastBet, long lastPrize) {
    }

    /**
     * 商店的一个「大分类 + 子分类」组合（也就是界面上的一个标签页）。
     * <p>
     * 名字用两套：{@code lang} 是翻译键（能查到就用它，多语言下更自然），
     * {@code name} 是原样文本（自己加的分类直接写中文名即可）。客户端优先用前者。
     *
     * @param unlockable 该大分类是否走「逐格解锁」进度
     */
    public record ShopTab(String groupId, String groupLang, String groupName, String groupIcon,
                          String subId, String subLang, String subName, String subIcon,
                          boolean unlockable) {
    }

    /**
     * 一个商店货架条目的快照。
     *
     * @param key       商品键（{@code E:附魔ID} / {@code I:物品ID} / {@code X:经验点数}），客户端购买时原样回传
     * @param groupId   大分类 ID（对应 {@link ShopTab#groupId()}）
     * @param subId     子分类 ID（对应 {@link ShopTab#subId()}）
     * @param id        附魔 ID 或物品 ID（经验值商品是经验点数）
     * @param price     售价
     * @param remaining 今日剩余可购次数
     * @param enchant   是不是附魔书（界面据此决定要不要显示等级与适用物品）
     * @param exp       经验值商品的点数（0 表示不是经验值商品）
     * @param limited   是否受「每人每日限购」约束（「其他」大分类的商品不限量）
     */
    public record ShopGood(String key, String groupId, String subId, String id, int price,
                           int remaining, boolean enchant, int exp, boolean limited) {
    }

    /**
     * 一条账户流水（ATM 的「交易记录」界面用）。
     *
     * @param type          交易类型枚举名
     * @param amount        变动金额（正数收入、负数支出）
     * @param balanceBefore 这笔操作之前的余额
     * @param balanceAfter  这笔操作之后的余额
     * @param debtBefore    这笔操作之前的贷款欠款
     * @param debtAfter     这笔操作之后的贷款欠款
     */
    public record TxLine(String type, long amount, long balanceBefore, long balanceAfter,
                         long debtBefore, long debtAfter) {
    }

    /**
     * 一条收购条目。
     *
     * @param itemId 物品注册名；{@code tag} 为真时是「标签行」，形如 {@code #minecraft:saplings}
     * @param price  收购单价（每「一份」的价格）
     * @param friday 是否属于疯狂星期五
     * @param batch    一份等于几个（1 表示按个收）
     * @param tag      是不是「整类收购」行（卖背包里该类里的任意一种都行）
     * @param wildcard 是不是通配行（例如 {@code *_spawn_egg}：任意刷怪蛋，含模组）
     * @param lang     显示名翻译键（可为空）
     * @param name     显示名原样文本（翻译键查不到时用它）
     * @param filter   额外过滤规则（可为空，目前是 {@code treasure_map}）
     */
    public record AcquireLine(String itemId, long price, boolean friday, int batch,
                              boolean tag, boolean wildcard, String lang, String name, String filter) {
    }

    /**
     * 一个收购分类及其条目。
     *
     * @param id   分类 ID（内容文件里写的 id，疯狂星期五固定是 {@code FRIDAY}）
     * @param lang 分类名翻译键（可为空）
     * @param name 分类名原样文本
     */
    public record AcquireCategoryLine(String id, String lang, String name, List<AcquireLine> entries) {
    }

    /**
     * 排行榜上的一名玩家。
     *
     * @param playerId 玩家 UUID（转账 / 赠送时原样回传）
     * @param name     玩家名
     * @param balance  账户余额
     * @param debt     贷款欠款
     * @param net      净值 = 余额 - 欠款
     * @param experience ATM 里已存的经验点
     * @param online   玩家此刻是否在线（转账 / 赠送要求在线）
     */
    public record RankingEntry(String playerId, String name, long balance, long debt, long net,
                               long experience, boolean online) {
    }

    /**
     * 一个附魔的回收价。
     *
     * @param enchantId 附魔 ID
     * @param level     当前等级
     * @param price     回收价（最低价 ÷ 3 ÷ 最大等级 × 当前等级）
     */
    public record EnchantPriceLine(String enchantId, int level, long price) {
    }

    /**
     * 背包里一叠带附魔的物品（「附魔回收」分类的一行）。
     *
     * @param slot       背包格子序号（0~35）
     * @param itemId     物品注册名
     * @param count      这一叠的数量
     * @param unitPrice  单件回收总价
     * @param totalPrice 整叠回收总价
     * @param enchants   每个附魔及其回收价
     */
    public record EnchantItemLine(int slot, String itemId, int count, long unitPrice, long totalPrice,
                                  List<EnchantPriceLine> enchants) {
    }

    // ------------------------------------------------------------------
    // 具体包
    // ------------------------------------------------------------------

    /** 打开某个界面。 */
    public record OpenScreen(int screenType) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<OpenScreen> TYPE =
                new CustomPacketPayload.Type<>(EEConstants.id("open_screen"));

        public static final StreamCodec<RegistryFriendlyByteBuf, OpenScreen> STREAM_CODEC =
                StreamCodec.of(
                        (buf, payload) -> buf.writeVarInt(payload.screenType()),
                        buf -> new OpenScreen(buf.readVarInt()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * 在玩家当前打开的机器界面上弹一条提示（成功 / 失败都走这里）。
     * <p>
     * 需求：操作不满足条件时不能只在聊天栏说一句，界面上也要看得见，
     * 否则玩家得先关掉界面才能知道为什么点不动。
     * <p>
     * 消息本身是<b>可翻译组件</b>，服务端只决定「用哪个键、带什么参数」，
     * 真正翻译成哪种语言由客户端决定。
     *
     * @param message 要显示的文本
     * @param error   是否是失败提示（界面用不同颜色区分）
     */
    public record ScreenNotice(Component message, boolean error) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<ScreenNotice> TYPE =
                new CustomPacketPayload.Type<>(EEConstants.id("screen_notice"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ScreenNotice> STREAM_CODEC =
                StreamCodec.composite(
                        ComponentSerialization.STREAM_CODEC, ScreenNotice::message,
                        ByteBufCodecs.BOOL, ScreenNotice::error,
                        ScreenNotice::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * 账户快照（ATM 界面用）。
     *
     * @param balance              绿宝石余额
     * @param debt                 贷款欠款
     * @param creditLimit          信用额度
     * @param availableCredit      可贷额度
     * @param levelUnlocked        「经验存取」是否已付费解锁
     * @param storedExperience     存在 ATM 里的经验点数
     * @param playerLevel          玩家当前的等级（用于界面显示与「最多能存几级」）
     * @param levelUnlockCost      解锁「经验存取」需要的绿宝石（来自服务端配置）
     * @param onlineRewardReady    今天有没有可领的在线奖励（结算时在线且还没领）
     * @param onlineRewardClaimed  今天是否已经领过在线奖励
     * @param onlineRewardExperience 今天领取能拿到多少经验值（基础值 + 游戏日）
     * @param playerId             自己的 UUID（排行榜里用来标记「我」、禁止给自己转账）
     * @param transferFeeRate      玩家转账手续费率
     * @param rankingDay           排行榜对应的游戏日
     * @param ranking              余额排行榜（按余额从高到低）
     * @param experienceRanking    经验排行榜（按 ATM 已存经验从高到低）
     * @param transactions         最近的全部流水（新的在前）
     */
    public record SyncAccount(long balance, long debt, long creditLimit, long availableCredit,
                              double depositFeeRate, double withdrawFeeRate,
                              double dailyInterestRate, double loanInterestRate,
                              long interestMinBalance,
                              boolean levelUnlocked, long storedExperience, int playerLevel,
                              long levelUnlockCost, boolean onlineRewardReady,
                              boolean onlineRewardClaimed, int onlineRewardExperience,
                              String playerId, double transferFeeRate, long rankingDay,
                              List<RankingEntry> ranking,
                              List<RankingEntry> experienceRanking,
                              List<TxLine> transactions) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<SyncAccount> TYPE =
                new CustomPacketPayload.Type<>(EEConstants.id("sync_account"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SyncAccount> STREAM_CODEC =
                StreamCodec.of(ServerPayloads::writeAccount, ServerPayloads::readAccount);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * 市场快照（交易所界面用）。
     *
     * @param dailyBuyLimit      每日买入份额上限（0 表示不限制）
     * @param dailyBuyRemaining  今日还剩多少份额可以买入
     * @param dailySellLimit     每日卖出份额上限（0 表示不限制）
     * @param dailySellRemaining 今日还剩多少份额可以卖出
     */
    public record SyncMarket(List<FundSnapshot> funds, List<NewsLine> news, long day,
                             double tradeFeeRate, int cooldownMillis,
                             long balance, int dailyBuyLimit, long dailyBuyRemaining,
                             int dailySellLimit, long dailySellRemaining) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<SyncMarket> TYPE =
                new CustomPacketPayload.Type<>(EEConstants.id("sync_market"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SyncMarket> STREAM_CODEC =
                StreamCodec.of(ServerPayloads::writeMarket, ServerPayloads::readMarket);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 抽奖快照。 */
    public record SyncLottery(List<LotterySlot> slots, long day, long balance,
                              boolean hasDebt) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<SyncLottery> TYPE =
                new CustomPacketPayload.Type<>(EEConstants.id("sync_lottery"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SyncLottery> STREAM_CODEC =
                StreamCodec.of(ServerPayloads::writeLottery, ServerPayloads::readLottery);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 商店快照（货架标签页 + 商品行）。 */
    public record SyncShop(List<ShopTab> tabs, List<ShopGood> goods, long nextUpgradeCost, int unlocked,
                           int maxSlots, int dailyLimit, long balance) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<SyncShop> TYPE =
                new CustomPacketPayload.Type<>(EEConstants.id("sync_shop"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SyncShop> STREAM_CODEC =
                StreamCodec.of(ServerPayloads::writeShop, ServerPayloads::readShop);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 收购快照。 */
    public record SyncAcquire(List<AcquireCategoryLine> categories, boolean fridayActive,
                              long day, int fridayIntervalDays, long balance,
                              boolean hasDebt, List<EnchantItemLine> enchantItems)
            implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<SyncAcquire> TYPE =
                new CustomPacketPayload.Type<>(EEConstants.id("sync_acquire"));

        public static final StreamCodec<RegistryFriendlyByteBuf, SyncAcquire> STREAM_CODEC =
                StreamCodec.of(ServerPayloads::writeAcquire, ServerPayloads::readAcquire);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------
    // 编解码实现
    // ------------------------------------------------------------------

    private static void writeStrings(RegistryFriendlyByteBuf buf, List<String> list) {
        buf.writeVarInt(list.size());
        for (String value : list) {
            buf.writeUtf(value);
        }
    }

    private static List<String> readStrings(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<String> list = new ArrayList<>(Math.min(size, 512));
        for (int i = 0; i < size; i++) {
            list.add(buf.readUtf());
        }
        return list;
    }

    private static void writeAccount(RegistryFriendlyByteBuf buf, SyncAccount payload) {
        buf.writeVarLong(payload.balance());
        buf.writeVarLong(payload.debt());
        buf.writeVarLong(payload.creditLimit());
        buf.writeVarLong(payload.availableCredit());
        buf.writeDouble(payload.depositFeeRate());
        buf.writeDouble(payload.withdrawFeeRate());
        buf.writeDouble(payload.dailyInterestRate());
        buf.writeDouble(payload.loanInterestRate());
        buf.writeVarLong(payload.interestMinBalance());
        buf.writeBoolean(payload.levelUnlocked());
        buf.writeVarLong(payload.storedExperience());
        buf.writeVarInt(payload.playerLevel());
        buf.writeVarLong(payload.levelUnlockCost());
        buf.writeBoolean(payload.onlineRewardReady());
        buf.writeBoolean(payload.onlineRewardClaimed());
        buf.writeVarInt(payload.onlineRewardExperience());
        buf.writeUtf(payload.playerId() == null ? "" : payload.playerId());
        buf.writeDouble(payload.transferFeeRate());
        buf.writeVarLong(payload.rankingDay());
        writeRanking(buf, payload.ranking());
        writeRanking(buf, payload.experienceRanking());
        buf.writeVarInt(payload.transactions().size());
        for (TxLine line : payload.transactions()) {
            buf.writeUtf(line.type());
            buf.writeVarLong(line.amount());
            buf.writeVarLong(line.balanceBefore());
            buf.writeVarLong(line.balanceAfter());
            buf.writeVarLong(line.debtBefore());
            buf.writeVarLong(line.debtAfter());
        }
    }

    private static void writeRanking(RegistryFriendlyByteBuf buf, List<RankingEntry> ranking) {
        buf.writeVarInt(ranking.size());
        for (RankingEntry entry : ranking) {
            buf.writeUtf(entry.playerId());
            buf.writeUtf(entry.name());
            buf.writeVarLong(entry.balance());
            buf.writeVarLong(entry.debt());
            buf.writeVarLong(entry.net());
            buf.writeVarLong(entry.experience());
            buf.writeBoolean(entry.online());
        }
    }

    private static List<RankingEntry> readRanking(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<RankingEntry> ranking = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            ranking.add(new RankingEntry(buf.readUtf(), buf.readUtf(), buf.readVarLong(),
                    buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readBoolean()));
        }
        return ranking;
    }

    private static SyncAccount readAccount(RegistryFriendlyByteBuf buf) {
        long balance = buf.readVarLong();
        long debt = buf.readVarLong();
        long creditLimit = buf.readVarLong();
        long availableCredit = buf.readVarLong();
        double depositFee = buf.readDouble();
        double withdrawFee = buf.readDouble();
        double dailyRate = buf.readDouble();
        double loanRate = buf.readDouble();
        long minBalance = buf.readVarLong();
        boolean levelUnlocked = buf.readBoolean();
        long storedExperience = buf.readVarLong();
        int playerLevel = buf.readVarInt();
        long levelUnlockCost = buf.readVarLong();
        boolean onlineRewardReady = buf.readBoolean();
        boolean onlineRewardClaimed = buf.readBoolean();
        int onlineRewardExperience = buf.readVarInt();
        String playerId = buf.readUtf();
        double transferFeeRate = buf.readDouble();
        long rankingDay = buf.readVarLong();
        List<RankingEntry> ranking = readRanking(buf);
        List<RankingEntry> experienceRanking = readRanking(buf);
        int count = buf.readVarInt();
        List<TxLine> transactions = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            transactions.add(new TxLine(buf.readUtf(), buf.readVarLong(), buf.readVarLong(),
                    buf.readVarLong(), buf.readVarLong(), buf.readVarLong()));
        }
        return new SyncAccount(balance, debt, creditLimit, availableCredit,
                depositFee, withdrawFee, dailyRate, loanRate, minBalance,
                levelUnlocked, storedExperience, playerLevel, levelUnlockCost,
                onlineRewardReady, onlineRewardClaimed, onlineRewardExperience,
                playerId, transferFeeRate, rankingDay, ranking, experienceRanking, transactions);
    }

    private static void writeMarket(RegistryFriendlyByteBuf buf, SyncMarket payload) {
        buf.writeVarInt(payload.funds().size());
        for (FundSnapshot fund : payload.funds()) {
            buf.writeVarInt(fund.fund());
            buf.writeVarInt(fund.price());
            buf.writeVarInt(fund.previousClose());
            buf.writeVarInt(fund.basePrice());
            buf.writeVarInt(fund.history().length);
            for (int value : fund.history()) {
                buf.writeVarInt(value);
            }
            buf.writeVarLong(fund.shares());
            buf.writeVarLong(fund.totalCost());
            buf.writeVarLong(fund.realized());
        }
        buf.writeVarInt(payload.news().size());
        for (NewsLine line : payload.news()) {
            buf.writeUtf(line.langKey());
            buf.writeUtf(line.kind());
            writeStrings(buf, line.args());
            buf.writeVarLong(line.day());
        }
        buf.writeVarLong(payload.day());
        buf.writeDouble(payload.tradeFeeRate());
        buf.writeVarInt(payload.cooldownMillis());
        buf.writeVarLong(payload.balance());
        buf.writeVarInt(payload.dailyBuyLimit());
        buf.writeVarLong(payload.dailyBuyRemaining());
        buf.writeVarInt(payload.dailySellLimit());
        buf.writeVarLong(payload.dailySellRemaining());
    }

    private static SyncMarket readMarket(RegistryFriendlyByteBuf buf) {
        int fundCount = buf.readVarInt();
        List<FundSnapshot> funds = new ArrayList<>(Math.max(0, fundCount));
        for (int i = 0; i < fundCount; i++) {
            int fund = buf.readVarInt();
            int price = buf.readVarInt();
            int previousClose = buf.readVarInt();
            int basePrice = buf.readVarInt();
            int length = buf.readVarInt();
            int[] history = new int[Math.max(0, length)];
            for (int j = 0; j < history.length; j++) {
                history[j] = buf.readVarInt();
            }
            funds.add(new FundSnapshot(fund, price, previousClose, basePrice, history,
                    buf.readVarLong(), buf.readVarLong(), buf.readVarLong()));
        }
        int newsCount = buf.readVarInt();
        List<NewsLine> news = new ArrayList<>(Math.max(0, newsCount));
        for (int i = 0; i < newsCount; i++) {
            String langKey = buf.readUtf();
            String kind = buf.readUtf();
            List<String> args = readStrings(buf);
            news.add(new NewsLine(langKey, kind, args, buf.readVarLong()));
        }
        return new SyncMarket(funds, news, buf.readVarLong(), buf.readDouble(), buf.readVarInt(),
                buf.readVarLong(), buf.readVarInt(), buf.readVarLong(),
                buf.readVarInt(), buf.readVarLong());
    }

    private static void writeLottery(RegistryFriendlyByteBuf buf, SyncLottery payload) {
        buf.writeVarInt(payload.slots().size());
        for (LotterySlot slot : payload.slots()) {
            buf.writeVarInt(slot.lotteryType());
            buf.writeDouble(slot.odds());
            buf.writeVarInt(slot.betChoice());
            buf.writeVarLong(slot.betAmount());
            buf.writeVarInt(slot.lastResult());
            buf.writeVarLong(slot.lastBet());
            buf.writeVarLong(slot.lastPrize());
        }
        buf.writeVarLong(payload.day());
        buf.writeVarLong(payload.balance());
        buf.writeBoolean(payload.hasDebt());
    }

    private static SyncLottery readLottery(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<LotterySlot> slots = new ArrayList<>(Math.max(0, size));
        for (int i = 0; i < size; i++) {
            slots.add(new LotterySlot(buf.readVarInt(), buf.readDouble(), buf.readVarInt(),
                    buf.readVarLong(), buf.readVarInt(), buf.readVarLong(), buf.readVarLong()));
        }
        return new SyncLottery(slots, buf.readVarLong(), buf.readVarLong(), buf.readBoolean());
    }

    private static void writeShop(RegistryFriendlyByteBuf buf, SyncShop payload) {
        buf.writeVarInt(payload.tabs().size());
        for (ShopTab tab : payload.tabs()) {
            buf.writeUtf(tab.groupId());
            buf.writeUtf(tab.groupLang());
            buf.writeUtf(tab.groupName());
            buf.writeUtf(tab.groupIcon());
            buf.writeUtf(tab.subId());
            buf.writeUtf(tab.subLang());
            buf.writeUtf(tab.subName());
            buf.writeUtf(tab.subIcon());
            buf.writeBoolean(tab.unlockable());
        }
        buf.writeVarInt(payload.goods().size());
        for (ShopGood good : payload.goods()) {
            buf.writeUtf(good.key());
            buf.writeUtf(good.groupId());
            buf.writeUtf(good.subId());
            buf.writeUtf(good.id());
            buf.writeVarInt(good.price());
            buf.writeVarInt(good.remaining());
            buf.writeBoolean(good.enchant());
            buf.writeVarInt(good.exp());
            buf.writeBoolean(good.limited());
        }
        buf.writeVarLong(payload.nextUpgradeCost());
        buf.writeVarInt(payload.unlocked());
        buf.writeVarInt(payload.maxSlots());
        buf.writeVarInt(payload.dailyLimit());
        buf.writeVarLong(payload.balance());
    }

    private static SyncShop readShop(RegistryFriendlyByteBuf buf) {
        int tabCount = buf.readVarInt();
        List<ShopTab> tabs = new ArrayList<>(Math.max(0, tabCount));
        for (int i = 0; i < tabCount; i++) {
            tabs.add(new ShopTab(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                    buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readBoolean()));
        }
        int size = buf.readVarInt();
        List<ShopGood> goods = new ArrayList<>(Math.max(0, size));
        for (int i = 0; i < size; i++) {
            goods.add(new ShopGood(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                    buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(),
                    buf.readBoolean()));
        }
        return new SyncShop(tabs, goods, buf.readVarLong(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarLong());
    }

    private static void writeAcquire(RegistryFriendlyByteBuf buf, SyncAcquire payload) {
        buf.writeVarInt(payload.categories().size());
        for (AcquireCategoryLine category : payload.categories()) {
            buf.writeUtf(category.id());
            buf.writeUtf(category.lang());
            buf.writeUtf(category.name());
            buf.writeVarInt(category.entries().size());
            for (AcquireLine line : category.entries()) {
                buf.writeUtf(line.itemId());
                buf.writeVarLong(line.price());
                buf.writeBoolean(line.friday());
                buf.writeVarInt(line.batch());
                buf.writeBoolean(line.tag());
                buf.writeBoolean(line.wildcard());
                buf.writeUtf(line.lang() == null ? "" : line.lang());
                buf.writeUtf(line.name() == null ? "" : line.name());
                buf.writeUtf(line.filter() == null ? "" : line.filter());
            }
        }
        buf.writeBoolean(payload.fridayActive());
        buf.writeVarLong(payload.day());
        buf.writeVarInt(payload.fridayIntervalDays());
        buf.writeVarLong(payload.balance());
        buf.writeBoolean(payload.hasDebt());
        buf.writeVarInt(payload.enchantItems().size());
        for (EnchantItemLine item : payload.enchantItems()) {
            buf.writeVarInt(item.slot());
            buf.writeUtf(item.itemId());
            buf.writeVarInt(item.count());
            buf.writeVarLong(item.unitPrice());
            buf.writeVarLong(item.totalPrice());
            buf.writeVarInt(item.enchants().size());
            for (EnchantPriceLine line : item.enchants()) {
                buf.writeUtf(line.enchantId());
                buf.writeVarInt(line.level());
                buf.writeVarLong(line.price());
            }
        }
    }

    private static SyncAcquire readAcquire(RegistryFriendlyByteBuf buf) {
        int categoryCount = buf.readVarInt();
        List<AcquireCategoryLine> categories = new ArrayList<>(Math.max(0, categoryCount));
        for (int i = 0; i < categoryCount; i++) {
            String id = buf.readUtf();
            String lang = buf.readUtf();
            String name = buf.readUtf();
            int entryCount = buf.readVarInt();
            List<AcquireLine> entries = new ArrayList<>(Math.max(0, entryCount));
            for (int j = 0; j < entryCount; j++) {
                entries.add(new AcquireLine(buf.readUtf(), buf.readVarLong(), buf.readBoolean(),
                        buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readUtf(),
                        buf.readUtf(), buf.readUtf()));
            }
            categories.add(new AcquireCategoryLine(id, lang, name, entries));
        }
        boolean fridayActive = buf.readBoolean();
        long day = buf.readVarLong();
        int fridayIntervalDays = buf.readVarInt();
        long balance = buf.readVarLong();
        boolean hasDebt = buf.readBoolean();
        int enchantCount = buf.readVarInt();
        List<EnchantItemLine> enchantItems = new ArrayList<>(Math.max(0, enchantCount));
        for (int i = 0; i < enchantCount; i++) {
            int slot = buf.readVarInt();
            String itemId = buf.readUtf();
            int count = buf.readVarInt();
            long unitPrice = buf.readVarLong();
            long totalPrice = buf.readVarLong();
            int lineCount = buf.readVarInt();
            List<EnchantPriceLine> enchants = new ArrayList<>(Math.max(0, lineCount));
            for (int j = 0; j < lineCount; j++) {
                enchants.add(new EnchantPriceLine(buf.readUtf(), buf.readVarInt(), buf.readVarLong()));
            }
            enchantItems.add(new EnchantItemLine(slot, itemId, count, unitPrice, totalPrice, enchants));
        }
        return new SyncAcquire(categories, fridayActive, day, fridayIntervalDays, balance, hasDebt,
                enchantItems);
    }
}
