package cn.daenx.mc.emeraldeconomy.client.screen;

import cn.daenx.mc.emeraldeconomy.acquire.AcquireService;
import cn.daenx.mc.emeraldeconomy.client.ClientEconomyCache;
import cn.daenx.mc.emeraldeconomy.client.widget.NumberBox;
import cn.daenx.mc.emeraldeconomy.content.AcquireContent;
import cn.daenx.mc.emeraldeconomy.core.EEConstants;
import cn.daenx.mc.emeraldeconomy.network.ScreenType;
import cn.daenx.mc.emeraldeconomy.network.payload.ClientPayloads;
import cn.daenx.mc.emeraldeconomy.network.payload.ServerPayloads;
import cn.daenx.mc.emeraldeconomy.shop.EnchantCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 自动收购机界面（需求文档 6.5）。
 * <p>
 * 顶部是分类标签（普通分类来自 {@code config/emerald_economy/acquire.json}），
 * 主体是当前分类下的物品与收购价，底部统一下注出售数量；
 * 疯狂星期五开放时会多出一个分类与横幅提示。
 * <p>
 * 本轮新增两个特殊分类：
 * <ul>
 *     <li><b>附魔回收</b>：列出背包里所有带附魔的物品（含附魔书），显示每个附魔的回收价与整叠总价；
 *         点「回收」会先弹二次确认，确认后才真正出售；</li>
 *     <li>普通分类里带 {@code filter=treasure_map} 的行（藏宝图）只收带结构标记的地图。</li>
 * </ul>
 * <b>背包里没有的物品，出售按钮是灰的</b>——数量不足「一份」（例如鸡蛋要 8 个）也一并置灰，
 * 免得玩家点了才发现卖不掉。判断只在客户端做（服务端结算时仍会再校验一次）。
 */
public class AcquisitionScreen extends EEScreen {

    private static final int PAGE_SIZE = 8;
    private static final int ROW_H = 20;
    /** 附魔回收每页几行、每行多高（它要显示多行附魔名）。 */
    private static final int ENCHANT_PAGE_SIZE = 5;
    private static final int ENCHANT_ROW_H = 32;

    /** 当前选中的分类 ID（内容文件里写的 id，或 {@link AcquireContent#ENCHANT_ID}）。 */
    private String category = "";
    private int page;
    private NumberBox countBox;
    /** 当前这一页的「出售」按钮，按行序排列（每帧按背包刷新可用状态）。 */
    private final List<Button> sellButtons = new ArrayList<>();
    /** 当前这一页的「出售全部」按钮，与 {@link #sellButtons} 一一对应。 */
    private final List<Button> sellAllButtons = new ArrayList<>();
    /** 附魔回收页的「回收」按钮。 */
    private final List<Button> enchantButtons = new ArrayList<>();
    /** 是否已经在 init 里创建了数量输入框（用于决定要不要画它的标签）。 */
    private boolean countBoxCreated;

    /** 该界面对应的服务端快照类型。 */
    @Override
    protected ScreenType screenType() {
        return ScreenType.ACQUISITION;
    }

    public AcquisitionScreen() {
        super(Component.translatable(EEConstants.langKey("screen.acquire.title")));
    }

    /** 从附魔回收的确认框返回时用：保持停在「附魔回收」分类。 */
    public AcquisitionScreen(String category) {
        this();
        this.category = category == null ? "" : category;
    }

    @Override
    protected int maxPanelWidth() {
        return 380;
    }

    @Override
    protected int maxPanelHeight() {
        return 262;
    }

    private boolean isEnchantCategory() {
        return AcquireContent.ENCHANT_ID.equalsIgnoreCase(category);
    }

    private static List<ServerPayloads.EnchantItemLine> enchantItems() {
        ServerPayloads.SyncAcquire acquire = ClientEconomyCache.acquire;
        return acquire == null ? List.of() : acquire.enchantItems();
    }

    @Override
    protected void init() {
        super.init();
        countBox = null;
        countBoxCreated = false;
        sellButtons.clear();
        sellAllButtons.clear();
        enchantButtons.clear();

        ServerPayloads.SyncAcquire acquire = ClientEconomyCache.acquire;
        int x = contentX();
        int tabsY = panelY + PAD + 30;
        int listTop = tabsY + 22;

        if (acquire == null) {
            return;
        }
        List<ServerPayloads.AcquireCategoryLine> categories = acquire.categories();
        if (category.isEmpty() || categoryById(acquire, category) == null) {
            category = categories.isEmpty() ? "" : categories.get(0).id();
        }
        int tabWidth = Math.max(40, contentW() / Math.max(1, categories.size()));
        for (int i = 0; i < categories.size(); i++) {
            ServerPayloads.AcquireCategoryLine line = categories.get(i);
            final String id = line.id();
            boolean isFriday = AcquireContent.FRIDAY_ID.equalsIgnoreCase(id);
            Component label = tabName(line);
            if (isFriday) {
                label = Component.literal("★ ").append(label);
            }
            addRenderableWidget(Button.builder(label, b -> {
                                category = id;
                                page = 0;
                                this.rebuildWidgets();
                            })
                    .bounds(x + i * tabWidth, tabsY, Math.max(24, tabWidth - 2), 18).build());
        }

        if (isEnchantCategory()) {
            if (acquire.enchantUnlocked()) {
                initEnchantButtons(x, listTop);
            } else {
                // 「附魔回收」未解锁：不摆回收按钮，只给一个解锁按钮
                addUnlockButton(x, "screen.acquire.enchant.unlock", acquire.enchantUnlockCost());
            }
        } else if (AcquireContent.FRIDAY_ID.equalsIgnoreCase(category) && !acquire.fridayUnlocked()) {
            // 疯狂星期五未解锁：列表照常显示（看得到价格与名单），但没有「出售」按钮
            initSellButtons(acquire, x, listTop, true);
            addUnlockButton(x, "screen.acquire.friday.unlock", acquire.fridayUnlockCost());
        } else {
            initSellButtons(acquire, x, listTop, false);
        }
    }

    /** 在面板右下角放一个「解锁」按钮，点击后向服务端发送解锁请求。 */
    private void addUnlockButton(int x, String langKey, long cost) {
        addRenderableWidget(Button.builder(
                        Component.translatable(EEConstants.langKey(langKey), money(cost)),
                        b -> PacketDistributor.sendToServer(new ClientPayloads.AcquireUnlockRequest(category)))
                .bounds(x + contentW() - 160, panelY + panelH - 24, 160, 18).build());
    }

    /** 普通分类：物品行 + 出售按钮 + 数量框。 */
    private void initSellButtons(ServerPayloads.SyncAcquire acquire, int x, int listTop,
                                 boolean fridayLocked) {
        List<ServerPayloads.AcquireLine> entries = entriesOf(acquire, category);
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page >= pages) {
            page = pages - 1;
        }
        if (page < 0) {
            page = 0;
        }

        // 欠款未清 / 未付费解锁时疯狂星期五不能参加：列表照常显示（看得到价格），
        // 但界面上不生成「出售」按钮
        boolean fridayBlocked = fridayLocked && AcquireContent.FRIDAY_ID.equalsIgnoreCase(category);

        int from = page * PAGE_SIZE;
        int to = Math.min(entries.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            if (fridayBlocked) {
                break;
            }
            ServerPayloads.AcquireLine entry = entries.get(i);
            int rowY = listTop + (i - from) * ROW_H;
            Button sell = Button.builder(
                            Component.translatable(EEConstants.langKey("screen.acquire.sell")),
                            b -> sell(entry))
                    .bounds(x + contentW() - 94, rowY - 1, 36, 16).build();
            Button sellAll = Button.builder(
                            Component.translatable(EEConstants.langKey("screen.acquire.sell_all")),
                            b -> sellAll(entry))
                    .bounds(x + contentW() - 54, rowY - 1, 54, 16).build();
            sellButtons.add(sell);
            sellAllButtons.add(sellAll);
            addRenderableWidget(sell);
            addRenderableWidget(sellAll);
        }
        refreshSellButtons(entries, from);

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
        countBox = addRenderableWidget(new NumberBox(this.font, x + 120, bottom, 70, 18, "1"));
        countBoxCreated = true;
    }

    /** 附魔回收：每行一个背包格子，按钮点开后先弹二次确认。 */
    private void initEnchantButtons(int x, int listTop) {
        List<ServerPayloads.EnchantItemLine> items = enchantItems();
        int pages = Math.max(1, (items.size() + ENCHANT_PAGE_SIZE - 1) / ENCHANT_PAGE_SIZE);
        if (page >= pages) {
            page = pages - 1;
        }
        if (page < 0) {
            page = 0;
        }
        int from = page * ENCHANT_PAGE_SIZE;
        int to = Math.min(items.size(), from + ENCHANT_PAGE_SIZE);
        for (int i = from; i < to; i++) {
            ServerPayloads.EnchantItemLine item = items.get(i);
            int rowY = listTop + (i - from) * ENCHANT_ROW_H;
            Button button = Button.builder(
                            Component.translatable(EEConstants.langKey("screen.acquire.recycle")),
                            b -> Minecraft.getInstance().setScreen(
                                    new EnchantRecycleConfirmScreen(item)))
                    .bounds(x + contentW() - 54, rowY - 1, 54, 16).build();
            enchantButtons.add(button);
            addRenderableWidget(button);
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
    }

    /**
     * 按背包刷新出售按钮的可用状态。
     * <p>
     * 背包里的东西随时会变（卖掉、捡到、别的机器给的），所以不能只在 {@code init()} 里算一次——
     * 这里每帧都刷，玩家一捡到东西按钮就会亮起来。
     */
    private void refreshSellButtons(List<ServerPayloads.AcquireLine> entries, int from) {
        for (int i = 0; i < sellButtons.size(); i++) {
            int index = from + i;
            if (index >= entries.size()) {
                break;
            }
            ServerPayloads.AcquireLine entry = entries.get(index);
            // 背包里不够一份就置灰（数量不足整份的话服务端也会拒绝）
            boolean ready = ownedCount(entry) >= Math.max(1, entry.batch());
            sellButtons.get(i).active = ready;
            if (i < sellAllButtons.size()) {
                sellAllButtons.get(i).active = ready;
            }
        }
    }

    /**
     * 点「出售」时发请求。
     * <p>
     * 有批次的物品（鸡蛋 8 个一份）如果框里的数量还不到一份，就自动改成
     * 「把手里的整份全卖掉」——不然玩家会看到「我明明有 16 个，却说数量不足」，
     * 而数字框默认就是 1，很容易撞上。框里的数字会同步改掉，玩家看得见卖了多少。
     * <p>
     * 标签行（例如「任意音乐唱片」）也一样按「整份」处理，只不过数的是该类物品的总数。
     */
    private void sell(ServerPayloads.AcquireLine entry) {
        long count = countBox == null ? 0L : countBox.longValue();
        int batch = Math.max(1, entry.batch());
        if (batch > 1 && count < batch) {
            long owned = ownedCount(entry);
            if (owned >= batch) {
                count = (owned / batch) * batch;
                if (countBox != null) {
                    countBox.setValue(String.valueOf(count));
                }
            }
        }
        if (count <= 0L || count > Integer.MAX_VALUE) {
            return;
        }
        PacketDistributor.sendToServer(
                new ClientPayloads.AcquireSellRequest(entry.itemId(), (int) count, false));
    }

    /**
     * 点「出售全部」：数量交给服务端按背包实际可卖数量计算。
     * <p>
     * 客户端不传具体数字，是因为藏宝图（只收带结构标记的）与标签行（整类合计）
     * 在客户端统计不准；服务端本来就能准确数出来。
     */
    private void sellAll(ServerPayloads.AcquireLine entry) {
        PacketDistributor.sendToServer(
                new ClientPayloads.AcquireSellRequest(entry.itemId(), 0, true));
    }

    /** 分类标签的显示名：优先用翻译键，查不到就用内容文件里写的原样名字。 */
    private static Component tabName(ServerPayloads.AcquireCategoryLine line) {
        String lang = line.lang();
        if (lang != null && !lang.isBlank()) {
            return Component.translatable(EEConstants.langKey(lang));
        }
        return Component.literal(line.name() == null ? line.id() : line.name());
    }

    private static ServerPayloads.AcquireCategoryLine categoryById(ServerPayloads.SyncAcquire acquire,
                                                                  String id) {
        for (ServerPayloads.AcquireCategoryLine line : acquire.categories()) {
            if (line.id().equalsIgnoreCase(id)) {
                return line;
            }
        }
        return null;
    }

    private static List<ServerPayloads.AcquireLine> entriesOf(ServerPayloads.SyncAcquire acquire,
                                                             String categoryId) {
        ServerPayloads.AcquireCategoryLine line = categoryById(acquire, categoryId);
        return line == null ? List.of() : line.entries();
    }

    /** 客户端自己数背包里有几个（判断按钮灰不灰用，服务端结算时还会再校验）。 */
    private static long ownedCount(String itemId) {
        var player = Minecraft.getInstance().player;
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (player == null || id == null) {
            return 0L;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == null) {
            return 0L;
        }
        return countOwned(player, java.util.Set.of(item));
    }

    /**
     * 一行条目在背包里的拥有数量。
     * <p>
     * 标签行（「任意音乐唱片」「所有树苗」……）数的是该类里<b>所有</b>物品加起来的数量，
     * 因为出售时也确实允许混着卖。
     * <p>
     * 藏宝图行是个例外：客户端拿不到完整的地图数据，分不清哪张是藏宝图，
     * 所以这里只能先把所有已填写地图都算上；真正的过滤在服务端做，
     * 普通地图会被拒绝（界面上的「拥有 N」只是参考）。
     */
    private static long ownedCount(ServerPayloads.AcquireLine entry) {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return 0L;
        }
        if (AcquireContent.Item.FILTER_TREASURE_MAP.equals(entry.filter())) {
            return countOwned(player, Set.of(Items.FILLED_MAP));
        }
        if (entry.wildcard()) {
            // 通配行（任意刷怪蛋）：把注册表里所有匹配的物品数量加在一起
            Set<Item> members = new LinkedHashSet<>(AcquireService.wildcardMemberItems(entry.itemId()));
            return members.isEmpty() ? 0L : countOwned(player, members);
        }
        if (!entry.tag()) {
            return ownedCount(entry.itemId());
        }
        Set<Item> members = tagMembers(entry.itemId());
        return members.isEmpty() ? 0L : countOwned(player, members);
    }

    /** 标签行里的全部物品（客户端本地解析，用于显示图标与统计数量）。 */
    private static Set<Item> tagMembers(String tagRowId) {
        Set<Item> out = new LinkedHashSet<>();
        if (tagRowId == null || tagRowId.length() < 2) {
            return out;
        }
        ResourceLocation id = ResourceLocation.tryParse(tagRowId.substring(1));
        if (id == null) {
            return out;
        }
        TagKey<Item> tag = TagKey.create(Registries.ITEM, id);
        for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
            out.add(holder.value());
        }
        return out;
    }

    private static long countOwned(net.minecraft.world.entity.player.Player player, Set<Item> items) {
        long total = 0L;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && items.contains(stack.getItem())) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** 一行的显示名：翻译键 → 原样文本 → 物品名 / 标签 ID。 */
    private static Component rowName(ServerPayloads.AcquireLine entry) {
        if (entry.lang() != null && !entry.lang().isBlank()) {
            return Component.translatable(EEConstants.langKey(entry.lang()));
        }
        if (entry.name() != null && !entry.name().isBlank()) {
            return Component.literal(entry.name());
        }
        if (entry.tag()) {
            return Component.literal(entry.itemId());
        }
        return iconOf(entry.itemId()).getHoverName();
    }

    /** 一行的图标：标签行取该类里的第一个物品当代表。 */
    private static ItemStack rowIcon(ServerPayloads.AcquireLine entry) {
        if (entry.wildcard()) {
            for (Item item : AcquireService.wildcardMemberItems(entry.itemId())) {
                return new ItemStack(item);
            }
            return ItemStack.EMPTY;
        }
        if (!entry.tag()) {
            return iconOf(entry.itemId());
        }
        for (Item item : tagMembers(entry.itemId())) {
            return new ItemStack(item);
        }
        return ItemStack.EMPTY;
    }

    @Override
    protected void drawContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = contentX();
        int y = panelY + PAD;
        drawTitle(graphics, Component.translatable(EEConstants.langKey("screen.acquire.title")));

        ServerPayloads.SyncAcquire acquire = ClientEconomyCache.acquire;
        if (acquire == null) {
            drawText(graphics, Component.translatable(EEConstants.langKey("screen.loading")).getString(),
                    x, y + 20, COLOR_MUTED);
            return;
        }

        drawRight(graphics, Component.translatable(EEConstants.langKey("screen.exchange.balance")).getString()
                + " " + money(acquire.balance()), x + contentW(), y, COLOR_UP);
        y += 14;

        if (acquire.fridayActive()) {
            graphics.fill(x, y, x + contentW(), y + 12, 0x50FF5E5E);
            // 横幅按状态换文案：欠款未清 / 未付费解锁都要说清「为什么暂时不能参加」
            String bannerKey = acquire.hasDebt() ? "screen.acquire.friday_blocked"
                    : acquire.fridayUnlocked() ? "screen.acquire.friday_banner"
                    : "screen.acquire.friday_locked";
            // 物品名长短不一，按宽度截断，避免横幅文字顶出面板
            drawTrimmed(graphics, Component.translatable(EEConstants.langKey(bannerKey),
                    fridayNames(acquire)).getString(), x + 4, y + 2, COLOR_GOLD, contentW() - 8);
        } else {
            drawText(graphics, Component.translatable(EEConstants.langKey("screen.acquire.hint"),
                    acquire.day(), acquire.fridayIntervalDays()).getString(), x, y, COLOR_MUTED);
        }

        int tabsY = panelY + PAD + 30;
        int listTop = tabsY + 22;
        // 高亮当前分类
        List<ServerPayloads.AcquireCategoryLine> categories = acquire.categories();
        int tabWidth = Math.max(40, contentW() / Math.max(1, categories.size()));
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).id().equalsIgnoreCase(category)) {
                graphics.fill(x + i * tabWidth, tabsY - 1, x + i * tabWidth + tabWidth - 2, tabsY + 19,
                        0x40FFD75E);
            }
        }

        if (isEnchantCategory()) {
            if (acquire.enchantUnlocked()) {
                drawEnchantContents(graphics, x, listTop);
            } else {
                // 未解锁：列表区域只画提示（服务端此时也不会下发背包扫描结果）
                drawText(graphics, Component.translatable(EEConstants.langKey(
                                "screen.acquire.enchant.locked"),
                        money(acquire.enchantUnlockCost())).getString(), x, listTop + 6, COLOR_DOWN);
                drawText(graphics, Component.translatable(EEConstants.langKey(
                        "screen.acquire.enchant.locked_hint")).getString(), x, listTop + 26,
                        COLOR_MUTED);
            }
        } else {
            drawSellContents(graphics, acquire, x, listTop);
        }
    }

    /** 普通分类的列表。 */
    private void drawSellContents(GuiGraphics graphics, ServerPayloads.SyncAcquire acquire, int x,
                                  int listTop) {
        List<ServerPayloads.AcquireLine> entries = entriesOf(acquire, category);
        int from = page * PAGE_SIZE;
        int to = Math.min(entries.size(), from + PAGE_SIZE);
        // 背包可能刚变过（例如刚捡到东西），画之前先把按钮状态刷一遍
        refreshSellButtons(entries, from);
        for (int i = from; i < to; i++) {
            ServerPayloads.AcquireLine entry = entries.get(i);
            int rowY = listTop + (i - from) * ROW_H;
            graphics.fill(x, rowY - 2, x + contentW(), rowY + 17, COLOR_PANEL_ALT);

            ItemStack icon = rowIcon(entry);
            long owned = ownedCount(entry);
            if (!icon.isEmpty()) {
                graphics.renderItem(icon, x + 2, rowY - 1);
            }
            drawTrimmed(graphics, rowName(entry).getString(), x + 22, rowY + 3, COLOR_TEXT, 120);
            // 背包里有几个：没有就标红，让玩家一眼看出为什么按钮是灰的
            drawText(graphics, Component.translatable(EEConstants.langKey("screen.acquire.owned"),
                    owned).getString(), x + 144, rowY + 3, owned > 0 ? COLOR_MUTED : COLOR_DOWN);
            // 有批次的物品（鸡蛋 8 个一份）写清楚「每 N 个 → M」，避免玩家以为卖亏了
            String priceText = entry.batch() > 1
                    ? Component.translatable(EEConstants.langKey("screen.acquire.batch_price"),
                    entry.batch(), entry.price()).getString()
                    : money(entry.price()) + " /个";
            drawRight(graphics, priceText,
                    x + contentW() - 100, rowY + 3, entry.friday() ? COLOR_GOLD : COLOR_UP);
        }

        if (entries.isEmpty()) {
            drawText(graphics, Component.translatable(EEConstants.langKey("screen.acquire.empty")).getString(),
                    x, listTop, COLOR_MUTED);
        }

        if (countBoxCreated) {
            drawText(graphics, Component.translatable(EEConstants.langKey("screen.acquire.count")).getString(),
                    x + 56, panelY + panelH - 19, COLOR_TEXT);
            int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            drawRight(graphics, Component.translatable(EEConstants.langKey("screen.acquire.page"),
                    page + 1, pages).getString(), x + contentW(), panelY + panelH - 19, COLOR_MUTED);
        }
    }

    /** 附魔回收的列表：物品名 / 数量 / 每个附魔的回收价 / 整叠总价。 */
    private void drawEnchantContents(GuiGraphics graphics, int x, int listTop) {
        List<ServerPayloads.EnchantItemLine> items = enchantItems();
        int from = page * ENCHANT_PAGE_SIZE;
        int to = Math.min(items.size(), from + ENCHANT_PAGE_SIZE);
        for (int i = from; i < to; i++) {
            ServerPayloads.EnchantItemLine item = items.get(i);
            int rowY = listTop + (i - from) * ENCHANT_ROW_H;
            graphics.fill(x, rowY - 2, x + contentW(), rowY + ENCHANT_ROW_H - 4, COLOR_PANEL_ALT);

            ItemStack icon = iconOf(item.itemId());
            if (!icon.isEmpty()) {
                graphics.renderItem(icon, x + 2, rowY - 1);
            }
            String name = icon.isEmpty() ? item.itemId() : icon.getHoverName().getString();
            drawTrimmed(graphics, name + " ×" + item.count(), x + 22, rowY + 2, COLOR_TEXT, 170);
            drawRight(graphics, money(item.totalPrice()), x + contentW() - 58, rowY + 2, COLOR_UP);
            drawTrimmed(graphics, enchantSummary(item), x + 22, rowY + 14, COLOR_MUTED,
                    contentW() - 32);
        }

        if (items.isEmpty()) {
            drawText(graphics, Component.translatable(EEConstants.langKey(
                    "screen.acquire.enchant.empty")).getString(), x, listTop, COLOR_MUTED);
        }

        int pages = Math.max(1, (items.size() + ENCHANT_PAGE_SIZE - 1) / ENCHANT_PAGE_SIZE);
        // 回收规则放在列表下方，避免和底部的页码 / 翻页按钮叠在一起
        int hintY = listTop + Math.min(ENCHANT_PAGE_SIZE, Math.max(1, items.size())) * ENCHANT_ROW_H - 2;
        drawTrimmed(graphics, Component.translatable(EEConstants.langKey(
                "screen.acquire.enchant.hint")).getString(), x, hintY, COLOR_MUTED, contentW());
        drawText(graphics, Component.translatable(EEConstants.langKey("screen.acquire.page"),
                page + 1, pages).getString(), x + 56, panelY + panelH - 19, COLOR_MUTED);
    }

    /** 一行附魔的摘要：「经验修补 1 → 520 · 保护 3 → 224」。 */
    private static String enchantSummary(ServerPayloads.EnchantItemLine item) {
        StringBuilder builder = new StringBuilder();
        for (ServerPayloads.EnchantPriceLine line : item.enchants()) {
            if (builder.length() > 0) {
                builder.append(" · ");
            }
            builder.append(enchantName(line.enchantId()))
                    .append(" ").append(line.level())
                    .append(" → ").append(money(line.price()));
        }
        return builder.toString();
    }

    /** 附魔名（客户端按自己的语言渲染）。 */
    private static String enchantName(String enchantId) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return enchantId;
        }
        return EnchantCatalog.displayName(level.registryAccess(), enchantId).getString();
    }

    /** 疯狂星期五横幅：列出当日三件重金收购物品与价格。 */
    private static String fridayNames(ServerPayloads.SyncAcquire acquire) {
        StringBuilder builder = new StringBuilder();
        for (ServerPayloads.AcquireLine entry : entriesOf(acquire, AcquireContent.FRIDAY_ID)) {
            if (builder.length() > 0) {
                builder.append("、");
            }
            builder.append(rowName(entry).getString())
                    .append("(").append(entry.price()).append(")");
        }
        return builder.toString();
    }

    private static ItemStack iconOf(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }
}
