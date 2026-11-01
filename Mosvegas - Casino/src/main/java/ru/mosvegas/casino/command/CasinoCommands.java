package ru.mosvegas.casino.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.BlockState;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import ru.mosvegas.casino.block.BlackjackTableBlockEntity;
import ru.mosvegas.casino.block.TablePartBlock;
import ru.mosvegas.casino.config.ConfigManager;
import ru.mosvegas.casino.config.TableStyle;
import ru.mosvegas.casino.dealer.DealerRoster;
import ru.mosvegas.casino.economy.Economy;
import ru.mosvegas.casino.item.TableItem;
import ru.mosvegas.casino.net.CasinoNet;
import ru.mosvegas.casino.registry.CasinoRegistry;
import ru.mosvegas.casino.table.BlackjackTable;
import ru.mosvegas.casino.table.TableRegistry;
import ru.mosvegas.casino.util.Perms;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** {@code /casino}: table setup for admins, {@code /casino leave} for players. */
public final class CasinoCommands {
    private static final SuggestionProvider<ServerCommandSource> STYLES = (ctx, builder) -> {
        for (String id : ConfigManager.get().styles.keySet()) if (id.startsWith(builder.getRemainingLowerCase())) builder.suggest(id);
        return builder.buildFuture();
    };

    private CasinoCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(literal("casino")
                .executes(CasinoCommands::help)
                .then(literal("leave").executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    BlackjackTable t = TableRegistry.tableOf(p.getUuid());
                    if (t != null) t.leave(p.getUuid(), true);
                    return 1;
                }))
                .then(literal("give").requires(Perms::admin)
                        .then(argument("style", StringArgumentType.word()).suggests(STYLES).executes(CasinoCommands::give)))
                .then(literal("place").requires(Perms::admin)
                        .then(argument("style", StringArgumentType.word()).suggests(STYLES).executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            BlockPos master = p.getBlockPos().offset(p.getHorizontalFacing(), 3);
                            String err = ru.mosvegas.casino.table.TablePlacer.place(p.getServerWorld(), master, p.getHorizontalFacing().getOpposite(),
                                    StringArgumentType.getString(ctx, "style"));
                            if (err != null) {
                                ctx.getSource().sendError(Text.literal(err));
                                return 0;
                            }
                            ctx.getSource().sendFeedback(() -> Text.literal("Стол установлен: " + master.toShortString()), true);
                            return 1;
                        })))
                .then(literal("reload").requires(Perms::admin).executes(CasinoCommands::reload))
                .then(literal("stats").requires(Perms::admin).executes(CasinoCommands::stats))
                .then(literal("dealer").requires(Perms::admin)
                        .then(literal("list").executes(ctx -> {
                            for (String line : DealerRoster.status()) ctx.getSource().sendFeedback(() -> Text.literal(line), false);
                            return 1;
                        }))
                        .then(literal("call").executes(ctx -> withTable(ctx, t -> {
                            t.director().call();
                            return "Крупье вызван к столу.";
                        })))
                        .then(literal("change").executes(ctx -> withTable(ctx, t -> t.director().handoverNow()
                                ? "Смена крупье после текущего раунда (если есть свободный крупье — см. /casino dealer list)."
                                : "§eЗа столом сейчас нет крупье.")))
                        .then(literal("dismiss").executes(ctx -> withTable(ctx, t -> {
                            t.director().dismiss();
                            return "Крупье отпущен (уйдёт после текущего раунда).";
                        }))))
                .then(literal("table").requires(Perms::admin)
                        .then(literal("info").executes(CasinoCommands::info))
                        .then(literal("open").executes(ctx -> withTable(ctx, t -> {
                            t.blockEntity().setOpen(true);
                            return "Стол открыт.";
                        })))
                        .then(literal("close").executes(ctx -> withTable(ctx, t -> {
                            t.blockEntity().setOpen(false);
                            return "Стол закрыт: ставки текущего раунда возвращены, крупье уйдёт.";
                        })))
                        .then(literal("style").then(argument("style", StringArgumentType.word()).suggests(STYLES).executes(ctx -> withTable(ctx, t -> {
                            String id = StringArgumentType.getString(ctx, "style");
                            TableStyle style = ConfigManager.get().styles.get(id);
                            if (style == null) return "§cНет стиля " + id;
                            if (style.size() != t.geometry().size) return "§cСтиль " + id + " другого размера (" + style.size().id + "): поставьте новый стол.";
                            if (!t.engine().betweenRounds()) return "§cДождитесь конца раунда.";
                            t.blockEntity().setStyle(id);
                            return "Стиль стола: " + style.name;
                        }))))
                        .then(literal("entrance")
                                .then(literal("here").executes(ctx -> withTable(ctx, t -> {
                                    t.blockEntity().setEntrance(BlockPos.ofFloored(ctx.getSource().getPosition()));
                                    return "Вход персонала для крупье: " + BlockPos.ofFloored(ctx.getSource().getPosition()).toShortString();
                                })))
                                .then(literal("reset").executes(ctx -> withTable(ctx, t -> {
                                    t.blockEntity().setEntrance(null);
                                    return "Вход персонала сброшен (4.5 блока за спиной крупье).";
                                }))))
                        .then(literal("limits")
                                .then(literal("reset").executes(ctx -> withTable(ctx, t -> {
                                    t.blockEntity().setLimits(0, 0);
                                    return "Лимиты как в стиле стола.";
                                })))
                                .then(argument("min", LongArgumentType.longArg(1)).then(argument("max", LongArgumentType.longArg(1)).executes(ctx -> withTable(ctx, t -> {
                                    long min = LongArgumentType.getLong(ctx, "min"), max = LongArgumentType.getLong(ctx, "max");
                                    if (max < min) return "§cМаксимум меньше минимума.";
                                    t.blockEntity().setLimits(min, max);
                                    return "Лимиты стола: " + Economy.format(min) + " — " + Economy.format(max);
                                }))))))
                .then(literal("money").requires(Perms::admin)
                        .then(argument("player", EntityArgumentType.player()).then(argument("amount", LongArgumentType.longArg(0)).executes(ctx -> {
                            if (!Economy.isDev()) {
                                ctx.getSource().sendError(Text.literal("Только для тестового кошелька: деньгами управляет Mosvegas Core."));
                                return 0;
                            }
                            ServerPlayerEntity p = EntityArgumentType.getPlayer(ctx, "player");
                            Economy.devSet(p.getUuid(), LongArgumentType.getLong(ctx, "amount"));
                            ctx.getSource().sendFeedback(() -> Text.literal("Баланс " + p.getName().getString() + ": " + Economy.format(Economy.balance(p))), false);
                            return 1;
                        })))));
    }

    private interface TableTask { String run(BlackjackTable table); }

    private static int withTable(CommandContext<ServerCommandSource> ctx, TableTask task) {
        BlackjackTable table = target(ctx.getSource());
        if (table == null) {
            ctx.getSource().sendError(Text.literal("Посмотрите на стол казино (или встаньте рядом, до 10 блоков)."));
            return 0;
        }
        String result = task.run(table);
        ctx.getSource().sendFeedback(() -> Text.literal(result), true);
        return 1;
    }

    static BlackjackTable target(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return null;
        HitResult hit = player.raycast(10, 0, false);
        if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = bhr.getBlockPos();
            BlockState state = player.getWorld().getBlockState(pos);
            if (state.isOf(CasinoRegistry.TABLE_PART)) pos = TablePartBlock.master(state, pos);
            BlackjackTable t = TableRegistry.get(player.getWorld(), pos);
            if (t != null) return t;
        }
        return TableRegistry.nearest(player, 10);
    }

    private static int help(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource s = ctx.getSource();
        s.sendFeedback(() -> Text.literal("§6MosVegas Casino§7 — ПКМ по столу: сесть за свободный бокс или ставить за спиной, Shift — встать, [B] — меню стола."), false);
        if (Perms.admin(s)) {
            s.sendFeedback(() -> Text.literal("§7/casino give <стиль> · table info|open|close|style|entrance here|reset|limits <мин> <макс>|reset · dealer list|call|dismiss · reload · stats · money <игрок> <сумма>"), false);
        }
        s.sendFeedback(() -> Text.literal("§7/casino leave — выйти из-за стола."), false);
        return 1;
    }

    private static int give(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        String id = StringArgumentType.getString(ctx, "style");
        TableStyle style = ConfigManager.get().styles.get(id);
        if (style == null) {
            ctx.getSource().sendError(Text.literal("Нет стиля " + id + ". Доступно: " + String.join(", ", ConfigManager.get().styles.keySet())));
            return 0;
        }
        player.getInventory().offerOrDrop(TableItem.forStyle(id, style.size(), style.name));
        ctx.getSource().sendFeedback(() -> Text.literal("Выдан стол: " + style.name + " (" + style.variant().title + ", " + style.boxCount() + " боксов)"), true);
        return 1;
    }

    private static int reload(CommandContext<ServerCommandSource> ctx) {
        boolean ok = ConfigManager.load();
        Economy.configure(ConfigManager.get());
        for (ServerPlayerEntity p : ctx.getSource().getServer().getPlayerManager().getPlayerList()) CasinoNet.sendProfiles(p);
        ctx.getSource().sendFeedback(() -> Text.literal(ok ? "§aКонфиг казино перечитан." : "§cОшибка в конфиге — оставлены прежние настройки, см. лог."), true);
        return ok ? 1 : 0;
    }

    private static int info(CommandContext<ServerCommandSource> ctx) {
        BlackjackTable t = target(ctx.getSource());
        if (t == null) {
            ctx.getSource().sendError(Text.literal("Стол не найден."));
            return 0;
        }
        BlackjackTableBlockEntity be = t.blockEntity();
        var e = t.engine();
        String[] lines = {
                "§6" + t.style().name + " §7(" + t.style().id + ", " + t.style().variant().title + ", " + e.boxes() + " боксов)",
                "§7Фаза: §f" + e.phase() + " §7· игроков: §f" + e.seatedCount() + " §7· открыт: §f" + (be.isOpen() ? "да" : "нет"),
                "§7Лимиты: §f" + Economy.format(e.minBet()) + " — " + Economy.format(e.maxBet()) + " §7· шуз: §f" + e.shoeRemaining() + "/" + e.shoeSize(),
                "§7Крупье: §f" + t.director().statusText(),
                "§7Вход персонала: §f" + (be.entrance() == null ? "по умолчанию" : be.entrance().toShortString()),
                "§7За всё время: ставки §f" + Economy.format(be.wagered() + e.statWagered()) + "§7, выплаты §f" + Economy.format(be.paid() + e.statPaid())
                        + "§7, раундов §f" + (be.rounds() + e.statRounds())
        };
        for (String line : lines) ctx.getSource().sendFeedback(() -> Text.literal(line), false);
        return 1;
    }

    private static int stats(CommandContext<ServerCommandSource> ctx) {
        for (BlackjackTable t : TableRegistry.all()) {
            var e = t.engine();
            long profit = e.statWagered() - e.statPaid();
            ctx.getSource().sendFeedback(() -> Text.literal("§6" + t.style().name + " §7" + t.key() + " §f" + e.statRounds() + " раундов, доход казино: "
                    + Economy.format(profit) + " §7(" + e.seatedCount() + " игроков, " + e.phase() + ")"), false);
        }
        return 1;
    }
}
