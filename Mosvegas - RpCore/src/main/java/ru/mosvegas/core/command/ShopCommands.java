package ru.mosvegas.core.command;

import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.*;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import ru.mosvegas.core.shop.*;
import ru.mosvegas.core.util.Permissions;
import static net.minecraft.server.command.CommandManager.*;

public final class ShopCommands {
    private ShopCommands() {}
    public static LiteralArgumentBuilder<ServerCommandSource> shop() {
        return literal("shop").executes(c -> run(c, () -> ShopService.list(c.getSource().getPlayerOrThrow(), 0)))
                .then(id().executes(c -> run(c, () -> ShopService.open(c.getSource().getPlayerOrThrow(), value(c, "id"), 0))));
    }
    public static LiteralArgumentBuilder<ServerCommandSource> admin() {
        return literal("shopadmin").requires(s -> Permissions.hasAny(s, 3, "mosvegas.admin.shops", "mosvegas.admin"))
                .then(literal("list").executes(c -> run(c, () -> ShopStore.get().shops.forEach(s -> c.getSource().sendFeedback(
                        () -> Text.literal(s.id + " — " + s.title + " | " + s.offers.size() + " товаров | " + (s.enabled ? "открыт" : "закрыт")), false)))))
                .then(literal("create").then(argument("id", StringArgumentType.word()).then(argument("title", StringArgumentType.greedyString()).executes(c -> run(c, () -> {
                    ShopStore.update(state -> {
                        ShopStore.Shop shop = new ShopStore.Shop(); shop.id = value(c, "id"); shop.title = value(c, "title"); state.shops.add(shop);
                    });
                    ok(c, "Магазин создан. Добавьте товар из руки: /mosvegas shopadmin add " + value(c, "id") + " <товар> <цена>; или откройте edit.");
                })))))
                .then(literal("edit").then(id().executes(c -> run(c, () -> ShopService.edit(c.getSource().getPlayerOrThrow(), value(c, "id"), 0)))))
                .then(literal("add").then(id().then(argument("offer", StringArgumentType.word()).then(argument("price", LongArgumentType.longArg(0)).executes(c -> run(c, () -> {
                    ShopService.add(value(c, "id"), value(c, "offer"), LongArgumentType.getLong(c, "price"), c.getSource().getPlayerOrThrow().getMainHandStack().copy());
                    ok(c, "Товар добавлен. Цена задана за всю пачку из руки; оригинал остался у вас.");
                }))))))
                .then(literal("price").then(id().then(offer().then(argument("price", LongArgumentType.longArg(0)).executes(c -> run(c, () -> {
                    ShopStore.update(state -> ShopStore.offer(ShopStore.find(state, value(c, "id")), value(c, "offer")).price = LongArgumentType.getLong(c, "price")); ok(c, "Цена обновлена.");
                }))))))
                .then(literal("remove").then(id().then(offer().executes(c -> run(c, () -> {
                    ShopStore.update(state -> {
                        ShopStore.Shop shop = ShopStore.find(state, value(c, "id")); shop.offers.remove(ShopStore.offer(shop, value(c, "offer")));
                    }); ok(c, "Товар удалён.");
                })))))
                .then(literal("enabled").then(id().then(argument("enabled", BoolArgumentType.bool()).executes(c -> run(c, () -> {
                    ShopStore.update(state -> ShopStore.find(state, value(c, "id")).enabled = BoolArgumentType.getBool(c, "enabled")); ok(c, "Состояние магазина изменено.");
                })))))
                .then(literal("rename").then(id().then(argument("title", StringArgumentType.greedyString()).executes(c -> run(c, () -> {
                    ShopStore.update(state -> ShopStore.find(state, value(c, "id")).title = value(c, "title")); ok(c, "Название изменено.");
                })))))
                .then(literal("delete").then(id().executes(c -> run(c, () -> {
                    ShopStore.update(state -> state.shops.remove(ShopStore.find(state, value(c, "id")))); ok(c, "Магазин удалён.");
                }))));
    }
    private static RequiredArgumentBuilder<ServerCommandSource, String> id() {
        return argument("id", StringArgumentType.word()).suggests((c, b) -> CommandSource.suggestMatching(ShopStore.get().shops.stream().map(s -> s.id), b));
    }
    private static RequiredArgumentBuilder<ServerCommandSource, String> offer() {
        return argument("offer", StringArgumentType.word()).suggests((c, b) -> CommandSource.suggestMatching(ShopStore.find(ShopStore.get(), value(c, "id")).offers.stream().map(o -> o.id), b));
    }
    private static String value(CommandContext<ServerCommandSource> c, String name) { return StringArgumentType.getString(c, name); }
    private static void ok(CommandContext<ServerCommandSource> c, String text) { c.getSource().sendFeedback(() -> Text.literal(text), true); }
    static int run(CommandContext<ServerCommandSource> c, Action action) {
        try { action.run(); return 1; }
        catch (Exception e) { c.getSource().sendError(Text.literal("Mosvegas: " + e.getMessage())); return 0; }
    }
    @FunctionalInterface interface Action { void run() throws Exception; }
}
