package cn.daenx.mc.emeraldeconomy.command;

import cn.daenx.mc.emeraldeconomy.acquire.AcquireService;
import cn.daenx.mc.emeraldeconomy.content.EconomyContent;
import cn.daenx.mc.emeraldeconomy.core.DaySettlement;
import cn.daenx.mc.emeraldeconomy.data.AccountData;
import cn.daenx.mc.emeraldeconomy.data.EconomyData;
import cn.daenx.mc.emeraldeconomy.data.TransactionType;
import cn.daenx.mc.emeraldeconomy.market.FundState;
import cn.daenx.mc.emeraldeconomy.market.FundType;
import cn.daenx.mc.emeraldeconomy.market.MarketEngine;
import cn.daenx.mc.emeraldeconomy.shop.ShopService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * 调试与管理命令。
 * <p>
 * 主要用途是验证各阶段功能（需求文档 3.7 建议「每完成一个阶段都可以在游戏内验证」），
 * 以及给服主提供应急调整手段。
 */
public final class EECommands {

    private EECommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ee")
                .then(Commands.literal("balance")
                        .executes(ctx -> showSelf(ctx.getSource()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> showOther(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("give")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                        .executes(ctx -> adjustBalance(ctx,
                                                EntityArgument.getPlayer(ctx, "player"),
                                                LongArgumentType.getLong(ctx, "amount"), true)))))
                .then(Commands.literal("take")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                        .executes(ctx -> adjustBalance(ctx,
                                                EntityArgument.getPlayer(ctx, "player"),
                                                LongArgumentType.getLong(ctx, "amount"), false)))))
                .then(Commands.literal("price")
                        .executes(EECommands::showPrices))
                .then(Commands.literal("tick")
                        .requires(source -> source.hasPermission(2))
                        .executes(EECommands::forcePriceTick))
                .then(Commands.literal("settle")
                        .requires(source -> source.hasPermission(2))
                        .executes(EECommands::forceSettle))
                .then(Commands.literal("supply")
                        .executes(EECommands::showSupply))
                .then(Commands.literal("friday")
                        .executes(EECommands::showFriday))
                .then(Commands.literal("setprice")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("fund", StringArgumentType.word())
                                .then(Commands.argument("price", LongArgumentType.longArg(1))
                                        .executes(EECommands::setPrice))))
                .then(Commands.literal("shop")
                        .then(Commands.literal("reset")
                                .requires(source -> source.hasPermission(2))
                                .executes(EECommands::resetShop)))
                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(2))
                        .executes(EECommands::reloadContent)));
    }

    // ------------------------------------------------------------------

    /**
     * 重新读取 {@code config/emerald_economy/shop.json} 与 {@code acquire.json}。
     * <p>
     * 改了货架 / 收购价表之后不用重启服务器；解析失败会保留上一次的内容并在日志里报错。
     */
    private static int reloadContent(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String summary = EconomyContent.reload(source.getServer());
        source.sendSuccess(() -> Component.literal("§6[绿宝石经济] §f" + summary
                + "\n§7售货机：§f" + EconomyContent.shopFile()
                + "\n§7收购机：§f" + EconomyContent.acquireFile()), true);
        return 1;
    }

    private static int showSelf(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return showOther(source, source.getPlayerOrException());
    }

    private static int showOther(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        return showOther(ctx.getSource(), target);
    }

    private static int showOther(CommandSourceStack source, ServerPlayer target) {
        EconomyData data = EconomyData.get(source.getServer());
        AccountData account = data.account(target.getUUID());
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                        "§6[绿宝石经济] §f%s §7余额 §a%d §7欠款 §c%d §7信用额度 §e%d §7可贷 §b%d",
                        target.getGameProfile().getName(),
                        account.balance(), account.debt(),
                        account.creditLimit(), account.availableCredit())),
                false);
        return 1;
    }

    private static int adjustBalance(CommandContext<CommandSourceStack> ctx, ServerPlayer target,
                                     long amount, boolean add)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        EconomyData data = EconomyData.get(source.getServer());
        AccountData account = data.account(target.getUUID());
        long balanceBefore = account.balance();
        long debtBefore = account.debt();
        account.addBalance(add ? amount : -amount);
        account.pushRecord(add ? TransactionType.DEPOSIT : TransactionType.WITHDRAW,
                add ? amount : -amount, balanceBefore, debtBefore, target.level().getGameTime());
        data.setDirty();
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                        "§6[绿宝石经济] §f已%s %s 的账户 %d，当前余额 %d",
                        add ? "发放" : "扣除", target.getGameProfile().getName(), amount, account.balance())),
                true);
        return 1;
    }

    private static int showPrices(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        EconomyData data = EconomyData.get(source.getServer());
        for (FundType type : FundType.values()) {
            FundState state = data.market().fund(type);
            double change = state.changeRatio() * 100.0D;
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                            "%s §7(%s) §f价格 §a%d §7基准 §f%d §7涨跌 %s%.2f%% §7动量 %.4f",
                            type.displayName().getString(), type.code(), state.price(), state.basePrice(),
                            change >= 0 ? "§a+" : "§c", change, state.momentum())),
                    false);
        }
        return FundType.values().length;
    }

    private static int forcePriceTick(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        EconomyData data = EconomyData.get(server);
        MarketEngine.updatePrices(data, server.overworld().random);
        source.sendSuccess(() -> Component.literal("§6[绿宝石经济] §f已强制刷新一次基金价格"), true);
        return 1;
    }

    private static int forceSettle(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        EconomyData data = EconomyData.get(server);
        long day = DaySettlement.currentDay(server.overworld());
        DaySettlement.performSettlement(server, data, day, day,
                DaySettlement.readWeather(server.overworld()), server.overworld().random);
        data.setLastSettledDay(day);
        data.setLastSettleGameTime(server.overworld().getGameTime());
        source.sendSuccess(() -> Component.literal("§6[绿宝石经济] §f已强制执行一次每日结算（第 " + day + " 天）"), true);
        return 1;
    }

    private static int showSupply(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        EconomyData data = EconomyData.get(source.getServer());
        for (FundType type : FundType.values()) {
            long supply = data.market().supply(type);
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                            "%s §7当日供给量 §f%d", type.code(), supply)),
                    false);
        }
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                        "§7当前天气 §f%s", data.market().lastWeather().name())),
                false);
        return FundType.values().length;
    }

    private static int showFriday(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        EconomyData data = EconomyData.get(source.getServer());
        var book = data.acquire();
        if (!book.isFridayActive()) {
            source.sendSuccess(() -> Component.literal("§6[疯狂星期五] §7今日未开放"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                        "§6[疯狂星期五] §f第 %d 天开放，重金收购：%s",
                        book.fridayDay(), String.join("、", book.fridayItems()))),
                false);
        return 1;
    }

    private static int setPrice(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String code = StringArgumentType.getString(ctx, "fund").toUpperCase(Locale.ROOT);
        long price = LongArgumentType.getLong(ctx, "price");
        FundType type = FundType.byCode(code);
        if (type == null) {
            source.sendFailure(Component.literal("§c未知的基金代码：" + code));
            return 0;
        }
        EconomyData data = EconomyData.get(source.getServer());
        data.market().fund(type).setPrice((int) price);
        data.setDirty();
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                        "§6[绿宝石经济] §f已将 %s 的价格设为 %d", code, price)),
                true);
        return 1;
    }

    private static int resetShop(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        EconomyData data = EconomyData.get(source.getServer());
        long day = DaySettlement.currentDay(source.getServer().overworld());
        ShopService.resetDaily(data, day);
        source.sendSuccess(() -> Component.literal("§6[绿宝石经济] §f已重置商店限购次数"), true);
        return 1;
    }
}
