package cn.daenx.mc.emeraldeconomy.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 配置文件里「字符串表格」的解析工具。
 * <p>
 * 为了让配置更灵活，本模组把价格表、修正表一类的数据放进配置文件，用
 * {@code key=value} 的字符串列表表示，例如：
 * <pre>
 *   "minecraft:wheat=8"
 *   "EMR_MIN=100"
 *   "RAIN:EMR_AGR=0.08"
 * </pre>
 * 以 {@code #} 开头的行会被当作注释忽略。
 */
public final class Params {

    private Params() {
    }

    /** 解析 {@code key=double} 形式的字符串列表。 */
    public static Map<String, Double> parseDoubleMap(List<? extends String> raw) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (String line : clean(raw)) {
            int idx = line.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            String key = line.substring(0, idx).trim();
            String value = line.substring(idx + 1).trim();
            try {
                map.put(key, Double.parseDouble(value));
            } catch (NumberFormatException ignored) {
                // 配置写错时忽略该条，避免整个服务端启动失败
            }
        }
        return map;
    }

    /** 解析 {@code key=long} 形式的字符串列表。 */
    public static Map<String, Long> parseLongMap(List<? extends String> raw) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (String line : clean(raw)) {
            int idx = line.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            String key = line.substring(0, idx).trim();
            String value = line.substring(idx + 1).trim();
            try {
                map.put(key, Long.parseLong(value));
            } catch (NumberFormatException ignored) {
                // 同上
            }
        }
        return map;
    }

    /**
     * 解析 {@code key=long} 形式的字符串列表，<b>但保留以 {@code #} 开头的键</b>。
     * <p>
     * 收购价表里 {@code #} 有特殊含义（{@code #minecraft:fishes=2} 表示收整个物品标签），
     * 不能按注释丢掉。没有 {@code =} 的行仍然会被跳过，所以「# 这是注释」这种写法照样安全。
     */
    public static Map<String, Long> parseLongMapWithTags(List<? extends String> raw) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (String line : clean(raw, true)) {
            int idx = line.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            String key = line.substring(0, idx).trim();
            String value = line.substring(idx + 1).trim();
            try {
                map.put(key, Long.parseLong(value));
            } catch (NumberFormatException ignored) {
                // 配置写错时忽略该条
            }
        }
        return map;
    }

    /** 把浮点表取整成整数表（用于价格）。 */
    public static Map<String, Long> parseRoundedLongMap(List<? extends String> raw) {
        Map<String, Double> doubles = parseDoubleMap(raw);
        Map<String, Long> map = new LinkedHashMap<>();
        doubles.forEach((k, v) -> map.put(k, Math.round(v)));
        return map;
    }

    /** 读取 double，键不存在时返回默认值。 */
    public static double getDouble(Map<String, Double> map, String key, double fallback) {
        Double v = map.get(key);
        return v == null ? fallback : v;
    }

    /** 读取 long，键不存在时返回默认值。 */
    public static long getLong(Map<String, Long> map, String key, long fallback) {
        Long v = map.get(key);
        return v == null ? fallback : v;
    }

    /** 读取 int，键不存在时返回默认值。 */
    public static int getInt(Map<String, Long> map, String key, int fallback) {
        Long v = map.get(key);
        return v == null ? fallback : v.intValue();
    }

    /** 去掉空行与注释行，并裁剪空白。 */
    private static List<String> clean(List<? extends String> raw) {
        return clean(raw, false);
    }

    /**
     * 去掉空行（并按需保留 {@code #} 开头的行）后裁剪空白。
     *
     * @param keepHash true 表示把 {@code #} 开头的行当作正式数据（价格表里的物品标签）
     */
    private static List<String> clean(List<? extends String> raw, boolean keepHash) {
        List<String> out = new ArrayList<>(raw.size());
        for (String line : raw) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!keepHash && trimmed.startsWith("#")) {
                continue;
            }
            out.add(trimmed);
        }
        return out;
    }

    /** 把字符串转成大写枚举名，解析失败时返回默认值。 */
    public static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, E fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
