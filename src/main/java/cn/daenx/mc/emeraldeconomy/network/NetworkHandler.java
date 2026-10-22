package cn.daenx.mc.emeraldeconomy.network;

import cn.daenx.mc.emeraldeconomy.acquire.AcquireService;
import cn.daenx.mc.emeraldeconomy.acquire.EnchantRecycleService;
import cn.daenx.mc.emeraldeconomy.client.ClientNetworkHandler;
import cn.daenx.mc.emeraldeconomy.core.Notify;
import cn.daenx.mc.emeraldeconomy.data.AccountData;
import cn.daenx.mc.emeraldeconomy.data.EconomyData;
import cn.daenx.mc.emeraldeconomy.economy.AccountService;
import cn.daenx.mc.emeraldeconomy.lottery.LotteryService;
import cn.daenx.mc.emeraldeconomy.lottery.LotteryType;
import cn.daenx.mc.emeraldeconomy.market.FundTradeService;
import cn.daenx.mc.emeraldeconomy.market.FundType;
import cn.daenx.mc.emeraldeconomy.network.payload.ClientPayloads;
import cn.daenx.mc.emeraldeconomy.network.payload.ServerPayloads;
import cn.daenx.mc.emeraldeconomy.shop.EnchantCatalog;
import cn.daenx.mc.emeraldeconomy.shop.ShopService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.UUID;

/**
 * 网络注册与处理（需求文档 3.4.3、3.8）。
 * <p>
 * 设计原则：客户端只发「意图」，服务端负责全部校验与结算；
 * 每次操作完成后服务端会把最新的快照回推给该玩家，界面自动刷新。
 * <p>
 * 另外，每次操作的提示都会同时走两条路：聊天栏（{@link Notify}）与界面内提示
 * （{@link ServerSync#sendNotice}）。需求要求「操作不满足条件时机器界面上也要看得见」，
 * 否则玩家得先关掉界面才能知道为什么点不动。
 * <p>
 * 注意：这里引用 {@link ClientNetworkHandler} 的地方全部写在 lambda 内部，
 * 保证专用服务端不会因为加载客户端类而崩服。
 */
public final class NetworkHandler {

    /** 协议版本，客户端与服务端不一致时会被拒绝连接。 */
    private static final String PROTOCOL_VERSION = "7";

    private NetworkHandler() {
    }

    /** 模组事件总线上注册网络包。 */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        // ---- 服务端 → 客户端 ----
        registrar.playToClient(ServerPayloads.OpenScreen.TYPE, ServerPayloads.OpenScreen.STREAM_CODEC,
                (payload, context) -> ClientNetworkHandler.handleOpenScreen(payload, context));
        registrar.playToClient(ServerPayloads.ScreenNotice.TYPE, ServerPayloads.ScreenNotice.STREAM_CODEC,
                (payload, context) -> ClientNetworkHandler.handleScreenNotice(payload, context));
        registrar.playToClient(ServerPayloads.SyncAccount.TYPE, ServerPayloads.SyncAccount.STREAM_CODEC,
                (payload, context) -> ClientNetworkHandler.handleSyncAccount(payload, context));
        registrar.playToClient(ServerPayloads.SyncMarket.TYPE, ServerPayloads.SyncMarket.STREAM_CODEC,
                (payload, context) -> ClientNetworkHandler.handleSyncMarket(payload, context));
        registrar.playToClient(ServerPayloads.SyncLottery.TYPE, ServerPayloads.SyncLottery.STREAM_CODEC,
                (payload, context) -> ClientNetworkHandler.handleSyncLottery(payload, context));
        registrar.playToClient(ServerPayloads.SyncShop.TYPE, ServerPayloads.SyncShop.STREAM_CODEC,
                (payload, context) -> ClientNetworkHandler.handleSyncShop(payload, context));
        registrar.playToClient(ServerPayloads.SyncAcquire.TYPE, ServerPayloads.SyncAcquire.STREAM_CODEC,
                (payload, context) -> ClientNetworkHandler.handleSyncAcquire(payload, context));

        // ---- 客户端 → 服务端 ----
        registrar.playToServer(ClientPayloads.RequestScreen.TYPE, ClientPayloads.RequestScreen.STREAM_CODEC,
                NetworkHandler::handleRequestScreen);
        registrar.playToServer(ClientPayloads.AtmActionRequest.TYPE, ClientPayloads.AtmActionRequest.STREAM_CODEC,
                NetworkHandler::handleAtmAction);
        registrar.playToServer(ClientPayloads.AtmTransferRequest.TYPE, ClientPayloads.AtmTransferRequest.STREAM_CODEC,
                NetworkHandler::handleAtmTransfer);
        registrar.playToServer(ClientPayloads.FundTradeRequest.TYPE, ClientPayloads.FundTradeRequest.STREAM_CODEC,
                NetworkHandler::handleFundTrade);
        registrar.playToServer(ClientPayloads.LotteryBetRequest.TYPE, ClientPayloads.LotteryBetRequest.STREAM_CODEC,
                NetworkHandler::handleLotteryBet);
        registrar.playToServer(ClientPayloads.ShopActionRequest.TYPE, ClientPayloads.ShopActionRequest.STREAM_CODEC,
                NetworkHandler::handleShopAction);
        registrar.playToServer(ClientPayloads.AcquireSellRequest.TYPE, ClientPayloads.AcquireSellRequest.STREAM_CODEC,
                NetworkHandler::handleAcquireSell);
        registrar.playToServer(ClientPayloads.AcquireEnchantSellRequest.TYPE,
                ClientPayloads.AcquireEnchantSellRequest.STREAM_CODEC,
                NetworkHandler::handleAcquireEnchantSell);
    }

    /** 服务端通知客户端打开界面。 */
    public static void openScreen(ServerPlayer player, ScreenType type) {
        PacketDistributor.sendToPlayer(player, new ServerPayloads.OpenScreen(type.ordinal()));
        ServerSync.sendFor(player, type);
    }

    // ------------------------------------------------------------------
    // 提示：聊天栏 + 界面
    // ------------------------------------------------------------------

    /**
     * 同一条提示发给玩家两次：聊天栏一条、机器界面内一条。
     *
     * @param error 是否是失败提示（界面用红色显示）
     */
    private static void feedback(ServerPlayer player, boolean error, String langPath, Object... args) {
        Notify.send(player, langPath, args);
        ServerSync.sendNotice(player, Notify.component(langPath, args), error);
    }

    /** 同上，带 emoji 前缀。 */
    private static void feedbackPrefixed(ServerPlayer player, String prefix, boolean error,
                                         String langPath, Object... args) {
        Notify.sendPrefixed(player, prefix, langPath, args);
        ServerSync.sendNotice(player, Notify.componentPrefixed(prefix, langPath, args), error);
    }

    // ------------------------------------------------------------------
    // 客户端请求处理
    // ------------------------------------------------------------------

    private static void handleRequestScreen(ClientPayloads.RequestScreen payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ScreenType type = ScreenType.byIndex(payload.screenType());
                ServerSync.rememberOpenScreen(player, type);
                ServerSync.sendFor(player, type);
            }
        });
    }

    private static void handleAtmAction(ClientPayloads.AtmActionRequest payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            EconomyData data = EconomyData.get(player.getServer());
            if (data == null) {
                return;
            }
            ClientPayloads.AtmAction action = ClientPayloads.AtmAction.byIndex(payload.action());
            long amount = payload.amount();
            switch (action) {
                case DEPOSIT -> {
                    AccountService.Outcome outcome = AccountService.deposit(data, player, amount);
                    if (outcome.ok()) {
                        feedbackPrefixed(player, "💰 ", false, "atm.deposit_ok",
                                outcome.credited(), outcome.fee());
                    } else {
                        notifyAtmFailure(player, outcome.result());
                    }
                }
                case WITHDRAW -> {
                    AccountService.Outcome outcome = AccountService.withdraw(data, player, amount);
                    if (outcome.ok()) {
                        feedbackPrefixed(player, "💰 ", false, "atm.withdraw_ok",
                                outcome.amount(), outcome.fee());
                    } else {
                        notifyAtmFailure(player, outcome.result());
                    }
                }
                case LOAN -> {
                    AccountService.Outcome outcome = AccountService.loan(data, player, amount);
                    if (outcome.ok()) {
                        feedbackPrefixed(player, "💰 ", false, "atm.loan_ok", outcome.credited());
                    } else {
                        notifyAtmFailure(player, outcome.result());
                    }
                }
                case REPAY -> {
                    AccountService.Outcome outcome = AccountService.repay(data, player, amount);
                    if (outcome.ok()) {
                        feedbackPrefixed(player, "💰 ", false, "atm.repay_ok", outcome.credited());
                    } else {
                        notifyAtmFailure(player, outcome.result());
                    }
                }
                case DEPOSIT_ALL -> {
                    AccountService.Outcome outcome = AccountService.depositAll(data, player);
                    if (outcome.ok()) {
                        feedbackPrefixed(player, "💰 ", false, "atm.deposit_all_ok",
                                outcome.amount(), outcome.credited(), outcome.fee());
                    } else {
                        notifyAtmFailure(player, outcome.result());
                    }
                }
                case WITHDRAW_ALL -> {
                    AccountService.Outcome outcome = AccountService.withdrawAll(data, player);
                    if (outcome.ok()) {
                        feedbackPrefixed(player, "💰 ", false, "atm.withdraw_all_ok",
                                outcome.amount(), outcome.fee());
                    } else {
                        notifyAtmFailure(player, outcome.result());
                    }
                }
                case LEVEL_UNLOCK -> {
                    AccountService.Outcome outcome = AccountService.unlockLevelAccess(data, player);
                    if (outcome.ok()) {
                        feedbackPrefixed(player, "💰 ", false, "atm.level_unlock_ok", outcome.credited());
                    } else {
                        notifyAtmFailure(player, outcome.result());
                    }
                }
                case LEVEL_DEPOSIT -> {
                    AccountService.ExperienceOutcome outcome = AccountService.depositExperience(
                            data, player, (int) Math.min(Integer.MAX_VALUE, amount));
                    if (outcome.ok()) {
                        feedbackPrefixed(player, "💰 ", false, "atm.level_deposit_ok",
                                outcome.experience(), outcome.stored(), player.experienceLevel);
                    } else {
                        notifyLevelFailure(player, outcome.result());
                    }
                }
                case LEVEL_WITHDRAW -> {
                    AccountService.ExperienceOutcome outcome = AccountService.withdrawExperience(
                            data, player, (int) Math.min(Integer.MAX_VALUE, amount));
                    if (outcome.ok()) {
                        feedbackPrefixed(player, "💰 ", false, "atm.level_withdraw_ok",
                                outcome.experience(), outcome.stored(), player.experienceLevel);
                    } else {
                        notifyLevelFailure(player, outcome.result());
                    }
                }
                case LEVEL_DEPOSIT_ALL -> {
                    AccountService.ExperienceOutcome outcome = AccountService.depositAllExperience(data, player);
                    if (outcome.ok()) {
                        feedbackPrefixed(player, "💰 ", false, "atm.level_deposit_ok",
                                outcome.experience(), outcome.stored(), player.experienceLevel);
                    } else {
                        notifyLevelFailure(player, outcome.result());
                    }
                }
                case LEVEL_WITHDRAW_ALL -> {
                    AccountService.ExperienceOutcome outcome = AccountService.withdrawAllExperience(data, player);
                    if (outcome.ok()) {
                        feedbackPrefixed(player, "💰 ", false, "atm.level_withdraw_ok",
                                outcome.experience(), outcome.stored(), player.experienceLevel);
                    } else {
                        notifyLevelFailure(player, outcome.result());
                    }
                }
                case CLAIM_ONLINE_REWARD -> {
                    long day = cn.daenx.mc.emeraldeconomy.core.DaySettlement.currentDay(player.serverLevel());
                    if (!AccountService.claimOnlineReward(data, player, day)) {
                        // 提示要说清「为什么领不了」：今天没资格 / 已经领过 / 没在线
                        if (data.account(player.getUUID()).onlineRewardClaimedToday(day)) {
                            feedback(player, true, "atm.online_reward_already");
                        } else {
                            feedback(player, true, "atm.online_reward_unavailable");
                        }
                    }
                }
            }
            ServerSync.sendAccount(player);
        });
    }

    /** 玩家之间的转账（ATM 排行榜界面）。 */
    private static void handleAtmTransfer(ClientPayloads.AtmTransferRequest payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            EconomyData data = EconomyData.get(player.getServer());
            if (data == null) {
                return;
            }
            UUID targetId;
            try {
                targetId = UUID.fromString(payload.targetId());
            } catch (IllegalArgumentException | NullPointerException ex) {
                feedback(player, true, "atm.transfer_fail_target");
                ServerSync.sendAccount(player);
                return;
            }
            if (payload.mode() == ClientPayloads.AtmTransferRequest.MODE_EXPERIENCE) {
                AccountService.ExperienceGiftOutcome outcome = AccountService.giftExperience(
                        data, player, targetId, payload.amount());
                if (outcome.ok()) {
                    feedbackPrefixed(player, "✨ ", false, "atm.gift_ok",
                            outcome.amount(), outcome.targetName());
                } else {
                    String key = switch (outcome.result()) {
                        case INVALID_AMOUNT -> "atm.fail_invalid_amount";
                        case INSUFFICIENT_EXPERIENCE -> "atm.fail_no_experience";
                        case TARGET_NOT_FOUND -> "atm.transfer_fail_target";
                        case TARGET_OFFLINE -> "atm.transfer_fail_offline";
                        case SELF_TRANSFER -> "atm.transfer_fail_self";
                        default -> "atm.fail_unknown";
                    };
                    feedback(player, true, key);
                }
            } else {
                AccountService.TransferOutcome outcome = AccountService.transfer(data, player, targetId,
                        payload.amount());
                if (outcome.ok()) {
                    feedbackPrefixed(player, "💰 ", false, "atm.transfer_ok",
                            outcome.amount(), outcome.fee(), outcome.received(), outcome.targetName());
                } else {
                    String key = switch (outcome.result()) {
                        case INVALID_AMOUNT -> "atm.fail_invalid_amount";
                        case INSUFFICIENT_BALANCE -> "atm.fail_no_balance";
                        case TARGET_NOT_FOUND -> "atm.transfer_fail_target";
                        case TARGET_OFFLINE -> "atm.transfer_fail_offline";
                        case SELF_TRANSFER -> "atm.transfer_fail_self";
                        default -> "atm.fail_unknown";
                    };
                    feedback(player, true, key);
                }
            }
            ServerSync.sendAccount(player);
            // 目标玩家必须在线（服务端已校验），顺手刷新他的界面
            MinecraftServer server = player.getServer();
            ServerPlayer onlineTarget = server == null ? null : server.getPlayerList().getPlayer(targetId);
            if (onlineTarget != null && onlineTarget != player) {
                ServerSync.sendAccount(onlineTarget);
            }
        });
    }

    /** 经验存取失败时的提示。 */
    private static void notifyLevelFailure(ServerPlayer player, AccountService.Result result) {
        String key = switch (result) {
            case LEVEL_LOCKED -> "atm.level_fail_locked";
            case LEVEL_ALREADY_UNLOCKED -> "atm.level_fail_unlocked";
            case INSUFFICIENT_EXPERIENCE -> "atm.level_fail_experience";
            case INVALID_AMOUNT -> "atm.fail_invalid_amount";
            case INSUFFICIENT_BALANCE -> "atm.fail_no_balance";
            default -> "atm.fail_unknown";
        };
        feedback(player, true, key);
    }

    private static void notifyAtmFailure(ServerPlayer player, AccountService.Result result) {
        String key = switch (result) {
            case INVALID_AMOUNT -> "atm.fail_invalid_amount";
            case INSUFFICIENT_ITEMS -> "atm.fail_no_emerald";
            case INSUFFICIENT_BALANCE -> "atm.fail_no_balance";
            case EXCEEDS_CREDIT -> "atm.fail_credit";
            case NO_DEBT -> "atm.fail_no_debt";
            case NO_SPACE -> "atm.fail_no_space";
            case LEVEL_LOCKED -> "atm.level_fail_locked";
            case LEVEL_ALREADY_UNLOCKED -> "atm.level_fail_unlocked";
            case INSUFFICIENT_EXPERIENCE -> "atm.level_fail_experience";
            case NO_ONLINE_REWARD -> "atm.online_reward_unavailable";
            case TARGET_NOT_FOUND -> "atm.transfer_fail_target";
            case TARGET_OFFLINE -> "atm.transfer_fail_offline";
            case SELF_TRANSFER -> "atm.transfer_fail_self";
            case OK -> "atm.fail_unknown";
        };
        feedback(player, true, key);
    }

    private static void handleFundTrade(ClientPayloads.FundTradeRequest payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            EconomyData data = EconomyData.get(player.getServer());
            if (data == null) {
                return;
            }
            FundType fund = FundType.byIndex(payload.fund());
            FundTradeService.Outcome outcome = payload.buy()
                    ? FundTradeService.buy(data, player, fund, payload.quantity())
                    : FundTradeService.sell(data, player, fund, payload.quantity());
            if (outcome.ok()) {
                feedback(player, false, payload.buy() ? "market.buy_ok" : "market.sell_ok",
                        fund.displayName(), outcome.quantity(), outcome.price(), outcome.net());
            } else {
                String key = switch (outcome.result()) {
                    case INVALID_QUANTITY -> "market.fail_quantity";
                    case INSUFFICIENT_BALANCE -> "market.fail_balance";
                    case INSUFFICIENT_SHARES -> "market.fail_shares";
                    case COOLDOWN -> "market.fail_cooldown";
                    case DAILY_BUY_LIMIT -> "market.fail_daily_buy";
                    case DAILY_SELL_LIMIT -> "market.fail_daily_sell";
                    case OK -> "market.fail_unknown";
                };
                if (outcome.result() == FundTradeService.Result.COOLDOWN) {
                    feedback(player, true, key, (outcome.cooldown() + 999) / 1000);
                } else if (outcome.result() == FundTradeService.Result.DAILY_BUY_LIMIT
                        || outcome.result() == FundTradeService.Result.DAILY_SELL_LIMIT) {
                    // 提示里带上「今天还剩多少份」，玩家才知道下一次能买 / 卖多少
                    long today = cn.daenx.mc.emeraldeconomy.core.DaySettlement.currentDay(player.serverLevel());
                    AccountData account = data.account(player.getUUID());
                    long remaining = payload.buy()
                            ? FundTradeService.remainingDailyBuy(account, today)
                            : FundTradeService.remainingDailySell(account, today);
                    feedback(player, true, key, remaining);
                } else {
                    feedback(player, true, key);
                }
            }
            ServerSync.sendMarket(player);
        });
    }

    private static void handleLotteryBet(ClientPayloads.LotteryBetRequest payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            EconomyData data = EconomyData.get(player.getServer());
            if (data == null) {
                return;
            }
            LotteryType type = LotteryType.byIndex(payload.lotteryType());
            long day = cn.daenx.mc.emeraldeconomy.core.DaySettlement.currentDay(player.serverLevel());
            LotteryService.Result result = LotteryService.placeBet(
                    data, player, type, payload.choice(), payload.amount(), day);
            if (result == LotteryService.Result.OK) {
                feedback(player, false, "lottery.bet_ok", type.displayName(), payload.amount());
            } else {
                String key = switch (result) {
                    case INVALID_AMOUNT -> "lottery.fail_amount";
                    case INVALID_CHOICE -> "lottery.fail_choice";
                    case INSUFFICIENT_BALANCE -> "lottery.fail_balance";
                    case ALREADY_BET -> "lottery.fail_already";
                    case HAS_DEBT -> "lottery.fail_debt";
                    case OK -> "lottery.fail_unknown";
                };
                feedback(player, true, key);
            }
            ServerSync.sendLottery(player);
        });
    }

    private static void handleShopAction(ClientPayloads.ShopActionRequest payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            MinecraftServer server = player.getServer();
            EconomyData data = server == null ? null : EconomyData.get(server);
            if (data == null) {
                return;
            }
            ClientPayloads.ShopAction action = ClientPayloads.ShopAction.byIndex(payload.action());
            if (action == ClientPayloads.ShopAction.BUY) {
                ShopService.Result result = ShopService.buy(data, player, server.registryAccess(),
                        payload.goodKey(),
                        cn.daenx.mc.emeraldeconomy.core.DaySettlement.currentDay(player.serverLevel()));
                if (result == ShopService.Result.OK) {
                    // 提示里带上买到的到底是什么（附魔书报附魔名，其他商品报物品名）
                    feedback(player, false, "shop.buy_ok",
                            ShopService.goodName(server.registryAccess(), payload.goodKey()));
                } else {
                    feedback(player, true, shopFailKey(result));
                }
            } else {
                ShopService.UpgradeOutcome outcome = ShopService.upgrade(data, player,
                        server.registryAccess(), player.serverLevel().random);
                if (outcome.ok()) {
                    feedback(player, false, "shop.upgrade_ok",
                            EnchantCatalog.displayName(server.registryAccess(), outcome.enchantId()),
                            data.shop().unlockedSlots());
                } else {
                    feedback(player, true, shopFailKey(outcome.result()));
                }
            }
            ServerSync.sendShop(player);
        });
    }

    private static String shopFailKey(ShopService.Result result) {
        return switch (result) {
            case SLOT_LOCKED -> "shop.fail_slot";
            case LIMIT_REACHED -> "shop.fail_limit";
            case INSUFFICIENT_BALANCE -> "shop.fail_balance";
            case ALL_UNLOCKED -> "shop.fail_all_unlocked";
            case OK -> "shop.fail_unknown";
        };
    }

    private static void handleAcquireSell(ClientPayloads.AcquireSellRequest payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            EconomyData data = EconomyData.get(player.getServer());
            if (data == null) {
                return;
            }
            AcquireService.SellOutcome outcome = AcquireService.sell(data, player, payload.itemId(),
                    payload.count(), payload.all());
            if (outcome.ok()) {
                // 数量与金额都用服务端算好的结果（批次只会吃「整份」，不会把零头算进去）
                feedback(player, false, "acquire.sell_ok", AcquireService.displayName(payload.itemId()),
                        outcome.consumed(), outcome.total());
            } else {
                if (outcome.result() == AcquireService.Result.TOO_FEW_FOR_BATCH) {
                    // 说清「几个算一份」，不然玩家看到「我明明有 16 个」会很困惑
                    int batch = AcquireService.batchOf(data, payload.itemId());
                    feedback(player, true, "acquire.fail_batch",
                            AcquireService.displayName(payload.itemId()), batch, batch,
                            outcome.available());
                } else {
                    String key = switch (outcome.result()) {
                        case UNKNOWN_ITEM -> "acquire.fail_item";
                        case INVALID_COUNT -> "acquire.fail_count";
                        case INSUFFICIENT_ITEMS -> "acquire.fail_no_items";
                        case HAS_DEBT -> "acquire.fail_debt";
                        case TOO_FEW_FOR_BATCH -> "acquire.fail_batch";
                        case OK -> "acquire.fail_unknown";
                    };
                    feedback(player, true, key);
                }
            }
            ServerSync.sendAcquire(player);
        });
    }

    /** 附魔回收：整叠扣除、按附魔价格入账。 */
    private static void handleAcquireEnchantSell(ClientPayloads.AcquireEnchantSellRequest payload,
                                                 IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            EconomyData data = EconomyData.get(player.getServer());
            if (data == null) {
                return;
            }
            EnchantRecycleService.Outcome outcome = EnchantRecycleService.recycle(data, player,
                    payload.slot(), payload.itemId(), payload.count());
            if (outcome.ok()) {
                feedback(player, false, "acquire.enchant_sell_ok", outcome.count(), outcome.total());
            } else {
                String key = switch (outcome.result()) {
                    case ITEM_CHANGED -> "acquire.enchant_fail_changed";
                    case NO_ENCHANTS -> "acquire.enchant_fail_empty";
                    case INVALID_SLOT -> "acquire.fail_count";
                    case OK -> "acquire.fail_unknown";
                };
                feedback(player, true, key);
            }
            ServerSync.sendAcquire(player);
        });
    }
}
