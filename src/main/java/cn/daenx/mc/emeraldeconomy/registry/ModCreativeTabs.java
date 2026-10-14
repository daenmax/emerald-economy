package cn.daenx.mc.emeraldeconomy.registry;

import cn.daenx.mc.emeraldeconomy.EmeraldEconomy;
import cn.daenx.mc.emeraldeconomy.block.MachineType;
import cn.daenx.mc.emeraldeconomy.core.EEConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 创造模式物品栏注册。
 * <p>
 * 五台机器放在同一个标签页里，方便在创造模式测试。
 * 标签页图标用绿宝石图标物品（贴图见 {@code textures/item/emerald_icon.png}）。
 */
public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EmeraldEconomy.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable(EEConstants.langKey("itemGroup.main")))
                    .icon(() -> new ItemStack(ModItems.EMERALD_ICON.get()))
                    .displayItems((parameters, output) -> {
                        for (MachineType type : MachineType.values()) {
                            output.accept(ModBlocks.machine(type).get());
                        }
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
