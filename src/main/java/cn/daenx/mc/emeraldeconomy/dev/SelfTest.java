package cn.daenx.mc.emeraldeconomy.dev;

import cn.daenx.mc.emeraldeconomy.acquire.AcquireService;
import cn.daenx.mc.emeraldeconomy.config.EEConfig;
import cn.daenx.mc.emeraldeconomy.core.Amounts;
import cn.daenx.mc.emeraldeconomy.core.Params;
import cn.daenx.mc.emeraldeconomy.data.AccountData;
import cn.daenx.mc.emeraldeconomy.data.EconomyData;
import cn.daenx.mc.emeraldeconomy.data.TransactionRecord;
import cn.daenx.mc.emeraldeconomy.data.TransactionType;
import cn.daenx.mc.emeraldeconomy.lottery.LotteryBook;
import cn.daenx.mc.emeraldeconomy.lottery.LotteryType;
import cn.daenx.mc.emeraldeconomy.market.FundState;
import cn.daenx.mc.emeraldeconomy.market.FundType;
import cn.daenx.mc.emeraldeconomy.market.Holding;
import cn.daenx.mc.emeraldeconomy.market.MarketEngine;
import cn.daenx.mc.emeraldeconomy.market.WeatherKind;
import cn.daenx.mc.emeraldeconomy.shop.EnchantCatalog;
import cn.daenx.mc.emeraldeconomy.shop.ShopService;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 开发者自检（默认关闭）。
 * <p>
 * 价格引擎、利息与贷款、持仓成本、抽奖判定这些逻辑很难靠手动游戏验证，
 * 这里在服务端启动后跑一遍纯逻辑断言，把结果打到日志里。
 * <p>
 * 启用方式：给服务端加系统属性 {@code -Dee.selftest=true}。
 * 开发用的 {@code runServer} 配置已经默认打开，正式整合包里不会自动运行。
 * <p>
 * 自检全程使用一份临时的 {@link EconomyData}，不会污染真实的存档数据。
 */
public final class SelfTest {

    /** 系统属性名。 */
    public static final String PROPERTY = "ee.selftest";

    private static int passed;
    private static int failed;

    private SelfTest() {
    }

    /** 是否需要执行自检。 */
    public static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty(PROPERTY, "false"));
    }

    /** 执行全部自检，把结果写入日志。 */
    public static void run(MinecraftServer server) {
        passed = 0;
        failed = 0;
        long start = System.currentTimeMillis();

        try {
            testAmounts();
            testParams();
            testHolding();
            testFundState();
            testPriceEngine();
            testSettlement(server);
            testLottery();
            testLotteryDebtRule();
            testAcquireTables();
            testFridayPool();
            testShopGoods();
            testEnchantPrices();
            testShopCost();
            testFriday();
            testAccountPersistence();
            testEconomyPersistence();
        } catch (Throwable throwable) {
            failed++;
            log("自检过程中抛出异常：" + throwable);
            throwable.printStackTrace();
        }

        log(String.format("自检结束：通过 %d，失败 %d，耗时 %d ms",
                passed, failed, System.currentTimeMillis() - start));
    }

    // ------------------------------------------------------------------
    // 各项断言
    // ------------------------------------------------------------------

    private static void testAmounts() {
        check("手续费最低 1", Amounts.fee(100L, 0.01D) == 1L);
        check("手续费按费率取整", Amounts.fee(1000L, 0.01D) == 10L);
        check("收费为 0 时手续费为 0", Amounts.fee(0L, 0.01D) == 0L);
        check("利息向下取整", Amounts.interest(999L, 0.001D) == 0L);
        check("价格四舍五入且最小 1", Amounts.roundPrice(0.4D) == 1);
        check("价格四舍五入", Amounts.roundPrice(12.6D) == 13);
        check("中奖金额至少 1", Amounts.floorAtLeastOne(1.2D) == 1L);
    }

    private static void testParams() {
        var doubles = Params.parseDoubleMap(List.of("# 注释", "A=0.5", "bad", "B=2"));
        check("解析 double 表", Params.getDouble(doubles, "A", -1) == 0.5D);
        check("跳过注释与非法行", doubles.size() == 2);
        check("缺省值生效", Params.getDouble(doubles, "Z", 9.0D) == 9.0D);
        var longs = Params.parseLongMap(List.of("minecraft:wheat=8"));
        check("解析 long 表", Params.getLong(longs, "minecraft:wheat", 0L) == 8L);
    }

    private static void testHolding() {
        Holding holding = new Holding();
        holding.onBuy(10L, 1000L);
        check("买入后份额", holding.shares() == 10L);
        check("平均成本", holding.averageCost() == 100L);
        long profit = holding.onSell(4L, 600L);
        check("卖出后份额", holding.shares() == 6L);
        check("已实现收益", holding.realizedProfit() == 200L && profit == 200L);
        check("卖出后成本同步减少", holding.totalCost() == 600L);
        holding.onSell(6L, 600L);
        check("清仓后份额为 0", holding.shares() == 0L);
        check("清仓后成本为 0", holding.totalCost() == 0L);
    }

    private static void testFundState() {
        FundState state = new FundState(FundType.EMR_MIN, 100, 3);
        state.pushHistory(101);
        state.pushHistory(102);
        state.pushHistory(103);
        state.pushHistory(104);
        check("价格历史只保留配置长度", state.history().length == 3);
        state.setPrice(110);
        state.setPreviousClose(100);
        check("涨跌幅计算", Math.abs(state.changeRatio() - 0.10D) < 1.0E-9D);
    }

    private static void testPriceEngine() {
        EconomyData data = new EconomyData();
        data.ensureMarketInitialized();
        RandomSource random = RandomSource.create(20261013L);

        double minFactor = EEConfig.priceMinFactor();
        double maxFactor = EEConfig.priceMaxFactor();
        boolean boundsOk = true;
        boolean positiveOk = true;

        for (int i = 0; i < 500; i++) {
            MarketEngine.updatePrices(data, random);
            for (FundType type : FundType.values()) {
                FundState state = data.market().fund(type);
                int base = state.basePrice();
                int price = state.price();
                if (price < 1) {
                    positiveOk = false;
                }
                if (price < Math.floor(base * minFactor) || price > Math.ceil(base * maxFactor)) {
                    boundsOk = false;
                }
            }
        }
        check("500 次价格更新后价格始终不小于 1", positiveOk);
        check("500 次价格更新后价格始终在硬性边界内", boundsOk);

        boolean momentumOk = true;
        for (FundType type : FundType.industryFunds()) {
            double momentum = data.market().fund(type).momentum();
            if (Math.abs(momentum) > EEConfig.maxMomentum() + 1.0E-6D) {
                momentumOk = false;
            }
        }
        check("动量被限制在最大幅度内", momentumOk);

        // 综合基金应当等于六支基金的加权指数
        double index = 0.0D;
        for (FundType type : FundType.industryFunds()) {
            FundState state = data.market().fund(type);
            index += type.compositeWeight() * ((double) state.price() / state.basePrice());
        }
        FundState composite = data.market().fund(FundType.EMR_CMP);
        double expected = composite.basePrice() * index * (1.0D + composite.momentum());
        double actual = composite.price();
        check("综合基金由六支基金加权得出",
                Math.abs(actual - expected) <= Math.max(2.0D, expected * 0.02D));
    }

    private static void testSettlement(MinecraftServer server) {
        EconomyData data = new EconomyData();
        data.ensureMarketInitialized();
        data.market().addSupply(FundType.EMR_MIN, 5000L);
        data.market().addSupply(FundType.EMR_AGR, 100L);
        RandomSource random = RandomSource.create(7L);

        for (WeatherKind weather : WeatherKind.values()) {
            MarketEngine.settleDay(null, data, 1L, weather, random);
        }
        check("供给量在结算后被清空", data.market().supply(FundType.EMR_MIN) == 0L);
        check("结算后记录了天气", data.market().weatherDay() == 1L);
        check("结算后生成市场新闻", !data.market().news().isEmpty());
        check("结算后记录了收盘价",
                data.market().fund(FundType.EMR_MIN).previousClose() > 0);
    }

    private static void testLottery() {
        check("猜单双判定（单）", LotteryType.ODD_EVEN.isWinner(LotteryType.OPTION_ODD, 7));
        check("猜单双判定（双）", LotteryType.ODD_EVEN.isWinner(LotteryType.OPTION_EVEN, 8));
        check("猜大小判定（小）", LotteryType.BIG_SMALL.isWinner(LotteryType.OPTION_SMALL, 4));
        check("猜大小判定（大）", LotteryType.BIG_SMALL.isWinner(LotteryType.OPTION_BIG, 5));
        check("猜尾数判定", LotteryType.LAST_DIGIT.isWinner(3, 3));
        check("猜尾数不中", !LotteryType.LAST_DIGIT.isWinner(3, 4));
        check("选项数量正确", LotteryType.LAST_DIGIT.optionCount() == 10);
        check("赔率来自配置", LotteryType.ODD_EVEN.odds() > 1.0D);
    }

    /**
     * 抽奖的「上次开奖余额变化」记录（需求：上次开奖要显示余额是加了还是没变、加了多少）。
     * <p>
     * 这一段是纯数据逻辑，不需要服务端：开奖时记下每个参与者的下注额与中奖额，
     * 界面上据此显示 +X / 未中奖 / 未参与。
     */
    private static void testLotteryDebtRule() {
        LotteryBook book = new LotteryBook();
        UUID winner = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        UUID loser = UUID.fromString("00000000-0000-0000-0000-00000000000b");

        check("没开过奖时「上次下注」为 -1", book.lastBet(LotteryType.ODD_EVEN, winner) == -1L);
        check("没开过奖时「上次中奖」为 -1", book.lastPrize(LotteryType.ODD_EVEN, winner) == -1L);

        book.recordOutcome(LotteryType.ODD_EVEN, winner, 50L, 97L);
        book.recordOutcome(LotteryType.ODD_EVEN, loser, 20L, 0L);
        check("记录中奖者的下注额", book.lastBet(LotteryType.ODD_EVEN, winner) == 50L);
        check("记录中奖者的中奖额（余额增加量）", book.lastPrize(LotteryType.ODD_EVEN, winner) == 97L);
        check("记录未中奖者：下注了但奖金为 0", book.lastPrize(LotteryType.ODD_EVEN, loser) == 0L);

        // 存档往返：这些数据要在重启后仍能显示
        LotteryBook loaded = new LotteryBook();
        loaded.load(book.save());
        check("上次开奖记录随存档持久化", loaded.lastPrize(LotteryType.ODD_EVEN, winner) == 97L
                && loaded.lastBet(LotteryType.ODD_EVEN, loser) == 20L);

        loaded.clearOutcomes(LotteryType.ODD_EVEN);
        check("开新一期时清空上一期的记录",
                loaded.lastPrize(LotteryType.ODD_EVEN, winner) == -1L);
    }

    /**
     * 内容文件（config/emerald_economy/*.json）能正确加载，并且条目、标签、批次都符合预期。
     * <p>
     * 这几条是这一层最重要的「上线前检查」：只要 JSON 写错或者被清空，这里就会红。
     */
    private static void testAcquireTables() {
        Map<String, Long> farming = priceMap("AGRICULTURE");
        check("农业表里能展开出小麦", farming.containsKey("minecraft:wheat"));
        check("农业表的 saplings 标签展开成了具体树苗",
                farming.containsKey("minecraft:oak_sapling") && farming.containsKey("minecraft:birch_sapling"));
        check("树苗的价格是 1", farming.getOrDefault("minecraft:oak_sapling", 0L) == 1L);

        Map<String, Long> misc = priceMap("MISC");
        check("杂物表里有山羊角与潜影壳",
                misc.containsKey("minecraft:goat_horn") && misc.containsKey("minecraft:shulker_shell"));
        check("杂物表的音乐唱片标签展开了（含 13 号与猪布林）", misc.containsKey("minecraft:music_disc_13")
                && misc.containsKey("minecraft:music_disc_pigstep"));
        check("杂物表的马铠标签展开了 4 件", misc.containsKey("minecraft:diamond_horse_armor"));

        Map<String, Long> mining = priceMap("MINING");
        check("矿物表不再收购绿宝石", !mining.containsKey("minecraft:emerald"));
        check("矿物表里有下界合金碎片（10）",
                mining.getOrDefault("minecraft:netherite_scrap", 0L) == 10L);

        Map<String, Long> forestry = priceMap("FORESTRY");
        check("林业表不再收购木棍", !forestry.containsKey("minecraft:stick"));
        check("林业表原木统一 1 个", forestry.getOrDefault("minecraft:oak_log", 0L) == 1L);

        Map<String, Long> livestock = priceMap("LIVESTOCK");
        check("畜牧表有鱼肉类（fishes 标签展开出鳕鱼）", livestock.containsKey("minecraft:cod"));
        check("畜牧表收骨头、不收骨粉",
                livestock.containsKey("minecraft:bone") && !livestock.containsKey("minecraft:bone_meal"));

        check("鸡蛋配了 8 个一份", AcquireService.batchOf(null, "minecraft:egg") == 8);
        check("羽毛配了 8 个一份", AcquireService.batchOf(null, "minecraft:feather") == 8);
        check("竹子配了 4 个一份", AcquireService.batchOf(null, "minecraft:bamboo") == 4);
        check("没配批次的物品按 1 个算", AcquireService.batchOf(null, "minecraft:wheat") == 1);
        check("甘蔗单价 2", farming.getOrDefault("minecraft:sugar_cane", 0L) == 2L);

        check("收购分类来自内容文件（5 个）", AcquireService.categories().size() == 5);
        check("疯狂星期五那段配置存在", AcquireService.hasFriday());
    }

    /** 某个分类展开成「物品 ID -> 价格」表（不需要玩家数据）。 */
    private static Map<String, Long> priceMap(String categoryId) {
        Map<String, Long> out = new java.util.LinkedHashMap<>();
        for (AcquireService.Entry entry : AcquireService.entries(null, categoryId)) {
            out.putIfAbsent(entry.itemId(), entry.price());
        }
        return out;
    }

    /** 疯狂星期五的物品池里不该再有潜影壳。 */
    private static void testFridayPool() {
        var friday = cn.daenx.mc.emeraldeconomy.content.EconomyContent.acquire().friday();
        check("疯狂星期五配置存在", friday != null);
        if (friday == null) {
            return;
        }
        check("疯狂星期五物品池里没有潜影壳",
                friday.poolList().stream().noneMatch(id -> id != null && id.contains("shulker_shell")));
        check("疯狂星期五基准价里没有潜影壳",
                friday.basePriceMap().keySet().stream().noneMatch(id -> id.contains("shulker_shell")));
        check("疯狂星期五物品池有 9 件", friday.poolList().size() == 9);
    }

    /** 自动售货机的货架：附魔书 + 「其他」商品（种子 / 树苗）。 */
    private static void testShopGoods() {
        var content = cn.daenx.mc.emeraldeconomy.content.EconomyContent.shop();
        check("售货机有大分类", content.groupList().size() >= 2);
        check("附魔书大分类是解锁制",
                content.groupList().stream().anyMatch(g -> g.isUnlockable() && g.subcategories() != null));
        check("附魔书表解析出 42 个附魔", content.unlockableEnchants().size() == 42);
        check("「其他」商品表里有小麦种子（8）",
                content.plainItems().getOrDefault("minecraft:wheat_seeds", -1) == 8);
        check("「其他」商品表里有橡树树苗（16）",
                content.plainItems().getOrDefault("minecraft:oak_sapling", -1) == 16);
        check("锋利的价格区间是 896-1280", content.enchantSpec("minecraft:sharpness") != null
                && content.enchantSpec("minecraft:sharpness").minPrice() == 896
                && content.enchantSpec("minecraft:sharpness").maxPrice() == 1280);
        check("附魔都能查到自己的子分类", content.tabOfEnchant("minecraft:mending") != null);
    }

    private static void testShopCost() {
        int min = EEConfig.shopUpgradeCostMin();
        int max = EEConfig.shopUpgradeCostMax();
        boolean inRange = true;
        boolean stable = true;
        for (int unlocked = 1; unlocked <= 42; unlocked++) {
            long cost = ShopService.upgradeCost(unlocked);
            if (cost < min || cost > max) {
                inRange = false;
            }
            if (cost != ShopService.upgradeCost(unlocked)) {
                stable = false;
            }
        }
        check("升级费用落在配置区间 [" + min + ", " + max + "] 内", inRange);
        check("同一级的升级费用固定（刷新界面不会变价）", stable);
        check("升级费用不是个定值（区间内随机）",
                ShopService.upgradeCost(1) != ShopService.upgradeCost(2)
                        || ShopService.upgradeCost(2) != ShopService.upgradeCost(3)
                        || min == max);
    }

    /**
     * 附魔书价格：落在自己配置的区间内，而且同一附魔价格稳定不变。
     * <p>
     * 价格区间来自内容文件（shop.json），这里直接对着区间校验，不依赖注册表。
     */
    private static void testEnchantPrices() {
        var content = cn.daenx.mc.emeraldeconomy.content.EconomyContent.shop();
        boolean rangeOk = true;
        boolean stableOk = true;
        for (var entry : content.unlockableEnchants().entrySet()) {
            var spec = entry.getValue();
            int price = EnchantCatalog.priceOf(null, entry.getKey());
            if (price < spec.minPrice() || price > spec.maxPrice()) {
                rangeOk = false;
            }
            if (price != EnchantCatalog.priceOf(null, entry.getKey())) {
                stableOk = false;
            }
        }
        check("每个附魔的价格都落在自己配的区间内", rangeOk);
        check("同一附魔的价格稳定不变（界面与扣费不会打架）", stableOk);
    }

    private static void testFriday() {
        EconomyData data = new EconomyData();
        RandomSource random = RandomSource.create(99L);
        int interval = Math.max(1, EEConfig.fridayIntervalDays());

        AcquireService.ensureFridayState(data, 1L, random);
        check("非开放日不启用疯狂星期五", !data.acquire().isFridayActive());

        AcquireService.ensureFridayState(data, interval, random);
        check("逢 N 启用疯狂星期五", data.acquire().isFridayActive());
        check("挑选了配置数量的物品",
                data.acquire().fridayItems().size() == EEConfig.fridayPickCount());

        var entries = AcquireService.fridayEntries(data);
        boolean priceOk = !entries.isEmpty();
        for (var entry : entries) {
            if (entry.price() < 1 || !entry.friday()) {
                priceOk = false;
            }
        }
        check("疯狂星期五价格为正且标记正确", priceOk);
    }

    private static void testAccountPersistence() {
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-0000000000ab");
        AccountData account = new AccountData(uuid);
        account.setName("Steve");
        account.addBalance(1234L);
        account.addDebt(500L);
        account.addDebtInterest(40L);
        account.pushRecord(TransactionType.DEPOSIT, 1234L, 0L, 500L, 100L);
        account.holding(FundType.EMR_MIN).onBuy(7L, 700L);

        AccountData loaded = AccountData.load(account.save());
        check("账户 UUID 持久化", uuid.equals(loaded.playerId()));
        check("账户名持久化", "Steve".equals(loaded.name()));
        check("余额持久化", loaded.balance() == 1234L);
        check("欠款总额持久化（本金 500 + 利息 40）", loaded.debt() == 540L);
        check("欠款利息部分持久化", loaded.debtInterest() == 40L);
        check("欠款本金部分正确", loaded.debtPrincipal() == 500L);
        check("流水持久化", loaded.records().size() == 1);
        check("持仓持久化", loaded.holding(FundType.EMR_MIN).shares() == 7L);
        // 流水里同时记下余额与欠款，ATM 的交易记录界面才能显示两处变动
        TransactionRecord record = loaded.records().peekFirst();
        check("流水记录余额变动后的余额", record.balanceAfter() == 1234L);
        check("流水记录欠款变动后的欠款", record.debtAfter() == 540L);
    }

    private static void testEconomyPersistence() {
        EconomyData data = new EconomyData();
        data.ensureMarketInitialized();
        UUID uuid = UUID.fromString("00000000-0000-0000-0000-0000000000cd");
        data.account(uuid).addBalance(4321L);
        data.market().addSupply(FundType.EMR_BLD, 42L);
        data.setLastSettledDay(12L);

        HolderLookup.Provider provider = null;
        CompoundTag tag = data.save(new CompoundTag(), provider);
        EconomyData loaded = EconomyData.load(tag, provider);
        check("存档金额持久化", loaded.account(uuid).balance() == 4321L);
        check("存档日期持久化", loaded.lastSettledDay() == 12L);
        check("存档基金价格持久化",
                loaded.market().fund(FundType.EMR_CMP).price() == data.market().fund(FundType.EMR_CMP).price());
    }

    // ------------------------------------------------------------------

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            log("  [OK]   " + name);
        } else {
            failed++;
            log("  [FAIL] " + name);
        }
    }

    private static void log(String message) {
        System.out.println("[自检] " + message);
    }
}
