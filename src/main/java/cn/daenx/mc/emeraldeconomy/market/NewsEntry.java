package cn.daenx.mc.emeraldeconomy.market;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * 一条市场新闻（随机市场事件或天气通报）。
 * <p>
 * 只保存「翻译键 + 参数」，让客户端按自己的语言渲染，而不是把中文写死进存档。
 */
public class NewsEntry {

    private static final String KEY_LANG = "lang";
    private static final String KEY_ARGS = "args";
    private static final String KEY_DAY = "day";
    private static final String KEY_KIND = "kind";

    /** 新闻种类，决定界面中的图标前缀。 */
    public static final String KIND_WEATHER = "weather";
    public static final String KIND_EVENT = "event";
    public static final String KIND_SETTLE = "settle";

    private final String langKey;
    private final List<String> args;
    private final long day;
    private final String kind;

    public NewsEntry(String langKey, List<String> args, long day, String kind) {
        this.langKey = langKey;
        this.args = List.copyOf(args);
        this.day = day;
        this.kind = kind;
    }

    public String langKey() {
        return langKey;
    }

    public List<String> args() {
        return args;
    }

    public long day() {
        return day;
    }

    public String kind() {
        return kind;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY_LANG, langKey);
        ListTag list = new ListTag();
        args.forEach(arg -> list.add(StringTag.valueOf(arg)));
        tag.put(KEY_ARGS, list);
        tag.putLong(KEY_DAY, day);
        tag.putString(KEY_KIND, kind);
        return tag;
    }

    public static NewsEntry load(CompoundTag tag) {
        List<String> args = new ArrayList<>();
        ListTag list = tag.getList(KEY_ARGS, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            args.add(list.getString(i));
        }
        return new NewsEntry(
                tag.getString(KEY_LANG),
                args,
                tag.getLong(KEY_DAY),
                tag.contains(KEY_KIND) ? tag.getString(KEY_KIND) : KIND_EVENT);
    }
}
