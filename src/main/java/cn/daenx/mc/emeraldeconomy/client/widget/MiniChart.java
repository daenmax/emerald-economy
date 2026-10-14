package cn.daenx.mc.emeraldeconomy.client.widget;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 迷你走势图（需求文档 3.5.1 底部的「最近 20 个价格点」）。
 * <p>
 * 直接用 {@link GuiGraphics#fill} 逐像素列绘制折线，不需要额外的渲染类型，
 * 配合面板的半透明背景看起来就是一张简单的行情图。
 */
public final class MiniChart {

    private static final int COLOR_BG = 0x50000000;
    private static final int COLOR_GRID = 0x30FFFFFF;
    private static final int COLOR_BASELINE = 0x60FFD75E;

    private MiniChart() {
    }

    /**
     * 画一张折线图。
     *
     * @param values    价格序列（旧的在前）
     * @param lineColor 折线颜色
     * @param baseValue 基准价格（画一条参考虚线），小于等于 0 时不画
     */
    public static void draw(GuiGraphics graphics, int x, int y, int width, int height,
                            int[] values, int lineColor, int baseValue) {
        if (width <= 2 || height <= 2) {
            return;
        }
        graphics.fill(x, y, x + width, y + height, COLOR_BG);
        // 横向网格
        for (int i = 1; i < 4; i++) {
            int gy = y + height * i / 4;
            graphics.fill(x, gy, x + width, gy + 1, COLOR_GRID);
        }
        if (values == null || values.length == 0) {
            return;
        }

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int value : values) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        if (baseValue > 0) {
            min = Math.min(min, baseValue);
            max = Math.max(max, baseValue);
        }
        if (max <= min) {
            // 价格没有变化时，把它画在中间
            max = min + 1;
        }

        if (baseValue > 0) {
            int baseline = valueToY(baseValue, min, max, y, height);
            for (int px = 0; px < width; px += 3) {
                graphics.fill(x + px, baseline, x + px + 2, baseline + 1, COLOR_BASELINE);
            }
        }

        int count = values.length;
        int previousY = -1;
        for (int px = 0; px < width; px++) {
            double t = count == 1 ? 0.0D : (double) px / (double) (width - 1) * (count - 1);
            int low = (int) Math.floor(t);
            int high = Math.min(count - 1, low + 1);
            double frac = t - low;
            double value = values[low] * (1.0D - frac) + values[high] * frac;
            int py = valueToY(value, min, max, y, height);
            graphics.fill(x + px, py, x + px + 1, py + 1, lineColor);
            // 把相邻两列连起来，避免陡峭时出现断点
            if (previousY >= 0 && Math.abs(previousY - py) > 1) {
                int from = Math.min(previousY, py);
                int to = Math.max(previousY, py);
                graphics.fill(x + px, from, x + px + 1, to + 1, lineColor);
            }
            previousY = py;
        }
    }

    private static int valueToY(double value, int min, int max, int y, int height) {
        double ratio = (value - min) / (double) (max - min);
        int py = y + height - 1 - (int) Math.round(ratio * (height - 1));
        return Math.max(y, Math.min(y + height - 1, py));
    }
}
