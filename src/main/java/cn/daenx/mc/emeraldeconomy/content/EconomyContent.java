package cn.daenx.mc.emeraldeconomy.content;

import cn.daenx.mc.emeraldeconomy.EmeraldEconomy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 由 JSON 文件驱动的「内容配置」：自动售货机的货架与自动收购机的价格表。
 * <p>
 * 为什么不用模组配置（TOML）：配置界面对列表只能改不能方便地增删，
 * 而这两张表天生是「随时想加一条」的东西。所以：
 * <ul>
 *     <li>世界内容放在 {@code config/emerald_economy/shop.json} 与 {@code acquire.json}，
 *         第一次启动时自动从模组内置的默认模板生成，之后以这两个文件为准；</li>
 *     <li>直接编辑 JSON 即可，游戏里执行 {@code /ee reload} 立刻生效（不用重启）；</li>
 *     <li>数值型设置（费率、概率、限购次数……）仍然留在 TOML，方便游戏内配置界面改。</li>
 * </ul>
 * 解析失败的策略是「保留上一次的正确内容」，避免一个手滑打成语法错误就把商店变空。
 */
public final class EconomyContent {

    /** 内容文件所在的子目录（拼在 NeoForge 的 config 目录后面）。 */
    private static final String FOLDER = "emerald_economy";
    private static final String SHOP_FILE = "shop.json";
    private static final String ACQUIRE_FILE = "acquire.json";
    /** 模组内置的默认模板（在 jar 里，也是开发环境的一份拷贝）。 */
    private static final String DEFAULT_ROOT = "/data/emerald_economy/economy/";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static ShopContent shop = ShopContent.empty();
    private static AcquireContent acquire = AcquireContent.empty();
    private static boolean loaded;
    private static Path shopPath;
    private static Path acquirePath;

    private EconomyContent() {
    }

    public static boolean isLoaded() {
        return loaded;
    }

    public static ShopContent shop() {
        ensureLoaded();
        return shop;
    }

    public static AcquireContent acquire() {
        ensureLoaded();
        return acquire;
    }

    /** 内容文件所在目录。 */
    public static Path folder() {
        return FMLPaths.CONFIGDIR.get().resolve(FOLDER);
    }

    /** 内容文件路径（给 /ee 命令显示）。 */
    public static Path shopFile() {
        return folder().resolve(SHOP_FILE);
    }

    public static Path acquireFile() {
        return folder().resolve(ACQUIRE_FILE);
    }

    /** 第一次访问时加载（服务端启动事件里也会主动调一次）。 */
    public static void ensureLoaded() {
        if (!loaded) {
            reload(null);
        }
    }

    /**
     * 重新读取两个内容文件（缺文件时先从模组内置模板生成）。
     *
     * @param server 有服务端时顺便把新内容推给正开着界面的玩家
     * @return 一句可显示的摘要（成功/失败与条目数）
     */
    public static String reload(MinecraftServer server) {
        shopPath = ensureFile(SHOP_FILE);
        acquirePath = ensureFile(ACQUIRE_FILE);

        ShopContent newShop = read(shopPath, ShopContent.class, shop);
        AcquireContent newAcquire = read(acquirePath, AcquireContent.class, acquire);
        boolean ok = newShop != null && newAcquire != null;
        if (newShop != null) {
            shop = newShop;
        }
        if (newAcquire != null) {
            acquire = newAcquire;
        }
        loaded = true;

        int groups = shop.groupList().size();
        int goods = shop.unlockableEnchants().size() + shop.plainItems().size() + shop.expItems().size();
        int categories = acquire.categoryList().size();
        String summary = String.format("内容已加载：售货机 %d 个大分类 / %d 件商品；收购机 %d 个分类",
                groups, goods, categories);
        if (ok) {
            EmeraldEconomy.LOGGER.info("[内容] {}（{}、{}）", summary, shopPath, acquirePath);
        }
        if (server != null) {
            cn.daenx.mc.emeraldeconomy.network.ServerSync.refreshOpenScreens(server);
        }
        return ok ? summary : summary + "（有文件解析失败，已保留上一次的内容，请看日志）";
    }

    /** 保证内容文件存在：不存在就从模组内置模板复制一份出来。 */
    private static Path ensureFile(String name) {
        Path dir = folder();
        Path target = dir.resolve(name);
        try {
            if (!Files.isDirectory(dir)) {
                Files.createDirectories(dir);
            }
            if (Files.exists(target)) {
                return target;
            }
            try (InputStream in = EconomyContent.class.getResourceAsStream(DEFAULT_ROOT + name)) {
                if (in == null) {
                    EmeraldEconomy.LOGGER.error("[内容] 模组里找不到默认模板 {}", DEFAULT_ROOT + name);
                    return target;
                }
                Files.copy(in, target);
                EmeraldEconomy.LOGGER.info("[内容] 已从模组默认模板生成 {}", target);
            }
        } catch (IOException ex) {
            EmeraldEconomy.LOGGER.error("[内容] 无法准备内容文件 {}：{}", target, ex.toString());
        }
        return target;
    }

    /** 读一个 JSON；失败时返回 null（调用方保留旧内容）。 */
    private static <T> T read(Path path, Class<T> type, T fallback) {
        if (!Files.exists(path)) {
            return fallback;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, type);
        } catch (Exception ex) {
            EmeraldEconomy.LOGGER.error("[内容] 解析 {} 失败（保留上一次的内容）：{}", path, ex.toString());
            return null;
        }
    }
}
