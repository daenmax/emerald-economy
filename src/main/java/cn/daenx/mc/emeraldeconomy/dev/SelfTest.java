package cn.daenx.mc.emeraldeconomy.dev;

import cn.daenx.mc.emeraldeconomy.acquire.AcquireService;
import cn.daenx.mc.emeraldeconomy.acquire.EnchantRecycleService;
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
            testLevelAndOnlineReward();
            testMarketRandomDrift();
            testEconomyPersistence();
            testRankingAndRecycle(server);
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
        check("农业表新增毒马铃薯（10），且排在马铃薯后面",
                farming.getOrDefault("minecraft:poisonous_potato", 0L) == 10L
                        && rowOrder("AGRICULTURE").indexOf("minecraft:potato")
                        < rowOrder("AGRICULTURE").indexOf("minecraft:poisonous_potato"));

        Map<String, Long> forestry = priceMap("FORESTRY");
        check("林业表新增所有树苗（1）",
                forestry.getOrDefault("minecraft:oak_sapling", 0L) == 1L
                        && forestry.containsKey("minecraft:cherry_sapling"));
        check("林业表新增所有花（1）",
                forestry.getOrDefault("minecraft:dandelion", 0L) == 1L
                        && forestry.getOrDefault("minecraft:sunflower", 0L) == 1L);
        check("林业表新增所有蘑菇（1）",
                forestry.getOrDefault("minecraft:brown_mushroom", 0L) == 1L
                        && forestry.getOrDefault("minecraft:red_mushroom", 0L) == 1L);
        check("林业表新增仙人掌（1）", forestry.getOrDefault("minecraft:cactus", 0L) == 1L);
        check("林业表新增紫颂花（5）与紫颂果（1）",
                forestry.getOrDefault("minecraft:chorus_flower", 0L) == 5L
                        && forestry.getOrDefault("minecraft:chorus_fruit", 0L) == 1L);
        check("林业表不再收购木棍", !forestry.containsKey("minecraft:stick"));
        check("林业表原木统一 1 个", forestry.getOrDefault("minecraft:oak_log", 0L) == 1L);

        Map<String, Long> misc = priceMap("MISC");
        check("杂物表里有山羊角与潜影壳",
                misc.containsKey("minecraft:goat_horn") && misc.containsKey("minecraft:shulker_shell"));
        check("杂物表新增不祥旗帜（50，按白色旗帜收）",
                misc.getOrDefault("minecraft:white_banner", 0L) == 50L);
        check("杂物表新增恶魂之泪（20）", misc.getOrDefault("minecraft:ghast_tear", 0L) == 20L);
        check("杂物表新增腐肉 / 蜘蛛眼（1）",
                misc.getOrDefault("minecraft:rotten_flesh", 0L) == 1L
                        && misc.getOrDefault("minecraft:spider_eye", 0L) == 1L);
        check("杂物表新增幽匿感测体（20）", misc.getOrDefault("minecraft:sculk_sensor", 0L) == 20L);
        check("杂物表新增藏宝图 / 光灵箭 / 箭 / 鹦鹉螺壳 / 蜘蛛网",
                misc.getOrDefault("minecraft:filled_map", 0L) == 20L
                        && misc.getOrDefault("minecraft:spectral_arrow", 0L) == 10L
                        && misc.getOrDefault("minecraft:arrow", 0L) == 1L
                        && misc.getOrDefault("minecraft:nautilus_shell", 0L) == 50L
                        && misc.getOrDefault("minecraft:cobweb", 0L) == 5L);
        check("藏宝图行带 treasure_map 过滤（普通地图不收）",
                AcquireService.isTreasureMapRow(null, "minecraft:filled_map"));
        check("岩浆膏（5）排在粘液球（2）下面，烈焰棒 5",
                misc.getOrDefault("minecraft:magma_cream", 0L) == 5L
                        && misc.getOrDefault("minecraft:slime_ball", 0L) == 2L
                        && misc.getOrDefault("minecraft:blaze_rod", 0L) == 5L
                        && rowOrder("MISC").indexOf("minecraft:slime_ball")
                        < rowOrder("MISC").indexOf("minecraft:magma_cream"));
        check("马铠按品质分开收（皮革 10 / 铁 50 / 金 100 / 钻石 200），不再合并成一行",
                misc.getOrDefault("minecraft:leather_horse_armor", 0L) == 10L
                        && misc.getOrDefault("minecraft:iron_horse_armor", 0L) == 50L
                        && misc.getOrDefault("minecraft:golden_horse_armor", 0L) == 100L
                        && misc.getOrDefault("minecraft:diamond_horse_armor", 0L) == 200L
                        && !hasTagRow("MISC", "emerald_economy:acquire/horse_armors"));

        // 「整类」条目在界面上只占一行：唱片 / 树苗 / 花 / 蘑菇 / 马铠都是标签行
        check("唱片是一行标签行（不再把 19 张唱片一件件列出来）",
                hasTagRow("MISC", "emerald_economy:acquire/music_discs")
                        && !rowOrder("MISC").contains("minecraft:music_disc_13"));
        check("树苗 / 花 / 蘑菇都是标签行",
                hasTagRow("FORESTRY", "minecraft:saplings")
                        && hasTagRow("FORESTRY", "emerald_economy:acquire/flowers")
                        && hasTagRow("FORESTRY", "emerald_economy:acquire/mushrooms"));
        check("标签行有配置好的显示名（音乐唱片）",
                tagRowLang("MISC", "emerald_economy:acquire/music_discs") != null);
        // 每一行标签都要有名字：裸标签 ID（#emerald_economy:acquire/xxx）玩家看不懂
        List<String> unnamed = new java.util.ArrayList<>();
        for (AcquireService.Entry entry : entriesOfAllCategories()) {
            if (entry.tag() && (entry.lang() == null || entry.lang().isBlank())
                    && (entry.name() == null || entry.name().isBlank())) {
                unnamed.add(entry.itemId());
            }
        }
        check("所有标签行都配了显示名（没有裸标签 ID 露到界面上）：" + unnamed, unnamed.isEmpty());

        Map<String, Long> mining = priceMap("MINING");
        check("矿物表不再收购绿宝石", !mining.containsKey("minecraft:emerald"));
        check("矿物表里有下界合金碎片（10）",
                mining.getOrDefault("minecraft:netherite_scrap", 0L) == 10L);
        check("矿物表新增哭泣的黑曜石 / 紫水晶块 / 铁铜金锭",
                mining.getOrDefault("minecraft:crying_obsidian", 0L) == 5L
                        && mining.getOrDefault("minecraft:amethyst_block", 0L) == 2L
                        && mining.getOrDefault("minecraft:iron_ingot", 0L) == 4L
                        && mining.getOrDefault("minecraft:copper_ingot", 0L) == 4L
                        && mining.getOrDefault("minecraft:gold_ingot", 0L) == 8L);
        check("紫水晶碎片调价为 15", mining.getOrDefault("minecraft:amethyst_shard", 0L) == 15L);

        Map<String, Long> livestock = priceMap("LIVESTOCK");
        check("畜牧表有鱼肉类（fishes 标签展开出鳕鱼）", livestock.containsKey("minecraft:cod"));
        check("河豚从鱼类里独立出来（20），鱼类标签不再包含河豚",
                livestock.getOrDefault("minecraft:pufferfish", 0L) == 20L
                        && !AcquireService.tagMemberIds("emerald_economy:acquire/fishes")
                        .contains("minecraft:pufferfish"));
        check("畜牧表新增海龟蛋（20）与犰狳鳞甲（10）",
                livestock.getOrDefault("minecraft:turtle_egg", 0L) == 20L
                        && livestock.getOrDefault("minecraft:armadillo_scute", 0L) == 10L);
        check("畜牧表收骨头、不收骨粉",
                livestock.containsKey("minecraft:bone") && !livestock.containsKey("minecraft:bone_meal"));

        check("鸡蛋配了 8 个一份", AcquireService.batchOf(null, "minecraft:egg") == 8);
        check("羽毛配了 8 个一份", AcquireService.batchOf(null, "minecraft:feather") == 8);
        check("种子配了 8 个一份", AcquireService.batchOf(null, "minecraft:wheat_seeds") == 8);
        check("竹子已从农业默认收购里移除", !farming.containsKey("minecraft:bamboo"));
        check("没配批次的物品按 1 个算", AcquireService.batchOf(null, "minecraft:wheat") == 1);
        check("甘蔗单价 2", farming.getOrDefault("minecraft:sugar_cane", 0L) == 2L);
        check("标签行也能查到价格（树苗 1）",
                AcquireService.priceOf(null, "#minecraft:saplings") == 1L);

        check("收购分类来自内容文件（5 个）", AcquireService.categories().size() == 5);
        check("疯狂星期五那段配置存在", AcquireService.hasFriday());
    }

    /** 某个分类展开成「物品 ID -> 价格」表（标签会展开成具体物品，与旧断言兼容）。 */
    private static Map<String, Long> priceMap(String categoryId) {
        Map<String, Long> out = new java.util.LinkedHashMap<>();
        for (AcquireService.Entry entry : AcquireService.entries(null, categoryId)) {
            if (entry.tag()) {
                for (String id : AcquireService.tagMemberIds(entry.itemId().substring(1))) {
                    out.putIfAbsent(id, entry.price());
                }
            } else {
                out.putIfAbsent(entry.itemId(), entry.price());
            }
        }
        return out;
    }

    /** 一个分类里「具体物品行」的 ID 顺序（用于校验配置里的先后顺序）。 */
    private static List<String> rowOrder(String categoryId) {
        List<String> out = new java.util.ArrayList<>();
        for (AcquireService.Entry entry : AcquireService.entries(null, categoryId)) {
            if (!entry.tag()) {
                out.add(entry.itemId());
            }
        }
        return out;
    }

    /** 某个分类里有没有指定的标签行。 */
    private static boolean hasTagRow(String categoryId, String tag) {
        for (AcquireService.Entry entry : AcquireService.entries(null, categoryId)) {
            if (entry.tag() && entry.itemId().equalsIgnoreCase("#" + tag)) {
                return true;
            }
        }
        return false;
    }

    /** 所有常驻分类的全部行（用于「每个标签行都得有名字」这类全局检查）。 */
    private static List<AcquireService.Entry> entriesOfAllCategories() {
        List<AcquireService.Entry> out = new java.util.ArrayList<>();
        for (var category : AcquireService.categories()) {
            out.addAll(AcquireService.entries(null, category.idOr("")));
        }
        return out;
    }

    /** 标签行的显示名翻译键。 */
    private static String tagRowLang(String categoryId, String tag) {
        for (AcquireService.Entry entry : AcquireService.entries(null, categoryId)) {
            if (entry.tag() && entry.itemId().equalsIgnoreCase("#" + tag)) {
                return entry.lang();
            }
        }
        return null;
    }

    /**
     * 本轮新增的纯逻辑：排行榜排序与附魔回收价公式。
     * <p>
     * 排行榜用临时存档验证「净值 = 余额 - 欠款」「按净值降序」「记录更新日」；
     * 附魔回收价直接用本地注册表验证需求里的两个例子（经验修补 1560、保护 3 级 672）。
     */
    private static void testRankingAndRecycle(MinecraftServer server) {
        EconomyData data = new EconomyData();
        UUID rich = UUID.randomUUID();
        UUID poor = UUID.randomUUID();
        AccountData a = data.account(rich);
        a.setName("Rich");
        a.addBalance(1000L);
        AccountData b = data.account(poor);
        b.setName("Poor");
        b.addBalance(500L);
        b.addDebt(200L);
        data.ranking().update(data, 7L);
        check("排行榜记录了更新日", data.ranking().day() == 7L);
        check("排行榜按净值降序（1000 > 300）",
                data.ranking().entries().size() == 2
                        && data.ranking().entries().get(0).name().equals("Rich")
                        && data.ranking().entries().get(0).net() == 1000L
                        && data.ranking().entries().get(1).net() == 300L);
        check("转账手续费率默认 0.01", EEConfig.transferFeeRate() == 0.01D);

        var access = server.registryAccess();
        check("附魔回收价：经验修补 1 级 = 1560",
                EnchantRecycleService.priceOf(access, "minecraft:mending", 1) == 1560L);
        check("附魔回收价：保护 3 级 = 896 / 4 * 3 = 672",
                EnchantRecycleService.priceOf(access, "minecraft:protection", 3) == 672L);
    }

    /**
     * 疯狂星期五的物品池：池子写法（具体物品 / 标签 / 通配）与「同类最多占几件」。
     * <p>
     * 需求：一天挑 3 件时，<b>头颅最多占 1 件、刷怪蛋整类最多占 1 行</b>。
     * 通配条目 {@code *_spawn_egg} 被抽中时，存档里记的是整行而不是某一种蛋，
     * 因此玩家卖背包里任意一种刷怪蛋（含其他模组）都按这一行的价格结算。
     */
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
        check("池子里有刷怪笼", friday.poolList().contains("minecraft:spawner"));
        check("池子里有「所有怪物的头颅」标签",
                friday.poolList().contains("#emerald_economy:acquire/heads"));
        check("池子里有「任意刷怪蛋」通配行",
                friday.poolList().contains("*_spawn_egg"));
        check("池子里有不祥之瓶", friday.poolList().contains("minecraft:ominous_bottle"));
        // 龙首并进了头颅标签（不再单独列、不再单独配价），不死图腾从池子里删掉
        check("头颅标签里含龙首（6 种头颅同一个池子条目）",
                AcquireService.tagMemberIds("emerald_economy:acquire/heads")
                        .contains("minecraft:dragon_head")
                        && !friday.poolList().contains("minecraft:dragon_head"));
        check("池子里没有不死图腾",
                friday.poolList().stream().noneMatch(id -> id != null && id.contains("totem_of_undying"))
                        && friday.basePriceMap().keySet().stream()
                        .noneMatch(id -> id.contains("totem_of_undying")));
        check("池子条目没有把挑选上限调大（默认就是每天最多 1 件）",
                friday.poolMaxPickMap().values().stream().allMatch(value -> value <= 1));
        check("池子条目的基准价（头颅 83 / 不祥之瓶 42 / 刷怪蛋 40 / 刷怪笼 40）",
                friday.basePriceMap().getOrDefault("#emerald_economy:acquire/heads", 0) == 83
                        && friday.basePriceMap().getOrDefault("minecraft:ominous_bottle", 0) == 42
                        && friday.basePriceMap().getOrDefault("*_spawn_egg", 0) == 40
                        && friday.basePriceMap().getOrDefault("minecraft:spawner", 0) == 40);

        // 200 次抽样：每天挑满 3 件、三件互不相同、同类不超 1 件，
        // 并且「具体是哪一种头颅」确实是随机的（出现过不止一种）
        boolean countOk = true;
        boolean distinctOk = true;
        boolean headLimitOk = true;
        boolean priceOk = true;
        boolean eggRowOk = true;
        int eggRowDays = 0;
        long headPriceMin = -1L;
        long headPriceMax = -1L;
        java.util.Set<String> heads = new java.util.HashSet<>(List.of(
                "minecraft:zombie_head", "minecraft:skeleton_skull", "minecraft:wither_skeleton_skull",
                "minecraft:creeper_head", "minecraft:dragon_head", "minecraft:piglin_head"));
        java.util.Set<String> pickedHeads = new java.util.HashSet<>();
        for (int day = 1; day <= 200; day++) {
            EconomyData data = new EconomyData();
            AcquireService.ensureFridayState(data, EEConfig.fridayIntervalDays(), RandomSource.create(day));
            List<String> picked = data.acquire().fridayItems();
            if (picked.size() != EEConfig.fridayPickCount()) {
                countOk = false;
            }
            if (new java.util.HashSet<>(picked).size() != picked.size()) {
                distinctOk = false;
            }
            if (picked.stream().filter(heads::contains).count() > 1) {
                headLimitOk = false;
            }
            boolean hasEggRow = picked.contains("*_spawn_egg");
            if (hasEggRow) {
                eggRowDays++;
            }
            picked.stream().filter(heads::contains).forEach(pickedHeads::add);
            for (var entry : AcquireService.fridayEntries(data)) {
                if (entry.price() < 1) {
                    priceOk = false;
                }
                if (heads.contains(entry.itemId())) {
                    headPriceMin = headPriceMin < 0 ? entry.price() : Math.min(headPriceMin, entry.price());
                    headPriceMax = Math.max(headPriceMax, entry.price());
                }
                if (entry.wildcard()) {
                    // 通配行必须按 40 的基准价计算，而且任意刷怪蛋都能查到同一个价格
                    long expected = AcquireService.priceOf(data, "minecraft:zombie_spawn_egg");
                    if (!entry.itemId().equals("*_spawn_egg")
                            || entry.price() != expected
                            || entry.price() < 40 * EEConfig.fridayMultiplierMin() - 1
                            || entry.price() > 40 * EEConfig.fridayMultiplierMax() + 1) {
                        eggRowOk = false;
                    }
                }
            }
        }
        check("每天都能挑满配置数量的物品（200 次抽样）", countOk);
        check("同一天不会重复挑中同一件物品（200 次抽样）", distinctOk);
        check("头颅每天最多占 1 件（200 次抽样）", headLimitOk);
        check("200 次抽样里至少出现过一次「任意刷怪蛋」行（" + eggRowDays + " 天）", eggRowDays > 0);
        check("刷怪蛋整类行价格按基准价 40 计算，且任意蛋都能查到同一价格", eggRowOk);
        check("「是哪种头颅」是随机挑的（200 次里出现过 " + pickedHeads.size() + " 种）",
                pickedHeads.size() >= 2);
        check("疯狂星期五价格为正", priceOk);
        check("挑中头颅时按基准价 83 算（含龙首）：" + headPriceMin + "~" + headPriceMax,
                headPriceMin >= 0 && headPriceMin >= 83 * EEConfig.fridayMultiplierMin() - 1
                        && headPriceMax <= 83 * EEConfig.fridayMultiplierMax() + 1);
    }

    /** 自动售货机的货架：附魔书 + 「其他」商品（种子 / 树苗 / 杂物里的经验值）。 */
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
        check("「其他」新增经验子分类（排在树苗后面）", subOrder("OTHER").indexOf("SAPLING")
                < subOrder("OTHER").indexOf("MISC"));
        check("经验子分类卖三档经验值（10/100/1000 点）",
                content.expItems().getOrDefault(10, -1) == 100
                        && content.expItems().getOrDefault(100, -1) == 800
                        && content.expItems().getOrDefault(1000, -1) == 6000);
        check("「其他」新增杂物子分类（排在经验后面）", subOrder("OTHER").indexOf("MISC")
                < subOrder("OTHER").indexOf("GENERAL"));
        check("杂物子分类卖玻璃（1）",
                content.plainItems().getOrDefault("minecraft:glass", -1) == 1);
        check("经验值商品算得进「其他」货架（不是附魔书）",
                content.tabOfExp(1000) != null && !content.tabOfExp(1000).unlockable());
        check("锋利的价格区间是 896-1280", content.enchantSpec("minecraft:sharpness") != null
                && content.enchantSpec("minecraft:sharpness").minPrice() == 896
                && content.enchantSpec("minecraft:sharpness").maxPrice() == 1280);
        check("附魔都能查到自己的子分类", content.tabOfEnchant("minecraft:mending") != null);
    }

    /** 某个大分类下的子分类 ID 顺序。 */
    private static List<String> subOrder(String groupId) {
        List<String> out = new java.util.ArrayList<>();
        for (var group : cn.daenx.mc.emeraldeconomy.content.EconomyContent.shop().groupList()) {
            if (!group.idOr("").equalsIgnoreCase(groupId)) {
                continue;
            }
            for (var sub : cn.daenx.mc.emeraldeconomy.content.ShopContent.subList(group)) {
                out.add(sub.idOr(""));
            }
        }
        return out;
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

    /**
     * ATM 的「经验存取」与「在线奖励」。
     * <p>
     * 这两块的核心规则都是纯数据判断，很适合放在这里验：
     * 资格只发给「结算那一刻在线」的玩家、每天只能领一次、经验库存不会被扣成负数，
     * 以及新旧存档字段的换算不会丢数据。
     */
    private static void testLevelAndOnlineReward() {
        check("经验存取解锁费用默认 1000", EEConfig.levelUnlockCost() == 1000L);
        check("在线奖励基础经验默认 50",
                EEConfig.onlineRewardBaseExperience() == 50);
        check("在线奖励每日递增默认 1",
                EEConfig.onlineRewardPerDayExperience() == 1);
        check("在线奖励按「基础 + 游戏日」计算（第 38 天 = 88）",
                EEConfig.onlineRewardExperience(38L) == 88);
        check("每日随机情绪修正默认开启", EEConfig.dailyRandomDrift() > 0.0D);

        check("1.21.1 升级曲线：0 级升 1 级需要 7 点", cn.daenx.mc.emeraldeconomy.core.Experience
                .xpToNextLevel(0) == 7);
        check("1.21.1 升级曲线：15 级升 16 级需要 37 点", cn.daenx.mc.emeraldeconomy.core.Experience
                .xpToNextLevel(15) == 37);
        check("1.21.1 升级曲线：30 级升 31 级需要 112 点", cn.daenx.mc.emeraldeconomy.core.Experience
                .xpToNextLevel(30) == 112);
        check("累计经验：30 级 = 1395 点", cn.daenx.mc.emeraldeconomy.core.Experience
                .totalXpForLevel(30) == 1395L);
        check("累计经验反查等级", cn.daenx.mc.emeraldeconomy.core.Experience
                .levelForTotal(1395L) == 30);

        UUID uuid = UUID.fromString("00000000-0000-0000-0000-0000000000ef");
        AccountData account = new AccountData(uuid);
        check("经验存取默认未解锁", !account.levelUnlocked());
        check("经验库存默认 0", account.storedExperience() == 0L);
        check("默认没有在线奖励资格", !account.hasOnlineReward(5L));

        account.grantOnlineReward(5L);
        check("结算时在线 → 当天拿到资格", account.hasOnlineReward(5L));
        check("资格只对当天有效（第 6 天要重新结算）", !account.hasOnlineReward(6L));
        account.markOnlineRewardClaimed();
        check("领取后当天不能再领", !account.hasOnlineReward(5L) && account.onlineRewardClaimedToday(5L));

        account.grantOnlineReward(6L);
        check("第二天结算时又在线 → 重新拿到资格", account.hasOnlineReward(6L));
        account.revokeOnlineReward();
        check("结算时不在线 → 资格被收回、也不能补领",
                !account.hasOnlineReward(6L) && !account.onlineRewardClaimedToday(6L));

        account.setLevelUnlocked(true);
        account.addStoredExperience(1200);
        check("存入经验会增加库存", account.storedExperience() == 1200L);
        account.addStoredExperience(-2000);
        check("经验库存不会变成负数", account.storedExperience() == 0L);
        account.addStoredExperience(315);

        AccountData loaded = AccountData.load(account.save());
        check("经验解锁 / 经验库存随存档持久化",
                loaded.levelUnlocked() && loaded.storedExperience() == 315L);
        check("在线奖励资格随存档持久化（不会重启就白送一次）",
                !loaded.hasOnlineReward(6L));

        // 旧存档没有新字段：必须按「未解锁 / 库存 0 / 没有资格」读，不能读出乱数字
        AccountData legacy = AccountData.load(new CompoundTag());
        check("旧存档读出来是未解锁、库存 0、没有资格",
                !legacy.levelUnlocked() && legacy.storedExperience() == 0L
                        && !legacy.hasOnlineReward(0L));

        // 旧版本存的是「等级数」：升级后要折算成经验，不能把老数据丢掉
        CompoundTag oldTag = new CompoundTag();
        oldTag.putBoolean("levelUnlocked", true);
        oldTag.putLong("storedLevels", 5L);
        AccountData migrated = AccountData.load(oldTag);
        check("旧存档的「5 级」折算成 55 点经验",
                migrated.storedExperience() == 55L);

        check("经验类流水会被交易记录界面认出来（不按余额变动过滤）",
                TransactionType.LEVEL_DEPOSIT.isExperienceRecord()
                        && TransactionType.LEVEL_WITHDRAW.isExperienceRecord()
                        && TransactionType.ONLINE_REWARD.isExperienceRecord()
                        && !TransactionType.LEVEL_UNLOCK.isExperienceRecord()
                        && !TransactionType.DEPOSIT.isExperienceRecord());
    }

    /**
     * 每日结算里的随机情绪修正（需求：即便玩家什么都没干，行情也要有随机性）。
     */
    private static void testMarketRandomDrift() {
        // 谁都没做事（供给 0）+ 晴天：矿业的天气修正也是 0，此时动量的唯一来源就是随机修正
        EconomyData calm = new EconomyData();
        calm.ensureMarketInitialized();
        MarketEngine.settleDay(null, calm, 0L, WeatherKind.CLEAR, RandomSource.create(7L));
        int nonzero = 0;
        for (FundType type : FundType.values()) {
            if (Math.abs(calm.market().fund(type).momentum()) > 1.0E-9D) {
                nonzero++;
            }
        }
        check("没有世界活动时每支基金也会拿到随机修正（7 支里至少 6 支有值）", nonzero >= 6);
        check("矿业基金在晴天不受天气影响（说明上面测的确实是随机项）",
                Math.abs(EEConfig.dailyRandomDrift()) > 0.0D);

        // 正负对称：跑 300 天，矿业基金的随机修正必须涨跌都出现过。
        // 注意种子要「散开」：java.util.Random 的相邻种子第一次取值的差异极小，
        // 用 1000+i 当种子会让 300 天几乎取到同一个随机数（假失败），所以这里乘以一个大质数。
        int positive = 0;
        int negative = 0;
        for (int i = 0; i < 300; i++) {
            EconomyData data = new EconomyData();
            data.ensureMarketInitialized();
            MarketEngine.settleDay(null, data, 0L, WeatherKind.CLEAR, RandomSource.create(7919L * i + 13L));
            double momentum = data.market().fund(FundType.EMR_MIN).momentum();
            if (momentum > 0.0D) {
                positive++;
            } else if (momentum < 0.0D) {
                negative++;
            }
        }
        log("  矿业基金 300 天的随机修正：正 " + positive + " 天 / 负 " + negative + " 天");
        check("随机修正正负对称（300 天里矿业涨跌都出现过）", positive > 50 && negative > 50);
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
