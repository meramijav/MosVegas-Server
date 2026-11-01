package ru.mosvegas.core.shop;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import ru.mosvegas.core.data.JsonFile;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import static ru.mosvegas.core.data.JsonFile.require;

public final class ShopStore {
    public static final class State {
        public int version = 1;
        public List<Shop> shops = new ArrayList<>();
    }
    public static final class Shop {
        public String id, title;
        public boolean enabled = true;
        public List<Offer> offers = new ArrayList<>();
    }
    public static final class Offer {
        public String id, stack;
        public long price;
    }
    private static Path path;
    private static State state = new State();
    private static Map<String, ItemStack> stacks = Map.of();
    private static long revision;
    private ShopStore() {}
    public static State get() { return state; }
    public static long revision() { return revision; }
    public static ItemStack stack(String shop, String offer) { return stacks.get(shop + "/" + offer).copy(); }

    public static void start(MinecraftServer server) {
        path = server.getSavePath(WorldSavePath.ROOT).resolve("mosvegas-shops.json");
        try {
            if (Files.notExists(path)) JsonFile.write(path, new State());
            reload();
        } catch (Exception e) { throw new IllegalStateException("Не удалось загрузить магазины: " + path, e); }
    }
    public static void reload() throws Exception {
        State next = JsonFile.read(path, State.class);
        Map<String, ItemStack> decoded = validate(next);
        state = next;
        stacks = decoded;
        revision++;
    }
    public static void update(Consumer<State> edit) throws Exception {
        State next = JsonFile.copy(state, State.class);
        edit.accept(next);
        Map<String, ItemStack> decoded = validate(next);
        JsonFile.write(path, next);
        state = next;
        stacks = decoded;
        revision++;
    }
    public static Shop find(State source, String id) {
        return source.shops.stream().filter(s -> s.id.equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Магазин не найден: " + id));
    }
    public static Offer offer(Shop shop, String id) {
        return shop.offers.stream().filter(o -> o.id.equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Товар не найден: " + id));
    }
    private static Map<String, ItemStack> validate(State next) throws Exception {
        require(next.version == 1 && next.shops != null, "Некорректный файл магазинов.");
        Set<String> ids = new HashSet<>();
        Map<String, ItemStack> decoded = new HashMap<>();
        for (Shop shop : next.shops) {
            require(shop != null, "Пустой магазин.");
            JsonFile.validId(shop.id);
            require(ids.add(shop.id), "Повтор ID магазина: " + shop.id);
            require(shop.title != null && !shop.title.isBlank() && shop.title.length() <= 100, "Название магазина: 1–100 символов.");
            require(shop.offers != null, "Пустой список товаров.");
            Set<String> offers = new HashSet<>();
            for (Offer offer : shop.offers) {
                require(offer != null, "Пустой товар.");
                JsonFile.validId(offer.id);
                require(offers.add(offer.id) && offer.price >= 0, "Повтор ID товара или отрицательная цена.");
                require(offer.stack != null && offer.stack.length() <= 1_000_000, "Отсутствует или слишком большой NBT товара.");
                ItemStack stack = ItemStack.fromNbt(StringNbtReader.parse(offer.stack));
                require(!stack.isEmpty() && stack.getCount() <= stack.getMaxCount(), "Недоступный предмет или недопустимый размер пачки: " + shop.id + "/" + offer.id);
                decoded.put(shop.id + "/" + offer.id, stack);
            }
        }
        return Map.copyOf(decoded);
    }
}
