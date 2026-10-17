package cn.daenx.mc.emeraldeconomy.client.screen;

import cn.daenx.mc.emeraldeconomy.client.ClientEconomyCache;
import cn.daenx.mc.emeraldeconomy.core.EEConstants;
import cn.daenx.mc.emeraldeconomy.network.ScreenType;
import cn.daenx.mc.emeraldeconomy.network.payload.ClientPayloads;
import cn.daenx.mc.emeraldeconomy.network.payload.ServerPayloads;
import cn.daenx.mc.emeraldeconomy.shop.EnchantCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 自动售货机界面（需求文档 5.5）。
 * <p>
 * 三层结构，全部由服务端下发的标签页数据驱动（数据来自 {@code config/emerald_economy/shop.json}）：
 * <ol>
 *     <li><b>大分类</b>：附魔书 / 其他……（可以自己加）</li>
 *     <li><b>子分类</b>：附魔书下面是武器 / 远程 / 工具 / 盔甲 / 通用与诅咒；其他下面是种子 / 树苗</li>
 *     <li><b>商品行</b>：附魔书显示附魔名 + 等级 + 适用物品；普通商品显示物品名 + 子分类名</li>
 * </ol>
 * 服务端只下发商品数据（键 / 大分类 / 子分类 / ID / 价格 / 剩余限购），等级与适用物品
 * 由客户端从自己的附魔注册表读出，这样联机时客户端不需要服务端配置也能把界面画全。
 */
public class VendingScreen extends EEScreen {

    private static final int PAGE_SIZE = 4;
    private static final int ROW_H = 28;

    private int page;
    /** 当前大分类 ID。 */
    private String groupId = "";
    /** 当前子分类 ID。 */
    private String subId = "";

    /** 该界面对应的服务端快照类型。 */
    @Override
    protected ScreenType screenType() {
        return ScreenType.VENDING;
    }

    public VendingScreen() {
        super(Component.translatable(EEConstants.langKey("screen.vending.title")));
    }

    @Override
    protected int maxPanelWidth() {
        return 396;
    }

    @Override
    protected int maxPanelHeight() {
        return 262;
    }

    private static List<ServerPayloads.ShopTab> tabs() {
        ServerPayloads.SyncShop shop = ClientEconomyCache.shop;
        return shop == null ? List.of() : shop.tabs();
    }

    private static List<ServerPayloads.ShopGood> allGoods() {
        ServerPayloads.SyncShop shop = ClientEconomyCache.shop;
        return shop == null ? List.of() : shop.goods();
    }

    /** 全部大分类 ID（按服务端给的顺序去重）。 */
    private static List<String> groupIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (ServerPayloads.ShopTab tab : tabs()) {
            ids.add(tab.groupId());
        }
        return new ArrayList<>(ids);
    }

    /** 某个大分类下的子分类。 */
    private static List<ServerPayloads.ShopTab> subsOf(String groupId) {
        List<ServerPayloads.ShopTab> out = new ArrayList<>();
        for (ServerPayloads.ShopTab tab : tabs()) {
            if (tab.groupId().equals(groupId)) {
                out.add(tab);
            }
        }
        return out;
    }

    /** 当前选中的子分类要显示的商品。 */
    private static List<ServerPayloads.ShopGood> goodsOf(String groupId, String subId) {
        List<ServerPayloads.ShopGood> out = new ArrayList<>();
        for (ServerPayloads.ShopGood good : allGoods()) {
            if (good.groupId().equals(groupId) && good.subId().equals(subId)) {
                out.add(good);
            }
        }
        return out;
    }

    /** 某个大分类是不是「解锁制」（只有解锁制才显示升级按钮）。 */
    private static boolean unlockable(String groupId) {
        for (ServerPayloads.ShopTab tab : tabs()) {
            if (tab.groupId().equals(groupId)) {
                return tab.unlockable();
            }
        }
        return false;
    }

    /** 标签显示名：优先翻译键，其次内容文件里写的原样名。 */
    private static Component label(String lang, String name, String fallback) {
        if (lang != null && !lang.isBlank()) {
            return Component.translatable(EEConstants.langKey(lang));
        }
        return Component.literal(name == null || name.isBlank() ? fallback : name);
    }

    private static Component groupLabel(ServerPayloads.ShopTab tab) {
        return label(tab.groupLang(), tab.groupName(), tab.groupId());
    }

    private static Component subLabel(ServerPayloads.ShopTab tab) {
        return label(tab.subLang(), tab.subName(), tab.subId());
    }

    /** 「图标 + 名字」的标签文本。 */
    private static Component tagged(String icon, Component name) {
        return Component.literal(icon == null || icon.isBlank() ? "" : icon + " ").append(name);
    }

    @Override
    protected void init() {
        super.init();

        ServerPayloads.SyncShop shop = ClientEconomyCache.shop;
        int x = contentX();
        int groupTabsY = panelY + PAD + 26;
        int subTabsY = groupTabsY + 20;
        int listTop = subTabsY + 20;

        if (shop == null) {
            return;
        }
        // 大分类标签：默认落到有商品的那个大分类
        List<String> groups = groupIds();
        if (groupId.isEmpty() || !groups.contains(groupId)) {
            groupId = firstGroupWithGoods(groups);
            subId = "";
        }
        int groupTabWidth = Math.max(60, contentW() / Math.max(1, groups.size()));
        for (int i = 0; i < groups.size(); i++) {
            final String id = groups.get(i);
            List<ServerPayloads.ShopTab> subs = subsOf(id);
            ServerPayloads.ShopTab first = subs.isEmpty() ? null : subs.get(0);
            Component text = first == null ? Component.literal(id) : groupLabel(first);
            String icon = first == null ? "" : first.groupIcon();
            addRenderableWidget(Button.builder(tagged(icon, text), b -> {
                        groupId = id;
                        subId = "";
                        page = 0;
                        this.rebuildWidgets();
                    })
                    .bounds(x + i * groupTabWidth, groupTabsY, groupTabWidth - 2, 18).build());
        }

        // 子分类标签：默认落到「真的有商品」的第一个子分类（解锁是随机的，第一个子分类很可能是空的）
        List<ServerPayloads.ShopTab> subs = subsOf(groupId);
        if (subId.isEmpty() || !containsSub(subs, subId)) {
            subId = firstSubWithGoods(subs);
        }
        int subTabWidth = subs.isEmpty() ? contentW()
                : Math.max(40, Math.min(96, contentW() / subs.size()));
        for (int i = 0; i < subs.size(); i++) {
            ServerPayloads.ShopTab tab = subs.get(i);
            final String id = tab.subId();
            addRenderableWidget(Button.builder(tagged(tab.subIcon(), subLabel(tab)), b -> {
                        subId = id;
                        page = 0;
                        this.rebuildWidgets();
                    })
                    .bounds(x + i * subTabWidth, subTabsY, Math.max(30, subTabWidth - 2), 18).build());
        }

        // 商品行
        List<ServerPayloads.ShopGood> goods = goodsOf(groupId, subId);
        int pages = Math.max(1, (goods.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page >= pages) {
            page = pages - 1;
        }
        if (page < 0) {
            page = 0;
        }
        int from = page * PAGE_SIZE;
        int to = Math.min(goods.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            ServerPayloads.ShopGood good = goods.get(i);
            int rowY = listTop + (i - from) * ROW_H;
            addRenderableWidget(Button.builder(
                            Component.translatable(EEConstants.langKey("screen.vending.buy")),
                            b -> PacketDistributor.sendToServer(new ClientPayloads.ShopActionRequest(
                                    ClientPayloads.ShopAction.BUY.ordinal(), good.key())))
                    .bounds(x + contentW() - 56, rowY + 3, 56, 18).build());
        }

        int bottom = panelY + panelH - 24;
        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("◀"), b -> {
                        page--;
                        this.rebuildWidgets();
                    })
                    .bounds(x, bottom, 22, 18).build());
            addRenderableWidget(Button.builder(Component.literal("▶"), b -> {
                        page++;
                        this.rebuildWidgets();
                    })
                    .bounds(x + 26, bottom, 22, 18).build());
        }

        // 升级只跟解锁制的大分类有关，「其他」那种常驻货架不显示升级按钮
        if (unlockable(groupId) && shop.nextUpgradeCost() >= 0L) {
            addRenderableWidget(Button.builder(
                            Component.translatable(EEConstants.langKey("screen.vending.upgrade"),
                                    money(shop.nextUpgradeCost()), shop.unlocked() + 1),
                            b -> PacketDistributor.sendToServer(new ClientPayloads.ShopActionRequest(
                                    ClientPayloads.ShopAction.UPGRADE.ordinal(), "")))
                    .bounds(x + contentW() - 150, bottom, 150, 18).build());
        }
    }

    /** 第一个「有已解锁商品」的大分类（都没有时退回第一个）。 */
    private static String firstGroupWithGoods(List<String> groups) {
        if (groups.isEmpty()) {
            return "";
        }
        for (String id : groups) {
            if (!goodsOf(id, "").isEmpty()) {
                return id;
            }
            for (ServerPayloads.ShopTab tab : subsOf(id)) {
                if (!goodsOf(id, tab.subId()).isEmpty()) {
                    return id;
                }
            }
        }
        return groups.get(0);
    }

    /** 某个大分类下第一个「有商品」的子分类。 */
    private static String firstSubWithGoods(List<ServerPayloads.ShopTab> subs) {
        if (subs.isEmpty()) {
            return "";
        }
        for (ServerPayloads.ShopTab tab : subs) {
            if (!goodsOf(tab.groupId(), tab.subId()).isEmpty()) {
                return tab.subId();
            }
        }
        return subs.get(0).subId();
    }

    private static boolean containsSub(List<ServerPayloads.ShopTab> subs, String subId) {
        for (ServerPayloads.ShopTab tab : subs) {
            if (tab.subId().equals(subId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void drawContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = contentX();
        int y = panelY + PAD;
        drawTitle(graphics, Component.translatable(EEConstants.langKey("screen.vending.title")));

        ServerPayloads.SyncShop shop = ClientEconomyCache.shop;
        if (shop == null) {
            drawText(graphics, Component.translatable(EEConstants.langKey("screen.loading")).getString(),
                    x, y + 20, COLOR_MUTED);
            return;
        }

        drawRight(graphics, Component.translatable(EEConstants.langKey("screen.exchange.balance")).getString()
                + " " + money(shop.balance()), x + contentW(), y, COLOR_UP);
        y += 12;
        drawText(graphics, Component.translatable(EEConstants.langKey("screen.vending.unlocked"),
                shop.unlocked(), shop.maxSlots()).getString(), x, y, COLOR_MUTED);
        drawRight(graphics, Component.translatable(EEConstants.langKey(
                        unlockable(groupId) ? "screen.vending.hint" : "screen.vending.hint.other")).getString(),
                x + contentW(), y, COLOR_MUTED);

        int groupTabsY = panelY + PAD + 26;
        int subTabsY = groupTabsY + 20;
        int listTop = subTabsY + 20;

        // 高亮当前大分类 / 子分类
        List<String> groups = groupIds();
        int groupTabWidth = Math.max(60, contentW() / Math.max(1, groups.size()));
        int groupIndex = groups.indexOf(groupId);
        if (groupIndex >= 0) {
            graphics.fill(x + groupIndex * groupTabWidth, groupTabsY - 1,
                    x + groupIndex * groupTabWidth + groupTabWidth - 2, groupTabsY + 19, 0x40FFD75E);
        }

        List<ServerPayloads.ShopTab> subs = subsOf(groupId);
        int subTabWidth = subs.isEmpty() ? contentW()
                : Math.max(40, Math.min(96, contentW() / subs.size()));
        for (int i = 0; i < subs.size(); i++) {
            if (subs.get(i).subId().equals(subId)) {
                graphics.fill(x + i * subTabWidth, subTabsY - 1, x + i * subTabWidth + subTabWidth - 2,
                        subTabsY + 19, 0x4000C8FF);
            }
        }

        List<ServerPayloads.ShopGood> goods = goodsOf(groupId, subId);
        int from = page * PAGE_SIZE;
        int to = Math.min(goods.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            ServerPayloads.ShopGood good = goods.get(i);
            int rowY = listTop + (i - from) * ROW_H;
            graphics.fill(x, rowY, x + contentW(), rowY + ROW_H - 2, COLOR_PANEL_ALT);

            ItemStack icon = iconOf(good);
            if (!icon.isEmpty()) {
                graphics.renderItem(icon, x + 2, rowY + 4);
            }
            // 第一行：名称（附魔书带等级；经验值商品写「N 点经验值」）
            Component name = good.enchant()
                    ? Component.empty().append(enchantName(good.id())).append(" ")
                    .append(Component.translatable("enchantment.level." + levelOf(good.id())))
                    : good.exp() > 0
                    ? Component.translatable(EEConstants.langKey("screen.vending.exp_goods"), good.exp())
                    : itemName(good.id());
            drawTrimmed(graphics, name.getString(), x + 22, rowY + 3, COLOR_TEXT, 150);
            // 第二行：附魔书显示适用物品，经验值商品说明「不是等级」，其他商品显示它的子分类
            drawTrimmed(graphics, good.enchant()
                            ? Component.translatable(EEConstants.langKey("screen.vending.applicable"),
                            applicableOf(good.id())).getString()
                            : good.exp() > 0
                            ? Component.translatable(EEConstants.langKey("screen.vending.exp_hint")).getString()
                            : subNameOf(good).getString(),
                    x + 22, rowY + 14, COLOR_MUTED, 190);

            drawRight(graphics, money(good.price()), x + contentW() - 106, rowY + 3, COLOR_GOLD);
            drawRight(graphics, Component.translatable(EEConstants.langKey("screen.vending.remaining"),
                            good.remaining()).getString(),
                    x + contentW() - 62, rowY + 14, good.remaining() > 0 ? COLOR_MUTED : COLOR_DOWN);
        }

        if (goods.isEmpty()) {
            ServerPayloads.ShopTab current = currentTab();
            drawText(graphics, Component.translatable(EEConstants.langKey("screen.vending.empty_category"),
                    current == null ? subId : subLabel(current)).getString(), x, listTop + 6, COLOR_MUTED);
        }

        int pages = Math.max(1, (goods.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (pages > 1) {
            drawText(graphics, Component.translatable(EEConstants.langKey("screen.vending.page"),
                    page + 1, pages).getString(), x + 54, panelY + panelH - 19, COLOR_MUTED);
        }
    }

    /** 当前选中的标签页（找不到返回 null）。 */
    private ServerPayloads.ShopTab currentTab() {
        for (ServerPayloads.ShopTab tab : subsOf(groupId)) {
            if (tab.subId().equals(subId)) {
                return tab;
            }
        }
        return null;
    }

    /** 某个商品所属子分类的显示名。 */
    private static Component subNameOf(ServerPayloads.ShopGood good) {
        for (ServerPayloads.ShopTab tab : tabs()) {
            if (tab.groupId().equals(good.groupId()) && tab.subId().equals(good.subId())) {
                return subLabel(tab);
            }
        }
        return Component.literal(good.subId());
    }

    /** 商品图标：附魔书本地造一本满级书，经验值商品用经验瓶示意，其他商品直接取物品。 */
    private static ItemStack iconOf(ServerPayloads.ShopGood good) {
        if (good.enchant()) {
            return bookOf(good.id());
        }
        if (good.exp() > 0) {
            // 经验值没有物品形态，用经验瓶当图标，让玩家一眼看出这条卖的是经验
            return new ItemStack(net.minecraft.world.item.Items.EXPERIENCE_BOTTLE);
        }
        ResourceLocation id = ResourceLocation.tryParse(good.id());
        Level level = Minecraft.getInstance().level;
        if (id == null || level == null) {
            return ItemStack.EMPTY;
        }
        Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    /** 客户端本地构造一个满级附魔书图标（不涉及任何价格计算）。 */
    private static ItemStack bookOf(String enchantId) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return ItemStack.EMPTY;
        }
        try {
            return EnchantCatalog.createMaxLevelBook(level.registryAccess(), enchantId);
        } catch (RuntimeException ex) {
            return ItemStack.EMPTY;
        }
    }

    /** 附魔书显示名，取不到时退回注册名。 */
    private static Component enchantName(String enchantId) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return Component.literal(enchantId);
        }
        try {
            var holder = EnchantCatalog.holder(level.registryAccess(), enchantId);
            return holder == null ? Component.literal(enchantId) : holder.value().description();
        } catch (RuntimeException ex) {
            return Component.literal(enchantId);
        }
    }

    /** 普通物品显示名。 */
    private static Component itemName(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) {
            return Component.literal(itemId);
        }
        Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
        return item == null ? Component.literal(itemId) : Component.translatable(item.getDescriptionId());
    }

    /** 附魔等级上限（1.21 的每本附魔书都卖满级）。 */
    private static int levelOf(String enchantId) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return 1;
        }
        try {
            int maxLevel = EnchantCatalog.level(level.registryAccess(), enchantId);
            return maxLevel <= 0 ? 1 : maxLevel;
        } catch (RuntimeException ex) {
            return 1;
        }
    }

    /** 「适用物品」文案。 */
    private static Component applicableOf(String enchantId) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return Component.literal("-");
        }
        try {
            return EnchantCatalog.applicable(level.registryAccess(), enchantId);
        } catch (RuntimeException ex) {
            return Component.literal("-");
        }
    }
}
