package cn.daenx.mc.emeraldeconomy.event;

import cn.daenx.mc.emeraldeconomy.command.EECommands;
import cn.daenx.mc.emeraldeconomy.content.EconomyContent;
import cn.daenx.mc.emeraldeconomy.core.DaySettlement;
import cn.daenx.mc.emeraldeconomy.core.EEConstants;
import cn.daenx.mc.emeraldeconomy.data.AccountData;
import cn.daenx.mc.emeraldeconomy.data.EconomyData;
import cn.daenx.mc.emeraldeconomy.dev.SelfTest;
import cn.daenx.mc.emeraldeconomy.market.SupplyTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 游戏事件监听（需求文档 3.3.2 与第七节）。
 * <p>
 * 这里的回调都保持「轻量」：只做计数，不做价格计算。
 * 复杂运算统一放到 {@link DaySettlement} 与服务端 Tick 流程里执行。
 */
@EventBusSubscriber(modid = EEConstants.MOD_ID)
public final class GameEvents {

    private GameEvents() {
    }

    /** 服务端每刻：价格更新与每日结算的调度入口。 */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        DaySettlement.onServerTick(event.getServer());
    }

    /** 服务器启动完成：加载内容文件（售货机货架 / 收购价表），按需运行开发者自检。 */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        // 内容文件要在这里加载：物品标签是在启动过程中绑定的，更早读会拿到空标签
        EconomyContent.reload(event.getServer());
        if (SelfTest.enabled()) {
            SelfTest.run(event.getServer());
        }
    }

    /** 服务器停止：清理内存中的冷却与统计缓存。 */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        DaySettlement.onServerStopping();
    }

    /** 破坏方块：矿石 / 原木 / 成熟作物等累加供给量。 */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        EconomyData data = economy(level);
        if (data == null) {
            return;
        }
        SupplyTracker.onBlockBroken(data, event.getPos(), event.getState());
    }

    /** 放置方块：放水或熔岩算水电供给。 */
    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        LevelAccessor accessor = event.getLevel();
        if (!(accessor instanceof ServerLevel level)) {
            return;
        }
        EconomyData data = economy(level);
        if (data == null) {
            return;
        }
        SupplyTracker.onBlockPlaced(data, event.getPlacedBlock());
    }

    /** 玩家使用物品：倒水 / 倒熔岩算水电供给。 */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        EconomyData data = economy(level);
        if (data == null) {
            return;
        }
        SupplyTracker.onItemUsed(data, event.getItemStack());
    }

    /** 实体死亡：被动动物算畜牧，怪物与水生生物算狩猎。 */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) {
            return;
        }
        Entity killer = event.getSource().getEntity();
        if (killer == null) {
            return;
        }
        EconomyData data = economy(level);
        if (data == null) {
            return;
        }
        SupplyTracker.onEntityKilled(data, victim, killer);
    }

    /** 玩家登录：确保账户存在并刷新玩家名。 */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        EconomyData data = EconomyData.get(server);
        AccountData account = data.account(player.getUUID());
        account.setName(player.getGameProfile().getName());
        data.setDirty();
    }

    /** 玩家退出：清理交易统计基线。 */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        SupplyTracker.forget(player);
        if (player instanceof ServerPlayer serverPlayer) {
            cn.daenx.mc.emeraldeconomy.network.ServerSync.forget(serverPlayer);
        }
    }

    /** 注册命令。 */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        EECommands.register(event.getDispatcher());
    }

    private static EconomyData economy(ServerLevel level) {
        MinecraftServer server = level.getServer();
        return server == null ? null : EconomyData.get(server);
    }
}
