package ru.mosvegas.core.command;

import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.*;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import ru.mosvegas.core.zone.*;
import ru.mosvegas.core.util.Permissions;
import java.util.*;
import static net.minecraft.server.command.CommandManager.*;
import static ru.mosvegas.core.command.ShopCommands.run;

public final class ZoneCommands {
    private record Selection(String world, BlockPos first, BlockPos second) {}
    private static final Map<UUID, Selection> selections = new HashMap<>();
    private ZoneCommands() {}
    public static void clear() { selections.clear(); }
    public static void disconnect(ServerPlayerEntity player) { selections.remove(player.getUuid()); }

    public static LiteralArgumentBuilder<ServerCommandSource> branch() {
        var root = literal("zone").requires(s -> Permissions.hasAny(s, 3, "mosvegas.admin.zones", "mosvegas.admin"));
        root.then(position("pos1", true)).then(position("pos2", false));
        root.then(literal("create").then(argument("id", StringArgumentType.word())
                .executes(c -> create(c, 0)).then(argument("priority", IntegerArgumentType.integer()).executes(c -> create(c, IntegerArgumentType.getInteger(c, "priority"))))));
        root.then(literal("bounds").then(id().executes(c -> run(c, () -> {
            Selection s = selected(c); ZoneStore.update(state -> bounds(ZoneStore.find(state, value(c, "id")), s)); ok(c, "Границы зоны обновлены.");
        }))));
        root.then(literal("priority").then(id().then(argument("priority", IntegerArgumentType.integer()).executes(c -> run(c, () -> {
            ZoneStore.update(state -> ZoneStore.find(state, value(c, "id")).priority = IntegerArgumentType.getInteger(c, "priority")); ok(c, "Приоритет обновлён.");
        })))));
        root.then(literal("flag").then(id().then(action(false).then(argument("rule", StringArgumentType.word())
                .suggests((c, b) -> CommandSource.suggestMatching(List.of("allow", "deny", "members", "inherit"), b)).executes(c -> run(c, () -> {
                    ZoneAction action = ZoneAction.parse(value(c, "action"));
                    String mode = value(c, "rule");
                    ZoneStore.update(state -> {
                        ZoneDefinition zone = ZoneStore.find(state, value(c, "id"));
                        if (mode.equals("inherit")) zone.flags.remove(action);
                        else zone.flags.put(action, ZoneDefinition.Rule.valueOf(mode.toUpperCase(Locale.ROOT)));
                    }); ok(c, "Правило " + action.id() + " = " + mode);
                }))))));
        root.then(access("grant", true)).then(access("revoke", false));
        root.then(literal("delete").then(id().executes(c -> run(c, () -> {
            ZoneStore.update(state -> state.zones.remove(ZoneStore.find(state, value(c, "id")))); ok(c, "Зона удалена.");
        }))));
        root.then(literal("info").then(id().executes(c -> run(c, () -> show(c, ZoneStore.find(ZoneStore.get(), value(c, "id")))))));
        root.then(literal("list").executes(c -> run(c, () -> {
            ok(c, "Защищённые миры: " + ZoneStore.get().protectedWorlds);
            for (ZoneDefinition z : ZoneStore.get().zones) ok(c, z.id + " | " + z.dimension + " | приоритет " + z.priority);
        })));
        root.then(literal("here").executes(c -> run(c, () -> {
            var p = c.getSource().getPlayerOrThrow();
            var zones = ZoneStore.index().at(p.getWorld().getRegistryKey().getValue().toString(), p.getX(), p.getY(), p.getZ());
            ok(c, "Зоны здесь: " + zones.stream().map(z -> z.id).toList());
            for (ZoneAction a : ZoneAction.values()) ok(c, a.id() + " для вас: " + ZoneService.can(p, p.getWorld(), p.getBlockPos(), a));
        })));
        root.then(literal("world").then(argument("dimension", IdentifierArgumentType.identifier())
                .suggests((c, b) -> CommandSource.suggestMatching(c.getSource().getServer().getWorldRegistryKeys().stream().map(k -> k.getValue().toString()), b))
                .then(argument("protected", BoolArgumentType.bool()).executes(c -> run(c, () -> {
                    ZoneStore.update(state -> {
                        if (BoolArgumentType.getBool(c, "protected")) state.protectedWorlds.add(IdentifierArgumentType.getIdentifier(c, "dimension").toString());
                        else state.protectedWorlds.remove(IdentifierArgumentType.getIdentifier(c, "dimension").toString());
                    }); ok(c, "Защита мира обновлена (build и terrain).");
                })))));
        return root;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> position(String name, boolean first) {
        return literal(name).executes(c -> setPosition(c, first, c.getSource().getPlayerOrThrow().getBlockPos()))
                .then(argument("pos", BlockPosArgumentType.blockPos()).executes(c -> setPosition(c, first, BlockPosArgumentType.getBlockPos(c, "pos"))));
    }
    private static int setPosition(CommandContext<ServerCommandSource> c, boolean first, BlockPos pos) {
        return run(c, () -> {
            ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
            String world = p.getWorld().getRegistryKey().getValue().toString();
            Selection previous = selections.get(p.getUuid());
            if (previous == null || !previous.world.equals(world)) previous = new Selection(world, null, null);
            selections.put(p.getUuid(), new Selection(world, first ? pos.toImmutable() : previous.first, first ? previous.second : pos.toImmutable()));
            ok(c, (first ? "Первая" : "Вторая") + " точка: " + pos.toShortString());
        });
    }
    private static Selection selected(CommandContext<ServerCommandSource> c) throws Exception {
        Selection s = selections.get(c.getSource().getPlayerOrThrow().getUuid());
        if (s == null || s.first == null || s.second == null) throw new IllegalArgumentException("Сначала задайте /mosvegas zone pos1 и pos2 в одном измерении.");
        return s;
    }
    private static int create(CommandContext<ServerCommandSource> c, int priority) {
        return run(c, () -> {
            Selection s = selected(c);
            ZoneStore.update(state -> {
                ZoneDefinition z = new ZoneDefinition(); z.id = value(c, "id"); z.priority = priority; bounds(z, s); state.zones.add(z);
            }); ok(c, "Зона создана. Правила наследуются; настройте их через /mosvegas zone flag.");
        });
    }
    private static void bounds(ZoneDefinition z, Selection s) {
        z.dimension = s.world;
        z.minX = Math.min(s.first.getX(), s.second.getX()); z.maxX = Math.max(s.first.getX(), s.second.getX());
        z.minY = Math.min(s.first.getY(), s.second.getY()); z.maxY = Math.max(s.first.getY(), s.second.getY());
        z.minZ = Math.min(s.first.getZ(), s.second.getZ()); z.maxZ = Math.max(s.first.getZ(), s.second.getZ());
    }
    private static LiteralArgumentBuilder<ServerCommandSource> access(String name, boolean grant) {
        var target = id();
        for (boolean faction : List.of(true, false)) target.then(literal(faction ? "faction" : "player")
                .then(argument("subject", StringArgumentType.word()).suggests((c, b) -> CommandSource.suggestMatching(
                        faction ? ru.mosvegas.core.config.ConfigManager.get().factions.stream().map(f -> f.id).toList()
                                : Arrays.asList(c.getSource().getServer().getPlayerNames()), b))
                .then(action(true).executes(c -> run(c, () -> {
                    String subject = value(c, "subject");
                    if (!faction) {
                        ServerPlayerEntity online = c.getSource().getServer().getPlayerManager().getPlayer(subject);
                        subject = online == null ? UUID.fromString(subject).toString() : online.getUuidAsString();
                    }
                    String key = subject;
                    List<ZoneAction> actions = value(c, "action").equals("all") ? Arrays.stream(ZoneAction.values()).filter(a -> a != ZoneAction.TERRAIN).toList()
                            : List.of(ZoneAction.parse(value(c, "action")));
                    if (actions.contains(ZoneAction.TERRAIN)) throw new IllegalArgumentException("У terrain нет участников.");
                    ZoneStore.update(state -> {
                        ZoneDefinition zone = ZoneStore.find(state, value(c, "id"));
                        var map = faction ? zone.factions : zone.players;
                        for (ZoneAction action : actions) {
                            var entries = map.computeIfAbsent(action, a -> new LinkedHashSet<>());
                            if (grant) entries.add(key); else entries.remove(key);
                        }
                    }); ok(c, "Доступ " + (grant ? "добавлен" : "отозван") + ". Список применяется только при flag = members.");
                })))));
        return literal(name).then(target);
    }
    private static RequiredArgumentBuilder<ServerCommandSource, String> id() {
        return argument("id", StringArgumentType.word()).suggests((c, b) -> CommandSource.suggestMatching(ZoneStore.get().zones.stream().map(z -> z.id), b));
    }
    private static RequiredArgumentBuilder<ServerCommandSource, String> action(boolean all) {
        return argument("action", StringArgumentType.word()).suggests((c, b) -> {
            List<String> values = new ArrayList<>(Arrays.stream(ZoneAction.values()).map(ZoneAction::id).toList());
            if (all) values.add("all");
            return CommandSource.suggestMatching(values, b);
        });
    }
    private static String value(CommandContext<ServerCommandSource> c, String name) { return StringArgumentType.getString(c, name); }
    private static void ok(CommandContext<ServerCommandSource> c, String text) { c.getSource().sendFeedback(() -> Text.literal(text), false); }
    private static void show(CommandContext<ServerCommandSource> c, ZoneDefinition z) {
        ok(c, z.id + " | " + z.dimension + " | " + z.minX + " " + z.minY + " " + z.minZ + " → " + z.maxX + " " + z.maxY + " " + z.maxZ + " | приоритет " + z.priority);
        ok(c, "Правила: " + z.flags); ok(c, "Фракции: " + z.factions); ok(c, "Игроки (UUID): " + z.players);
    }
}
