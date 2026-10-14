package cn.daenx.mc.emeraldeconomy.client.screen;

import cn.daenx.mc.emeraldeconomy.client.ClientEconomyCache;
import cn.daenx.mc.emeraldeconomy.client.widget.NumberBox;
import cn.daenx.mc.emeraldeconomy.content.AcquireContent;
import cn.daenx.mc.emeraldeconomy.core.EEConstants;
import cn.daenx.mc.emeraldeconomy.network.ScreenType;
import cn.daenx.mc.emeraldeconomy.network.payload.ClientPayloads;
import cn.daenx.mc.emeraldeconomy.network.payload.ServerPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * 自动收购机界面（需求文档 6.5）。
 * <p>
 * 顶部是分类标签（分类来自 {@code config/emerald_economy/acquire.json}），
 * 主体是当前分类下的物品与收购价，底部统一下注出售数量；
 * 疯狂星期五开放时会多出一个分类与横幅提示。
 * <p>
 * <b>背包里没有的物品，出售按钮是灰的</b>——数量不足「一份」（例如鸡蛋要 8 个）也一并置灰，
 * 免得玩家点了才发现卖不掉。判断只在客户端做（服务端结算时仍会再校验一次），
 * 所以背包里有多少就看界面上的「拥有 N」。
 */
public class AcquisitionScreen extends EEScreen {

    private static final int PAGE_SIZE = 8;
    private static final int ROW_H = 20;

    /** 当前选中的分类 ID（内容文件里写的 id）。 */
    private String category = "";
    private int page;
    private NumberBox countBox;
    /** 当前这一页的「出售」按钮，按行序排列（每帧按背包刷新可用状态）。 */
    private final List<Button> sellButtons = new ArrayList<>();
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

    @Override
    protected int maxPanelWidth() {
        return 380;
    }

    @Override
    protected int maxPanelHeight() {
        return 262;
    }

    @Override
    protected void init() {
        super.init();

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
            addRenderableWidget(Button.builder(
                            isFriday ? Component.literal("★ ").append(label) : label,
                            b -> {
                                category = id;
                                page = 0;
                                this.rebuildWidgets();
                            })
                    .bounds(x + i * tabWidth, tabsY, Math.max(24, tabWidth - 2), 18).build());
        }

        List<ServerPayloads.AcquireLine> entries = entriesOf(acquire, category);
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page >= pages) {
            page = pages - 1;
        }
        if (page < 0) {
            page = 0;
        }

        // 欠款未清时疯狂星期五不能参加：列表照常显示（看得到价格），但不摆「出售」按钮
        boolean fridayBlocked = acquire.hasDebt() && AcquireContent.FRIDAY_ID.equalsIgnoreCase(category);

        sellButtons.clear();
        int from = page * PAGE_SIZE;
        int to = Math.min(entries.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            if (fridayBlocked) {
                break;
            }
            ServerPayloads.AcquireLine entry = entries.get(i);
            int rowY = listTop + (i - from) * ROW_H;
            Button button = Button.builder(
                            Component.translatable(EEConstants.langKey("screen.acquire.sell")),
                            b -> sell(entry.itemId()))
                    .bounds(x + contentW() - 48, rowY - 1, 48, 16).build();
            sellButtons.add(button);
            addRenderableWidget(button);
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
            sellButtons.get(i).active = ownedCount(entry.itemId()) >= Math.max(1, entry.batch());
        }
    }

    /**
     * 点「出售」时发请求。
     * <p>
     * 有批次的物品（鸡蛋 8 个一份）如果框里的数量还不到一份，就自动改成
     * 「把手里的整份全卖掉」——不然玩家会看到「我明明有 16 个，却说数量不足」，
     * 而数字框默认就是 1，很容易撞上。框里的数字会同步改掉，玩家看得见卖了多少。
     */
    private void sell(String itemId) {
        long count = countBox == null ? 0L : countBox.longValue();
        int batch = batchOf(itemId);
        if (batch > 1 && count < batch) {
            long owned = ownedCount(itemId);
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
                new ClientPayloads.AcquireSellRequest(itemId, (int) count));
    }

    /** 某个物品的批次大小（从当前快照里查，查不到按 1 算）。 */
    private static int batchOf(String itemId) {
        ServerPayloads.SyncAcquire acquire = ClientEconomyCache.acquire;
        if (acquire == null || itemId == null) {
            return 1;
        }
        for (ServerPayloads.AcquireCategoryLine line : acquire.categories()) {
            for (ServerPayloads.AcquireLine entry : line.entries()) {
                if (entry.itemId().equals(itemId)) {
                    return Math.max(1, entry.batch());
                }
            }
        }
        return 1;
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
        long total = 0L;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
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
            // 物品名长短不一，按宽度截断，避免横幅文字顶出面板
            drawTrimmed(graphics, Component.translatable(EEConstants.langKey(
                            acquire.hasDebt() ? "screen.acquire.friday_blocked" : "screen.acquire.friday_banner"),
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

        List<ServerPayloads.AcquireLine> entries = entriesOf(acquire, category);
        int from = page * PAGE_SIZE;
        int to = Math.min(entries.size(), from + PAGE_SIZE);
        // 背包可能刚变过（例如刚捡到东西），画之前先把按钮状态刷一遍
        refreshSellButtons(entries, from);
        for (int i = from; i < to; i++) {
            ServerPayloads.AcquireLine entry = entries.get(i);
            int rowY = listTop + (i - from) * ROW_H;
            graphics.fill(x, rowY - 2, x + contentW(), rowY + 17, COLOR_PANEL_ALT);

            ItemStack icon = iconOf(entry.itemId());
            long owned = ownedCount(entry.itemId());
            if (!icon.isEmpty()) {
                graphics.renderItem(icon, x + 2, rowY - 1);
            }
            drawTrimmed(graphics, icon.getHoverName().getString(), x + 22, rowY + 3, COLOR_TEXT, 120);
            // 背包里有几个：没有就标红，让玩家一眼看出为什么按钮是灰的
            drawText(graphics, Component.translatable(EEConstants.langKey("screen.acquire.owned"),
                    owned).getString(), x + 144, rowY + 3, owned > 0 ? COLOR_MUTED : COLOR_DOWN);
            // 有批次的物品（鸡蛋 8 个一份）写清楚「每 N 个 → M」，避免玩家以为卖亏了
            String priceText = entry.batch() > 1
                    ? Component.translatable(EEConstants.langKey("screen.acquire.batch_price"),
                    entry.batch(), entry.price()).getString()
                    : money(entry.price()) + " /个";
            drawRight(graphics, priceText,
                    x + contentW() - 54, rowY + 3, entry.friday() ? COLOR_GOLD : COLOR_UP);
        }

        if (entries.isEmpty()) {
            drawText(graphics, Component.translatable(EEConstants.langKey("screen.acquire.empty")).getString(),
                    x, listTop, COLOR_MUTED);
        }

        if (countBoxCreated) {
            drawText(graphics, Component.translatable(EEConstants.langKey("screen.acquire.count")).getString(),
                    x + 56, panelY + panelH - 19, COLOR_TEXT);
        }
    }

    /** 疯狂星期五横幅：列出当日三件重金收购物品与价格。 */
    private static String fridayNames(ServerPayloads.SyncAcquire acquire) {
        StringBuilder builder = new StringBuilder();
        for (ServerPayloads.AcquireLine entry : entriesOf(acquire, AcquireContent.FRIDAY_ID)) {
            if (builder.length() > 0) {
                builder.append("、");
            }
            builder.append(iconOf(entry.itemId()).getHoverName().getString())
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
