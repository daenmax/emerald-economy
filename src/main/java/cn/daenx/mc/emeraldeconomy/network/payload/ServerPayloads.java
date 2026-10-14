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
     * @param previousClose 上次收盘价（用于算涨跌幅）
     * @param history       最近的价格点（走势图）
     * @param shares        玩家持有份额
     * @param totalCost     持有总成本（用于算平均成本与浮动盈亏）
     * @param realized      已实现收益
     */
    public record FundSnapshot(int fund, int price, int previousClose, int[] history,
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
     * @param key       商品键（{@code E:附魔ID} / {@code I:物品ID}），客户端购买时原样回传
     * @param groupId   大分类 ID（对应 {@link ShopTab#groupId()}）
     * @param subId     子分类 ID（对应 {@link ShopTab#subId()}）
     * @param id        附魔 ID 或物品 ID
     * @param price     售价
     * @param remaining 今日剩余可购次数
     * @param enchant   是不是附魔书（界面据此决定要不要显示等级与适用物品）
     */
    public record ShopGood(String key, String groupId, String subId, String id, int price,
                           int remaining, boolean enchant) {
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
     * @param itemId 物品注册名
     * @param price  收购单价（每「一份」的价格）
     * @param friday 是否属于疯狂星期五
     * @param batch  一份等于几个（1 表示按个收）
     */
    public record AcquireLine(String itemId, long price, boolean friday, int batch) {
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
     * @param transactions 最近的全部流水（新的在前）
     */
    public record SyncAccount(long balance, long debt, long creditLimit, long availableCredit,
                              double depositFeeRate, double withdrawFeeRate,
                              double dailyInterestRate, double loanInterestRate,
                              long interestMinBalance,
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

    /** 市场快照（交易所界面用）。 */
    public record SyncMarket(List<FundSnapshot> funds, List<NewsLine> news, long day,
                             double tradeFeeRate, int cooldownMillis,
                             long balance) implements CustomPacketPayload {

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
                              boolean hasDebt) implements CustomPacketPayload {

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
        int count = buf.readVarInt();
        List<TxLine> transactions = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            transactions.add(new TxLine(buf.readUtf(), buf.readVarLong(), buf.readVarLong(),
                    buf.readVarLong(), buf.readVarLong(), buf.readVarLong()));
        }
        return new SyncAccount(balance, debt, creditLimit, availableCredit,
                depositFee, withdrawFee, dailyRate, loanRate, minBalance, transactions);
    }

    private static void writeMarket(RegistryFriendlyByteBuf buf, SyncMarket payload) {
        buf.writeVarInt(payload.funds().size());
        for (FundSnapshot fund : payload.funds()) {
            buf.writeVarInt(fund.fund());
            buf.writeVarInt(fund.price());
            buf.writeVarInt(fund.previousClose());
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
    }

    private static SyncMarket readMarket(RegistryFriendlyByteBuf buf) {
        int fundCount = buf.readVarInt();
        List<FundSnapshot> funds = new ArrayList<>(Math.max(0, fundCount));
        for (int i = 0; i < fundCount; i++) {
            int fund = buf.readVarInt();
            int price = buf.readVarInt();
            int previousClose = buf.readVarInt();
            int length = buf.readVarInt();
            int[] history = new int[Math.max(0, length)];
            for (int j = 0; j < history.length; j++) {
                history[j] = buf.readVarInt();
            }
            funds.add(new FundSnapshot(fund, price, previousClose, history,
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
                buf.readVarLong());
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
                    buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
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
            }
        }
        buf.writeBoolean(payload.fridayActive());
        buf.writeVarLong(payload.day());
        buf.writeVarInt(payload.fridayIntervalDays());
        buf.writeVarLong(payload.balance());
        buf.writeBoolean(payload.hasDebt());
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
                        buf.readVarInt()));
            }
            categories.add(new AcquireCategoryLine(id, lang, name, entries));
        }
        return new SyncAcquire(categories, buf.readBoolean(), buf.readVarLong(), buf.readVarInt(),
                buf.readVarLong(), buf.readBoolean());
    }
}
