package cn.daenx.mc.emeraldeconomy.client.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * 只接受整数的输入框。
 * <p>
 * 本模组的金额、数量、份额都是整数，输入框直接过滤掉非数字字符，
 * 避免把非法字符串发到服务端。
 */
public class NumberBox extends EditBox {

    /** 允许输入的最大位数（防止 Long 溢出）。 */
    private static final int MAX_DIGITS = 15;

    public NumberBox(Font font, int x, int y, int width, int height, String initialValue) {
        super(font, x, y, width, height, Component.empty());
        setMaxLength(MAX_DIGITS);
        setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        setValue(initialValue);
    }

    /** 读取当前值，空串或非法时返回 0。 */
    public long longValue() {
        String value = getValue();
        if (value.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
}
