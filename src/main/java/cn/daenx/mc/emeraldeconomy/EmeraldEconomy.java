package cn.daenx.mc.emeraldeconomy;

import cn.daenx.mc.emeraldeconomy.config.EEConfig;
import cn.daenx.mc.emeraldeconomy.core.EEConstants;
import cn.daenx.mc.emeraldeconomy.registry.ModBlocks;
import cn.daenx.mc.emeraldeconomy.registry.ModCreativeTabs;
import cn.daenx.mc.emeraldeconomy.registry.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.daenx.mc.emeraldeconomy.network.NetworkHandler;

/**
 * 绿宝石经济（Emerald Economy）模组主类。
 * <p>
 * 版本组合：Minecraft 1.21.1 + NeoForge 21.1.x + Java 21。
 * <p>
 * 模组提供六套互相打通的经济玩法，全部围绕统一的玩家账户结算：
 * ATM 存取款与贷款、基金理财、抽奖、商店、收购，加上每日统一结算与广播。
 */
@Mod(EmeraldEconomy.MOD_ID)
public class EmeraldEconomy {

    /** 模组 ID，与 {@code neoforge.mods.toml} 保持一致。 */
    public static final String MOD_ID = EEConstants.MOD_ID;

    /** 日志。 */
    public static final Logger LOGGER = LoggerFactory.getLogger("EmeraldEconomy");

    public EmeraldEconomy(IEventBus modEventBus, ModContainer modContainer) {
        // 注册方块、物品、创造模式标签页
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);

        // 注册网络包（RegisterPayloadHandlersEvent 派发在模组事件总线上）
        modEventBus.addListener(NetworkHandler::register);

        // 服务端配置
        modContainer.registerConfig(ModConfig.Type.SERVER, EEConfig.SPEC);

        LOGGER.info("Emerald Economy 已加载（Minecraft 1.21.1 / NeoForge 21.1.x）");
    }
}
