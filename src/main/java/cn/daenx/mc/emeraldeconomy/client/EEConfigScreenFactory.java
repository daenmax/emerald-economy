package cn.daenx.mc.emeraldeconomy.client;

import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * 配置界面工厂。
 * <p>
 * 模组列表里的「配置」按钮会调用 {@link #createScreen}：
 * <ul>
 *     <li>有权限（单机房主 / 联机 OP）：交给 NeoForge 自带的
 *         {@link ConfigurationScreen} 渲染，它会按 {@code ModConfigSpec} 自动生成控件</li>
 *     <li>没权限：显示一个只说「为什么不能改」的提示界面</li>
 * </ul>
 * 这样「配置」按钮不再是灰的（能看到当前配置），但真正能改的只有服主。
 */
public final class EEConfigScreenFactory implements IConfigScreenFactory {

    @Override
    public Screen createScreen(ModContainer container, Screen parent) {
        if (EmeraldEconomyClient.canEditServerConfig()) {
            return new ConfigurationScreen(container, parent);
        }
        return new ConfigDeniedScreen(parent);
    }
}
