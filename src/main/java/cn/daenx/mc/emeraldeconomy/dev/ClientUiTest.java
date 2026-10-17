package cn.daenx.mc.emeraldeconomy.dev;

import cn.daenx.mc.emeraldeconomy.client.ClientEconomyCache;
import cn.daenx.mc.emeraldeconomy.client.screen.AcquisitionScreen;
import cn.daenx.mc.emeraldeconomy.client.screen.AtmScreen;
import cn.daenx.mc.emeraldeconomy.client.screen.ExchangeScreen;
import cn.daenx.mc.emeraldeconomy.client.screen.LotteryScreen;
import cn.daenx.mc.emeraldeconomy.client.screen.TradeScreen;
import cn.daenx.mc.emeraldeconomy.client.screen.VendingScreen;
import cn.daenx.mc.emeraldeconomy.core.EEConstants;
import cn.daenx.mc.emeraldeconomy.lottery.LotteryType;
import cn.daenx.mc.emeraldeconomy.market.FundType;
import cn.daenx.mc.emeraldeconomy.network.payload.ServerPayloads;
import cn.daenx.mc.emeraldeconomy.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 开发者：客户端界面实证测试（默认关闭）。
 * <p>
 * 需求文档里有一大批要求「只有真的进游戏点一遍才能确认」的界面行为：
 * 按钮是否真的存在、点击后是否真的发出请求、服务端的结算是否真的回到了界面。
 * 这个工具把这些事自动化：
 * <ol>
 *     <li>逐个打开八个界面并截图（验证布局与渲染）</li>
 *     <li><b>真的去点击界面上的按钮</b>（用与界面相同的语言键匹配文本，切语言也不失效）</li>
 *     <li>走完「客户端发包 → 服务端结算 → 快照回推 → 界面重建」全链路</li>
 *     <li>用账户余额 / 持仓 / 限购次数等真实数据做断言，结论写进日志文件</li>
 * </ol>
 * 启用方式：{@code -Dee.uitest=true}，并且必须真的连着服务端（否则点了也发不出包）。
 * 截图与日志输出到 {@code run/screenshots/ee/}。
 * <p>
 * 纯开发工具：正式发行版不会运行，也不影响任何游戏逻辑。
 */
@EventBusSubscriber(modid = EEConstants.MOD_ID, value = Dist.CLIENT)
public final class ClientUiTest {

    /** 开关属性。 */
    public static final String PROPERTY = "ee.uitest";

    /** 用例变体属性：为空时跑完整用例；{@code shopfull} 只跑「商店全解锁」；
     *  {@code marketevents} 只跑「随机市场事件」；{@code multi} 跑多人并发（配合 role）。 */
    public static final String VARIANT_PROPERTY = "ee.uitest.variant";

    /** 多人用例里的角色：{@code owner}（先动手的那个）或 {@code peer}（后面做断言的）。 */
    public static final String ROLE_PROPERTY = "ee.uitest.role";

    /** 进入世界后先等多久（等服务端把区块与数据准备好）。 */
    private static final int START_DELAY_TICKS = 80;

    /** 截图后至少再等几刻（截图取上一帧的帧缓冲，要给它时间画出来）。 */
    private static final int RENDER_WAIT = 4;

    /** 一个测试动作。 */
    private interface Action {
        void run(Minecraft minecraft, LocalPlayer player);
    }

    /** 一个带等待时间的测试动作：等待是给「发包 → 回包 → 重建界面」留时间。 */
    private record Step(String name, int waitAfter, Action action) {
    }

    private static final List<Step> STEPS = new ArrayList<>();
    private static final List<String> LOG = new ArrayList<>();

    private static boolean started;
    private static boolean finished;
    private static int delay;
    private static int index;
    private static int wait;
    private static int passed;
    private static int failed;
    private static int shotCount;
    private static long savedBalance;
    private static int newsBefore;
    /** 供电门控测试用的目标方块位置。 */
    private static BlockPos gateTarget;
    /** 本步刚摆好、下一步要挖掉的那一排方块。 */
    private static final List<BlockPos> STAGED = new ArrayList<>();
    /** 供给量基线：基金代码 -> 当日供给量。 */
    private static final java.util.Map<String, Long> SUPPLY_BASE = new java.util.LinkedHashMap<>();
    /** 客户端收到的聊天内容（只保留最近若干条）。 */
    private static final Deque<String> CHAT = new ArrayDeque<>();
    /** 冷却测试前记录的持仓份额。 */
    private static long sharesBeforeCooldown;
    /** 拒绝路径测试用的基准值。 */
    /** 「存入全部」测试用的背包绿宝石基线。 */
    private static long emeraldsBefore;
    /** 等级存取 / 在线奖励测试前的玩家等级基线。 */
    private static int levelsBeforeReward;
    /** 买「经验值商品」之前的经验点基线。 */
    private static long xpBefore;
    /** 疯狂星期五「收购数量不限」用例：当天挑中的第一件物品与它的单价。 */
    private static String fridayItemId;
    private static long fridayItemPrice;
    private static long guardBalance;
    private static long guardDebt;
    private static long guardBetAmount;
    /** 升级费用序列，用于验证「逐级递增」。 */
    private static final List<Long> UPGRADE_COSTS = new ArrayList<>();
    /** 市场事件用例：某个相位结算前的动量快照。 */
    private static final java.util.Map<FundType, Double> MOMENTUM_BEFORE = new java.util.EnumMap<>(FundType.class);
    /** 市场事件用例：已经核对过的相位说明。 */
    private static final List<String> EVENT_PHASES_DONE = new ArrayList<>();
    /** 多人用例：客人是否已经轮询到房主升级出来的格子。 */
    private static boolean peerSawSharedUpgrade;
    /** 多人用例：房主是否已经看到客人改动过的行情。 */
    private static boolean ownerSawSharedPrice;
    /** 多人用例用来核对「行情共享」的固定价格标记。 */
    private static final int SHARED_PRICE_MARKER = 777;

    private ClientUiTest() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    /** 多人用例当前的角色。 */
    private static String role() {
        return System.getProperty(ROLE_PROPERTY, "owner");
    }

    /** 收集聊天内容：/ee supply 之类的命令输出只能从聊天里读。 */
    @SubscribeEvent
    public static void onChatReceived(ClientChatReceivedEvent event) {
        if (!enabled()) {
            return;
        }
        CHAT.addLast(event.getMessage().getString());
        while (CHAT.size() > 200) {
            CHAT.removeFirst();
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!enabled() || finished) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        if (!started) {
            started = true;
            delay = START_DELAY_TICKS;
            String variant = System.getProperty(VARIANT_PROPERTY, "");
            switch (variant) {
                case "shopfull" ->
                    // 这个变体是独立用例：它依赖「配置里把 initialSlots 调满 + 全新存档」，
                    // 和主用例的前置状态互不相干，所以不能混着跑。
                        buildShopFullSteps();
                case "marketevents" -> buildMarketEventSteps();
                case "multi" -> buildMultiPlayerSteps();
                case "mine" -> buildMineSteps();
                default -> {
                    buildSteps();
                    buildSupplyAndFailureSteps();
                }
            }
            log("已进入世界，等待 " + START_DELAY_TICKS + " 刻后开始界面实证测试");
            return;
        }
        if (delay > 0) {
            delay--;
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        if (index >= STEPS.size()) {
            finish();
            return;
        }
        Step step = STEPS.get(index++);
        try {
            log("> " + step.name());
            step.action().run(minecraft, player);
        } catch (Exception exception) {
            failed++;
            log("  !! 动作抛出异常：" + exception);
            exception.printStackTrace();
        }
        wait = Math.max(RENDER_WAIT, step.waitAfter());
    }

    // ------------------------------------------------------------------
    // 测试脚本
    // ------------------------------------------------------------------

    private static void buildSteps() {
        if (!STEPS.isEmpty()) {
            return;
        }

        // ---------- 准备 ----------
        add("准备测试物资（绿宝石 1024 / 小麦 16 / 余额 100000）", 30, (mc, player) -> {
            command(player, "give @s emerald 1024");
            command(player, "give @s wheat 16");
            command(player, "ee give @s 100000");
        });

        // ---------- ATM：四个操作全部真的点一遍 ----------
        add("打开 ATM 界面", 35, (mc, player) -> open(mc, new AtmScreen()));
        add("截图 01 ATM（存款前：余额 100,000 / 信用额度 1,000）", 0,
                (mc, player) -> shot(mc, "01_atm_before_deposit"));
        add("输入 1000 并点击「存入」按钮", 35, (mc, player) -> {
            setNumbers(mc, "1000");
            click(mc, tr("screen.atm.button.deposit"));
        });
        add("截图 02 ATM（存款后：余额应为 100,990）", 0,
                (mc, player) -> shot(mc, "02_atm_after_deposit"));
        add("校验存款", 10, (mc, player) -> {
            log("  实际余额 = " + accountBalance());
            check("存款后余额 = 100000 + (1000 - 1% 手续费 10) = 100990", accountBalance() == 100990L);
        });
        add("输入 100 并点击「取出」按钮", 35, (mc, player) -> {
            setNumbers(mc, "100");
            click(mc, tr("screen.atm.button.withdraw"));
        });
        add("截图 03 ATM（取款后：余额应为 100,889）", 0,
                (mc, player) -> shot(mc, "03_atm_after_withdraw"));
        add("校验取款", 10, (mc, player) -> {
            log("  实际余额 = " + accountBalance());
            check("取款后余额 = 100990 - (100 + 1 手续费) = 100889", accountBalance() == 100889L);
        });
        add("输入 1000 并点击「申请」按钮（贷款）", 35, (mc, player) -> {
            setNumbers(mc, "1000");
            click(mc, tr("screen.atm.button.loan"));
        });
        add("截图 04 ATM（贷款后：余额 101,889 / 欠款 1,000 / 可贷 0）", 0,
                (mc, player) -> shot(mc, "04_atm_after_loan"));
        add("校验贷款", 10, (mc, player) -> {
            log("  实际余额 = " + accountBalance() + "，欠款 = " + accountDebt());
            check("贷款后余额 = 100889 + 1000 = 101889", accountBalance() == 101889L);
            check("贷款后欠款 = 1000", accountDebt() == 1000L);
            check("可贷额度 = 信用额度 - 欠款 = 0",
                    ClientEconomyCache.account != null
                            && ClientEconomyCache.account.availableCredit() == 0L);
        });
        add("输入 500 并点击「还款」按钮", 35, (mc, player) -> {
            setNumbers(mc, "500");
            click(mc, tr("screen.atm.button.repay"));
        });
        add("截图 05 ATM（还款后：余额 101,389 / 欠款 500）", 0,
                (mc, player) -> shot(mc, "05_atm_after_repay"));
        add("校验还款", 10, (mc, player) -> {
            log("  实际余额 = " + accountBalance() + "，欠款 = " + accountDebt());
            check("还款后余额 = 101889 - 500 = 101389", accountBalance() == 101389L);
            check("还款后欠款 = 500", accountDebt() == 500L);
        });

        // ---------- ATM：交易记录界面（点按钮打开，列出全部流水） ----------
        add("从 ATM 点开「交易记录」按钮", 30, (mc, player) -> {
            open(mc, new AtmScreen());
            click(mc, tr("screen.atm.records.title"));
        });
        add("截图 31 交易记录界面（类型 / 金额 / 余额 / 欠款）", 0,
                (mc, player) -> shot(mc, "31_atm_transactions"));
        add("校验交易记录界面", 10, (mc, player) -> {
            var account = ClientEconomyCache.account;
            int count = account == null ? -1 : account.transactions().size();
            log("  流水条数 = " + count + "，界面类型 = " + ClientEconomyCache.currentScreen);
            check("交易记录界面拿到了完整流水（>= 4 条：存/取/贷/还）", count >= 4);
            check("每条流水都带余额与欠款变动",
                    account != null && account.transactions().stream()
                            .allMatch(t -> t.balanceAfter() >= 0L && t.debtAfter() >= 0L));
            check("流水里能看到贷款那一笔（欠款变成 1000）",
                    account != null && account.transactions().stream()
                            .anyMatch(t -> t.type().equals("LOAN") && t.debtAfter() == 1000L));
            check("流水里能看到还款那一笔（欠款变成 500）",
                    account != null && account.transactions().stream()
                            .anyMatch(t -> t.type().equals("REPAY") && t.debtAfter() == 500L));
        });

        add("切到「贷款欠款明细」分类", 20, (mc, player) -> click(mc, tr("screen.atm.records.tab.debt")));
        add("截图 33 交易记录·贷款欠款明细（变动前 → 变动后）", 0,
                (mc, player) -> shot(mc, "33_atm_records_debt"));
        add("切回「账户余额明细」分类", 20, (mc, player) -> click(mc, tr("screen.atm.records.tab.balance")));
        add("校验两个分类与变动前后数字", 10, (mc, player) -> {
            var account = ClientEconomyCache.account;
            log("  流水条数 = " + (account == null ? -1 : account.transactions().size()));
            check("每条流水都带变动前后余额与欠款", account != null
                    && account.transactions().stream()
                    .allMatch(t -> t.balanceAfter() >= 0L && t.debtAfter() >= 0L));
            check("存款不动欠款（变动前后一致）", account != null
                    && account.transactions().stream().filter(t -> t.type().equals("DEPOSIT"))
                    .allMatch(t -> t.debtBefore() == t.debtAfter()));
            check("贷款那笔：欠款 0 → 1000", account != null
                    && account.transactions().stream().anyMatch(t -> t.type().equals("LOAN")
                    && t.debtBefore() == 0L && t.debtAfter() == 1000L));
            check("还款那笔：欠款 1000 → 500", account != null
                    && account.transactions().stream().anyMatch(t -> t.type().equals("REPAY")
                    && t.debtBefore() == 1000L && t.debtAfter() == 500L));
            check("余额明细里不会混进「只动欠款」的贷款利息", account != null
                    && account.transactions().stream().filter(t -> t.type().equals("LOAN_INTEREST"))
                    .allMatch(t -> t.balanceBefore() == t.balanceAfter()));
        });

        // ---------- ATM：存入全部 / 取出全部 ----------
        add("存入全部：先记录背包里的绿宝石与余额", 10, (mc, player) -> {
            emeraldsBefore = countEmeralds(player);
            savedBalance = accountBalance();
            log("  背包绿宝石 = " + emeraldsBefore + "，账户余额 = " + savedBalance);
        });
        add("点「存入全部」", 35, (mc, player) -> {
            open(mc, new AtmScreen());
            click(mc, tr("screen.atm.button.deposit_all"));
        });
        add("校验「存入全部」把背包清空并入账", 10, (mc, player) -> {
            long emeralds = countEmeralds(player);
            long balance = accountBalance();
            long fee = Math.max(1L, (long) Math.floor(emeraldsBefore * 0.01D));
            log("  存入后背包绿宝石 = " + emeralds + "，账户余额 = " + balance
                    + "（预期 " + (savedBalance + emeraldsBefore - fee) + "）");
            check("存入全部后背包里一颗绿宝石都不剩", emeralds == 0L);
            check("存入全部按手续费 1% 入账",
                    balance == savedBalance + emeraldsBefore - fee);
        });
        add("点「取出全部」（余额远超背包容量）", 30, (mc, player) -> {
            savedBalance = accountBalance();
            click(mc, tr("screen.atm.button.withdraw_all"));
        });
        add("校验「取出全部」因背包放不下而整笔失败", 10, (mc, player) -> {
            long balance = accountBalance();
            log("  取出后余额 = " + balance + "，聊天含「背包放不下」= " + chatContains("背包放不下"));
            check("背包容量不足时取出全部被拒绝（余额一分没动）", balance == savedBalance);
            check("取出全部失败给出了界面/聊天提示", chatContains("背包放不下"));
        });
        add("把余额压到 0 再给 800、清空背包（准备测成功路径）", 25, (mc, player) -> {
            command(player, "ee take @s 999999999");
            command(player, "ee give @s 800");
            command(player, "clear @s");
        });
        add("点「取出全部」（这次应成功）", 30, (mc, player) -> click(mc, tr("screen.atm.button.withdraw_all")));
        add("校验「取出全部」成功路径", 10, (mc, player) -> {
            long balance = accountBalance();
            long emeralds = countEmeralds(player);
            log("  取出后余额 = " + balance + "，背包绿宝石 = " + emeralds);
            check("取出全部把余额基本取空（只剩凑不出一个的零头）", balance < 10L);
            check("取出全部真的把绿宝石放进了背包", emeralds >= 790L);
        });
        add("把余额与测试物资补回来，继续后面的用例", 20, (mc, player) -> {
            command(player, "ee give @s 100000");
            command(player, "give @s minecraft:wheat 16");
        });

        // ---------- 交易所 ----------
        add("打开交易所界面", 35, (mc, player) -> open(mc, new ExchangeScreen()));
        add("截图 06 交易所（七支基金 / 买入卖出按钮 / 走势图）", 0,
                (mc, player) -> shot(mc, "06_exchange"));
        add("校验交易所数据", 10, (mc, player) -> {
            var market = ClientEconomyCache.market;
            int funds = market == null ? -1 : market.funds().size();
            log("  基金快照条数 = " + funds);
            check("交易所拿到七支基金快照", funds == 7);
            check("价格历史点数不超过配置上限 20",
                    market != null && market.funds().stream().allMatch(f -> f.history().length <= 20));
            check("每支基金都有收盘价（用于算涨跌幅）",
                    market != null && market.funds().stream().allMatch(f -> f.previousClose() > 0));
        });
        add("点击第 1 行基金的「买入」按钮", 20, (mc, player) -> click(mc, tr("screen.common.buy")));
        add("截图 07 交易弹窗（基金名 / 当前价格 / 数量框 / 预计花费 / 手续费）", 0,
                (mc, player) -> shot(mc, "07_trade_dialog"));
        add("在弹窗里填 5 份并点「确认」", 35, (mc, player) -> {
            setNumbers(mc, "5");
            click(mc, tr("screen.common.confirm"));
        });
        add("截图 08 交易后的交易所", 0, (mc, player) -> shot(mc, "08_after_buy"));
        add("从交易所点开「查看持仓」", 25, (mc, player) -> {
            open(mc, new ExchangeScreen());
            click(mc, tr("screen.exchange.holdings"));
        });
        add("截图 09 持仓界面（份额 / 平均成本 / 市值 / 浮动盈亏 / 已实现）", 0,
                (mc, player) -> shot(mc, "09_holdings"));
        add("校验持仓", 10, (mc, player) -> {
            var market = ClientEconomyCache.market;
            long shares = market == null ? -1 : market.funds().get(FundType.EMR_MIN.ordinal()).shares();
            log("  矿业基金持仓份额 = " + shares);
            check("买入 5 份后持仓份额为 5", shares == 5L);
        });
        add("从交易所点开「市场新闻」", 25, (mc, player) -> {
            open(mc, new ExchangeScreen());
            click(mc, tr("screen.exchange.news"));
        });
        add("截图 10 市场新闻界面", 0, (mc, player) -> shot(mc, "10_market_news"));

        // ---------- 抽奖：三个场次各下一注 ----------
        // 新规则：有未还清的贷款时界面能打开但不能下注（这里欠款还剩 500）
        add("打开抽奖界面", 35, (mc, player) -> open(mc, new LotteryScreen()));
        add("截图 11 抽奖（三个场次 / 赔率 / 选项按钮 / 下注框）", 0,
                (mc, player) -> shot(mc, "11_lottery"));
        add("校验欠款未清时不能下注", 10, (mc, player) -> {
            var lottery = ClientEconomyCache.lottery;
            int betButtons = countButtons(mc, tr("screen.lottery.bet"));
            log("  欠款 = " + accountDebt() + "，hasDebt = " + (lottery == null ? "?" : lottery.hasDebt())
                    + "，「下注」按钮数 = " + betButtons);
            check("有欠款时抽奖界面可以打开（赔率与开奖仍可见）", lottery != null);
            check("有欠款时快照标记 hasDebt",
                    lottery != null && lottery.hasDebt() && accountDebt() > 0L);
            check("有欠款时界面上没有「下注」按钮", betButtons == 0);
        });
        add("去 ATM 把剩余 500 欠款还清", 35, (mc, player) -> {
            open(mc, new AtmScreen());
            setNumbers(mc, "500");
            click(mc, tr("screen.atm.button.repay"));
        });
        add("校验欠款已还清", 10, (mc, player) -> {
            log("  还款后欠款 = " + accountDebt());
            check("再还 500 后欠款清零", accountDebt() == 0L);
        });
        add("重新打开抽奖界面（欠款清零后应恢复下注）", 35, (mc, player) -> open(mc, new LotteryScreen()));
        add("校验欠款还清后可以下注", 10, (mc, player) -> {
            var lottery = ClientEconomyCache.lottery;
            int betButtons = countButtons(mc, tr("screen.lottery.bet"));
            log("  hasDebt = " + (lottery == null ? "?" : lottery.hasDebt())
                    + "，「下注」按钮数 = " + betButtons);
            check("还清欠款后 hasDebt 为 false", lottery != null && !lottery.hasDebt());
            check("还清欠款后重新出现三个「下注」按钮", betButtons == 3);
        });
        add("猜单双：选「单」、下注 50、点第 1 个「下注」", 35, (mc, player) -> {
            click(mc, trFull(LotteryType.ODD_EVEN.optionLangKey(0)));
            setNumbers(mc, "50");
            clickAt(mc, tr("screen.lottery.bet"), 0);
        });
        add("截图 12 猜单双已下注（应显示今日已下注）", 0,
                (mc, player) -> shot(mc, "12_lottery_oddeven"));
        add("校验猜单双", 10, (mc, player) -> {
            var lottery = ClientEconomyCache.lottery;
            var slot = lottery == null ? null : lottery.slots().get(0);
            log("  猜单双 betChoice = " + (slot == null ? "?" : slot.betChoice())
                    + "，金额 = " + (slot == null ? "?" : slot.betAmount()));
            check("猜单双已记录：押「单」50", slot != null && slot.betChoice() == 0 && slot.betAmount() == 50L);
        });
        add("猜大小：选「大」、下注 20、点第 2 个「下注」", 35, (mc, player) -> {
            click(mc, trFull(LotteryType.BIG_SMALL.optionLangKey(1)));
            setNumbers(mc, "20");
            clickAt(mc, tr("screen.lottery.bet"), 1);
        });
        add("猜尾数：选「3」、下注 10、点第 3 个「下注」", 35, (mc, player) -> {
            click(mc, trFull(LotteryType.LAST_DIGIT.optionLangKey(3)));
            setNumbers(mc, "10");
            clickAt(mc, tr("screen.lottery.bet"), 2);
        });
        add("截图 13 三个场次全部下注", 0, (mc, player) -> shot(mc, "13_lottery_all_bet"));
        add("校验三个场次", 10, (mc, player) -> {
            var lottery = ClientEconomyCache.lottery;
            int betted = 0;
            if (lottery != null) {
                for (var slot : lottery.slots()) {
                    if (slot.betChoice() >= 0) {
                        betted++;
                    }
                }
            }
            log("  已下注场次数 = " + betted);
            check("三个场次都已下注", betted == 3);
        });

        // ---------- 商店 ----------
        add("打开自动售货机界面", 35, (mc, player) -> open(mc, new VendingScreen()));
        add("截图 14 售货机（附魔书 / 价格 / 剩余限购 / 升级按钮）", 0,
                (mc, player) -> shot(mc, "14_vending"));
        add("校验商店初始状态", 10, (mc, player) -> {
            var shop = ClientEconomyCache.shop;
            var enchants = enchantGoods(shop);
            log("  已解锁格子 = " + (shop == null ? -1 : shop.unlocked())
                    + "，下一级升级费用 = " + (shop == null ? -1 : shop.nextUpgradeCost())
                    + "，附魔商品数 = " + enchants.size()
                    + "，全部商品数 = " + (shop == null ? -1 : shop.goods().size()));
            check("商店已解锁至少 1 个格子", shop != null && shop.unlocked() >= 1);
            check("每个商品都有今日剩余限购次数",
                    shop != null && shop.goods().stream().allMatch(g -> g.remaining() >= 0));
            // 附魔价格来自配置的分类价格区间：最低 256（穿刺），最高 2560（风爆/冰霜行者等）
            check("附魔书价格落在配置区间 256-2560",
                    enchants.stream().allMatch(g -> g.price() >= 256 && g.price() <= 2560));
            check("每个商品都带大分类与子分类（界面按两级分类排版）",
                    shop != null && shop.goods().stream()
                            .allMatch(g -> g.groupId() != null && !g.groupId().isEmpty()
                                    && g.subId() != null && !g.subId().isEmpty()));
            check("服务端下发了货架标签页（大分类 + 子分类）",
                    shop != null && shop.tabs().size() >= 5
                            && shop.tabs().stream().anyMatch(t -> t.unlockable())
                            && shop.tabs().stream().anyMatch(t -> !t.unlockable()));
            check("「其他」大分类里已有种子与树苗商品",
                    shop != null && shop.goods().stream().anyMatch(g -> !g.enchant() && "SEED".equals(g.subId()))
                            && shop.goods().stream().anyMatch(g -> !g.enchant() && "SAPLING".equals(g.subId())));
        });
        add("点击第一个格子的「购买」按钮", 35, (mc, player) -> click(mc, tr("screen.vending.buy")));
        add("截图 15 购买后（限购次数应减少）", 0, (mc, player) -> shot(mc, "15_vending_after_buy"));
        add("校验购买后限购减少", 10, (mc, player) -> {
            var shop = ClientEconomyCache.shop;
            int remaining = shop == null || shop.goods().isEmpty() ? -1 : shop.goods().get(0).remaining();
            log("  第一个格子剩余限购 = " + remaining);
            check("购买后剩余限购 = 1（默认每日 2 次）", remaining == 1);
        });
        add("点击「升级」按钮（全服共享解锁下一格）", 35, (mc, player) -> clickLastButton(mc));
        add("截图 16 升级后（格子数应 +1）", 0, (mc, player) -> shot(mc, "16_vending_after_upgrade"));
        add("校验升级后格子增加", 10, (mc, player) -> {
            var shop = ClientEconomyCache.shop;
            log("  升级后已解锁格子 = " + (shop == null ? -1 : shop.unlocked()));
            check("升级后解锁格子数 >= 2", shop != null && shop.unlocked() >= 2);
        });

        add("切到「其他」大分类", 20, (mc, player) -> click(mc, tr("shop.group.other")));
        add("截图 36 售货机·其他（种子 / 树苗 / 杂物三个子分类）", 0,
                (mc, player) -> shot(mc, "36_vending_other"));
        add("校验「其他」分类的商品", 10, (mc, player) -> {
            var shop = ClientEconomyCache.shop;
            long others = shop == null ? -1 : shop.goods().stream().filter(g -> !g.enchant()).count();
            boolean seed = shop != null && shop.goods().stream()
                    .anyMatch(g -> !g.enchant() && "SEED".equals(g.subId())
                            && "minecraft:wheat_seeds".equals(g.id()) && g.price() == 8);
            boolean sapling = shop != null && shop.goods().stream()
                    .anyMatch(g -> !g.enchant() && "SAPLING".equals(g.subId())
                            && "minecraft:oak_sapling".equals(g.id()) && g.price() == 16);
            log("  「其他」商品数 = " + others + "，有种子 = " + seed + "，有橡树树苗 = " + sapling);
            check("「其他」大分类里有 6 件商品（1 种子 + 2 树苗 + 3 档经验值）", others == 6);
            check("小麦种子 8 绿宝石、橡树树苗 16 绿宝石", seed && sapling);
        });
        add("直接改 shop.json（把小麦种子改成 99）并 /ee reload", 45, (mc, player) -> {
            if (patchShopItemPrice("minecraft:wheat_seeds", 99)) {
                command(player, "ee reload");
            }
        });
        add("校验热重载生效", 30, (mc, player) -> {
            open(mc, new VendingScreen());
            var shop = ClientEconomyCache.shop;
            int price = -1;
            if (shop != null) {
                for (var good : shop.goods()) {
                    if ("minecraft:wheat_seeds".equals(good.id())) {
                        price = good.price();
                    }
                }
            }
            log("  改文件并 reload 后的小麦种子价格 = " + price);
            check("改了 JSON 并 /ee reload 之后价格立刻变成 99", price == 99);
        });
        add("把价格改回去并再次 reload", 45, (mc, player) -> {
            if (patchShopItemPrice("minecraft:wheat_seeds", 8)) {
                command(player, "ee reload");
            }
        });
        add("校验价格已恢复", 30, (mc, player) -> {
            open(mc, new VendingScreen());
            var shop = ClientEconomyCache.shop;
            int price = -1;
            if (shop != null) {
                for (var good : shop.goods()) {
                    if ("minecraft:wheat_seeds".equals(good.id())) {
                        price = good.price();
                    }
                }
            }
            log("  恢复后的小麦种子价格 = " + price);
            check("价格改回 8 并 reload 后恢复", price == 8);
        });
        add("回到「其他」分类并买一份小麦种子", 30, (mc, player) -> {
            click(mc, tr("shop.group.other"));
            click(mc, tr("screen.vending.buy"));
        });
        add("校验买到了种子", 10, (mc, player) -> {
            long seeds = countItems(player, net.minecraft.world.item.Items.WHEAT_SEEDS);
            log("  背包里的种子 = " + seeds + "，聊天含「小麦种子」= " + chatContains("小麦种子"));
            check("买到的小麦种子进了背包", seeds >= 1L);
            check("界面/聊天提示里写清了买到的是小麦种子", chatContains("小麦种子"));
        });

        // ---------- 自动售货机：杂物子分类里的「经验值」商品（100 余额 → 1000 点经验） ----------
        add("切到「其他 → 杂物」子分类（顺便把余额补足，卖经验值那档不便宜）", 30, (mc, player) -> {
            command(player, "ee give @s 20000");
            // 重新打开一次界面：界面初始化会主动向服务端请求一次快照，
            // 这样下面读到的余额才是「补钱之后」的值（否则读到的还是上一次动作时的旧值）
            open(mc, new VendingScreen());
            click(mc, tr("shop.group.other"));
            click(mc, tr("shop.sub.misc"));
        });
        add("截图 40c 售货机·杂物（10 / 100 / 1000 点经验值）", 0,
                (mc, player) -> shot(mc, "40c_vending_misc_exp"));
        add("校验经验值商品（点数与价格）", 10, (mc, player) -> {
            var shop = ClientEconomyCache.shop;
            boolean ten = shop != null && shop.goods().stream().anyMatch(g -> "MISC".equals(g.subId())
                    && g.exp() == 10 && g.price() == 100);
            boolean hundred = shop != null && shop.goods().stream().anyMatch(g -> "MISC".equals(g.subId())
                    && g.exp() == 100 && g.price() == 800);
            boolean thousand = shop != null && shop.goods().stream().anyMatch(g -> "MISC".equals(g.subId())
                    && g.exp() == 1000 && g.price() == 6000);
            log("  10/100/1000 点经验值都在货架上 = " + ten + "/" + hundred + "/" + thousand);
            check("杂物子分类卖三档经验值（10→100、100→800、1000→6000）",
                    ten && hundred && thousand);
            check("经验值是「经验点」不是等级（货架上写的是点数，不是等级）",
                    shop != null && shop.goods().stream().anyMatch(g -> g.exp() == 10));
        });
        add("记录买经验前的余额与经验点", 10, (mc, player) -> {
            xpBefore = player.totalExperience;
            savedBalance = shopBalance();
            log("  买之前：余额 = " + savedBalance + "，经验点 = " + xpBefore);
        });
        add("点第 3 行的「购买」（1000 点经验值）", 30,
                (mc, player) -> clickAt(mc, tr("screen.vending.buy"), 2));
        add("校验买经验：经验点 +1000、余额 -6000", 15, (mc, player) -> {
            long xpNow = player.totalExperience;
            log("  经验点 " + xpBefore + " → " + xpNow + "，余额 " + savedBalance + " → " + shopBalance());
            check("买 1000 点经验后经验点 +1000", xpNow == xpBefore + 1000L);
            check("买经验扣掉 6000 余额", shopBalance() == savedBalance - 6000L);
        });
        add("切回「附魔书」并再升级一格", 25, (mc, player) -> {
            clearChat();
            click(mc, tr("shop.group.enchanted_book"));
        });
        add("点「升级」（应提示解锁了哪个附魔）", 30, (mc, player) -> clickLastButton(mc));
        add("校验升级提示里带上了附魔名与格子数", 10, (mc, player) -> {
            String line = chatLineContaining("解锁了");
            String enchant = firstGroup(line, "解锁了\\s*([^，,]+)");
            log("  升级提示 = " + line + "（解析出附魔名 = " + enchant + "）");
            check("升级提示里带上了新解锁的附魔名", enchant != null
                    && !enchant.isEmpty() && !enchant.contains("emerald_economy.")
                    && !enchant.contains("minecraft:"));
            check("升级提示里带上了当前解锁格子数", line != null && line.contains("个格子"));
        });

        // ---------- 收购 ----------
        add("打开自动收购机界面", 35, (mc, player) -> open(mc, new AcquisitionScreen()));
        add("截图 17 收购（分类标签 / 物品列表 / 出售按钮）", 0,
                (mc, player) -> shot(mc, "17_acquisition"));
        add("校验收购分类", 10, (mc, player) -> {
            var acquire = ClientEconomyCache.acquire;
            int categories = acquire == null ? -1 : acquire.categories().size();
            log("  收购分类数 = " + categories);
            check("收购界面至少四个常驻分类", categories >= 4);
            check("农业分类里有小麦", acquire != null && acquire.categories().stream()
                    .anyMatch(c -> c.entries().stream()
                            .anyMatch(e -> e.itemId().equals("minecraft:wheat"))));
        });
        add("切到「矿物」分类", 20, (mc, player) -> click(mc, tr("acquire.category.mining")));
        add("截图 18 矿物分类", 0, (mc, player) -> shot(mc, "18_acquisition_mining"));
        add("记录出售前余额（读收购机快照里的余额）", 10, (mc, player) -> {
            var acquire = ClientEconomyCache.acquire;
            savedBalance = acquire == null ? Long.MIN_VALUE : acquire.balance();
            log("  出售前余额 = " + savedBalance);
        });
        add("切回「农业产物」并出售 16 个小麦", 35, (mc, player) -> {
            click(mc, tr("acquire.category.agriculture"));
            setNumbers(mc, "16");
            click(mc, tr("screen.acquire.sell"));
        });
        add("截图 19 出售后（余额应 +16）", 0, (mc, player) -> shot(mc, "19_acquisition_after_sell"));
        add("校验出售结果", 10, (mc, player) -> {
            var acquire = ClientEconomyCache.acquire;
            long now = acquire == null ? Long.MIN_VALUE : acquire.balance();
            log("  出售前 " + savedBalance + " → 出售后 " + now);
            // 2026-10 调价：农业产物收购价降为原来的 1/6（最低 1），小麦 8 → 1
            check("出售 16 个小麦（单价 1）后余额 +16", now == savedBalance + 16L);
        });

        add("切到「杂物」分类", 20, (mc, player) -> click(mc, tr("acquire.category.misc")));
        add("截图 37 收购机·杂物（不祥旗帜 / 唱片一行 / 潜影壳 / 幽匿感测体）", 0,
                (mc, player) -> shot(mc, "37_acquire_misc"));
        add("校验杂物分类", 10, (mc, player) -> {
            var acquire = ClientEconomyCache.acquire;
            log("  杂物条目数 = " + entriesOf(acquire, "MISC").size());
            check("杂物分类里有山羊角、潜影壳、马铠（马铠是一整行标签）",
                    hasEntry(acquire, "MISC", "minecraft:goat_horn")
                            && hasEntry(acquire, "MISC", "minecraft:shulker_shell")
                            && hasEntry(acquire, "MISC", "#emerald_economy:acquire/horse_armors"));
            // 本轮新增：不祥旗帜 / 恶魂之泪 / 岩浆膏 / 腐肉 / 蜘蛛眼 / 幽匿感测体
            check("杂物新增不祥旗帜 50、恶魂之泪 20、岩浆膏 5、腐肉 1、蜘蛛眼 1、幽匿感测体 20",
                    entryPrice(acquire, "MISC", "minecraft:white_banner") == 50L
                            && entryPrice(acquire, "MISC", "minecraft:ghast_tear") == 20L
                            && entryPrice(acquire, "MISC", "minecraft:magma_cream") == 5L
                            && entryPrice(acquire, "MISC", "minecraft:rotten_flesh") == 1L
                            && entryPrice(acquire, "MISC", "minecraft:spider_eye") == 1L
                            && entryPrice(acquire, "MISC", "minecraft:sculk_sensor") == 20L);
            check("粘液球 2、烈焰棒 5",
                    entryPrice(acquire, "MISC", "minecraft:slime_ball") == 2L
                            && entryPrice(acquire, "MISC", "minecraft:blaze_rod") == 5L);
            // 需求 5：唱片不再一件件列出来，改成「一行、卖任意一种」
            check("唱片只占一行（标签行，不再列出 13 号唱片等具体条目）",
                    hasEntry(acquire, "MISC", "#emerald_economy:acquire/music_discs")
                            && !hasEntry(acquire, "MISC", "minecraft:music_disc_13"));
            check("这一行有配置好的显示名（不是裸标签 ID）", hasTagRowName(acquire, "MISC",
                    "#emerald_economy:acquire/music_discs"));
        });
        // 需求 5 的落地验证：背包里放两张不同类型唱片 → 点「唱片」那一行全卖掉
        add("给两张不同唱片（等它们真的进了背包）", 30, (mc, player) -> {
            command(player, "give @s minecraft:music_disc_13 1");
            command(player, "give @s minecraft:music_disc_cat 1");
        });
        add("点「唱片」那一行的「出售」（数量框填 2，把两种唱片一起卖掉）", 35, (mc, player) -> {
            savedBalance = ClientEconomyCache.acquire == null
                    ? Long.MIN_VALUE : ClientEconomyCache.acquire.balance();
            // 「出售」按钮在背包里没有这类物品时是灰的（点不动），所以要先给物品、再点按钮
            setNumbers(mc, "2");
            clickAt(mc, tr("screen.acquire.sell"),
                    sellButtonIndex(ClientEconomyCache.acquire, "MISC",
                            "#emerald_economy:acquire/music_discs"));
        });
        add("校验「唱片一行」把两种唱片一起卖了（3 × 2 = 6）", 15, (mc, player) -> {
            long now = ClientEconomyCache.acquire == null
                    ? Long.MIN_VALUE : ClientEconomyCache.acquire.balance();
            log("  卖唱片前 " + savedBalance + " → 卖唱片后 " + now);
            check("一行标签卖掉了两张不同类型的唱片（各 3 绿宝石）", now == savedBalance + 6L);
            check("卖完以后背包里没有唱片了", inventoryCount(player, "minecraft:music_disc_13") == 0L
                    && inventoryCount(player, "minecraft:music_disc_cat") == 0L);
        });
        add("截图 40b 收购机·杂物（卖完唱片后）", 0, (mc, player) -> shot(mc, "40b_acquire_disc_sold"));

        add("给 16 个小麦并切到「农业产物」（准备验按钮置灰）", 30, (mc, player) -> {
            command(player, "give @s minecraft:wheat 16");
            click(mc, tr("acquire.category.agriculture"));
        });
        add("校验「背包里没有的物品，出售按钮是灰的」", 15, (mc, player) -> {
            // 农业分类里只有小麦是背包里有的，所以它可用、别的都灰
            List<Boolean> states = sellButtonStates(mc);
            long enabled = states.stream().filter(b -> b).count();
            log("  农业分类里可出售的行数 = " + enabled + " / " + states.size());
            check("手里有小麦 → 对应那行按钮可用", !states.isEmpty() && states.get(0));
            check("背包里没有的物品 → 出售按钮被置灰",
                    states.size() > 1 && states.stream().anyMatch(b -> !b));
        });
        add("切到「畜牧」分类", 20, (mc, player) -> click(mc, tr("acquire.category.livestock")));
        add("截图 38 收购机·畜牧（鱼肉类 / 骨粉 / 鸡蛋每 8 个）", 0,
                (mc, player) -> shot(mc, "38_acquire_livestock"));
        add("校验批次与鱼类、骨粉", 10, (mc, player) -> {
            var acquire = ClientEconomyCache.acquire;
            String livestock = "LIVESTOCK";
            log("  鸡蛋批次 = " + entryBatch(acquire, livestock, "minecraft:egg")
                    + "，羽毛批次 = " + entryBatch(acquire, livestock, "minecraft:feather"));
            check("鸡蛋与羽毛都是 8 个一份", entryBatch(acquire, livestock, "minecraft:egg") == 8
                    && entryBatch(acquire, livestock, "minecraft:feather") == 8);
            check("畜牧里新增了鱼肉类（鳕鱼一类，占一行标签行）",
                    hasEntry(acquire, livestock, "#minecraft:fishes"));
            check("畜牧里新增了骨头（不是骨粉）", hasEntry(acquire, livestock, "minecraft:bone")
                    && !hasEntry(acquire, livestock, "minecraft:bone_meal"));
            check("农业里新增毒马铃薯（10）",
                    entryPrice(acquire, "AGRICULTURE", "minecraft:poisonous_potato") == 10L);
            check("农业里竹子是 4 个一份、甘蔗单价 2",
                    entryBatch(acquire, "AGRICULTURE", "minecraft:bamboo") == 4
                            && entryPrice(acquire, "AGRICULTURE",
                            "minecraft:sugar_cane") == 2L);
            check("矿物里新增下界合金碎片（10）",
                    entryPrice(acquire, "MINING",
                            "minecraft:netherite_scrap") == 10L);
        });
        add("切到「林业产物」看整类收购的那几行", 25, (mc, player) ->
                click(mc, tr("acquire.category.forestry")));
        add("校验林业：树苗 / 花 / 蘑菇各占一行，仙人掌 1", 10, (mc, player) -> {
            var acquire = ClientEconomyCache.acquire;
            log("  林业条目数 = " + entriesOf(acquire, "FORESTRY").size());
            check("树苗 / 花 / 蘑菇都是「一行标签行」",
                    hasEntry(acquire, "FORESTRY", "#minecraft:saplings")
                            && hasEntry(acquire, "FORESTRY", "#emerald_economy:acquire/flowers")
                            && hasEntry(acquire, "FORESTRY", "#emerald_economy:acquire/mushrooms"));
            check("这三行都有显示名（树苗任意 / 花任意 / 蘑菇任意）",
                    hasTagRowName(acquire, "FORESTRY", "#minecraft:saplings")
                            && hasTagRowName(acquire, "FORESTRY", "#emerald_economy:acquire/flowers")
                            && hasTagRowName(acquire, "FORESTRY", "#emerald_economy:acquire/mushrooms"));
            check("林业里收仙人掌（1）",
                    entryPrice(acquire, "FORESTRY", "minecraft:cactus") == 1L);
        });
        add("给 16 个鸡蛋并切回「畜牧」（数字框保持默认 1，复现需求方遇到的场景）", 30, (mc, player) -> {
            command(player, "give @s minecraft:egg 16");
            click(mc, tr("acquire.category.livestock"));
            savedBalance = ClientEconomyCache.acquire == null
                    ? Long.MIN_VALUE : ClientEconomyCache.acquire.balance();
        });
        add("点鸡蛋那行的「出售」（数字框不动）", 30, (mc, player) -> {
            // 界面上每页 8 行、按钮按行序创建，所以要按「鸡蛋在列表里的序号」点对应那个按钮
            clickAt(mc, tr("screen.acquire.sell"),
                    sellButtonIndex(ClientEconomyCache.acquire, "LIVESTOCK",
                            "minecraft:egg"));
        });
        add("截图 39 卖完鸡蛋（余额 +1）", 0, (mc, player) -> shot(mc, "39_acquire_after_egg"));
        add("校验批次收购的结算", 10, (mc, player) -> {
            long now = ClientEconomyCache.acquire == null
                    ? Long.MIN_VALUE : ClientEconomyCache.acquire.balance();
            long eggs = countItems(player, net.minecraft.world.item.Items.EGG);
            log("  出售前 " + savedBalance + " → 出售后 " + now + "，背包剩鸡蛋 " + eggs);
            // 数字框里是默认的 1（不到一份），界面会自动改成「把手里的整份全卖掉」= 16 个 = 2 份
            check("数字框填 1 也能卖掉：16 个鸡蛋换 2 绿宝石", now == savedBalance + 2L);
            check("整份的鸡蛋都被扣掉", eggs == 0L);
        });

        // ---------- 红石块供电门控：真实右键 ----------
        // 需求开头那条硬要求的实证：
        // 「所有功能方块可放置在任意方块上，但只有放置在红石块上方时才能正常工作；
        //   否则右键点击时提示：未接通红石电源」
        add("关掉界面并把视角摆平（右键测试用）", 15, (mc, player) -> {
            mc.setScreen(null);
            command(player, String.format(Locale.ROOT, "tp @s ~ ~ ~ %.1f 0", player.getYRot()));
        });
        add("在前方 3 格、与头同高处放一台 ATM（下方是泥土）", 30, (mc, player) -> {
            gateTarget = gateTargetOf(player);
            log("  目标方块 = " + xyz(gateTarget));
            command(player, "setblock " + xyz(gateTarget.below()) + " minecraft:dirt");
            placeLower(player, gateTarget, "atm");
            placeUpper(player, gateTarget, "atm");
        });
        add("对着未接电的 ATM 右键 → 应只聊天提示、不弹界面", 30, (mc, player) -> {
            boolean hit = rightClick(mc, player);
            log("  准星命中目标机器 = " + hit);
            check("准星确实打在机器上（走的是真实交互路径）", hit);
            check("未接红石电源时不会打开界面", mc.screen == null);
        });
        add("把机床下方换成红石块（接通电源）", 30, (mc, player) ->
                command(player, "setblock " + xyz(gateTarget.below()) + " minecraft:redstone_block"));
        add("对着已接电的 ATM 右键", 20, (mc, player) -> {
            boolean hit = rightClick(mc, player);
            log("  准星命中目标机器 = " + hit);
        });
        add("校验 ATM 界面已被服务端通知打开", 10,
                (mc, player) -> check("接通红石块后右键打开 ATM 界面", mc.screen instanceof AtmScreen));
        add("截图 20 右键真实打开出来的 ATM 界面", 0,
                (mc, player) -> shot(mc, "20_atm_opened_by_click"));
        add("关掉界面，看一眼ATM的外观", 20, (mc, player) -> mc.setScreen(null));
        add("截图 machine_atm（ATM外观）", 0,
                (mc, player) -> shot(mc, "machine_atm"));

        // 注意：/setblock 是服务端下一格才生效的，客户端世界状态再晚一格才更新，
        // 所以「放机器」与「右键」必须分成两个动作，否则射线检测打到的还是旧方块。
        add("拆掉 ATM，准备换交易所电脑", 20, (mc, player) -> clearMachine(mc, player, gateTarget));
        add("放交易所电脑（下半格）", 20, (mc, player) -> placeLower(player, gateTarget, "exchange"));
        add("放交易所电脑（上半格）", 20, (mc, player) -> placeUpper(player, gateTarget, "exchange"));
        add("对着交易所电脑右键", 20, (mc, player) -> rightClick(mc, player));
        add("校验交易所界面已打开", 10,
                (mc, player) -> check("交易所电脑右键打开交易所界面", mc.screen instanceof ExchangeScreen));
        add("关掉界面，看一眼交易所电脑的外观", 20, (mc, player) -> mc.setScreen(null));
        add("截图 machine_exchange（交易所电脑外观）", 0,
                (mc, player) -> shot(mc, "machine_exchange"));

        add("拆掉上一台机器", 20, (mc, player) -> clearMachine(mc, player, gateTarget));
        add("放新机器（下半格）", 20, (mc, player) -> placeLower(player, gateTarget, "lottery"));
        add("放新机器（上半格）", 20, (mc, player) -> placeUpper(player, gateTarget, "lottery"));
        add("对着抽奖机右键", 20, (mc, player) -> rightClick(mc, player));
        add("校验抽奖界面已打开", 10,
                (mc, player) -> check("抽奖机右键打开抽奖界面", mc.screen instanceof LotteryScreen));
        add("关掉界面，看一眼抽奖机的外观", 20, (mc, player) -> mc.setScreen(null));
        add("截图 machine_lottery（抽奖机外观）", 0,
                (mc, player) -> shot(mc, "machine_lottery"));

        add("拆掉上一台机器", 20, (mc, player) -> clearMachine(mc, player, gateTarget));
        add("放新机器（下半格）", 20, (mc, player) -> placeLower(player, gateTarget, "vending"));
        add("放新机器（上半格）", 20, (mc, player) -> placeUpper(player, gateTarget, "vending"));
        add("对着自动售货机右键", 20, (mc, player) -> rightClick(mc, player));
        add("校验商店界面已打开", 10,
                (mc, player) -> check("自动售货机右键打开商店界面", mc.screen instanceof VendingScreen));
        add("关掉界面，看一眼自动售货机的外观", 20, (mc, player) -> mc.setScreen(null));
        add("截图 machine_vending（自动售货机外观）", 0,
                (mc, player) -> shot(mc, "machine_vending"));

        add("拆掉上一台机器", 20, (mc, player) -> clearMachine(mc, player, gateTarget));
        add("放新机器（下半格）", 20, (mc, player) -> placeLower(player, gateTarget, "acquisition"));
        add("放新机器（上半格）", 20, (mc, player) -> placeUpper(player, gateTarget, "acquisition"));
        add("对着自动收购机右键", 20, (mc, player) -> rightClick(mc, player));
        add("校验收购界面已打开", 10,
                (mc, player) -> check("自动收购机右键打开收购界面", mc.screen instanceof AcquisitionScreen));
        add("截图 21 右键真实打开出来的收购界面", 0,
                (mc, player) -> shot(mc, "21_acquisition_opened_by_click"));
        add("关掉界面，看一眼自动收购机的外观", 20, (mc, player) -> mc.setScreen(null));
        add("截图 machine_acquisition（自动收购机外观）", 0,
                (mc, player) -> shot(mc, "machine_acquisition"));

        // ---------- 模型 / 贴图 硬诊断 ----------
        add("诊断：检查机器方块的模型与贴图是否真的加载了", 10, (mc, player) -> {
            mc.setScreen(null);
            var state = mc.level.getBlockState(gateTarget);
            log("  目标位置的方块 = " + state.getBlock());
            var missing = mc.getModelManager().getMissingModel();
            var blockModel = mc.getBlockRenderer().getBlockModel(state);
            log("  方块模型是否 missing = " + (blockModel == missing));

            // 物品模型的 key 是 <id>#inventory（standalone 是另一套东西，查不到会误报）
            var upperState = mc.level.getBlockState(gateTarget.above());
            log("  上半格方块 = " + upperState.getBlock()
                    + "，渲染类型 = " + upperState.getRenderShape());
            check("机器上半格不参与渲染（避免空模型退化成 missing 品红方块）",
                    upperState.getRenderShape() == net.minecraft.world.level.block.RenderShape.INVISIBLE);
            var itemModel = mc.getModelManager().getModel(
                    new ModelResourceLocation(EEConstants.id("atm"), "inventory"));
            log("  物品模型(emerald_economy:atm#inventory) 是否 missing = " + (itemModel == missing));
            for (int slot = 0; slot < 12; slot++) {
                var stack = player.getInventory().getItem(slot);
                if (!stack.isEmpty()) {
                    log("  背包槽 " + slot + " = "
                            + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())
                            + " x" + stack.getCount());
                }
            }

            var atlas = mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
            var sprite = atlas.apply(EEConstants.id("block/machine_base"));
            var missingSprite = atlas.apply(MissingTextureAtlasSprite.getLocation());
            log("  machine_base 贴图是否 missing = " + (sprite == missingSprite)
                    + "，内容尺寸 = " + sprite.contents().width() + "x" + sprite.contents().height());
            var faceSprite = atlas.apply(EEConstants.id("block/atm_face"));
            log("  atm_face 贴图是否 missing = " + (faceSprite == missingSprite));

            check("机器方块模型已成功烘焙（不是 missing 模型）", blockModel != missing);
            check("机器物品模型已成功加载（不是 missing 模型）", itemModel != missing);
            check("machine_base 贴图已进入方块图集", sprite != missingSprite);
            check("atm_face 贴图已进入方块图集", faceSprite != missingSprite);
        });
        add("站远一点拍一张只有机器的净景（看外观）", 30, (mc, player) -> {
            // 清掉地上的掉落物，避免干扰观察
            command(player, "kill @e[type=item]");
        });
        add("打印机器上下四周的方块，确认现场到底有什么", 5, (mc, player) -> {
            BlockPos t = gateTarget;
            String[] labels = {"下方", "下半格", "上半格", "上方", "北侧", "南侧"};
            BlockPos[] positions = {t.below(), t, t.above(), t.above(2),
                    t.north(), t.south()};
            for (int i = 0; i < positions.length; i++) {
                log("  " + labels[i] + " " + xyz(positions[i]) + " = "
                        + mc.level.getBlockState(positions[i]).getBlock());
            }
        });
        add("贴到机器正面 4 格、眼睛与机器同高（拍特写）", 25, (mc, player) -> {
            BlockPos t = gateTarget;
            // 站在地面（脚 y = 机器下半格 y - 1）上，眼睛正好落在下半格的高度区间里
            command(player, String.format(Locale.ROOT,
                    "tp @s %.1f %.1f %.1f 0 0", t.getX() + 0.5, (double) (t.getY() - 1), t.getZ() - 4.5));
        });
        add("清掉掉落物，并列出机器 8 格内的所有实体", 20, (mc, player) -> {
            command(player, "kill @e[type=item]");
        });
        add("列出附近实体（确认品红/金色是方块还是掉落物）", 15, (mc, player) -> {
            double radius = 8.0D;
            var box = new net.minecraft.world.phys.AABB(
                    gateTarget.getX() - radius, gateTarget.getY() - radius, gateTarget.getZ() - radius,
                    gateTarget.getX() + radius, gateTarget.getY() + radius, gateTarget.getZ() + radius);
            var entities = mc.level.getEntities((net.minecraft.world.entity.Entity) null, box);
            log("  机器 8 格内实体数 = " + entities.size());
            for (var entity : entities) {
                log("   - " + net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                        .getKey(entity.getType()) + " @ " + entity.position());
            }
        });
        add("截图 25 机器外观特写（红石块 + 机器，已清场）", 20, (mc, player) -> {
            shot(mc, "25_machine_look");
        });

        // ---------- 每日结算：走真实的 tick 跨天路径（不用 /ee settle） ----------
        add("关掉界面并打开交易所（结算后靠它接收刷新）", 30, (mc, player) -> {
            mc.setScreen(null);
            open(mc, new ExchangeScreen());
        });
        add("记录跨天前的市场新闻条数", 10, (mc, player) -> {
            newsBefore = newsCount();
            log("  跨天前新闻条数 = " + newsBefore);
        });
        add("用 /time set 推到第 1 天（触发真实跨天结算）", 45, (mc, player) -> {
            command(player, "time set 24000");
            log("  已 /time set 24000，等待 tick 自动完成每日结算");
        });
        add("截图 22 跨天结算后的交易所", 0,
                (mc, player) -> shot(mc, "22_exchange_after_day_change"));
        add("校验 tick 自动完成了每日结算", 10, (mc, player) -> {
            int now = newsCount();
            log("  跨天前 " + newsBefore + " → 跨天后 " + now + " 条新闻");
            check("时间跨天后 tick 自动完成了结算（市场新闻增加）", now > newsBefore);
        });
        add("打开抽奖界面看开奖结果（界面会主动拉最新数据）", 35,
                (mc, player) -> open(mc, new LotteryScreen()));
        add("截图 23 开奖后的抽奖界面（应显示下注已清空 + 上次开奖结果）", 0,
                (mc, player) -> shot(mc, "23_lottery_after_draw"));
        add("校验开奖结果已同步到界面", 10, (mc, player) -> {
            var lottery = ClientEconomyCache.lottery;
            boolean drawn = lottery != null && lottery.slots().stream().anyMatch(s -> s.lastResult() >= 0);
            boolean cleared = lottery != null && lottery.slots().stream().allMatch(s -> s.betChoice() < 0);
            log("  抽奖已开奖 = " + drawn + "，下注已清空 = " + cleared);
            check("三个场次都产生了开奖结果", drawn);
            check("开奖后当天所有下注被清空", cleared);
        });
        add("回到交易所并点开「市场新闻」", 30, (mc, player) -> {
            open(mc, new ExchangeScreen());
            click(mc, tr("screen.exchange.news"));
        });
        add("截图 24 结算后的市场新闻", 0,
                (mc, player) -> shot(mc, "24_news_after_day_change"));
        add("校验结算播报已进入市场新闻", 10, (mc, player) ->
                check("跨天结算在新闻里留下了记录", newsCount() > newsBefore));

        // ---------- ATM：在线奖励（跨天结算时在线 → 有资格；领一次 +1 级） ----------
        add("打开 ATM 看在线奖励状态（刚跨天结算完，玩家在线）", 35, (mc, player) -> {
            open(mc, new AtmScreen());
        });
        add("校验「结算时在线 → 当天有领取资格」", 10, (mc, player) -> {
            var account = ClientEconomyCache.account;
            log("  可领取 = " + (account != null && account.onlineRewardReady())
                    + "，已领取 = " + (account != null && account.onlineRewardClaimed())
                    + "，奖励等级数 = " + (account == null ? -1 : account.onlineRewardLevels()));
            check("跨天结算后在线玩家拿到了当日领取资格",
                    account != null && account.onlineRewardReady());
            check("奖励是「等级」而非经验值（配置为 1 级）",
                    account != null && account.onlineRewardLevels() == 1);
        });
        add("截图 40 ATM·在线奖励（可领取状态）", 0, (mc, player) -> shot(mc, "40_atm_online_reward"));
        add("点「领取奖励」", 30, (mc, player) -> {
            levelsBeforeReward = player.experienceLevel;
            click(mc, tr("screen.atm.online.claim"));
        });
        add("校验在线奖励：等级 +1、当天不能再领", 15, (mc, player) -> {
            var account = ClientEconomyCache.account;
            log("  领奖前等级 = " + levelsBeforeReward + " → 领奖后 = " + player.experienceLevel);
            check("领取后等级 +1（35 级 → 36 级，不是加经验值）",
                    player.experienceLevel == levelsBeforeReward + 1);
            check("领完之后当天没有资格了",
                    account != null && !account.onlineRewardReady() && account.onlineRewardClaimed());
            check("流水里留下了「在线奖励」这一笔", account != null
                    && account.transactions().stream().anyMatch(t -> t.type().equals("ONLINE_REWARD")));
        });
        add("再点一次「领取奖励」（按钮此时应该已经消失）", 20, (mc, player) -> {
            boolean exists = buttonExists(mc, tr("screen.atm.online.claim"));
            log("  领取按钮还存在 = " + exists);
            check("领过之后界面不再给出领取按钮", !exists);
        });

        // ---------- ATM：等级存取（默认锁定 → 付费解锁 → 存 / 取等级） ----------
        add("给 5000 余额与 20 级（准备测等级存取）", 30, (mc, player) -> {
            command(player, "ee give @s 5000");
            command(player, "xp add @s 20 levels");
        });
        add("打开 ATM（界面初始化会拉一次最新快照）", 35, (mc, player) -> open(mc, new AtmScreen()));
        add("记录基准：余额与当前等级", 10, (mc, player) -> {
            savedBalance = accountBalance();
            levelsBeforeReward = player.experienceLevel;
            log("  基准：余额 = " + savedBalance + "，等级 = " + levelsBeforeReward);
        });
        add("校验默认未解锁", 10, (mc, player) -> {
            var account = ClientEconomyCache.account;
            log("  已解锁 = " + (account != null && account.levelUnlocked())
                    + "，解锁费用 = " + (account == null ? -1 : account.levelUnlockCost()));
            check("等级存取对每个玩家默认关闭", account != null && !account.levelUnlocked());
            check("解锁费用来自配置（1000）", account != null && account.levelUnlockCost() == 1000L);
            check("解锁前界面只有解锁按钮、没有存取按钮",
                    buttonExists(mc, tr("screen.atm.level.unlock", money(1000L)))
                            && !buttonExists(mc, tr("screen.atm.level.deposit")));
        });
        add("截图 41 ATM·等级存取（未解锁）", 0, (mc, player) -> shot(mc, "41_atm_level_locked"));
        add("点「解锁」按钮（花 1000 余额）", 30, (mc, player) ->
                click(mc, tr("screen.atm.level.unlock", money(1000L))));
        add("校验解锁成功：扣 1000、状态变为已解锁", 20, (mc, player) -> {
            var account = ClientEconomyCache.account;
            log("  解锁后余额 = " + accountBalance() + "（解锁前 " + savedBalance + "）");
            check("解锁扣掉 1000 余额", accountBalance() == savedBalance - 1000L);
            check("账户已标记为解锁", account != null && account.levelUnlocked());
        });
        add("记录存入前的等级", 10, (mc, player) -> {
            levelsBeforeReward = player.experienceLevel;
            log("  存入前等级 = " + levelsBeforeReward);
        });
        add("在等级框里填 5，点「存入等级」", 35, (mc, player) -> {
            setNumbers(mc, "5");
            click(mc, tr("screen.atm.level.deposit"));
        });
        add("截图 42 ATM·等级存取（存了 5 级）", 0, (mc, player) -> shot(mc, "42_atm_level_stored"));
        add("校验存入等级：玩家掉 5 级、ATM 里多 5 级", 20, (mc, player) -> {
            var account = ClientEconomyCache.account;
            log("  玩家等级 " + levelsBeforeReward + " → " + player.experienceLevel
                    + "，ATM 库存 = " + (account == null ? -1 : account.storedLevels()));
            check("存入 5 级后玩家等级 -5", player.experienceLevel == levelsBeforeReward - 5);
            check("ATM 里存下了 5 级", account != null && account.storedLevels() == 5L);
        });
        add("点「取出等级」（同样填 5）", 35, (mc, player) -> {
            setNumbers(mc, "5");
            click(mc, tr("screen.atm.level.withdraw"));
        });
        add("校验取出等级：等级回到原样、ATM 清空", 20, (mc, player) -> {
            var account = ClientEconomyCache.account;
            log("  取出后玩家等级 = " + player.experienceLevel
                    + "，ATM 库存 = " + (account == null ? -1 : account.storedLevels()));
            check("取出 5 级后玩家等级回来", player.experienceLevel == levelsBeforeReward);
            check("ATM 库存清零", account != null && account.storedLevels() == 0L);
            check("等级存取留下了流水（存入 / 取出各一笔）", account != null
                    && account.transactions().stream().anyMatch(t -> t.type().equals("LEVEL_DEPOSIT"))
                    && account.transactions().stream().anyMatch(t -> t.type().equals("LEVEL_WITHDRAW")));
        });
        add("点开交易记录，确认等级类流水也列在余额明细里", 30, (mc, player) -> {
            open(mc, new AtmScreen());
            click(mc, tr("screen.atm.records.title"));
        });
        add("截图 43 交易记录·等级存取（金额列写的是「级」）", 0,
                (mc, player) -> shot(mc, "43_atm_level_records"));
        add("校验等级流水没有因为「余额没变」被过滤掉", 10, (mc, player) -> {
            var account = ClientEconomyCache.account;
            check("余额明细里能看到等级类流水（服务端确实推了这几笔）", account != null
                    && account.transactions().stream().filter(t -> t.type().startsWith("LEVEL_"))
                    .count() >= 2);
        });
    }

    /** 前方 3 格、与头同高的目标方块（俯仰角 0 时准星正好落在它上面）。 */
    private static BlockPos gateTargetOf(LocalPlayer player) {
        BlockPos feet = player.blockPosition();
        Direction facing = player.getDirection();
        return feet.relative(facing, 3).atY(feet.getY() + 1);
    }

    private static String xyz(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    /**
     * 用命令把目标位置清空。
     * <p>
     * 必须先清空再放新机器：直接 setblock 覆盖旧机器时，旧的上半格会被拆掉，
     * 而这次拆除的连锁更新又会把刚放上去的新下半格一起拆掉，机器就凭空消失了。
     */
    private static void clearMachine(Minecraft minecraft, LocalPlayer player, BlockPos target) {
        minecraft.setScreen(null);
        command(player, "setblock " + xyz(target) + " minecraft:air");
        command(player, "kill @e[type=item]");
    }

    /** 放机器的下半格（渲染整机的那一半）。 */
    private static void placeLower(LocalPlayer player, BlockPos target, String block) {
        command(player, "setblock " + xyz(target) + " emerald_economy:" + block + "[half=lower,facing=north]");
    }

    /** 放机器的上半格。 */
    private static void placeUpper(LocalPlayer player, BlockPos target, String block) {
        command(player, "setblock " + xyz(target.above()) + " emerald_economy:" + block + "[half=upper,facing=north]");
    }

    /**
     * 用真实交互路径右键：先按视线做射线检测，命中方块后调用
     * {@code MultiPlayerGameMode#useItemOn}——这正是原版处理鼠标右键时调用的方法。
     *
     * @return 准星是否命中了本次测试放置的机器
     */
    private static boolean rightClick(Minecraft minecraft, LocalPlayer player) {
        HitResult hit = player.pick(5.0D, 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit)) {
            log("  !! 准星没有命中任何方块");
            return false;
        }
        minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, blockHit);
        BlockPos hitPos = blockHit.getBlockPos();
        return hitPos.equals(gateTarget) || hitPos.equals(gateTarget.above());
    }

    private static int newsCount() {
        var market = ClientEconomyCache.market;
        return market == null ? 0 : market.news().size();
    }


    // ==================================================================
    // 专用用例：随机市场事件（需求 3.3.4）
    // ==================================================================

    /** 一个事件相位：把事件表改成只留一条，再触发结算看动量增量。 */
    private record EventPhase(String scope, double delta, String langKey, String label) {
    }

    /**
     * 逐个验证五类随机市场事件分支。
     * <p>
     * 做法：把事件表临时改成「只留一条」+ 概率 1.0，这样每次结算必定命中这一类事件，
     * 于是「动量增量 = 天气修正 + 事件修正」可以精确核对。
     * 第一条相位用空事件表做对照，确认测量方法本身没问题。
     */
    private static void buildMarketEventSteps() {
        add("市场事件：先切成晴天（让天气修正是已知的）", 25,
                (mc, player) -> command(player, "weather clear"));

        List<EventPhase> phases = List.of(
                new EventPhase("", 0.0D, "", "对照：空事件表（只应有天气修正）"),
                new EventPhase("ALL", 0.05D, "news.trade_boom", "市场普涨（全市场 +0.05）"),
                new EventPhase("ALL", -0.06D, "news.recession", "市场普跌（全市场 -0.06）"),
                new EventPhase("EMR_MIN", -0.10D, "news.mine_disaster", "行业利空（矿业 -0.10）"),
                new EventPhase("EMR_AGR", 0.08D, "news.bumper_harvest", "行业利好（农业 +0.08）"),
                new EventPhase("EMR_CMP", -0.12D, "news.emerald_devalue", "单一基金事件（综合 -0.12）"));

        for (int i = 0; i < phases.size(); i++) {
            EventPhase phase = phases.get(i);
            int no = i + 1;
            add("市场事件 " + no + "：把事件表改成「" + phase.label() + "」", 120, (mc, player) -> {
                writeMarketEvents(phase.scope().isEmpty() && phase.langKey().isEmpty()
                        ? List.of()
                        : List.of(phase.scope() + "|" + phase.delta() + "|" + phase.langKey()));
                clearChat();
                log("  已写入事件表：" + (phase.langKey().isEmpty() ? "（空）"
                        : phase.scope() + "|" + phase.delta() + "|" + phase.langKey()));
            });
            add("市场事件 " + no + "：读取结算前动量", 20, (mc, player) -> command(player, "ee price"));
            add("市场事件 " + no + "：记录结算前动量", 10, (mc, player) -> {
                MOMENTUM_BEFORE.clear();
                for (FundType type : FundType.values()) {
                    MOMENTUM_BEFORE.put(type, momentumFromChat(type.code()));
                }
                log("  结算前动量：" + formatMomentum(MOMENTUM_BEFORE));
            });
            add("市场事件 " + no + "：触发一次每日结算", 30, (mc, player) -> {
                clearChat();
                command(player, "ee settle");
            });
            add("市场事件 " + no + "：读取结算后动量", 20, (mc, player) -> command(player, "ee price"));
            add("市场事件 " + no + "：核对动量修正", 10, (mc, player) -> {
                StringBuilder actual = new StringBuilder();
                StringBuilder expected = new StringBuilder();
                boolean deltaOk = true;
                boolean newsOk = true;
                for (FundType type : FundType.values()) {
                    double before = MOMENTUM_BEFORE.getOrDefault(type, Double.NaN);
                    double after = momentumFromChat(type.code());
                    double expectedDelta = weatherDelta(type)
                            + (phase.scope().isEmpty() ? 0.0D
                            : (phase.scope().equals("ALL") || phase.scope().equals(type.code())
                            ? phase.delta() : 0.0D));
                    double actualDelta = after - before;
                    actual.append(type.code()).append('=').append(String.format(Locale.ROOT, "%.4f", actualDelta)).append(' ');
                    expected.append(type.code()).append('=')
                            .append(String.format(Locale.ROOT, "%.4f", expectedDelta)).append(' ');
                    if (Math.abs(actualDelta - expectedDelta) > 0.003D) {
                        deltaOk = false;
                    }
                }
                log("  " + phase.label() + " 实际增量：" + actual);
                log("  " + phase.label() + " 期望增量：" + expected);
                String expectedText = "";
                if (!phase.langKey().isEmpty()) {
                    expectedText = expectedEventText(phase.scope(), phase.delta(), phase.langKey());
                    newsOk = chatContains(expectedText);
                    log("  聊天里出现该事件播报「" + expectedText + "」= " + newsOk);
                }
                EVENT_PHASES_DONE.add(phase.label()
                        + (deltaOk ? " [动量 OK]" : " [动量 不符]")
                        + (newsOk ? " [播报 OK]" : " [播报 缺失]"));
                check("相位「" + phase.label() + "」各基金动量增量与预期一致", deltaOk);
                if (!phase.langKey().isEmpty()) {
                    check("相位「" + phase.label() + "」的事件播报已出现", newsOk);
                }
            });
        }

        add("市场事件：打开交易所看新闻", 30, (mc, player) -> open(mc, new ExchangeScreen()));
        add("市场事件：打开市场新闻界面", 25, (mc, player) -> click(mc, tr("screen.exchange.news")));
        add("截图 29 市场事件播报（随机市场新闻）", 0,
                (mc, player) -> shot(mc, "29_market_events"));
        add("市场事件：汇总", 10, (mc, player) -> {
            log("  六个相位结果：" + EVENT_PHASES_DONE);
            var market = ClientEconomyCache.market;
            int events = 0;
            if (market != null) {
                for (var line : market.news()) {
                    if ("event".equals(line.kind())) {
                        events++;
                    }
                }
            }
            log("  市场新闻里的事件类条目数 = " + events);
            check("市场新闻里累计出现了事件类条目", events > 0);
        });
        add("市场事件：把事件表还原成默认的 15 条", 40, (mc, player) -> {
            writeMarketEvents(defaultMarketEvents());
            log("  已还原默认事件表");
        });
    }

    /** 晴天（CLEAR）的天气动量修正，与默认配置里的 weatherModifiers 对应。 */
    private static double weatherDelta(FundType type) {
        return switch (type) {
            case EMR_AGR -> 0.030D;
            case EMR_BLD -> 0.020D;
            case EMR_HYD -> -0.020D;
            case EMR_HUN -> 0.020D;
            default -> 0.0D;
        };
    }

    private static String formatMomentum(java.util.Map<FundType, Double> map) {
        StringBuilder builder = new StringBuilder();
        map.forEach((type, value) ->
                builder.append(type.code()).append('=')
                        .append(String.format(Locale.ROOT, "%.4f", value)).append(' '));
        return builder.toString();
    }

    /** 默认的 15 条市场事件（与 EEConfig 的默认值一致），用于测试完还原。 */
    private static List<String> defaultMarketEvents() {
        return List.of(
                "EMR_MIN|0.08|news.vein_found", "EMR_MIN|-0.10|news.mine_disaster",
                "EMR_AGR|0.08|news.bumper_harvest", "EMR_AGR|-0.10|news.crop_blight",
                "EMR_LIV|0.08|news.ranch_boom", "EMR_LIV|-0.10|news.plague",
                "EMR_BLD|0.08|news.construction_boom", "EMR_BLD|-0.10|news.land_slump",
                "EMR_HYD|0.08|news.energy_breakthrough", "EMR_HYD|-0.10|news.power_outage",
                "EMR_HUN|0.08|news.hunting_season", "EMR_HUN|-0.10|news.game_depleted",
                "ALL|0.05|news.trade_boom", "ALL|-0.06|news.recession",
                "EMR_CMP|-0.12|news.emerald_devalue");
    }

    /**
     * 改写开发用服务端配置里的 marketEvents。
     * <p>
     * 客户端与服务端共用 run/ 目录，所以这里能直接改文件；
     * NeoForge 的配置监听会检测到变化并热加载。
     */
    private static void writeMarketEvents(List<String> entries) {
        try {
            File file = new File(Minecraft.getInstance().gameDirectory,
                    "config/emerald_economy-server.toml");
            String list = entries.stream().map(e -> "\"" + e + "\"")
                    .collect(java.util.stream.Collectors.joining(", "));
            // 按行替换而不是用正则，省得跟转义字符较劲
            String[] lines = Files.readString(file.toPath(), StandardCharsets.UTF_8)
                    .split("\n", -1);
            boolean found = false;
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].trim().startsWith("marketEvents")) {
                    lines[i] = "\tmarketEvents = [" + list + "]";
                    found = true;
                }
            }
            if (!found) {
                log("  !! 配置里没有 marketEvents 行，未修改");
                failed++;
                return;
            }
            Files.writeString(file.toPath(), String.join("\n", lines), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            log("  !! 改写配置失败：" + exception);
            failed++;
        }
    }

    // ==================================================================
    // 专用用例：多人并发（需求 1.1 / 3.1 / 5.3）
    // ==================================================================

    private static void buildMultiPlayerSteps() {
        if ("peer".equals(role())) {
            buildMultiPeerSteps();
        } else {
            buildMultiOwnerSteps();
        }
    }

    /** 先动手的那名玩家：买基金、下注、升级商店。 */
    private static void buildMultiOwnerSteps() {
        add("[房主] 给自己发钱", 25, (mc, player) -> command(player, "ee give @s 100000"));
        add("[房主] 打开交易所", 30, (mc, player) -> open(mc, new ExchangeScreen()));
        add("[房主] 买入 5 份矿业基金", 20, (mc, player) -> click(mc, tr("screen.common.buy")));
        add("[房主] 在弹窗里填 5 份并确认", 30, (mc, player) -> {
            setNumbers(mc, "5");
            click(mc, tr("screen.common.confirm"));
        });
        add("[房主] 校验自己持有 5 份", 15, (mc, player) -> {
            long shares = holdingShares(mc, FundType.EMR_MIN);
            log("  [房主] 矿业基金份额 = " + shares);
            check("[房主] 买入后持有 5 份矿业基金", shares == 5L);
        });
        add("[房主] 打开抽奖机并押「猜单双·单」20", 30, (mc, player) -> open(mc, new LotteryScreen()));
        add("[房主] 下注", 30, (mc, player) -> {
            click(mc, trFull(LotteryType.ODD_EVEN.optionLangKey(0)));
            setNumbers(mc, "20");
            clickAt(mc, tr("screen.lottery.bet"), 0);
        });
        add("[房主] 校验自己下注成功", 15, (mc, player) -> {
            var lottery = ClientEconomyCache.lottery;
            var slot = lottery == null ? null : lottery.slots().get(0);
            log("  [房主] 猜单双 = " + (slot == null ? "?" : slot.betChoice() + "/" + slot.betAmount()));
            check("[房主] 猜单双下注 20 成功", slot != null && slot.betChoice() == 0 && slot.betAmount() == 20L);
        });
        add("[房主] 打开售货机", 30, (mc, player) -> open(mc, new VendingScreen()));
        add("[房主] 购买第 1 格（占用自己的限购）", 25,
                (mc, player) -> click(mc, tr("screen.vending.buy")));
        add("[房主] 校验限购被自己占用", 15, (mc, player) -> {
            var shop = ClientEconomyCache.shop;
            int remaining = shop == null || shop.goods().isEmpty() ? -1 : shop.goods().get(0).remaining();
            log("  [房主] 第 1 格剩余限购 = " + remaining);
            check("[房主] 购买后自己这格剩余限购为 1", remaining == 1);
        });
        add("[房主] 升级商店（全服共享进度）", 30, (mc, player) -> clickLastButton(mc));
        add("[房主] 校验升级生效", 15, (mc, player) -> {
            var shop = ClientEconomyCache.shop;
            log("  [房主] 升级后已解锁格子数 = " + (shop == null ? -1 : shop.unlocked()));
            check("[房主] 升级后解锁格子数 >= 2", shop != null && shop.unlocked() >= 2);
        });
        // 留出时间让另一名玩家完成他的断言
        // 与客人的同步点：客人会把矿业基金价格改成 SHARED_PRICE_MARKER，
        // 房主反复打开交易所直到看见这个值 —— 看到了就说明「基金数据所有玩家共享」。
        for (int i = 0; i < 80; i++) {
            final int round = i + 1;
            add("[房主] 轮询行情是否已被客人改动（" + round + "/80）", 16, (mc, player) -> {
                if (ownerSawSharedPrice) {
                    return;
                }
                open(mc, new ExchangeScreen());
            });
            add("[房主] 检查行情标记", 2, (mc, player) -> {
                if (ownerSawSharedPrice) {
                    return;
                }
                long price = priceInfo(mc, FundType.EMR_MIN)[0];
                if (price == SHARED_PRICE_MARKER) {
                    ownerSawSharedPrice = true;
                    log("  [房主] 第 " + round + " 次轮询时看到了客人改出来的价格 " + price);
                }
            });
        }
        add("[房主] 校验与客人看到同一份行情", 10, (mc, player) -> {
            long price = priceInfo(mc, FundType.EMR_MIN)[0];
            log("  [房主] 最终读到的矿业基金价格 = " + price);
            check("[房主] 看到了客人改出来的价格，说明行情为所有玩家共享（"
                    + SHARED_PRICE_MARKER + "）", price == SHARED_PRICE_MARKER);
        });
        add("[房主] 收工", 20, (mc, player) -> mc.setScreen(null));
    }

    /** 后做断言的那名玩家：确认自己的账户/持仓/限购与房主互不影响，但看到同一份市场与商店进度。 */
    private static void buildMultiPeerSteps() {
        add("[客人] 给自己发钱", 25, (mc, player) -> command(player, "ee give @s 50000"));
        // 同步点：反复打开售货机，直到看见房主升级出来的第 2 格。
        // 两个客户端各自启动耗时不同，用固定等待很容易在房主还没做完时就断言。
        for (int i = 0; i < 45; i++) {
            final int round = i + 1;
            add("[客人] 轮询商店是否已有第 2 格（" + round + "/45）", 1, (mc, player) -> {
                if (peerSawSharedUpgrade) {
                    return;
                }
                open(mc, new VendingScreen());
            });
            add("[客人] 检查共享升级是否已出现", 4, (mc, player) -> {
                if (peerSawSharedUpgrade) {
                    return;
                }
                var shop = ClientEconomyCache.shop;
                if (shop != null && shop.unlocked() >= 2) {
                    peerSawSharedUpgrade = true;
                    log("  [客人] 第 " + round + " 次轮询时看到了房主升级出来的第 2 格");
                }
            });
        }
        add("[客人] 确认轮询到了共享升级", 10, (mc, player) ->
                check("[客人] 轮询到了房主升级出来的格子（升级进度全服共享）", peerSawSharedUpgrade));
        add("[客人] 先强制触发一次价格更新（新存档还没到第一次 5 分钟更新）", 25,
                (mc, player) -> command(player, "ee tick"));
        add("[客人] 打开交易所", 35, (mc, player) -> open(mc, new ExchangeScreen()));
        add("[客人] 校验账户与持仓独立、行情共享", 15, (mc, player) -> {
            var market = ClientEconomyCache.market;
            long balance = market == null ? Long.MIN_VALUE : market.balance();
            long shares = market == null ? -1 : market.funds().get(FundType.EMR_MIN.ordinal()).shares();
            long[] info = priceInfo(mc, FundType.EMR_MIN);
            log("  [客人] 余额 = " + balance + "，矿业份额 = " + shares
                    + "，矿业价格 = " + info[0] + "，历史点数 = " + info[1]);
            check("[客人] 看到的是自己的余额 50000，而不是房主的余额", balance == 50000L);
            check("[客人] 自己的持仓为 0（房主持有 5 份也不影响自己）", shares == 0L);
            check("[客人] 能看到共享的行情（价格 > 0）", info[0] > 0);
            check("[客人] 价格更新后的历史点已同步到客户端", info[1] > 0);
        });
        add("[客人] 把矿业基金价格改成固定标记，供房主核对行情是否共享", 25,
                (mc, player) -> command(player, "ee setprice EMR_MIN " + SHARED_PRICE_MARKER));
        add("[客人] 打开抽奖机，押房主已经押过的同一场次同一选项", 30,
                (mc, player) -> open(mc, new LotteryScreen()));
        add("[客人] 下注 30 押「单」", 30, (mc, player) -> {
            click(mc, trFull(LotteryType.ODD_EVEN.optionLangKey(0)));
            setNumbers(mc, "30");
            clickAt(mc, tr("screen.lottery.bet"), 0);
        });
        add("[客人] 校验同一场次自己也能下注（限购是按人算的）", 15, (mc, player) -> {
            var lottery = ClientEconomyCache.lottery;
            var slot = lottery == null ? null : lottery.slots().get(0);
            log("  [客人] 猜单双 = " + (slot == null ? "?" : slot.betChoice() + "/" + slot.betAmount()));
            check("[客人] 与房主同一天同一场次也能成功下注", slot != null && slot.betAmount() == 30L);
        });
        add("[客人] 同场次再下一次（自己应被拒）", 30, (mc, player) -> {
            click(mc, trFull(LotteryType.ODD_EVEN.optionLangKey(1)));
            setNumbers(mc, "40");
            clickAt(mc, tr("screen.lottery.bet"), 0);
        });
        add("[客人] 校验自己被拒且记录没被改", 15, (mc, player) -> {
            var lottery = ClientEconomyCache.lottery;
            var slot = lottery == null ? null : lottery.slots().get(0);
            log("  [客人] 再下注后 = " + (slot == null ? "?" : slot.betChoice() + "/" + slot.betAmount()));
            check("[客人] 同一天同场次第二次下注被拒绝", slot != null && slot.betAmount() == 30L);
        });
        add("[客人] 打开售货机看全服共享的升级进度", 30, (mc, player) -> open(mc, new VendingScreen()));
        add("截图 30 客人看到房主升级出来的格子", 0,
                (mc, player) -> shot(mc, "30_multi_peer_shop"));
        add("[客人] 校验全服共享进度 + 限购按人独立", 15, (mc, player) -> {
            var shop = ClientEconomyCache.shop;
            int unlocked = shop == null ? -1 : shop.unlocked();
            int remaining = shop == null || shop.goods().isEmpty() ? -1 : shop.goods().get(0).remaining();
            log("  [客人] 已解锁格子数 = " + unlocked + "，第 1 格剩余限购 = " + remaining);
            check("[客人] 售货机上看到的格子数仍然 >= 2", unlocked >= 2);
            check("[客人] 房主买过书不影响自己的每日限购（仍为 2）", remaining == 2);
        });
        add("[客人] 收工", 20, (mc, player) -> mc.setScreen(null));
    }

    /**
     * 专用用例：商店把全部格子解锁完。
     * <p>
     * 需要先把服务端配置的 {@code shop.initialSlots} 调满（128）并清掉世界存档，
     * 让商店在第一次初始化时就解锁全部格子。
     */

    /**
     * 只跑「生存模式挖机器」的专用用例（{@code -Dee.uitest.variant=mine}）。
     * <p>
     * 挖掘涉及「客户端发包 + 服务端累计进度」的跨端协作，单独拆出来跑得快、日志也干净。
     */
    private static void buildMineSteps() {
        add("准备：创造模式，清背包与地上的掉落物", 30, (mc, player) -> {
            command(player, "gamemode creative");
            command(player, "clear @s");
            // 世界会被多次运行复用，地上可能还躺着上一轮的掉落物，先清干净再测
            command(player, "kill @e[type=item,distance=..64]");
            command(player, "give @s minecraft:stone_pickaxe");
        });
        add("摆一台 ATM 并垫上红石块", 40, (mc, player) -> {
            command(player, String.format(Locale.ROOT, "tp @s ~ ~ ~ %.1f 0", player.getYRot()));
            gateTarget = gateTargetOf(player);
            log("  目标方块 = " + xyz(gateTarget));
            command(player, "setblock " + xyz(gateTarget.below()) + " minecraft:redstone_block");
            command(player, "setblock " + xyz(gateTarget.above()) + " minecraft:air");
            command(player, "setblock " + xyz(gateTarget) + " minecraft:air");
        });
        add("放机器（下半格 + 上半格）", 35, (mc, player) -> {
            placeLower(player, gateTarget, "atm");
            placeUpper(player, gateTarget, "atm");
        });
        add("诊断：客户端看到的方块与游戏模式", 20, (mc, player) -> {
            var lower = mc.level.getBlockState(gateTarget);
            var upper = mc.level.getBlockState(gateTarget.above());
            log("  下半格 = " + lower.getBlock() + " " + lower.getValues());
            log("  上半格 = " + upper.getBlock() + " " + upper.getValues());
            log("  客户端游戏模式 = " + mc.gameMode.getPlayerMode()
                    + "，可达距离 = " + player.blockInteractionRange() + "，目标距离 = "
                    + Math.sqrt(player.distanceToSqr(gateTarget.getCenter())));
            check("下半格确实是我放的 ATM", lower.getBlock() == ModBlocks.ATM.get());
        });
        add("切生存模式", 30, (mc, player) -> command(player, "gamemode survival"));
        add("诊断：确认已切到生存", 20, (mc, player) -> {
            log("  客户端游戏模式 = " + mc.gameMode.getPlayerMode());
            check("客户端已是生存模式", mc.gameMode.getPlayerMode() == net.minecraft.world.level.GameType.SURVIVAL);
        });
        add("开始挖「上半格」（START + 连点 continue，模拟按住鼠标）", 10, (mc, player) -> {
            BlockPos pos = gateTarget.above();
            mc.gameMode.startDestroyBlock(pos, Direction.UP);
            log("  START 之后立刻 isDestroying = " + mc.gameMode.isDestroying()
                    + "，destroyStage = " + mc.gameMode.getDestroyStage());
            for (int i = 0; i < 80 && !mc.level.getBlockState(pos).isAir(); i++) {
                mc.gameMode.continueDestroyBlock(pos, Direction.UP);
            }
            log("  连点 80 次 continue 之后 isDestroying = " + mc.gameMode.isDestroying()
                    + "，上半格 = " + mc.level.getBlockState(pos).getBlock());
        });
        add("等 3 秒让服务端处理", 60, (mc, player) -> {
        });
        add("诊断：上半格状态与掉落", 15, (mc, player) -> {
            var upper = mc.level.getBlockState(gateTarget.above());
            var lower = mc.level.getBlockState(gateTarget);
            long inBag = countItems(player, ModBlocks.ATM.get().asItem());
            long onGround = droppedItems(mc, player, ModBlocks.ATM.get().asItem());
            log("  上半格 = " + upper.getBlock() + "，下半格 = " + lower.getBlock()
                    + "，背包 ATM = " + inBag + "，地上 ATM = " + onGround);
            for (Entity e : mc.level.getEntities(player, player.getBoundingBox().inflate(8.0D))) {
                if (e instanceof net.minecraft.world.entity.item.ItemEntity drop) {
                    log("    掉落物 " + drop.getItem() + " @ " + drop.blockPosition());
                }
            }
            check("挖掉上半格后整机消失", upper.isAir() && lower.isAir());
            check("挖掉上半格掉了 1 台机器（背包或地上）", inBag + onGround == 1L);
        });
        add("重新摆一台（准备挖下半格）", 35, (mc, player) -> {
            command(player, "kill @e[type=item,distance=..16]");
            command(player, "clear @s");
            command(player, "give @s minecraft:stone_pickaxe");
            command(player, "setblock " + xyz(gateTarget.below()) + " minecraft:redstone_block");
        });
        add("放置机器", 30, (mc, player) -> {
            placeLower(player, gateTarget, "atm");
            placeUpper(player, gateTarget, "atm");
        });
        add("挖「下半格」", 10, (mc, player) -> {
            BlockPos pos = gateTarget;
            mc.gameMode.startDestroyBlock(pos, Direction.UP);
            for (int i = 0; i < 120 && !mc.level.getBlockState(pos).isAir(); i++) {
                mc.gameMode.continueDestroyBlock(pos, Direction.UP);
            }
        });
        add("等 3 秒", 60, (mc, player) -> {
        });
        add("诊断：下半格的掉落", 15, (mc, player) -> {
            long inBag = countItems(player, ModBlocks.ATM.get().asItem());
            long onGround = droppedItems(mc, player, ModBlocks.ATM.get().asItem());
            log("  下半格 = " + mc.level.getBlockState(gateTarget).getBlock()
                    + "，背包 ATM = " + inBag + "，地上 ATM = " + onGround);
            check("挖掉下半格后整机消失", mc.level.getBlockState(gateTarget).isAir());
            check("挖掉下半格掉了恰好 1 台机器（不重复也不吞掉）", inBag + onGround == 1L);
        });
    }

    private static void buildShopFullSteps() {
        add("全解锁：打开售货机", 35, (mc, player) -> open(mc, new VendingScreen()));
        add("截图 28 商店全解锁（升级按钮应已隐藏）", 0,
                (mc, player) -> shot(mc, "28_vending_all_unlocked"));
        add("校验全解锁分支", 10, (mc, player) -> {
            var shop = ClientEconomyCache.shop;
            int unlocked = shop == null ? -1 : shop.unlocked();
            int max = shop == null ? -1 : shop.maxSlots();
            long next = shop == null ? Long.MIN_VALUE : shop.nextUpgradeCost();
            log("  已解锁 " + unlocked + " / " + max + "，下一级升级费用 = " + next);
            check("配置 initialSlots 调满后一次性解锁全部格子", unlocked == max && max > 1);
            check("全解锁时不再有下一级升级费用（-1）", next < 0L);

            List<String> ids = new ArrayList<>();
            if (shop != null) {
                for (var good : enchantGoods(shop)) {
                    ids.add(good.id());
                }
            }
            log("  全部格子附魔数 = " + ids.size());
            check("全部格子的附魔两两不同（不重复）",
                    ids.size() > 1 && ids.size() == ids.stream().distinct().count());
            check("格子数等于附魔种类数", enchantGoods(shop).size() == max);

            // 界面上必须找不到「升级」按钮
            String upgradeLabel = tr("screen.vending.upgrade", "", "");
            boolean hasUpgrade = false;
            for (AbstractWidget widget : widgets(mc.screen)) {
                if (widget instanceof Button button && button.visible
                        && button.getMessage().getString().contains("升级")) {
                    hasUpgrade = true;
                    log("  意外发现升级按钮：" + button.getMessage().getString());
                }
            }
            log("  升级按钮模板 = 「" + upgradeLabel + "」");
            check("所有格子已解锁时升级按钮已隐藏", !hasUpgrade);

            // 顺带抽查价格仍在配置区间内（分类价格表给出的区间是 256-2560）
            boolean priceOk = true;
            if (shop != null) {
                for (var good : enchantGoods(shop)) {
                    if (good.price() < 256 || good.price() > 2560) {
                        priceOk = false;
                    }
                }
            }
            check("全部附魔书价格都在 256-2560 区间内", priceOk);
        });
    }

    // ==================================================================
    // A/B/C/D 四组补充实证：世界事件供给、冷却与失败路径、疯狂星期五、规则性拒绝
    // ==================================================================

    private static void buildSupplyAndFailureSteps() {

        // ---------- A. 世界事件供给（需求 3.3.2） ----------
        add("供给测试：关界面、摆平视角、记录基线", 25, (mc, player) -> {
            mc.setScreen(null);
            command(player, String.format(Locale.ROOT, "tp @s ~ ~ ~ %.1f 0", player.getYRot()));
            command(player, "ee supply");
        });
        add("读取各行业供给量基线", 15, (mc, player) -> {
            SUPPLY_BASE.clear();
            for (FundType type : FundType.values()) {
                long value = supplyFromChat(type.code());
                SUPPLY_BASE.put(type.code(), value);
                log("  基线 " + type.code() + " = " + value);
            }
        });

        // 矿业：挖 3 块铁矿石
        add("摆 3 块铁矿石", 20, (mc, player) ->
                stageLine(player, List.of("minecraft:iron_ore", "minecraft:iron_ore", "minecraft:iron_ore")));
        add("用真实挖掘动作挖掉这 3 块铁矿石", 25, (mc, player) -> breakStaged(mc, player));

        // 建筑：砍 2 根原木 + 挖 1 块石头
        add("摆 2 根原木与 1 块石头", 20, (mc, player) ->
                stageLine(player, List.of("minecraft:oak_log", "minecraft:oak_log", "minecraft:stone")));
        add("挖掉这 3 个建筑类方块", 25, (mc, player) -> breakStaged(mc, player));

        // 农业：收 2 株成熟小麦
        add("摆 2 株成熟作物（下面必须垫耕地，否则作物会被邻居更新判定为「没有支撑」而消失）", 20,
                (mc, player) -> stageLine(player,
                        List.of("minecraft:wheat[age=7]", "minecraft:carrots[age=7]"), "minecraft:farmland"));
        add("收掉这 2 株成熟小麦", 25, (mc, player) -> breakStaged(mc, player));

        // 水电：挖 1 块煤矿石 + 倒 1 桶水
        add("摆 1 块煤矿石", 20, (mc, player) -> stageLine(player, List.of("minecraft:coal_ore")));
        add("挖掉煤矿石", 25, (mc, player) -> breakStaged(mc, player));
        add("朝脚下倒一桶水（真实使用物品）", 30, (mc, player) -> {
            command(player, String.format(Locale.ROOT, "tp @s ~ ~ ~ %.1f 60", player.getYRot()));
            command(player, "item replace entity @s weapon.mainhand with minecraft:water_bucket");
        });
        add("右键倒水", 30, (mc, player) -> {
            mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            log("  已使用水桶");
        });

        // 畜牧：杀 1 头牛（用真实攻击动作，而不是 /kill）
        add("召唤一头 1 点血的牛并换上手剑", 30, (mc, player) -> {
            command(player, String.format(Locale.ROOT, "tp @s ~ ~ ~ %.1f 0", player.getYRot()));
            command(player, "item replace entity @s weapon.mainhand with minecraft:diamond_sword");
            command(player, "summon minecraft:cow " + xyz(inFrontOf(player, 2))
                    + " {Health:1.0f,PersistenceRequired:1b}");
        });
        add("攻击这头牛", 25, (mc, player) ->
                check("附近找到并攻击了牛", attackNearest(mc, player, Animal.class)));

        // 狩猎：杀 1 只僵尸
        add("召唤一只 1 点血的僵尸", 25, (mc, player) ->
                command(player, "summon minecraft:zombie " + xyz(inFrontOf(player, 2))
                        + " {Health:1.0f,PersistenceRequired:1b}"));
        add("攻击这只僵尸", 25, (mc, player) ->
                check("附近找到并攻击了僵尸", attackNearest(mc, player, Monster.class)));

        // 综合：与村民交易一次
        add("腾空背包并召唤一名农民", 25, (mc, player) -> {
            // 先清空背包：既要保证交易结果放得下，也避免被之前测试留下的杂物干扰
            command(player, "clear @s");
            command(player, "summon minecraft:villager " + xyz(inFrontOf(player, 2))
                    + " {VillagerData:{type:\"minecraft:plains\",profession:\"minecraft:farmer\",level:1},"
                    + "PersistenceRequired:1b,NoAI:1b}");
        });
        add("右键村民打开交易界面", 30, (mc, player) -> {
            Entity villager = nearest(mc, player, 6.0D, e -> e.getType()
                    == net.minecraft.world.entity.EntityType.VILLAGER);
            if (villager == null) {
                log("  !! 附近没有村民");
                failed++;
                return;
            }
            mc.gameMode.interact(player, villager, InteractionHand.MAIN_HAND);
        });
        add("校验交易界面已打开", 10, (mc, player) ->
                check("右键村民打开了交易界面", mc.screen instanceof MerchantScreen));
        add("按村民的报价把付款物品发给玩家", 25, (mc, player) -> {
            if (!(player.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu menu)) {
                log("  !! 当前不是村民菜单");
                failed++;
                return;
            }
            log("  村民报价值 " + menu.getOffers().size() + " 笔");
            for (var offer : menu.getOffers()) {
                giveCost(player, offer.getCostA(), "A");
                giveCost(player, offer.getCostB(), "B");
                log("    报价: " + offer.getCostA().getCount() + "x"
                        + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(offer.getCostA().getItem())
                        + (offer.getCostB().isEmpty() ? ""
                        : " + " + offer.getCostB().getCount() + "x"
                        + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(offer.getCostB().getItem()))
                        + " → " + offer.getResult());
            }
        });
        add("选中第 1 个交易", 25, (mc, player) -> {
            if (!clickFirstContainerButton(mc)) {
                log("  !! 交易列表里没有可点的交易项");
                failed++;
            }
        });
        add("点击交易结果槽完成一次交易", 30, (mc, player) -> {
            var menu = player.containerMenu;
            log("  容器 = " + menu.getClass().getSimpleName() + "，槽位数 = " + menu.slots.size());
            log("  付款槽 0 = " + menu.slots.get(0).getItem().getCount() + " 个 "
                    + menu.slots.get(0).getItem().getItem());
            log("  付款槽 1 = " + menu.slots.get(1).getItem().getCount() + " 个 "
                    + menu.slots.get(1).getItem().getItem());
            log("  结果槽 = " + menu.slots.get(2).getItem());
            // 真实槽位点击：和原版客户端点容器格走的是同一条包
            mc.gameMode.handleInventoryMouseClick(menu.containerId, 2, 0,
                    net.minecraft.world.inventory.ClickType.PICKUP, player);
            log("  已发出点击交易结果槽的请求");
        });

        // 核对供给量：等一秒让村民交易统计轮询到
        add("重新读取各行业供给量", 30, (mc, player) -> command(player, "ee supply"));
        add("校验各行业供给量按行业正确累加", 15, (mc, player) -> {
            long min = supplyDelta(FundType.EMR_MIN);
            long bld = supplyDelta(FundType.EMR_BLD);
            long agr = supplyDelta(FundType.EMR_AGR);
            long hyd = supplyDelta(FundType.EMR_HYD);
            long liv = supplyDelta(FundType.EMR_LIV);
            long hun = supplyDelta(FundType.EMR_HUN);
            long cmp = supplyDelta(FundType.EMR_CMP);
            log(String.format(Locale.ROOT,
                    "  供给量增量：MIN=%d BLD=%d AGR=%d HYD=%d LIV=%d HUN=%d CMP=%d",
                    min, bld, agr, hyd, liv, hun, cmp));
            check("挖 3 块铁矿石 → 矿业基金 +3", min == 3L);
            check("砍 2 根原木 + 挖 1 块石头 → 建筑基金 +3", bld == 3L);
            check("收 2 株成熟小麦 → 农业基金 +2", agr == 2L);
            check("挖 1 块煤矿石 + 倒 1 桶水 → 水电基金 +2", hyd == 2L);
            check("杀 1 头牛 → 畜牧基金 +1", liv == 1L);
            check("杀 1 只僵尸 → 狩猎基金 +1", hun == 1L);
            check("与村民交易 1 次 → 综合基金 +1", cmp == 1L);
        });

        // ---------- B. 交易冷却（需求 3.4.2） ----------
        add("冷却测试：打开交易所", 30, (mc, player) -> open(mc, new ExchangeScreen()));
        add("记录当前持仓份额", 10, (mc, player) -> {
            sharesBeforeCooldown = holdingShares(mc, FundType.EMR_MIN);
            log("  冷却测试前矿业基金份额 = " + sharesBeforeCooldown);
        });
        add("第 1 次买入：点「买入」打开弹窗", 20, (mc, player) -> click(mc, tr("screen.common.buy")));
        add("第 1 次买入：填 1 份并确认", 20, (mc, player) -> {
            setNumbers(mc, "1");
            click(mc, tr("screen.common.confirm"));
        });
        add("第 2 次买入（5 秒内）：立刻再次买入", 15, (mc, player) -> click(mc, tr("screen.common.buy")));
        add("第 2 次买入：填 1 份并确认（应被冷却拒绝）", 25, (mc, player) -> {
            setNumbers(mc, "1");
            click(mc, tr("screen.common.confirm"));
        });
        add("校验冷却生效", 10, (mc, player) -> {
            long now = holdingShares(mc, FundType.EMR_MIN);
            log("  两次买入后份额 = " + now + "，聊天含「冷却」= " + chatContains("冷却"));
            check("5 秒内的第 2 次交易被拒绝（份额只 +1）", now == sharesBeforeCooldown + 1L);
            check("冷却拒绝给出了提示", chatContains("冷却"));
        });
        add("等过 5 秒冷却（120 刻）", 125, (mc, player) -> {
        });
        add("冷却结束后的第 3 次买入", 20, (mc, player) -> click(mc, tr("screen.common.buy")));
        add("第 3 次确认", 25, (mc, player) -> {
            setNumbers(mc, "1");
            click(mc, tr("screen.common.confirm"));
        });
        add("校验冷却结束后可以正常成交", 10, (mc, player) -> {
            long now = holdingShares(mc, FundType.EMR_MIN);
            log("  冷却结束后份额 = " + now);
            check("冷却结束后第 3 次交易成交（份额再 +1）", now == sharesBeforeCooldown + 2L);
        });

        // ---------- B2. 失败路径 ----------
        add("余额不足取款：打开 ATM", 30, (mc, player) -> open(mc, new AtmScreen()));
        add("记下当前余额", 10, (mc, player) ->
                savedBalance = ClientEconomyCache.account == null
                        ? Long.MIN_VALUE : ClientEconomyCache.account.balance());
        add("取款 999999999（远超余额）", 30, (mc, player) -> {
            setNumbers(mc, "999999999");
            click(mc, tr("screen.atm.button.withdraw"));
        });
        add("校验取款被拒绝且余额不变", 10, (mc, player) -> {
            long now = ClientEconomyCache.account == null
                    ? Long.MIN_VALUE : ClientEconomyCache.account.balance();
            log("  取款前后余额 = " + savedBalance + " → " + now
                    + "，聊天含「余额不足」= " + chatContains("余额不足"));
            check("余额不足时取款被拒绝（余额不变）", now == savedBalance);
            check("余额不足给出了提示", chatContains("余额不足"));
        });

        add("限购用尽：打开售货机", 30, (mc, player) -> open(mc, new VendingScreen()));
        add("把第 1 个格子的剩余限购买光", 30, (mc, player) -> click(mc, tr("screen.vending.buy")));
        add("再买一次把剩余限购清零", 30, (mc, player) -> click(mc, tr("screen.vending.buy")));
        add("校验限购已清零", 10, (mc, player) -> {
            var shop = ClientEconomyCache.shop;
            boolean cleared = shop != null
                    && shop.goods().stream().anyMatch(g -> g.remaining() == 0);
            log("  各格剩余限购 = " + (shop == null ? "-" : shop.goods().stream()
                    .map(g -> String.valueOf(g.remaining())).toList()));
            check("连买 2 次后有格子被买光（剩余限购 0）", cleared);
        });
        add("限购用尽后再买一次", 30, (mc, player) -> click(mc, tr("screen.vending.buy")));
        add("校验限购用尽被拒绝", 10, (mc, player) -> {
            log("  聊天含「限购已用完」= " + chatContains("限购已用完"));
            check("今日限购用尽时购买被拒绝并提示", chatContains("限购已用完"));
        });

        add("余额不足购买：先把账户余额清零", 20, (mc, player) ->
                command(player, "ee take @s 999999999"));
        add("打开售货机并买任意一件商品（余额已清零，必定失败）", 35, (mc, player) -> {
            open(mc, new VendingScreen());
            click(mc, tr("screen.vending.buy"));
        });
        add("校验余额不足购买被拒绝", 10, (mc, player) -> {
            var shop = ClientEconomyCache.shop;
            long balance = shop == null ? Long.MIN_VALUE : shop.balance();
            log("  清零后的余额 = " + balance + "，聊天含「余额不足」= " + chatContains("余额不足"));
            check("清空余额后确实为 0", balance == 0L);
            check("余额不足时购买被拒绝并提示", chatContains("余额不足"));
        });
        add("把余额补回来", 15, (mc, player) -> command(player, "ee give @s 100000"));

        // ---------- C. 疯狂星期五（需求 6.4） ----------
        add("把游戏日推到第 5 天（逢 5 开启疯狂星期五）", 45, (mc, player) -> {
            clearChat();
            command(player, "time set 120000");
            log("  已 /time set 120000（第 5 天）");
        });
        add("校验疯狂星期五已开启并广播", 20, (mc, player) -> {
            String line = chatLineContaining("疯狂星期五");
            log("  广播 = " + line);
            check("逢 5 当天广播了疯狂星期五", chatContains("疯狂星期五"));
            // 需求 4：广播里要带上价格（形如「唤魔者刷怪蛋 152/个」）
            check("广播里带上了每件的单价", line != null && line.contains("/个"));
        });
        // 注意：新开的界面是用**客户端缓存里那份快照**画标签页的，而快照要等界面主动请求之后才回来，
        // 所以「打开界面」与「切到疯狂星期五」必须分成两步（同一步里点，那一刻疯狂星期五标签还不存在）
        add("打开自动收购机（界面初始化会拉一次最新快照）", 40,
                (mc, player) -> open(mc, new AcquisitionScreen()));
        add("切到「疯狂星期五」分类", 30, (mc, player) -> click(mc, tr("acquire.category.friday")));
        add("校验疯狂星期五分类已切换过去（三行都有「出售」按钮）", 10, (mc, player) -> {
            int buttons = countButtons(mc, tr("screen.acquire.sell"));
            log("  疯狂星期五分类里的「出售」按钮数 = " + buttons);
            check("疯狂星期五分类真的切过去了", buttons == 3);
        });
        add("截图 26 疯狂星期五分类（重金收购横幅 + 三件重金物品）", 0,
                (mc, player) -> shot(mc, "26_crazy_friday"));
        add("校验疯狂星期五分类已出现且价格是平时的 5-10 倍", 15, (mc, player) -> {
            var acquire = ClientEconomyCache.acquire;
            int categories = acquire == null ? -1 : acquire.categories().size();
            log("  收购分类数 = " + categories + "（平时 5 个，开放日 6 个）");
            check("疯狂星期五开放时多出一个分类", categories == 6);
            long fridayCount = 0;
            boolean priceOk = true;
            if (acquire != null) {
                for (var line : acquire.categories()) {
                    if (!cn.daenx.mc.emeraldeconomy.content.AcquireContent.FRIDAY_ID.equalsIgnoreCase(line.id())) {
                        continue;
                    }
                    fridayCount = line.entries().size();
                    for (var entry : line.entries()) {
                        // 基准价是 30（刷怪蛋）~133（下界之星），×5~10 倍 ⇒ 单件价格落在 150-1330
                        if (!entry.friday() || entry.price() < 150L || entry.price() > 1330L) {
                            priceOk = false;
                        }
                    }
                }
            }
            log("  疯狂星期五物品数 = " + fridayCount);
            check("疯狂星期五当天随机选出了 3 件物品", fridayCount == 3L);
            check("疯狂星期五物品价格按 5-10 倍计算", priceOk);
        });
        // 「每天最多被随机选中 1 件」说的是随机结果，跟收购数量无关：
        // 挑中的物品想卖多少卖多少，这里给 3 个、一次卖掉，应该按 3 倍单价入账
        add("给 3 个当天挑中的重金物品", 30, (mc, player) -> {
            var acquire = ClientEconomyCache.acquire;
            fridayItemId = null;
            fridayItemPrice = 0L;
            if (acquire != null) {
                for (var line : acquire.categories()) {
                    if (cn.daenx.mc.emeraldeconomy.content.AcquireContent.FRIDAY_ID
                            .equalsIgnoreCase(line.id()) && !line.entries().isEmpty()) {
                        fridayItemId = line.entries().get(0).itemId();
                        fridayItemPrice = line.entries().get(0).price();
                    }
                }
            }
            log("  当天第一件重金物品 = " + fridayItemId + "，单价 = " + fridayItemPrice);
            command(player, "give @s " + fridayItemId + " 3");
        });
        add("填 3 并点第一行的「出售」", 35, (mc, player) -> {
            savedBalance = ClientEconomyCache.acquire == null
                    ? Long.MIN_VALUE : ClientEconomyCache.acquire.balance();
            setNumbers(mc, "3");
            clickAt(mc, tr("screen.acquire.sell"), 0);
        });
        add("校验疯狂星期五的收购数量不限（一次卖掉 3 个）", 15, (mc, player) -> {
            long now = ClientEconomyCache.acquire == null
                    ? Long.MIN_VALUE : ClientEconomyCache.acquire.balance();
            log("  卖之前 " + savedBalance + " → 卖之后 " + now
                    + "（预期 +" + (fridayItemPrice * 3L) + "）");
            check("一次卖掉 3 个重金物品，按 3 倍单价入账",
                    fridayItemPrice > 0L && now == savedBalance + fridayItemPrice * 3L);
            check("背包里的那 3 个已经被扣掉", inventoryCount(player, fridayItemId) == 0L);
        });

        add("借 100 贷款（验证欠款时不能参加疯狂星期五）", 35, (mc, player) -> {
            open(mc, new AtmScreen());
            setNumbers(mc, "100");
            click(mc, tr("screen.atm.button.loan"));
        });
        add("回到收购机并切到疯狂星期五分类", 35, (mc, player) -> {
            open(mc, new AcquisitionScreen());
            click(mc, tr("acquire.category.friday"));
        });
        add("校验有欠款时疯狂星期五不能出售", 15, (mc, player) -> {
            var acquire = ClientEconomyCache.acquire;
            int buttons = countButtons(mc, tr("screen.acquire.sell"));
            log("  欠款 = " + accountDebt() + "，hasDebt = " + (acquire == null ? "?" : acquire.hasDebt())
                    + "，「出售」按钮数 = " + buttons);
            check("有欠款时收购快照标记 hasDebt", acquire != null && acquire.hasDebt());
            check("有欠款时疯狂星期五分类里没有「出售」按钮", buttons == 0);
        });
        add("还清 100 贷款", 35, (mc, player) -> {
            open(mc, new AtmScreen());
            setNumbers(mc, "100");
            click(mc, tr("screen.atm.button.repay"));
        });
        add("回到疯狂星期五分类（应恢复出售）", 35, (mc, player) -> {
            open(mc, new AcquisitionScreen());
            click(mc, tr("acquire.category.friday"));
        });
        add("校验还清后疯狂星期五恢复出售", 15, (mc, player) -> {
            int buttons = countButtons(mc, tr("screen.acquire.sell"));
            log("  还清后「出售」按钮数 = " + buttons);
            check("还清欠款后疯狂星期五重新可以出售", buttons >= 1);
        });
        // ---------- D. 规则性拒绝路径 ----------
        add("重复下注：打开抽奖界面", 35, (mc, player) -> open(mc, new LotteryScreen()));
        add("重复下注：第 1 次押「猜单双·单」20 绿宝石", 30, (mc, player) -> {
            click(mc, trFull(LotteryType.ODD_EVEN.optionLangKey(0)));
            setNumbers(mc, "20");
            clickAt(mc, tr("screen.lottery.bet"), 0);
        });
        add("校验第 1 次下注成功", 10, (mc, player) -> {
            var lottery = ClientEconomyCache.lottery;
            var slot = lottery == null ? null : lottery.slots().get(0);
            guardBetAmount = slot == null ? -1L : slot.betAmount();
            log("  猜单双下注：" + (slot == null ? "?" : slot.betChoice() + " / " + slot.betAmount()));
            check("第 1 次下注被接受（押「单」20）",
                    slot != null && slot.betChoice() == 0 && slot.betAmount() == 20L);
        });
        add("重复下注：同一天同一场次再押「双」30（应被拒）", 30, (mc, player) -> {
            click(mc, trFull(LotteryType.ODD_EVEN.optionLangKey(1)));
            setNumbers(mc, "30");
            clickAt(mc, tr("screen.lottery.bet"), 0);
        });
        add("校验重复下注被拒绝", 10, (mc, player) -> {
            var lottery = ClientEconomyCache.lottery;
            var slot = lottery == null ? null : lottery.slots().get(0);
            log("  第 2 次下注后：" + (slot == null ? "?" : slot.betChoice() + " / " + slot.betAmount())
                    + "，聊天含「已下注」= " + chatContains("已下注"));
            check("同场次当天第 2 次下注被拒绝（记录未被覆盖）",
                    slot != null && slot.betChoice() == 0 && slot.betAmount() == guardBetAmount);
            check("重复下注给出了提示", chatContains("已下注"));
        });

        add("超额贷款：打开 ATM", 35, (mc, player) -> open(mc, new AtmScreen()));
        add("超额贷款：记录余额 / 欠款 / 可贷额度", 10, (mc, player) -> {
            guardBalance = ClientEconomyCache.account == null
                    ? Long.MIN_VALUE : ClientEconomyCache.account.balance();
            guardDebt = ClientEconomyCache.account == null
                    ? Long.MIN_VALUE : ClientEconomyCache.account.debt();
            long credit = ClientEconomyCache.account == null
                    ? Long.MIN_VALUE : ClientEconomyCache.account.availableCredit();
            log("  当前余额 = " + guardBalance + "，欠款 = " + guardDebt + "，可贷额度 = " + credit);
            check("此时可贷额度确实小于 999999（否则这条测试没有意义）", credit < 999999L);
        });
        add("超额贷款：申请 999999（远超可贷额度）", 30, (mc, player) -> {
            setNumbers(mc, "999999");
            click(mc, tr("screen.atm.button.loan"));
        });
        add("校验超额贷款被拒绝", 10, (mc, player) -> {
            long balance = ClientEconomyCache.account == null
                    ? Long.MIN_VALUE : ClientEconomyCache.account.balance();
            long debt = ClientEconomyCache.account == null
                    ? Long.MIN_VALUE : ClientEconomyCache.account.debt();
            log("  申请后余额 = " + balance + "，欠款 = " + debt
                    + "，聊天含「超出可贷额度」= " + chatContains("超出可贷额度"));
            check("超额贷款被拒绝（余额与欠款都没变）", balance == guardBalance && debt == guardDebt);
            check("超额贷款给出了提示", chatContains("超出可贷额度"));
        });

        add("存款超额：先清空绿宝石，只给 5 个", 20, (mc, player) -> {
            command(player, "clear @s minecraft:emerald");
            command(player, "give @s emerald 5");
        });
        add("存款超额：尝试存入 1000", 35, (mc, player) -> {
            guardBalance = ClientEconomyCache.account == null
                    ? Long.MIN_VALUE : ClientEconomyCache.account.balance();
            open(mc, new AtmScreen());
            setNumbers(mc, "1000");
            click(mc, tr("screen.atm.button.deposit"));
        });
        add("校验存款被拒绝且绿宝石没被吞", 10, (mc, player) -> {
            long balance = ClientEconomyCache.account == null
                    ? Long.MIN_VALUE : ClientEconomyCache.account.balance();
            long emeralds = countEmeralds(player);
            log("  存款后余额 = " + balance + "，背包绿宝石 = " + emeralds
                    + "，聊天含「背包里的绿宝石不足」= " + chatContains("背包里的绿宝石不足"));
            check("存款数量超过背包绿宝石时被拒绝（余额不变）", balance == guardBalance);
            check("被拒绝时背包里的绿宝石没被扣除", emeralds == 5L);
            check("存款超额给出了提示", chatContains("背包里的绿宝石不足"));
        });

        add("商店不重复：打开售货机", 35, (mc, player) -> {
            UPGRADE_COSTS.clear();
            open(mc, new VendingScreen());
        });
        add("商店不重复：记录当前解锁格子与下一级费用", 10, (mc, player) -> {
            var shop = ClientEconomyCache.shop;
            UPGRADE_COSTS.add(shop == null ? -1L : shop.nextUpgradeCost());
            log("  当前已解锁 " + (shop == null ? -1 : shop.unlocked())
                    + " 格，下一级费用 " + (shop == null ? -1L : shop.nextUpgradeCost()));
        });
        for (int i = 0; i < 4; i++) {
            add("商店不重复：升级第 " + (i + 1) + " 次", 30, (mc, player) -> clickLastButton(mc));
            add("商店不重复：记录升级后的格子数与费用", 10, (mc, player) -> {
                var shop = ClientEconomyCache.shop;
                UPGRADE_COSTS.add(shop == null ? -1L : shop.nextUpgradeCost());
            });
        }
        add("校验各格附魔不重复、升级费用在配置区间内", 10, (mc, player) -> {
            var shop = ClientEconomyCache.shop;
            List<String> ids = new ArrayList<>();
            if (shop != null) {
                for (var good : enchantGoods(shop)) {
                    ids.add(good.id());
                }
            }
            log("  已解锁格子附魔：" + ids);
            boolean distinct = ids.size() == ids.stream().distinct().count();
            check("已解锁的 " + ids.size() + " 个格子售卖不同附魔（不重复）", !ids.isEmpty() && distinct);
            check("解锁格子数与附魔数一致",
                    enchantGoods(shop).size() == shop.unlocked());

            // 2026-10 调整：升级费用改成「512-1280 之间按格子序号稳定随机」，不再逐级递增
            boolean inRange = !UPGRADE_COSTS.isEmpty();
            for (long cost : UPGRADE_COSTS) {
                if (cost < 512L || cost > 1280L) {
                    inRange = false;
                }
            }
            boolean varied = UPGRADE_COSTS.stream().distinct().count() > 1;
            log("  升级费用序列 = " + UPGRADE_COSTS);
            check("升级费用都落在配置区间 512-1280 内", inRange);
            check("升级费用不是个定值（区间内随机）", varied);
        });
        add("截图 27 商店已解锁多格（各格附魔互不相同）", 0,
                (mc, player) -> shot(mc, "27_vending_multi_slot"));

        add("关掉界面把机器重新摆好，准备收尾", 20, (mc, player) -> mc.setScreen(null));

        // ---------- 生存模式下的挖掘与掉落（需求：任意镐子都能挖，且必定掉落一台） ----------
        add("切生存模式、清背包、给一把石镐", 25, (mc, player) -> {
            command(player, "gamemode survival");
            command(player, "clear @s");
            command(player, "give @s minecraft:stone_pickaxe");
        });
        add("在目标位置重新放一台 ATM（下方垫泥土）", 35, (mc, player) -> {
            gateTarget = gateTargetOf(player);
            command(player, "setblock " + xyz(gateTarget.below()) + " minecraft:dirt");
            command(player, "setblock " + xyz(gateTarget.above()) + " minecraft:air");
            command(player, "setblock " + xyz(gateTarget) + " minecraft:air");
        });
        add("放机器（下半格 + 上半格）", 30, (mc, player) -> {
            placeLower(player, gateTarget, "atm");
            placeUpper(player, gateTarget, "atm");
        });
        add("生存模式下开始挖「上半格」", 10, (mc, player) -> startMine(mc, gateTarget.above()));
        add("等它挖穿（硬度 5.0、石镐约 2 秒）", 70, (mc, player) -> {
        });
        add("校验挖掉上半格恰好掉 1 台机器", 20, (mc, player) -> {
            long machines = countItems(player, ModBlocks.ATM.get().asItem())
                    + droppedItems(mc, player, ModBlocks.ATM.get().asItem());
            boolean gone = mc.level.getBlockState(gateTarget).isAir();
            log("  挖到手的 ATM（背包 + 地上）= " + machines + "，下半格是否已消失 = " + gone);
            check("挖掉上半格后整机一起消失", gone);
            check("挖掉上半格掉落恰好 1 台机器", machines == 1L);
        });
        add("先清掉地上的掉落物与背包，再放一台挖下半格", 35, (mc, player) -> {
            command(player, "kill @e[type=item,distance=..16]");
            command(player, "clear @s");
            command(player, "give @s minecraft:stone_pickaxe");
        });
        add("放置机器", 30, (mc, player) -> {
            placeLower(player, gateTarget, "atm");
            placeUpper(player, gateTarget, "atm");
        });
        add("生存模式下开始挖「下半格」", 10, (mc, player) -> startMine(mc, gateTarget));
        add("等它挖穿", 70, (mc, player) -> {
        });
        add("校验挖掉下半格也恰好掉 1 台机器", 20, (mc, player) -> {
            long machines = countItems(player, ModBlocks.ATM.get().asItem())
                    + droppedItems(mc, player, ModBlocks.ATM.get().asItem());
            boolean gone = mc.level.getBlockState(gateTarget.above()).isAir();
            log("  挖到手的 ATM（背包 + 地上）= " + machines + "，上半格是否已消失 = " + gone);
            check("挖掉下半格后上半格也一起消失", gone);
            check("挖掉下半格掉落恰好 1 台机器（不重复也不吞掉）", machines == 1L);
        });
        add("切回创造模式并清空背包", 25, (mc, player) -> {
            command(player, "gamemode creative");
            command(player, "clear @s");
            mc.setScreen(null);
        });
    }

    /**
     * 直接改 {@code config/emerald_economy/shop.json} 里某个商品的价格。
     * <p>
     * 客户端与服务端共用 run/ 目录，所以这里能直接改文件；
     * 改完由游戏里的 {@code /ee reload} 让服务端重新读取（不用重启）。
     */
    private static boolean patchShopItemPrice(String itemId, int price) {
        try {
            File file = new File(Minecraft.getInstance().gameDirectory,
                    "config/emerald_economy/shop.json");
            String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            var root = com.google.gson.JsonParser.parseString(text).getAsJsonObject();
            boolean found = false;
            for (var group : root.getAsJsonArray("groups")) {
                for (var sub : group.getAsJsonObject().getAsJsonArray("subcategories")) {
                    if (!sub.getAsJsonObject().has("items")) {
                        continue;
                    }
                    for (var item : sub.getAsJsonObject().getAsJsonArray("items")) {
                        var obj = item.getAsJsonObject();
                        if (obj.has("item") && itemId.equals(obj.get("item").getAsString())) {
                            obj.addProperty("price", price);
                            found = true;
                        }
                    }
                }
            }
            if (!found) {
                log("  !! shop.json 里没找到 " + itemId);
                failed++;
                return false;
            }
            Files.writeString(file.toPath(), new com.google.gson.GsonBuilder()
                    .setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
            log("  已把 " + itemId + " 的价格改成 " + price);
            return true;
        } catch (Exception exception) {
            log("  !! 改写 shop.json 失败：" + exception);
            failed++;
            return false;
        }
    }

    /**
     * 当前界面上「出售」按钮的可用状态（按创建顺序）。
     * <p>
     * 用来验证「背包里没有的物品按钮置灰」：界面上的按钮是按行序创建的。
     */
    private static List<Boolean> sellButtonStates(Minecraft minecraft) {
        List<Boolean> out = new ArrayList<>();
        String label = tr("screen.acquire.sell");
        for (AbstractWidget widget : widgets(minecraft.screen)) {
            if (widget instanceof Button button && button.getMessage().getString().contains(label)) {
                out.add(button.active);
            }
        }
        return out;
    }

    /** 玩家附近「掉在地上」的某种物品数量（挖方块后马上统计用）。 */
    private static long droppedItems(Minecraft minecraft, LocalPlayer player,
                                     net.minecraft.world.item.Item item) {
        long total = 0L;
        for (Entity entity : minecraft.level.getEntities(player, player.getBoundingBox().inflate(8.0D))) {
            if (entity instanceof net.minecraft.world.entity.item.ItemEntity drop
                    && drop.getItem().is(item)) {
                total += drop.getItem().getCount();
            }
        }
        return total;
    }

    /**
     * 挖掉一个方块。
     * <p>
     * 生存模式下客户端的破坏进度是自己一点点累计的（原版客户端每刻调一次
     * {@code continueDestroyBlock}），测试里没有「按住鼠标」这件事，
     * 所以这里手动连续调用把它挖穿——服务端会收到正常的破坏包并掉落物品。
     */
    private static void startMine(Minecraft minecraft, BlockPos pos) {
        if (minecraft.gameMode == null || minecraft.level == null) {
            return;
        }
        minecraft.gameMode.startDestroyBlock(pos, Direction.UP);
        for (int i = 0; i < 120 && !minecraft.level.getBlockState(pos).isAir(); i++) {
            minecraft.gameMode.continueDestroyBlock(pos, Direction.UP);
        }
        // 不要调 stopDestroyBlock：那是「提前松手」，会让服务端放弃这次破坏（物品就不掉了）
    }

    /** 数一数玩家背包里的绿宝石（客户端侧，用来确认被拒绝时物品没被吞）。 */
    private static long countEmeralds(LocalPlayer player) {
        long total = 0L;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(net.minecraft.world.item.Items.EMERALD)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    // ------------------------------------------------------------------
    // 新一组步骤用到的辅助方法
    // ------------------------------------------------------------------

    /** 玩家正前方距离 n 格、与脚同高的位置。 */
    private static BlockPos inFrontOf(LocalPlayer player, int distance) {
        return player.blockPosition().relative(player.getDirection(), distance);
    }

    /**
     * 在玩家正前方摆一排方块（从朝向的左手边开始往后排）。
     * <p>
     * 用 /setblock 而不是让玩家自己放，是为了让「摆方块」这一步不产生供给量，
     * 这样后面测出来的增量就完全来自玩家真实的挖掘动作。
     */
    private static void stageLine(LocalPlayer player, List<String> blockIds) {
        stageLine(player, blockIds, null);
    }

    /**
     * 在玩家正前方摆一排方块（从朝向的左手边开始往后排）。
     * <p>
     * 用 /setblock 而不是让玩家自己放，是为了让「摆方块」这一步不产生供给量，
     * 这样后面测出来的增量就完全来自玩家真实的挖掘动作。
     *
     * @param supportBelow 需要在方块下面额外垫的方块（作物必须踩在耕地上，否则会自我销毁）
     */
    private static void stageLine(LocalPlayer player, List<String> blockIds, String supportBelow) {
        STAGED.clear();
        Direction facing = player.getDirection();
        Direction side = facing.getClockWise();
        BlockPos base = player.blockPosition().relative(facing, 3).atY(player.blockPosition().getY() + 1);
        for (int i = 0; i < blockIds.size(); i++) {
            BlockPos pos = base.relative(side, i);
            if (supportBelow != null) {
                command(player, "setblock " + xyz(pos.below()) + " " + supportBelow);
            }
            command(player, "setblock " + xyz(pos) + " " + blockIds.get(i));
            STAGED.add(pos);
        }
        log("  已摆好 " + blockIds.size() + " 个方块，起点 " + xyz(base));
    }

    /** 用真实的挖掘动作挖掉刚摆好的那一排方块。 */
    private static void breakStaged(Minecraft minecraft, LocalPlayer player) {
        if (STAGED.isEmpty()) {
            log("  !! 没有摆好的方块可挖");
            failed++;
            return;
        }
        for (BlockPos pos : STAGED) {
            // 挖掘前把方块状态和它会被归到哪支基金打出来，方便定位事件钩子的问题
            var state = minecraft.level.getBlockState(pos);
            log("  挖掘前 " + xyz(pos) + " = " + state.getBlock()
                    + " " + state.getValues()
                    + "，归类 = " + cn.daenx.mc.emeraldeconomy.market.SupplyTracker.classifyBlock(state));
            minecraft.gameMode.startDestroyBlock(pos, Direction.UP);
        }
        log("  已挖掘 " + STAGED.size() + " 个方块");
        STAGED.clear();
    }

    /** 找最近的、满足条件的实体。 */
    private static Entity nearest(Minecraft minecraft, LocalPlayer player, double radius,
                                  java.util.function.Predicate<Entity> filter) {
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : minecraft.level.getEntities(player, player.getBoundingBox().inflate(radius))) {
            if (!filter.test(entity)) {
                continue;
            }
            double distance = entity.distanceToSqr(player);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        return best;
    }

    /** 攻击最近的指定类型实体（走真实的攻击包，服务端才会算成玩家击杀）。 */
    private static boolean attackNearest(Minecraft minecraft, LocalPlayer player, Class<?> type) {
        Entity target = nearest(minecraft, player, 8.0D, type::isInstance);
        if (target == null) {
            log("  !! 附近找不到目标实体：" + type.getSimpleName());
            return false;
        }
        minecraft.gameMode.attack(player, target);
        log("  已攻击 " + target.getType() + " @ " + target.position());
        return true;
    }

    /** 按村民报价的某一项（A/B）给玩家补足物品。 */
    private static void giveCost(LocalPlayer player, net.minecraft.world.item.ItemStack cost, String slot) {
        if (cost.isEmpty()) {
            return;
        }
        var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(cost.getItem());
        // 多给一些，保证一定能凑够
        command(player, "give @s " + id + " " + Math.max(1, cost.getCount() * 2));
    }

    /** 村民界面里点第一个交易项（界面里的交易项本身就是按钮控件）。 */
    private static boolean clickFirstContainerButton(Minecraft minecraft) {
        if (minecraft.screen == null) {
            return false;
        }
        for (AbstractWidget widget : widgets(minecraft.screen)) {
            if (widget instanceof Button button && button.visible) {
                return press(minecraft.screen, button, "交易项");
            }
        }
        return false;
    }

    /** 读某支基金的价格与历史点数（{价格, 历史点数}）。 */
    private static long[] priceInfo(Minecraft minecraft, FundType fund) {
        var market = ClientEconomyCache.market;
        if (market == null) {
            return new long[]{-1L, -1L};
        }
        var snapshot = market.funds().get(fund.ordinal());
        return new long[]{snapshot.price(), snapshot.history().length};
    }

    /** 取某支基金当前的持仓份额（读客户端快照）。 */
    private static long holdingShares(Minecraft minecraft, FundType fund) {
        var market = ClientEconomyCache.market;
        return market == null ? Long.MIN_VALUE : market.funds().get(fund.ordinal()).shares();
    }

    /** 从 /ee price 的输出里读出某支基金的动量（取最近的一条）。 */
    private static double momentumFromChat(String fundCode) {
        double value = Double.NaN;
        for (String line : CHAT) {
            if (!line.contains(fundCode)) {
                continue;
            }
            String plain = line.replaceAll("§.", "");
            int index = plain.indexOf("动量");
            if (index < 0) {
                continue;
            }
            Matcher matcher = Pattern.compile("(-?\\d+\\.?\\d*)").matcher(plain.substring(index));
            if (matcher.find()) {
                try {
                    value = Double.parseDouble(matcher.group(1));
                } catch (NumberFormatException ignored) {
                    // 解析不了就保留上一个值
                }
            }
        }
        return value;
    }

    /**
     * 还原一条随机市场事件在聊天栏里的文案。
     * <p>
     * 服务端是「前缀 + 可翻译组件」，参数为百分比（普涨/普跌）或「基金名 + 百分比」（针对单支基金）。
     * 聊天里出现的是翻译后的文本而不是语言键，所以断言要比对翻译结果。
     */
    private static String expectedEventText(String scope, double delta, String langKey) {
        List<Object> args = new ArrayList<>();
        String percent = String.format(Locale.ROOT, "%.1f%%", delta * 100.0D);
        if ("ALL".equals(scope)) {
            args.add(percent);
        } else {
            FundType target = FundType.byCode(scope);
            args.add(target == null ? scope : target.displayName());
            args.add(percent);
        }
        return Component.translatable(EEConstants.langKey(langKey), args.toArray()).getString();
    }

    /** 聊天里是否出现过某段文字。 */
    private static boolean chatContains(String needle) {
        if (needle == null || needle.isEmpty()) {
            return false;
        }
        for (String line : CHAT) {
            if (line.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static void clearChat() {
        CHAT.clear();
    }

    /** 从 /ee supply 的输出里读出某支基金的当日供给量（取最近的一条）。 */
    private static long supplyFromChat(String fundCode) {
        long value = Long.MIN_VALUE;
        for (String line : CHAT) {
            if (!line.contains(fundCode)) {
                continue;
            }
            String plain = line.replaceAll("§.", "").trim();
            Matcher matcher = Pattern.compile("(\\d+)\\s*$").matcher(plain);
            if (matcher.find()) {
                value = Long.parseLong(matcher.group(1));
            }
        }
        return value;
    }

    /** 供给量相对基线的增量（读最近一次 /ee supply 的结果）。 */
    private static long supplyDelta(FundType type) {
        long now = supplyFromChat(type.code());
        long base = SUPPLY_BASE.getOrDefault(type.code(), Long.MIN_VALUE);
        if (now == Long.MIN_VALUE || base == Long.MIN_VALUE) {
            return Long.MIN_VALUE;
        }
        return now - base;
    }

    private static void finish() {
        finished = true;
        log("========== 界面实证测试结束 ==========");
        log("通过 " + passed + "，失败 " + failed + "，截图 " + shotCount + " 张");
        dumpLog();
        System.out.println("[ClientUiTest] passed=" + passed + " failed=" + failed + " shots=" + shotCount);
    }

    // ------------------------------------------------------------------
    // 结果收集
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
        LOG.add(message);
        System.out.println("[ClientUiTest] " + message);
    }

    /** 把日志以 UTF-8 写到截图目录，避开控制台编码问题。 */
    private static void dumpLog() {
        try {
            File dir = new File(Minecraft.getInstance().gameDirectory, "screenshots/ee");
            if (!dir.exists() && !dir.mkdirs()) {
                return;
            }
            Path path = new File(dir, "test-log.txt").toPath();
            Files.write(path, LOG, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception ignored) {
            // 测试日志写不出来不影响测试本身
        }
    }

    // ------------------------------------------------------------------
    // 界面操作
    // ------------------------------------------------------------------

    private static void add(String name, int waitAfter, Action action) {
        STEPS.add(new Step(name, waitAfter, action));
    }

    private static void open(Minecraft minecraft, Screen screen) {
        minecraft.setScreen(screen);
    }

    private static void command(LocalPlayer player, String command) {
        if (player.connection != null) {
            player.connection.sendCommand(command);
        }
    }

    /** 用与界面相同的语言键取按钮文字，切语言也不会让测试失效。 */
    private static String tr(String path, Object... args) {
        return Component.translatable(EEConstants.langKey(path), args).getString();
    }

    /** 与界面一致地格式化金额（界面上带千分位，这里必须一致才能对上按钮文字）。 */
    private static String money(long value) {
        return cn.daenx.mc.emeraldeconomy.core.Amounts.format(value);
    }

    /** 界面上现在有没有文字包含关键字的按钮。 */
    private static boolean buttonExists(Minecraft minecraft, String label) {
        for (AbstractWidget widget : widgets(minecraft.screen)) {
            if (widget instanceof Button button && button.getMessage().getString().contains(label)) {
                return true;
            }
        }
        return false;
    }

    /** 同上，但传入的已经是完整翻译键（例如 LotteryType#optionLangKey 的返回值）。 */
    private static String trFull(String fullKey, Object... args) {
        return Component.translatable(fullKey, args).getString();
    }

    private static long accountBalance() {
        return ClientEconomyCache.account == null ? Long.MIN_VALUE : ClientEconomyCache.account.balance();
    }

    /**
     * 售货机快照里的余额。
     * <p>
     * 售货机的购买只会回推<b>商店快照</b>（不会回推账户快照），所以买东西之后
     * 要看余额得读商店快照，否则读到的是上一次 ATM 操作时的旧数字。
     */
    private static long shopBalance() {
        return ClientEconomyCache.shop == null ? Long.MIN_VALUE : ClientEconomyCache.shop.balance();
    }

    private static long accountDebt() {
        return ClientEconomyCache.account == null ? Long.MIN_VALUE : ClientEconomyCache.account.debt();
    }

    private static List<AbstractWidget> widgets(Screen screen) {
        if (screen == null) {
            return List.of();
        }
        List<AbstractWidget> out = new ArrayList<>();
        for (GuiEventListener listener : screen.children()) {
            if (listener instanceof AbstractWidget widget) {
                out.add(widget);
            }
        }
        return out;
    }

    private static void setNumbers(Minecraft minecraft, String value) {
        for (AbstractWidget widget : widgets(minecraft.screen)) {
            if (widget instanceof EditBox box) {
                box.setValue(value);
            }
        }
    }

    private static boolean click(Minecraft minecraft, String label) {
        return clickAt(minecraft, label, 0);
    }

    /**
     * 点击第 N 个文本包含关键字的按钮。
     *
     * @param occurrence 从 0 开始
     */
    private static boolean clickAt(Minecraft minecraft, String label, int occurrence) {
        int seen = 0;
        for (AbstractWidget widget : widgets(minecraft.screen)) {
            if (!(widget instanceof Button button)
                    || !button.getMessage().getString().contains(label)) {
                continue;
            }
            if (seen++ < occurrence) {
                continue;
            }
            return press(minecraft.screen, button, label);
        }
        log("  !! 找不到按钮：" + label + "（第 " + occurrence + " 个）");
        failed++;
        return false;
    }

    /** 商店快照里的附魔书商品（「其他」大分类的种子/树苗不算在内）。 */
    private static List<ServerPayloads.ShopGood> enchantGoods(ServerPayloads.SyncShop shop) {
        List<ServerPayloads.ShopGood> out = new ArrayList<>();
        if (shop != null) {
            for (ServerPayloads.ShopGood good : shop.goods()) {
                if (good.enchant()) {
                    out.add(good);
                }
            }
        }
        return out;
    }

    /** 收购界面每页的行数（与 AcquisitionScreen 里的 PAGE_SIZE 保持一致）。 */
    private static final int ACQUIRE_PAGE_SIZE = 8;

    /**
     * 某个物品的「出售」按钮在这一页里是第几个（从 0 开始）。
     * <p>
     * 界面上按钮是按行序创建的，所以要用它在条目列表里的下标取模。
     */
    private static int sellButtonIndex(ServerPayloads.SyncAcquire acquire, String category, String itemId) {
        List<ServerPayloads.AcquireLine> lines = entriesOf(acquire, category);
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).itemId().equals(itemId)) {
                return i % ACQUIRE_PAGE_SIZE;
            }
        }
        return 0;
    }

    /** 收购快照里某个分类的全部条目。 */
    private static List<ServerPayloads.AcquireLine> entriesOf(ServerPayloads.SyncAcquire acquire,
                                                             String categoryId) {
        if (acquire != null) {
            for (ServerPayloads.AcquireCategoryLine line : acquire.categories()) {
                if (line.id().equalsIgnoreCase(categoryId)) {
                    return line.entries();
                }
            }
        }
        return List.of();
    }

    /** 某个分类里有没有这个物品。 */
    private static boolean hasEntry(ServerPayloads.SyncAcquire acquire, String category, String itemId) {
        for (ServerPayloads.AcquireLine line : entriesOf(acquire, category)) {
            if (line.itemId().equals(itemId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 标签行是不是「有名字的」（配置里给了翻译键）。
     * <p>
     * 需求里要求唱片 / 树苗 / 花 / 蘑菇「合并成一个」，如果名字还是裸标签 ID（#xxx），
     * 玩家根本看不懂那一行是什么，所以这一条也要一起验。
     */
    private static boolean hasTagRowName(ServerPayloads.SyncAcquire acquire, String category, String itemId) {
        for (ServerPayloads.AcquireLine line : entriesOf(acquire, category)) {
            if (line.itemId().equals(itemId)) {
                return line.tag() && line.lang() != null && !line.lang().isBlank();
            }
        }
        return false;
    }

    /** 背包里某种物品的数量。 */
    private static long inventoryCount(LocalPlayer player, String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        Item item = id == null ? null : BuiltInRegistries.ITEM.get(id);
        if (item == null) {
            return 0L;
        }
        long total = 0L;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** 某个物品的收购单价（找不到返回 -1）。 */
    private static long entryPrice(ServerPayloads.SyncAcquire acquire, String category, String itemId) {
        for (ServerPayloads.AcquireLine line : entriesOf(acquire, category)) {
            if (line.itemId().equals(itemId)) {
                return line.price();
            }
        }
        return -1L;
    }

    /** 某个物品的批次大小（找不到返回 -1）。 */
    private static int entryBatch(ServerPayloads.SyncAcquire acquire, String category, String itemId) {
        for (ServerPayloads.AcquireLine line : entriesOf(acquire, category)) {
            if (line.itemId().equals(itemId)) {
                return line.batch();
            }
        }
        return -1;
    }

    /** 背包里某种物品的数量（客户端侧）。 */
    private static long countItems(LocalPlayer player, net.minecraft.world.item.Item item) {
        long total = 0L;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** 聊天里第一行包含关键字的文本（没有则返回 null）。 */
    private static String chatLineContaining(String needle) {
        for (String line : CHAT) {
            if (line.contains(needle)) {
                return line.replaceAll("§.", "").trim();
            }
        }
        return null;
    }

    /** 从一段文本里抠出正则的第一个捕获组（没匹配返回 null）。 */
    private static String firstGroup(String text, String regex) {
        if (text == null) {
            return null;
        }
        Matcher matcher = Pattern.compile(regex).matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    /** 数一数当前界面上文本包含关键字的按钮有几个（用于断言「按钮不存在」）。 */
    private static int countButtons(Minecraft minecraft, String label) {
        int count = 0;
        for (AbstractWidget widget : widgets(minecraft.screen)) {
            if (widget instanceof Button button && button.getMessage().getString().contains(label)) {
                count++;
            }
        }
        return count;
    }

    /** 点击最后一个按钮：售货机的「升级」按钮标签带参数，用位置最稳。 */
    private static boolean clickLastButton(Minecraft minecraft) {
        Button last = null;
        for (AbstractWidget widget : widgets(minecraft.screen)) {
            if (widget instanceof Button button) {
                last = button;
            }
        }
        return last != null && press(minecraft.screen, last, last.getMessage().getString());
    }

    private static boolean press(Screen screen, Button button, String label) {
        int x = button.getX() + button.getWidth() / 2;
        int y = button.getY() + button.getHeight() / 2;
        // 原版按钮在 mouseReleased 里才触发 onPress，所以两个都要调
        screen.mouseClicked(x, y, 0);
        screen.mouseReleased(x, y, 0);
        log("  点击「" + label + "」");
        return true;
    }

    /** 截图（取的是上一帧的帧缓冲，调用方要保证前面已经渲染过一帧）。 */
    private static void shot(Minecraft minecraft, String name) {
        try {
            var image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget());
            File dir = new File(minecraft.gameDirectory, "screenshots/ee");
            if (!dir.exists() && !dir.mkdirs()) {
                log("  !! 无法创建截图目录 " + dir);
                return;
            }
            image.writeToFile(new File(dir, name + ".png"));
            image.close();
            shotCount++;
        } catch (Exception exception) {
            log("  !! 截图 " + name + " 失败：" + exception);
        }
    }
}
