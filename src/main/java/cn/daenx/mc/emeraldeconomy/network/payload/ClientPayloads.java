package cn.daenx.mc.emeraldeconomy.network.payload;

import cn.daenx.mc.emeraldeconomy.core.EEConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端 → 服务端的网络包。
 * <p>
 * 所有请求都只携带「意图」，具体的数量校验、余额校验全部在服务端做，
 * 客户端无法通过伪造数据获得好处（防刷）。
 */
public final class ClientPayloads {

    private ClientPayloads() {
    }

    /** ATM 操作类型。 */
    public enum AtmAction {
        /** 存款 */
        DEPOSIT,
        /** 取款 */
        WITHDRAW,
        /** 贷款 */
        LOAN,
        /** 还款 */
        REPAY,
        /** 把背包里的绿宝石全部存入 */
        DEPOSIT_ALL,
        /** 把账户余额全部取出（背包放不下就失败） */
        WITHDRAW_ALL,
        /** 付费解锁「经验存取」 */
        LEVEL_UNLOCK,
        /** 按等级把玩家身上的经验存进 ATM */
        LEVEL_DEPOSIT,
        /** 按等级从 ATM 取回经验 */
        LEVEL_WITHDRAW,
        /** 领取当天的在线奖励（奖励是经验值） */
        CLAIM_ONLINE_REWARD,
        /** 把玩家当前的全部经验存进 ATM */
        LEVEL_DEPOSIT_ALL,
        /** 把 ATM 里的经验全部取回 */
        LEVEL_WITHDRAW_ALL;

        public static AtmAction byIndex(int index) {
            AtmAction[] values = values();
            if (index < 0 || index >= values.length) {
                return DEPOSIT;
            }
            return values[index];
        }
    }

    /** 商店操作类型。 */
    public enum ShopAction {
        /** 购买某个格子 */
        BUY,
        /** 升级商店 */
        UPGRADE;

        public static ShopAction byIndex(int index) {
            ShopAction[] values = values();
            if (index < 0 || index >= values.length) {
                return BUY;
            }
            return values[index];
        }
    }

    /** 请求某个界面的完整数据（客户端打开界面时主动拉取一次）。 */
    public record RequestScreen(int screenType) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<RequestScreen> TYPE =
                new CustomPacketPayload.Type<>(EEConstants.id("request_screen"));

        public static final StreamCodec<RegistryFriendlyByteBuf, RequestScreen> STREAM_CODEC =
                StreamCodec.of(
                        (buf, payload) -> buf.writeVarInt(payload.screenType()),
                        buf -> new RequestScreen(buf.readVarInt()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** ATM 操作请求。 */
    public record AtmActionRequest(int action, long amount) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<AtmActionRequest> TYPE =
                new CustomPacketPayload.Type<>(EEConstants.id("atm_action"));

        public static final StreamCodec<RegistryFriendlyByteBuf, AtmActionRequest> STREAM_CODEC =
                StreamCodec.of(
                        (buf, payload) -> {
                            buf.writeVarInt(payload.action());
                            buf.writeVarLong(payload.amount());
                        },
                        buf -> new AtmActionRequest(buf.readVarInt(), buf.readVarLong()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * 玩家之间的转账 / 经验赠送请求（ATM 排行榜界面用）。
     *
     * @param mode 0 = 余额转账，1 = 经验赠送
     */
    public record AtmTransferRequest(String targetId, long amount, int mode)
            implements CustomPacketPayload {

        /** 余额转账。 */
        public static final int MODE_BALANCE = 0;
        /** 赠送经验。 */
        public static final int MODE_EXPERIENCE = 1;

        public static final CustomPacketPayload.Type<AtmTransferRequest> TYPE =
                new CustomPacketPayload.Type<>(EEConstants.id("atm_transfer"));

        public static final StreamCodec<RegistryFriendlyByteBuf, AtmTransferRequest> STREAM_CODEC =
                StreamCodec.of(
                        (buf, payload) -> {
                            buf.writeUtf(payload.targetId() == null ? "" : payload.targetId());
                            buf.writeVarLong(payload.amount());
                            buf.writeVarInt(payload.mode());
                        },
                        buf -> new AtmTransferRequest(buf.readUtf(), buf.readVarLong(),
                                buf.readVarInt()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * 附魔回收请求。
     * <p>
     * 客户端只发「背包第几格」以及它看到的内容；服务端会重新核对物品与数量，
     * 计价也完全在服务端完成，避免客户端伪造价格。
     */
    public record AcquireEnchantSellRequest(int slot, String itemId, int count)
            implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<AcquireEnchantSellRequest> TYPE =
                new CustomPacketPayload.Type<>(EEConstants.id("acquire_enchant_sell"));

        public static final StreamCodec<RegistryFriendlyByteBuf, AcquireEnchantSellRequest> STREAM_CODEC =
                StreamCodec.of(
                        (buf, payload) -> {
                            buf.writeVarInt(payload.slot());
                            buf.writeUtf(payload.itemId() == null ? "" : payload.itemId());
                            buf.writeVarInt(payload.count());
                        },
                        buf -> new AcquireEnchantSellRequest(buf.readVarInt(), buf.readUtf(),
                                buf.readVarInt()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 基金买卖请求。 */
    public record FundTradeRequest(int fund, boolean buy, long quantity) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<FundTradeRequest> TYPE =
                new CustomPacketPayload.Type<>(EEConstants.id("fund_trade"));

        public static final StreamCodec<RegistryFriendlyByteBuf, FundTradeRequest> STREAM_CODEC =
                StreamCodec.of(
                        (buf, payload) -> {
                            buf.writeVarInt(payload.fund());
                            buf.writeBoolean(payload.buy());
                            buf.writeVarLong(payload.quantity());
                        },
                        buf -> new FundTradeRequest(buf.readVarInt(), buf.readBoolean(), buf.readVarLong()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 抽奖下注请求。 */
    public record LotteryBetRequest(int lotteryType, int choice, long amount) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<LotteryBetRequest> TYPE =
                new CustomPacketPayload.Type<>(EEConstants.id("lottery_bet"));

        public static final StreamCodec<RegistryFriendlyByteBuf, LotteryBetRequest> STREAM_CODEC =
                StreamCodec.of(
                        (buf, payload) -> {
                            buf.writeVarInt(payload.lotteryType());
                            buf.writeVarInt(payload.choice());
                            buf.writeVarLong(payload.amount());
                        },
                        buf -> new LotteryBetRequest(buf.readVarInt(), buf.readVarInt(), buf.readVarLong()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 商店操作请求。 */
    public record ShopActionRequest(int action, String goodKey) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<ShopActionRequest> TYPE =
                new CustomPacketPayload.Type<>(EEConstants.id("shop_action"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ShopActionRequest> STREAM_CODEC =
                StreamCodec.of(
                        (buf, payload) -> {
                            buf.writeVarInt(payload.action());
                            buf.writeUtf(payload.goodKey() == null ? "" : payload.goodKey());
                        },
                        buf -> new ShopActionRequest(buf.readVarInt(), buf.readUtf()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * 收购出售请求。
     *
     * @param itemId 物品注册名或 {@code #标签}
     * @param count  出售数量（{@code all = true} 时忽略）
     * @param all    true 表示「出售全部」：由服务端按背包里的实际数量结算，
     *               避免客户端对藏宝图 / 标签行统计不准
     */
    public record AcquireSellRequest(String itemId, int count, boolean all) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<AcquireSellRequest> TYPE =
                new CustomPacketPayload.Type<>(EEConstants.id("acquire_sell"));

        public static final StreamCodec<RegistryFriendlyByteBuf, AcquireSellRequest> STREAM_CODEC =
                StreamCodec.of(
                        (buf, payload) -> {
                            buf.writeUtf(payload.itemId());
                            buf.writeVarInt(payload.count());
                            buf.writeBoolean(payload.all());
                        },
                        buf -> new AcquireSellRequest(buf.readUtf(), buf.readVarInt(), buf.readBoolean()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
