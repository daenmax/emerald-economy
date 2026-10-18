package cn.daenx.mc.emeraldeconomy.client.screen;

import cn.daenx.mc.emeraldeconomy.core.EEConstants;
import cn.daenx.mc.emeraldeconomy.network.ScreenType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * ATM 子页面（存款取款 / 贷款还款 / 等级存取 / 在线奖励）的基类。
 * <p>
 * 需求把 ATM 主界面改成「只显示账户概况 + 一排功能按钮」，
 * 具体操作与费率说明都挪进各自的子页面。所有子页面共用 ATM 快照
 * （{@link ScreenType#ATM}），所以服务端回推的账户数据会同时刷新它们。
 * <p>
 * 这里统一放「返回 ATM 主界面」的按钮与翻译键辅助方法，避免每个子页面抄一遍。
 */
public abstract class AtmSubScreen extends EEScreen {

    protected AtmSubScreen(Component title) {
        super(title);
    }

    @Override
    protected ScreenType screenType() {
        return ScreenType.ATM;
    }

    /** 底部左侧的「返回」按钮：回到 ATM 主界面。 */
    protected void addBackButton() {
        addRenderableWidget(Button.builder(label("screen.common.back"),
                        b -> Minecraft.getInstance().setScreen(new AtmScreen()))
                .bounds(contentX(), panelY + panelH - 24, 60, 18).build());
    }

    /** 本模组的翻译键快捷方式。 */
    protected static Component label(String path) {
        return Component.translatable(EEConstants.langKey(path));
    }
}
