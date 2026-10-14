package cn.daenx.mc.emeraldeconomy.core;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 提示与广播工具。
 * <p>
 * 需求文档要求「所有广播使用聊天栏，格式统一，带有 emoji 或前缀，便于识别」，
 * 统一在这里拼接前缀，避免每条消息各写一遍。
 */
public final class Notify {

    /** 各类消息的 emoji 前缀。 */
    private static final String PREFIX_ECONOMY = "💰 ";
    private static final String PREFIX_MARKET = "📈 ";
    private static final String PREFIX_LOTTERY = "🎲 ";
    private static final String PREFIX_SHOP = "🏪 ";
    private static final String PREFIX_ACQUIRE = "📦 ";
    private static final String PREFIX_SYSTEM = "⚙️ ";

    private Notify() {
    }

    /**
     * 拼一条本模组的可翻译文本。
     * <p>
     * 聊天栏与机器界面共用同一份组件：服务端只决定「用哪个键、带哪些参数」，
     * 客户端按自己的语言渲染，所以不会把文案固定成服务端的语言。
     */
    public static Component component(String langPath, Object... args) {
        return Component.translatable(EEConstants.langKey(langPath), args);
    }

    /** 拼一条带前缀的可翻译文本。 */
    public static Component componentPrefixed(String prefix, String langPath, Object... args) {
        return Component.literal(prefix).append(component(langPath, args));
    }

    /** 给单个玩家发送一条聊天栏消息。 */
    public static void send(ServerPlayer player, String langPath, Object... args) {
        player.displayClientMessage(component(langPath, args), false);
    }

    /** 给单个玩家发送一条带前缀的聊天栏消息。 */
    public static void sendPrefixed(ServerPlayer player, String prefix, String langPath, Object... args) {
        player.displayClientMessage(componentPrefixed(prefix, langPath, args), false);
    }

    /** 全服广播。 */
    public static void broadcast(MinecraftServer server, String prefix, String langPath, Object... args) {
        Component message = Component.literal(prefix)
                .append(Component.translatable(EEConstants.langKey(langPath), args));
        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    /** 全服广播：经济类。 */
    public static void broadcastEconomy(MinecraftServer server, String langPath, Object... args) {
        broadcast(server, PREFIX_ECONOMY, langPath, args);
    }

    /** 全服广播：市场类。 */
    public static void broadcastMarket(MinecraftServer server, String langPath, Object... args) {
        broadcast(server, PREFIX_MARKET, langPath, args);
    }

    /** 全服广播：抽奖类。 */
    public static void broadcastLottery(MinecraftServer server, String langPath, Object... args) {
        broadcast(server, PREFIX_LOTTERY, langPath, args);
    }

    /** 全服广播：商店类。 */
    public static void broadcastShop(MinecraftServer server, String langPath, Object... args) {
        broadcast(server, PREFIX_SHOP, langPath, args);
    }

    /** 全服广播：收购类。 */
    public static void broadcastAcquire(MinecraftServer server, String langPath, Object... args) {
        broadcast(server, PREFIX_ACQUIRE, langPath, args);
    }

    /** 全服广播：系统类。 */
    public static void broadcastSystem(MinecraftServer server, String langPath, Object... args) {
        broadcast(server, PREFIX_SYSTEM, langPath, args);
    }
}
