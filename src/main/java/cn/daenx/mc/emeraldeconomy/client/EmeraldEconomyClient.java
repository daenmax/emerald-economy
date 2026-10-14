package cn.daenx.mc.emeraldeconomy.client;

import cn.daenx.mc.emeraldeconomy.EmeraldEconomy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * 模组的客户端入口（只在物理客户端加载）。
 * <p>
 * 存在的唯一理由是给「模组列表 → 配置」按钮提供界面：
 * NeoForge 的 {@code ModListScreen} 只在模组容器注册了 {@link IConfigScreenFactory}
 * 扩展点时才让那个按钮可点（{@code configButton.active = IConfigScreenFactory.getForMod(...).isPresent()}）。
 * 我们只注册了 SERVER 类型的配置，NeoForge 不会自动补一个工厂，所以按钮一直是灰的。
 * <p>
 * 按 NeoForge 官方文档的做法，扩展点必须注册在<b>客户端专属</b>的 {@code @Mod(dist = CLIENT)}
 * 类里——{@link IConfigScreenFactory} 本身是客户端类，放在通用构造里会让专用服务端加载不到它。
 */
@Mod(value = EmeraldEconomy.MOD_ID, dist = Dist.CLIENT)
public class EmeraldEconomyClient {

    public EmeraldEconomyClient(IEventBus modEventBus, ModContainer modContainer) {
        // 先建好实例再注册：registerExtensionPoint 有 (Class, T) 与 (Class, Supplier<T>)
        // 两个重载，写成构造器引用会「引用不明确」
        IConfigScreenFactory factory = new EEConfigScreenFactory();
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, factory);
    }

    /**
     * 判断当前客户端上的玩家有没有资格修改服务端配置。
     * <p>
     * 规则（需求：除了服主，其他玩家不能修改）：
     * <ul>
     *     <li>单机：只有这个存档的房主可以改</li>
     *     <li>联机：权限等级 &ge; 2（OP / 服主）才可以改</li>
     *     <li>没进世界（主菜单）：拒绝，此时既没有房主也没有 OP 身份</li>
     * </ul>
     */
    static boolean canEditServerConfig() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.hasSingleplayerServer() && minecraft.getSingleplayerServer() != null
                && minecraft.getSingleplayerServer().isSingleplayerOwner(minecraft.getGameProfile())) {
            return true;
        }
        return minecraft.player != null && minecraft.player.getPermissionLevel() >= 2;
    }
}
