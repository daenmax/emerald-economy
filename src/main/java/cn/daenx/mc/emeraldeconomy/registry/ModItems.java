package cn.daenx.mc.emeraldeconomy.registry;

import cn.daenx.mc.emeraldeconomy.EmeraldEconomy;
import cn.daenx.mc.emeraldeconomy.block.MachineType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 物品注册。
 * <p>
 * 机器物品都是机器方块的 BlockItem；此外还有一个「只为图标存在」的物品，
 * 用来当创造模式标签页的图标（见 {@link #EMERALD_ICON}）。
 */
public final class ModItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(EmeraldEconomy.MOD_ID);

    private static final Map<MachineType, DeferredItem<BlockItem>> MACHINE_ITEMS =
            new EnumMap<>(MachineType.class);

    /**
     * 绿宝石图标物品。
     * <p>
     * 创造模式标签页的图标必须是一个 {@code ItemStack}，而模组里没有「货币物品」
     * （货币就是原版绿宝石），所以单独注册一个只用于展示的物品：
     * 它<b>不放进标签页</b>，也不参与任何玩法，玩家只能通过 {@code /give} 看到它。
     */
    public static final DeferredItem<Item> EMERALD_ICON =
            ITEMS.registerSimpleItem("emerald_icon", new Item.Properties());

    static {
        for (MachineType type : MachineType.values()) {
            MACHINE_ITEMS.put(type, ITEMS.registerSimpleBlockItem(ModBlocks.machine(type)));
        }
    }

    private ModItems() {
    }

    /** 取某台机器对应的物品。 */
    public static DeferredItem<BlockItem> machineItem(MachineType type) {
        return MACHINE_ITEMS.get(type);
    }

    /** 全部机器物品（只读）。 */
    public static Map<MachineType, DeferredItem<BlockItem>> machineItems() {
        return Collections.unmodifiableMap(MACHINE_ITEMS);
    }
}
