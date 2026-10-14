package cn.daenx.mc.emeraldeconomy.market;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 单支基金的运行时状态（需求文档 3.3.1）。
 * <p>
 * 包含：当前价格、基础价格、动量、波动率、均值回归系数，以及用于走势图的价格历史。
 */
public class FundState {

    private static final String KEY_PRICE = "price";
    private static final String KEY_BASE = "base";
    private static final String KEY_MOMENTUM = "momentum";
    private static final String KEY_PREV_CLOSE = "prevClose";
    private static final String KEY_HISTORY = "history";

    private final FundType type;

    /** 当前价格（整数，最小 1）。 */
    private int price;
    /** 长期锚定的基础价格。 */
    private int basePrice;
    /** 当前动量，正数表示上涨趋势。 */
    private double momentum;
    /** 上一次每日结算时的收盘价，用于计算涨跌幅。 */
    private int previousClose;
    /** 价格历史（旧的在前，最新的在最后），用于走势图。 */
    private final Deque<Integer> history = new ArrayDeque<>();
    /** 价格历史最多保留多少个点。 */
    private int historySize;

    public FundState(FundType type, int basePrice, int historySize) {
        this.type = type;
        this.basePrice = Math.max(1, basePrice);
        this.price = this.basePrice;
        this.previousClose = this.basePrice;
        this.momentum = 0.0D;
        this.historySize = Math.max(1, historySize);
    }

    public FundType type() {
        return type;
    }

    public int price() {
        return price;
    }

    public void setPrice(int price) {
        this.price = Math.max(1, price);
    }

    public int basePrice() {
        return basePrice;
    }

    public void setBasePrice(int basePrice) {
        this.basePrice = Math.max(1, basePrice);
    }

    public double momentum() {
        return momentum;
    }

    public void setMomentum(double momentum) {
        this.momentum = momentum;
    }

    public void addMomentum(double delta) {
        this.momentum += delta;
    }

    public int previousClose() {
        return previousClose;
    }

    public void setPreviousClose(int previousClose) {
        this.previousClose = Math.max(1, previousClose);
    }

    public void setHistorySize(int historySize) {
        this.historySize = Math.max(1, historySize);
        while (history.size() > this.historySize) {
            history.pollFirst();
        }
    }

    /** 记录一个价格点（用于走势图与收盘价）。 */
    public void pushHistory(int value) {
        history.addLast(Math.max(1, value));
        while (history.size() > historySize) {
            history.pollFirst();
        }
    }

    /** 价格历史（旧的在前）。 */
    public int[] history() {
        int[] out = new int[history.size()];
        int i = 0;
        for (int v : history) {
            out[i++] = v;
        }
        return out;
    }

    /**
     * 相对于上次收盘价的涨跌幅（例如 {@code 0.0123} 表示上涨 1.23%）。
     * <p>
     * 上一次收盘价缺失时以基础价格作为参照。
     */
    public double changeRatio() {
        int reference = previousClose > 0 ? previousClose : basePrice;
        if (reference <= 0) {
            return 0.0D;
        }
        return (double) (price - reference) / (double) reference;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(KEY_PRICE, price);
        tag.putInt(KEY_BASE, basePrice);
        tag.putDouble(KEY_MOMENTUM, momentum);
        tag.putInt(KEY_PREV_CLOSE, previousClose);
        ListTag list = new ListTag();
        for (int v : history) {
            list.add(net.minecraft.nbt.IntTag.valueOf(v));
        }
        tag.put(KEY_HISTORY, list);
        return tag;
    }

    public void load(CompoundTag tag, int fallbackBasePrice) {
        this.basePrice = Math.max(1, tag.contains(KEY_BASE) ? tag.getInt(KEY_BASE) : fallbackBasePrice);
        this.price = Math.max(1, tag.contains(KEY_PRICE) ? tag.getInt(KEY_PRICE) : basePrice);
        this.momentum = tag.getDouble(KEY_MOMENTUM);
        this.previousClose = Math.max(1, tag.contains(KEY_PREV_CLOSE) ? tag.getInt(KEY_PREV_CLOSE) : price);
        history.clear();
        ListTag list = tag.getList(KEY_HISTORY, Tag.TAG_INT);
        for (int i = 0; i < list.size(); i++) {
            history.addLast(Math.max(1, list.getInt(i)));
        }
    }
}
