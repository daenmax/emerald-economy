package cn.daenx.mc.emeraldeconomy.client;

import cn.daenx.mc.emeraldeconomy.core.EEConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 「没有权限修改配置」的提示界面。
 * <p>
 * 配置按钮本身对所有玩家开放（这样大家都能点开看看当前规则），
 * 但真正能改的只有服主 / OP，其他玩家点开时看到这个界面。
 */
public class ConfigDeniedScreen extends Screen {

    private final Screen parent;

    public ConfigDeniedScreen(Screen parent) {
        super(Component.translatable(EEConstants.langKey("config.title")));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable(EEConstants.langKey("screen.common.back")),
                        b -> this.minecraft.setScreen(this.parent))
                .bounds(this.width / 2 - 60, this.height / 2 + 30, 120, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        List<Component> lines = List.of(
                Component.translatable(EEConstants.langKey("config.no_permission")),
                Component.translatable(EEConstants.langKey("config.no_permission_hint")));
        int y = this.height / 2 - 20;
        graphics.drawCenteredString(this.font, this.title, this.width / 2, y - 20, 0xFFFFD75E);
        for (Component line : lines) {
            graphics.drawCenteredString(this.font, line, this.width / 2, y, 0xFFFF6060);
            y += 14;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
