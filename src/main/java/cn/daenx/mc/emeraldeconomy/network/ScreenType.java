package cn.daenx.mc.emeraldeconomy.network;

/**
 * 界面类型。
 * <p>
 * 客户端和服务端共用：服务端决定「玩家点了哪台机器、要打开哪个界面」，
 * 客户端收到 {@code OpenScreenPayload} 后按这个枚举创建对应的 Screen。
 */
public enum ScreenType {
    /** ATM 存取款与贷款 */
    ATM,
    /** 基金交易所 */
    EXCHANGE,
    /** 抽奖机 */
    LOTTERY,
    /** 自动售货机（附魔书商店） */
    VENDING,
    /** 自动收购机 */
    ACQUISITION;

    public static ScreenType byIndex(int index) {
        ScreenType[] values = values();
        if (index < 0 || index >= values.length) {
            return ATM;
        }
        return values[index];
    }
}
