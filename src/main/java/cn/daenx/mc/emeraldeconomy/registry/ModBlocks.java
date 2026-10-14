package cn.daenx.mc.emeraldeconomy.registry;

import cn.daenx.mc.emeraldeconomy.EmeraldEconomy;
import cn.daenx.mc.emeraldeconomy.block.MachineBlock;
import cn.daenx.mc.emeraldeconomy.block.MachineType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 方块注册。
 * <p>
 * 五台机器共用 {@link MachineBlock}，只在外观和打开的界面上有区别。
 */
public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(EmeraldEconomy.MOD_ID);

    private static final Map<MachineType, DeferredBlock<MachineBlock>> MACHINES =
            new EnumMap<>(MachineType.class);

    static {
        for (MachineType type : MachineType.values()) {
            MACHINES.put(type, BLOCKS.registerBlock(
                    type.registryName(),
                    properties -> new MachineBlock(type, properties),
                    machineProperties()));
        }
    }

    private ModBlocks() {
    }

    /**
     * 机器的通用方块属性：金属质感，**硬度与铁块一致**（5.0），爆炸抗性也是铁块的 6.0。
     * <p>
     * 刻意<b>不</b>加 {@code requiresCorrectToolForDrops()}：需求方反馈「用镐子挖很慢而且没有掉落」，
     * 根因就是这条要求——手上没拿对工具时，原版不仅挖得慢（硬度按 5 倍算），而且一颗都不掉。
     * 现在任何工具都能挖下来（镐子最快），生存模式破坏任意一半都恰好掉落一台。
     */
    private static BlockBehaviour.Properties machineProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(5.0F, 6.0F)
                .sound(SoundType.METAL)
                .noOcclusion();
    }

    /** 取某台机器的方块。 */
    public static DeferredBlock<MachineBlock> machine(MachineType type) {
        return MACHINES.get(type);
    }

    /** 全部机器方块（只读）。 */
    public static Map<MachineType, DeferredBlock<MachineBlock>> machines() {
        return Collections.unmodifiableMap(MACHINES);
    }

    /** 便于 IDE 直接引用。 */
    public static final DeferredBlock<MachineBlock> ATM = machine(MachineType.ATM);
    public static final DeferredBlock<MachineBlock> EXCHANGE = machine(MachineType.EXCHANGE);
    public static final DeferredBlock<MachineBlock> LOTTERY = machine(MachineType.LOTTERY);
    public static final DeferredBlock<MachineBlock> VENDING = machine(MachineType.VENDING);
    public static final DeferredBlock<MachineBlock> ACQUISITION = machine(MachineType.ACQUISITION);
}
