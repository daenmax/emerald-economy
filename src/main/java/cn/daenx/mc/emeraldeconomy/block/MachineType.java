package cn.daenx.mc.emeraldeconomy.block;

import cn.daenx.mc.emeraldeconomy.core.EEConstants;
import cn.daenx.mc.emeraldeconomy.network.ScreenType;

import java.util.Locale;

/**
 * 五台功能机器的类型。
 * <p>
 * 它们的行为完全一样（右键开界面、必须放在红石块上），区别只有外观和打开的界面，
 * 所以共用一个 {@link MachineBlock} 实现。
 */
public enum MachineType {

    /** ATM：存取款与贷款 */
    ATM(ScreenType.ATM),
    /** 交易所电脑：基金理财 */
    EXCHANGE(ScreenType.EXCHANGE),
    /** 抽奖机 */
    LOTTERY(ScreenType.LOTTERY),
    /** 自动售货机：附魔书商店 */
    VENDING(ScreenType.VENDING),
    /** 自动收购机 */
    ACQUISITION(ScreenType.ACQUISITION);

    private final ScreenType screen;
    private final String registryName = name().toLowerCase(Locale.ROOT);

    MachineType(ScreenType screen) {
        this.screen = screen;
    }

    /** 注册名（同时也是资源路径与方块状态名）。 */
    public String registryName() {
        return registryName;
    }

    /** 右键后要打开的界面。 */
    public ScreenType screen() {
        return screen;
    }

    /**
     * 方块名翻译键。
     * <p>
     * 必须是原版格式 {@code block.emerald_economy.atm}——方块物品的名字
     * 由 {@code Block.getDescriptionId()} 决定，写成 {@code emerald_economy.block.atm}
     * 游戏会查不到，创造模式物品栏里就会显示成英文注册名。
     */
    public String langKey() {
        return EEConstants.blockDescriptionKey(registryName);
    }
}
