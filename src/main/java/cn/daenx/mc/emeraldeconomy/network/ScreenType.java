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
    ACQUISITION,
    /**
     * ATM 排行榜（余额 / 经验两个榜单）。
     * <p>
     * 单独做成一种界面类型，是为了让「打开排行榜时实时刷新一次」只发生在排行榜页面，
     * 而不是每次 ATM 存取款都重新排序所有账户。
     */
    RANKING;

    public static ScreenType byIndex(int index) {
        ScreenType[] values = values();
        if (index < 0 || index >= values.length) {
            return ATM;
        }
        return values[index];
    }
}
